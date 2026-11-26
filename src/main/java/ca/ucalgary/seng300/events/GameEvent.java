package ca.ucalgary.seng300.events;

/**
 * Abstract base class for all platform events.
 * All events flow through the EventBus and are consumed by PlatformCore.
 */
public abstract class GameEvent {

    private final String eventId;
    private final long timestamp;
    private final String eventType;

    protected GameEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.timestamp = System.currentTimeMillis();
        this.eventType = eventType;
    }

    public String getEventId() { return eventId; }
    public long getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
}
