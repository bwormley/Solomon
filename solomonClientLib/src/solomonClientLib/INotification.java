package solomonClientLib;
/**
 * Package details?
 * 
 * @author R Brett Wormley
 */

import solomonserver.ResultCode;
import solomonserver.Scorecard;


/**
 * <h1> INotification </h1>
 * 
 * <p> Author: R Brett Wormley</p>
 * 
 * This class allows the Solomon Server to notify the client of certain 
 * events, such as the remote player's gesture, or the Server going down.  
 * These methods must be implemented, but may return a ResultCode indicating 
 * that they are not being used.  The requestMatch() method is the only one 
 * that must have a functional implementation for a fully functioning RPS 
 * program to accept remote matches.
 *
 * @author R Brett Wormley
 */
public interface INotification {
    
    /**
     * This method is called by the Server when a remote player is requesting 
     * a match with the local client.  This method is only called when the 
     * local client is not already playing a match.
     *
     * @param teamName name of the team requesting a match
     * @param numberOfRounds number of rounds for the requested match
     * @return A ResultCode from the following list:
     *     RC_OK - the local player accepts the match, and can begin playing 
     *         by calling doGesture();
     *     RC_REQUEST_DENIED - the local player denies the invitation to 
     *         play this invitation
     *     E_NOT_IMPLEMENTED - the local player refuses to play any 
     *         invited matches
     *     any other ResultCode is interpreted the same as E_NOT_IMPLEMENTED
     * 
     * Note: if the E_NOT_IMPLEMENTED result is returned, the Server may assume 
     * that the client will NEVER accept a match request.  From this point on, 
     * the local client will only be able to initiate matches.
     */
    ResultCode requestMatch( String teamName, int numberOfRounds );
    
    /**
     * This method is called at the conclusion of both players making a 
     * gesture, to report the results of the round.  The complete match status 
     * is provided.  Once the Server has informed both players of the status of 
     * the round -- either by this method (asynchronously), or by polling -- the 
     * next round may begin, with a call to doGesture().
     * 
     * @param score a record containing complete match status, including the 
     * result of the current round.
     * @return A ResultCode from the following list:
     *     RC_OK - acknowledges receipt of the score, allowing the 
     *         match to continue
     *     E_NOT_IMPLEMENTED - denies receipt of the score, so the match 
     *         does not continue until the score is retrieved by a call 
     *         to getScore()
     *     any other ResultCode is interpreted the same as E_NOT_IMPLEMENTED
     * 
     * Note: if the E_NOT_IMPLEMENTED result is returned, the Server may 
     * assume that the client will NEVER acknowledge receipt of a score record, 
     * and, for reasons of efficiency, may choose to not call this method 
     * asynchronously again.
     */
    ResultCode notifyScore( Scorecard score );

    /**
     * This method is called by the Server to indicate an abnormal termination 
     * of the match, probably due to the remote player's request.  This is an 
     * advisory call.
     * 
     * @param rc A ResultCode may be sent to indicate a reason for the 
     * termination.  This is merely advisory.
     */
    void abortMatch( ResultCode rc );
    
    /**
     * This method is called by the Server to indicate the connection to the 
     * Server is about to be terminated, most likely because the server is 
     * going down.  This call is merely advisory.
     * 
     * @param rc A ResultCode may be sent to indicate a reason for the 
     * termination.  This is merely advisory.
     */
    void endSession( ResultCode rc );

}
