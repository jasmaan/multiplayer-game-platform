package ca.ucalgary.seng300.games;

import ca.ucalgary.seng300.validation.Validation;
import ca.ucalgary.seng300.validation.ValidationError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Checkers extends BoardGame{
    private final int rows = 8;
    private final int cols = 8;
    private final int pieceRows = 3;
    private static final Piece Player1 = Piece.White;
    private static final Piece Player2 = Piece.Black;
    private Map<String, Piece> playerProfiles;

    /**
     * Create the initial game state for a set of players.
     *
     * @param players list of player IDs
     * @return the initial board/game state
     */
    @Override
    public Object createInitialState(List<String> players) {
        board = new Board(rows,cols);

        //Adds the currently playing players
        playerProfiles = new HashMap<>();
        playerProfiles.put(players.get(0), Player1);
        playerProfiles.put(players.get(1), Player2);

        //adds the pieces in alternating rows
        // White pieces on dark squares in top 3 rows
        for (int i = 0; i < cols; i += 2){
            for (int j = 0; j < pieceRows; j++){
                board.addPiece(i + 1 - j % 2, j, Piece.White);
            }
        }
        // Black pieces on dark squares in bottom 3 rows
        for (int i = 0; i < cols; i += 2){
            for (int j = 0; j < pieceRows; j++){
                board.addPiece(i + j % 2, rows-1-j, Piece.Black);
            }
        }
        return board;
    }

    /**
     * Validate whether a move is legal given the current state.
     *
     * @param state current game state
     * @param move  the proposed move
     * @return true if move is valid
     */
    @Override
    public Validation validateMove(Object state, Object move) {
        if (!(move instanceof CheckersMove)) {
            return Validation.failure(
                    ValidationError.INVALID_FORMAT,
                    "Move must be a CheckersMove"
            );

        }
        CheckersMove cm = (CheckersMove) move;
        board = (Board) state;

        int tx = cm.target.x;
        int ty = cm.target.y;

        // Target must be in bounds
        if (tx < 0 || tx >= cols || ty < 0 || ty >= rows) {
            return Validation.failure(
                    ValidationError.OUT_OF_BOUNDS,
                    "Target position is out of bounds"
            );


        }
        // There must be a piece at the target
        Piece piece = board.getPiece(tx, ty);
        if (piece == null) {
            return Validation.failure(
                    ValidationError.INVALID_MOVE,
                    "No piece at target position"
            );
        }

        // The last move must have been by the opposing player
        if (((Board) state).getLastMove() != null) {
            CheckersMove lastMove = (CheckersMove) ((Board) state).getLastMove();
            Piece lastPiece = board.getPiece(lastMove.moves.getLast().x, lastMove.moves.getLast().y);
            if (isSameSide(piece, lastPiece)) {
                return Validation.failure(
                        ValidationError.INVALID_MOVE,
                        "It is not your turn"
                );
            }
        }

        // Must have at least one destination
        if (cm.moves == null || cm.moves.isEmpty()) {
            return Validation.failure(
                    ValidationError.INVALID_MOVE,
                    "No destination specified"
            );
        }

        // Validate each hop against a simulated board so later jumps see pieces removed by earlier captures.
        Board validationBoard = copyBoard(board);
        int curX = tx;
        int curY = ty;

        for (int i = 0; i < cm.moves.size(); i++) {
            CheckersMove.Coordinate dest = cm.moves.get(i);
            // Read from the simulated board because earlier hops may have moved or crowned the piece.
            Piece currentPiece = validationBoard.getPiece(curX, curY);
            if (currentPiece == null) {
                return Validation.failure(
                        ValidationError.INVALID_MOVE,
                        "Invalid move"
                );
            }

            // Destination must be in bounds
            if (dest.x < 0 || dest.x >= cols || dest.y < 0 || dest.y >= rows) {
                return Validation.failure(
                        ValidationError.OUT_OF_BOUNDS,
                        "Destination is out of bounds"
                );
            }

            // Destination must be empty
            if (validationBoard.getPiece(dest.x, dest.y) != null) {
                return Validation.failure(
                        ValidationError.CELL_OCCUPIED,
                        "Destination cell is occupied"
                );
            }

            int dx = dest.x - curX;
            int dy = dest.y - curY;

            // Movement must be strictly diagonal
            if (Math.abs(dx) != Math.abs(dy)) {
                return Validation.failure(
                        ValidationError.INVALID_MOVE,
                        "Checkers pieces must move diagonally"
                );
            }

            int distance = Math.abs(dx);
            boolean isKing = isKing(currentPiece);

            // Non-kings are restricted to one forward direction
            if (!isKing) {
                if (currentPiece == Piece.White && dy <= 0) {
                    return Validation.failure(
                            ValidationError.INVALID_MOVE,
                            "White pieces can only move forward (increasing row)"
                    );
                }
                if (currentPiece == Piece.Black && dy >= 0) {
                    return Validation.failure(
                            ValidationError.INVALID_MOVE,
                            "Black pieces can only move forward (decreasing row)"

                    );
                }
            }
            if (distance == 1) {
                // Normal step — valid only as a standalone (no chaining)
                if (cm.moves.size() > 1) {
                    return Validation.failure(
                            ValidationError.INVALID_MOVE,
                            "Cannot chain non-capture moves"
                    );
                }
            } else if (distance == 2) {
                // Jump capture — there must be exactly one opponent piece in between
                int midX = (curX + dest.x) / 2;
                int midY = (curY + dest.y) / 2;
                Piece midPiece = validationBoard.getPiece(midX, midY);

                if (midPiece == null) {
                    return Validation.failure(
                        ValidationError.INVALID_MOVE,
                        "No piece to capture at jump"
                    );
                }

                if (isSameSide(currentPiece, midPiece)) {
                    return Validation.failure(
                            ValidationError.INVALID_MOVE,
                            "Cannot capture own piece"
                    );
                }
            } else {
                return Validation.failure(
                        ValidationError.INVALID_MOVE,
                        "Invalid move distance (must be 1 or 2 squares)"
                );
            }

            // Apply this hop to the simulated board before checking the next landing square.
            validationBoard.addPiece(curX, curY, null);
            if (distance == 2) {
                int midX = (curX + dest.x) / 2;
                int midY = (curY + dest.y) / 2;
                validationBoard.addPiece(midX, midY, null);
            }

            Piece movedPiece = promoteIfNeeded(currentPiece, dest.y);
            validationBoard.addPiece(dest.x, dest.y, movedPiece);

            // Crowning ends the turn in this ruleset, so a newly promoted piece cannot continue the chain.
            if (!isKing(currentPiece) && isKing(movedPiece) && i < cm.moves.size() - 1) {
                return Validation.failure(
                        ValidationError.INVALID_MOVE,
                        "Invalid move"
                );
            }

            curX = dest.x;
            curY = dest.y;
        }

        return Validation.success();
    }


    /**
     * Apply a validated move to the current state.
     * @param state current game state
     * @param move the move to apply
     * @return the new game state after the move
     */
    @Override
    public Object applyMove(Object state, Object move){
        board = (Board) state;
        CheckersMove checkersMove = (CheckersMove) move;
        board.setLastMove(checkersMove);

        // gets the coordinates of the target destination, and adds the selected piece to that coordinate
        CheckersMove.Coordinate newPosition = checkersMove.moves.getLast();
        Piece targetPiece = board.getPiece(checkersMove.target.x, checkersMove.target.y);
        Piece placePiece = promoteIfNeeded(targetPiece, newPosition.y);
        board.addPiece(newPosition.x, newPosition.y, placePiece);

        // removes the piece from its previous location
        board.addPiece(checkersMove.target.x, checkersMove.target.y, null);

        // iterates over the moves to remove pieces that were moved over
        CheckersMove.Coordinate previousMove = checkersMove.target;
        for (CheckersMove.Coordinate coordinate : checkersMove.moves){
            int distance = Math.abs(coordinate.x - previousMove.x);
            int middleX = (coordinate.x + previousMove.x) / 2;
            int middleY = (coordinate.y + previousMove.y) / 2;
            if (distance > 1) {
                board.addPiece(middleX, middleY, null);
            }
            previousMove = coordinate;
        }

        return board;
    }

    /**
     * Check if the game has reached a terminal condition (win/draw).
     * @param state current game state
     * @return true if the game is over
     */
    @Override
    public boolean evaluateEnd(Object state){
        board = (Board) state;

        for (Piece player : new Piece[]{Player1, Player2}) {
            if (countPieces(board, player) == 0) {
                return true;
            }
            if (!hasLegalMoves(board, player)) {
                return true;
            }
         }
        return false;
    }

    /**
     * Determine the winner of the game (if any).
     *
     * @param state the terminal game state
     * @return the winning player's ID, or null for a draw
     */
    @Override
    public String getWinner(Object state) {
        board = (Board) state;

        for (Map.Entry<String, Piece> entry : playerProfiles.entrySet()) {
            String playerName = entry.getKey();
            Piece playerPiece = entry.getValue();
            if (countPieces(board, getOpponent(playerPiece)) == 0) {
                return playerName;
            }
            if (!hasLegalMoves(board, getOpponent(playerPiece))) {
                return playerName;
            }
        }
        return null; // no winner yet
    }

    private boolean isSameSide(Piece a, Piece b) {
        boolean aWhite = (a == Piece.White || a == Piece.WhiteKing);
        boolean bWhite = (b == Piece.White || b == Piece.WhiteKing);
        return aWhite == bWhite;
    }

    //Checks if a piece should be promoted to King piece.
    private Piece promoteIfNeeded(Piece piece, int destinationRow) {
        if (piece == Piece.White && destinationRow == rows - 1) {
            return Piece.WhiteKing;
        }

        if (piece == Piece.Black && destinationRow == 0) {
            return Piece.BlackKing;
        }

        return piece;
    }



    private int countPieces(Board board, Piece piece) {
        int count = 0;
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Piece p = board.getPiece(x, y);
                if (p != null && isSameSide(p, piece)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean hasLegalMoves(Board board, Piece piece) {

        // Stores and removes the last move from the board to prevent validation errors
        CheckersMove lastMove = (CheckersMove) board.getLastMove();
        board.setLastMove(null);
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Piece p = board.getPiece(x, y);
                if (p == null || !isSameSide(p, piece)) continue;

                int[] dirs = {-1, 1};
                for (int dx : dirs) {
                    for (int dy : dirs) {
                        CheckersMove.Coordinate target = new CheckersMove.Coordinate(x, y);

                        CheckersMove.Coordinate step = new CheckersMove.Coordinate(x + dx, y + dy);
                        CheckersMove stepMove = new CheckersMove(target, List.of(step));
                        if (validateMove(board, stepMove).isValid()) {
                            board.setLastMove(lastMove);
                            return true;
                        }

                        CheckersMove.Coordinate jump = new CheckersMove.Coordinate(x + dx * 2, y + dy * 2);
                        CheckersMove jumpMove = new CheckersMove(target, List.of(jump));
                        if (validateMove(board, jumpMove).isValid()) {
                            board.setLastMove(lastMove);
                            return true;
                        }
                    }
                }
            }
        }

        // restores the last move to the board
        board.setLastMove(lastMove);
        return false;
    }

    private Piece getOpponent(Piece piece) {
        if (piece == Piece.White || piece == Piece.WhiteKing) {
            return Player2;
        } else {
            return Player1;
        }
    }

        /**
     * Returns all legal move paths for the selected piece.
     * Jump paths are preferred over simple moves when available for that piece.
     *
     * @param board current board state
     * @param from starting coordinate of the selected piece
     * @return legal moves for that piece, or an empty list if none exist
     */
    public List<CheckersMove> getLegalMovesForPiece(Board board, CheckersMove.Coordinate from) {
        if (board == null || from == null) {
            return List.of();
        }
        if (from.x < 0 || from.x >= cols || from.y < 0 || from.y >= rows) {
            return List.of();
        }

        Piece piece = board.getPiece(from.x, from.y);
        if (piece == null) {
            return List.of();
        }

        // Capture paths take priority for the selected piece, so explore those first.
        List<CheckersMove> jumpMoves = new ArrayList<>();
        collectJumpMoves(board, from, from, piece, new ArrayList<>(), jumpMoves);
        if (!jumpMoves.isEmpty()) {
           return jumpMoves;
        }

        // Only fall back to simple diagonal steps when no captures exist for this piece.
        return collectSimpleMoves(board, from, piece);
    }


    private List<CheckersMove> collectSimpleMoves(Board board, CheckersMove.Coordinate from, Piece piece) {
        List<CheckersMove> moves = new ArrayList<>();
        int[] dirs = {-1, 1};

        // Reuse validateMove so the UI and engine stay on the same rule set.
        for (int dx : dirs) {
            for (int dy : dirs) {
                CheckersMove.Coordinate destination = new CheckersMove.Coordinate(from.x + dx, from.y + dy);
                CheckersMove move = new CheckersMove(from, List.of(destination));
                if (validateMove(board, move).isValid()) {
                    moves.add(move);
                }
            }
        }
        return moves;
    }

    private void collectJumpMoves(
            Board board,
            CheckersMove.Coordinate start,
            CheckersMove.Coordinate current,
            Piece piece,
            List<CheckersMove.Coordinate> path,
            List<CheckersMove> moves
    ) {
        boolean foundJump = false;
        int[] dirs = {-1, 1};

        // Probe every possible jump direction from the current landing square.
        for (int dx : dirs) {
            for (int dy : dirs) {
                CheckersMove.Coordinate jump = new CheckersMove.Coordinate(
                        current.x + dx * 2,
                        current.y + dy * 2
                );
                CheckersMove move = new CheckersMove(current, List.of(jump));
                if (!validateMove(board, move).isValid()) {
                    continue;
                }

                foundJump = true;
                // Continue searching on a copied board so capture chains can branch safely.
                Board nextBoard = copyBoard(board);
                applyMove(nextBoard, move);
                nextBoard.setLastMove(null);

                List<CheckersMove.Coordinate> nextPath = new ArrayList<>(path);
                nextPath.add(jump);
                Piece landedPiece = nextBoard.getPiece(jump.x, jump.y);
                // Crowning ends the move, so do not continue the jump chain after promotion.
                if (!isKing(piece) && isKing(landedPiece)) {
                    moves.add(new CheckersMove(start, List.copyOf(nextPath)));
                    continue;
                }
                collectJumpMoves(nextBoard, start, jump, landedPiece, nextPath, moves);
            }
        }

        // A path becomes a legal move once no further jumps can be extended from it.
        if (!foundJump && !path.isEmpty()) {
            moves.add(new CheckersMove(start, List.copyOf(path)));
        }
    }

    private Board copyBoard(Board board) {
        Board copy = new Board(rows, cols);

        // Clone the board contents so move exploration does not change the live game state.
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                copy.addPiece(x, y, board.getPiece(x, y));
            }
        }
        return copy;
    }

    private boolean isKing(Piece piece) {
        return piece == Piece.WhiteKing || piece == Piece.BlackKing;
    }

}

