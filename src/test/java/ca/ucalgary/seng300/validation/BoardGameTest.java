package ca.ucalgary.seng300.validation;

import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardGameTest {

    private Board board;
    @BeforeEach
    void setUp() {
        board = new Board(5,5);
    }

    @Test
    void ThreeInRow(){
        board.addPiece(1,1,Piece.X);
        board.addPiece(2,1,Piece.X);
        board.addPiece(3,1,Piece.X);
        assertTrue(board.inRow(Piece.X,3));
        assertFalse(board.inRow(Piece.X,4));
        assertFalse(board.inRow(Piece.O,3));
    }

    @Test
    void TwoInEdgeRow(){
        board.addPiece(3,4,Piece.X);
        board.addPiece(4,4,Piece.X);
        assertTrue(board.inRow(Piece.X,2));
        assertFalse(board.inRow(Piece.X,3));
        assertFalse(board.inRow(Piece.O,2));
    }

    @Test
    void ThreeInColumn(){
        board.addPiece(1,1,Piece.X);
        board.addPiece(1,2,Piece.X);
        board.addPiece(1,3,Piece.X);
        assertTrue(board.inColumn(Piece.X,3));
        assertFalse(board.inColumn(Piece.X,4));
        assertFalse(board.inColumn(Piece.O,3));
    }

    @Test
    void TwoInEdgeColumn(){
        board.addPiece(4,3,Piece.X);
        board.addPiece(4,4,Piece.X);
        assertTrue(board.inColumn(Piece.X,2));
        assertFalse(board.inColumn(Piece.X,3));
        assertFalse(board.inColumn(Piece.O,2));
    }

    @Test
    void ThreeInLeftDiagonal(){
        board.addPiece(1,1,Piece.X);
        board.addPiece(2,2,Piece.X);
        board.addPiece(3,3,Piece.X);
        assertTrue(board.inDiagonal(Piece.X,3));
        assertFalse(board.inDiagonal(Piece.X,4));
        assertFalse(board.inDiagonal(Piece.O,3));
    }

    @Test
    void ThreeInRightDiagonal(){
        board.addPiece(1,3,Piece.X);
        board.addPiece(2,2,Piece.X);
        board.addPiece(3,1,Piece.X);
        assertTrue(board.inDiagonal(Piece.X,3));
        assertFalse(board.inDiagonal(Piece.X,4));
        assertFalse(board.inDiagonal(Piece.O,3));
    }

    @Test
    void TwoInTopRightDiagonal(){
        board.addPiece(3,1,Piece.X);
        board.addPiece(4,0,Piece.X);
        assertTrue(board.inDiagonal(Piece.X,2));
        assertFalse(board.inDiagonal(Piece.X,3));
        assertFalse(board.inDiagonal(Piece.O,2));
    }

    @Test
    void TwoInTopLeftDiagonal(){
        board.addPiece(0,0,Piece.X);
        board.addPiece(1,1,Piece.X);
        assertTrue(board.inDiagonal(Piece.X,2));
        assertFalse(board.inDiagonal(Piece.X,3));
        assertFalse(board.inDiagonal(Piece.O,2));
    }

    @Test
    void ThreeConsecutiveOfTwoTypes(){
        board.addPiece(0,0,Piece.X);
        board.addPiece(1,1,Piece.X);
        board.addPiece(2,2,Piece.X);
        board.addPiece(1,0,Piece.O);
        board.addPiece(2,0,Piece.O);
        board.addPiece(3,0,Piece.O);
        assertTrue(board.consecutivePieces(Piece.X,3));
        assertFalse(board.consecutivePieces(Piece.X,4));
        assertTrue(board.consecutivePieces(Piece.O,3));
    }

    @Test
    void FourInBottomRow(){
        board.addPiece(0,4,Piece.X);
        board.addPiece(1,4,Piece.X);
        board.addPiece(2,4,Piece.X);
        board.addPiece(3,4,Piece.X);
        assertTrue(board.consecutivePieces(Piece.X,4));
        assertFalse(board.consecutivePieces(Piece.X,5));
        assertFalse(board.consecutivePieces(Piece.O,4));
    }

    @Test
    void FourInLeftColumn(){
        board.addPiece(0,0,Piece.X);
        board.addPiece(0,1,Piece.X);
        board.addPiece(0,2,Piece.X);
        board.addPiece(0,3,Piece.X);
        assertTrue(board.consecutivePieces(Piece.X,4));
        assertFalse(board.consecutivePieces(Piece.X,5));
        assertFalse(board.consecutivePieces(Piece.O,4));
    }

    @Test
    void FourInMiddleDiagonal(){
        board.addPiece(0,0,Piece.X);
        board.addPiece(1,1,Piece.X);
        board.addPiece(2,2,Piece.X);
        board.addPiece(3,3,Piece.X);
        assertTrue(board.consecutivePieces(Piece.X,4));
        assertFalse(board.consecutivePieces(Piece.X,5));
        assertFalse(board.consecutivePieces(Piece.O,4));
    }

    @Test
    void BoardFull(){
        for (int i = 0; i < board.width; i++) {
            for (int j = 0; j < board.height; j++) {
                board.addPiece(i, j, Piece.X);
            }
        }
        assertTrue(board.full());
    }

    @Test
    void BoardAlmostFull(){
        for (int i = 0; i < board.width; i++) {
            for (int j = 0; j < board.height; j++) {
                if (i != 0 | j != 0) {
                    board.addPiece(i, j, Piece.X);
                }
            }
        }
        assertFalse(board.full());
    }

    @Test
    void BoardFullAlternating(){
        for (int i = 0; i < board.width; i++) {
            for (int j = 0; j < board.height; j++) {
                if (i % 2 == 0) {
                    board.addPiece(i, j, Piece.X);
                }
                else{
                    board.addPiece(i, j, Piece.O);
                }
            }
        }
        assertTrue(board.full());
    }

    @Test
    void BoardEmpty(){
        assertFalse(board.full());
    }

    @Test
    void boardInitiallyEqual(){
        Board secondBoard = new Board(5,5);
        Board thirdBoard = new Board(3,5);

        assertEquals(board, secondBoard);
        assertNotEquals(board, thirdBoard);
    }

    @Test
    void boardEqualAddition(){
        Board secondBoard = new Board(5,5);
        Board thirdBoard = new Board(5,5);

        board.addPiece(0,0,Piece.X);
        secondBoard.addPiece(0,0,Piece.X);
        thirdBoard.addPiece(1,0,Piece.X);

        assertEquals(board, secondBoard);
        assertNotEquals(board, thirdBoard);
    }

    @Test
    void boardSetLastMove(){
        board.setLastMove(5);
        int expected = 5;
        int actual = (int) board.getLastMove();
        assertEquals(expected,actual);
    }
}
