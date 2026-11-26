package ca.ucalgary.seng300.games;

import java.util.List;

/**
 * A class that represents the moves in checkers.
 */
public class CheckersMove {

    public final Coordinate target;
    public final List<Coordinate> moves;

    /**
     * The constructor for the CheckersMove class
     * @param target the coordinates of the piece to be moved
     * @param moves the coordinates of the target pieces moves
     */
    public CheckersMove(Coordinate target, List<Coordinate> moves){
        this.target = target;
        this.moves = moves;
    }

    /**
     * A class containing x and y coordinates that represent moves in the game
     */
    public static class Coordinate{

        public final int x;
        public final int y;

        /**
         * The constructor for the Coordinate class
         * @param x an int representing the x coordinate
         * @param y an int representing the y coordinate
         */
        public Coordinate(int x, int y){
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o){
            if(o instanceof Coordinate){
                Coordinate other = (Coordinate) o;
                return this.x == other.x && this.y == other.y;
            }
            return false;
        }
    }

    @Override
    public boolean equals(Object o){
        if(o instanceof CheckersMove){
            CheckersMove other = (CheckersMove) o;
            if (target.equals(other.target) && (this.moves.size() == other.moves.size())){
                for (int i = 0; i < this.moves.size(); i++){
                    if (!this.moves.get(i).equals(other.moves.get(i))){
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
}
