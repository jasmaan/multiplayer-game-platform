package ca.ucalgary.seng300.validation;

import ca.ucalgary.seng300.games.TicTacToe;
import ca.ucalgary.seng300.games.TicTacToeMove;
import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TicTacToeTest {
    private TicTacToe game;
    private Object state;

    @BeforeEach
    void setUp() {
        game = new TicTacToe();
        state = game.createInitialState(List.of("Player1", "Player2"));
    }

    @Test
    void InitialBoardIsEmpty() {
        Board board = (Board) state;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertNull(board.getPiece(i, j));
            }
        }
    }

    @Test
    void InvalidMoveWrongType() {
        Validation result = game.validateMove(state, "notRealMove");
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_FORMAT, result.getErrorCode());
    }

    @Test
    void InvalidMoveOutOfBounds() {
        TicTacToeMove move = new TicTacToeMove(5, 5, Piece.X);
        Validation result = game.validateMove(state, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.OUT_OF_BOUNDS, result.getErrorCode());
    }

    @Test
    void InvalidMoveNegativeCoordinates() {
        TicTacToeMove move = new TicTacToeMove(-1, 0, Piece.X);
        Validation result = game.validateMove(state, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.OUT_OF_BOUNDS, result.getErrorCode());
    }

    @Test
    void InvalidMoveOccupiedCell() {
        TicTacToeMove move = new TicTacToeMove(0, 0, Piece.X);
        state = game.applyMove(state, move);
        Validation result = game.validateMove(state, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.CELL_OCCUPIED, result.getErrorCode());
    }

    @Test
    void ValidMoveSuccess() {
        TicTacToeMove move = new TicTacToeMove(0, 0, Piece.X);
        Validation result = game.validateMove(state, move);
        assertTrue(result.isValid());
        assertEquals(ValidationError.NONE, result.getErrorCode());
    }

    @Test
    void Player1PlacesFirst() {
        state = game.applyMove(state, new TicTacToeMove(0, 0, Piece.X));
        Board board = (Board) state;
        assertEquals(Piece.X, board.getPiece(0, 0));
    }

    @Test
    void Player2PlacesSecond() {
        state = game.applyMove(state, new TicTacToeMove(0, 0, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 1, Piece.O));
        Board board = (Board) state;
        assertEquals(Piece.O, board.getPiece(1, 1));
    }

    @Test
    void GameNotOverOnEmptyBoard() {
        assertFalse(game.evaluateEnd(state));
    }

    @Test
    void GameOverOnHorizontalWin() {
        state = game.applyMove(state, new TicTacToeMove(0, 0, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 0, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(0, 1, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 1, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(0, 2, Piece.X));
        assertTrue(game.evaluateEnd(state));
        assertEquals("Player1", game.getWinner(state));
    }

    @Test
    void GameOverOnDraw() {
        state = game.applyMove(state, new TicTacToeMove(0, 0, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(0, 1, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(0, 2, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 1, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(1, 0, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 2, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(2, 1, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(2, 0, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(2, 2, Piece.X));
        assertTrue(game.evaluateEnd(state));
    }

    @Test
    void DrawReturnsNoWinner() {
        state = game.applyMove(state, new TicTacToeMove(0, 0, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(0, 1, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(0, 2, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 1, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(1, 0, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 2, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(2, 1, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(2, 0, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(2, 2, Piece.X));
        assertEquals(null, game.getWinner(state));
    }

    @Test
    void WinnerCorrectlyIdentified() {
        state = game.applyMove(state, new TicTacToeMove(0, 0, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 0, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(0, 1, Piece.X));
        state = game.applyMove(state, new TicTacToeMove(1, 1, Piece.O));
        state = game.applyMove(state, new TicTacToeMove(0, 2, Piece.X));
        assertEquals("Player1", game.getWinner(state));
    }


}
