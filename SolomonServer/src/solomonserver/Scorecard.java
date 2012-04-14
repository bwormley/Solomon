package solomonserver;

import java.io.Serializable;

/**
 * This data record contains a snapshot of the current status of a match.  
 * Typically, an RPS program will either ask for this record via a call to 
 * getScore(), or receive one asynchronously via its notifyScore() callback.
 *
 * @author R Brett Wormley
 */
public class Scorecard implements Serializable {
    public Scorecard() {
        // sensible initial values
        rc              = ResultCode.RC_OK;
        opponentGesture = Gesture.NONE;
    }
    
    /*
     * One of several ResultCodes indicating the status of the return from 
     * a call to getScore(), if there was an error.  When delivered via the 
     * notifyScore() asynchronous callback, it always contains RC_OK.  It  will 
     * contain one of the follwoing values:
     *     RC_OK - the contained score is valid
     */
    // TODO enumerate possible codes
    public ResultCode rc;
    
    /**
     * the gesture made by the opposing player
     */
    public Gesture opponentGesture;
    
    /**
     * the number of rounds I have won so far in this match
     */
    public int myScore;
    
    /**
     * the number of rounds the remote player has won so far in thi smatch
     */
    public int opponentScore;
    
    /**
     * the number of tied rounds so far in this match
     */
    public int ties;
    
    /**
     * the number of rounds played so far in this match
     */
    public int roundsPlayed;
    
    /**
     * the number of rounds in this match
     */
    public int maxRounds;
    
    /**
     * convenience function for dumping contents
     * 
     * @return human-readable representation
     */
    @Override
    public String toString() {
        return String.format( "round %d/%d    WIN/LOSS/TIE %d/%d/%d  rc=%s\n",
            roundsPlayed, 
            maxRounds,
            myScore, 
            opponentScore, 
            ties,
            rc );
    }
    
}
