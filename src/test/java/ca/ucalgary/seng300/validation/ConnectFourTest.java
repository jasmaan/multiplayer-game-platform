package ca.ucalgary.seng300.validation;
import ca.ucalgary.seng300.games.ConnectFour;
import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.ConnectFour.*;
import ca.ucalgary.seng300.games.ConnectFourMove;
import ca.ucalgary.seng300.games.Piece;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
public class ConnectFourTest {
    Board board;
    List<String >players = List.of("P1", "P2");
    ConnectFour connectFour;

    @BeforeEach
    public void setUp(){
        connectFour = new ConnectFour();
        board = (Board) connectFour.createInitialState(players);
    }

    @Test
    void validMove(){
        ConnectFourMove move = new ConnectFourMove("P1", 0);
        assertTrue(connectFour.validateMove(board,move).isValid());
    }

    @Test
    void invalidMoveType(){
        Validation result = connectFour.validateMove(board, "bad move");
        assertFalse(result.isValid());
        assertEquals(ValidationError.INVALID_FORMAT, result.getErrorCode());
        assertEquals("Move must be of type ConnectFourMove(playerID, column index)", result.getMessage());
    }

    @Test
    void unauthorizedPlayer(){
        ConnectFourMove move = new ConnectFourMove("P3", 3);
        Validation result = connectFour.validateMove(board,move);

        assertEquals(ValidationError.UNAUTHORIZED_PLAYER, result.getErrorCode());
        assertEquals("Player P3 does not exist", result.getMessage());
        assertFalse(result.isValid());
    }

    @Test
    void ColumnOutOfBound(){
        ConnectFourMove move = new ConnectFourMove("P1", 7);
        Validation result = connectFour.validateMove(board,move);

        assertFalse(result.isValid());
        assertEquals(ValidationError.OUT_OF_BOUNDS, result.getErrorCode());
        assertEquals("The column is out of bounds",result.getMessage());
    }

    @Test
    void Column_outOfBound2(){
        ConnectFourMove move = new ConnectFourMove("P1", -1);
        Validation result = connectFour.validateMove(board,move);

        assertFalse(result.isValid());
        assertEquals(ValidationError.OUT_OF_BOUNDS, result.getErrorCode());
        assertEquals("The column is out of bounds",result.getMessage());
    }

    @Test
    void Column_Full(){
        ConnectFourMove move = new ConnectFourMove("P1", 4);
        for(int i = 0; i < 6; i++){
            board.addPiece(i, 4, Piece.Red);
        }

        Validation result = connectFour.validateMove(board,move);
        assertFalse(result.isValid());
        assertEquals(ValidationError.COLUMN_FULL, result.getErrorCode());
        assertEquals("The column is already full", result.getMessage());
    }

    @Test
    void applyMoveLastRow(){
        for (int i = 0; i < 7; i++){
            if(i % 2 == 0){
                ConnectFourMove move = new ConnectFourMove("P1", i);
                connectFour.applyMove(board,move);
            }else {
                ConnectFourMove move = new ConnectFourMove("P2", i);
                connectFour.applyMove(board,move);
            }
        }

        assertEquals(Piece.Red, board.getPiece(5,0));
        assertEquals(Piece.Blue, board.getPiece(5,1));
        assertEquals(Piece.Red, board.getPiece(5,2));
        assertEquals(Piece.Blue, board.getPiece(5,3));
        assertEquals(Piece.Red, board.getPiece(5,4));
        assertEquals(Piece.Blue, board.getPiece(5,5));
        assertEquals(Piece.Red, board.getPiece(5,6));

    }

    @Test
    void applyMoveFillColumn(){
        for (int i = 0; i < 6; i++){
            if(i % 2 == 0){
                ConnectFourMove move = new ConnectFourMove("P2", 3);
                connectFour.applyMove(board,move);
            }else {
                ConnectFourMove move = new ConnectFourMove("P1", 3);
                connectFour.applyMove(board,move);
            }
        }

        assertEquals(Piece.Red, board.getPiece(0,3));
        assertEquals(Piece.Blue, board.getPiece(1,3));
        assertEquals(Piece.Red, board.getPiece(2,3));
        assertEquals(Piece.Blue, board.getPiece(3,3));
        assertEquals(Piece.Red, board.getPiece(4,3));
        assertEquals(Piece.Blue, board.getPiece(5,3));
    }

    @Test
    void applyMoveStacking(){
        connectFour.applyMove(board,new ConnectFourMove("P1", 0));
        for (int i = 1; i < 3; i++){
            if (i % 2 == 0){
                connectFour.applyMove(board,new ConnectFourMove("P1", i));
                connectFour.applyMove(board,new ConnectFourMove("P1", i));
                connectFour.applyMove(board,new ConnectFourMove("P1", i));
            }else {
                connectFour.applyMove(board,new ConnectFourMove("P2", i));
                connectFour.applyMove(board,new ConnectFourMove("P2", i));
            }
        }

        assertEquals(Piece.Red, board.getPiece(5,0));
        assertEquals(Piece.Blue, board.getPiece(5,1));
        assertEquals(Piece.Blue, board.getPiece(4,1));
        assertEquals(Piece.Red, board.getPiece(5,2));
        assertEquals(Piece.Red, board.getPiece(4,2));
        assertEquals(Piece.Red, board.getPiece(3,2));
    }
}
