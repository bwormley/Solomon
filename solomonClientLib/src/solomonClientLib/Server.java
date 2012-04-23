package solomonClientLib;


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

/**
 * This singleton class encapsulates all synchronous communication to and 
 * from the server.  (Asynchronous communication (i.e., the IResponse 
 * interface) is handled by the Player class directly.  The primary value 
 * added by this class is that it encapsulates finding, registering, and 
 * unregistering with the server. 
 */
public class Server  {
    
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
    private Properties prop;
    private String serverAddr;
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
            Logger.getLogger(Server.class.getName()).log(Level.WARNING, null, ex);
        }
        if (propReader!=null) {
            try {
                propReader.close();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.WARNING, null, ex);
            }
        }

        // find Solomon Server, from properties or from a port scan
        serverAddr = prop.getProperty(SERVER_ADDRESS_KEY);
        if (serverAddr==null) {
            serverAddr = findSolomonServer();
            if (serverAddr!=null) {
                FileWriter propWriter = null;
                prop.setProperty( SERVER_ADDRESS_KEY, serverAddr );
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
        }
    }
        
    private String findSolomonServer() {
        
        String serverAddr = null;;

        // port scan for the registry on the local subnet
            
        // get local ip address
        InetAddress localHost = null;
        byte[] localIpAddress = null;
        try {
            localHost = Inet4Address.getLocalHost();
            localIpAddress = localHost.getAddress();
            System.out.printf( "local host: %d.%d.%d.%d\n", localIpAddress[0],
                     + localIpAddress[1],
                     + localIpAddress[2],
                     + localIpAddress[3]
                    );
        } catch (UnknownHostException ex) {             
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // get local subnet mask(s)
        NetworkInterface networkInterface = null;
        try {
            networkInterface = NetworkInterface.getByInetAddress(localHost);
        } catch (SocketException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }

        int subnetMaskLength = 1;
        for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
            System.out.printf( "addr: %s   prefix len:%d   %s%s%s%s\n", 
                    address.getAddress(), 
                    address.getNetworkPrefixLength(),
                    address.getAddress().isAnyLocalAddress()?"isAnyLocalAddress ":"",
                    address.getAddress().isLinkLocalAddress()?"isLinkLocalAddress ":"",
                    address.getAddress().isLoopbackAddress()?"isLoopbackAddress ":"",
                    address.getAddress().isSiteLocalAddress()?"isSiteLocalAddress ":""
                    );
            if (address.getAddress().isSiteLocalAddress()) {
                subnetMaskLength = address.getNetworkPrefixLength();
//                break;
            }
        }
            
        // check localHost first: is registry local?
            
        // scan for the rmiregistry in the subnet
                        
System.out.printf( "%x %x %x %x\n", localIpAddress[0], localIpAddress[0]<<8, localIpAddress[0]<<16, localIpAddress[0]<<24 );
        long longAddress = 
                  ( ((long)(localIpAddress[0]) &0xFF) <<24) 
                | ( ((long)(localIpAddress[1]) &0xFF) <<16) 
                | ( ((long)(localIpAddress[2]) &0xFF) << 8) 
                |   ((long)(localIpAddress[3]) &0xFF);
        System.out.printf( "localAddress is %x\n", longAddress );
        long mask = (1 << (32-subnetMaskLength) ) -1;
        System.out.printf( " with mask of %x and ~mask of %x\n", mask, ~mask );
        longAddress &= ~mask;
        System.out.printf( "   and after mask, is %x\n", longAddress );
        byte[] ip = new byte[4];
        for ( int ix=0; ix < ( (1<<(32-subnetMaskLength)) )-1; ix++ ) {
            longAddress++;
            ip[0] = (byte) ((longAddress>>24)&0xFF);
            ip[1] = (byte) ((longAddress>>16)&0xFF);
            ip[2] = (byte) ((longAddress>> 8)&0xFF);
            ip[3] = (byte)  (longAddress     &0xFF);
            try {
                InetAddress inAddr = InetAddress.getByAddress(ip);
//                System.out.printf( "trying %08x   aka   %d.%d.%d.%d   aka   %s\n", longAddress, ip[0], ip[1], ip[2], ip[3], inAddr );
                if (inAddr.isReachable(10)) { // TODO adjustable timeout, AND manually settable IP/port
                    System.out.printf( "trying %08x   aka   %d.%d.%d.%d   aka   %s    ALIVE\n", longAddress, ip[0], ip[1], ip[2], ip[3], inAddr );
                    Socket sock = new Socket();
                    SocketAddress sockAddr = new InetSocketAddress( inAddr, 1099);
                    sock.connect( sockAddr, 100 );
                    System.out.printf( "    our registry port %d of the ip address %d.%d.%d.%d is in use\n", Registrar.PORT, ip[0], ip[1], ip[2], ip[3] );
                    serverAddr = String.format( "//%d.%d.%d.%d:%d/", 
                            ((long)ip[0])&0xFF, 
                            ((long)ip[1])&0xFF, 
                            ((long)ip[2])&0xFF, 
                            ((long)ip[3])&0xFF, 
                            Registrar.PORT );
//                   break;
                }
            } catch (Exception e) {}
        }
        return serverAddr;
    }
    
//    finalize()
//    {
//        super.finalize();
//    }
    
    public static Server getInstance()
    {
        if (_instance==null)
            _instance = new Server();
        return _instance;
    }

    public ResultCode register( String teamName, IResponse response )
    {
        ResultCode rc = RC_OK;
        try
        {
            // find the server and register with it
            IRegistrar registrar = (IRegistrar) Naming.lookup( serverAddr+"Registrar"); // TODO global name
            conn = registrar.register( teamName, response );
            if (conn==null)
                rc =  E_REGISTRATION_FAILED;
            else {
                    playerID = conn.getID();
                    if (playerID==0) {
                        rc = E_REGISTRATION_FAILED;
                        conn.terminateConnection(E_REGISTRATION_FAILED);
                        conn = null;
                    }
            }
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
        return rc;
    }
    
    public void setNumberOfRounds( int numberOfRounds ) {
        this.numberOfRounds = numberOfRounds;
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
