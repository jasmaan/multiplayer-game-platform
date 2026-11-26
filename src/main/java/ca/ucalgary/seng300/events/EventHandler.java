package ca.ucalgary.seng300.events;

/**
 * Interface for any component that subscribes to events on the EventBus.
 */
public interface EventHandler {

    /**
     * Handle an incoming event from the EventBus.
     * @param event the event to process
     */
    void handle(GameEvent event);
}
