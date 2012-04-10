/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package solomonserver;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

/**
 *
 * @author bwormley
 */
public class Administrator 
        extends UnicastRemoteObject 
        implements IAdministrator {
    
    Administrator()
            throws RemoteException {
    }
    
    @Override
    public ArrayList<PlayerEntry> getPlayerList() throws RemoteException
    {
        return ConnectionTable.getInstance().getPlayerList( null );
    }

    @Override
    public void stopServer(String challenge) throws RemoteException {
        
        try {
            
            // TODO: notify clients of shutdown
            
            System.out.println("Server: locating registry");
            Registry registry = LocateRegistry.getRegistry();
            
            System.out.println("Server: UNexporting Registrar");
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (Exception e) {
                System.out.println( "Server: informational (ignored): "+e);
            }
            
            System.out.println("Server: UNbinding IRegistrar");
            registry.unbind("Registrar"); // TODO: make this global string
            
            System.out.println( "Server: Registrar is down");
        } catch (Exception e) {
            System.out.println("Server: " +e);
        }

    }
    
    @Override
    public void killConnection(int PlayerID) throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void killMatch(int matchID) throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void addPlayerListListener(IPlayerListListener listener) throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void remotePlayerListListener(IPlayerListListener listener) throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
