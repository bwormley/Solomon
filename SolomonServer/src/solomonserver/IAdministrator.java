package solomonserver;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 *
 * @author bwormley
 */
public interface IAdministrator extends Remote 
{
    ArrayList<PlayerEntry> getPlayerList()
            throws RemoteException;
    
    void addPlayerListListener( IPlayerListListener listener ) 
            throws RemoteException;
    
    void remotePlayerListListener( IPlayerListListener listener )
            throws RemoteException;
    
    void stopServer( String auth ) 
            throws RemoteException;
    
    void killConnection( int PlayerID )
            throws RemoteException;
    
    void killMatch( int matchID )
            throws RemoteException;

}
