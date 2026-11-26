package ca.ucalgary.seng300.core;

/**
 * Enumeration of platform-level error codes used across services.
 */
public enum PlatformError {
    ROOM_FULL,
    ROOM_NOT_FOUND,
    PLAYER_ALREADY_IN_ROOM,
    PLAYER_NOT_IN_ROOM,
    INVALID_SESSION,
    INVALID_MOVE,
    NOT_YOUR_TURN,
    GAME_ALREADY_OVER,
    AUTH_FAILED,
    QUEUE_FAILED,
    SESSION_NOT_FOUND,
    INVALID_STATE,
    ENGINE_NOT_REGISTERED,
    UNKNOWN_PLAYER
}
