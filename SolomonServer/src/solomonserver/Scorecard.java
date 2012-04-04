package solomonserver;

import java.io.Serializable;

/**
 *
 * @author bwormley
 */
public class Scorecard implements Serializable {
    public Scorecard() {
        // sensible initial values
        rc              = ResultCode.RC_OK;
        opponentGesture = Gesture.NONE;
    }
    public ResultCode rc;
    public Gesture opponentGesture;
    public int myScore;
    public int opponentScore;
    public int ties;
    public int roundsPlayed;
    public int maxRounds;
}
