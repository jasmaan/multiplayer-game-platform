package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.client.NetworkClient;
import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.core.PlatformError;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.gameai.DinoBot;
import ca.ucalgary.seng300.notification.NotificationService;
import ca.ucalgary.seng300.room.Room;
import ca.ucalgary.seng300.ui.controllers.MatchFoundController.PlayerData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameRoomPageController implements NotificationService.NotificationListener {

    private PlatformCore platformCore;
    private String gameType;
    private boolean inQueue = false;
    private Room currentRoom = null;

    // ── Network-mode room state ────────────────────────────────────
    // When connected to the server, rooms live on the server. We track
    // just the roomId and the last-known player count so we can show
    // the "current room" panel without a local Room object.
    private String  networkRoomId          = null;
    private int     networkRoomPlayerCount = 0;
    private int     networkRoomMaxPlayers  = 2;
    private boolean networkRoomFull        = false;

    @FXML private Label  gameNameLabel;
    @FXML private Label  gameTypeTag;
    @FXML private Label  roomCountLabel;
    @FXML private Label  queueStatusLabel;
    @FXML private Label  currentRoomLabel;
    @FXML private VBox   roomListContainer;
    @FXML private VBox   queueStatusBox;
    @FXML private HBox   roomStatusBanner;
    @FXML private Button joinQueueBtn;
    @FXML private Button createRoomBtn;
    @FXML private Button cancelRoomBtn;
    @FXML private Button startGameBtn;

    public void setPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
        NetworkClient nc = NetworkClient.getInstance();
        if (nc.isNetworkMode()) {
            // ROOM_CREATED — we just created a room on the server
            nc.getDispatcher().on("ROOM_CREATED", payload -> javafx.application.Platform.runLater(() -> {
                networkRoomId = (String) payload.get("roomId");
                networkRoomPlayerCount = 1;
                networkRoomFull = false;
                showCurrentRoomNetwork();
                refreshRoomsFromServer();
            }));
            // ROOM_JOINED — we (or someone else) joined; refresh state
            nc.getDispatcher().on("ROOM_JOINED", payload -> javafx.application.Platform.runLater(() -> {
                String joinedRoom = (String) payload.get("roomId");
                if (networkRoomId == null) {
                    // We are the one who joined
                    networkRoomId = joinedRoom;
                }
                refreshCurrentRoomState();
                refreshRoomsFromServer();
            }));
            // NOTIFICATION — someone joined our room (server pushes this to host)
            nc.getDispatcher().on("NOTIFICATION", payload -> javafx.application.Platform.runLater(() -> {
                refreshCurrentRoomState();
                refreshRoomsFromServer();
            }));
            // GAME_STARTED — host sent START_GAME and server responded
            nc.getDispatcher().on("GAME_STARTED", this::handleNetworkGameStarted);
        } else {
            platformCore.getNotificationService().setListener(this);
        }
    }

    @Override
    public void onNotification(String playerId, String message) {
        // Someone joined the room — refresh the panel so Start Game enables when full.
        javafx.application.Platform.runLater(() -> {
            if (currentRoom != null) showCurrentRoom(currentRoom);
            refreshRooms();
        });
    }

    @Override
    public void onMatchFound(MatchFoundEvent event) {
        showMatchFoundModal(event);
    }

    private void showMatchFoundModal(MatchFoundEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MatchFound.fxml"));
            Parent root = loader.load();
            MatchFoundController ctrl = loader.getController();

            String gameType = event.getGameType();
            java.util.List<PlayerData> players = new java.util.ArrayList<>();
            for (String id : event.getPlayers()) {
                String initials = id.length() >= 2 ? id.substring(0, 2).toUpperCase() : id.toUpperCase();
                players.add(new PlayerData(id, initials, false));
            }
            ctrl.initMatch(formatGameName(gameType), players);

            javafx.stage.Stage matchStage = new javafx.stage.Stage();
            matchStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            matchStage.setTitle("Match Found!");
            matchStage.setScene(new Scene(root));
            matchStage.setResizable(false);

            ctrl.setOnAccepted(() -> javafx.application.Platform.runLater(() -> {
                ctrl.stopCountdown();
                matchStage.close();
                handleStartMatchGame(event, gameType);
            }));
            ctrl.setOnDeclined(() -> javafx.application.Platform.runLater(matchStage::close));
            ctrl.setOnTimeout(() -> javafx.application.Platform.runLater(matchStage::close));

            matchStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleStartMatchGame(MatchFoundEvent event, String gameType) {
        platformCore.getNotificationService().setListener(null);
        try {
            java.util.List<String> players = event.getPlayers();
            String sessionId = platformCore.getSessionManager()
                    .startSession(event.getRoomId(), gameType, players);
            if (sessionId == null) return;

            String fxmlPath = gameType.equalsIgnoreCase("ConnectFour")
                    ? "/fxml/ConnectFourGamePage.fxml"
                    : gameType.equalsIgnoreCase("Checkers")
                    ? "/fxml/CheckersGamePage.fxml"
                    : "/fxml/TicTacToeGamePage.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            if (gameType.equalsIgnoreCase("ConnectFour")) {
                ConnectFourGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
            } else if (gameType.equalsIgnoreCase("Checkers")) {
                CheckersGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
            } else {
                TicTacToeGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
            }

            Stage stage = (Stage) roomListContainer.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when the server pushes GAME_STARTED (room-based network flow).
     * Navigates to the appropriate game page and enables network mode on the controller.
     */
    @SuppressWarnings("unchecked")
    private void handleNetworkGameStarted(Map<String, Object> payload) {
        javafx.application.Platform.runLater(() -> {
            NetworkClient nc = NetworkClient.getInstance();
            nc.getDispatcher().off("GAME_STARTED");
            nc.getDispatcher().off("ROOM_CREATED");
            nc.getDispatcher().off("ROOM_JOINED");
            nc.getDispatcher().off("NOTIFICATION");

            String sessionId  = (String) payload.get("sessionId");
            String gType      = (String) payload.get("gameType");
            List<String> players = (List<String>) payload.get("players");
            if (sessionId == null || gType == null || players == null) return;

            String myId = nc.getPlayerId();
            try {
                String fxmlPath = gType.equalsIgnoreCase("ConnectFour")
                        ? "/fxml/ConnectFourGamePage.fxml"
                        : gType.equalsIgnoreCase("Checkers")
                        ? "/fxml/CheckersGamePage.fxml"
                        : "/fxml/TicTacToeGamePage.fxml";

                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                Parent root = loader.load();

                if (gType.equalsIgnoreCase("ConnectFour")) {
                    ConnectFourGamePageController ctrl = loader.getController();
                    ctrl.setSessionData(platformCore, sessionId, players);
                    ctrl.enableNetworkMode(myId, nc);
                } else if (gType.equalsIgnoreCase("Checkers")) {
                    CheckersGamePageController ctrl = loader.getController();
                    ctrl.setSessionData(platformCore, sessionId, players);
                    ctrl.enableNetworkMode(myId, nc);
                } else {
                    TicTacToeGamePageController ctrl = loader.getController();
                    ctrl.setSessionData(platformCore, sessionId, players);
                    ctrl.enableNetworkMode(myId, nc);
                }

                Stage stage = (Stage) roomListContainer.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.sizeToScene();
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
        String displayName = formatGameName(gameType);
        gameNameLabel.setText(displayName + " — Rooms");
        gameTypeTag.setText(displayName.toUpperCase());
        refreshRooms();
        if (!NetworkClient.getInstance().isNetworkMode()) {
            restoreExistingRoom();
        }
    }

    /**
     * After navigating back to this page, check whether the current player already
     * hosts an open room for this game type. If so, restore the currentRoom reference
     * and show the room panel — otherwise the room becomes unmanageable (no cancel button).
     */
    private void restoreExistingRoom() {
        String playerId = platformCore.getCurrentUser();
        platformCore.getRoomManager().getAvailableRooms(gameType).stream()
                .filter(r -> r.getHostId().equals(playerId))
                .findFirst()
                .ifPresent(room -> {
                    currentRoom = room;
                    showCurrentRoom(room);
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Room list
    // ─────────────────────────────────────────────────────────────

    private void refreshRooms() {
        if (NetworkClient.getInstance().isNetworkMode()) {
            refreshRoomsFromServer();
            return;
        }
        roomListContainer.getChildren().clear();
        List<Room> rooms = platformCore.getRoomManager().getAvailableRooms(gameType);

        roomCountLabel.setText(rooms.size() + " room" + (rooms.size() == 1 ? "" : "s") + " available");

        if (rooms.isEmpty()) {
            Label empty = new Label("No open rooms. Create one or join the queue!");
            empty.setTextFill(Color.web("#3d6080"));
            empty.setStyle("-fx-font-size: 13px;");
            empty.setWrapText(true);
            roomListContainer.getChildren().add(empty);
            return;
        }

        for (Room room : rooms) {
            roomListContainer.getChildren().add(buildRoomCard(room));
        }
    }

    /** Fetch the room list from the server and render it. */
    private void refreshRoomsFromServer() {
        List<Map<String, Object>> allRooms = NetworkClient.getInstance().getJsonList("/rooms");
        // Filter by gameType
        List<Map<String, Object>> rooms = allRooms.stream()
                .filter(r -> gameType.equalsIgnoreCase((String) r.get("gameType")))
                .filter(r -> !Boolean.TRUE.equals(r.get("isFull")))
                .toList();

        javafx.application.Platform.runLater(() -> {
            roomListContainer.getChildren().clear();
            roomCountLabel.setText(rooms.size() + " room" + (rooms.size() == 1 ? "" : "s") + " available");
            if (rooms.isEmpty()) {
                Label empty = new Label("No open rooms. Create one or join the queue!");
                empty.setTextFill(Color.web("#3d6080"));
                empty.setStyle("-fx-font-size: 13px;");
                empty.setWrapText(true);
                roomListContainer.getChildren().add(empty);
            } else {
                for (Map<String, Object> room : rooms) {
                    roomListContainer.getChildren().add(buildServerRoomCard(room));
                }
            }
        });
    }

    /** Build a room card from a server-side room map (roomId, gameType, host, isFull). */
    private VBox buildServerRoomCard(Map<String, Object> room) {
        String roomId = (String) room.get("roomId");
        String host   = (String) room.get("host");
        boolean full  = Boolean.TRUE.equals(room.get("isFull"));

        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: rgba(10,25,45,0.92); -fx-background-radius: 14; " +
                "-fx-border-color: rgba(77,166,255,0.2); -fx-border-width: 1; " +
                "-fx-border-radius: 14; -fx-padding: 14 16;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label idLabel = new Label("Room " + roomId.substring(0, Math.min(8, roomId.length())));
        idLabel.setTextFill(Color.web("#e6f3ff"));
        idLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        HBox.setHgrow(idLabel, Priority.ALWAYS);
        String statusColor = full ? "#ff8040" : "#40d090";
        String statusText  = full ? "FULL" : "OPEN";
        Label statusBadge = new Label(statusText);
        statusBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; " +
                "-fx-background-color: " + statusColor + "22; -fx-border-color: " + statusColor + "88; " +
                "-fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5; " +
                "-fx-padding: 2 8; -fx-text-fill: " + statusColor + ";");
        topRow.getChildren().addAll(idLabel, statusBadge);

        Label hostLabel = new Label("Host: " + host);
        hostLabel.setTextFill(Color.web("#7fbfff"));
        hostLabel.setStyle("-fx-font-size: 11px;");

        javafx.scene.control.Button joinBtn = new javafx.scene.control.Button(full ? "Room Full" : "Join Room");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setPrefHeight(34);
        if (full) {
            joinBtn.setStyle("-fx-background-color: rgba(30,50,80,0.5); -fx-text-fill: #3d6080; " +
                    "-fx-font-size: 12px; -fx-background-radius: 8; -fx-cursor: default;");
            joinBtn.setDisable(true);
        } else {
            joinBtn.setStyle("-fx-background-color: rgba(77,166,255,0.15); -fx-text-fill: #4da6ff; " +
                    "-fx-font-weight: bold; -fx-font-size: 12px; -fx-background-radius: 8; " +
                    "-fx-border-color: rgba(77,166,255,0.35); -fx-border-width: 1; " +
                    "-fx-border-radius: 8; -fx-cursor: hand;");
            joinBtn.setOnAction(e -> handleJoinRoomNetwork(roomId));
        }
        card.getChildren().addAll(topRow, hostLabel, joinBtn);
        return card;
    }

    /** Re-fetch this player's current room from the server and update the panel. */
    private void refreshCurrentRoomState() {
        if (networkRoomId == null) return;
        List<Map<String, Object>> allRooms = NetworkClient.getInstance().getJsonList("/rooms");
        allRooms.stream()
                .filter(r -> networkRoomId.equals(r.get("roomId")))
                .findFirst()
                .ifPresent(r -> {
                    networkRoomFull = Boolean.TRUE.equals(r.get("isFull"));
                    // /rooms only shows open rooms, so if it's in the list it isn't full;
                    // if not found it may have gone full. Check the flag directly.
                    networkRoomPlayerCount = networkRoomFull ? networkRoomMaxPlayers : 1;
                    showCurrentRoomNetwork();
                });
        // If room not found in open rooms, check if it became full
        boolean foundInOpen = allRooms.stream().anyMatch(r -> networkRoomId.equals(r.get("roomId")));
        if (!foundInOpen && networkRoomId != null) {
            // Room is either full or was removed — assume full (2/2)
            networkRoomFull = true;
            networkRoomPlayerCount = networkRoomMaxPlayers;
            showCurrentRoomNetwork();
        }
    }

    /** Show the "current room" panel using network-tracked state. */
    private void showCurrentRoomNetwork() {
        String status = networkRoomFull ? "Room full — ready to start!" : "Waiting for players to join…";
        currentRoomLabel.setText(
                networkRoomId.substring(0, Math.min(8, networkRoomId.length())) + "…  ·  " +
                networkRoomPlayerCount + "/" + networkRoomMaxPlayers + " players  ·  " + status);
        roomStatusBanner.setVisible(true);
        roomStatusBanner.setManaged(true);
        createRoomBtn.setDisable(true);
        startGameBtn.setDisable(!networkRoomFull);
        if (networkRoomFull) {
            startGameBtn.setStyle("-fx-background-color: linear-gradient(to right, #40d090, #20b070); " +
                    "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; " +
                    "-fx-background-radius: 8; -fx-cursor: hand;");
        } else {
            startGameBtn.setStyle("-fx-background-color: rgba(30,50,80,0.5); -fx-text-fill: #3d6080; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: default;");
        }
    }

    private VBox buildRoomCard(Room room) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: rgba(10,25,45,0.92); -fx-background-radius: 14; " +
                "-fx-border-color: rgba(77,166,255,0.2); -fx-border-width: 1; " +
                "-fx-border-radius: 14; -fx-padding: 14 16;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label idLabel = new Label("Room " + room.getRoomId());
        idLabel.setTextFill(Color.web("#e6f3ff"));
        idLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        HBox.setHgrow(idLabel, Priority.ALWAYS);

        String statusColor = room.isFull() ? "#ff8040" : "#40d090";
        String statusText  = room.isFull() ? "FULL" : "OPEN";
        Label statusBadge = new Label(statusText);
        statusBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; " +
                "-fx-background-color: " + statusColor + "22; " +
                "-fx-border-color: " + statusColor + "88; " +
                "-fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5; " +
                "-fx-padding: 2 8; -fx-text-fill: " + statusColor + ";");

        topRow.getChildren().addAll(idLabel, statusBadge);

        HBox infoRow = new HBox(16);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        Label hostLabel = new Label("Host: " + room.getHostId());
        hostLabel.setTextFill(Color.web("#7fbfff"));
        hostLabel.setStyle("-fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label playersLabel = new Label(room.getPlayerCount() + " / " + room.getMaxPlayers() + " players");
        playersLabel.setTextFill(Color.web("#b9d9ff"));
        playersLabel.setStyle("-fx-font-size: 11px;");

        infoRow.getChildren().addAll(hostLabel, spacer, playersLabel);

        Button joinBtn = new Button(room.isFull() ? "Room Full" : "Join Room");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setPrefHeight(34);
        if (room.isFull()) {
            joinBtn.setStyle("-fx-background-color: rgba(30,50,80,0.5); -fx-text-fill: #3d6080; " +
                    "-fx-font-size: 12px; -fx-background-radius: 8; -fx-cursor: default;");
            joinBtn.setDisable(true);
        } else {
            joinBtn.setStyle("-fx-background-color: rgba(77,166,255,0.15); -fx-text-fill: #4da6ff; " +
                    "-fx-font-weight: bold; -fx-font-size: 12px; -fx-background-radius: 8; " +
                    "-fx-border-color: rgba(77,166,255,0.35); -fx-border-width: 1; " +
                    "-fx-border-radius: 8; -fx-cursor: hand;");
            joinBtn.setOnAction(e -> handleJoinRoom(room.getRoomId()));
        }

        card.getChildren().addAll(topRow, infoRow, joinBtn);
        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // Handlers
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleRefresh(ActionEvent ignoredEvent) {
        refreshRooms();
    }

    @FXML
    private void handleCreateRoom(ActionEvent ignoredEvent) {
        NetworkClient nc = NetworkClient.getInstance();
        if (nc.isNetworkMode()) {
            nc.send("CREATE_ROOM", Map.of("gameType", gameType));
            // ROOM_CREATED handler in setPlatformCore() will update the UI
            return;
        }
        String playerId = platformCore.getCurrentUser();
        Room room = platformCore.getRoomManager().createRoom(playerId, gameType, 2);
        if (room != null) {
            currentRoom = room;
            showCurrentRoom(room);
            refreshRooms();
        }
    }

    @FXML
    private void handleJoinQueue(ActionEvent event) {
        NetworkClient nc = NetworkClient.getInstance();
        if (!nc.isNetworkMode()) {
            // Local mode: join queue now (network mode defers JOIN_QUEUE to QueuePage.setGameType
            // so the MATCH_FOUND handler is registered BEFORE the frame can arrive).
            platformCore.joinQueue(platformCore.getCurrentUser(), gameType);
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/QueuePage.fxml"));
            Parent root = loader.load();
            QueuePageController ctrl = loader.getController();
            ctrl.setPlatformCore(platformCore);
            ctrl.setGameType(gameType);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLeaveQueue(ActionEvent ignoredEvent) {
        // kept for FXML compat — leave queue is handled by QueuePageController
    }

    private void handleJoinRoom(String roomId) {
        String playerId = platformCore.getCurrentUser();
        PlatformError error = platformCore.joinRoom(roomId, playerId);
        if (error == null) {
            Room room = platformCore.getRoomManager().getAvailableRooms(gameType).stream()
                    .filter(r -> r.getRoomId().equals(roomId))
                    .findFirst().orElse(null);
            if (room != null) {
                currentRoom = room;
                showCurrentRoom(room);
            }
            refreshRooms();
        }
    }

    private void handleJoinRoomNetwork(String roomId) {
        NetworkClient nc = NetworkClient.getInstance();
        networkRoomId = roomId;
        networkRoomPlayerCount = 2; // we're joining so it's now 2/2
        networkRoomFull = true;
        nc.send("JOIN_ROOM", Map.of("roomId", roomId));
        // ROOM_JOINED handler in setPlatformCore() will update the UI
    }

    @FXML
    private void handleCancelRoom(ActionEvent ignoredEvent) {
        if (NetworkClient.getInstance().isNetworkMode()) {
            if (networkRoomId != null) {
                NetworkClient.getInstance().send("LEAVE_ROOM", Map.of("roomId", networkRoomId));
            }
            networkRoomId = null;
            networkRoomFull = false;
            networkRoomPlayerCount = 0;
            roomStatusBanner.setVisible(false);
            roomStatusBanner.setManaged(false);
            createRoomBtn.setDisable(false);
            refreshRoomsFromServer();
            return;
        }
        if (currentRoom == null) return;
        platformCore.getRoomManager().leaveRoom(currentRoom.getRoomId(), platformCore.getCurrentUser());
        currentRoom = null;
        roomStatusBanner.setVisible(false);
        roomStatusBanner.setManaged(false);
        createRoomBtn.setDisable(false);
        refreshRooms();
    }

    @FXML
    private void handleStartGame(ActionEvent event) {
        NetworkClient nc = NetworkClient.getInstance();
        if (nc.isNetworkMode()) {
            if (networkRoomId == null || !networkRoomFull) return;
            nc.send("START_GAME", Map.of("roomId", networkRoomId, "gameType", gameType));
            // GAME_STARTED handler will navigate; registered in setPlatformCore()
            return;
        }
        if (currentRoom == null) return;
        if (!currentRoom.isFull()) return;

        platformCore.getNotificationService().setListener(null);

        // Capture room players before we remove the room
        List<String> roomPlayers = new ArrayList<>(currentRoom.getPlayers());
        List<String> players = new ArrayList<>(roomPlayers);

        String sessionId = platformCore.getSessionManager()
                .startSession(currentRoom.getRoomId(), gameType, players);

        if (sessionId == null) return;

        // Remove the room immediately — its lifecycle is the lobby, not the game.
        // Only remove players who are actually in the room (not the dummy "Player2").
        for (String pid : roomPlayers) {
            platformCore.getRoomManager().leaveRoom(currentRoom.getRoomId(), pid);
        }
        currentRoom = null;

        try {
            String fxmlPath = gameType.equalsIgnoreCase("ConnectFour")
                    ? "/fxml/ConnectFourGamePage.fxml"
                    : gameType.equalsIgnoreCase("Checkers")
                    ? "/fxml/CheckersGamePage.fxml"
                    : "/fxml/TicTacToeGamePage.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            if (gameType.equalsIgnoreCase("ConnectFour")) {
                ConnectFourGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
            } else if (gameType.equalsIgnoreCase("Checkers")) {
                CheckersGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
            } else {
                TicTacToeGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handlePlayBot(ActionEvent event) {
        platformCore.getNotificationService().setListener(null);

        String me = platformCore.getCurrentUser();
        List<String> players = new ArrayList<>(List.of(me, DinoBot.BOT_USERNAME));

        // Create a transient room to satisfy the session handoff contract
        Room room = platformCore.getRoomManager().createRoom(me, gameType, 2);
        if (room == null) return;
        platformCore.getRoomManager().joinRoom(room.getRoomId(), DinoBot.BOT_USERNAME);

        String sessionId = platformCore.getSessionManager()
                .startSession(room.getRoomId(), gameType, players);
        if (sessionId == null) {
            platformCore.getRoomManager().leaveRoom(room.getRoomId(), me);
            platformCore.getRoomManager().leaveRoom(room.getRoomId(), DinoBot.BOT_USERNAME);
            return;
        }

        // Remove the room — its lifecycle ends at game start
        platformCore.getRoomManager().leaveRoom(room.getRoomId(), me);
        platformCore.getRoomManager().leaveRoom(room.getRoomId(), DinoBot.BOT_USERNAME);

        try {
            String fxmlPath = gameType.equalsIgnoreCase("ConnectFour")
                    ? "/fxml/ConnectFourGamePage.fxml"
                    : gameType.equalsIgnoreCase("Checkers")
                    ? "/fxml/CheckersGamePage.fxml"
                    : "/fxml/TicTacToeGamePage.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            if (gameType.equalsIgnoreCase("ConnectFour")) {
                ConnectFourGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
                ctrl.setBotGame(DinoBot.BOT_USERNAME);
            } else if (gameType.equalsIgnoreCase("Checkers")) {
                CheckersGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
                ctrl.setBotGame(DinoBot.BOT_USERNAME);
            } else {
                TicTacToeGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
                ctrl.setBotGame(DinoBot.BOT_USERNAME);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToDashboard(ActionEvent event) {
        if (inQueue) platformCore.leaveQueue(platformCore.getCurrentUser());
        platformCore.getNotificationService().setListener(null);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DashboardPage.fxml"));
            Parent root = loader.load();
            DashboardPageController controller = loader.getController();
            controller.setPlatformCore(platformCore);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void showCurrentRoom(Room room) {
        boolean full = room.isFull();
        String status = full ? "Room full — ready to start!" : "Waiting for players to join…";
        currentRoomLabel.setText(
                room.getRoomId().substring(0, 8) + "…  ·  " +
                room.getPlayerCount() + "/" + room.getMaxPlayers() + " players  ·  " + status);
        roomStatusBanner.setVisible(true);
        roomStatusBanner.setManaged(true);
        createRoomBtn.setDisable(true);
        startGameBtn.setDisable(!full);
        if (full) {
            startGameBtn.setStyle("-fx-background-color: linear-gradient(to right, #40d090, #20b070); " +
                    "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; " +
                    "-fx-background-radius: 8; -fx-cursor: hand;");
        } else {
            startGameBtn.setStyle("-fx-background-color: rgba(30,50,80,0.5); -fx-text-fill: #3d6080; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: default;");
        }
    }

    private String formatGameName(String raw) {
        if (raw == null) return "Game";
        return switch (raw.toLowerCase()) {
            case "tictactoe"   -> "Tic-Tac-Toe";
            case "connectfour" -> "Connect Four";
            case "checkers"    -> "Checkers";
            default            -> raw;
        };
    }
}
