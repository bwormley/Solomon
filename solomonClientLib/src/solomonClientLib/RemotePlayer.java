package solomonClientLib;


import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Semaphore;
import solomonserver.*;
import static solomonserver.ResultCode.*;

/**
 * This class is the interface between a team's RPS project and an external 
 * player (usually in the form of another team's RPS project, as arbitrated by 
 * the Solomon Server.  It isolates the caller from details about remote player 
 * selection and negotiation, server callbacks, and network events and errors.
 * 
 * This is a singleton class; the single instance may be retrieved at any point 
 * in the RPS program.
 */
public class RemotePlayer 
        extends UnicastRemoteObject 
        implements IResponse
{    
    /**
     * Private constructor, for singleton implementation
     * 
     * @throws RemoteException 
     */
    private RemotePlayer() throws RemoteException {
    }

    /**
     * The singleton instantiation of this class
     */
    private static RemotePlayer _instance = null;

    /* ***********************************************
     * CLIENT-FACING APPLICATION PROGRAMMING INTERFACE
     * ***********************************************
     */
    
    /**
     * The static method to create/acquire the singleton instance
     * 
     * @return the instance
     */
    public static RemotePlayer getInstance() 
    { 
        if (_instance==null) {
            try {
                _instance = new RemotePlayer();
            }
            catch (Exception e)
            {
                System.out.println( "Caught exception in solomonclientlib.Player constructor: "+e);
            }
        }
        return _instance; 
    }
    
    /**
     * Register with the Solomon Server, in preparation for starting a match.  
     * This must be the first call to the RemotePlayer object.
     * 
     * @param teamName unique name identifying the team to other 
     * potential opponents.  The name must be unique per IP address.
     * @param notify Listener-style argument to receive optional callbacks 
     * when certain events happen
     * @return status of this request.  Possible return values are:
     * TODO: enumerate the return values:
     *     RC_OK - this client was successfully registered
     */
    public ResultCode register( String teamName, INotification notify ) 
    { 
        ResultCode rc;
        this.teamName = teamName;
        rc = Server.getInstance().register( teamName, (IResponse)this );
        this.notify = (rc==RC_OK) ? notify : null;
        return rc; 
    }
    
    /**
     * Set the number of rounds requested for this match.  This will be the 
     * number of rounds requested when inviting a remote player to a match.
     * If, instead, this local player accepts an invitation from another 
     * player, the number of rounds specified by that player will take 
     * precedence.  So, for the general case, the local player should heed the 
     * actual number of rounds to play, as shown in each Scorecard record.  
     * Note: this call is only effective before a match has initiated 
     * (i.e., before the call to startMatch())
     * 
     * Typically, this method is used when it is inconvenient for the client 
     * to access the number of rounds when calling the startMatch() method.
     * 
     * @param numberOfRounds number of rounds to play, in the invitation 
     * to a remote player
     */
    private final int DEFAULT_NUMBER_OF_ROUNDS = 256;
    private int numberOfRounds = DEFAULT_NUMBER_OF_ROUNDS;
    public void setNumberOfRounds( int numberOfRoundsRequested ) {
        numberOfRounds = numberOfRoundsRequested;
    }
    
    /**
     * Return the number of rounds for this match.  This may be different 
     * than requested by the local client if he accepted a remote invitation.  
     * Accepting the remote invitation alos accepts the number of rounds in 
     * the invitation.
     * 
     * @return number of rounds in the match
     */
    public int getNumberOfRounds() {
        return numberOfRounds;
    }
    
    /**
     * Initiate a match with a remote player, resulting in either requesting 
     * a certain number of rounds of play, or accepting an invitation from 
     * a remote player.
     * 
     * Note: this call will typically query the Solomon Server for a list of 
     * active, available  players, and present a Dialog Box for the user to 
     * select a player.  Once the player has been selected, the Solomon Client 
     * negotiates with the remote player via the Solomon Server; and if he 
     * accepts the challenge, the match begins.  This is all transparent to 
     * the caller of this method.
     * 
     * @param numberOfRounds one or more rounds to play for this match.  If less 
     * than one, then the number of rounds specified in setNumberOfRounds() 
     * is used.  See that method description for the meaning of 
     * 'number of rounds.'  If less than one, and no value was set using 
     * setNumberOfRounds(), the number of rounds defaults to 
     * DEFAULT_NUMBER_OF_ROUNDS.
     * 
     * @return 
     */
    private Semaphore startingMatch;
    private RemotePlayerSelector rpSel;
    private GameStatus gameStatus;
    private String teamName = "UNKNOWN LOCAL";
    public ResultCode startMatch( int numberOfRoundsRequested ) 
    { 
        
        // we'll use the number of rounds passed in here.  But if it isn't 
        // known (but was passed in previously), use that instead.  
        if (numberOfRoundsRequested>0) {
            numberOfRounds = numberOfRoundsRequested;
        }
        
        // give this to server object as an advisory value
        Server.getInstance().setNumberOfRounds(numberOfRounds);
        
        // or block, waiting for an invitation
        // or list of available players dialog also shows current request
        
        // get list, display list (incl invitation), make selection
        RemotePlayerList rpl = RemotePlayerList.getInstance();
        rpl.refresh();
        
        // 1) select from a list, then request match, loop until someone agrees
        // problem: how to agree on #rounds?
        
        // 2) capture callback, accept match request
        // problem: how to do thread synchronization
        
        startingMatch = new Semaphore(1,true);
        startingMatch.drainPermits();
        rpSel = new RemotePlayerSelector(startingMatch);
        
        // run the selector dialog with default look&feel
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                rpSel.setVisible(true);
            }
        });
        
        // block until match starts
        try {
           startingMatch.acquire();
        } catch ( InterruptedException e) {
        }

        PlayerEntry opponentPlayer = rpSel.getOpponentPlayer();
        if (opponentPlayer!=null)
            opponentName = opponentPlayer.teamName;
        numberOfRounds = Server.getInstance().getNumberOfRounds();
        
        // match in play: fire up the score box
        gameStatus = new GameStatus( teamName, opponentName, numberOfRounds );
        // run the selector dialog with default L&F
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                gameStatus.setVisible(true);
            }
        });
                
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
     * Also, whenever we update the  score, we update the GameStatus GUI.
     * 
     * @return object containing fields describing the match status
     */
    public Scorecard getScore() 
    { 
        Scorecard score = Server.getInstance().getScore();
        if (score!=null)
            gameStatus.updateProgress(
                    score.roundsPlayed,
                    score.myScore,
                    score.ties,
                    score.opponentScore );
        return score;
    }
    
    /**
     * Abnormal abort of the current match.
     * 
     * Cause the match to end prematurely.  (A match automatically ends once 
     * all the rounds have been played).  The opponent player is notified. 
     * After this call, the local player may enter into another match with 
     * any player.  Both players statuses are immediately reset to AVAILABLE.
     * 
     * @param rc an optional indication to the opponent about the reason for 
     * the abortion
     */
    public void abortingMatch( ResultCode rc ) 
    {
        Server.getInstance().abortingMatch( rc );
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

    private INotification notify;
    
    @Override
    public ResultCode requestToInitiateMatch( PlayerEntry challenger, 
                                              int         maxNumberOfRounds) 
            throws RemoteException {
        ResultCode rc = RC_OK;
        
        // if there's a callback, first try notifying asynchronously
        if (notify!=null)
            rc =  notify.requestMatch( challenger.teamName, maxNumberOfRounds );
            
        // if not implementing async mode, try synchronously, via the GUI
        if (notify==null || rc!=RC_OK) {
            Semaphore ourDecisionToAcceptMatch = new Semaphore(1,false);
            ourDecisionToAcceptMatch.drainPermits();
            rpSel.displayRemoteInvitationExtended( challenger, maxNumberOfRounds, ourDecisionToAcceptMatch );
            try {
                ourDecisionToAcceptMatch.acquire();
            } catch ( InterruptedException e) {
                // TODO handle timeouts, local cancel, remote cancel
            }
            
            if (rpSel.getOpponentPlayer()!=null) {
                startingMatch.release();
                rc = RC_OK;
            } else
                rc = ResultCode.RC_REQUEST_DENIED;
        }
        return rc;
    }

    @Override
    public void abortMatch( ResultCode rc ) 
            throws RemoteException {
        if (notify!=null)
            notify.abortMatch( rc );
    }

    @Override
    public void abortConnection(ResultCode rc) 
            throws RemoteException {
        if (notify!=null)
            notify.endSession(rc);
    }

    @Override
    public ResultCode notifyScore(Scorecard score) 
            throws RemoteException {
        if (notify!=null)
            return notify.notifyScore(score);
        return E_NOT_IMPLEMENTED;
    }
}
