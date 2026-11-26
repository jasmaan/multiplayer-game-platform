package ca.ucalgary.seng300.events;

import java.util.UUID;

/**
 * Fired by PlatformCore when a player sends a chat message during a game session.
 * Consumed by PlatformCore to deliver the message to the registered per-session callback.
 */
public class ChatMessageEvent extends GameEvent {

    private final String sessionId;
    private final String senderId;
    private final String text;

    public ChatMessageEvent(String sessionId, String senderId, String text) {
        super(UUID.randomUUID().toString(), "ChatMessage");
        this.sessionId = sessionId;
        this.senderId  = senderId;
        this.text      = text;
    }

    public String getSessionId() { return sessionId; }
    public String getSenderId()  { return senderId; }
    public String getText()      { return text; }
}
