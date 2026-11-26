package ca.ucalgary.seng300.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents a game room/lobby where players gather before a session starts.
 */
public class Room {

    private final String roomId;
    private  String hostId;
    private final String gameType;
    private final List<String> players;
    private final int maxPlayers;
    private RoomStatus status;
    private final long createdAt;

    public Room(String roomId, String hostId, String gameType, int maxPlayers) {
        this.roomId = roomId;
        this.hostId = hostId;
        this.gameType = gameType;
        this.maxPlayers = maxPlayers;
        this.players = new ArrayList<>();
        this.status = RoomStatus.OPEN;
        this.createdAt = System.currentTimeMillis();
        // Host automatically joins the room
        this.players.add(hostId);

    }


    public boolean addPlayer(String playerId) {
        if (isFull() || players.contains(playerId)) {
            return false;
        }
        players.add(playerId);
        if (isFull()) {
            this.status = RoomStatus.FULL;
        }
        return true;
    }

    public boolean removePlayer(String playerId) {
        boolean removed = players.remove(playerId);
        if (!removed) {
            return false;
        }

        if (players.isEmpty()) {
            this.status = RoomStatus.CLOSED;
            return true;
        }

        // Transfer host to a random remaining player if the host left
        if (playerId.equals(hostId)) {
            Random random = new Random();
            hostId = players.get(random.nextInt(players.size()));
        }

        // If the room was full before, it now has a free slot — reopen it
        if (this.status == RoomStatus.FULL) {
            this.status = RoomStatus.OPEN;
        }

        return true;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public int getPlayerCount() {
        return players.size();
    }

    public List<String> getPlayers() {
        return new ArrayList<>(players);
    }

    public String getRoomId() { return roomId; }
    public String getHostId() { return hostId; }
    public String getGameType() { return gameType; }
    public int getMaxPlayers() { return maxPlayers; }
    public long getCreatedAt() { return createdAt; }

    public RoomStatus getStatus() { return status; }

    public void setStatus(RoomStatus status) { this.status = status; }
}
