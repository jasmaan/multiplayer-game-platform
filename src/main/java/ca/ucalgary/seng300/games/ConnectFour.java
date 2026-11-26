package ca.ucalgary.seng300.games;
import ca.ucalgary.seng300.validation.Validation;
import ca.ucalgary.seng300.validation.ValidationError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConnectFour extends BoardGame {
    private final int rows = 6;
    private final int cols = 7;
    private final int consecutive = 4;
    private static final Piece Player1 = Piece.Red;
    private static final Piece Player2 = Piece.Blue;
    private Map<String, Piece> playerProfiles;

    @Override
    public Object createInitialState(List<String> players) {
        board = new Board(rows, cols);
        playerProfiles = new HashMap<>();
        playerProfiles.put(players.get(0), Player1);
        playerProfiles.put(players.get(1), Player2);
        return board;
    }

    @Override
    public Validation validateMove(Object state, Object move) {
        if (!(move instanceof ConnectFourMove)) {
            return Validation.failure(ValidationError.INVALID_FORMAT, "Move must be of type ConnectFourMove(playerID, column index)");
        }

        ConnectFourMove m = (ConnectFourMove) move;
        String player = m.player;
        int column = m.column;

        if (!playerProfiles.containsKey(player)) {
            return Validation.failure(ValidationError.UNAUTHORIZED_PLAYER, "Player " + player + " does not exist");
        }

        Board board = (Board) state;
        if (column < 0 || column >= cols) {
            //TO DO: return validation code for out of bounds.
            return Validation.failure(ValidationError.OUT_OF_BOUNDS, "The column is out of bounds");
        }
        if (board.getPiece(0, column) != null) {
            //TO DO: return validation code for column full.
            return Validation.failure(ValidationError.COLUMN_FULL, "The column is already full");
        }

        return Validation.success();
    }

    @Override
    public Object applyMove(Object state, Object move) {
        if (!(move instanceof ConnectFourMove)) {
            throw new IllegalArgumentException("Invalid move type");
        }

        board = (Board) state;
        ConnectFourMove m = (ConnectFourMove) move;
        String player = m.player;
        int column = m.column;

        Piece piece = playerProfiles.get(player);

        //Placing the piece in the column, while implementing the gravity rule.
        for (int i = rows-1; i >= 0; i--) {
            if (board.getPiece(i, column) == null) {
                board.addPiece(i, column, piece);
                break;
            }
        }

        return board;
    }

    @Override
    public boolean evaluateEnd(Object state) {
        board = (Board) state;
        return board.consecutivePieces(Player1, consecutive)||
               board.consecutivePieces(Player2, consecutive)||
               board.full();
    }

    @Override
    public String getWinner(Object state) {
        board = (Board) state;
        for (Map.Entry<String, Piece> players : playerProfiles.entrySet()) {
            if (board.consecutivePieces(players.getValue(), consecutive)) {
                return players.getKey();
            }
        }
        return null;
    }
}