package ca.ucalgary.seng300.server.dto.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Payload for JOIN_QUEUE frames. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueuePayload {

    private String gameType;

    public QueuePayload() {}

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
}
