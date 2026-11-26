package ca.ucalgary.seng300.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Base envelope for every server → client WebSocket frame.
 * Use the static factory helpers to build typed responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboundMessage {

    private String type;
    private Object payload;
    private long   timestamp;

    public OutboundMessage() {}

    private OutboundMessage(String type, Object payload) {
        this.type      = type;
        this.payload   = payload;
        this.timestamp = System.currentTimeMillis();
    }

    // ── Factories ─────────────────────────────────────────────────

    public static OutboundMessage of(String type, Object payload) {
        return new OutboundMessage(type, payload);
    }

    public static OutboundMessage error(String code, String message) {
        return new OutboundMessage("ERROR", new ErrorPayload(code, message));
    }

    public static OutboundMessage notification(String message) {
        return new OutboundMessage("NOTIFICATION", new SimpleMessage(message));
    }

    // ── Getters ───────────────────────────────────────────────────

    public String getType()      { return type; }
    public Object getPayload()   { return payload; }
    public long   getTimestamp() { return timestamp; }

    // ── Nested payload types used by factories ────────────────────

    public record ErrorPayload(String code, String message) {}
    public record SimpleMessage(String message) {}
}
