package solomonClientLib;


import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import solomonserver.*;
import static solomonserver.ResultCode.E_NOT_IMPLEMENTED;
import static solomonserver.ResultCode.*;

/**
 *
 * 
 */
public class Player extends UnicastRemoteObject implements IResponse
{
    /*
     * SYNC INTERFACE
     * --------------
     * register - register with server                     MANDATORY (need team name, callbacks)
     * startMatch - initiate negotiationc                  MANDATORY
     * doGesture - make a throw FIRST                      MANDATORY
     * getRoundResult - round result                       MANDATORY (if sync mode)
     * abortMatch
     * terminateSession - RPS shutting down (unregister)   RECOMMENDED (graceful shutdown)
     * 
     * ADMINISTRATIVE SYNC
     * -------------------
     * pushLog - send logging to server
     * 
     * ADMINISTRATIVE ASYNC CALLBACK
     * -----------------------------
     * setLog - turn on,off,level
     */
    
    private Player() throws RemoteException {}

    /* ****************************************
     * PUBLIC APPLICATION PROGRAMMING INTERFACE
     * ****************************************
     */
    private static Player _instance = null;
    public static Player getInstance() 
    { 
        if (_instance==null) {
            try {
                _instance = new Player();
            }
            catch (Exception e)
            {
                System.out.println( "Caught exception in solomonclientlib.Player  constructor: "+e);
            }
        }
        return _instance; 
    }
    
    /**
     * Register with the Solomon Server, in preparation for starting a match.  
     * This must be the first call to the Solomon Client library.
     * 
     * @param teamName unique name identifying the team to other 
     * potential opponents
     * @param notify Listener-style argument to receive optional callbacks 
     * when certain events happen
     * @return status of this request.  Possible return values are:
     * TODO: enumerate the return values
     */
    public ResultCode register( String teamName, INotification notify ) 
    { 
        ResultCode rc;
        rc = Server.getInstance().register( teamName, (IResponse)this );
        return rc; 
    }
    
    /**
     * Initiate a match with a remote player, requesting a certain number of 
     * rounds of play.
     * 
     * Note: this call will typically query the Solomon Server for a list of 
     * active, available  players, and present a Dialog Box for the user to 
     * select a player.  Once the player has been selected, the Solomon Client 
     * negotiates with the remote player via the Solomon Server; and if he 
     * accepts the challenge, the match begins.  This is all transparent to 
     * the caller of this method.
     * 
     * @param numberOfRounds 1 or more rounds to play for this match
     * @return 
     */
    public ResultCode startMatch( int numberOfRounds ) 
    { 
        // or block, waiing for an invitation
        // or list of available players dialog also shows current request
        
        // get list, display list (incl invitation), make selection
        RemotePlayerList rpl = RemotePlayerList.getInstance();
        rpl.refresh();
        
        // 1) select from a list, then request match, loop until someone agrees
        // problem: how to agree on #rounds?
        
        // 2) capture callback, accept match request
        // problem: how to do thread synchronization
        
        String[] args = new String[0];
        RemotePlayerSelector rpSel = new RemotePlayerSelector();
        RemotePlayerSelector.main( args );
        PlayerEntry opponentPlayer = rpSel.getOpponentPlayer();
        if (opponentPlayer!=null)
            opponentName = opponentPlayer.teamName;
        
        return RC_OK; 
    }
    
    private String opponentName = null;
    public String getOpponentName()
    {
        if (opponentName==null)
            return "unknown";
        return opponentName;
    }
    
    /**
     * Make a ROCK/PAPER/SCISSORS gesture
     * 
     * As the action of playing a round, make a gesture.  This function 
     * sends the gesture to the Solomon Server, which is arbitrating the 
     * match.  A match must be already in play.
     * 
     * @param g the choice of ROCK/PAPER/SCISSORS
     * @return 
     */
    public ResultCode doGesture( Gesture g ) 
    { 
        ResultCode rc;
        rc = Server.getInstance().doGesture(g);
        return rc; 
    }
    
    /**
     * Return the results of this round of play
     * 
     * Request the current status of play, including the opponent player's 
     * move, the current score, and the round number.  The Scorecard object 
     * also contains a ResultCode which may be useful in determining match 
     * status, such as whether the match has ended.
     * 
     * @return object containing fields describing the match status
     */
    public Scorecard getScore() 
    { 
        return Server.getInstance().getScore();
    }
    
    /**
     * Abnormal abort of the current match.
     * 
     * Cause the match to end prematurely.  (A match automatically ends once 
     * all the rounds have been played).  The opponent player is notified. 
     * After this call, the local player may enter into another match with 
     * any player.
     * 
     * @param rc an optional indication to the opponent about the reason for 
     * the abortion
     */
    public void abortMatch( ResultCode rc ) 
    {
        Server.getInstance().abortMatch( rc );
    }
    
    /**
     * End the session with the Solomon Server
     * 
     * This function is the recommended way to gracefully exit communications 
     * with the Solomon Server.  Any current match in play is aborted
     * 
     * @param rc an optional indication to the server about the reason for
     * the termination
     */
    public void terminateSession( ResultCode rc ) 
    {
    }
    
    /* ******************************
     * PRIVATE METHODS AND INTERFACES
     * ******************************
     */

    @Override
    public boolean requestToInitiateMatch(String teamName, int maxNumberOfRounds) throws RemoteException {
        System.out.printf( "Lunar.requestToInitiateMatch(%s,%d) ACCEPTING\n", teamName, maxNumberOfRounds );
        opponentName = teamName;
        return true;
    }

    @Override
    public void abortMatch() throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void abortConnection(ResultCode rc) throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ResultCode notifyScore(Scorecard score) throws RemoteException {
        return E_NOT_IMPLEMENTED;
    }

}
