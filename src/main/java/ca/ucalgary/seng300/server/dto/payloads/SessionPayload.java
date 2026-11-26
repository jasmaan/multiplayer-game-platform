package ca.ucalgary.seng300.server.dto.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Payload for FORFEIT, REQUEST_PLAY_AGAIN, and CANCEL_PLAY_AGAIN frames. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionPayload {

    private String sessionId;

    public SessionPayload() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
