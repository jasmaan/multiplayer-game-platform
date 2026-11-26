package ca.ucalgary.seng300.core;

/**
 * Represents the lifecycle status of a game session.
 */
public enum GameStatus {
    LOBBY,
    MATCHMAKING,
    STARTING,
    IN_PROGRESS,
    GAME_OVER
}
