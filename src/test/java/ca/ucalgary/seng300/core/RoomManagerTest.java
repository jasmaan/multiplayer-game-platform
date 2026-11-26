package ca.ucalgary.seng300.core;
import ca.ucalgary.seng300.room.*;

import ca.ucalgary.seng300.core.PlatformError;
import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.session.GameSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomManagerTest {

    private RoomManager roomManager;

    @BeforeEach
    void setup() {
        roomManager = new RoomManager(new GameSessionManager(new EventBus()));
    }

    // --------------------------- Create Room --------------------------------------
    @Test
    void createRoom_hostIsAdded() {
        Room room = roomManager.createRoom("host1", "chess", 2);

        assertNotNull(room);
        assertEquals(1, room.getPlayerCount());
        assertTrue(room.getPlayers().contains("host1"));
        assertEquals(RoomStatus.OPEN, room.getStatus());
    }

    @Test
    void createRoom_assignsUniqueRoomIds() {
        Room r1 = roomManager.createRoom("host1", "connect", 3);
        Room r2 = roomManager.createRoom("host1", "connect", 3);

        assertNotEquals(r1.getRoomId(), r2.getRoomId());
    }

    @Test
    void createRoom_correctFieldsStored() {
        Room room = roomManager.createRoom("dave", "MissingGame", 6);

        assertEquals("dave", room.getHostId());
        assertEquals("MissingGame", room.getGameType());
        assertEquals(6, room.getMaxPlayers());
    }


    // ----------------- JOIN ------------------------------ //
    @Test
    void joinRoom_roomNotFound() {
        PlatformError err = roomManager.joinRoom("fake-id", "player1");

        assertEquals(PlatformError.ROOM_NOT_FOUND, err);
    }
    @Test
    void joinRoom_roomFull() {
        Room room = roomManager.createRoom("host1", "gametype", 2);
        roomManager.joinRoom(room.getRoomId(), "player2");

        PlatformError err = roomManager.joinRoom(room.getRoomId(), "player3");

        assertEquals(PlatformError.ROOM_FULL, err);
    }

    // check for Duplicate Join
    @Test
    void joinRoom_duplicatePlayer() {
        Room room = roomManager.createRoom("host1", "gametype", 3);

        PlatformError err = roomManager.joinRoom(room.getRoomId(), "host1");

        assertEquals(PlatformError.PLAYER_ALREADY_IN_ROOM, err);
    }
    @Test
    void joinRoom_success() {
        Room room = roomManager.createRoom("eve", "MissingGame", 3);

        PlatformError err = roomManager.joinRoom(room.getRoomId(), "frank");

        assertNull(err);
        assertEquals(2, room.getPlayerCount());
        assertTrue(room.getPlayers().contains("frank"));
    }

    @Test
    void joinRoom_multiplePlayersJoinSuccessfully() {
        Room room = roomManager.createRoom("grace", "MissingGame", 4);

        assertNull(roomManager.joinRoom(room.getRoomId(), "henry"));
        assertNull(roomManager.joinRoom(room.getRoomId(), "isabel"));

        assertEquals(3, room.getPlayerCount());
    }
    // -------------- Leave Room --------------------
    @Test
    void leaveRoom_roomNotFound() {
        PlatformError err = roomManager.leaveRoom("fake-id", "player1");

        assertEquals(PlatformError.ROOM_NOT_FOUND, err);
    }
    @Test
    void leaveRoom_success() {
        Room room = roomManager.createRoom("host1", "missinggame", 2);
        roomManager.joinRoom(room.getRoomId(), "player2");

        PlatformError err = roomManager.leaveRoom(room.getRoomId(), "player2");

        assertNull(err);
        assertEquals(1, room.getPlayerCount());
    }

    @Test
    void leaveRoom_playerNotInRoom() {
        Room room = roomManager.createRoom("host1", "gametype", 2);

        PlatformError err = roomManager.leaveRoom(room.getRoomId(), "ghost");

        assertEquals(PlatformError.PLAYER_NOT_IN_ROOM, err);
    }

    @Test
    void leaveRoom_hostTransfer() {
        Room room = roomManager.createRoom("host1", "gametype", 3);
        roomManager.joinRoom(room.getRoomId(), "player2");
        roomManager.joinRoom(room.getRoomId(), "player3");

        roomManager.leaveRoom(room.getRoomId(), "host1");

        assertNotEquals("host1", room.getHostId());

        assertFalse(room.getPlayers().contains("host1"));
    }

    @Test
    void leaveRoom_emptyRoomRemoved() {
        Room room = roomManager.createRoom("host1", "tictac", 2);

        roomManager.leaveRoom(room.getRoomId(), "host1");

        assertNull(roomManager.getRoom(room.getRoomId()));
    }
    // ------------------ Get Available Room -------------------------------
    @Test
    void getAvailableRooms_filtersCorrectly() {
        roomManager.createRoom("host1", "tictac", 2);
        roomManager.createRoom("host2", "gametype", 2);

        assertEquals(1, roomManager.getAvailableRooms("tictac").size());
        assertEquals(1, roomManager.getAvailableRooms("gametype").size());
    }

    @Test
    void startRoom_twice_failsSecondTime() {
        Room room = roomManager.createRoom("host1", "tictac", 2);
        roomManager.joinRoom(room.getRoomId(), "player2");

        assertNull(roomManager.startRoom(room.getRoomId()));

        PlatformError err = roomManager.startRoom(room.getRoomId());

        assertEquals(PlatformError.INVALID_STATE, err);
    }
    @Test
    void leaveRoom_fromFullRoom_reopensRoom() {
        Room room = roomManager.createRoom("host1", "checkers", 2);
        roomManager.joinRoom(room.getRoomId(), "player2");

        assertEquals(RoomStatus.FULL, room.getStatus());

        roomManager.leaveRoom(room.getRoomId(), "player2");

        assertEquals(RoomStatus.OPEN, room.getStatus());
    }
    @Test
    void leaveRoom_nonHostLeaves_hostUnchanged() {
        Room room = roomManager.createRoom("host1", "checkers", 3);
        roomManager.joinRoom(room.getRoomId(), "player2");

        roomManager.leaveRoom(room.getRoomId(), "player2");

        assertEquals("host1", room.getHostId());
    }
    @Test
    void joinRoom_afterGameStarted_fails() {
        Room room = roomManager.createRoom("host1", "tictac", 2);
        roomManager.joinRoom(room.getRoomId(), "player2");
        roomManager.startRoom(room.getRoomId());

        PlatformError err = roomManager.joinRoom(room.getRoomId(), "Player3");

        assertEquals(PlatformError.INVALID_STATE, err);
    }

    @Test
    void startRoom_success() {
        Room room = roomManager.createRoom("host1", "tictac", 2);
        roomManager.joinRoom(room.getRoomId(), "player2");

        PlatformError err = roomManager.startRoom(room.getRoomId());

        assertEquals(null,err);
        assertEquals(RoomStatus.IN_PROGRESS, room.getStatus());
    }

    @Test
    void startRoom_notReady() {
        Room room = roomManager.createRoom("host1", "tictac", 3);

        PlatformError err = roomManager.startRoom(room.getRoomId());

        assertEquals(PlatformError.INVALID_STATE, err);
    }
    @Test
    void getAvailableRooms_excludesInProgressRooms() {
        Room room = roomManager.createRoom("host3", "tictac", 2);
        roomManager.joinRoom(room.getRoomId(), "player2");
        roomManager.startRoom(room.getRoomId()); // room is now IN_PROGRESS

        assertEquals(0, roomManager.getAvailableRooms("tictac").size());
    }

    @Test
    void getAvailableRooms_allRoomsFull_returnsEmpty() {
        Room room1 = roomManager.createRoom("host4", "connect4", 2);
        Room room2 = roomManager.createRoom("host5", "connect4", 2);
        roomManager.joinRoom(room1.getRoomId(), "player2");
        roomManager.joinRoom(room2.getRoomId(), "player3");
        assertEquals(0, roomManager.getAvailableRooms("connect4").size());
    }

    @Test
    void getAvailableRooms_mixedStates_returnsOnlyOpen() {
        Room openRoom       = roomManager.createRoom("host6", "checkers", 3);
        Room fullRoom       = roomManager.createRoom("host7", "checkers", 2);
        roomManager.joinRoom(fullRoom.getRoomId(), "player4");  // fills fullRoom
        roomManager.joinRoom(fullRoom.getRoomId(), "player5");  // fills fullRoom

        assertEquals(1, roomManager.getAvailableRooms("checkers").size());
        assertEquals(openRoom.getRoomId(),
                roomManager.getAvailableRooms("checkers").get(0).getRoomId());
    }

    // start room that never existed.
    @Test
    void startRoom_roomNotFound() {
        PlatformError err = roomManager.startRoom("fake-id");

        assertEquals(PlatformError.ROOM_NOT_FOUND, err);
    }
    @Test
    void startRoom_success_statusNoLongerOpen() {
        Room room = roomManager.createRoom("host8", "tictac", 2);
        roomManager.joinRoom(room.getRoomId(), "player5");

        roomManager.startRoom(room.getRoomId());

        assertNotEquals(PlatformError.INVALID_STATE, room.getStatus());
    }

    @Test
    void startRoom_notReady_partialFourPlayerRoom() {
        Room room = roomManager.createRoom("host9", "poker", 4);
        roomManager.joinRoom(room.getRoomId(), "player6");

        PlatformError err = roomManager.startRoom(room.getRoomId());

        assertEquals(PlatformError.INVALID_STATE, err);
        assertNotEquals(RoomStatus.IN_PROGRESS, room.getStatus());
    }
}