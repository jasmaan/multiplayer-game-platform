package ca.ucalgary.seng300.matchmaking;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Queue of players waiting for a match in a specific game type.
 */
public class PlayerQueue {

    private final String gameType;
    private final LinkedList<String> waitingPlayers;

    public PlayerQueue(String gameType) {
        this.gameType = gameType;
        this.waitingPlayers = new LinkedList<>();
    }

    public void enqueue(String playerId) {
        if (!waitingPlayers.contains(playerId)) {
            waitingPlayers.add(playerId);
        }
    }

    public String dequeue() {
        return waitingPlayers.isEmpty() ? null : waitingPlayers.removeFirst();
    }

    public boolean remove(String playerId) {
        return waitingPlayers.remove(playerId);
    }

    public int size() {
        return waitingPlayers.size();
    }

    public int getPosition(String playerId) {
        return waitingPlayers.indexOf(playerId);
    }

    public boolean isPlayerInQueue(String playerId){
        return waitingPlayers.contains(playerId);
    }

    public int getQueueSize(String playerId){
        return waitingPlayers.size();
    }

    /**
     * Peek at the first N players in the queue without removing them.
     */
    public List<String> peek(int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(count, waitingPlayers.size()); i++) {
            result.add(waitingPlayers.get(i));
        }
        return result;
    }

    public String getGameType() { return gameType; }
}
