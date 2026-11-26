package ca.ucalgary.seng300.validation;

import ca.ucalgary.seng300.gameai.DinoBot;
import ca.ucalgary.seng300.games.*;
import ca.ucalgary.seng300.validation.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DinoBotTest {

    private Board board;

    @Test
    void botCompletesColumnTicTacToe() {
        board = new Board(3,3);
        board.addPiece(2,0,Piece.O);
        board.addPiece(2,2,Piece.O);
        board.addPiece(2,1,Piece.X);
        board.addPiece(0,0,Piece.X);
        board.addPiece(0,1,Piece.X);
        int[] actual = DinoBot.computeTicTacToeMove(board,Piece.X,Piece.O);
        int[] expected = new int[]{0,2};
        assertArrayEquals(expected, actual);
    }

    @Test
    void botBlocksDiagonalTicTacToe() {
        board = new Board(3,3);
        board.addPiece(0,0,Piece.O);
        board.addPiece(1,1,Piece.O);
        board.addPiece(0,1,Piece.X);
        board.addPiece(1,0,Piece.X);
        int[] actual = DinoBot.computeTicTacToeMove(board,Piece.X,Piece.O);
        int[] expected = new int[]{2,2};
        assertArrayEquals(expected, actual);
    }

    @Test
    void botBlocksRowTicTacToe() {
        board = new Board(3,3);
        board.addPiece(0,0,Piece.O);
        board.addPiece(1,0,Piece.O);
        board.addPiece(0,1,Piece.X);
        board.addPiece(2,2,Piece.X);
        int[] actual = DinoBot.computeTicTacToeMove(board,Piece.X,Piece.O);
        int[] expected = new int[]{2,0};
        assertArrayEquals(expected, actual);
    }

    @Test
    void botBlocksRowConnectFour() {
        board = new Board(6,7);
        board.addPiece(0,0,Piece.Red);
        board.addPiece(1,0,Piece.Red);
        board.addPiece(2,0,Piece.Red);
        int actual = DinoBot.computeConnectFourColumn(board,Piece.Blue,Piece.Red);
        int expected = 3;
        assertEquals(expected, actual);
    }

    @Test
    void botCompletesColumnConnectFour() {
        board = new Board(6,7);
        board.addPiece(0,0,Piece.Red);
        board.addPiece(0,1,Piece.Red);
        board.addPiece(0,2,Piece.Red);
        board.addPiece(3,0,Piece.Blue);
        board.addPiece(3,1,Piece.Blue);
        board.addPiece(3,2,Piece.Blue);
        int actual = DinoBot.computeConnectFourColumn(board,Piece.Blue,Piece.Red);
        int expected = 3;
        assertEquals(expected, actual);
    }

    @Test
    void botAddsToColumnConnectFour() {
        board = new Board(6,7);
        board.addPiece(2,0,Piece.Red);
        board.addPiece(2,1,Piece.Red);
        board.addPiece(3,0,Piece.Blue);
        board.addPiece(3,1,Piece.Red);
        board.addPiece(3,2,Piece.Red);
        board.addPiece(4,0,Piece.Blue);
        board.addPiece(4,1,Piece.Blue);
        int actual = DinoBot.computeConnectFourColumn(board,Piece.Blue,Piece.Red);
        int expected = 3;
        assertEquals(expected, actual);
    }

    @Test
    void botDoesNotForfeitConnectFour() {
        board = new Board(6,7);
        board.addPiece(1,0,Piece.Red);
        board.addPiece(2,0,Piece.Red);
        board.addPiece(2,1,Piece.Red);
        board.addPiece(3,0,Piece.Blue);
        board.addPiece(3,1,Piece.Red);
        board.addPiece(3,2,Piece.Red);
        board.addPiece(4,0,Piece.Blue);
        board.addPiece(4,1,Piece.Blue);
        int actual = DinoBot.computeConnectFourColumn(board,Piece.Blue,Piece.Red);
        int expected = 2;
        assertEquals(expected, actual);
    }

    @Test
    void botTakesOpportunityCheckers() {
        board = new Board(8,8);
        board.addPiece(1,0,Piece.White);
        board.addPiece(5,0,Piece.White);
        board.addPiece(2,1,Piece.Black);

        CheckersMove actual = DinoBot.computeCheckersMove(board,Piece.White);
        CheckersMove expected = new CheckersMove(new CheckersMove.Coordinate(1,0),List.of(new CheckersMove.Coordinate(3,2)));
        assertEquals(expected, actual);
    }

    @Test
    void botTakesDoubleJumpCheckers() {
        board = new Board(8,8);
        board.addPiece(1,0,Piece.White);
        board.addPiece(5,0,Piece.White);
        board.addPiece(2,1,Piece.Black);
        board.addPiece(4,1,Piece.Black);
        board.addPiece(4,3,Piece.Black);

        CheckersMove actual = DinoBot.computeCheckersMove(board,Piece.White);
        CheckersMove expected = new CheckersMove(new CheckersMove.Coordinate(1,0),List.of(new CheckersMove.Coordinate(3,2),new CheckersMove.Coordinate(5,4)));
        assertEquals(expected, actual);
    }

    @Test
    void botPromotes() {
        board = new Board(8,8);
        board.addPiece(0,0,Piece.White);
        board.addPiece(2,0,Piece.White);
        board.addPiece(0,6,Piece.White);

        CheckersMove actual = DinoBot.computeCheckersMove(board,Piece.White);
        CheckersMove expected = new CheckersMove(new CheckersMove.Coordinate(0,6),List.of(new CheckersMove.Coordinate(1,7)));
        assertEquals(expected, actual);
    }
}
