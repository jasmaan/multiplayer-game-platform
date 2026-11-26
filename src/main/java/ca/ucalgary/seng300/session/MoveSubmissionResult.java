package ca.ucalgary.seng300.session;

import ca.ucalgary.seng300.events.GameStateUpdate;
import ca.ucalgary.seng300.events.MoveRejectedEvent;

/**
 * Represents the result of a move submission attempt.
 * Encapsulates whether the move was accepted or rejected, and includes relevant details for each case.
 */
public class MoveSubmissionResult {
    
    private final GameStateUpdate update;
    private final boolean accepted;
    private final MoveRejectedEvent rejection;

    /**
     * Private constructor to enforce use of static factory methods for clarity. 
     * @param update the game state update if the move was accepted, null otherwise
     * @param accepted true if the move was accepted, false if rejected
     * @param rejection the move rejection details if the move was rejected, null otherwise
     */
    private MoveSubmissionResult(GameStateUpdate update, boolean accepted, MoveRejectedEvent rejection) {
        this.update = update;
        this.accepted = accepted;
        this.rejection = rejection;
    }

    /**
     * Factory method for an accepted move result.
     * @param update the game state update resulting from the accepted move
     * @return a MoveSubmissionResult representing an accepted move
     */
    public static MoveSubmissionResult accepted(GameStateUpdate update) {
        return new MoveSubmissionResult(update, true, null);
    }

    /**
     * Factory method for a rejected move result.
     * @param rejection the details of why the move was rejected
     * @return a MoveSubmissionResult representing a rejected move
     */
    public static MoveSubmissionResult rejected(MoveRejectedEvent rejection) {
        return new MoveSubmissionResult(null, false, rejection);
    }

    /**
     * Get the game state update if the move was accepted.
     * @return the GameStateUpdate if accepted, null if rejected
     */
    public GameStateUpdate getUpdate() {
        return update;
    }

    /**
     * Check if the move was accepted.
     * @return true if the move was accepted, false if rejected
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Get the move rejection details if the move was rejected.
     * @return the MoveRejectedEvent if rejected, null if accepted
     */
    public MoveRejectedEvent getRejection() {
        return rejection;
    }
}
