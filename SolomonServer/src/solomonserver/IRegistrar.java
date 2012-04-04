package solomonserver;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 *
 * @author bwormley
 */
public interface IRegistrar extends Remote {
    
    IConnection register( String teamName, IResponse response ) 
            throws RemoteException;
    
    IAdministrator getAdminInterface( String challenge )
            throws RemoteException;
    
}
