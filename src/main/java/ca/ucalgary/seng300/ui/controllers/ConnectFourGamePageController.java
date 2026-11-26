package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.client.NetworkClient;
import ca.ucalgary.seng300.core.PlayerProfile;
import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.games.BoardSerializer;
import ca.ucalgary.seng300.events.ChatMessageEvent;
import ca.ucalgary.seng300.events.GameStateUpdate;
import ca.ucalgary.seng300.gameai.DinoBot;
import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.ConnectFourMove;
import ca.ucalgary.seng300.games.Piece;
import ca.ucalgary.seng300.session.MoveSubmissionResult;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConnectFourGamePageController {

    // ── FXML nodes ────────────────────────────────────────────────
    @FXML private Label      sessionIdLabel;
    @FXML private VBox       player1Card;
    @FXML private VBox       player2Card;
    @FXML private Label      player1Label;
    @FXML private Label      player2Label;
    @FXML private Label      player1TurnLabel;
    @FXML private Label      player2TurnLabel;
    @FXML private Label      turnStatusLabel;
    @FXML private Label      moveCountLabel;
    @FXML private StackPane  boardContainer;
    @FXML private StackPane  forfeitOverlay;
    @FXML private StackPane  gameOverOverlay;
    @FXML private VBox       gameOverCard;
    @FXML private StackPane  playAgainWaitOverlay;
    @FXML private Label      waitingMessageLabel;
    @FXML private HBox       forfeitToast;
    @FXML private Label      forfeitToastLabel;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox       chatMessagesBox;
    @FXML private TextField  chatInputField;

    // ── Session state ─────────────────────────────────────────────
    private PlatformCore platformCore;
    private String sessionId;
    private String player1Id;
    private String player2Id;
    private boolean gameFinished = false;
    private int moveCount = 0;

    // ── Board ─────────────────────────────────────────────────────
    private static final int ROWS = 6;
    private static final int COLS = 7;
    private final Circle[][] boardCircles = new Circle[ROWS][COLS];
    private final Button[]   colButtons   = new Button[COLS];

    // ── Pending navigation (forfeit path) ─────────────────────────
    private ActionEvent pendingNavEvent;

    // ── Bot game ──────────────────────────────────────────────────
    private boolean isBotGame   = false;
    private String  botPlayerId = null;
    private Board   currentBoard;           // updated after every move

    // ── Network mode ──────────────────────────────────────────────
    private boolean       networkMode     = false;
    private String        networkPlayerId = null;
    private String        networkActiveId = null;
    private NetworkClient networkClient   = null;

    // ── MMR constants ─────────────────────────────────────────────
    private static final int WIN_MMR  =  25;
    private static final int DRAW_MMR =   5;
    private static final int LOSS_MMR = -15;

    // ── Card styles ───────────────────────────────────────────────
    private static final String P1_CARD_ACTIVE =
            "-fx-background-color: rgba(255,68,68,0.18); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(255,68,68,0.6); -fx-border-width: 1.5; -fx-border-radius: 12; -fx-padding: 12 14;";
    private static final String P1_CARD_INACTIVE =
            "-fx-background-color: rgba(255,68,68,0.06); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(255,68,68,0.2); -fx-border-width: 1; -fx-border-radius: 12; -fx-padding: 12 14;";
    private static final String P2_CARD_ACTIVE =
            "-fx-background-color: rgba(77,166,255,0.18); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(77,166,255,0.6); -fx-border-width: 1.5; -fx-border-radius: 12; -fx-padding: 12 14;";
    private static final String P2_CARD_INACTIVE =
            "-fx-background-color: rgba(10,25,45,0.8); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(77,166,255,0.15); -fx-border-width: 1; -fx-border-radius: 12; -fx-padding: 12 14;";

    // ─────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {}

    public void setSessionData(PlatformCore platformCore, String sessionId, List<String> players) {
        this.platformCore = platformCore;
        this.sessionId    = sessionId;
        this.player1Id    = players.get(0);
        this.player2Id    = players.get(1);

        sessionIdLabel.setText("Session: " + sessionId.substring(0, 8) + "…");
        player1Label.setText(player1Id);
        player2Label.setText(player2Id);

        buildBoard();
        updateTurnIndicator(player1Id);

        platformCore.setChatMessageCallback(sessionId, this::onChatMessage);
        platformCore.setForfeitWinCallback(this::showForfeitWinToast);
    }

    // ─────────────────────────────────────────────────────────────
    // Board building
    // ─────────────────────────────────────────────────────────────

    private void buildBoard() {
        var session = platformCore.getSessionManager().getSession(sessionId);
        currentBoard = session != null
                ? (Board) session.getGameState().getBoard()
                : new Board(6, 7); // empty initial board for network mode
        VBox layout = new VBox(4);
        layout.setAlignment(Pos.CENTER);

        // Column drop buttons
        HBox colBtnRow = new HBox(4);
        colBtnRow.setAlignment(Pos.CENTER);
        for (int c = 0; c < COLS; c++) {
            Button btn = new Button("▼");
            btn.setPrefWidth(54);
            btn.setPrefHeight(28);
            btn.setStyle(colButtonStyle("#ff6464", "rgba(255,68,68,0.18)", "rgba(255,68,68,0.35)"));
            final int col = c;
            btn.setOnAction(e -> handleColumnClick(col));
            colButtons[c] = btn;
            colBtnRow.getChildren().add(btn);
        }
        layout.getChildren().add(colBtnRow);

        // Board panel
        VBox gridPanel = new VBox(4);
        gridPanel.setAlignment(Pos.CENTER);
        gridPanel.setStyle(
                "-fx-background-color: rgba(15,40,90,0.92); -fx-background-radius: 10; " +
                "-fx-border-color: rgba(77,166,255,0.18); -fx-border-width: 1.5; -fx-border-radius: 10; " +
                "-fx-padding: 10;");

        for (int r = 0; r < ROWS; r++) {
            HBox rowBox = new HBox(4);
            rowBox.setAlignment(Pos.CENTER);
            for (int c = 0; c < COLS; c++) {
                Circle cell = new Circle(24);
                cell.setFill(Color.web("#0b1f3a"));
                cell.setStroke(Color.web("#1e3a6a"));
                cell.setStrokeWidth(1.5);
                boardCircles[r][c] = cell;

                StackPane slot = new StackPane(cell);
                slot.setPrefSize(54, 54);
                rowBox.getChildren().add(slot);
            }
            gridPanel.getChildren().add(rowBox);
        }
        layout.getChildren().add(gridPanel);
        boardContainer.getChildren().add(layout);
    }

    private String colButtonStyle(String textColor, String bgColor, String borderColor) {
        return "-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor + "; " +
               "-fx-font-size: 11px; -fx-background-radius: 8; -fx-border-color: " + borderColor + "; " +
               "-fx-border-width: 1; -fx-border-radius: 8; -fx-cursor: hand;";
    }

    // ─────────────────────────────────────────────────────────────
    // Move handling
    // ─────────────────────────────────────────────────────────────

    private void handleColumnClick(int col) {
        if (gameFinished) return;

        if (networkMode) {
            if (!networkPlayerId.equals(networkActiveId)) return;
            Map<String, Object> moveData = Map.of("player", networkPlayerId, "column", col);
            Map<String, Object> payload  = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("gameType",  "ConnectFour");
            payload.put("moveId",    UUID.randomUUID().toString());
            payload.put("moveData",  moveData);
            networkClient.send("SUBMIT_MOVE", payload);
            return;
        }

        String activeId = platformCore.getSessionManager()
                .getSession(sessionId).getGameState().getActivePlayerId();

        ConnectFourMove move = new ConnectFourMove(activeId, col);
        MoveSubmissionResult result = platformCore.submitMove(sessionId, activeId, move);

        if (result == null || !result.isAccepted()) {
            flashColumnRejected(col);
            return;
        }

        moveCount++;
        GameStateUpdate update = result.getUpdate();
        renderBoard((Board) update.getPayload());

        if (update.isGameOver()) {
            gameFinished = true;
            setColumnButtonsDisabled(true);
            showGameOver(update.getWinnerId());
        } else {
            updateTurnIndicator(update.getActivePlayerId());
            if (isBotGame && update.getActivePlayerId().equals(botPlayerId)) {
                scheduleBotMove();
            }
        }
    }

    // ── Network mode ──────────────────────────────────────────────────────────

    public void enableNetworkMode(String myPlayerId, NetworkClient nc) {
        this.networkMode     = true;
        this.networkPlayerId = myPlayerId;
        this.networkClient   = nc;
        this.networkActiveId = player1Id;
        nc.getDispatcher().on("GAME_STATE",   this::applyServerState);
        nc.getDispatcher().on("CHAT_MESSAGE", payload -> {
            String from = (String) payload.get("from");
            String text = (String) payload.get("text");
            if (from != null && text != null) {
                onChatMessage(new ca.ucalgary.seng300.events.ChatMessageEvent(sessionId, from, text));
            }
        });
        nc.getDispatcher().on("GAME_STARTED",         this::handleNetworkRematch);
        nc.getDispatcher().on("OPPONENT_ENDED_MATCH", p -> javafx.application.Platform.runLater(() -> {
            nc.getDispatcher().off("OPPONENT_ENDED_MATCH");
            playAgainWaitOverlay.setVisible(false);
            playAgainWaitOverlay.setManaged(false);
            showForfeitWinToast("Opponent ended the match");
            new Timeline(new KeyFrame(Duration.seconds(2.5), e -> navigateToGameRoomNoEvent())).play();
        }));
        nc.getDispatcher().on("PLAYER_FORFEITED", p -> javafx.application.Platform.runLater(() -> {
            String forfeitedBy = (String) p.get("forfeitedBy");
            String winnerId    = (String) p.get("winnerId");
            gameFinished = true;
            nc.getDispatcher().off("GAME_STATE");
            setColumnButtonsDisabled(true);
            showGameOver(winnerId);
            if (networkPlayerId.equals(winnerId)) {
                showForfeitWinToast(forfeitedBy + " forfeited the match — You Win!");
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private void applyServerState(Map<String, Object> payload) {
        Object rawBoard = payload.get("board");
        if (rawBoard instanceof List<?> rows) {
            Board board = BoardSerializer.deserializeFromList(rows);
            currentBoard = board;
            renderBoard(board);
        }
        networkActiveId = (String) payload.get("activePlayer");
        moveCount++;
        boolean gameOver = Boolean.TRUE.equals(payload.get("gameOver"));
        if (gameOver) {
            gameFinished = true;
            setColumnButtonsDisabled(true);
            networkClient.getDispatcher().off("GAME_STATE");
            showGameOver((String) payload.get("winnerId"));
        } else {
            updateTurnIndicator(networkActiveId);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Bot support
    // ─────────────────────────────────────────────────────────────

    /**
     * Call this after setSessionData to enable autonomous bot moves.
     * If the bot is player 1 (Red) it triggers its first move immediately.
     */
    public void setBotGame(String botId) {
        this.isBotGame   = true;
        this.botPlayerId = botId;
        if (botId.equals(player1Id)) {
            scheduleBotMove(); // bot goes first
        }
    }

    private void scheduleBotMove() {
        Board snapshot = currentBoard;
        new Timeline(new KeyFrame(Duration.millis(900), e -> {
            if (gameFinished || snapshot == null) return;
            Piece botPiece = botPlayerId.equals(player1Id) ? Piece.Red  : Piece.Blue;
            Piece oppPiece = botPlayerId.equals(player1Id) ? Piece.Blue : Piece.Red;
            int col = DinoBot.computeConnectFourColumn(snapshot, botPiece, oppPiece);

            ConnectFourMove move = new ConnectFourMove(botPlayerId, col);
            MoveSubmissionResult result = platformCore.submitMove(sessionId, botPlayerId, move);
            if (result == null || !result.isAccepted()) return;

            moveCount++;
            GameStateUpdate update = result.getUpdate();
            renderBoard((Board) update.getPayload());
            if (update.isGameOver()) {
                gameFinished = true;
                setColumnButtonsDisabled(true);
                showGameOver(update.getWinnerId());
            } else {
                updateTurnIndicator(update.getActivePlayerId());
            }
        })).play();
    }

    private void flashColumnRejected(int col) {
        Button btn = colButtons[col];
        String orig = btn.getStyle();
        btn.setStyle(colButtonStyle("#ff6666", "rgba(220,40,40,0.25)", "rgba(220,40,40,0.6)"));
        new Timeline(new KeyFrame(Duration.millis(400), e -> btn.setStyle(orig))).play();
    }

    private void setColumnButtonsDisabled(boolean disabled) {
        for (Button b : colButtons) b.setDisable(disabled);
    }

    // ─────────────────────────────────────────────────────────────
    // Board rendering
    // ─────────────────────────────────────────────────────────────

    private void renderBoard(Board board) {
        currentBoard = board;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Piece piece = board.getPiece(r, c);
                Circle cell = boardCircles[r][c];
                if (piece == Piece.Red) {
                    cell.setFill(Color.web("#e63232"));
                    cell.setStroke(Color.web("#ff6464"));
                } else if (piece == Piece.Blue) {
                    cell.setFill(Color.web("#1e90ff"));
                    cell.setStroke(Color.web("#64b4ff"));
                } else {
                    cell.setFill(Color.web("#0b1f3a"));
                    cell.setStroke(Color.web("#1e3a6a"));
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Turn indicator
    // ─────────────────────────────────────────────────────────────

    private void updateTurnIndicator(String activePlayerId) {
        boolean p1Active = player1Id.equals(activePlayerId);

        player1Card.setStyle(p1Active ? P1_CARD_ACTIVE : P1_CARD_INACTIVE);
        player2Card.setStyle(p1Active ? P2_CARD_INACTIVE : P2_CARD_ACTIVE);

        player1TurnLabel.setText(p1Active ? "▶  Your turn" : "Waiting…");
        player1TurnLabel.setTextFill(Color.web(p1Active ? "#ff4444" : "#3d6080"));

        player2TurnLabel.setText(p1Active ? "Waiting…" : "▶  Your turn");
        player2TurnLabel.setTextFill(Color.web(p1Active ? "#3d6080" : "#4da6ff"));

        turnStatusLabel.setText((p1Active ? "Red" : "Blue") + " to move");
        moveCountLabel.setText("Move " + (moveCount + 1));

        // Tint column buttons to match active player colour
        String btnStyle = p1Active
                ? colButtonStyle("#ff6464", "rgba(255,68,68,0.18)", "rgba(255,68,68,0.35)")
                : colButtonStyle("#4da6ff", "rgba(77,166,255,0.15)", "rgba(77,166,255,0.2)");
        for (Button b : colButtons) b.setStyle(btnStyle);
    }

    // ─────────────────────────────────────────────────────────────
    // Game-over overlay (built programmatically)
    // ─────────────────────────────────────────────────────────────

    public void showGameOver(String winnerId) {
        gameFinished = true;
        gameOverCard.getChildren().clear();
        String me = platformCore.getCurrentUser();

        if (winnerId == null) {
            buildDrawCard();
        } else if (winnerId.equals(me)) {
            buildWinCard(platformCore.getPlayerProfile(me));
        } else {
            buildLossCard(platformCore.getPlayerProfile(winnerId));
        }

        gameOverOverlay.setVisible(true);
        gameOverOverlay.setManaged(true);
    }

    private void buildDrawCard() {
        Label icon = new Label("🤝");
        icon.setStyle("-fx-font-size: 52px; -fx-text-fill: white;");
        VBox.setMargin(icon, new Insets(0, 0, 14, 0));

        Label title = new Label("Match Draw");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        Label pts = pointsLabel(DRAW_MMR);
        VBox.setMargin(pts, new Insets(6, 0, 28, 0));

        gameOverCard.getChildren().addAll(icon, title, pts, buildButtons());
    }

    private void buildWinCard(PlayerProfile profile) {
        Label icon = new Label("🏆");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #f5c518;");
        VBox.setMargin(icon, new Insets(0, 0, 10, 0));

        VBox avatarBlock = buildAvatarBlock(profile, 54, "#f5c518");
        VBox.setMargin(avatarBlock, new Insets(0, 0, 6, 0));

        Label won = new Label("Won!");
        won.setTextFill(Color.web("#f5c518"));
        won.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label pts = pointsLabel(WIN_MMR);
        VBox.setMargin(pts, new Insets(6, 0, 28, 0));

        gameOverCard.getChildren().addAll(icon, avatarBlock, won, pts, buildButtons());
    }

    private void buildLossCard(PlayerProfile winnerProfile) {
        Label icon = new Label("💔");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #ff4444;");
        VBox.setMargin(icon, new Insets(0, 0, 10, 0));

        VBox avatarBlock = buildAvatarBlock(winnerProfile, 54, "#ff8040");
        VBox.setMargin(avatarBlock, new Insets(0, 0, 6, 0));

        Label won = new Label("Won!");
        won.setTextFill(Color.web("#ff8040"));
        won.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label pts = pointsLabel(LOSS_MMR);
        VBox.setMargin(pts, new Insets(6, 0, 28, 0));

        gameOverCard.getChildren().addAll(icon, avatarBlock, won, pts, buildButtons());
    }

    private VBox buildAvatarBlock(PlayerProfile profile, double radius, String borderColor) {
        ImageView av = new ImageView();
        av.setFitWidth(radius * 2);
        av.setFitHeight(radius * 2);
        av.setPreserveRatio(false);
        av.setSmooth(true);

        String avatarPath = (profile != null && profile.getAvatarPath() != null)
                ? profile.getAvatarPath() : "/images/gameDinoLogo.png";
        try {
            var url = getClass().getResource(avatarPath);
            if (url != null) av.setImage(new Image(url.toExternalForm()));
        } catch (Exception ignored) {}

        Circle clip = new Circle(radius, radius, radius);
        av.setClip(clip);

        StackPane avatarPane = new StackPane(av);
        avatarPane.setStyle(
                "-fx-border-color: " + borderColor + "; -fx-border-width: 2.5; " +
                "-fx-border-radius: " + radius + "; -fx-background-radius: " + radius + ";");
        avatarPane.setMaxSize(radius * 2, radius * 2);
        avatarPane.setMinSize(radius * 2, radius * 2);

        String name = profile != null ? profile.getUsername() : "Unknown";
        Label nameLabel = new Label(name);
        nameLabel.setTextFill(Color.web("#e6f3ff"));
        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        VBox block = new VBox(6, avatarPane, nameLabel);
        block.setAlignment(Pos.CENTER);
        return block;
    }

    private Label pointsLabel(int delta) {
        String sign = delta >= 0 ? "+" : "";
        Label lbl = new Label("Points achieved: " + sign + delta + " pts");
        lbl.setTextFill(Color.web(delta >= 0 ? "#40d090" : "#ff6666"));
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        return lbl;
    }

    private VBox buildButtons() {
        Button playAgain = new Button("Play Again");
        playAgain.setMaxWidth(Double.MAX_VALUE);
        playAgain.setPrefHeight(42);
        playAgain.setStyle(
                "-fx-background-color: linear-gradient(to right, #4da6ff, #1e90ff); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; " +
                "-fx-background-radius: 10; -fx-cursor: hand;");
        playAgain.setOnAction(this::handlePlayAgain);
        VBox.setMargin(playAgain, new Insets(0, 0, 8, 0));

        Button endMatch = new Button("End Match");
        endMatch.setMaxWidth(Double.MAX_VALUE);
        endMatch.setPrefHeight(42);
        endMatch.setStyle(
                "-fx-background-color: rgba(10,25,45,0.9); -fx-text-fill: #7fbfff; " +
                "-fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 10; " +
                "-fx-border-color: rgba(77,166,255,0.3); -fx-border-width: 1; " +
                "-fx-border-radius: 10; -fx-cursor: hand;");
        endMatch.setOnAction(this::handleEndMatch);

        VBox box = new VBox(0, playAgain, endMatch);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // ─────────────────────────────────────────────────────────────
    // Play-again coordination
    // ─────────────────────────────────────────────────────────────

    private void handlePlayAgain(ActionEvent event) {
        // Bot is always immediately ready — skip coordination
        if (isBotGame) { startNewGame(event); return; }

        if (networkMode) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            networkClient.send("REQUEST_PLAY_AGAIN", payload);
            String other = player1Id.equals(networkPlayerId) ? player2Id : player1Id;
            waitingMessageLabel.setText(other + " is still viewing result…");
            playAgainWaitOverlay.setVisible(true);
            playAgainWaitOverlay.setManaged(true);
            return;
        }

        String me    = platformCore.getCurrentUser();
        String other = player1Id.equals(me) ? player2Id : player1Id;

        boolean bothReady = platformCore.requestPlayAgain(sessionId, me, () ->
                javafx.application.Platform.runLater(() -> {
                    playAgainWaitOverlay.setVisible(false);
                    playAgainWaitOverlay.setManaged(false);
                    navigateToGameRoom(event);
                }));

        if (bothReady) {
            startNewGame(event);
        } else {
            waitingMessageLabel.setText(other + " is still viewing result…");
            playAgainWaitOverlay.setVisible(true);
            playAgainWaitOverlay.setManaged(true);
        }
    }

    @FXML
    private void handleDismissWait(ActionEvent event) {
        if (networkMode) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            networkClient.send("CANCEL_PLAY_AGAIN", payload);
            playAgainWaitOverlay.setVisible(false);
            playAgainWaitOverlay.setManaged(false);
            return;
        }
        platformCore.cancelPlayAgain(sessionId, platformCore.getCurrentUser());
        playAgainWaitOverlay.setVisible(false);
        playAgainWaitOverlay.setManaged(false);
    }

    @SuppressWarnings("unchecked")
    private void handleNetworkRematch(Map<String, Object> payload) {
        javafx.application.Platform.runLater(() -> {
            networkClient.getDispatcher().off("GAME_STARTED");
            networkClient.getDispatcher().off("GAME_STATE");
            networkClient.getDispatcher().off("CHAT_MESSAGE");
            networkClient.getDispatcher().off("OPPONENT_ENDED_MATCH");
            networkClient.getDispatcher().off("PLAYER_FORFEITED");
            String newSessionId = (String) payload.get("sessionId");
            List<String> players = (List<String>) payload.get("players");
            if (newSessionId == null || players == null) return;
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ConnectFourGamePage.fxml"));
                Parent root = loader.load();
                ConnectFourGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, newSessionId, players);
                ctrl.enableNetworkMode(networkPlayerId, networkClient);
                Stage stage = (Stage) boardContainer.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.sizeToScene();
                stage.show();
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    private void startNewGame(ActionEvent event) {
        playAgainWaitOverlay.setVisible(false);
        playAgainWaitOverlay.setManaged(false);
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setManaged(false);

        List<String> players = List.of(player1Id, player2Id);
        String gameType = "ConnectFour";

        // Create a fresh room for the rematch (previous room was cleaned up at game start)
        ca.ucalgary.seng300.room.Room room =
                platformCore.getRoomManager().createRoom(player1Id, gameType, 2);
        if (room == null) { navigateToGameRoom(event); return; }
        platformCore.getRoomManager().joinRoom(room.getRoomId(), player2Id);

        String newSessionId = platformCore.getSessionManager()
                .startSession(room.getRoomId(), gameType, players);

        // Room lifecycle ends at game start — remove both players immediately
        platformCore.getRoomManager().leaveRoom(room.getRoomId(), player1Id);
        platformCore.getRoomManager().leaveRoom(room.getRoomId(), player2Id);

        if (newSessionId == null) { navigateToGameRoom(event); return; }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ConnectFourGamePage.fxml"));
            Parent root = loader.load();
            ConnectFourGamePageController ctrl = loader.getController();
            ctrl.setSessionData(platformCore, newSessionId, players);
            if (isBotGame) ctrl.setBotGame(botPlayerId);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // End match / forfeit navigation
    // ─────────────────────────────────────────────────────────────

    private void handleEndMatch(ActionEvent event) {
        gameFinished = true;
        if (networkMode) {
            Map<String, Object> p = new HashMap<>();
            p.put("sessionId", sessionId);
            networkClient.send("NOTIFY_OPPONENT_ENDED", p);
            closeRoom();
            navigateToGameRoomNoEvent();
            return;
        }
        if (!isBotGame) {
            platformCore.notifyOpponentEndedMatch(sessionId, platformCore.getCurrentUser());
        }
        closeRoom();
        navigateToGameRoom(event);
    }

    @FXML
    private void handleBackToDashboard(ActionEvent event) {
        if (!gameFinished) {
            forfeitOverlay.setVisible(true);
            forfeitOverlay.setManaged(true);
            pendingNavEvent = event;
            return;
        }
        if (networkMode) {
            Map<String, Object> p = new HashMap<>();
            p.put("sessionId", sessionId);
            networkClient.send("NOTIFY_OPPONENT_ENDED", p);
            closeRoom();
            navigateToGameRoomNoEvent();
            return;
        }
        platformCore.notifyOpponentEndedMatch(sessionId, platformCore.getCurrentUser());
        closeRoom();
        navigateToGameRoom(event);
    }

    @FXML
    private void handleConfirmForfeit(ActionEvent ignoredEvent) {
        if (networkMode) {
            gameFinished = true;
            forfeitOverlay.setVisible(false);
            forfeitOverlay.setManaged(false);
            Map<String, Object> p = new HashMap<>();
            p.put("sessionId", sessionId);
            networkClient.send("FORFEIT", p);
            closeRoom();
            navigateToGameRoomNoEvent();
            return;
        }
        platformCore.forfeitGame(sessionId, platformCore.getCurrentUser());
        gameFinished = true;
        forfeitOverlay.setVisible(false);
        forfeitOverlay.setManaged(false);
        closeRoom();
        navigateToGameRoom(pendingNavEvent);
    }

    @FXML
    private void handleCancelForfeit(ActionEvent ignoredEvent) {
        forfeitOverlay.setVisible(false);
        forfeitOverlay.setManaged(false);
    }

    // ─────────────────────────────────────────────────────────────
    // Forfeit win toast
    // ─────────────────────────────────────────────────────────────

    private void showForfeitWinToast(String message) {
        forfeitToastLabel.setText(message);
        forfeitToast.setVisible(true);
        forfeitToast.setManaged(true);
        new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            forfeitToast.setVisible(false);
            forfeitToast.setManaged(false);
        })).play();
    }

    // ─────────────────────────────────────────────────────────────
    // Chat
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleSendChat(ActionEvent ignoredEvent) {
        String text = chatInputField.getText().trim();
        if (text.isEmpty()) return;
        chatInputField.clear();
        if (networkMode) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("text", text);
            networkClient.send("SEND_CHAT", payload);
        } else {
            platformCore.sendChatMessage(sessionId, platformCore.getCurrentUser(), text);
        }
    }

    private void onChatMessage(ChatMessageEvent event) {
        javafx.application.Platform.runLater(() -> {
            boolean isMe = event.getSenderId().equals(platformCore.getCurrentUser());
            Label bubble = new Label((isMe ? "You" : event.getSenderId()) + ": " + event.getText());
            bubble.setWrapText(true);
            bubble.setMaxWidth(Double.MAX_VALUE);
            bubble.setStyle(isMe
                    ? "-fx-text-fill: #4da6ff; -fx-font-size: 11px; " +
                      "-fx-background-color: rgba(77,166,255,0.1); -fx-background-radius: 8; -fx-padding: 5 8;"
                    : "-fx-text-fill: #e6f3ff; -fx-font-size: 11px; " +
                      "-fx-background-color: rgba(10,25,50,0.75); -fx-background-radius: 8; -fx-padding: 5 8;");
            chatMessagesBox.getChildren().add(bubble);
            chatScrollPane.setVvalue(1.0);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void closeRoom() {
        platformCore.setChatMessageCallback(sessionId, null);
        platformCore.setForfeitWinCallback(null);
        platformCore.getRoomManager().getAllRooms("ConnectFour").stream()
                .filter(r -> r.getPlayers().contains(player1Id) && r.getPlayers().contains(player2Id))
                .forEach(r -> {
                    platformCore.getRoomManager().leaveRoom(r.getRoomId(), player1Id);
                    platformCore.getRoomManager().leaveRoom(r.getRoomId(), player2Id);
                });
    }

    private void navigateToGameRoom(ActionEvent event) {
        platformCore.setForfeitWinCallback(null);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GameRoomPage.fxml"));
            Parent root = loader.load();
            GameRoomPageController controller = loader.getController();
            controller.setPlatformCore(platformCore);
            controller.setGameType("ConnectFour");
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void navigateToGameRoomNoEvent() {
        if (networkMode) {
            networkClient.getDispatcher().off("GAME_STATE");
            networkClient.getDispatcher().off("GAME_STARTED");
            networkClient.getDispatcher().off("CHAT_MESSAGE");
            networkClient.getDispatcher().off("OPPONENT_ENDED_MATCH");
            networkClient.getDispatcher().off("PLAYER_FORFEITED");
        }
        platformCore.setForfeitWinCallback(null);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GameRoomPage.fxml"));
            Parent root = loader.load();
            GameRoomPageController controller = loader.getController();
            controller.setPlatformCore(platformCore);
            controller.setGameType("ConnectFour");
            Stage stage = (Stage) boardContainer.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
