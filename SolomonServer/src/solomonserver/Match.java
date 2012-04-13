package solomonserver;

import static solomonserver.Gesture.*;
import static solomonserver.ResultCode.*;

public class Match
{ 
    private Connection player1 = null;
    private Connection player2 = null;
    
    private int maxNumberOfRounds = 20;
    private int player1Score = 0;
    private int player2Score = 0;
    private int ties = 0;
    private int round = 0;
    
    private Gesture player1Gesture;
    private Gesture player2Gesture;
    
    private State state = State.BEGIN_ROUND;
    
    enum State {
        BEGIN_ROUND,   /* initial state: ready for gestures, no pending results */
        P1_GESTURED,   /* Player One has gestured, pending on Player Two */
        P2_GESTURED,   /* Player Two has gestured, pending on Player One */
        BOTH_GESTURED, /* Gestures received from both players, pending on requests for results */
        P1_INFORMED,   /* Player One informed of rounds results, pending on Player Two */
        P2_INFORMED,   /* Player One informed of rounds results, pending on Player Two */
        BOTH_INFORMED, /* Both players informed of round results, ready to transition to next round */
        GAME_OVER,     /* Terminal state */
    }
    
    public Match( IConnection player1, IConnection player2, int maxNumberOfRounds )
    {
        this.player1 = (Connection)player1;
        this.player2 = (Connection)player2;
        this.maxNumberOfRounds = maxNumberOfRounds;
    }
    
    Connection getPlayer1() {
        return player1;
    }
    
    Connection getPlayer2() {
        return player2;
    }
    
    public synchronized Scorecard getScorecard( Connection caller )
    {
        PlayerSelector sel    = whichPlayerCalledUs(caller);
        ResultCode     rc     = RC_OK;
        Scorecard      score  = null;
        EventType      event  = EventType.UNKNOWN_COMM;
        
        switch (sel)
        {
            case PLAYER1: event = EventType.P1_QUERY;  break;
            case PLAYER2: event = EventType.P2_QUERY;  break;
        }
System.out.printf( "Server.Match.%s getScorecard() pre_state=%s event=%s ", caller.getTeamName(), state, event );
        switch (state) 
        {
            case BEGIN_ROUND:   rc = stBeginRound(   event, NONE ); break;
            case P1_GESTURED:   rc = stP1Gestured(   event, NONE ); break;
            case P2_GESTURED:   rc = stP2Gestured(   event, NONE ); break;
            case BOTH_GESTURED: rc = stBothGestured( event, NONE ); break;
            case P1_INFORMED:   rc = stP1Informed(   event, NONE ); break;
            case P2_INFORMED:   rc = stP2Informed(   event, NONE ); break;
            case BOTH_INFORMED: rc = stBothInformed( event, NONE ); break;
            case GAME_OVER:     rc = stGameOver(     event, NONE ); break;
        }
        // if score requested, and in correct state (ie, no error) return r/p/s
//        if (rc==RC_OK)
//        {
//            switch (state) {
//                case BOTH_GESTURED:
//                case P1_INFORMED:
//                case P2_INFORMED:
//                case BOTH_INFORMED:
                    score = makeScorecard( caller );
//                    break;
//            }
//        }
        score.rc = rc;
System.out.printf( " post_state=%s\n", state );
        return score;
    }
    
    private enum PlayerSelector {
        PLAYER1,
        PLAYER2,
        UNKNOWN_PLAYER,
    }
    
    private PlayerSelector whichPlayerCalledUs( Connection caller )
    {
        PlayerSelector sel = PlayerSelector.UNKNOWN_PLAYER;
        if (caller.getID()==player1.getID())
            sel = PlayerSelector.PLAYER1;
        else if (caller.getID()==player2.getID())
            sel =  PlayerSelector.PLAYER2;
        return sel;
    }
    
    public synchronized ResultCode doGesture( Connection caller, Gesture g )
    {
        State previousState   = state;
        PlayerSelector sel    = whichPlayerCalledUs(caller);
        ResultCode     rc     = RC_OK;
        EventType      event  = EventType.UNKNOWN_COMM;
        switch (sel)
        {
            case PLAYER1: event = EventType.P1_GESTURES;  break;
            case PLAYER2: event = EventType.P2_GESTURES;  break;
        }
        System.out.printf( "%s.doGesture(%s) %s\n", caller.getTeamName(), g, state );
        switch (state) 
        {
            case BEGIN_ROUND:   rc = stBeginRound(   event, g ); break;
            case P1_GESTURED:   rc = stP1Gestured(   event, g ); break;
            case P2_GESTURED:   rc = stP2Gestured(   event, g ); break;
            case BOTH_GESTURED: rc = stBothGestured( event, g ); break;
            case P1_INFORMED:   rc = stP1Informed(   event, g ); break;
            case P2_INFORMED:   rc = stP2Informed(   event, g ); break;
            case BOTH_INFORMED: rc = stBothInformed( event, g ); break;
            case GAME_OVER:     rc = stGameOver(     event, g ); break;
        }
        // if we just transitioned to BOTH_GESTURED, update score
        if (state==State.BOTH_GESTURED 
                && previousState!=state 
                && rc==RC_OK)
        {
            if (player1Gesture==player2Gesture)
                ties++;
            else if (  (player1Gesture==ROCK     && player2Gesture==PAPER   )
                    || (player1Gesture==PAPER    && player2Gesture==SCISSORS)
                    || (player1Gesture==SCISSORS && player2Gesture==ROCK    ))
                player2Score++;
            else
                player1Score++;
        }
        // attempt to push score, and update state if score was accepted
        if (state==State.BOTH_GESTURED
                && previousState!=state
                && rc==RC_OK) 
        {
            // TODO: only try notifying once if rejected; don't keep trying (works, but inefficient)
            boolean p1Notified = player1.notifyScore(makeScorecard(player1));
            boolean p2Notified = player2.notifyScore(makeScorecard(player2));
            if (p1Notified && !p2Notified)
                state = State.P1_INFORMED;
            else if (!p1Notified && p2Notified)
                state = State.P2_INFORMED;
            else if (p1Notified && p2Notified)
                state = State.BOTH_INFORMED;
        }
        
        return rc;
    }
    

    public void abortMatch( Connection killjoy, ResultCode rc )
    {
        // TODO abortMatch
        // TODO perhaps administrative abort match, which needs to inform both players
        state = State.GAME_OVER;
        PlayerSelector whichPlayer = whichPlayerCalledUs( killjoy );
        switch (whichPlayer) {
            case PLAYER1: player2.abortMatch(rc); break;
            case PLAYER2: player1.abortMatch(rc); break;
        }
    }

    public int getRoundNumber()
    {
        return this.round;
    }
    
    public Scorecard makeScorecard( Connection caller )
    {
        PlayerSelector sel  = whichPlayerCalledUs(caller);
        Scorecard score     = new Scorecard();
        score.maxRounds     = maxNumberOfRounds;
        score.ties          = ties;
        score.roundsPlayed  = round;
        score.rc            = RC_OK;
        if (sel==PlayerSelector.PLAYER1)
        {
            score.myScore         = player1Score;
            score.opponentScore   = player2Score;
            score.opponentGesture = player2Gesture;
        }
        else if (sel==PlayerSelector.PLAYER2)
        {
            score.myScore         = player2Score;
            score.opponentScore   = player1Score;            
            score.opponentGesture = player1Gesture;
        }
        else
            return null;
        
        return score;
    }
    
    
    /****************************************/
    /*          MAIN STATE MACHINE          */
    /****************************************/

    enum EventType {
        P1_GESTURES,
        P2_GESTURES,
        P1_QUERY,
        P2_QUERY,
        UNKNOWN_COMM,
    }
    
    ResultCode stBeginRound(EventType action, Gesture g )
    {
        ResultCode rc = RC_OK;
        switch (action) 
        {
            case P1_GESTURES:  player1Gesture = g; state = State.P1_GESTURED; break;
            case P2_GESTURES:  player2Gesture = g; state = State.P2_GESTURED; break;
            case P1_QUERY:
            case P2_QUERY:     rc = RC_OK; break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        return rc;
    }
    
    ResultCode stP1Gestured( EventType action, Gesture g ) 
    {
        ResultCode rc = RC_OK;
        switch (action) {
            case P1_GESTURES:  rc = E_ALREADY_GESTURED; break;
            case P2_GESTURES:  player2Gesture = g; state = State.BOTH_GESTURED; round++; break;
            case P1_QUERY:
            case P2_QUERY:     rc = E_IN_GAME_MODE; break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        return rc;
    }
        
    ResultCode stP2Gestured( EventType action, Gesture g ) 
    {
        ResultCode rc = RC_OK;
        switch (action) {
            case P1_GESTURES:  player2Gesture = g; state = State.BOTH_GESTURED; round++; break;
            case P2_GESTURES:  rc = E_ALREADY_GESTURED; break;
            case P1_QUERY:
            case P2_QUERY:     rc = E_IN_GAME_MODE; break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        return rc;
    }
    
    ResultCode stBothGestured( EventType action, Gesture g ) 
    {
        ResultCode rc = RC_OK;
        switch (action) {
            case P1_GESTURES:
            case P2_GESTURES:  rc = E_IN_INFO_MODE; break;
            case P1_QUERY:     state = State.P1_INFORMED; break;
            case P2_QUERY:     state = State.P2_INFORMED; break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        return rc;
    }
    
    ResultCode stP1Informed( EventType action, Gesture g ) 
    {
        ResultCode rc = RC_OK;
        switch (action) {
            case P1_GESTURES:
            case P2_GESTURES:  rc = E_IN_INFO_MODE; break;
            case P1_QUERY:     break;
            case P2_QUERY:     state = State.BOTH_INFORMED; break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        // if transitioning to BOTH_INFORMED, and rounds>=maxRounds, game has ended
        if (state==State.BOTH_INFORMED && round>=maxNumberOfRounds)
            state = State.GAME_OVER;
        return rc;
    }
    
    ResultCode stP2Informed( EventType action, Gesture g ) 
    {
        ResultCode rc = RC_OK;
        switch (action) {
            case P1_GESTURES:
            case P2_GESTURES:  rc = E_IN_INFO_MODE; break;
            case P1_QUERY:     state = State.BOTH_INFORMED; break;
            case P2_QUERY:     break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        // if transitioning to BOTH_INFORMED, and rounds>=maxRouonds, game has ended
        if (state==State.BOTH_INFORMED && round>=maxNumberOfRounds)
            state = State.GAME_OVER;
        return rc;
    }
    
    ResultCode stBothInformed( EventType action, Gesture g )
    {
        ResultCode rc = RC_OK;
        switch (action) {
            case P1_GESTURES:  player1Gesture = g; state = State.P1_GESTURED; break;
            case P2_GESTURES:  player2Gesture = g; state = State.P2_GESTURED; break;
            case P1_QUERY:     break;
            case P2_QUERY:     break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        return rc;
    }
        
    ResultCode stGameOver( EventType action, Gesture g )
    {
        ResultCode rc;
        switch (action) {
            case P1_GESTURES:  rc = E_MATCH_ENDED; break;
            case P2_GESTURES:  rc = E_MATCH_ENDED; break;
            case P1_QUERY:     rc = E_MATCH_ENDED; break;
            case P2_QUERY:     rc = E_MATCH_ENDED; break;
            case UNKNOWN_COMM: rc = E_UNRECOGNIZED_PLAYER; break;
            default:           rc = E_401; break;
        }
        return rc;
    }
        
    
}
