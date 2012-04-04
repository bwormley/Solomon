package solomonserver;

import java.io.Serializable;

public class PlayerEntry implements Serializable {
    
    PlayerEntry( String teamName, String origin, int ID, ConnectionState state )
    {
        this.teamName = teamName;
        this.origin   = origin;
        this.id       = ID;
        this.state    = state;
    }
    
    PlayerEntry( Connection player )
    {
        this.teamName = player.getTeamName();
        this.origin   = player.getOrigin();
        this.id       = player.getID();
        this.state    = player.getState();
    }
    
    public String teamName;
    public String origin;
    public int id;
    public ConnectionState state;
    
    @Override
    public String toString()
    {
        return teamName + " @ " + origin;
    }
}
