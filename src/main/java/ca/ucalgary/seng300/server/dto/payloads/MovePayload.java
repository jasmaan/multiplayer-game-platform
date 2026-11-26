package ca.ucalgary.seng300.server.dto.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Payload for SUBMIT_MOVE frames.
 * moveData is kept as raw JSON so MessageRouter can deserialize
 * it into the correct game-specific move class (TicTacToeMove,
 * ConnectFourMove, CheckersMove) based on gameType.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MovePayload {

    private String   sessionId;
    private String   moveId;     // client-generated UUID for idempotency
    private String   gameType;
    private JsonNode moveData;

    public MovePayload() {}

    public String   getSessionId() { return sessionId; }
    public String   getMoveId()    { return moveId; }
    public String   getGameType()  { return gameType; }
    public JsonNode getMoveData()  { return moveData; }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setMoveId(String moveId)       { this.moveId = moveId; }
    public void setGameType(String gameType)   { this.gameType = gameType; }
    public void setMoveData(JsonNode moveData) { this.moveData = moveData; }
}
