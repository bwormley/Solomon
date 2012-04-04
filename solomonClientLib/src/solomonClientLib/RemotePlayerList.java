package solomonClientLib;


import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import solomonserver.IPlayerListListener;
import solomonserver.ListAction;
import solomonserver.PlayerEntry;
import solomonserver.ResultCode;
import static solomonserver.ResultCode.*;

/**
 *
 * @author bwormley
 */
public class RemotePlayerList extends UnicastRemoteObject
        implements ListModel, IPlayerListListener {
        
    private RemotePlayerList()
            throws RemoteException
    {
    }
    /**
     * Override clone to ensure we stay a singleton
     * 
     * @return never called
     * @throws CloneNotSupportedException 
     */
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        throw new CloneNotSupportedException();
    }
    

    private static RemotePlayerList _instance = null;
    
    public static synchronized RemotePlayerList getInstance()
    {
        if (_instance==null) {
            try {
                _instance = new RemotePlayerList();
            } catch (Exception e) {
                System.out.println( "ClientLib: in RemotePlayerList constructor: " + e );
            }
        }
        
        return _instance;
    }
    
    
    ArrayList<PlayerEntry> list;
    boolean bListening = false;
    
    public void refresh()
    {
        list = Server.getInstance().getAvailablePlayersList();
        if (!bListening) {
            Server.getInstance().addPlayerListListener(this);
            bListening = true;
        }
    }

    /* *********************************
     * INTERFACE FOR SWING LISTBOX MODEL
     * ********************************* */
    
    ListDataListener listener = null;
    
    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public Object getElementAt(int i) {
        return list.get(i);
    }

    /**
     * Implementation for ListDataListener for informing 
     * JListbox of model changes
     * 
     * @param ll 
     */
    @Override
    public void addListDataListener(ListDataListener ll) {
        listener = ll;
    }

    @Override
    public void removeListDataListener(ListDataListener ll) {
        listener = null;
    }
    
    /* ***********************************************
     * INTERFACE FOR SOLOMONSERVER.IPLAYERLISTLISTENER
     * *********************************************** */

    /**
     * Implementation for solomonserver.IPlayerListListener, 
     * used to keep our remote player list, and the Swing 
     * JListBox up-to-date
     * 
     * @param e - event concerning the connection table
     * @return
     * @throws RemoteException 
     */
    @Override
    public ResultCode notifyAction(ListAction e) 
            throws RemoteException {
        
        int index = -1;
        
        // if a removal, find and remove it, and report it to JListBox
        if (e.event==ListAction.Action.REMOVE) {
            for ( int ix=0; ix<list.size(); ix++ ) {
                if (e.player.teamName.equals(list.get(ix).teamName)
                    && e.player.origin.equals(list.get(ix).origin)) {
                    index = ix;
                    break;
                }
            }
            if (index!=-1) {
                list.remove(index);
                if (listener!=null) {
                    ListDataEvent lde = new ListDataEvent( this, ListDataEvent.INTERVAL_REMOVED, index, index );
                    listener.intervalRemoved(lde);
                }
            }
        }
        
        // if an addition, add it, and report it to JListBox
        else if (e.event==ListAction.Action.ADD) {
            list.add(e.player);   
            index = list.size() - 1;
            if (listener!=null) {
                if (e.event==ListAction.Action.ADD) {
                    ListDataEvent lde = new ListDataEvent( this, ListDataEvent.INTERVAL_ADDED, index, index );
                    listener.intervalAdded(lde);
                }
            }
        }
        
        return RC_OK;
    }
}
