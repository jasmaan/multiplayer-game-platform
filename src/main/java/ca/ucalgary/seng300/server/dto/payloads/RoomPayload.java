package ca.ucalgary.seng300.server.dto.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Payload for CREATE_ROOM, JOIN_ROOM, CANCEL_ROOM, and START_GAME frames. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoomPayload {

    private String roomId;
    private String gameType;

    public RoomPayload() {}

    public String getRoomId()   { return roomId; }
    public String getGameType() { return gameType; }

    public void setRoomId(String roomId)     { this.roomId = roomId; }
    public void setGameType(String gameType) { this.gameType = gameType; }
}
