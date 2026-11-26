package ca.ucalgary.seng300.games;

import ca.ucalgary.seng300.validation.Validation;
import ca.ucalgary.seng300.validation.ValidationError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TicTacToe extends BoardGame {

    // 3x3 board, need 3 (in order) to win
    private final int rows = 3;
    private final int cols = 3;
    private final int winCondition = 3;

    // symbol assigned to player
    private static final Piece Player1 = Piece.X;
    private static final Piece Player2 = Piece.O;
    Map<String, Piece> playerProfiles;

    @Override
    public Object createInitialState(List<String> players) {
        board = new Board(rows, cols);
        playerProfiles = new HashMap<>();
        playerProfiles.put(players.get(0), Player1);
        playerProfiles.put(players.get(1), Player2);       // 3x3 board created, players mapped to piece

        return board;
    }

    @Override
    public Validation validateMove(Object state, Object move) {
        if (!(move instanceof TicTacToeMove)) {
            return Validation.failure(ValidationError.INVALID_FORMAT, "Move needs to be a TicTacToeMove object");
        }

        TicTacToeMove tttMove = (TicTacToeMove) move;
        int row = tttMove.row;
        int col = tttMove.col;
        boolean rowOutOfBounds = row < 0 || row >= rows;
        boolean colOutOfBounds = col < 0 || col >= cols;
        if (rowOutOfBounds || colOutOfBounds) {
            return Validation.failure(ValidationError.OUT_OF_BOUNDS, "The cell is outside of the board");
        }

        board = (Board) state;
        boolean cellAlreadyTaken = board.getPiece(row, col) != null;       // check if cell is actually empty
        if (cellAlreadyTaken) {
            return Validation.failure(ValidationError.CELL_OCCUPIED, "Cell is already occupied");
        }

        return Validation.success();
    }

    @Override
    public Object applyMove(Object state, Object move) {
        board = (Board) state;
        TicTacToeMove tttMove = (TicTacToeMove) move;   // cast to TicTacToeMove for row,col, piece
        int row = tttMove.row;
        int col = tttMove.col;
        int totalPiecesOnBoard = countPieces(board);  // Check how many pieces on board to see whose turn

        if (totalPiecesOnBoard % 2 == 0) {
            board.addPiece(row,col,Player1);   // even = player 1 turn
        } else {
            board.addPiece(row, col, Player2); // odd = player 2 turn
        }

        return board;
    }

    @Override
    public boolean evaluateEnd(Object state) {
        board = (Board) state;
        boolean player1Won = board.consecutivePieces(Player1, winCondition);   // built in consecutive piece checker
        boolean player2Won = board.consecutivePieces(Player2, winCondition);

        if (player1Won || player2Won) {
            return true;
        }
        if (isBoardFull(board)) {      // if game is draw
            return true;
        }
        return false;
    }

    @Override
    public String getWinner(Object state) {
        Board stateBoard = (Board) state;

        for (Map.Entry<String, Piece> entry : playerProfiles.entrySet()) {
            String playerName = entry.getKey();
            Piece playerPiece = entry.getValue();

            boolean hasWon = stateBoard.consecutivePieces(playerPiece, winCondition);   // use built in consecutive piece checker

            if (hasWon) {
                return playerName;
            }
        }
        return null;  // in case game draw
    }

    // -------------
    // HELPER METHODS

    // Counts pieces currently on board (for figuring out whose turn it is)
    private int countPieces(Board board) {
        int count = 0;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (board.getPiece(i, j) != null) {     // if cell isn't empty, piece exists there
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isBoardFull(Board board) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (board.getPiece(i, j) == null) {
                    return false;   // found empty cell (board not full)
                }
            }
        }
        return true;
    }
}

