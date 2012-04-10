package solomonClientLib;


import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import solomonserver.*;
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
        // get the whole list
        ArrayList<PlayerEntry> tempList = Server.getInstance().getAvailablePlayersList();
        if (!bListening) {
            Server.getInstance().addPlayerListListener(this);
            bListening = true;
        }
        
        // only include available players who are not us
        ArrayList<PlayerEntry> tempList2 = new ArrayList<PlayerEntry>();
        for ( PlayerEntry entry: tempList ) {
            if (entry.state==ConnectionState.AVAILABLE_FOR_PLAY
                    && entry.id!=Server.getInstance().playerID)
                tempList2.add(entry);
        }
        list = tempList2;
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
        // Note: it might not be in our list
        // note: might be a CHANGE to not AVAILABE: for us, means REMOVE
        if (e.event==ListAction.Action.REMOVE 
                || (e.event==ListAction.Action.CHANGE 
                    && e.player.state!=ConnectionState.AVAILABLE_FOR_PLAY)) {
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
        // note: only add if its an AVAILABLE_FOR_PLAY player
        // note: might be a CHANGE to AVAILABLE_FOR_PLAY, which for us means ADD
        else if ((e.event==ListAction.Action.ADD || e.event==ListAction.Action.CHANGE)
                && e.player.state==ConnectionState.AVAILABLE_FOR_PLAY) {
            list.add(e.player);   
            index = list.size() - 1;
            if (listener!=null) {
                if (e.event==ListAction.Action.ADD) {
                    ListDataEvent lde = new ListDataEvent( this, ListDataEvent.INTERVAL_ADDED, index, index );
                    listener.intervalAdded(lde);
                }
            }
        }
        
        // if a change, only 
        
        return RC_OK;
    }
}
