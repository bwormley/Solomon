package solomonserver;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import static solomonserver.ResultCode.*;

public class Connection extends UnicastRemoteObject implements IConnection, Serializable {
    
    private String teamName = null;
    private String origin = null;
    private IResponse response;
    private Match match = null;

    private ConnectionState state = ConnectionState.DISCONNECTED;
        
    public Connection( String teamName, IResponse response, String origin )
            throws RemoteException
    {
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
    public int getID()
    {
        return this.hashCode();
    }
    
    @Override
    public void terminateConnection( ResultCode rc ) throws RemoteException {
        // TODO: implement, terminate match if needed
    }

    @Override
    public ResultCode requestRemoteMatch( int playerID, int maxNumberOfRounds ) 
            throws RemoteException 
    {
        ResultCode rc = RC_OK;
        
        if (state!=ConnectionState.AVAILABLE_FOR_PLAY)
            return E_WRONG_STATE;
        
        state = ConnectionState.REQUEST_IN_PROGRESS;
        
        System.out.println("Server: Connection.requestRemoteMatch() entry");

        Connection player2 = ConnectionTable.getInstance().getPlayer( playerID );
        if (player2==null) {
            state = ConnectionState.AVAILABLE_FOR_PLAY;
            System.out.printf( "Server: Connection.requestRemoteMatch(): player not found (ID=%08x)\n", playerID);
            rc = E_UNRECOGNIZED_PLAYER;
        }
            
        else {
            match = new Match( this, player2, maxNumberOfRounds );
            rc = player2.requestMatch( this, match, maxNumberOfRounds );
            if (rc==RC_OK) {
                System.out.printf( "Server: BEGIN match between %s and %s\n", this.getTeamName(), player2.getTeamName() );
                state = ConnectionState.MATCH_IN_PLAY;
            }
            else {
                match = null;
                state = ConnectionState.AVAILABLE_FOR_PLAY;
            }
        }
        return rc;
    }
    
    // TODO: implement: make sure object is in correct state to enter a match
    // TODO: result code if an error?  result code object?
    public ResultCode requestMatch( Connection challenger, Match match, int maxNumberOfRounds ) 
            throws RemoteException
    {
        ResultCode rc = RC_OK;
        
        if (state!=ConnectionState.AVAILABLE_FOR_PLAY)
            return E_WRONG_STATE;
        
        state = ConnectionState.REQUEST_IN_PROGRESS;

        boolean willingToPlay = response.requestToInitiateMatch(challenger.getTeamName(), maxNumberOfRounds);
        if (willingToPlay) {
            this.match = match;
            state = ConnectionState.MATCH_IN_PLAY;
        } else {
            state = ConnectionState.AVAILABLE_FOR_PLAY;
            rc = E_REQUEST_DENIED;
        }
        
        return rc;
    }
    
    @Override
    public ArrayList<PlayerEntry> getAvailablePlayerList() 
            throws RemoteException
    {
        
        return ConnectionTable
                .getInstance()
                .getPlayerList(
                    ConnectionTable.Filter.AVAILABLE, 
                    this );
    }

    @Override
    public ResultCode doGesture( Gesture g ) throws RemoteException {
        if (match==null)
            return E_WRONG_STATE;
        return match.doGesture(this,g);
    }

    @Override
    public void abortMatch( ResultCode rc ) throws RemoteException {
        if (match!=null)
            match.abortMatch();
    }

    @Override
    public Scorecard getScorecard() throws RemoteException {
        if (match==null)
            return null;
        Scorecard score = match.getScorecard(this);
        if (isMatchOver(score))
            state = ConnectionState.AVAILABLE_FOR_PLAY;
        return score;
    }
    
    private boolean isMatchOver( Scorecard score ) {
        return score.roundsPlayed==score.maxRounds 
                && score.roundsPlayed>0 
                && state==ConnectionState.MATCH_IN_PLAY;
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
        ResultCode rc = ResultCode.RC_CONTINUE;
        // TODO: handle this catch
        try { 
            rc = response.notifyScore( score );
            if (rc==RC_OK && isMatchOver(score))
                state = ConnectionState.AVAILABLE_FOR_PLAY;
        } 
        catch (Exception e) { 
            System.out.println("Server: while pushing score to client: "+e+" g="+score.opponentGesture); 
        }
        return (rc == ResultCode.RC_OK) ;
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
}
