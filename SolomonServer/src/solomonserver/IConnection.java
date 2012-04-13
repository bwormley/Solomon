package solomonserver;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface IConnection extends Remote {
    
    ResultCode requestRemoteMatch( int playerID, 
                                   int maxNumberOfRounds ) 
            throws RemoteException;
    
    void terminateConnection( ResultCode rc ) 
            throws RemoteException;
    
    ResultCode doGesture( Gesture g ) 
            throws RemoteException;
    
    /**
     * called by the Client, signaling the intention of aborting the match.  
     * The Server will immediately terminate the match, resetting both player 
     * to AVAILABLE_FOR_PLAY.
     * 
     * @param reasonCode
     * @throws RemoteException 
     */
    void abortingMatch( ResultCode reasonCode ) 
            throws RemoteException;
    
    Scorecard getScorecard() 
            throws RemoteException;
    
    ArrayList<PlayerEntry> getPlayerList() 
            throws RemoteException;
    
    void addPlayerListListener( IPlayerListListener listener )
            throws RemoteException;
    
    void removePlayerListListener( IPlayerListListener listener )
            throws RemoteException;
    
    int getID()
            throws RemoteException;
    
    void keepAlive()
            throws RemoteException;
}
