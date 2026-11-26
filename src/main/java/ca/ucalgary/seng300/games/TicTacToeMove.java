package ca.ucalgary.seng300.games;

public class TicTacToeMove {
    int row;
    int col;
    Piece piece;

    public TicTacToeMove(int row, int col, Piece piece) {
        this.row = row;
        this.col = col;
        this.piece = piece;
    }
}
