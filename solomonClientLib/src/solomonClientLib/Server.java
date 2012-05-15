package solomonClientLib;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.EnumSet;
import static solomonserver.ResultCode.*;
import solomonserver.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.NetworkInterface;
import java.rmi.registry.Registry;
import java.util.Enumeration;
import javax.swing.Timer;

/**
 * This singleton class encapsulates all synchronous communication to and 
 * from the server.  (Asynchronous communication (i.e., the IResponse 
 * interface) is handled by the Player class directly.  The primary value 
 * added by this class is that it encapsulates finding, registering, and 
 * unregistering with the server. 
 */
public class Server 
    implements ActionListener  {
    
    private static Server _instance = null;
    private IConnection conn = null;
    int playerID = 0;
    int numberOfRounds;
    
    /**
     * This is the delay for a non-fatal error from the server before 
     * retrying the command.  This allows the protocol to resynchronize with 
     * the remote player without pummeling  the server.
     */
    static final int SYNCHRONIZING_LATENCY = 50;
    
    /**
     * Max retries to doGesture before declaring us fatally out of 
     * synchronization.
     */
    static final int DO_GESTURE_MAX_RETRIES = 50;
    
    /**
     * Max retries to getScore before declaring  us fatally out of
     * synchronization.
     */
    static final int GET_SCORE_MAX_RETRIES = 50;
    
    private static final String CLIENT_PROPERTIES_FILENAME = "solomonClient.properties";
    private static final String PROP_FILE_HEADER_COMMENT = "Solomon Client Library Properties File";
    private static final String SERVER_ADDRESS_KEY = "serverAddress";
    private static final int PING_TIMEOUT = 15; 
    private static final int PORT_SCAN_TIMEOUT = 100;
    private Properties prop;
    private Server()
    {
        // read in persistent properties
        prop = new Properties();
        FileReader propReader = null;
        try {
            propReader = new FileReader(CLIENT_PROPERTIES_FILENAME);
            prop.load( propReader );
        }
        catch (IOException ex) {
//            Logger.getLogger(Server.class.getName()).log(Level.WARNING, null, ex);
        }
        if (propReader!=null) {
            try {
                propReader.close();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.WARNING, null, ex);
            }
        }
        
        // start our keepalive timer
        keepMeAlive.start();
    }
    
    // TODO: heartbeat should be 1/2 of server's zombie timeout
    /**
     * Period to notify server that we're still alive, in milliseconds
     */
    private final int HEARTBEAT_PERIOD = 2500;
    
    /**
     * Timer for generating keepalive heartbeat
     */
    private Timer keepMeAlive = new Timer(HEARTBEAT_PERIOD,this);
    
    /**
     * At every heartbeat, notify the server that we're still alive.
     * 
     * @param evt unused
     */
    @Override
    public void actionPerformed( ActionEvent evt ) {
        try { 
            if (conn!=null) 
                conn.keepAlive(); 
        } 
        catch (Exception e) {}
    }
        
    /**
     * Find the Solomon Server
     * 
     * This function attempts to auto-discover the Solomon Server by an 
     * exhaustive, iterative process, necessary due to the limitations of 
     * Java-based network discovery.
     * 
     * The general algorithm is to iterate through every network interface on 
     * the system, and port-scan every address within any qualifying subnet.  
     * The discovered system is the first found that:
     *   1) has a 32-bit IP address
     *   2) is Site Local 
     *   3) has a subnet with less than 2^16 addresses (for efficiency), 
     *   4) is reachable (i.e., an active system) 
     *   5) allows socket connection to our RMI Registry port
     * 
     * @return the URL of the Solomon Server, or null
     */
    private String findSolomonServer() {
        
        boolean debug = true;
        
        String serverAddr = null;

        // if debug mode, dump all interface info
        if (debug) {
        try {
            for ( Enumeration<NetworkInterface> nie = NetworkInterface.getNetworkInterfaces(); nie.hasMoreElements(); ) {
                NetworkInterface ni = nie.nextElement();
                if (ni!=null) {
                    System.out.println( "IF: " + ni.getDisplayName() );
                    for (InterfaceAddress address : ni.getInterfaceAddresses()) {
//                        if (address.getAddress().getAddress().length==4 && address.getAddress().isSiteLocalAddress() )
                            System.out.printf( "    addr: %s   len: %d   prefix len:%d   %s%s%s%s\n", 
                                    address.getAddress(),
                                    address.getAddress().getAddress().length,
                                    address.getNetworkPrefixLength(),
                                    address.getAddress().isAnyLocalAddress()?"isAnyLocalAddress ":"",
                                    address.getAddress().isLinkLocalAddress()?"isLinkLocalAddress ":"",
                                    address.getAddress().isLoopbackAddress()?"isLoopbackAddress ":"",
                                    address.getAddress().isSiteLocalAddress()?"isSiteLocalAddress ":"" );
                    }
                }
            }
            } catch (Exception ex) { }
        }
        
        // for every interface on this system
        boolean bFoundServer = false;
        try {
            for ( Enumeration<NetworkInterface> nie = NetworkInterface.getNetworkInterfaces(); nie.hasMoreElements() && !bFoundServer; ) {
                NetworkInterface ni = nie.nextElement();
                if (ni!=null) {
                
                    // for every addresses for this interface
                    //  (discriminate: only 32-bit addresses, only Site Local addresses, only subnet mask < 2^16)
                    for (InterfaceAddress address : ni.getInterfaceAddresses()) {
                        byte[] localIpAddress = address.getAddress().getAddress();
                        int networkPrefixLength = address.getNetworkPrefixLength();
                        if (localIpAddress.length==4 && address.getAddress().isSiteLocalAddress() && networkPrefixLength>=16) {
                            
                            // for every ip address in the subnet...
                            long longAddress = arrayToLongIp(localIpAddress);
                            long mask = (1 << (32-networkPrefixLength) ) -1;
                            longAddress &= ~mask;
                            if (debug)
                                System.out.printf( "Scanning %s/%d from IF: %s\n", arrayIpToString(localIpAddress), networkPrefixLength, ni.getDisplayName() );
                            for ( int ix=0; ix < ( (1<<(32-networkPrefixLength)) )-1 && !bFoundServer; ix++ ) {
                                byte[] ip;
                                longAddress++;
                                
                                // if there is an active system at that ip address...
                                ip = longIpToArray(longAddress);
                                try {
                                    InetAddress inAddr = InetAddress.getByAddress(ip);
                                    if (inAddr.isReachable(PING_TIMEOUT)) { // TODO adjustable timeout?

                                        // if our registry port is active
                                        // (if socket creation successful)
                                        Socket sock = new Socket();
                                        SocketAddress sockAddr = new InetSocketAddress( inAddr, Registrar.PORT);
                                        sock.connect( sockAddr, PORT_SCAN_TIMEOUT );
                                        
                                        // WE FOUND THE SOLOMON SERVER (and the right interface to use, too)
                                        serverAddr = String.format( "//%d.%d.%d.%d:%d/", 
                                            ((long)ip[0])&0xFF, 
                                            ((long)ip[1])&0xFF, 
                                            ((long)ip[2])&0xFF, 
                                            ((long)ip[3])&0xFF, 
                                            Registrar.PORT );
                                        bFoundServer = true;
                                    }
                                    
                                // OK to ignore socket creation failure: scanned system not up
                                } catch (Exception e) {}
                            } // for
                            
                            // if our port is active, we've found the server
                        
                        } // if
                        
                    } // for
                                        
                } // if
            } // for

        } // try
        catch (Exception e ) {}

        return serverAddr;
    }
    
    private byte[] longIpToArray( long longAddr ) {
        return new byte[] {
            (byte) ((longAddr>>24)&0xFF),
            (byte) ((longAddr>>16)&0xFF),
            (byte) ((longAddr>> 8)&0xFF),
            (byte)  (longAddr     &0xFF) };
    }
    
    private long arrayToLongIp( byte[] byteAddr ) {

        return   ( ((long)(byteAddr[0]) &0xFF) <<24) 
               | ( ((long)(byteAddr[1]) &0xFF) <<16) 
               | ( ((long)(byteAddr[2]) &0xFF) << 8) 
               |   ((long)(byteAddr[3]) &0xFF);
    }
    private String arrayIpToString( byte[] ip ) {
        return String.format( "%d.%d.%d.%d", 
            ((long)ip[0])&0xFF, 
            ((long)ip[1])&0xFF, 
            ((long)ip[2])&0xFF, 
            ((long)ip[3])&0xFF);
    }
    
    /**
     * Retrieve the singleton instance for this class, creating one 
     * if necessary.
     * 
     * @return the instance
     */
    public static Server getInstance()
    {
        if (_instance==null)
            _instance = new Server();
        return _instance;
    }
    
    public String getProperty( String key ) {
        return prop.getProperty( key );
    }
    
    public void setProperty( String key, String value ) {
        FileWriter propWriter = null;
        prop.setProperty( key, value );
        try {
            propWriter = new FileWriter(CLIENT_PROPERTIES_FILENAME);
            prop.store( propWriter, PROP_FILE_HEADER_COMMENT );
        }
        catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (propWriter!=null) {
            try {
                propWriter.close();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Locate (if necessary) and register our client with the Solomon Server.  
     * This should be the first call to this client-side Server proxy.
     * 
     * @param teamName name of this client, unique per IP address
     * @param response listener for server callbacks.  May not be null for 
     * this class, but may have interface methods which return an 
     * 'unimplemented' status
     * @return RC_OK if registration successful
     */
    public ResultCode register( String teamName, IResponse response )
    {
        ResultCode rc = RC_OK;

        // if we don't have address of server, search for it (auto-discovery 
        // via port scan of the local subnet), save it as a property
        String serverAddr = prop.getProperty(SERVER_ADDRESS_KEY);
        if (serverAddr==null) {
            serverAddr = findSolomonServer();
            if (serverAddr!=null) {
                setProperty( SERVER_ADDRESS_KEY, serverAddr );
            }
            else {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "SERVER NOT FOUND" );
                return ResultCode.E_SERVER_NOT_FOUND;                
            }
        }

        // try looking up and registering with the Solomon Server
        try
        {
            IRegistrar registrar = (IRegistrar) Naming.lookup( serverAddr + "Registrar"); // TODO global name
            conn = registrar.register( teamName, response );
            if (conn!=null) {
                playerID = conn.getID();
                if (playerID==0) {
                    rc = E_REGISTRATION_FAILED;
                    conn.terminateConnection(E_REGISTRATION_FAILED);
                    conn = null;
                }
            }
            else
                rc =  E_REGISTRATION_FAILED;
        }
        catch (MalformedURLException e)
        {
            System.out.println( "ClientLib URL failed binding the Registrar for team '"+teamName+"': "+e);
            rc = E_SERVER_NOT_FOUND;             
            
        }
        catch (NotBoundException e)
        {
            System.out.println( "ClientLib failed binding the Registrar for team '"+teamName+"': "+e);
            rc = E_SERVER_NOT_FOUND;             
        }
        catch (RemoteException e)
        {
            // TODO log extraordinary circumstances
            System.out.println( "ClientLib failed registering team '"+teamName+"': "+e);
            rc = E_SERVER_NOT_FOUND; 
        }

        // if we didn't register successfully, clear the server address property
        if (rc!=RC_OK) {
            setProperty( SERVER_ADDRESS_KEY, null );
        }
        
        return rc;
    }
    
    public void setNumberOfRounds( int numberOfRounds ) {
        this.numberOfRounds = numberOfRounds;
    }
    
    public int getNumberOfRounds() {
        return numberOfRounds;
    }
    
    public ArrayList<PlayerEntry> getAvailablePlayersList()
    {
        ArrayList<PlayerEntry> list = new ArrayList<PlayerEntry>();
        
        if (conn!=null) {
            try
            {
                list =  conn.getPlayerList();
            }
            catch (RemoteException e)
            {
                System.out.println( "ClientLib conn.getAvailablePlayerList returned " + e );
            }
        }
        return list;
    }
    
    public ResultCode doGesture( Gesture g )
    {
        ResultCode rc = RC_OK;
        int retryCount = DO_GESTURE_MAX_RETRIES;
        final EnumSet<ResultCode> FATAL_RC 
            = EnumSet.of( RC_MATCH_ENDED,
                          E_UNRECOGNIZED_PLAYER,
                          E_MATCH_ENDED,
                          E_SERVER_DOWN,
                          E_NO_CONNECTION );

        try
        {
            if (conn==null)
                rc = E_NO_CONNECTION;
            else do {
                rc = conn.doGesture(g);
                if (rc!=RC_OK) {
                    try { Thread.sleep(SYNCHRONIZING_LATENCY); }
                    catch (InterruptedException e) {}
                }
            } while (rc!=RC_OK 
                    && !FATAL_RC.contains(rc)
                    && --retryCount>0 );
            if (retryCount==0) {
                rc = E_LOSS_OF_SYNCHRONIZATION;
            }
        }
        catch (RemoteException e)
        {
            // TODO LOG extraordinary circumstances
        }
        return rc;
    }
    
    public Scorecard getScore()
    {
        Scorecard score = null;
        ResultCode rc = RC_OK;
        int retryCount = GET_SCORE_MAX_RETRIES;
        final EnumSet<ResultCode> FATAL_RC
                = EnumSet.of( RC_MATCH_ENDED, 
                              E_SCORE_NOT_AVAILABLE,
                              E_NO_CONNECTION );
        try
        {
            if (conn==null)
                rc = E_NO_CONNECTION;
            else do {
                score = conn.getScorecard();
                if (score==null) 
                    rc = E_SCORE_NOT_AVAILABLE;
                else {
                    rc = score.rc;
                    if (rc!=RC_OK) {
                        try { Thread.sleep(SYNCHRONIZING_LATENCY); }
                        catch (InterruptedException e) {}
                    }
                }
            } while (rc!=RC_OK 
                    && !FATAL_RC.contains(rc) 
                    && --retryCount>0 );
            if (retryCount==0)
                rc = E_LOSS_OF_SYNCHRONIZATION;
        }
        catch (RemoteException e)
        {
            // TODO log extraordinary circumstance
        }
        if (score!=null)
            score.rc = rc;
        return score;
    }
    
    
    public ResultCode requestRemoteMatch( int playerID, 
                                          int maxNumberOfRounds )
    {
        ResultCode rc = RC_OK;
        
        try
        {
            if (conn==null)
                rc = E_NO_CONNECTION;
            else
                rc = conn.requestRemoteMatch( playerID, maxNumberOfRounds );
        }
        catch (RemoteException e)
        {
            // TODO log extraordinary circumstance
        }
        
        return rc;
    }
    
    public void terminateConnection( ResultCode rc ) 
    {
        try
        {
            if (conn!=null)
                conn.terminateConnection(rc);
            conn = null;
        }
        catch (Exception e)
        {
            // we're forgiving!
        }
    }
    
    public void abortingMatch( ResultCode rc )
    {
        try
        {
            if (conn!=null)
                conn.abortingMatch(rc);
        }
        catch (Exception e)
        {
            // TODO: handle Server.terminateMatch exception smartly
        }
    }
    
    public void addPlayerListListener( IPlayerListListener listener ) {
        if (conn!=null) {
            try {
                conn.addPlayerListListener(listener);
            } catch (Exception e) {
                System.out.println( "ClientLib: while trying to add PlayerListListener: " + e );
            }
        }
    }
    
    public void removePlayerListListener( IPlayerListListener listener ) {
        if (conn!=null) {
            try {
                conn.removePlayerListListener(listener);
            } catch (Exception e) {
                System.out.println( "ClientLib: while trying to remove PlayerListListener: " + e );
            }
        }        
    }
}
