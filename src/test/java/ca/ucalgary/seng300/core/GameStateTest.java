package ca.ucalgary.seng300.core;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ca.ucalgary.seng300.session.GameState;

public class GameStateTest {
    
    @Test
    void constructor_initilizesFieldsCorrectly() {
        GameState state = new GameState("session1", "TicTacToe", List.of("alice", "bob"));

        assertEquals("session1", state.getSessionId());
        assertEquals("TicTacToe", state.getGameType());
        assertEquals(GameStatus.STARTING, state.getStatus());
        assertEquals("alice", state.getActivePlayerId());
        assertEquals(0, state.getCurrentTurnIndex());
        assertEquals(List.of("alice", "bob"), state.getPlayerOrder());
        assertNull(state.getBoard());
        assertTrue(state.getMoveHistory().isEmpty());
    }

    @Test
    void constructor_throwsException_whenPlayersListIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            new GameState("session1", "TicTacToe", null));
        assertEquals("Players list cannot be empty", exception.getMessage());
    }

    @Test
    void advanceTurn_switchesToNextPlayer() {
        GameState state = new GameState("Session1", "TicTacToe", List.of("alice", "bob"));

        state.advanceTurn();
        assertEquals("bob", state.getActivePlayerId());
        assertEquals(1,state.getCurrentTurnIndex());
    }

    @Test
    void advanceTurn_wrapsAroundToFirstPlayer() {
        GameState state = new GameState("Session1", "TicTacToe", List.of("alice", "bob"));

        state.advanceTurn();
        state.advanceTurn();

        assertEquals("alice", state.getActivePlayerId());
        assertEquals(0,state.getCurrentTurnIndex());
    }

    @Test
    void advanceTurn_cyclesThroughMultiplePlayers() {
        GameState state = new GameState("session1", "GameType", List.of("alice", "bob", "charlie"));

        state.advanceTurn();
        assertEquals("bob", state.getActivePlayerId());
        assertEquals(1, state.getCurrentTurnIndex());

        state.advanceTurn();
        assertEquals("charlie", state.getActivePlayerId());
        assertEquals(2, state.getCurrentTurnIndex());

        state.advanceTurn();
        assertEquals("alice", state.getActivePlayerId());
        assertEquals(0, state.getCurrentTurnIndex());
    }

    @Test
    void setBoard_updatesBoard() {
        GameState state = new GameState("session1", "GameType", List.of("alice", "bob"));
        String[][] board = new String[3][3];

        state.setBoard(board);
        assertEquals(board, state.getBoard());
    }

    @Test
    void addMoveToHistory_addsMoveSuccessfully() {
        GameState state = new GameState("session1", "TicTacToe", List.of("alice", "bob"));
        state.addMoveToHistory("move1");
        state.addMoveToHistory("move2");

        List<Object> history = state.getMoveHistory();
        assertEquals(2, history.size());
        assertEquals("move1", history.get(0));
        assertEquals("move2", history.get(1));
    }

    @Test
    void getMoveHistory_returnsDefensiveCopy() {
        GameState state = new GameState("session1", "TicTacToe", List.of("alice", "bob"));
        state.addMoveToHistory("move1");

        List<Object> history = state.getMoveHistory();
        history.add("move2");

        assertEquals(1, state.getMoveHistory().size());
        assertEquals("move1", state.getMoveHistory().get(0));
    }

    @Test
    void getPlayerOrder_returnsDefensiveCopy() {
        GameState state = new GameState("session1", "TicTacToe", List.of("alice", "bob"));

        List<String> playerOrder = state.getPlayerOrder();
        playerOrder.add("charlie");

        assertEquals(2, state.getPlayerOrder().size());
        assertEquals(List.of("alice", "bob"), state.getPlayerOrder());
    }

    @Test
    void setStatus_updatesStatus() {
        GameState state = new GameState("session1", "TicTacToe", List.of("alice", "bob"));

        state.setStatus(GameStatus.IN_PROGRESS);
        assertEquals(GameStatus.IN_PROGRESS, state.getStatus());
    }
}
