package ca.ucalgary.seng300.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Base envelope for every client → server WebSocket frame.
 * The 'payload' field is kept as a raw JsonNode so MessageRouter
 * can deserialize it into the correct payload type based on 'type'.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundMessage {

    private String type;
    private String token;
    private JsonNode payload;

    public InboundMessage() {}

    public String getType()       { return type; }
    public String getToken()      { return token; }
    public JsonNode getPayload()  { return payload; }

    public void setType(String type)         { this.type = type; }
    public void setToken(String token)       { this.token = token; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
