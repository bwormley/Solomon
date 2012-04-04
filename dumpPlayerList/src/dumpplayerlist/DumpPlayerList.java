/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dumpplayerlist;

import java.rmi.Naming;
import java.util.ArrayList;
import java.util.Iterator;
import solomonserver.IAdministrator;
import solomonserver.IRegistrar;
import solomonserver.PlayerEntry;

/**
 *
 * @author bwormley
 */
public class DumpPlayerList {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        DumpPlayerList d = new DumpPlayerList();
        d.dump(args);
    }
    
    public void dump(String[] args) {
            ArrayList<PlayerEntry> list;
            Iterator<PlayerEntry> i;
 
         try {
            IRegistrar registrar = (IRegistrar) Naming.lookup("Registrar");  
            IAdministrator admin = registrar.getAdminInterface("");
            do {
                list = admin.getPlayerList();
                i = list.iterator();
                while (i.hasNext()) {
                    PlayerEntry p = i.next();
                    System.out.printf( "   %8s  %08x %14s %s\n", p.teamName, p.id, p.origin, p.state );
                }
                Thread.sleep(1000);
                System.out.println("--------------------------------");
            } while (true);
         } catch (Exception e) {System.out.println(e);}
     }
    
}
