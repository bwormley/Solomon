package solomonClientLib;


import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
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
        }
        catch (Exception e)
        {
            // TODO log extraordinary circumstances
            System.out.println( "Solomon Client Lib failed registering team '"+teamName+"': "+e);
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
                list =  conn.getAvailablePlayerList();
            }
            catch (RemoteException e)
            {
                System.out.println( "Client.Lib conn.getAvaillablePlayerList returned " + e );
            }
        }
        return list;
    }
    
    public ResultCode doGesture( Gesture g )
    {
        ResultCode rc = RC_OK;
        
        try
        {
            if (conn==null)
                rc = E_NO_CONNECTION;
            else
                rc = conn.doGesture(g);
        }
        catch (RemoteException e)
        {
            // TODO log extraordinary circumstance
        }
        return rc;
    }
    
    public Scorecard getScore()
    {
        Scorecard score = null;
        try
        {
            if (conn!=null)
                score = conn.getScorecard();
        }
        catch (RemoteException e)
        {
            // TODO log extraordinary circumstance
        }
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
