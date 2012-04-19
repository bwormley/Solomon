package solomonClientLib.proxy;


import java.rmi.RemoteException;
import java.util.Random;
import solomonClientLib.INotification;
import solomonClientLib.Server;
import solomonserver.Gesture;
import static solomonserver.Gesture.*;
import solomonserver.ResultCode;
import static solomonserver.ResultCode.RC_OK;
import solomonserver.Scorecard;

/**
 * 
 * This is the PROXY class implementing the BLOCKING INTERFACE for the 
 * Solomon Client Library.
 * 
 * This class is the interface between a team's RPS project and an external 
 * player (usually in the form of another team's RPS project, as arbitrated by 
 * the Solomon Server.  It isolates the caller from details about remote player 
 * selection and negotiation, server callbacks, and network events and errors.
 * 
 * This is a singleton class; the single instance may be retrieved at any point 
 * in the RPS program.
 */
public class RemotePlayer 
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

    /* ****************************************
     * PUBLIC APPLICATION PROGRAMMING INTERFACE
     * ****************************************
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
        ResultCode rc = RC_OK;
        this.teamName = teamName;
        this.notify = notify;
        return rc; 
    }
    
    /**
     * Set the number of rounds requested for this match.  This will be the 
     * number of rounds requested when inviting a remote player to a match.
     * If,instead, this local player accepts an invitation from another 
     * player, the number of rounds specified by that player will take 
     * precedence.  So, for the general case, the local player should heed the 
     * actual number of rounds to play, as shown in each Scorecard record.
     * 
     * Typically, this method is used when it is inconvenient for the client 
     * to access the number of rounds when calling the startMatch() method.
     * 
     * @param numberOfRounds number of rounds to play, in the invitation 
     * to a remote player
     */
    private int numberOfRounds = 256;
    public void setNumberOfRounds( int numberOfRoundsRequested ) {
        numberOfRounds = numberOfRoundsRequested;
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
     * @param numberOfRounds one or more rounds to play for this match.  If less 
     * than one, then the number of rounds specified in setNumberOfRounds() 
     * is used.  See that method description for the meaning of 
     * 'number of rounds.'  If less than one, and no value was set using 
     * setNumberOfRounds(), the number of rounds defaults to 256.
     * 
     * @return 
     */
    private String teamName = "UNKNOWN LOCAL";
    public ResultCode startMatch( int numberOfRoundsRequested ) 
    { 
        
        // we'll use the number of rounds passed in here.  But if it isn't 
        // known (but was passed in previously), use that instead.  
        if (numberOfRoundsRequested>0) {
            numberOfRounds = numberOfRoundsRequested;
        }
                
        localGesture = null;
        opponentGesture = null;
        myScore = 0;
        opponentScore = 0;
        ties = 0;
        roundsPlayed = 0;

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
    private Gesture localGesture;
    private Gesture opponentGesture;
    private int myScore;
    private int opponentScore;
    private int ties;
    private int roundsPlayed;
    public ResultCode doGesture( Gesture g ) 
    { 
        localGesture = g;
        opponentGesture = newG();
        roundsPlayed++;
        if (localGesture==opponentGesture)
            ties++;
        else if (  (localGesture==ROCK       && opponentGesture==PAPER)
                || (localGesture==PAPER      && opponentGesture==SCISSORS)
                || (localGesture == SCISSORS && opponentGesture==ROCK) )
            opponentScore++;
        else 
            myScore++;
        
        // keep it under the speed of light
        try { Thread.sleep(25); } catch (Exception e){}
        return RC_OK; 
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
        Scorecard score       = new Scorecard();
        score.myGesture       = localGesture;
        score.myScore         = myScore;
        score.maxRounds       = numberOfRounds;
        score.opponentGesture = opponentGesture;
        score.opponentScore   = opponentScore;
        score.roundsPlayed    = roundsPlayed;
        score.ties            = ties;
        score.rc              = roundsPlayed>=numberOfRounds 
                                  ? ResultCode.RC_MATCH_ENDED
                                  : RC_OK;
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
    
    private Random rand = new Random();
            
    private Gesture newG() {
        switch (rand.nextInt(3)) {
            case 0: return Gesture.ROCK;
            case 1: return Gesture.PAPER; 
        } 
        return Gesture.SCISSORS;
    }

    /* ******************************
     * PRIVATE METHODS AND INTERFACES
     * ******************************
     */

    private INotification notify;
}
