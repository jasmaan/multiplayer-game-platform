package ca.ucalgary.seng300.server.dto.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Payload for SEND_CHAT frames. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatPayload {

    private String sessionId;
    private String text;

    public ChatPayload() {}

    public String getSessionId() { return sessionId; }
    public String getText()      { return text; }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setText(String text)           { this.text = text; }
}
