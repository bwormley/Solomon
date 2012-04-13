package solomonserver;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IResponse extends Remote {

    ResultCode requestToInitiateMatch( String teamName, int maxNumberOfRounds ) throws RemoteException;
    
    void abortMatch( ResultCode rc ) throws RemoteException;
    
    void abortConnection( ResultCode rc ) throws RemoteException;
    
    ResultCode notifyScore( Scorecard score ) throws RemoteException;

    // TODO: better as separate interfaces, for max flexibility for the clients? no: this is used by SolomonClient, not the App
}
