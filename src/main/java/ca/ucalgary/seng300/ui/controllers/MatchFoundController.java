package ca.ucalgary.seng300.ui.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class MatchFoundController implements Initializable {

    // FXML injections
    @FXML private Label gameBadgeLabel;
    @FXML private HBox playersRow;
    @FXML private Label countdownLabel;
    @FXML private ProgressBar countdownBar;
    @FXML private Button acceptButton;
    @FXML private Button declineButton;

    // Config
    private static final int TOTAL_SECONDS    = 15;
    private static final int URGENT_THRESHOLD = 5;

    // State
    private int     timeLeft = TOTAL_SECONDS;
    private boolean accepted = false;
    private Timeline countdownTimeline;
    private String gameName = "Connect Four";

    private final List<PlayerData> players     = new ArrayList<>();
    private final List<VBox>       playerCards = new ArrayList<>();

    // Callbacks
    private Runnable onAccepted;
    private Runnable onDeclined;
    private Runnable onTimeout;

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Placeholder players, replace with actual players that are matchmaking when matchmaking feature is implemented
        players.add(new PlayerData("DragonX",  "DX", true));
        players.add(new PlayerData("NovaStar", "NS", false));
        players.add(new PlayerData("You",      "YO", false));
        players.add(new PlayerData("BlazeFox", "BF", false));

        gameBadgeLabel.setText(gameName);
        renderPlayers();
        startCountdown();
    }

    /**
     * Call this after FXMLLoader.load() to inject real match data.
     *
     * @param gameName display name of the game, e.g. "Checkers"
     * @param players  ordered list: first half vs second half split at index n/2
     */
    public void initMatch(String gameName, List<PlayerData> players) {
        this.gameName = gameName;
        this.players.clear();
        this.players.addAll(players);
        if (gameBadgeLabel != null) {
            gameBadgeLabel.setText(gameName);
            renderPlayers();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Player card rendering
    // ─────────────────────────────────────────────────────────────────────────

    private void renderPlayers() {
        playersRow.getChildren().clear();
        playerCards.clear();

        int mid = players.size() / 2;

        for (int i = 0; i < players.size(); i++) {
            if (i == mid && players.size() > 1) {
                Label vs = new Label("VS");
                vs.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: rgba(120,160,220,0.45); -fx-padding: 0 4 12 4;");
                playersRow.getChildren().add(vs);
            }
            VBox card = buildPlayerCard(players.get(i));
            playerCards.add(card);
            playersRow.getChildren().add(card);
        }
    }

    private VBox buildPlayerCard(PlayerData player) {
        // Avatar circle background
        Circle avatarCircle = new Circle(26);
        avatarCircle.setStyle(buildAvatarCircleStyle(player.ready));

        // Initials label
        Label initials = new Label(player.initials);
        initials.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7ec8ff;");

        // Avatar stack
        StackPane avatarStack = new StackPane(avatarCircle, initials);
        avatarStack.setPrefSize(52, 52);
        avatarStack.setMaxSize(52, 52);

        // Status dot
        Circle dot = new Circle(5);
        dot.setStyle(player.ready
                ? "-fx-fill: #22c55e; -fx-stroke: #0a1932; -fx-stroke-width: 2;"
                : "-fx-fill: #f59e0b; -fx-stroke: #0a1932; -fx-stroke-width: 2;");

        StackPane dotWrap = new StackPane(avatarStack, dot);
        dotWrap.setPrefSize(52, 52);
        dotWrap.setMaxSize(52, 52);
        StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);

        // Name
        Label name = new Label(player.name);
        name.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: rgba(200,220,255,0.85);");
        name.setMaxWidth(72);

        // Ready / Waiting tag
        Label tag = new Label(player.ready ? "Ready" : "Waiting");
        tag.setStyle(buildTagStyle(player.ready));

        // Card VBox
        VBox card = new VBox(6, dotWrap, name, tag);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(72);
        card.setStyle(buildCardStyle(player.ready));

        // Store refs for live updates
        player.cardRef      = card;
        player.tagRef       = tag;
        player.dotRef       = dot;
        player.avatarRef    = avatarCircle;

        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Countdown
    // ─────────────────────────────────────────────────────────────────────────

    private void startCountdown() {
        countdownLabel.setText(String.valueOf(TOTAL_SECONDS));
        countdownBar.setProgress(1.0);
        countdownBar.setStyle("-fx-pref-height: 6px; -fx-background-radius: 4; -fx-border-radius: 4; -fx-accent: #60a5fa;");

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        countdownTimeline.setCycleCount(TOTAL_SECONDS);
        countdownTimeline.setOnFinished(e -> handleTimeout());
        countdownTimeline.play();
    }

    private void tick() {
        timeLeft--;
        countdownLabel.setText(String.valueOf(timeLeft));
        countdownBar.setProgress((double) timeLeft / TOTAL_SECONDS);

        if (timeLeft <= URGENT_THRESHOLD) {
            countdownLabel.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: #f87171;");
            countdownBar.setStyle("-fx-pref-height: 6px; -fx-background-radius: 4; -fx-border-radius: 4; -fx-accent: #f87171;");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button handlers
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleAccept() {
        if (accepted) return;
        accepted = true;

        acceptButton.setText("Accepted!");
        acceptButton.setStyle("-fx-background-color: linear-gradient(to right, #15803d, #22c55e); -fx-text-fill: white; -fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 10; -fx-border-radius: 10; -fx-cursor: hand;");
        acceptButton.setDisable(true);

        int localIdx = findLocalPlayerIndex();
        if (localIdx >= 0) setPlayerReady(localIdx, true);

        if (onAccepted != null) onAccepted.run();
    }

    @FXML
    private void handleDecline() {
        stopCountdown();
        countdownLabel.setText("✕");
        countdownLabel.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #f87171;");
        countdownBar.setProgress(0);
        acceptButton.setDisable(true);
        acceptButton.setOpacity(0.35);
        declineButton.setDisable(true);

        if (onDeclined != null) onDeclined.run();
    }

    private void handleTimeout() {
        Platform.runLater(() -> {
            countdownLabel.setText("0");
            countdownBar.setProgress(0);
            acceptButton.setDisable(true);
            acceptButton.setOpacity(0.4);
            declineButton.setDisable(true);

            if (onTimeout != null) onTimeout.run();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — call from matchmaking / network layer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mark a player as ready or waiting and refresh their card.
     * Thread-safe — can be called from any thread.
     */
    public void setPlayerReady(int index, boolean ready) {
        if (index < 0 || index >= players.size()) return;
        Platform.runLater(() -> {
            PlayerData p = players.get(index);
            p.ready = ready;
            if (p.cardRef   != null) p.cardRef.setStyle(buildCardStyle(ready));
            if (p.tagRef    != null) { p.tagRef.setText(ready ? "Ready" : "Waiting"); p.tagRef.setStyle(buildTagStyle(ready)); }
            if (p.dotRef    != null) p.dotRef.setStyle(ready ? "-fx-fill: #22c55e; -fx-stroke: #0a1932; -fx-stroke-width: 2;" : "-fx-fill: #f59e0b; -fx-stroke: #0a1932; -fx-stroke-width: 2;");
            if (p.avatarRef != null) p.avatarRef.setStyle(buildAvatarCircleStyle(ready));
        });
    }

    public void stopCountdown() {
        if (countdownTimeline != null) countdownTimeline.stop();
    }

    /**
     * Called after the local player has accepted and we are waiting for the opponent.
     * Stops the countdown and disables both buttons so the player can't change their mind.
     */
    public void showWaitingState() {
        stopCountdown();
        countdownLabel.setText("…");
        countdownLabel.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #60a5fa;");
        countdownBar.setProgress(0);
        declineButton.setDisable(true);
        declineButton.setOpacity(0.35);
    }

    // Callback setters
    public void setOnAccepted(Runnable r) { this.onAccepted = r; }
    public void setOnDeclined(Runnable r) { this.onDeclined = r; }
    public void setOnTimeout(Runnable r)  { this.onTimeout  = r; }

    // ─────────────────────────────────────────────────────────────────────────
    // Style helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildCardStyle(boolean ready) {
        return ready
                ? "-fx-background-color: rgba(34,197,94,0.07); -fx-border-color: rgba(34,197,94,0.35); -fx-border-width: 1.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 6 8 6;"
                : "-fx-background-color: rgba(245,158,11,0.07); -fx-border-color: rgba(245,158,11,0.3); -fx-border-width: 1.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 6 8 6;";
    }

    private String buildTagStyle(boolean ready) {
        return ready
                ? "-fx-font-family: 'Segoe UI'; -fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #4ade80; -fx-background-color: rgba(34,197,94,0.15); -fx-border-color: rgba(34,197,94,0.35); -fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 1 7 1 7;"
                : "-fx-font-family: 'Segoe UI'; -fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #fbbf24; -fx-background-color: rgba(245,158,11,0.15); -fx-border-color: rgba(245,158,11,0.3); -fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 1 7 1 7;";
    }

    private String buildAvatarCircleStyle(boolean ready) {
        return ready
                ? "-fx-fill: #1a3060; -fx-stroke: #22c55e; -fx-stroke-width: 2.5; -fx-effect: dropshadow(gaussian, rgba(34,197,94,0.3), 8, 0.4, 0, 0);"
                : "-fx-fill: #1a3060; -fx-stroke: #f59e0b; -fx-stroke-width: 2.5;";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int findLocalPlayerIndex() {
        for (int i = 0; i < players.size(); i++) {
            if ("You".equals(players.get(i).name)) return i;
        }
        return 2; // fallback: assume index 2 is local player
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data class
    // ─────────────────────────────────────────────────────────────────────────

    public static class PlayerData {
        public String name;
        public String initials;
        public boolean ready;

        // Internal refs populated during card build
        VBox   cardRef;
        Label  tagRef;
        Circle dotRef;
        Circle avatarRef;

        public PlayerData(String name, String initials, boolean ready) {
            this.name     = name;
            this.initials = initials;
            this.ready    = ready;
        }
    }
}
