package ca.ucalgary.seng300.events;

import java.util.List;

/**
 * Event emitted when matchmaking finds a valid match between players.
 */
public class MatchFoundEvent extends GameEvent {

    private final List<String> players;
    private final String gameType;
    private final String roomId;

    public MatchFoundEvent(String eventId, List<String> players, String gameType, String roomId) {
        super(eventId, "MatchFound");
        this.players = players;
        this.gameType = gameType;
        this.roomId = roomId;
    }

    public List<String> getPlayers() { return players; }
    public String getGameType() { return gameType; }
    public String getRoomId() { return roomId; }
}
