package ca.ucalgary.seng300.games;

public enum Piece{
    X('x'), O('o'), Red('0'), Blue('0'), White('0'), Black('0'), WhiteKing('K'), BlackKing('K');
    final char symbol;
    Piece(char symbol){
        this.symbol = symbol;
    }
}
