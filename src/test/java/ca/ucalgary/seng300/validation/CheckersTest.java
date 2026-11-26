package ca.ucalgary.seng300.validation;

import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.Checkers;
import ca.ucalgary.seng300.games.CheckersMove;
import ca.ucalgary.seng300.games.Piece;
import ca.ucalgary.seng300.validation.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckersTest {

    private Checkers checkers;
    private Board checkerBoard;
    private List<String> players;

    @BeforeEach
    void setUp() {
        checkers = new Checkers();
        players = List.of("P1", "P2");
        checkerBoard = (Board) checkers.createInitialState(players);
    }

    @Test
    void boardSetup(){
        Board secondBoard = new Board(8,8);
        secondBoard.addPiece(1,0,Piece.White);
        secondBoard.addPiece(3,0,Piece.White);
        secondBoard.addPiece(5,0,Piece.White);
        secondBoard.addPiece(7,0,Piece.White);

        secondBoard.addPiece(0,1,Piece.White);
        secondBoard.addPiece(2,1,Piece.White);
        secondBoard.addPiece(4,1,Piece.White);
        secondBoard.addPiece(6,1,Piece.White);

        secondBoard.addPiece(1,2,Piece.White);
        secondBoard.addPiece(3,2,Piece.White);
        secondBoard.addPiece(5,2,Piece.White);
        secondBoard.addPiece(7,2,Piece.White);

        secondBoard.addPiece(0,7,Piece.Black);
        secondBoard.addPiece(2,7,Piece.Black);
        secondBoard.addPiece(4,7,Piece.Black);
        secondBoard.addPiece(6,7,Piece.Black);

        secondBoard.addPiece(1,6,Piece.Black);
        secondBoard.addPiece(3,6,Piece.Black);
        secondBoard.addPiece(5,6,Piece.Black);
        secondBoard.addPiece(7,6,Piece.Black);

        secondBoard.addPiece(0,5,Piece.Black);
        secondBoard.addPiece(2,5,Piece.Black);
        secondBoard.addPiece(4,5,Piece.Black);
        secondBoard.addPiece(6,5,Piece.Black);

        assertEquals(checkerBoard, secondBoard);
    }

    @Test
    void moveWhiteDownLeft(){
        Board actual = new Board(8,8);
        actual.addPiece(1,0,Piece.White);
        actual = (Board) checkers.applyMove(actual, new CheckersMove(new CheckersMove.Coordinate(1,0),List.of(new CheckersMove.Coordinate(0,1))));

        Board expected = new Board(8,8);
        expected.addPiece(0,1,Piece.White);

        assertEquals(expected, actual);
    }

    @Test
    void moveBlackUpRight(){
        Board actual = new Board(8,8);
        actual.addPiece(1,2,Piece.Black);
        actual = (Board) checkers.applyMove(actual, new CheckersMove(new CheckersMove.Coordinate(1,2),List.of(new CheckersMove.Coordinate(2,1))));

        Board expected = new Board(8,8);
        expected.addPiece(2,1,Piece.Black);

        assertEquals(expected, actual);
    }

    @Test
    void moveBlackKingJump(){
        Board actual = new Board(8,8);
        actual.addPiece(1,1,Piece.BlackKing);
        actual.addPiece(2,2,Piece.White);
        actual = (Board) checkers.applyMove(actual, new CheckersMove(new CheckersMove.Coordinate(1,1),List.of(new CheckersMove.Coordinate(3,3))));

        Board expected = new Board(8,8);
        expected.addPiece(3,3,Piece.BlackKing);

        assertEquals(expected, actual);
    }

    @Test
    void moveWhiteDoubleJumpZigZag(){
        Board actual = new Board(8,8);
        actual.addPiece(1,1,Piece.White);
        actual.addPiece(2,2,Piece.BlackKing);
        actual.addPiece(2,4,Piece.Black);
        actual.addPiece(4,2,Piece.Black);
        actual = (Board) checkers.applyMove(actual, new CheckersMove(new CheckersMove.Coordinate(1,1),List.of(new CheckersMove.Coordinate(3,3),new CheckersMove.Coordinate(1,5))));

        Board expected = new Board(8,8);
        expected.addPiece(1,5,Piece.White);
        expected.addPiece(4,2,Piece.Black);

        assertEquals(expected, actual);
    }

    @Test
    void moveWhiteDoubleJumpStraight(){
        Board actual = new Board(8,8);
        actual.addPiece(1,1,Piece.White);
        actual.addPiece(2,2,Piece.BlackKing);
        actual.addPiece(2,4,Piece.Black);
        actual.addPiece(4,4,Piece.Black);
        actual = (Board) checkers.applyMove(actual, new CheckersMove(new CheckersMove.Coordinate(1,1),List.of(new CheckersMove.Coordinate(3,3),new CheckersMove.Coordinate(5,5))));

        Board expected = new Board(8,8);
        expected.addPiece(5,5,Piece.White);
        expected.addPiece(2,4,Piece.Black);

        assertEquals(expected, actual);
    }

    //Test for kingPromotion
    @Test
    void whitePromotesOnLastRow() {
        Board actual = new Board(8, 8);
        actual.addPiece(1, 6, Piece.White);

        actual = (Board) checkers.applyMove(
                actual,
                new CheckersMove(
                        new CheckersMove.Coordinate(1, 6),
                        List.of(new CheckersMove.Coordinate(0, 7))
                )
        );

        assertEquals(Piece.WhiteKing, actual.getPiece(0, 7));
    }

    @Test
    void whiteDoesNotPromoteBeforeLastRow() {
        Board actual = new Board(8, 8);
        actual.addPiece(1, 5, Piece.White);

        actual = (Board) checkers.applyMove(
                actual,
                new CheckersMove(
                        new CheckersMove.Coordinate(1, 5),
                        List.of(new CheckersMove.Coordinate(0, 6))
                )
        );

        assertEquals(Piece.White, actual.getPiece(0, 6));
    }

    @Test
    void blackPromotesOnFirstRow() {
        Board actual = new Board(8, 8);
        actual.addPiece(1, 1, Piece.Black);

        actual = (Board) checkers.applyMove(
                actual,
                new CheckersMove(
                        new CheckersMove.Coordinate(1, 1),
                        List.of(new CheckersMove.Coordinate(0, 0))
                )
        );

        assertEquals(Piece.BlackKing, actual.getPiece(0, 0));
    }

    @Test
    void blackDoesNotPromoteBeforeFirstRow() {
        Board actual = new Board(8, 8);
        actual.addPiece(1, 2, Piece.Black);

        actual = (Board) checkers.applyMove(
                actual,
                new CheckersMove(
                        new CheckersMove.Coordinate(1, 2),
                        List.of(new CheckersMove.Coordinate(0, 1))
                )
        );

        assertEquals(Piece.Black, actual.getPiece(0, 1));
    }

    @Test
    void legalMovesForPieceReturnsSimpleMoves() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);

        List<CheckersMove> legalMoves = checkers.getLegalMovesForPiece(
                board,
                new CheckersMove.Coordinate(3, 3)
        );

        assertEquals(2, legalMoves.size());
        assertTrue(containsMove(legalMoves, 3, 3, List.of(new int[]{2, 4})));
        assertTrue(containsMove(legalMoves, 3, 3, List.of(new int[]{4, 4})));
    }

    @Test
    void legalMovesForPiecePrefersCaptureMoves() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        board.addPiece(4, 4, Piece.Black);

        List<CheckersMove> legalMoves = checkers.getLegalMovesForPiece(
                board,
                new CheckersMove.Coordinate(3, 3)
        );

        assertEquals(1, legalMoves.size());
        assertTrue(containsMove(legalMoves, 3, 3, List.of(new int[]{5, 5})));
    }

    @Test
    void legalMovesForPieceReturnsMultiJumpPath() {
        Board board = new Board(8, 8);
        board.addPiece(1, 1, Piece.White);
        board.addPiece(2, 2, Piece.Black);
        board.addPiece(4, 4, Piece.Black);

        List<CheckersMove> legalMoves = checkers.getLegalMovesForPiece(
                board,
                new CheckersMove.Coordinate(1, 1)
        );

        assertEquals(1, legalMoves.size());
        assertTrue(containsMove(legalMoves, 1, 1, List.of(new int[]{3, 3}, new int[]{5, 5})));
    }

    @Test
    void legalMovesForPieceStopsChainWhenPieceCrowns() {
        Board board = new Board(8, 8);
        board.addPiece(1, 5, Piece.White);
        board.addPiece(2, 6, Piece.Black);
        board.addPiece(4, 6, Piece.Black);

        List<CheckersMove> legalMoves = checkers.getLegalMovesForPiece(
                board,
                new CheckersMove.Coordinate(1, 5)
        );

        assertEquals(1, legalMoves.size());
        assertTrue(containsMove(legalMoves, 1, 5, List.of(new int[]{3, 7})));
        assertFalse(containsMove(legalMoves, 1, 5, List.of(new int[]{3, 7}, new int[]{5, 5})));
    }

    // tests for validate move
    @Test
    void invalidMoveType() {
        var result = checkers.validateMove(checkerBoard, "not a move");
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_FORMAT, result.getErrorCode());
    }

    @Test
    void targetOutOfBounds() {
        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(-1, 0),
                List.of(new CheckersMove.Coordinate(0, 1))
        );

        var result = checkers.validateMove(checkerBoard, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.OUT_OF_BOUNDS, result.getErrorCode());
    }

    @Test
    void emptyStartSquare() {
        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(3, 3),
                List.of(new CheckersMove.Coordinate(4, 4))
        );

        var result = checkers.validateMove(checkerBoard, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_MOVE, result.getErrorCode());
    }

    @Test
    void validSingleMove() {
        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(1, 2),
                List.of(new CheckersMove.Coordinate(0, 3))
        );

        assertTrue(checkers.validateMove(checkerBoard, move).isValid());
    }

    @Test
    void whiteCannotMoveBackward() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);

        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(3, 3),
                List.of(new CheckersMove.Coordinate(2, 2))
        );

        var result = checkers.validateMove(board, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_MOVE, result.getErrorCode());
    }

    @Test
    void blackCannotMoveBackward() {
        Board board = new Board(8, 8);
        board.addPiece(3, 4, Piece.Black);

        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(3, 4),
                List.of(new CheckersMove.Coordinate(4, 5))
        );

        var result = checkers.validateMove(board, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_MOVE, result.getErrorCode());
    }

    @Test
    void destinationAlreadyOccupied() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        board.addPiece(4, 4, Piece.White);

        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(3, 3),
                List.of(new CheckersMove.Coordinate(4, 4))
        );

        var result = checkers.validateMove(board, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.CELL_OCCUPIED, result.getErrorCode());
    }

    @Test
    void validCaptureMove() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        board.addPiece(4, 4, Piece.Black);

        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(3, 3),
                List.of(new CheckersMove.Coordinate(5, 5))
        );

        assertTrue(checkers.validateMove(board, move).isValid());
    }

    @Test
    void cannotCaptureOwnPiece() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        board.addPiece(4, 4, Piece.White);

        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(3, 3),
                List.of(new CheckersMove.Coordinate(5, 5))
        );

        var result = checkers.validateMove(board, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_MOVE, result.getErrorCode());
    }

    @Test
    void cannotMoveTwice() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        CheckersMove firstMove = new CheckersMove(
                new CheckersMove.Coordinate(3, 3),
                List.of(new CheckersMove.Coordinate(5, 5))
        );
        CheckersMove secondMove = new CheckersMove(
                new CheckersMove.Coordinate(5, 5),
                List.of(new CheckersMove.Coordinate(7, 7))
        );
        board = (Board) checkers.applyMove(board,firstMove);

        var result = checkers.validateMove(board, secondMove);
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_MOVE, result.getErrorCode());
    }

    @Test
    void cannotJumpWithoutCapture() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);

        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(3, 3),
                List.of(new CheckersMove.Coordinate(5, 5))
        );

        var result = checkers.validateMove(board, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_MOVE, result.getErrorCode());
    }

    @Test
    void promotedPieceCannotContinueCaptureChain() {
        Board board = new Board(8, 8);
        board.addPiece(1, 5, Piece.White);
        board.addPiece(2, 6, Piece.Black);
        board.addPiece(4, 6, Piece.Black);

        CheckersMove move = new CheckersMove(
                new CheckersMove.Coordinate(1, 5),
                List.of(new CheckersMove.Coordinate(3, 7), new CheckersMove.Coordinate(5, 5))
        );

        var result = checkers.validateMove(board, move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_MOVE, result.getErrorCode());
    }

    @Test
    void generatedKingLoopCaptureValidatesSuccessfully() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.WhiteKing);
        board.addPiece(4, 4, Piece.Black);
        board.addPiece(4, 6, Piece.Black);
        board.addPiece(2, 6, Piece.Black);
        board.addPiece(2, 4, Piece.Black);

        List<CheckersMove> legalMoves = checkers.getLegalMovesForPiece(
                board,
                new CheckersMove.Coordinate(3, 3)
        );

       assertEquals(2, legalMoves.size());

        for (CheckersMove legalMove : legalMoves) {
            assertTrue(checkers.validateMove(board, legalMove).isValid());
        }
    }

    @Test
    void gameNotOverAtStart() {
        assertFalse(checkers.evaluateEnd(checkerBoard));
    }

    @Test
    void gameOverWhenNoPiecesLeft() {
        // only white pieces, black has none — game should be over
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        assertTrue(checkers.evaluateEnd(board));
    }

    @Test
    void gameNotOverWithBothPlayersHavingPieces() {
        // both players have pieces and moves — game should not be over
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        board.addPiece(3, 5, Piece.Black);
        assertFalse(checkers.evaluateEnd(board));
    }

    @Test
    void whiteWinsWhenBlackHasNoPieces() {
        Board board = new Board(8, 8);
        board.addPiece(3, 3, Piece.White);
        assertEquals("P1", checkers.getWinner(board));
    }

    @Test
    void blackWinsWhenWhiteHasNoPieces() {
        Board board = new Board(8, 8);
        board.addPiece(3, 5, Piece.Black);
        assertEquals("P2", checkers.getWinner(board));
    }

    @Test
    void noWinnerDuringActiveGame() {
        assertNull(checkers.getWinner(checkerBoard));   // fresh board
    }

    // Test helper: confirms that the generated legal moves include one exact path
    // from the expected starting square. This is important for multi-jumps because
    // matching only the final landing square would not prove the full path is correct.
    private boolean containsMove(List<CheckersMove> legalMoves, int startX, int startY, List<int[]> path) {
        for (CheckersMove move : legalMoves) {
            if (move.target.x != startX || move.target.y != startY) {
                continue;
            }
            if (move.moves.size() != path.size()) {
                continue;
            }

            boolean matches = true;
            // Compare each landing square in order so chained captures must match exactly.
            for (int i = 0; i < path.size(); i++) {
                int[] expected = path.get(i);
                CheckersMove.Coordinate actual = move.moves.get(i);
                if (actual.x != expected[0] || actual.y != expected[1]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    @Test
    void coordinatesEqual() {
        CheckersMove.Coordinate c1 = new CheckersMove.Coordinate(3, 3);
        CheckersMove.Coordinate c2 = new CheckersMove.Coordinate(3, 3);
        CheckersMove.Coordinate c3 = new CheckersMove.Coordinate(2, 4);

        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
    }

    @Test
    void checkersMoveEqual() {
        CheckersMove move1 = new CheckersMove(new CheckersMove.Coordinate(3, 3), List.of(new CheckersMove.Coordinate(3, 3),new CheckersMove.Coordinate(5, 5)));
        CheckersMove move2 = new CheckersMove(new CheckersMove.Coordinate(3, 3), List.of(new CheckersMove.Coordinate(3, 3),new CheckersMove.Coordinate(5, 5)));
        CheckersMove move3 = new CheckersMove(new CheckersMove.Coordinate(3, 3), List.of(new CheckersMove.Coordinate(5, 5),new CheckersMove.Coordinate(3, 3)));
        CheckersMove move4 = new CheckersMove(new CheckersMove.Coordinate(5, 5), List.of(new CheckersMove.Coordinate(3, 3),new CheckersMove.Coordinate(5, 5)));

        assertEquals(move1, move2);
        assertNotEquals(move1, move3);
        assertNotEquals(move1, move4);
        assertNotEquals(move3, move4);
    }
}
