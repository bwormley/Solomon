package solomonClientLib;


import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.EnumSet;
import solomonserver.*;

import static solomonserver.ResultCode.*;

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
    
    /**
     * This is the delay for a non-fatal error from the server before 
     * retrying the command.  This allows the protocol to resynchronize with 
     * the remote player without pummeling  the server.
     */
    static final int SYNCHRONIZING_LATENCY = 0;//50;
    
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
    
    private Server()
    {
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
            IRegistrar registrar = (IRegistrar) Naming.lookup("Registrar"); // TODO global name
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
                if (score==null) { rc = E_SCORE_NOT_AVAILABLE; }
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
    
    public void abortMatch( ResultCode rc )
    {
        try
        {
            if (conn!=null)
                conn.abortMatch(rc);
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
