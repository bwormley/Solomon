package genericclient;

import java.util.Random;
import solomonClientLib.INotification;
import solomonClientLib.RemotePlayer;
import solomonserver.Gesture;
import solomonserver.ResultCode;
import static solomonserver.ResultCode.E_NOT_IMPLEMENTED;
import static solomonserver.ResultCode.RC_OK;
import solomonserver.Scorecard;

/**
 * This is an ultra simple, minimalist implementation of an RPS client of 
 * Solomon Server.  This client can play an automated match with a remote 
 * player.
 * 
 * The team name for this client is taken from the first argument in the 
 * command line.  The second argument is the suggested number of rounds 
 * to play.
 *
 * @author R Brett Wormley
 */
public class GenericClient implements INotification {

    public static void main(String[] args) 
            throws InterruptedException {
        GenericClient gc = new GenericClient();
        gc.run(args);
    }
    
    private void run(String[] args) 
            throws InterruptedException {
        int numberOfRounds = Integer.parseInt(args[1]);
        ResultCode rc;
        Scorecard score;
        boolean bDone = false;
        
        // get the remote player object
        RemotePlayer remote = RemotePlayer.getInstance();
        
        // register with the server
        rc = remote.register( args[0], this );
        if (rc!=RC_OK) {
            System.out.println( "Failed to register: " + rc );
            System.exit(1);
        }
        
        while (true) {
        
            // request/start a match
            rc = remote.startMatch( numberOfRounds );
            if (rc!=RC_OK) {
                System.out.println( "Failed to start match: " + rc );
                System.exit(1);
            }
        
            // play rounds until finished
            do {
                rc = remote.doGesture( newGesture() );
                score = remote.getScore();
                if (score!=null) {
                    System.out.println( score );
                    bDone = score.roundsPlayed>=score.maxRounds;
                }
            } while (!bDone);
        }
    }
    
    
    private Gesture newGesture() {
        final Random rand = new Random();
        switch (rand.nextInt(3)) {
            case 0: return Gesture.ROCK;
            case 1: return Gesture.PAPER; 
        } 
        return Gesture.SCISSORS;
    }

    
    /* **************************************
     * INotification INTERFACE IMPLEMENTATION
     * ************************************** */
    
    @Override
    public ResultCode requestMatch(String teamName, int numberOfRounds) {
        return E_NOT_IMPLEMENTED;
    }

    @Override
    public ResultCode notifyScore(Scorecard score) {
        return E_NOT_IMPLEMENTED;
    }

    @Override
    public void abortMatch(ResultCode rc) {
        // NOT IMPLEMENTED
    }

    @Override
    public void endSession(ResultCode rc) {
        // NOT IMPLEMENTED
    }

}
