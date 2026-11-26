package ca.ucalgary.seng300.leaderboard;

/**
 * Immutable record of a single completed game session.
 */
public class GameResult {
    private final String sessionId;
    private final String winnerId;   // null if draw
    private final String loserId;    // null if draw
    private final boolean isDraw;
    private final long timestamp;    // System.currentTimeMillis()

    public GameResult(String sessionId, String winnerId, String loserId,
                      boolean isDraw, long timestamp) {
        this.sessionId = sessionId;
        this.winnerId  = winnerId;
        this.loserId   = loserId;
        this.isDraw    = isDraw;
        this.timestamp = timestamp;
    }

    public String getSessionId() { return sessionId; }
    public String getWinnerId()  { return winnerId; }
    public String getLoserId()   { return loserId; }
    public boolean isDraw()      { return isDraw; }
    public long getTimestamp()   { return timestamp; }
}
