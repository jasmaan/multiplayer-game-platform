package ca.ucalgary.seng300.events;

import java.util.UUID;

/**
 * Fired by PlatformCore when a player successfully joins a room.
 * Consumed by PlatformCore to notify the room's host.
 */
public class RoomJoinedEvent extends GameEvent {

    private final String roomId;
    private final String hostId;
    private final String joiningPlayerId;

    public RoomJoinedEvent(String roomId, String hostId, String joiningPlayerId) {
        super(UUID.randomUUID().toString(), "RoomJoined");
        this.roomId          = roomId;
        this.hostId          = hostId;
        this.joiningPlayerId = joiningPlayerId;
    }

    public String getRoomId()          { return roomId; }
    public String getHostId()          { return hostId; }
    public String getJoiningPlayerId() { return joiningPlayerId; }
}
