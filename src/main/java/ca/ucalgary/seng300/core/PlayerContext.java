package ca.ucalgary.seng300.core;

/**
 * Represents the current UI/navigation context of a logged-in player.
 * Used by PlatformCore to decide how to route notifications:
 * - DASHBOARD: player is browsing; show banner/badge notifications
 * - IN_LOBBY: player is in a room waiting for game start; show lobby alerts
 * - IN_QUEUE: player is in matchmaking queue; show queue status updates
 * - IN_GAME: player is actively in a session; update game view directly
 * - VIEWING_RESULTS: player is on the post-game results screen
 */
public enum PlayerContext {
    DASHBOARD,
    IN_LOBBY,
    IN_QUEUE,
    IN_GAME,
    VIEWING_RESULTS
}
