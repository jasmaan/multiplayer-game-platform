package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.client.NetworkClient;
import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.notification.NotificationService;
import ca.ucalgary.seng300.ui.controllers.MatchFoundController.PlayerData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QueuePageController implements NotificationService.NotificationListener {

    private PlatformCore platformCore;
    private String gameType;

    @FXML private Label gameNameLabel;
    @FXML private Label statusLabel;

    public void setPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
        NetworkClient nc = NetworkClient.getInstance();
        if (nc.isNetworkMode()) {
            // Network mode: listen for MATCH_FOUND pushed from the server over WebSocket.
            nc.getDispatcher().on("MATCH_FOUND", this::handleNetworkMatchFound);
        } else {
            platformCore.getNotificationService().setListener(this);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleNetworkMatchFound(Map<String, Object> payload) {
        String evGameType    = (String) payload.get("gameType");
        String matchId       = (String) payload.get("matchId");
        List<String> players = (List<String>) payload.get("players");
        if (evGameType == null || matchId == null || players == null) return;

        // Show the match-found modal; navigation only happens after MATCH_CONFIRMED
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MatchFound.fxml"));
            Parent root = loader.load();
            MatchFoundController ctrl = loader.getController();

            List<PlayerData> pds = new ArrayList<>();
            for (String id : players) {
                String initials = id.length() >= 2 ? id.substring(0, 2).toUpperCase() : id.toUpperCase();
                pds.add(new PlayerData(id, initials, false));
            }
            ctrl.initMatch(formatGameName(evGameType), pds);

            javafx.stage.Stage matchStage = new javafx.stage.Stage();
            matchStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            matchStage.setTitle("Match Found!");
            matchStage.setScene(new Scene(root));
            matchStage.setResizable(false);

            NetworkClient nc = NetworkClient.getInstance();

            // Register MATCH_DECLINED immediately — any player's decline should close this modal,
            // even if the local player hasn't decided yet. Keep MATCH_FOUND alive so the next
            // match offer is still received (server re-queues all players on decline).
            nc.getDispatcher().on("MATCH_DECLINED", dp -> javafx.application.Platform.runLater(() -> {
                nc.getDispatcher().off("MATCH_CONFIRMED");
                nc.getDispatcher().off("MATCH_DECLINED");
                ctrl.stopCountdown();
                matchStage.close();
                // MATCH_FOUND stays registered — server already re-queued us
            }));

            ctrl.setOnAccepted(() -> javafx.application.Platform.runLater(() -> {
                // Signal acceptance to server and wait — don't navigate yet
                ctrl.showWaitingState();
                nc.send("ACCEPT_MATCH", Map.of("matchId", matchId));

                // Both players accepted → server sends MATCH_CONFIRMED with the real sessionId
                nc.getDispatcher().on("MATCH_CONFIRMED", confirmedPayload -> javafx.application.Platform.runLater(() -> {
                    nc.getDispatcher().off("MATCH_CONFIRMED");
                    nc.getDispatcher().off("MATCH_DECLINED");
                    nc.getDispatcher().off("MATCH_FOUND");
                    String sessionId = (String) confirmedPayload.get("sessionId");
                    List<?> confirmedPlayers = (List<?>) confirmedPayload.get("players");
                    List<String> finalPlayers = confirmedPlayers != null
                            ? confirmedPlayers.stream().map(Object::toString).toList()
                            : players;
                    ctrl.stopCountdown();
                    matchStage.close();
                    navigateToNetworkGame(sessionId, evGameType, finalPlayers);
                }));
                // MATCH_DECLINED is already registered above
            }));

            ctrl.setOnDeclined(() -> javafx.application.Platform.runLater(() -> {
                // Local player declined — clean up this match's handlers then let the server
                // push MATCH_DECLINED back to us (which will be ignored since we already cleaned up)
                nc.getDispatcher().off("MATCH_CONFIRMED");
                nc.getDispatcher().off("MATCH_DECLINED");
                nc.send("DECLINE_MATCH", Map.of("matchId", matchId));
                matchStage.close();
                // MATCH_FOUND stays registered — server re-queues us
            }));
            ctrl.setOnTimeout(() -> javafx.application.Platform.runLater(() -> {
                nc.getDispatcher().off("MATCH_CONFIRMED");
                nc.getDispatcher().off("MATCH_DECLINED");
                nc.send("DECLINE_MATCH", Map.of("matchId", matchId));
                matchStage.close();
                // MATCH_FOUND stays registered — server re-queues us
            }));

            matchStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void navigateToNetworkGame(String sessionId, String gameType, List<String> players) {
        String myId = NetworkClient.getInstance().getPlayerId();
        try {
            String fxmlPath = gameType.equalsIgnoreCase("ConnectFour")
                    ? "/fxml/ConnectFourGamePage.fxml"
                    : gameType.equalsIgnoreCase("Checkers")
                    ? "/fxml/CheckersGamePage.fxml"
                    : "/fxml/TicTacToeGamePage.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            NetworkClient nc = NetworkClient.getInstance();
            if (gameType.equalsIgnoreCase("ConnectFour")) {
                ConnectFourGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
                ctrl.enableNetworkMode(myId, nc);
            } else if (gameType.equalsIgnoreCase("Checkers")) {
                CheckersGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
                ctrl.enableNetworkMode(myId, nc);
            } else {
                TicTacToeGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, sessionId, players);
                ctrl.enableNetworkMode(myId, nc);
            }

            Stage stage = (Stage) gameNameLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNotification(String playerId, String message) {
        // Queue page does not display generic notifications.
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

            String evGameType = event.getGameType();
            List<PlayerData> players = new ArrayList<>();
            for (String id : event.getPlayers()) {
                String initials = id.length() >= 2 ? id.substring(0, 2).toUpperCase() : id.toUpperCase();
                players.add(new PlayerData(id, initials, false));
            }
            ctrl.initMatch(formatGameName(evGameType), players);

            Stage matchStage = new Stage();
            matchStage.initModality(Modality.APPLICATION_MODAL);
            matchStage.setTitle("Match Found!");
            matchStage.setScene(new Scene(root));
            matchStage.setResizable(false);

            ctrl.setOnAccepted(() -> javafx.application.Platform.runLater(() -> {
                ctrl.stopCountdown();
                matchStage.close();
                navigateToGame(event, evGameType);
            }));
            ctrl.setOnDeclined(() -> javafx.application.Platform.runLater(matchStage::close));
            ctrl.setOnTimeout(() -> javafx.application.Platform.runLater(matchStage::close));

            matchStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void navigateToGame(MatchFoundEvent event, String gameType) {
        platformCore.getNotificationService().setListener(null);
        try {
            List<String> players = event.getPlayers();
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

            Stage stage = (Stage) gameNameLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
        gameNameLabel.setText(formatGameName(gameType));
        // Network mode: send JOIN_QUEUE HERE, after setPlatformCore() already registered the
        // MATCH_FOUND handler — guaranteeing we can't miss the server's instant response.
        NetworkClient nc = NetworkClient.getInstance();
        if (nc.isNetworkMode()) {
            nc.send("JOIN_QUEUE", Map.of("gameType", gameType));
        }
    }

    @FXML
    private void handleCancelQueue(ActionEvent event) {
        NetworkClient nc = NetworkClient.getInstance();
        if (nc.isNetworkMode()) {
            nc.send("LEAVE_QUEUE");
            nc.getDispatcher().off("MATCH_FOUND");
        } else {
            if (platformCore != null) platformCore.leaveQueue(platformCore.getCurrentUser());
            platformCore.getNotificationService().setListener(null);
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GameRoomPage.fxml"));
            Parent root = loader.load();
            GameRoomPageController controller = loader.getController();
            controller.setPlatformCore(platformCore);
            controller.setGameType(gameType);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatGameName(String raw) {
        if (raw == null) return "Matchmaking";
        return switch (raw.toLowerCase()) {
            case "tictactoe"   -> "Tic-Tac-Toe";
            case "connectfour" -> "Connect Four";
            case "checkers"    -> "Checkers";
            default            -> raw;
        };
    }
}