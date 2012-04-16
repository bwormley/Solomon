package solomonserver;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 *Simple aggregation for matches, for efficiency.  
 * (The Match objects themselves do almost all the work.)
 * 
 * @author bwormley
 */
public class MatchTable {
    final private static Logger l = Logger.getLogger("com.cs151.solomon.server");
    
    private MatchTable() {}
    
    static MatchTable _instance = null;
    
    public static synchronized MatchTable getInstance() {
        if (_instance==null)
            _instance = new MatchTable();
        return _instance;
    }
    
    ArrayList<Match> list = new ArrayList<Match>();
    
    void addMatch( Match match ) {
        list.add(match);
    }
    
    void removeMatch( Connection player, Match match ) {
        list.remove( match );
    }
    
    enum Filter { 
        NONE,         // return all matches in table
        IN_PLAY,     // return all matches in active play
        ZOMBIE,      // return matches that 
        TERMINATED } // return matches finished but persisting in table
    
    ArrayList<Match> getMatchList( Filter filter ) {
        return list;
    } 
    
}
