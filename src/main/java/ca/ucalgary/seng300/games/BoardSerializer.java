package ca.ucalgary.seng300.games;

import java.util.List;

/**
 * Utility for converting a {@link Board} to and from a 2-D {@code String[][]} array,
 * suitable for JSON serialisation by Jackson.
 *
 * Empty cells are represented as the string {@code "EMPTY"}.
 * Occupied cells use the {@link Piece#name()} (e.g. {@code "X"}, {@code "Red"}).
 */
public final class BoardSerializer {

    private BoardSerializer() {}

    // ── Serialise ──────────────────────────────────────────────────────────────

    /**
     * Encode a Board as a {@code String[][]} where each element is either a
     * {@link Piece} name or {@code "EMPTY"}.
     */
    public static String[][] serialize(Board board) {
        String[][] result = new String[board.height][board.width];
        for (int r = 0; r < board.height; r++) {
            for (int c = 0; c < board.width; c++) {
                Piece p = board.getPiece(r, c);
                result[r][c] = (p == null) ? "EMPTY" : p.name();
            }
        }
        return result;
    }

    // ── Deserialise ────────────────────────────────────────────────────────────

    /**
     * Reconstruct a {@link Board} from a {@code String[][]}.
     * Cells with value {@code "EMPTY"} or {@code null} are left empty.
     */
    public static Board deserialize(String[][] grid) {
        if (grid == null || grid.length == 0) return new Board(0, 0);
        int height = grid.length;
        int width  = grid[0] != null ? grid[0].length : 0;
        Board board = new Board(height, width);
        for (int r = 0; r < height; r++) {
            if (grid[r] == null) continue;
            for (int c = 0; c < grid[r].length; c++) {
                String val = grid[r][c];
                if (val != null && !val.equals("EMPTY")) {
                    try {
                        board.addPiece(r, c, Piece.valueOf(val));
                    } catch (IllegalArgumentException ignored) {
                        // skip unknown piece names — defensive
                    }
                }
            }
        }
        return board;
    }

    /**
     * Convenience overload that accepts the nested-{@link List} structure Jackson
     * produces when it deserialises a JSON array-of-arrays into a {@code Map<String,Object>}.
     */
    public static Board deserializeFromList(List<?> rows) {
        if (rows == null || rows.isEmpty()) return new Board(0, 0);
        int height = rows.size();
        int width  = rows.get(0) instanceof List<?> r0 ? r0.size() : 0;
        Board board = new Board(height, width);
        for (int r = 0; r < height; r++) {
            if (!(rows.get(r) instanceof List<?> row)) continue;
            for (int c = 0; c < row.size(); c++) {
                Object cell = row.get(c);
                String val  = cell != null ? cell.toString() : null;
                if (val != null && !val.equals("EMPTY")) {
                    try {
                        board.addPiece(r, c, Piece.valueOf(val));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return board;
    }
}
