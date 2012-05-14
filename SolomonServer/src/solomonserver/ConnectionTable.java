package solomonserver;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;

import static solomonserver.ResultCode.*;

public class ConnectionTable 
    implements ActionListener {
    
    final private static Logger l = Logger.getLogger("com.cs151.solomon.server");

    public enum Filter {
        AVAILABLE, /* available for play, excluding me */
        NONE        /* full table dump */
    };
    
    /**
     * Maximum number of connections before the server declines requests
     */
    static final int MAX_CONNECTIONS = 16;
    
    private ConnectionTable()
    {
        table = new HashMap<Integer,Connection>();
        
        // spawn the zombie killer thread
        zombieKiller.start();
    }
    
    /**
     * frequency of invocation of thread to check every connection's 
     * keepalive status, in milliseconds
     */
    
    private final int ZOMBIE_HEARTBEAT = 5000;
    
    /**
     * time after which we consider a connection dead, in seconds
     */
    private final int ZOMBIE_DELAY = 30;
    
    /**
     * Timer object for repeating invocation of thread that searches and 
     * removes zombie connections
     */
    private Timer zombieKiller = new Timer(ZOMBIE_HEARTBEAT,this);
    
    /**
     * method executed on the thread that periodically searches for 
     * zombie connections
     * 
     * @param evt unused
     */
    @Override
    public void actionPerformed(ActionEvent evt) {
        // wait until we can acquire lock on this table
        // determine time at which we give up on a connection
        Calendar now = Calendar.getInstance();
        now.add(Calendar.SECOND,-ZOMBIE_DELAY);
        Date presumedDead = new Date(now.getTimeInMillis());
        Iterator<Map.Entry<Integer,Connection>> ix = table.entrySet().iterator();
        while (ix.hasNext()) {
            Map.Entry<Integer,Connection> entry = ix.next();
            if (entry.getValue().lastKeepaliveReceived.compareTo(presumedDead)<0) {
                entry.getValue().changeState(ConnectionState.ZOMBIE);
                // TODO: notify (abort connect, both sides of a match, signal match termination for logging
            }
        }
    }
    
    private static HashMap<Integer,Connection> table = null;
    private static ConnectionTable _instance = null;
    
    public static ConnectionTable getInstance()
    {
        if (_instance==null) {
            _instance = new ConnectionTable();
        }
        return _instance;
    }
    
    public Connection getPlayer( int playerID )
    {
        return table.get(playerID);
    }
        
    /**
     * Add a player to the connection table.
     * 
     * If the player already present, send the original connection a 
     * terminate connection message, then add this new player.  The idea is 
     * that the client probably crashed.  In any case, we only allow one 
     * client (unique team name) per ID address.
     * 
     * @param player
     * @return 
     */
    public IConnection addPlayer( Connection player ) 
    {
        l.entering("ConnectionTable","addPlayer");
        
        // remove old entry if a duplicate
        Connection duplicatePlayer = findPlayer( player.getTeamName(), player.getOrigin());
        if (duplicatePlayer!=null)
        {
            l.log(Level.FINE, "removing duplicate first");
            try { duplicatePlayer.terminateConnection(E_REDUNDANT_PLAYER); } catch (Exception e) {}
            removePlayer( duplicatePlayer );
        }
        
        // decline if we are at maximum utilization
        if (table.size()>MAX_CONNECTIONS)
            return null;
        
        // now add in the new guy
        table.put(player.getID(),player);
        l.log(Level.INFO, "adding player {0}", player );
        notifyListeners( new ListAction( ListAction.Action.ADD, new PlayerEntry(player)) );

        return (IConnection)player;
    }
    
    /**
     * Find an instance of a player in the connection table, probably 
     * a duplicate, since we're only looking if we have complete information 
     * about him.  Normally player lookup is accomplished by using the 
     * playerID.
     * 
     * @param teamName - name of the player
     * @param origin - IP address of the client
     * @return connection in the table
     */
    public Connection findPlayer( String teamName, String origin )
    {
        // iterate through the table (ugly!) to find an exact match
        Iterator i = table.entrySet().iterator();
        while (i.hasNext()) 
        {
            Map.Entry pair = (Map.Entry) i.next();
            Connection player = (Connection)pair.getValue();
            if (player.getTeamName().equals(teamName) && player.getOrigin().equals(origin))
                return player;
        }
        return null;
    }
    
    /**
     * Remove a player from the table.  Gracefully 
     * returns if the player is not in the table.
     * 
     * @param player the unique ID of the player to remove.
     */
    public void removePlayer( Connection player )
    {
        l.entering("ConnectionTable","removePlayer");
        
        table.remove( player.getID() );
        notifyListeners( new ListAction(ListAction.Action.REMOVE, new PlayerEntry(player) ));
    }
    
    /**
     * 
     * @param filter
     * @param inquirer
     * @param listener
     * @return 
     */
    public ArrayList<PlayerEntry> getPlayerList( 
            Connection inquirer )
    {
        l.entering("ConnectionTable","getPlayerList");
        
        ArrayList<PlayerEntry> list = new ArrayList<PlayerEntry>();
        Iterator i = table.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry pair  = (Map.Entry)i.next();
            int id = (Integer)pair.getKey();
            Connection player = (Connection)pair.getValue();
            PlayerEntry e = new PlayerEntry( player.getTeamName(), player.getOrigin(), id, player.getState() );
            list.add(e);
        }
        return list;
    }
    
    /* *************************
     * LISTENER HELPER FUNCTIONS
     * ************************* */
    
    private ArrayList<IPlayerListListener> listeners = new ArrayList<IPlayerListListener>();
    
    void addListener( IPlayerListListener listener ) {
        if (listener!=null)
            listeners.add(listener);
    }
    void removeListener( IPlayerListListener listener ) {
        listeners.remove(listener);
    }
    public void notifyListeners( ListAction event ) {
        for ( IPlayerListListener listener : listeners )
            try {
                listener.notifyAction(event);
            } catch (Exception e) {
                l.log(Level.WARNING, "error sending ListAction",e);
            }
    }
}
