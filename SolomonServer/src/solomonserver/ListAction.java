package solomonserver;

import java.io.Serializable;

/**
 *
 */
public class ListAction implements Serializable {
    
    public ListAction( Action event, PlayerEntry player ) {
        this.event  = event;
        this.player = player;
    }
    
    public enum Action { ADD, REMOVE };
    
    public Action event;
    public PlayerEntry player;
}
