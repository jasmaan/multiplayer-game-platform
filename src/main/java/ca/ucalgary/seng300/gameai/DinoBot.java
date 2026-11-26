package ca.ucalgary.seng300.gameai;

import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.Checkers;
import ca.ucalgary.seng300.games.CheckersMove;
import ca.ucalgary.seng300.games.Piece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DinoBot — autonomous computer player for TicTacToe and Connect Four.
 *
 * TicTacToe:   full minimax (perfect play on a 3×3 board).
 * Connect Four: win-in-1 → block-in-1 → avoid giving opponent win → centre-biased column.
 */
public class DinoBot {

    public static final String BOT_USERNAME = "DinoBot";

    // ─────────────────────────────────────────────────────────────
    // TicTacToe — Minimax
    // ─────────────────────────────────────────────────────────────

    /**
     * Compute the best move for TicTacToe.
     * @return int[2] = {row, col}
     */
    public static int[] computeTicTacToeMove(Board board, Piece botPiece, Piece opponentPiece) {
        Piece[][] grid = copyBoard(board, 3, 3);
        int[] result = minimax(grid, botPiece, opponentPiece, true);
        return new int[]{result[1], result[2]};
    }

    /**
     * Minimax over a 3×3 grid.
     * @return int[3] = {score, row, col}
     */
    private static int[] minimax(Piece[][] grid, Piece bot, Piece opp, boolean maximising) {
        Integer terminal = evaluate(grid, bot, opp);
        if (terminal != null) return new int[]{terminal, -1, -1};

        int bestScore = maximising ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int bestRow = -1, bestCol = -1;

        outer:
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (grid[r][c] != null) continue;
                grid[r][c] = maximising ? bot : opp;
                int score = minimax(grid, bot, opp, !maximising)[0];
                grid[r][c] = null;

                if (maximising && score > bestScore) {
                    bestScore = score; bestRow = r; bestCol = c;
                    if (bestScore == 10) break outer; // can't do better
                } else if (!maximising && score < bestScore) {
                    bestScore = score; bestRow = r; bestCol = c;
                    if (bestScore == -10) break outer;
                }
            }
        }

        // All cells filled and no winner → draw
        if (bestRow == -1) return new int[]{0, -1, -1};
        return new int[]{bestScore, bestRow, bestCol};
    }

    /**
     * Returns +10 if bot wins, -10 if opponent wins, 0 for draw, null if still playing.
     */
    private static Integer evaluate(Piece[][] grid, Piece bot, Piece opp) {
        if (hasWon3x3(grid, bot))  return 10;
        if (hasWon3x3(grid, opp)) return -10;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                if (grid[r][c] == null) return null; // not terminal
        return 0; // draw
    }

    private static boolean hasWon3x3(Piece[][] g, Piece p) {
        // Rows & cols
        for (int i = 0; i < 3; i++) {
            if (g[i][0] == p && g[i][1] == p && g[i][2] == p) return true;
            if (g[0][i] == p && g[1][i] == p && g[2][i] == p) return true;
        }
        // Diagonals
        if (g[0][0] == p && g[1][1] == p && g[2][2] == p) return true;
        if (g[0][2] == p && g[1][1] == p && g[2][0] == p) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Connect Four — Heuristic + 2-ply look-ahead
    // ─────────────────────────────────────────────────────────────

    private static final int CF_ROWS = 6;
    private static final int CF_COLS = 7;
    /** Centre-outward column preference. */
    private static final int[] PREFERRED = {3, 2, 4, 1, 5, 0, 6};

    /**
     * Compute the best column for DinoBot to drop a piece in.
     * Strategy (in priority order):
     *  1. Win immediately.
     *  2. Block opponent immediate win.
     *  3. Avoid columns that give the opponent a win on the very next drop.
     *  4. Centre-biased column preference.
     */
    public static int computeConnectFourColumn(Board board, Piece botPiece, Piece opponentPiece) {
        Piece[][] grid = copyBoard(board, CF_ROWS, CF_COLS);

        // 1. Win in one move
        for (int c = 0; c < CF_COLS; c++) {
            if (!canDrop(grid, c)) continue;
            if (checkWinAfterDrop(grid, c, botPiece)) return c;
        }

        // 2. Block opponent from winning on their next move
        for (int c = 0; c < CF_COLS; c++) {
            if (!canDrop(grid, c)) continue;
            if (checkWinAfterDrop(grid, c, opponentPiece)) return c;
        }

        // 3. Prefer columns that do NOT give the opponent an immediate win above our piece
        for (int c : PREFERRED) {
            if (!canDrop(grid, c)) continue;
            Piece[][] after = drop(grid, c, botPiece);
            boolean opponentWinsAbove = false;
            for (int oc = 0; oc < CF_COLS; oc++) {
                if (!canDrop(after, oc)) continue;
                if (checkWinAfterDrop(after, oc, opponentPiece)) {
                    opponentWinsAbove = true;
                    break;
                }
            }
            if (!opponentWinsAbove) return c;
        }

        // 4. Fallback — any playable centre-biased column
        for (int c : PREFERRED) {
            if (canDrop(grid, c)) return c;
        }

        return 0;
    }

    private static boolean canDrop(Piece[][] grid, int col) {
        return grid[0][col] == null; // top row must be empty
    }

    private static boolean checkWinAfterDrop(Piece[][] grid, int col, Piece piece) {
        Piece[][] sim = drop(grid, col, piece);
        return checkConnectFourWin(sim, piece);
    }

    private static Piece[][] drop(Piece[][] grid, int col, Piece piece) {
        Piece[][] copy = new Piece[CF_ROWS][CF_COLS];
        for (int r = 0; r < CF_ROWS; r++) copy[r] = grid[r].clone();
        for (int r = CF_ROWS - 1; r >= 0; r--) {
            if (copy[r][col] == null) { copy[r][col] = piece; break; }
        }
        return copy;
    }

    private static boolean checkConnectFourWin(Piece[][] grid, Piece piece) {
        // Horizontal
        for (int r = 0; r < CF_ROWS; r++)
            for (int c = 0; c <= CF_COLS - 4; c++)
                if (grid[r][c] == piece && grid[r][c+1] == piece
                        && grid[r][c+2] == piece && grid[r][c+3] == piece) return true;
        // Vertical
        for (int r = 0; r <= CF_ROWS - 4; r++)
            for (int c = 0; c < CF_COLS; c++)
                if (grid[r][c] == piece && grid[r+1][c] == piece
                        && grid[r+2][c] == piece && grid[r+3][c] == piece) return true;
        // Diagonal ↘
        for (int r = 0; r <= CF_ROWS - 4; r++)
            for (int c = 0; c <= CF_COLS - 4; c++)
                if (grid[r][c] == piece && grid[r+1][c+1] == piece
                        && grid[r+2][c+2] == piece && grid[r+3][c+3] == piece) return true;
        // Diagonal ↙
        for (int r = 3; r < CF_ROWS; r++)
            for (int c = 0; c <= CF_COLS - 4; c++)
                if (grid[r][c] == piece && grid[r-1][c+1] == piece
                        && grid[r-2][c+2] == piece && grid[r-3][c+3] == piece) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Checkers - legal move picker
    // ─────────────────────────────────────────────────────────────

    /**
     * Compute a legal Checkers move for DinoBot.
     * The bot prefers captures, longer jump chains, and promotions, then falls back
     * to a stable board-order choice so behaviour stays deterministic for testing.
     */
    public static CheckersMove computeCheckersMove(Board board, Piece botPiece) {
        if (board == null || botPiece == null) {
            return null;
        }

        Checkers checkers = new Checkers();
        List<CheckersMove> legalMoves = new ArrayList<>();

        // Reuse the engine's legal-move generation instead of duplicating checkers rules here.
        for (int x = 0; x < board.width; x++) {
            for (int y = 0; y < board.height; y++) {
                Piece piece = board.getPiece(x, y);
                if (piece == null || !sameCheckersSide(piece, botPiece)) {
                    continue;
                }

                legalMoves.addAll(
                        checkers.getLegalMovesForPiece(board, new CheckersMove.Coordinate(x, y))
                );
            }
        }

        if (legalMoves.isEmpty()) {
            return null;
        }

        // Highest score wins; ties fall back to a consistent board-order choice.
        legalMoves.sort(Comparator
                .comparingInt((CheckersMove move) -> scoreCheckersMove(board, move)).reversed()
                .thenComparingInt(move -> move.target.x)      // If scores tie, prefer the smaller start-column.
                .thenComparingInt(move -> move.target.y)      // If still tied, prefer the smaller start-row.
                .thenComparingInt(move -> move.moves.getLast().x) // Next tie-break by the final landing column.
                .thenComparingInt(move -> move.moves.getLast().y)); // Final tie-break by the final landing row.

        return legalMoves.getFirst();
    }

    // Shared helpers
    private static Piece[][] copyBoard(Board board, int rows, int cols) {
        Piece[][] grid = new Piece[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                grid[r][c] = board.getPiece(r, c);
        return grid;
    }

    private static boolean sameCheckersSide(Piece a, Piece b) {
        boolean aWhite = a == Piece.White || a == Piece.WhiteKing;
        boolean bWhite = b == Piece.White || b == Piece.WhiteKing;
        return aWhite == bWhite;
    }

    private static int scoreCheckersMove(Board board, CheckersMove move) {
        Piece piece = board.getPiece(move.target.x, move.target.y);
        CheckersMove.Coordinate previous = move.target;
        int captures = 0;

        // Count captures by checking for two-square hops in the path.
        for (CheckersMove.Coordinate step : move.moves) {
            if (Math.abs(step.x - previous.x) == 2) {
                captures++;
            }
            previous = step;
        }

        // Prefer moves with more captures first, then kings and promotion opportunities.
        int score = captures * 100 + move.moves.size() * 10;
        if (piece == Piece.WhiteKing || piece == Piece.BlackKing) {
            score += 5;
        }

        CheckersMove.Coordinate last = move.moves.getLast();
        if (piece == Piece.White && last.y == board.height - 1) {
            score += 25;
        } else if (piece == Piece.Black && last.y == 0) {
            score += 25;
        }

        return score;
    }
}
