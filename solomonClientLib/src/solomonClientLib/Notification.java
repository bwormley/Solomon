package solomonClientLib;


import solomonserver.ResultCode;
import solomonserver.Scorecard;


/**
 *
 * @author bwormley
 */
public interface Notification {
    
    ResultCode requestMatch( String teamName, int numberOfRounds );
    
    ResultCode notifyScore( Scorecard score );
    
    ResultCode notifyMatchResult( Scorecard score );
    
    void abortMatch( ResultCode rc );
    
    void endSession( ResultCode rc );

    /*
     * ASYNC INTERFACE (CALLBACK)
     * --------------------------
     * requestMatch - init by remote player                MANDATORY
     * roundResult - push results to client                MANDATORY (if async mode)
     * matchResult - push results to client
     * abortMatch - push remote player or admin abort
     * endSession - push administrative shutdown
     *
     * 
     */

}
