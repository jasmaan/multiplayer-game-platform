package ca.ucalgary.seng300.client;

import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.server.dto.OutboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps inbound server frames to local callbacks or EventBus events.
 *
 * All callbacks fire on the JavaFX application thread (Platform.runLater),
 * so UI controllers can update controls directly without extra marshalling.
 *
 * Register typed handlers before connecting:
 *
 *   dispatcher.on("MATCH_FOUND",    payload -> showMatchModal(payload));
 *   dispatcher.on("NOTIFICATION",   payload -> showToast(payload));
 *   dispatcher.on("MOVE_ACCEPTED",  payload -> applyMove(payload));
 *   dispatcher.on("CHAT_MESSAGE",   payload -> appendChat(payload));
 *   dispatcher.on("PLAY_AGAIN_READY", payload -> startRematch(payload));
 *
 * The raw payload is passed as a {@code Map<String,Object>} (Jackson's default
 * for JSON objects).  Cast or re-map fields as needed in each handler.
 */
public class ClientEventDispatcher {

    private static final Logger LOG = Logger.getLogger(ClientEventDispatcher.class.getName());

    private final ObjectMapper mapper;
    // type → handler
    private final Map<String, Consumer<Map<String, Object>>> handlers = new HashMap<>();

    public ClientEventDispatcher() {
        this.mapper = new ObjectMapper();
    }

    // ── Handler registration ───────────────────────────────────────────────────

    /**
     * Register a handler for a specific frame type.
     * The handler runs on the JavaFX application thread.
     *
     * @param type    the frame's {@code "type"} string (e.g. "NOTIFICATION")
     * @param handler callback that receives the deserialized payload map
     */
    public void on(String type, Consumer<Map<String, Object>> handler) {
        handlers.put(type, handler);
    }

    /** Remove a previously registered handler. */
    public void off(String type) {
        handlers.remove(type);
    }

    // ── Dispatch ───────────────────────────────────────────────────────────────

    /**
     * Called by {@link ServerConnection} for every inbound frame.
     * Looks up the registered handler and fires it on the JavaFX thread.
     */
    @SuppressWarnings("unchecked")
    public void dispatch(OutboundMessage msg) {
        if (msg == null || msg.getType() == null) return;

        String type = msg.getType();

        // PING from server heartbeat — silently ignore
        if ("PING".equals(type)) return;

        Consumer<Map<String, Object>> handler = handlers.get(type);
        if (handler == null) {
            LOG.fine("No handler registered for frame type: " + type);
            return;
        }

        // Coerce payload to Map<String,Object> for uniform handling
        Map<String, Object> payloadMap;
        try {
            Object raw = msg.getPayload();
            if (raw == null) {
                payloadMap = Map.of();
            } else if (raw instanceof Map) {
                payloadMap = (Map<String, Object>) raw;
            } else {
                // Re-serialise then deserialise (handles nested records like ErrorPayload)
                String json = mapper.writeValueAsString(raw);
                payloadMap  = mapper.readValue(json, Map.class);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not coerce payload for type=" + type, e);
            payloadMap = Map.of();
        }

        final Map<String, Object> finalPayload = payloadMap;
        Platform.runLater(() -> {
            try {
                handler.accept(finalPayload);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Handler threw for type=" + type, e);
            }
        });
    }
}
