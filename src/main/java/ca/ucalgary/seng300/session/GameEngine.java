package ca.ucalgary.seng300.session;

import ca.ucalgary.seng300.validation.Validation;

import java.util.List;

/**
 * Interface that all game implementations must implement.
 * Provides a game-agnostic contract for the platform to interact with any game.
 * Game engines return state only - they never call UI, lobby, or platform services.
 */
public interface GameEngine {

    /**
     * Create the initial game state for a set of players.
     * @param players list of player IDs
     * @return the initial board/game state
     */
    Object createInitialState(List<String> players);

    /**
     * Validate a proposed move against the current game state.
     * @param state current game state
     * @param move the move to validate
     * @return a Validation object indicating if the move is valid and any error messages
     */
    Validation validateMove(Object state, Object move);

    /**
     * Apply a validated move to the current state.
     * @param state current game state
     * @param move the move to apply
     * @return the new game state after the move
     */
    Object applyMove(Object state, Object move);

    /**
     * Check if the game has reached a terminal condition (win/draw).
     * @param state current game state
     * @return true if the game is over
     */
    boolean evaluateEnd(Object state);

    /**
     * Determine the winner of the game (if any).
     * @param state the terminal game state
     * @return the winning player's ID, or null for a draw
     */
    String getWinner(Object state);
}
