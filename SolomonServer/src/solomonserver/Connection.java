package solomonserver;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import static solomonserver.ResultCode.*;

public class Connection extends UnicastRemoteObject implements IConnection, Serializable {
    private static final Logger l = Logger.getLogger("com.cs151.solomon.server");
    
    private String teamName = null;
    private String origin = null;
    private IResponse response;
    private Match match = null;
    public Date lastKeepaliveReceived = new Date();
    private boolean bNotAcceptingMatches = false;

    private ConnectionState state = ConnectionState.DISCONNECTED;
    
        
    public Connection( String teamName, IResponse response, String origin )
            throws RemoteException
    {
        l.entering("Connection","Constructor",teamName);
        this.teamName = teamName;
        this.response = response;
        this.origin   = origin;
        state = ConnectionState.AVAILABLE_FOR_PLAY;
    }
    
    ConnectionState getState()
    {
        return state;
    }
        
    public String getTeamName()
    {
        return teamName;
    }
    public String getOrigin()
    {
        return origin;
    }
    
    /**
     * Return a unique ID for this connection
     * 
     * This maps an ID to the connection object that is unique, in order to 
     * make it difficult for a client to hijack another client's connection.  
     * Team names may not be unique.  This ID is unique.
     * 
     * @return unique ID
     */
    @Override
    public int getID()
    {
        return this.hashCode();
    }
    
    @Override
    public void keepAlive()
            throws RemoteException
    {
        lastKeepaliveReceived = new Date();
    } 
           
    
    @Override
    public void terminateConnection( ResultCode rc ) 
            throws RemoteException {
// terminate match, other connection, my 
    }

    @Override
    public ResultCode requestRemoteMatch( int playerID, int maxNumberOfRounds ) 
            throws RemoteException 
    {
        l.entering("Connection","requestRemoteMatch",playerID);
        ResultCode rc = RC_OK;
        
        if (state!=ConnectionState.AVAILABLE_FOR_PLAY)
            return E_WRONG_STATE;
        
        changeState( ConnectionState.REQUEST_IN_PROGRESS );
        
        Connection player2 = ConnectionTable.getInstance().getPlayer( playerID );
        if (player2==null) {
            changeState( ConnectionState.AVAILABLE_FOR_PLAY );
            l.log(Level.WARNING,"match requested, but opponent not in table",playerID);
            rc = E_UNRECOGNIZED_PLAYER;
        }
            
        else {
            if (match!=null) {
                MatchTable.getInstance().removeMatch(this,match);
            }
            match = new Match( this, player2, maxNumberOfRounds );
            rc = player2.requestMatch( this, match, maxNumberOfRounds );
            if (rc==RC_OK) {
                MatchTable.getInstance().addMatch(match);
                changeState( ConnectionState.MATCH_IN_PLAY );
                l.log(Level.INFO,"BEGIN match between {0} and {1}",new Object[] {this,player2});
            }
            else {
                match = null;
                changeState( ConnectionState.AVAILABLE_FOR_PLAY );
                l.log(Level.INFO,"REFUSED match between {0} and {1}",new Object[] {this,player2});
            }
        }
        return rc;
    }
    
    // TODO: result code if an error?  result code object?
    public ResultCode requestMatch( Connection challenger, Match match, int maxNumberOfRounds ) 
            throws RemoteException
    {
        ResultCode rc = RC_OK;
        
        // don't ask if we're busy
        if (state!=ConnectionState.AVAILABLE_FOR_PLAY)
            return E_WRONG_STATE;
        
        // don't even ask if we'll never say yes
        if (bNotAcceptingMatches)
            return RC_REQUEST_DENIED;
        
        changeState( ConnectionState.REQUEST_IN_PROGRESS );

        // ask, and if amenable, start the match
        rc = response.requestToInitiateMatch( new PlayerEntry(challenger),
                                              maxNumberOfRounds);
        if (rc==RC_OK) {
            this.match = match;
            changeState( ConnectionState.MATCH_IN_PLAY );
        } else {
            // if anything but OK or DENIED: never ask this connection again
            if (rc!=RC_REQUEST_DENIED)
                bNotAcceptingMatches = true;
            // anything but OK: return DENIED
            changeState( ConnectionState.AVAILABLE_FOR_PLAY );
            rc = RC_REQUEST_DENIED;
        }
        
        return rc;
    }
    
    @Override
    public ArrayList<PlayerEntry> getPlayerList() 
            throws RemoteException
    {
        
        return ConnectionTable
                .getInstance()
                .getPlayerList( this );
    }

    @Override
    public ResultCode doGesture( Gesture g ) throws RemoteException {
        if (match==null)
            return E_WRONG_STATE;
        return match.doGesture(this,g);
    }

    @Override
    public void abortingMatch( ResultCode rc ) throws RemoteException {
        state = ConnectionState.AVAILABLE_FOR_PLAY;
        if (match!=null)
            match.abortMatch( this, rc );
    }
    

    /**
     * called by the Match object, as forwarded from the remote player, this 
     * function immediately resets the player connection to AVAILABLE, and 
     * informs the Client of the change in status asynchronously.  The reason 
     * code is informational only.
     * 
     * @param rc remote player's reason for aborting match
     */
    void abortMatch( ResultCode rc ) {
        state = ConnectionState.AVAILABLE_FOR_PLAY;
        // best effort: if it fails, we've done all we can
        try {
            if (response!=null)
                response.abortMatch( rc );
        }
        catch (Exception e) {}
    }

    @Override
    public Scorecard getScorecard() throws RemoteException {
        if (match==null)
            return null;
        Scorecard score = match.getScorecard(this);
        if (isMatchOver(score))
            changeState( ConnectionState.AVAILABLE_FOR_PLAY );
        return score;
    }
    
    private boolean isMatchOver( Scorecard score ) {
        return score.roundsPlayed==score.maxRounds 
                && score.roundsPlayed>0 
                && state==ConnectionState.MATCH_IN_PLAY;
    }
    
    ConnectionState changeState( ConnectionState newState ) {
        state = newState;
        ConnectionTable.getInstance().notifyListeners( 
                new ListAction( 
                        ListAction.Action.CHANGE, 
                        new PlayerEntry(this) ) );
        return state;
    }
    
    /**
     * Push the score to the client.
     * 
     * Note: the client returning RC_OK indicates receipt of the score 
     * (i.e., acknowledging PUSH notification).  Any other result code is 
     * interpreted as lack of acknowledgment of receipt (i.e., client 
     * prefers PULL notification)
     * 
     * @param score
     * @return true if the client accepted receipt of the score.  
     * (This is used by the Match class to determine the next 
     * state of the FSM.
     */
    public boolean notifyScore( Scorecard score )
    {
        ResultCode rc = RC_CONTINUE;
        try { 
            rc = response.notifyScore( score );
        } 
        catch (Exception e) {
            l.log(Level.WARNING,"error pushing score",e);
        }
        finally {
            if (rc==RC_OK && isMatchOver(score))
                changeState( ConnectionState.AVAILABLE_FOR_PLAY );
        }
        return (rc==RC_OK);
    }

    @Override
    public void addPlayerListListener(IPlayerListListener listener) 
            throws RemoteException {
        ConnectionTable.getInstance().addListener(listener);
    }

    @Override
    public void removePlayerListListener(IPlayerListListener listener) 
            throws RemoteException {
        ConnectionTable.getInstance().removeListener(listener);
    }
    
    @Override
    public String toString() {
        return String.format( "%s@%s %08x %s", teamName, origin, getID(), state );
    }
}
