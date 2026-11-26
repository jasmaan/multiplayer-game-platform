package ca.ucalgary.seng300.games;
import ca.ucalgary.seng300.session.GameEngine;
import ca.ucalgary.seng300.validation.Validation;

import java.util.List;

/**
 * An abstract class that implements Game Engine
 * Represents a game with a grid system that contains pieces
 */
abstract class BoardGame implements GameEngine{

    // an object representing the current board
    protected Board board;

    /**
     * Create the initial game state for a set of players.
     * @param players list of player IDs
     * @return the initial board/game state
     */
    public abstract Object createInitialState(List<String> players);

    /**
     * Validate whether a move is legal given the current state.
     * @param state current game state
     * @param move the proposed move
     * @return true if move is valid
     */
    public abstract Validation validateMove(Object state, Object move);

    /**
     * Apply a validated move to the current state.
     * @param state current game state
     * @param move the move to apply
     * @return the new game state after the move
     */
    public Object applyMove(Object state, Object move){
        board = (Board) state;
        board.setLastMove(move);
        return state;
    }

    /**
     * Check if the game has reached a terminal condition (win/draw).
     * @param state current game state
     * @return true if the game is over
     */
    public boolean evaluateEnd(Object state){
        board = (Board) state;
        return !board.full();
    }

    /**
     * Determine the winner of the game (if any).
     * @param state the terminal game state
     * @return the winning player's ID, or null for a draw
     */
    public abstract String getWinner(Object state);
}

