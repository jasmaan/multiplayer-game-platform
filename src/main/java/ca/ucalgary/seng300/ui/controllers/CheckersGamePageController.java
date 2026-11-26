package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.client.NetworkClient;
import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.core.PlayerProfile;
import ca.ucalgary.seng300.games.BoardSerializer;
import ca.ucalgary.seng300.events.ChatMessageEvent;
import ca.ucalgary.seng300.events.GameStateUpdate;
import ca.ucalgary.seng300.gameai.DinoBot;
import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.Checkers;
import ca.ucalgary.seng300.games.CheckersMove;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CheckersGamePageController {

    // -- Board and scoring constants -----------------------------------------------------------
    // Shared layout and result values reused across board rendering and end-of-game overlays.
    private static final int BOARD_SIZE = 8;
    private static final double CELL_SIZE = 64.0;
    private static final int WIN_MMR = 25;
    private static final int DRAW_MMR = 5;
    private static final int LOSS_MMR = -15;

    // -- Active/inactive player card styles ----------------------------------------------------
    // Player cards reuse different accent palettes so the active side is obvious at a glance.
    private static final String P1_CARD_ACTIVE =
            "-fx-background-color: rgba(77,166,255,0.18); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(77,166,255,0.6); -fx-border-width: 1.5; -fx-border-radius: 12; -fx-padding: 12 14;";
    private static final String P1_CARD_INACTIVE =
            "-fx-background-color: rgba(77,166,255,0.06); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(77,166,255,0.2); -fx-border-width: 1; -fx-border-radius: 12; -fx-padding: 12 14;";
    private static final String P2_CARD_ACTIVE =
            "-fx-background-color: rgba(255,128,64,0.18); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(255,128,64,0.6); -fx-border-width: 1.5; -fx-border-radius: 12; -fx-padding: 12 14;";
    private static final String P2_CARD_INACTIVE =
            "-fx-background-color: rgba(10,25,45,0.8); -fx-background-radius: 12; " +
            "-fx-border-color: rgba(77,166,255,0.15); -fx-border-width: 1; -fx-border-radius: 12; -fx-padding: 12 14;";

    // -- FXML nodes ---------------------------------------------------------------------------
    @FXML private Label sessionIdLabel;
    @FXML private VBox player1Card;
    @FXML private VBox player2Card;
    @FXML private Label player1Label;
    @FXML private Label player2Label;
    @FXML private Label player1TurnLabel;
    @FXML private Label player2TurnLabel;
    @FXML private Label turnStatusLabel;
    @FXML private Label moveCountLabel;
    @FXML private StackPane boardContainer;
    @FXML private StackPane forfeitOverlay;
    @FXML private StackPane gameOverOverlay;
    @FXML private VBox gameOverCard;
    @FXML private StackPane playAgainWaitOverlay;
    @FXML private Label waitingMessageLabel;
    @FXML private HBox forfeitToast;
    @FXML private Label forfeitToastLabel;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesBox;
    @FXML private TextField chatInputField;

    // -- Turn badge ---------------------------------------------------------------------------
    @FXML private HBox  turnBadge;
    @FXML private Label turnBadgeIcon;
    @FXML private Label turnBadgeLabel;
    private Timeline badgeTimer;

    // -- Session state ------------------------------------------------------------------------
    private PlatformCore platformCore;
    private String sessionId;
    private String player1Id;
    private String player2Id;
    private boolean gameFinished = false;
    private boolean firstTurn = true;
    private int moveCount = 0;
    private ActionEvent pendingNavEvent;

    // -- Bot state ----------------------------------------------------------------------------
    // Checkers bot play is still a placeholder, but the controller keeps the same session hooks.
    private boolean isBotGame = false;
    private String botPlayerId = null;

    // -- Network mode -------------------------------------------------------------------------
    private boolean       networkMode     = false;
    private String        networkPlayerId = null;
    private String        networkActiveId = null;
    private NetworkClient networkClient   = null;

    // -- Board state --------------------------------------------------------------------------
    private final StackPane[][] cells = new StackPane[BOARD_SIZE][BOARD_SIZE];
    // Cache every full engine-approved path for the currently selected piece.
    private final List<CheckersMove> legalMovesForSelection = new ArrayList<>();
    // Track the board snapshot the UI is rendering and the piece the player currently selected.
    private Board currentState;
    private CheckersMove.Coordinate selectedPiece;

    // -----------------------------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------------------------

    // Inject session and player state, then build the initial board view for this match.
    public void setSessionData(PlatformCore platformCore, String sessionId, List<String> players) {
        this.platformCore = platformCore;
        this.sessionId = sessionId;
        this.player1Id = players.get(0);
        this.player2Id = players.get(1);
        var localSession = platformCore.getSessionManager().getSession(sessionId);
        this.currentState = localSession != null
                ? (Board) localSession.getGameState().getBoard()
                : (Board) new ca.ucalgary.seng300.games.Checkers().createInitialState(players);

        sessionIdLabel.setText("Session: " + sessionId.substring(0, 8) + "...");
        player1Label.setText(player1Id);
        player2Label.setText(player2Id);

        buildBoard();
        renderBoard(currentState);
        updateTurnIndicator(player1Id);

        platformCore.setChatMessageCallback(sessionId, this::onChatMessage);
        platformCore.setForfeitWinCallback(this::showForfeitWinToast);
    }

    // Mark the session as player-vs-bot and trigger the bot immediately if it goes first.
    public void setBotGame(String botId) {
        this.isBotGame = true;
        this.botPlayerId = botId;
        if (botId.equals(player1Id)) {
            scheduleBotMove();
        }
    }

    // Create the 8x8 board grid and wire each square to the shared click handler.
    private void buildBoard() {
        boardContainer.getChildren().clear();
        VBox layout = new VBox(0);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle(
                "-fx-background-color: rgba(8,20,42,0.55); -fx-background-radius: 18; " +
                "-fx-border-color: rgba(77,166,255,0.18); -fx-border-width: 1; -fx-border-radius: 18; " +
                "-fx-padding: 18;");

        for (int y = 0; y < BOARD_SIZE; y++) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER);
            for (int x = 0; x < BOARD_SIZE; x++) {
                // Each cell is a blank container that renderBoard() repopulates from board state.
                StackPane cell = new StackPane();
                cell.setMinSize(CELL_SIZE, CELL_SIZE);
                cell.setPrefSize(CELL_SIZE, CELL_SIZE);
                final int cellX = x;
                final int cellY = y;
                cell.setOnMouseClicked(event -> handleCellClick(cellX, cellY));
                cells[x][y] = cell;
                row.getChildren().add(cell);
            }
            layout.getChildren().add(row);
        }
        boardContainer.getChildren().add(layout);
    }

    // -----------------------------------------------------------------------------------------
    // Input handling
    // -----------------------------------------------------------------------------------------

    // Route a board click to deselect, select, or submit depending on the square state.
    private void handleCellClick(int x, int y) {

        // inverts the coordinates of the board to account for the fact that it's flipped
        int invertedX = BOARD_SIZE - x - 1;
        int invertedY = BOARD_SIZE - y - 1;

        // Ignore clicks once the session is over.
        if (gameFinished) {
            return;
        }

        String activePlayerId = networkMode
                ? networkActiveId
                : platformCore.getSessionManager().getSession(sessionId).getGameState().getActivePlayerId();
        // In network mode, only allow interaction on my turn
        if (networkMode && !networkPlayerId.equals(activePlayerId)) return;
        // Bot turns are read-only from the human player's perspective.
        if (isBotGame && activePlayerId.equals(botPlayerId)) {
            return;
        }

        Piece clickedPiece = currentState.getPiece(invertedX, invertedY);

        // Clicking the selected piece again acts as a local deselect.
        if (selectedPiece != null && selectedPiece.x == invertedX && selectedPiece.y == invertedY) {
            clearSelection();
            renderBoard(currentState);
            return;
        }

        // Own pieces are selectable so the UI can ask the engine for legal paths.
        if (clickedPiece != null && belongsToActivePlayer(clickedPiece, activePlayerId)) {
            selectPiece(invertedX, invertedY);
            return;
        }

        // Highlighted empty squares map back to one cached engine-generated move.
        if (isHighlightedDestination(invertedX, invertedY)) {
            submitSelectedMove(invertedX, invertedY, activePlayerId);
        }

    }

    // -----------------------------------------------------------------------------------------
    // Board rendering
    // -----------------------------------------------------------------------------------------

    // Repaint the board, including square highlights, move hints, pieces, and king badges.
    private void renderBoard(Board board) {
        currentState = board;
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int y = 0; y < BOARD_SIZE; y++) {

                // inverts the coordinates of the board to account for the fact that it's flipped
                int invertedX = BOARD_SIZE - x - 1;
                int invertedY = BOARD_SIZE - y - 1;
                StackPane cell = cells[invertedX][invertedY];
                cell.getChildren().clear();

                boolean darkSquare = (x + y) % 2 == 1;
                boolean selected = selectedPiece != null
                        && selectedPiece.x == x
                        && selectedPiece.y == y;
                boolean highlighted = isHighlightedDestination(x, y);

                // Start from the board's base square colours, then layer selection/hint styling on top.
                String squareColor = darkSquare ? "#183653" : "#d7e4f0";
                String borderColor = "rgba(8,20,42,0.12)";
                double borderWidth = 1;

                if (selected) {
                    squareColor = darkSquare ? "#21476b" : "#edf8ff";
                    borderColor = "#4da6ff";
                    borderWidth = 3;
                } else if (highlighted) {
                    borderColor = "rgba(77,166,255,0.85)";
                    borderWidth = 2.5;
                }

                cell.setStyle(
                        "-fx-background-color: " + squareColor + "; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-width: " + borderWidth + ";");

                if (highlighted) {
                    // The move hint marks legal landing squares without obscuring the piece art.
                    Circle moveHint = new Circle(CELL_SIZE * 0.12);
                    moveHint.setFill(Color.web("#4da6ff"));
                    moveHint.setOpacity(0.85);
                    cell.getChildren().add(moveHint);
                }

                Piece piece = board.getPiece(x, y);
                if (piece != null) {
                    // Pieces are redrawn every frame so the view always matches the latest board snapshot.
                    cell.getChildren().add(buildPieceNode(piece));
                }
            }
        }
    }

    // Build the visual representation for one checker, adding a king badge when needed.
    private StackPane buildPieceNode(Piece piece) {
        Circle checker = new Circle(CELL_SIZE * 0.33);
        checker.setStrokeWidth(3);

        Color fill;
        Color stroke;
        if (piece == Piece.White || piece == Piece.WhiteKing) {
            fill = Color.web("#e6f3ff");
            stroke = Color.web("#4da6ff");
        } else {
            fill = Color.web("#0b1f33");
            stroke = Color.web("#ff8040");
        }
        checker.setFill(fill);
        checker.setStroke(stroke);

        StackPane pieceNode = new StackPane(checker);
        if (piece == Piece.WhiteKing || piece == Piece.BlackKing) {
            // Kings get a small badge instead of a second piece style so normal and king pieces stay aligned.
            Circle badge = new Circle(11, Color.web("#4da6ff"));
            badge.setStroke(Color.web("#d7e4f0"));
            badge.setStrokeWidth(1.5);
            Label crown = new Label("K");
            crown.setTextFill(Color.WHITE);
            crown.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            StackPane badgePane = new StackPane(badge, crown);
            StackPane.setAlignment(badgePane, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(badgePane, new Insets(0, 8, 8, 0));
            pieceNode.getChildren().add(badgePane);
        }
        return pieceNode;
    }

    // Bot support stays isolated here so the rest of the controller can treat human and bot games the same.
    private void scheduleBotMove() {
        Board snapshot = currentState;
        new Timeline(new KeyFrame(Duration.millis(900), event -> {
            if (gameFinished || snapshot == null) {
                return;
            }

            // White is always the first player in a checkers session, black is the second.
            Piece botPiece = botPlayerId.equals(player1Id) ? Piece.White : Piece.Black;
            CheckersMove move = DinoBot.computeCheckersMove(snapshot, botPiece);
            if (move == null) {
                return;
            }

            MoveSubmissionResult result = platformCore.submitMove(sessionId, botPlayerId, move);
            if (result == null || !result.isAccepted()) {
                return;
            }

            // Redraw from the authoritative session update after the bot move is accepted.
            moveCount++;
            clearSelection();
            GameStateUpdate update = result.getUpdate();
            renderBoard((Board) update.getPayload());

            if (update.isGameOver()) {
                gameFinished = true;
                showGameOver(update.getWinnerId());
            } else {
                updateTurnIndicator(update.getActivePlayerId());
            }
        })).play();
    }

    // -----------------------------------------------------------------------------------------
    // Move selection
    // -----------------------------------------------------------------------------------------

    // Ask the engine for all legal move paths for the chosen piece and cache them for the UI.
    private void selectPiece(int x, int y) {
        Piece piece = currentState.getPiece(x, y);
        if (piece == null) {
            return;
        }

        // Ask the engine for the exact legal paths instead of rebuilding rules in the UI.
        Object engine = platformCore.getSessionManager().getEngine("Checkers");
        if (!(engine instanceof Checkers checkers)) {
            return;
        }

        List<CheckersMove> nextMoves = checkers.getLegalMovesForPiece(
                currentState,
                new CheckersMove.Coordinate(x, y)
        );
        if (nextMoves.isEmpty()) {
            return;
        }

        // Cache the engine-approved moves so highlight and submit use the same choices.
        selectedPiece = new CheckersMove.Coordinate(x, y);
        legalMovesForSelection.clear();
        legalMovesForSelection.addAll(nextMoves);
        renderBoard(currentState);
    }

    // Submit the full cached move path that ends on the clicked destination square.
    private void submitSelectedMove(int x, int y, String activePlayerId) {
        if (selectedPiece == null) return;

        CheckersMove move = getMoveForDestination(x, y);
        if (move == null) return;

        clearSelection();

        if (networkMode) {
            // Serialize the CheckersMove and send to server
            // CheckersMove has public final fields — Jackson can serialize them
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("gameType",  "Checkers");
            payload.put("moveId",    UUID.randomUUID().toString());
            payload.put("moveData",  serializeCheckersMove(move));
            networkClient.send("SUBMIT_MOVE", payload);
            renderBoard(currentState); // re-render to clear hints
            return;
        }

        MoveSubmissionResult result = platformCore.submitMove(sessionId, activePlayerId, move);
        if (result == null || !result.isAccepted()) {
            return;
        }

        // Once the backend accepts the move, redraw from the authoritative update.
        moveCount++;
        GameStateUpdate update = result.getUpdate();
        renderBoard((Board) update.getPayload());

        if (update.isGameOver()) {
            gameFinished = true;
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
            renderBoard(board);
        }
        networkActiveId = (String) payload.get("activePlayer");
        moveCount++;
        boolean gameOver = Boolean.TRUE.equals(payload.get("gameOver"));
        if (gameOver) {
            gameFinished = true;
            networkClient.getDispatcher().off("GAME_STATE");
            showGameOver((String) payload.get("winnerId"));
        } else {
            updateTurnIndicator(networkActiveId);
        }
    }

    private Map<String, Object> serializeCheckersMove(CheckersMove move) {
        // Build the JSON representation of target + moves list
        Map<String, Object> targetMap = new HashMap<>();
        targetMap.put("x", move.target.x);
        targetMap.put("y", move.target.y);

        List<Map<String, Object>> movesJson = new ArrayList<>();
        for (CheckersMove.Coordinate coord : move.moves) {
            Map<String, Object> c = new HashMap<>();
            c.put("x", coord.x);
            c.put("y", coord.y);
            movesJson.add(c);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("target", targetMap);
        result.put("moves",  movesJson);
        return result;
    }

    // Restrict piece selection to the active player's colour.
    private boolean belongsToActivePlayer(Piece piece, String activePlayerId) {
        if (player1Id.equals(activePlayerId)) {
            return piece == Piece.White || piece == Piece.WhiteKing;
        }
        return piece == Piece.Black || piece == Piece.BlackKing;
    }

    // A destination is clickable when it matches the final landing square of a cached move path.
    private boolean isHighlightedDestination(int x, int y) {
        return getMoveForDestination(x, y) != null;
    }

    // Clear all current piece selection and move highlight state.
    private void clearSelection() {
        selectedPiece = null;
        legalMovesForSelection.clear();
    }

    // Convert a highlighted destination square back into its full engine-generated move path.
    private CheckersMove getMoveForDestination(int x, int y) {
        for (CheckersMove move : legalMovesForSelection) {
            // Match the clicked square back to its full precomputed move path.
            CheckersMove.Coordinate destination = move.moves.getLast();
            if (destination.x == x && destination.y == y) {
                return move;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------
    // Turn and result UI
    // -----------------------------------------------------------------------------------------

    // Refresh player cards, turn labels, and move counter after each accepted move.
    private void updateTurnIndicator(String activePlayerId) {
        boolean player1Active = player1Id.equals(activePlayerId);

        player1Card.setStyle(player1Active ? P1_CARD_ACTIVE : P1_CARD_INACTIVE);
        player2Card.setStyle(player1Active ? P2_CARD_INACTIVE : P2_CARD_ACTIVE);

        player1TurnLabel.setText(player1Active ? "▶  Your turn" : "Waiting…");
        player1TurnLabel.setTextFill(Color.web(player1Active ? "#4da6ff" : "#3d6080"));

        player2TurnLabel.setText(player1Active ? "Waiting…" : "▶  Your turn");
        player2TurnLabel.setTextFill(Color.web(player1Active ? "#3d6080" : "#ff8040"));

        turnStatusLabel.setText((player1Active ? "White" : "Black") + " to move");
        moveCountLabel.setText("Move " + (moveCount + 1));

        if (firstTurn) { firstTurn = false; return; }
        String color = player1Active ? "#4da6ff" : "#ff8040";
        String side  = player1Active ? "White" : "Black";
        showTurnBadge(side + " to move — " + activePlayerId, color);
    }

    private void showTurnBadge(String message, String accentColor) {
        turnBadgeLabel.setText(message);
        turnBadgeIcon.setTextFill(Color.web(accentColor));
        turnBadge.setVisible(true);
        if (badgeTimer != null) badgeTimer.stop();
        badgeTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> hideTurnBadge()));
        badgeTimer.play();
    }

    private void hideTurnBadge() { turnBadge.setVisible(false); }

    @FXML private void handleDismissBadge(ActionEvent ignoredEvent) { hideTurnBadge(); }

    // -----------------------------------------------------------------------------------------
    // Game-over overlay
    // -----------------------------------------------------------------------------------------

    // Show the correct result overlay based on whether the current user won, lost, or drew.
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

    // Build the result card used when the match ends in a draw.
    private void buildDrawCard() {
        Label icon = new Label("🤝");
        icon.setStyle("-fx-font-size: 52px; -fx-text-fill: white;");
        VBox.setMargin(icon, new Insets(0, 0, 14, 0));

        Label title = new Label("Match Draw");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        // Reuse the shared points/button helpers so all result cards stay consistent.
        Label points = pointsLabel(DRAW_MMR);
        VBox.setMargin(points, new Insets(6, 0, 28, 0));

        gameOverCard.getChildren().addAll(icon, title, points, buildButtons());
    }

    // Build the result card shown to the winning player.
    private void buildWinCard(PlayerProfile profile) {
        Label icon = new Label("🏆");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #f5c518;");
        VBox.setMargin(icon, new Insets(0, 0, 10, 0));

        VBox avatarBlock = buildAvatarBlock(profile, 54, "#f5c518");
        VBox.setMargin(avatarBlock, new Insets(0, 0, 6, 0));

        Label won = new Label("Won!");
        won.setTextFill(Color.web("#f5c518"));
        won.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        // The winner card shows the local player's profile and positive MMR gain.
        Label points = pointsLabel(WIN_MMR);
        VBox.setMargin(points, new Insets(6, 0, 28, 0));

        gameOverCard.getChildren().addAll(icon, avatarBlock, won, points, buildButtons());
    }

    // Build the result card shown to the losing player, featuring the winner's avatar.
    private void buildLossCard(PlayerProfile winnerProfile) {
        Label icon = new Label("💔");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #ff4444;");
        VBox.setMargin(icon, new Insets(0, 0, 10, 0));

        VBox avatarBlock = buildAvatarBlock(winnerProfile, 54, "#ff8040");
        VBox.setMargin(avatarBlock, new Insets(0, 0, 6, 0));

        Label won = new Label("Won!");
        won.setTextFill(Color.web("#ff8040"));
        won.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        // The loss card uses the winner's profile and the negative MMR delta.
        Label points = pointsLabel(LOSS_MMR);
        VBox.setMargin(points, new Insets(6, 0, 28, 0));

        gameOverCard.getChildren().addAll(icon, avatarBlock, won, points, buildButtons());
    }

    // Create the circular avatar/name block reused on the end-of-game cards.
    private VBox buildAvatarBlock(PlayerProfile profile, double radius, String borderColor) {
        ImageView avatar = new ImageView();
        avatar.setFitWidth(radius * 2);
        avatar.setFitHeight(radius * 2);
        avatar.setPreserveRatio(false);
        avatar.setSmooth(true);

        // Fall back to the app logo when a profile does not have a custom avatar.
        String avatarPath = (profile != null && profile.getAvatarPath() != null)
                ? profile.getAvatarPath() : "/images/gameDinoLogo.png";
        try {
            var url = getClass().getResource(avatarPath);
            if (url != null) {
                avatar.setImage(new Image(url.toExternalForm()));
            }
        } catch (Exception ignored) {}

        // Clip the image into a circle so all avatars render with the same shape.
        Circle clip = new Circle(radius, radius, radius);
        avatar.setClip(clip);

        StackPane avatarPane = new StackPane(avatar);
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

    // Format the MMR delta shown on result cards.
    private Label pointsLabel(int delta) {
        String sign = delta >= 0 ? "+" : "";
        Label label = new Label("Points achieved: " + sign + delta + " pts");
        label.setTextFill(Color.web(delta >= 0 ? "#40d090" : "#ff6666"));
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        return label;
    }

    // Build the shared "Play Again" / "End Match" button stack for result cards.
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

    // -----------------------------------------------------------------------------------------
    // Play-again coordination
    // -----------------------------------------------------------------------------------------

    // Handle replay requests, including waiting for the other human player when needed.
    private void handlePlayAgain(ActionEvent event) {
        if (isBotGame) {
            startNewGame(event);
            return;
        }

        if (networkMode) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            networkClient.send("REQUEST_PLAY_AGAIN", payload);
            String other = player1Id.equals(networkPlayerId) ? player2Id : player1Id;
            waitingMessageLabel.setText(other + " is still viewing result...");
            playAgainWaitOverlay.setVisible(true);
            playAgainWaitOverlay.setManaged(true);
            return;
        }

        String me = platformCore.getCurrentUser();
        String other = player1Id.equals(me) ? player2Id : player1Id;

        // The callback handles the case where the opponent exits instead of accepting the rematch.
        boolean bothReady = platformCore.requestPlayAgain(sessionId, me, () ->
                javafx.application.Platform.runLater(() -> {
                    playAgainWaitOverlay.setVisible(false);
                    playAgainWaitOverlay.setManaged(false);
                    navigateToGameRoom(event);
                }));

        if (bothReady) {
            startNewGame(event);
        } else {
            waitingMessageLabel.setText(other + " is still viewing result...");
            playAgainWaitOverlay.setVisible(true);
            playAgainWaitOverlay.setManaged(true);
        }
    }

    // Allow the player to back out of the replay wait state.
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
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CheckersGamePage.fxml"));
                Parent root = loader.load();
                CheckersGamePageController ctrl = loader.getController();
                ctrl.setSessionData(platformCore, newSessionId, players);
                ctrl.enableNetworkMode(networkPlayerId, networkClient);
                Stage stage = (Stage) boardContainer.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.sizeToScene();
                stage.show();
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    // Start a fresh checkers session and reload this page against the new session id.
    private void startNewGame(ActionEvent event) {
        playAgainWaitOverlay.setVisible(false);
        playAgainWaitOverlay.setManaged(false);
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setManaged(false);

        List<String> players = List.of(player1Id, player2Id);
        String gameType = "Checkers";
        // Rematches are created as a fresh room/session pair so the new game starts cleanly.
        ca.ucalgary.seng300.room.Room room =
                platformCore.getRoomManager().createRoom(player1Id, gameType, 2);
        if (room == null) {
            navigateToGameRoom(event);
            return;
        }
        platformCore.getRoomManager().joinRoom(room.getRoomId(), player2Id);

        String newSessionId = platformCore.getSessionManager()
                .startSession(room.getRoomId(), gameType, players);

        platformCore.getRoomManager().leaveRoom(room.getRoomId(), player1Id);
        platformCore.getRoomManager().leaveRoom(room.getRoomId(), player2Id);

        if (newSessionId == null) {
            navigateToGameRoom(event);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CheckersGamePage.fxml"));
            Parent root = loader.load();
            CheckersGamePageController controller = loader.getController();
            controller.setSessionData(platformCore, newSessionId, players);
            if (isBotGame) {
                controller.setBotGame(botPlayerId);
            }
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------------------------
    // End match / forfeit flow
    // -----------------------------------------------------------------------------------------

    // End the current match from the result screen and return to the game room.
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

    // Intercept dashboard navigation during an active match so the user can confirm forfeiting.
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

    // Confirm a forfeit, update the backend, and leave the session.
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

    // Close the forfeit confirmation overlay and keep playing.
    @FXML
    private void handleCancelForfeit(ActionEvent ignoredEvent) {
        forfeitOverlay.setVisible(false);
        forfeitOverlay.setManaged(false);
    }

    // -----------------------------------------------------------------------------------------
    // Forfeit win toast
    // -----------------------------------------------------------------------------------------

    // Show a temporary toast when this player wins because the opponent forfeited.
    private void showForfeitWinToast(String message) {
        forfeitToastLabel.setText(message);
        forfeitToast.setVisible(true);
        forfeitToast.setManaged(true);
        new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            forfeitToast.setVisible(false);
            forfeitToast.setManaged(false);
        })).play();
    }

    // -----------------------------------------------------------------------------------------
    // Chat
    // -----------------------------------------------------------------------------------------

    // Publish the local chat input into the shared session chat stream.
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

    // Render an incoming chat message as either a local or remote chat bubble.
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

    // -----------------------------------------------------------------------------------------
    // Room/session cleanup and navigation
    // -----------------------------------------------------------------------------------------

    // Unregister callbacks and leave any room associated with this head-to-head session.
    private void closeRoom() {
        platformCore.setChatMessageCallback(sessionId, null);
        platformCore.setForfeitWinCallback(null);
        // Clean up any lingering room state for this pair before returning to the lobby.
        platformCore.getRoomManager().getAllRooms("Checkers").stream()
                .filter(room -> room.getPlayers().contains(player1Id) && room.getPlayers().contains(player2Id))
                .forEach(room -> {
                    platformCore.getRoomManager().leaveRoom(room.getRoomId(), player1Id);
                    platformCore.getRoomManager().leaveRoom(room.getRoomId(), player2Id);
                });
    }

    // Return to the shared game room page after the current session ends or is abandoned.
    private void navigateToGameRoom(ActionEvent event) {
        platformCore.setForfeitWinCallback(null);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GameRoomPage.fxml"));
            Parent root = loader.load();
            GameRoomPageController controller = loader.getController();
            controller.setPlatformCore(platformCore);
            controller.setGameType("Checkers");
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
            controller.setGameType("Checkers");
            Stage stage = (Stage) boardContainer.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
