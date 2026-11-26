package ca.ucalgary.seng300.core;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.leaderboard.Leaderboard;
import ca.ucalgary.seng300.matchmaking.MatchMaking;
import ca.ucalgary.seng300.room.RoomManager;
import ca.ucalgary.seng300.session.GameSessionManager;

class MatchMakingTest {
    private MatchMaking matchMaking;
    private Leaderboard leaderboard;

    @BeforeEach
    void setUp(){
        leaderboard = new Leaderboard();
        matchMaking = new MatchMaking(new RoomManager(new GameSessionManager(new EventBus())), new EventBus(), leaderboard);
    }

    @Test
    void addRemovePlayersFromQueue(){
        matchMaking.addPlayerToQueue("Joe", "chess");
        matchMaking.addPlayerToQueue("Bob", "chess");
        assertEquals(2, matchMaking.getQueueSize("chess"));
        
        matchMaking.removePlayer("Joe");
        assertEquals(1, matchMaking.getQueueSize("chess"));
        assertTrue(matchMaking.isPlayerInQueue("Bob"));
        assertFalse(matchMaking.isPlayerInQueue("Joe"));
    }
    
    @Test
    void cannotDuplicatePlayer(){
        matchMaking.addPlayerToQueue("Bob", "chess");
        matchMaking.addPlayerToQueue("Bob", "chess");
        assertEquals(1, matchMaking.getQueueSize("chess"));
    }
    
    @Test
    void matchedPlayersRemoved(){
        matchMaking.addPlayerToQueue("Joe", "chess");
        matchMaking.addPlayerToQueue("Bob", "chess");
        List<String> matched = matchMaking.matchPlayers("chess");
        assertEquals(2, matched.size());
        assertEquals(0, matchMaking.getQueueSize("chess"));
        assertTrue(matched.contains("Joe"));
        assertTrue(matched.contains("Bob"));
    }
    
    @Test
    void returnEmptyIfNotEnoughPlayers(){
        matchMaking.addPlayerToQueue("Joe", "chess");
        List<String> matched = matchMaking.matchPlayers("chess");
        assertEquals(1, matchMaking.getQueueSize("chess"));
        assertTrue(matched.isEmpty());
    }
    
    @Test
    void getQueuePositionReturnsPosition(){
        matchMaking.addPlayerToQueue("Joe", "chess");
        matchMaking.addPlayerToQueue("Bob", "chess");
        int positionJoe = matchMaking.getQueuePosition("Joe");
        int positionBob = matchMaking.getQueuePosition("Bob");
        assertTrue(positionJoe == 0);
        assertTrue(positionBob == 1);
        assertNotEquals(positionBob, positionJoe);
    }

    @Test
    void ensureWrongGameTypeNotMatched(){
        matchMaking.addPlayerToQueue("Joe", "chess");
        matchMaking.addPlayerToQueue("Bob", "checkers");
        List<String> matchedChess = matchMaking.matchPlayers("chess");
        List<String> matchedCheckers = matchMaking.matchPlayers("checkers");
        assertEquals(1, matchMaking.getQueueSize("chess"));
        assertEquals(1, matchMaking.getQueueSize("checkers"));
        assertTrue(matchedChess.isEmpty());
        assertTrue(matchedCheckers.isEmpty());
    }

    @Test
    void closestRankedPlayersMatched(){
        // Setting up Alice MMR
        for(int i = 0; i < 20; i++){
            leaderboard.updatePlayerRanking("Alice", "chess", "win");
        }
        // Give Bob and Joe a similar mmr
        for(int i = 0; i < 5; i++){
            leaderboard.updatePlayerRanking("Bob", "chess", "win");
            leaderboard.updatePlayerRanking("Joe", "chess", "win");
        }

        matchMaking.addPlayerToQueue("Alice", "chess");
        matchMaking.addPlayerToQueue("Bob", "chess");
        matchMaking.addPlayerToQueue("Joe", "chess");

        List<String> matched = matchMaking.matchPlayers("chess");

        assertTrue(matched.contains("Bob"));
        assertTrue(matched.contains("Joe"));
        assertFalse(matched.contains("Alice"));
        assertEquals(1, matchMaking.getQueueSize("chess"));
    }

    @Test
    void rankTooFarApart(){
        // Alice will have 1000 MMR, Bob will have 0 MMR.
        // Time based MMR changes adds 50 per 15s interval
        // requires 18 intervals (270 seconds), so this test is stable under any
        // realistic runtime.
        for(int i = 0; i < 20; i++){
            leaderboard.updatePlayerRanking("Alice", "chess", "win");
        }

        matchMaking.addPlayerToQueue("Alice", "chess");
        matchMaking.addPlayerToQueue("Bob", "chess");

        List<String> matched = matchMaking.matchPlayers("chess");

        assertTrue(matched.isEmpty());
        assertEquals(2, matchMaking.getQueueSize("chess"));
    }
}
