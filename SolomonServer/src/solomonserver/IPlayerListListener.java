/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package solomonserver;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author bwormley
 */
public interface IPlayerListListener extends Remote {
    
    ResultCode notifyAction( ListAction e )
            throws RemoteException;
    
}
