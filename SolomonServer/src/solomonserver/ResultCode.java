package solomonserver;

import java.io.Serializable;

/**
 *
 * @author bwormley
 */
public enum ResultCode implements Serializable {
    RC_ROCK,                /* opponent played ROCK in this round */
    RC_PAPER,               /* opponent played PAPER in this round */
    RC_SCISSORS,            /* opponent played SCISSORS in this round */
    RC_MATCH_ENDED,
    RC_CONTINUE,            /* returned by RPS callback, indicating ready to resume game */
    RC_OK,                  /* successful result */
    E_401,                  /* major problem, completely unexpected. reboot. */
    E_IN_GAME_MODE,         /* next call should be doGesture */
    E_IN_INFO_MODE,         /* next call should be getGesture */
    E_UNRECOGNIZED_PLAYER,  /* unknown or illegal player */
    E_ALREADY_GESTURED,     /* already received a gesture for this round */
    E_REQUEST_DENIED,       /* challenged player denies request to play a match */
    E_WRONG_STATE,
    E_MATCH_ENDED,
    E_SERVER_NOT_FOUND,
    E_SERVER_DOWN,
    E_NOT_IMPLEMENTED,
    E_REDUNDANT_PLAYER,      /* reason for terminating a connection (eg, after a client crashes and re-registers) */
    
    /* Client-side codes */
    E_NO_CONNECTION,         /* no established connection with server */
}
