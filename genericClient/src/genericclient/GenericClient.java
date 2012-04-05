/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package genericclient;

import java.util.Random;
import solomonClientLib.Player;
import solomonserver.Gesture;
import solomonserver.ResultCode;

import static solomonserver.ResultCode.*;
import static solomonserver.Gesture.*;
import solomonserver.Scorecard;
import solomonClientLib.INotification;

/**
 *
 * @author bwormley
 */
public class GenericClient implements INotification {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        GenericClient gc = new GenericClient();
        gc.run(args);
    }
    
    private void run(String[] args) throws InterruptedException {
        int numberOfRounds = Integer.parseInt(args[1]);
        ResultCode rc;
        Scorecard score;
        boolean bDone = false;
        
        // get the remote player object
        Player fred = Player.getInstance();
        
        // register with the server
        rc = fred.register( args[0], this );
        if (rc!=RC_OK) {
            System.out.println( "Failed to register: " + rc );
            System.exit(1);
        }
        
        // request a match
        rc = fred.startMatch( numberOfRounds );
        if (rc!=RC_OK) {
            System.out.println( "Failed to start match: " + rc );
            System.exit(1);
        }
        
        // play rounds until finished
        do {
            rc = fred.doGesture(newG());
            score = fred.getScore();
            if (score!=null) {
                System.out.printf( "GenericClient.%s:     round %d/%d    %d/%d/%d  rc=%s\n",
                    args[0],
                    score.roundsPlayed, 
                    score.maxRounds,
                    score.myScore, 
                    score.opponentScore, 
                    score.ties,
                    score.rc );
                 bDone = score.roundsPlayed>0 && score.roundsPlayed>=score.maxRounds;
            }
             Thread.sleep(1000);

        } while (!bDone);
    }
    
    private Random rand = new Random();
            
    private Gesture newG() {
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
    public ResultCode notifyMatchResult(Scorecard score) {
        return E_NOT_IMPLEMENTED;
    }

    @Override
    public void abortMatch(ResultCode rc) {
    }

    @Override
    public void endSession(ResultCode rc) {
    }

}
