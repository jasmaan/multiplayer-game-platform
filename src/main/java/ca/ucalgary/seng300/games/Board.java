package ca.ucalgary.seng300.games;


import java.util.Arrays;

/**
 * A class representing the board for board games
 */
public class Board{

    private final Piece[][] board;
    public final int width;
    public final int height;
    private Object lastMove;

    /**
     * The constructor for the board class.
     * @param height an int representing the number of rows in the board
     * @param width an int representing the number of columns in the board
     */
    public Board(int height, int width){
        this.width = width;
        this.height = height;
        board = new Piece[height][width];
        lastMove = null;
    }

    /**
     * A method that returns a copy of the board, so that the board itself cannot be directly edited
     * @return An array of piece arrays, which represents the grid of the board
     */
    public Piece[][] getBoard(){
        return board.clone();
    }

    /**
     * A method that gets the last move performed in the game
     * @return An object representing the last performed move
     */
    public Object getLastMove(){
        return lastMove;
    }

    /**
     * A method that sets the last move performed in the game
     */
    public void setLastMove(Object lastMove){
        this.lastMove = lastMove;
    }

    /**
     * A method that gets the piece at a given coordinate on the board
     * @param x an int representing the column that the piece is in
     * @param y an int representing the row that the piece is in
     * @return the piece at the specified coordinates
     */
    public Piece getPiece(int x,int y){
        return board[x][y];
    }

    /**
     * A method that changes the piece at a given coordinate on the board
     * @param x an int representing the column that the piece to be replaced is in
     * @param y an int representing the column that the piece to be replaced is in
     * @param piece the piece to be added at the specified coordinates
     */
    public void addPiece(int x, int y, Piece piece){
        board[x][y] = piece;
    }

    /**
     * A method that checks if there is a given number of a type of piece consecutively
     * @param piece the type of piece that is being checked
     * @param consecutive an int representing the number of consecutive pieces to check for
     * @return a boolean representing if the given number of pieces was found consecutively
     */
    public boolean consecutivePieces(Piece piece, int consecutive){
        return inRow(piece, consecutive) | inColumn(piece, consecutive) | inDiagonal(piece, consecutive);
    }

    /**
     * A method that checks if there is a given number of a type of piece in a row
     * @param piece the type of piece that is being checked
     * @param consecutive an int representing the number of consecutive pieces to check for
     * @return a boolean representing if the given number of pieces was found in a row
     */
    public boolean inRow(Piece piece, int consecutive){

        // iterates over the rows from left to right,
        // until it is not possible to fit the consecutive pieces in the remaining space.
        for (int i = 0; i <= board.length-consecutive; i++) {
            for (int j = 0; j < board[0].length; j++) {

                // once a piece of interest is found, the subsequent pieces beyond it are checked.
                // if all of these pieces are equal to the piece of interest, a consecutive row has been found.
                if (board[i][j] == piece){
                    boolean broken = false;
                    for (int k = 1; k < consecutive; k++) {
                        if (board[i+k][j] != piece){
                            broken = true;
                            break;
                        }
                    }
                    if (!broken){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * A method that checks if there is a given number of a type of piece consecutively in a column
     * @param piece the type of piece that is being checked
     * @param consecutive an int representing the number of consecutive pieces to check for
     * @return a boolean representing if the given number of pieces was found consecutively in a column
     */
    public boolean inColumn(Piece piece, int consecutive){

        // iterates over the columns from top to bottom
        // until it is not possible to fit the consecutive pieces in the remaining space.
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j <= board[0].length-consecutive; j++) {

                // once a piece of interest is found, the subsequent pieces beyond it are checked.
                // if all of these pieces are equal to the piece of interest, a consecutive column has been found.
                if (board[i][j] == piece){
                    boolean broken = false;
                    for (int k = 1; k < consecutive; k++) {
                        if (board[i][j+k] != piece){
                            broken = true;
                            break;
                        }
                    }
                    if (!broken){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * A method that checks if there is a given number of a type of piece consecutively in a diagonal
     * @param piece the type of piece that is being checked
     * @param consecutive an int representing the number of consecutive pieces to check for
     * @return a boolean representing if the given number of pieces was found consecutively in a diagonal
     */
    public boolean inDiagonal(Piece piece, int consecutive){

        // iterates over the diagonals from left to right, and top to bottom,
        // until there is not enough room on the bottom
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j <= board[0].length-consecutive; j++) {

                // once the piece of interest is found, if there is enough space to the right for the consecutive pieces,
                // the descending right diagonal is checked for the piece of interest.
                // if the descending right diagonal is all the piece of interest, a consecutive diagonal has been found
                if (board[i][j] == piece){
                    boolean broken = false;
                    if (i <= board.length-consecutive) {
                        for (int k = 1; k < consecutive; k++) {
                            if (board[i + k][j + k] != piece) {
                                broken = true;
                                break;
                            }
                        }
                        if (!broken) {
                            return true;
                        }
                    }

                    // the same procedure is done for the left side.
                    if (i >= consecutive-1) {
                        for (int k = 1; k < consecutive; k++) {
                            if (board[i - k][j + k] != piece) {
                                broken = true;
                                break;
                            }
                        }
                        if (!broken) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * A method that checks if the board is full
     * @return a boolean representing if the board is full
     */
    public boolean full(){
        for (Piece[] pieces : board) {
            for (Piece piece : pieces) {
                if (piece == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Overrides the equals method
     * @param o the object this board will be compared to
     * @return a boolean representing if the boards contain the same pieces at the same positions
     */
    @Override
    public boolean equals(Object o){
        if (o instanceof Board other){
            return Arrays.deepEquals(other.getBoard(), this.getBoard());
        }
        return false;
    }
}

