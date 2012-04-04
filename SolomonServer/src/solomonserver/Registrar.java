package solomonserver;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

public class Registrar extends UnicastRemoteObject implements IRegistrar {
    
    /**
     * A simple string to challenge anyone calling an administrative interface
     */
    static String auth = "";
    
    /**
     * the singleton instance of this class
     */
    static private Registrar registrar = null;
    
    private Registrar() throws RemoteException 
    {
    }
    
    /**
     * Construct a remote object
     * 
     * @throws RemoteException 
     */
    private void startServer() throws RemoteException 
    {
        
        // TODO: if not running, programmatically start RMIRegistry with 
        // java.rmi.registry.LocateRegistry.createRegistry()
/*
        System.out.println("Server: in Registrar constructor");
        try {
            Registrar.setLog(System.out);
        } catch (Exception e) {
            System.out.println( "Server: problem directing error log: " + e);
        }
*/
        // start logger
        
        // register this server object in RMI registry
        try {
//            System.out.println("Server: UNexporting old Registrar");
//            try {
//                UnicastRemoteObject.unexportObject(registrar, true);
//            } catch (Exception e) {
//                System.out.println( "Server: informational (ignored): "+e);
//            }
            
            System.out.println("Server: UNexporting Registrar");
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (Exception e) {
                System.out.println( "Server: informational (ignored): "+e);
            }
            
            System.out.println("SOLOMON SERVER 0.9.0\nServer: exporting Registrar");
            IRegistrar stub = (IRegistrar)UnicastRemoteObject.exportObject(registrar,0);
            
            
            System.out.println("Server: locating registry");
            Registry registry = LocateRegistry.getRegistry();
            
            System.out.println( "Server: UNbinding previous registrar");
            try { registry.unbind("Registrar"); } catch (Exception e) {}
            
            System.out.println("Server: binding IRegistrar");
            registry.bind("Registrar",stub); 
            // TODO: make this global string
            // TOD: rebind?
            
            System.out.println( "Server: Registrar is up");
        } catch (Exception e) {
            System.out.println("Server: " +e);
        }
    }
    
    /**
     * Main entry point
     * 
     * @param args 
     */

    public static void main(String[] args) {
        
        // capture administrative password
        if (args.length>0)
            auth = args[0];
        
        // construct our singular existence
        try {
            registrar = new Registrar();
            registrar.startServer();
        } catch (Exception e) {
            System.out.println( "Server: error initializing: " + e );
        }
        
    }

    
    /**
     * Register this client
     * 
     * @param teamName
     * @return
     * @throws RemoteException 
     */
    @Override
    public IConnection register( String teamName, IResponse response ) 
            throws RemoteException 
    {
        String origin = "";
        try {
            origin = Registrar.getClientHost();
//            Registrar.setLog(System.out);
        } catch (Exception e) {
            System.out.println( "Server: problem getting client identity: " + e);
        }

        System.out.println( "Server: registering team '" + teamName + "' client at " + origin ); 
        
        Connection conn = new Connection( teamName, response, origin );
        ConnectionTable.getInstance().addPlayer(conn);
        IConnection rc  = (IConnection) conn;
        System.out.println("Server: Registrar.register() preparing to return IConnection " + rc.getClass() );
        return rc;
    }

    /* ************************
     * ADMINISTRATION INTERFACE 
     * ************************ */

    @Override
    public IAdministrator getAdminInterface(String challenge) throws RemoteException {
        return new Administrator();
    }

}

