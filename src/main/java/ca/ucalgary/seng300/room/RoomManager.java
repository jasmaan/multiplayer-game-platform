package ca.ucalgary.seng300.room;

import ca.ucalgary.seng300.core.PlatformError;
import ca.ucalgary.seng300.session.GameSession;
import ca.ucalgary.seng300.session.GameSessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages room/lobby lifecycle: creation, joining, leaving, and cleanup.
 */
public class RoomManager {

    private final Map<String, Room> rooms;
    private final GameSessionManager gameSessionManager;

    public RoomManager(GameSessionManager gameSessionManager) {
        this.rooms = new HashMap<>();
        this.gameSessionManager = gameSessionManager;
    }

    /**
     * Create a new room with the specified host, game type, and max players.
     * @return the created Room
     */
    public Room createRoom(String hostId, String gameType, int maxPlayers) {
        String roomId = UUID.randomUUID().toString();
        Room room = new Room(roomId, hostId, gameType, maxPlayers);
        rooms.put(roomId,  room);
        return room;
    }

    /**
     * Add a player to an existing room.
     * @return true if successfully joined
     */
    public PlatformError joinRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return PlatformError.ROOM_NOT_FOUND;
        }
        if (room.getPlayers().contains(playerId)) {
            return PlatformError.PLAYER_ALREADY_IN_ROOM;
        }
        if (room.getStatus() == RoomStatus.CLOSED || room.getStatus() == RoomStatus.IN_PROGRESS) {
            return PlatformError.INVALID_STATE;
        }
        if (room.isFull()) {
            return PlatformError.ROOM_FULL;
        }
        boolean added = room.addPlayer(playerId);
        if (!added) {
            return PlatformError.INVALID_STATE;
        }
        return null;
    }
    /**
     * Remove a player from a room.
     * @return true if successfully left
     */
    public PlatformError leaveRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return PlatformError.ROOM_NOT_FOUND;
        }
        if (!room.getPlayers().contains(playerId)) {
            return PlatformError.PLAYER_NOT_IN_ROOM;
        }
        boolean removed = room.removePlayer(playerId);
        if (!removed) {
            return PlatformError.INVALID_STATE;
        }

        if (room.isEmpty()) {
            room.setStatus(RoomStatus.CLOSED);
            rooms.remove(roomId);
        }

        return null; // success
    }


    public RoomStatus getRoomStatus(String roomId) {
        Room room = rooms.get(roomId);
        return room != null ? room.getStatus() : null;
    }

    /**
     * Get all open rooms for a given game type.
     */
    public List<Room> getAvailableRooms(String gameType) {
        List<Room> available = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (gameType != null && room.getStatus() == RoomStatus.OPEN && room.getGameType().equals(gameType)) {
                available.add(room);
            }
        }
        return available;
    }

    /**
     * Get all rooms for a given game type regardless of status.
     * Used for cleanup after a match ends (matched rooms have status FULL, not OPEN).
     */
    public List<Room> getAllRooms(String gameType) {
        List<Room> all = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (gameType == null || room.getGameType().equals(gameType)) {
                all.add(room);
            }
        }
        return all;
    }

    /**
     * Check if room has enough players to start.
     */
    public boolean isRoomReady(String roomId) {
        Room room = rooms.get(roomId);
        // Default: room is ready when full (can be customized per game)
        return room != null && room.isFull();
    }
    public PlatformError startRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return PlatformError.ROOM_NOT_FOUND;
        }
        if (room.getStatus() == RoomStatus.IN_PROGRESS) {
            return PlatformError.INVALID_STATE;
        }
        if (isRoomReady(roomId)) {
            room.setStatus(RoomStatus.IN_PROGRESS);
            gameSessionManager.startSession(roomId, room.getGameType(), room.getPlayers());
            return null;
        }
        return PlatformError.INVALID_STATE;
    }
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }
}
