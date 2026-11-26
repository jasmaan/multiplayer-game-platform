package ca.ucalgary.seng300.events;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publish/subscribe event bus for decoupling platform components.
 * Publishers emit events by type; subscribers receive matching events.
 */
public class EventBus {

    private final Map<String, List<EventHandler>> subscribers = new ConcurrentHashMap<>();

    public EventBus() {}

    /**
     * Publish an event to all subscribers of its event type.
     * @param event the event to publish
     */
    public void publish(GameEvent event) {
        //null guard
        if (event == null) {
            System.err.println("[EVENTBUS] Warning: null event published, ignoring");
            return;
        }
        //if event is not null, pass event to event handlers
        List<EventHandler> handlers = subscribers.get(event.getEventType());
        if (handlers != null) {
            for (EventHandler handler : handlers) {
                handler.handle(event);
            }
        }
    }

    /**
     * Subscribe a handler to a specific event type.
     * @param eventType the type of event to listen for
     * @param handler the handler to invoke
     */
    public void subscribe(String eventType, EventHandler handler) {
        //null guard
        if (eventType == null || eventType.isBlank() || handler == null) {
            System.err.println("[EVENTBUS] Warning: invalid subscribe call, ignoring");
            return;
        }

        List<EventHandler> handlers = subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        if (!handlers.contains(handler)) {
            handlers.add(handler);
        } else {
            System.err.println("[EVENTBUS] Warning: handler already subscribed to " + eventType + ", ignoring duplicate");
        }
    }

    /**
     * Unsubscribe a handler from a specific event type.
     * @param eventType the event type
     * @param handler the handler to remove
     */
    public void unsubscribe(String eventType, EventHandler handler) {
        if (eventType == null || eventType.isBlank() || handler == null) {
            System.err.println("[EVENTBUS] Warning: invalid unsubscribe call, ignoring");
            return;
        }

        List<EventHandler> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }
}
