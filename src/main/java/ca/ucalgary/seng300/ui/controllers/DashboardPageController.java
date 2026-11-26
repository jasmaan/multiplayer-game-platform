package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.core.PlayerProfile;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.notification.NotificationService;
import ca.ucalgary.seng300.ui.controllers.MatchFoundController.PlayerData;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DashboardPageController implements NotificationService.NotificationListener {

    // ── Styles ────────────────────────────────────────────────────
    private static final String USERNAME_FIELD_INACTIVE =
            "-fx-background-color: rgba(10,25,45,0.9); -fx-text-fill: #b9d9ff; " +
                    "-fx-prompt-text-fill: #3d6080; -fx-font-size: 13px; -fx-background-radius: 10; " +
                    "-fx-border-color: rgba(77,166,255,0.2); -fx-border-width: 1; -fx-border-radius: 10; -fx-padding: 8 12;";
    private static final String USERNAME_FIELD_ACTIVE =
            "-fx-background-color: rgba(10,25,50,0.95); -fx-text-fill: #e6f3ff; " +
                    "-fx-prompt-text-fill: #3d6080; -fx-font-size: 13px; -fx-background-radius: 10; " +
                    "-fx-border-color: #4da6ff; -fx-border-width: 1.5; -fx-border-radius: 10; -fx-padding: 8 12;";
    private static final String DEFAULT_FIELD_STYLE =
            "-fx-background-color: rgba(10,25,45,0.9); -fx-text-fill: #e6f3ff; -fx-font-size: 13px; " +
                    "-fx-prompt-text-fill: #3d6080; -fx-border-color: rgba(77,166,255,0.25); -fx-border-width: 1; " +
                    "-fx-padding: 0 12 0 12; -fx-border-radius: 8; -fx-background-radius: 8;";
    private static final String SETTINGS_AVATAR_IDLE =
            "-fx-background-radius: 25; -fx-border-radius: 25; -fx-border-width: 2; " +
                    "-fx-border-color: rgba(77,166,255,0.3); -fx-cursor: hand;";
    private static final String SETTINGS_AVATAR_SELECTED =
            "-fx-background-color: rgba(77,166,255,0.15); -fx-background-radius: 25; " +
                    "-fx-border-radius: 25; -fx-border-width: 2.5; -fx-border-color: #4da6ff; -fx-cursor: hand;";

    // ── State ─────────────────────────────────────────────────────
    private PlatformCore platformCore;
    private boolean activityPanelVisible = true;
    private boolean settingsEditMode = false;
    private String settingsOriginalUsername = "";
    private List<PlayerProfile> allProfiles = Collections.emptyList();
    private Popup leaderboardPopup;
    private Timeline settingsNotificationTimeline;
    private Timeline avatarNotificationTimeline;

    // ── Sidebar ───────────────────────────────────────────────────
    @FXML private VBox      userCard;
    @FXML private VBox      userCardDetails;
    @FXML private Label     userCardFullNameLabel;
    @FXML private Label     userCardJoinDateLabel;
    @FXML private Label     userCardMmrLabel;
    @FXML private Label     usernameLabel;
    @FXML private Label     rankLabel;
    @FXML private ImageView rankShieldImage;
    @FXML private ImageView userAvatarImage;
    @FXML private Button    logOutButton;
    @FXML private Button    activityToggleBtn;
    @FXML private Button    settingsButton;
    @FXML private Button    viewGameListButton;
    @FXML private Button    profileButton;
    @FXML private Button    leaderboardButton;

    // ── Center ────────────────────────────────────────────────────
    @FXML private Label     welcomeLabel;
    @FXML private Label     totalWinsLabel;
    @FXML private Label     totalMatchesLabel;
    @FXML private Label     rankStatLabel;
    @FXML private ImageView rankStatShieldImage;
    @FXML private Label     mmrStatLabel;

    // ── Right panel ───────────────────────────────────────────────
    @FXML private VBox      rightPanel;
    @FXML private VBox      recentlyPlayedList;
    @FXML private VBox      winStatsList;
    @FXML private ScrollPane leaderboardScroll;
    @FXML private VBox      leaderboardList;
    @FXML private Label     leaderboardHeader;
    @FXML private TextField leaderboardSearch;

    // ── Settings overlay ──────────────────────────────────────────
    @FXML private StackPane   settingsOverlay;
    @FXML private StackPane   unsavedWarningOverlay;
    @FXML private TextField   settingsUsernameField;
    @FXML private Button      settingsEditBtn;
    @FXML private Button      settingsSaveBtn;
    @FXML private HBox        settingsNotification;
    @FXML private Label       settingsNotificationLabel;
    @FXML private Region      sidePaneDivider;
    // Password pane
    @FXML private VBox          passwordPane;
    @FXML private Button        changePasswordBtn;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         passwordErrorLabel;
    // Avatar pane
    @FXML private VBox      avatarPane;
    @FXML private Button    changeAvatarBtn;
    @FXML private ImageView settingsCurrentAvatarImage;
    @FXML private HBox      avatarNotification;
    @FXML private Label     avatarNotificationLabel;
    @FXML private StackPane settingsAvatarCard1;
    @FXML private StackPane settingsAvatarCard2;
    @FXML private StackPane settingsAvatarCard3;
    @FXML private StackPane settingsAvatarCard4;

    // ── Carousel ──────────────────────────────────────────────────
    @FXML private StackPane imageCarousel;
    private Timeline carouselTimeline;

    // ── Friends overlay ───────────────────────────────────────────
    @FXML private StackPane  friendsOverlay;
    @FXML private Button     friendsButton;
    @FXML private Circle     friendsRequestBadge;
    @FXML private TextField  addFriendField;
    @FXML private Button     addFriendBtn;
    @FXML private HBox       friendsNotification;
    @FXML private Label      friendsNotificationLabel;
    @FXML private VBox       pendingRequestsList;
    @FXML private VBox       onlineFriendsList;
    @FXML private VBox       offlineFriendsList;
    private Timeline friendsNotificationTimeline;
    private Timeline friendsPollTimeline;

    // ── Settings — pane-switch approach (kept for FXML compat) ───
    @FXML private VBox      dashboardPane;
    @FXML private VBox      settingsPane;
    @FXML private StackPane unsavedSettingsPane;


    // ─────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────

    public void setPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
        platformCore.getNotificationService().setListener(this);

        // Remove the ScrollPane's default white viewport background so it blends
        // with the dark panel. Must be done after the scene graph is attached.
        if (leaderboardScroll != null) {
            leaderboardScroll.skinProperty().addListener((obs, o, n) -> {
                leaderboardScroll.lookup(".viewport")
                    .setStyle("-fx-background-color: transparent;");
            });
        }

        // Poll every 3 seconds to refresh friend online/offline status and catch
        // friend requests sent from other running instances via disk sync
        friendsPollTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            syncProfileFromDisk();
            updateFriendsBadge();
            if (friendsOverlay.isVisible()) {
                populateFriendsList();
            }
        }));
        friendsPollTimeline.setCycleCount(Timeline.INDEFINITE);
        friendsPollTimeline.play();

        populateDashboard();
    }

    @Override
    public void onNotification(String playerId, String message) {
        Platform.runLater(() -> showToast("🎮", "Room Update", message, "#4da6ff"));
    }

    @Override
    public void onMatchFound(MatchFoundEvent event) {
        Platform.runLater(() -> showMatchFoundModal(event));
    }

    @Override
    public void onFriendRequestReceived(String fromUsername) {
        Platform.runLater(() -> {
            updateFriendsBadge();
            if (friendsOverlay.isVisible()) {
                populateFriendsList();
            }
            showToast("👤", "Friend Request", fromUsername + " sent you a friend request!", "#40d090");
        });
    }

    /**
     * Shows a styled toast notification that auto-dismisses after 4 seconds.
     * Matches the platform's dark-navy / blue-gradient aesthetic.
     */
    private void showToast(String icon, String title, String body, String accentColor) {
        Stage owner = (Stage) usernameLabel.getScene().getWindow();

        HBox toast = new HBox(12);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setStyle(
                "-fx-background-color: linear-gradient(to right, rgba(10,25,50,0.97), rgba(15,41,66,0.97)); " +
                "-fx-background-radius: 14; " +
                "-fx-border-color: " + accentColor + "55; " +
                "-fx-border-width: 1; -fx-border-radius: 14; " +
                "-fx-padding: 14 20; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 6);");
        toast.setMaxWidth(380);

        // Icon
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 22px;");

        // Text column
        VBox textCol = new VBox(3);
        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.web(accentColor));
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        Label bodyLabel = new Label(body);
        bodyLabel.setTextFill(Color.web("#b9d9ff"));
        bodyLabel.setStyle("-fx-font-size: 11px;");
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxWidth(280);
        textCol.getChildren().addAll(titleLabel, bodyLabel);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        // Close button
        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #7fbfff; " +
                "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 0;");

        toast.getChildren().addAll(iconLabel, textCol, closeBtn);

        Popup toastPopup = new Popup();
        toastPopup.getContent().add(toast);
        toastPopup.setAutoHide(true);

        // Position at the top-right of the window
        double x = owner.getX() + owner.getWidth() - 400;
        double y = owner.getY() + 20;
        toastPopup.show(owner, x, y);

        closeBtn.setOnAction(e -> toastPopup.hide());

        // Auto-dismiss after 4 seconds
        Timeline dismiss = new Timeline(new KeyFrame(Duration.seconds(4), e -> toastPopup.hide()));
        dismiss.play();
    }

    // ─────────────────────────────────────────────────────────────
    // Dashboard population
    // ─────────────────────────────────────────────────────────────

    private void populateDashboard() {
        PlayerProfile profile = platformCore.getCurrentProfile();
        if (profile == null) return;

        String username = profile.getUsername();
        String rank     = profile.getCurrentRank();

        usernameLabel.setText(username);
        rankLabel.setText(rank);
        rankShieldImage.setImage(new Image(
                getClass().getResource(rankShieldPath(rank)).toExternalForm()));
        if (profile.getAvatarPath() != null) {
            userAvatarImage.setImage(new Image(
                    getClass().getResource(profile.getAvatarPath()).toExternalForm()));
        }

        userCardFullNameLabel.setText(profile.getFirstName() + " " + profile.getLastName());
        String joinDate = profile.getAccountCreated() != null
                ? profile.getAccountCreated().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                : "—";
        userCardJoinDateLabel.setText("Member since: " + joinDate);
        userCardMmrLabel.setText(profile.getTotalMmr() + " MMR");

        welcomeLabel.setText("Welcome back, " + username + "!");
        totalWinsLabel.setText(String.valueOf(profile.getTotalWins()));
        totalMatchesLabel.setText(String.valueOf(profile.getTotalMatchesPlayed()));
        rankStatLabel.setText(rank);
        rankStatShieldImage.setImage(new Image(
                getClass().getResource(rankShieldPath(rank)).toExternalForm()));
        mmrStatLabel.setText(profile.getTotalMmr() + " MMR");

        populateRecentlyPlayed(profile.getRecentGameHistory());
        populateWinStats(profile);
        populateLeaderboard(profile);
        updateFriendsBadge();

        leaderboardSearch.textProperty().addListener(
                (obs, oldVal, newVal) -> renderLeaderboardRows(newVal.trim().toLowerCase()));

        buildImageCarousel();
    }

    private void buildImageCarousel() {
        if (imageCarousel == null) return;
        String[] imagePaths = {
                "/images/Bannerimage1.png",
                "/images/BannerImage2.png",
                "/images/BannerImage3.png"
        };

        List<ImageView> views = new ArrayList<>();
        for (String path : imagePaths) {
            var url = getClass().getResource(path);
            if (url == null) continue;
            ImageView iv = new ImageView(new Image(url.toExternalForm()));
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.fitWidthProperty().bind(imageCarousel.widthProperty());
            iv.setFitHeight(280);
            iv.setOpacity(0.0);
            views.add(iv);
        }
        if (views.isEmpty()) return;

        // Clip so off-screen images don't bleed outside the carousel bounds
        imageCarousel.layoutBoundsProperty().addListener((obs, o, n) -> {
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(n.getWidth(), n.getHeight());
            imageCarousel.setClip(clip);
        });

        imageCarousel.getChildren().addAll(views);
        views.get(0).setOpacity(1.0);

        final int[] currentIndex = {0};
        carouselTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            int nextIndex = (currentIndex[0] + 1) % views.size();
            ImageView current = views.get(currentIndex[0]);
            ImageView next    = views.get(nextIndex);

            double width = imageCarousel.getWidth();
            next.setTranslateX(width);
            next.setOpacity(1.0);

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(500), current);
            slideOut.setFromX(0);
            slideOut.setToX(-width);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(500), next);
            slideIn.setFromX(width);
            slideIn.setToX(0);

            ParallelTransition slide = new ParallelTransition(slideOut, slideIn);
            slide.setOnFinished(ev -> {
                current.setOpacity(0.0);
                current.setTranslateX(0);
            });
            slide.play();

            currentIndex[0] = nextIndex;
        }));
        carouselTimeline.setCycleCount(Timeline.INDEFINITE);
        carouselTimeline.play();
    }

    private void populateRecentlyPlayed(List<String> history) {
        recentlyPlayedList.getChildren().clear();
        if (history.isEmpty()) {
            Label empty = new Label("No games played yet");
            empty.setTextFill(Color.web("#3d6080"));
            empty.setStyle("-fx-font-size: 12px;");
            recentlyPlayedList.getChildren().add(empty);
            return;
        }
        for (String game : history) {
            recentlyPlayedList.getChildren().add(buildActivityItem(game));
        }
    }

    private void populateWinStats(PlayerProfile profile) {
        winStatsList.getChildren().clear();
        String[][] games = {
                {"tictactoe",   "Tic-Tac-Toe", "#4da6ff"},
                {"connectfour", "Connect Four", "#ff8040"},
                {"checkers",    "Checkers",     "#40d090"}
        };
        for (String[] g : games) {
            int wins    = profile.getWins().getOrDefault(g[0], 0);
            int matches = profile.getMatchesPlayed()
                    .getOrDefault(g[0], Collections.emptyList()).size();
            winStatsList.getChildren().add(buildWinStatItem(g[1], g[2], wins, matches));
        }
    }

    private void populateLeaderboard(PlayerProfile currentUser) {
        allProfiles = platformCore.getAllProfiles();
        if (allProfiles.isEmpty()) allProfiles = List.of(currentUser);

        leaderboardList.getChildren().clear();
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label rankCol = styledLabel("#",      "#7fbfff", "-fx-font-size:11px; -fx-font-weight:bold;");
        Label nameCol = styledLabel("Player", "#7fbfff", "-fx-font-size:11px; -fx-font-weight:bold;");
        Label winsCol = styledLabel("W",      "#7fbfff", "-fx-font-size:11px; -fx-font-weight:bold;");
        rankCol.setPrefWidth(24);
        winsCol.setPrefWidth(30);
        HBox.setHgrow(nameCol, Priority.ALWAYS);
        header.getChildren().addAll(rankCol, nameCol, winsCol);
        leaderboardList.getChildren().add(header);

        renderLeaderboardRows("");
    }

    private void renderLeaderboardRows(String query) {
        if (leaderboardList.getChildren().size() > 1) {
            leaderboardList.getChildren().subList(1, leaderboardList.getChildren().size()).clear();
        }
        String currentUsername = platformCore.getCurrentProfile().getUsername();
        int position = 1;
        for (PlayerProfile p : allProfiles) {
            if (!query.isEmpty() && !p.getUsername().toLowerCase().contains(query)) continue;
            boolean isMe = p.getUsername().equals(currentUsername);
            HBox row = buildLeaderboardRow(position++, p.getUsername(), p.getTotalWins(), isMe);
            final PlayerProfile clicked = p;
            row.setOnMouseClicked(e -> showProfileOverlay(clicked));
            row.setStyle(row.getStyle() + " -fx-cursor: hand;");
            leaderboardList.getChildren().add(row);
        }
        if (leaderboardList.getChildren().size() == 1) {
            Label none = new Label("No players found");
            none.setTextFill(Color.web("#3d6080"));
            none.setStyle("-fx-font-size: 11px;");
            leaderboardList.getChildren().add(none);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Row / item builders
    // ─────────────────────────────────────────────────────────────

    private HBox buildActivityItem(String gameName) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setStyle("-fx-background-color: rgba(10,25,45,0.8); -fx-background-radius: 10; " +
                "-fx-border-color: rgba(77,166,255,0.15); -fx-border-width: 1; " +
                "-fx-border-radius: 10; -fx-padding: 8 12;");

        StackPane dot = new StackPane();
        dot.setPrefSize(10, 10);
        dot.setMinSize(10, 10);
        dot.setStyle("-fx-background-radius: 5; -fx-background-color: " + gameColor(gameName) + ";");

        Label name = new Label(formatGameName(gameName));
        name.setTextFill(Color.web("#e6f3ff"));
        name.setStyle("-fx-font-size: 12px;");

        item.getChildren().addAll(dot, name);
        return item;
    }

    private HBox buildWinStatItem(String displayName, String color, int wins, int matches) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setStyle("-fx-background-color: rgba(10,25,45,0.8); -fx-background-radius: 10; " +
                "-fx-border-color: rgba(77,166,255,0.15); -fx-border-width: 1; " +
                "-fx-border-radius: 10; -fx-padding: 7 12;");

        StackPane dot = new StackPane();
        dot.setPrefSize(8, 8);
        dot.setMinSize(8, 8);
        dot.setStyle("-fx-background-radius: 4; -fx-background-color: " + color + ";");

        Label nameLabel = new Label(displayName);
        nameLabel.setTextFill(Color.web("#b9d9ff"));
        nameLabel.setStyle("-fx-font-size: 12px;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label stats = new Label(wins + "W  " + matches + "M");
        stats.setTextFill(Color.web("#7fbfff"));
        stats.setStyle("-fx-font-size: 11px;");

        item.getChildren().addAll(dot, nameLabel, spacer, stats);
        return item;
    }

    private HBox buildLeaderboardRow(int position, String username, int wins, boolean isCurrentUser) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        String bg = isCurrentUser ? "rgba(77,166,255,0.12)" : "rgba(10,25,45,0.8)";
        row.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 10; " +
                "-fx-border-color: rgba(77,166,255,0.15); -fx-border-width: 1; " +
                "-fx-border-radius: 10; -fx-padding: 7 12;");

        Label rankLbl = styledLabel("#" + position, "#4da6ff", "-fx-font-size:12px; -fx-font-weight:bold;");
        Label nameLbl = styledLabel(username, isCurrentUser ? "#e6f3ff" : "#b9d9ff", "-fx-font-size:12px;");
        Label winsLbl = styledLabel(wins + "W", "#7fbfff", "-fx-font-size:11px;");

        rankLbl.setPrefWidth(24);
        winsLbl.setPrefWidth(30);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        row.getChildren().addAll(rankLbl, nameLbl, winsLbl);
        return row;
    }

    private Label styledLabel(String text, String colorHex, String style) {
        Label l = new Label(text);
        l.setTextFill(Color.web(colorHex));
        l.setStyle(style);
        return l;
    }

    // ─────────────────────────────────────────────────────────────
    // Leaderboard profile popup
    // ─────────────────────────────────────────────────────────────

    private void showProfileOverlay(PlayerProfile profile) {
        if (leaderboardPopup != null && leaderboardPopup.isShowing()) leaderboardPopup.hide();
        leaderboardPopup = buildLeaderboardPopup(profile);
        Bounds headerBounds = leaderboardHeader.localToScreen(leaderboardHeader.getBoundsInLocal());
        double popupWidth = 230;
        double x = headerBounds.getMinX() - popupWidth - 30;
        double y = headerBounds.getMinY();
        leaderboardPopup.show(leaderboardHeader.getScene().getWindow(), x, y);
    }

    private Popup buildLeaderboardPopup(PlayerProfile profile) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox card = new VBox(0);
        card.setPrefWidth(230);
        card.setStyle(
                "-fx-background-color: rgba(8,20,42,0.98); -fx-background-radius: 20; " +
                        "-fx-border-color: rgba(77,166,255,0.3); -fx-border-width: 1; -fx-border-radius: 20; " +
                        "-fx-padding: 24; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.65), 30, 0, 0, 8);");

        // Header row: avatar + username/rank + close
        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        StackPane avatarWrapper = new StackPane();
        avatarWrapper.setPrefSize(44, 44);
        avatarWrapper.setMinSize(44, 44);
        avatarWrapper.setStyle(
                "-fx-background-color: rgba(10,25,45,0.92); -fx-background-radius: 22; " +
                        "-fx-border-color: rgba(77,166,255,0.3); -fx-border-width: 1.5; -fx-border-radius: 22;");
        ImageView avatarImg = new ImageView();
        avatarImg.setFitWidth(44);
        avatarImg.setFitHeight(44);
        avatarImg.setPreserveRatio(true);
        avatarImg.setClip(new Circle(22, 22, 22));
        String avatarPath = profile.getAvatarPath();
        if (avatarPath == null && "DinoBot".equals(profile.getUsername())) {
            avatarPath = "/images/DinoBot_Avatar.png";
        }
        if (avatarPath != null) {
            var resource = getClass().getResource(avatarPath);
            if (resource != null) avatarImg.setImage(new Image(resource.toExternalForm()));
        }
        avatarWrapper.getChildren().add(avatarImg);

        VBox nameBlock = new VBox(3);
        HBox.setHgrow(nameBlock, Priority.ALWAYS);
        Label usernameText = new Label(profile.getUsername());
        usernameText.setTextFill(Color.web("#e6f3ff"));
        usernameText.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        HBox rankRow = new HBox(5);
        rankRow.setAlignment(Pos.CENTER_LEFT);
        ImageView shieldImg = new ImageView(new Image(
                getClass().getResource(rankShieldPath(profile.getCurrentRank())).toExternalForm()));
        shieldImg.setFitWidth(13);
        shieldImg.setFitHeight(13);
        shieldImg.setPreserveRatio(true);
        Label rankText = new Label(profile.getCurrentRank());
        rankText.setTextFill(Color.web("#888888"));
        rankText.setStyle("-fx-font-size: 11px;");
        rankRow.getChildren().addAll(shieldImg, rankText);
        nameBlock.getChildren().addAll(usernameText, rankRow);

        Button closeBtn = new Button("✕");
        closeBtn.setPrefSize(26, 26);
        closeBtn.setStyle("-fx-background-color: rgba(77,166,255,0.1); -fx-text-fill: #7fbfff; " +
                "-fx-font-size: 12px; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 0;");
        closeBtn.setOnAction(e -> popup.hide());

        headerRow.getChildren().addAll(avatarWrapper, nameBlock, closeBtn);

        // Divider
        Region div1 = divider();
        VBox.setMargin(div1, new Insets(14, 0, 14, 0));

        // Stats row
        HBox statsRow = new HBox(8);
        statsRow.getChildren().addAll(
                buildStatBox(String.valueOf(profile.getTotalWins()), "Total Wins"),
                buildStatBox(String.valueOf(profile.getTotalMatchesPlayed()), "Matches"));

        Region div2 = divider();
        VBox.setMargin(div2, new Insets(14, 0, 10, 0));

        Label gameStatsTitle = new Label("GAME STATS");
        gameStatsTitle.setTextFill(Color.web("#3d6080"));
        gameStatsTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        VBox.setMargin(gameStatsTitle, new Insets(0, 0, 8, 0));

        VBox gameStatsList = new VBox(6);
        String[][] games = {
                {"tictactoe",   "Tic-Tac-Toe", "#4da6ff"},
                {"connectfour", "Connect Four", "#ff8040"},
                {"checkers",    "Checkers",     "#40d090"}
        };
        for (String[] g : games) {
            int w = profile.getWins().getOrDefault(g[0], 0);
            int m = profile.getMatchesPlayed().getOrDefault(g[0], Collections.emptyList()).size();
            gameStatsList.getChildren().add(buildProfileGameRow(g[1], g[2], w, m));
        }

        card.getChildren().addAll(headerRow, div1, statsRow, div2, gameStatsTitle, gameStatsList);
        popup.getContent().add(card);
        return popup;
    }

    private VBox buildStatBox(String value, String label) {
        VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setStyle("-fx-background-color: rgba(10,25,45,0.8); -fx-background-radius: 10; " +
                "-fx-border-color: rgba(77,166,255,0.15); -fx-border-width: 1; " +
                "-fx-border-radius: 10; -fx-padding: 10 8;");
        Label val = new Label(value);
        val.setTextFill(Color.web("#4da6ff"));
        val.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label lbl = new Label(label);
        lbl.setTextFill(Color.web("#3d6080"));
        lbl.setStyle("-fx-font-size: 10px;");
        box.getChildren().addAll(val, lbl);
        return box;
    }

    private HBox buildProfileGameRow(String name, String color, int wins, int matches) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: rgba(10,25,45,0.7); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(77,166,255,0.1); -fx-border-width: 1; " +
                "-fx-border-radius: 8; -fx-padding: 6 10;");

        StackPane dot = new StackPane();
        dot.setPrefSize(8, 8);
        dot.setMinSize(8, 8);
        dot.setStyle("-fx-background-radius: 4; -fx-background-color: " + color + ";");

        Label nameLabel = new Label(name);
        nameLabel.setTextFill(Color.web("#b9d9ff"));
        nameLabel.setStyle("-fx-font-size: 11px;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label statsLabel = new Label(wins + "W  " + matches + "M");
        statsLabel.setTextFill(Color.web("#7fbfff"));
        statsLabel.setStyle("-fx-font-size: 11px;");

        row.getChildren().addAll(dot, nameLabel, statsLabel);
        return row;
    }

    private Region divider() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMaxHeight(1);
        r.setMaxWidth(Double.MAX_VALUE);
        r.setStyle("-fx-background-color: rgba(77,166,255,0.15);");
        return r;
    }

    // ─────────────────────────────────────────────────────────────
    // Settings overlay — open / close
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleOpenSettings(ActionEvent ignoredEvent) {
        PlayerProfile profile = platformCore.getCurrentProfile();
        if (profile != null) {
            settingsOriginalUsername = profile.getUsername();
            settingsUsernameField.setText(settingsOriginalUsername);
            if (profile.getAvatarPath() != null) {
                settingsCurrentAvatarImage.setImage(new Image(
                        getClass().getResource(profile.getAvatarPath()).toExternalForm()));
            }
        }
        settingsEditMode = false;
        settingsUsernameField.setEditable(false);
        settingsUsernameField.setStyle(USERNAME_FIELD_INACTIVE);
        settingsEditBtn.setVisible(true);
        settingsEditBtn.setManaged(true);
        settingsSaveBtn.setVisible(false);
        settingsSaveBtn.setManaged(false);
        settingsNotification.setVisible(false);
        settingsNotification.setManaged(false);
        passwordPane.setVisible(false);
        passwordPane.setManaged(false);
        avatarPane.setVisible(false);
        avatarPane.setManaged(false);
        sidePaneDivider.setVisible(false);
        sidePaneDivider.setManaged(false);
        unsavedWarningOverlay.setVisible(false);
        unsavedWarningOverlay.setManaged(false);
        clearPasswordFields();

        settingsOverlay.setVisible(true);
        settingsOverlay.setManaged(true);
    }

    @FXML
    private void handleCloseSettings(ActionEvent ignoredEvent) {
        if (hasUnsavedUsernameChange()) showUnsavedWarning();
        else closeSettings();
    }

    @FXML
    private void handleOverlayDimClick(MouseEvent ignoredEvent) {
        if (hasUnsavedUsernameChange()) showUnsavedWarning();
        else closeSettings();
    }

    @FXML
    private void consumeOverlayClick(MouseEvent event) {
        event.consume();
    }

    private boolean hasUnsavedUsernameChange() {
        return settingsEditMode &&
                !settingsUsernameField.getText().equals(settingsOriginalUsername);
    }

    private void showUnsavedWarning() {
        unsavedWarningOverlay.setVisible(true);
        unsavedWarningOverlay.setManaged(true);
    }

    @FXML
    private void handleCloseWithoutSaving(ActionEvent ignoredEvent) {
        unsavedWarningOverlay.setVisible(false);
        unsavedWarningOverlay.setManaged(false);
        closeSettings();
    }

    @FXML
    private void handleContinueEditing(ActionEvent ignoredEvent) {
        unsavedWarningOverlay.setVisible(false);
        unsavedWarningOverlay.setManaged(false);
    }

    private void closeSettings() {
        settingsEditMode = false;
        settingsUsernameField.setEditable(false);
        settingsUsernameField.setStyle(USERNAME_FIELD_INACTIVE);
        settingsEditBtn.setVisible(true);
        settingsEditBtn.setManaged(true);
        settingsSaveBtn.setVisible(false);
        settingsSaveBtn.setManaged(false);
        passwordPane.setVisible(false);
        passwordPane.setManaged(false);
        avatarPane.setVisible(false);
        avatarPane.setManaged(false);
        sidePaneDivider.setVisible(false);
        sidePaneDivider.setManaged(false);
        settingsNotification.setVisible(false);
        settingsNotification.setManaged(false);
        unsavedWarningOverlay.setVisible(false);
        unsavedWarningOverlay.setManaged(false);
        settingsOverlay.setVisible(false);
        settingsOverlay.setManaged(false);
    }

    // ── Settings — pane-switch stubs (kept for FXML compat) ──────

    @FXML private void HandleOpenSettings(ActionEvent ignoredEvent) {
        if (dashboardPane != null) dashboardPane.setVisible(false);
        if (settingsPane  != null) settingsPane.setVisible(true);
    }

    @FXML private void HandleCloseSettings(ActionEvent ignoredEvent) {
        if (dashboardPane != null) dashboardPane.setVisible(true);
        if (settingsPane  != null) settingsPane.setVisible(false);
    }

    @FXML private void handleReturnToSettings(ActionEvent ignoredEvent) {
        if (unsavedSettingsPane != null) unsavedSettingsPane.setVisible(false);
    }

    @FXML private void handleDoNotSave(ActionEvent ignoredEvent) {
        if (dashboardPane    != null) dashboardPane.setVisible(true);
        if (settingsPane     != null) settingsPane.setVisible(false);
        if (unsavedSettingsPane != null) unsavedSettingsPane.setVisible(false);
    }

    // ─────────────────────────────────────────────────────────────
    // Settings — username edit / save
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleSettingsEdit(ActionEvent ignoredEvent) {
        settingsEditMode = true;
        settingsUsernameField.setEditable(true);
        settingsUsernameField.setStyle(USERNAME_FIELD_ACTIVE);
        settingsEditBtn.setVisible(false);
        settingsEditBtn.setManaged(false);
        settingsSaveBtn.setVisible(true);
        settingsSaveBtn.setManaged(true);
        settingsUsernameField.requestFocus();
        settingsUsernameField.selectAll();
    }

    @FXML
    private void handleSettingsSave(ActionEvent ignoredEvent) {
        String newUsername = settingsUsernameField.getText().trim();
        String oldUsername = platformCore.getCurrentUser();

        if (newUsername.equals(oldUsername)) {
            exitEditMode();
            return;
        }

        boolean success = platformCore.updateUsername(oldUsername, newUsername);
        if (success) {
            settingsOriginalUsername = newUsername;
            usernameLabel.setText(newUsername);
            welcomeLabel.setText("Welcome back, " + newUsername + "!");
            exitEditMode();
            showSettingsNotification("Username updated successfully!", true);
        } else {
            showSettingsNotification(
                    "Invalid username. Choose something else or keep old username.", false);
        }
    }

    private void exitEditMode() {
        settingsEditMode = false;
        settingsUsernameField.setEditable(false);
        settingsUsernameField.setStyle(USERNAME_FIELD_INACTIVE);
        settingsEditBtn.setVisible(true);
        settingsEditBtn.setManaged(true);
        settingsSaveBtn.setVisible(false);
        settingsSaveBtn.setManaged(false);
    }

    private void showSettingsNotification(String message, boolean isSuccess) {
        settingsNotificationLabel.setText(message);
        String textColor   = isSuccess ? "#40d090"              : "#ff6b6b";
        String bgColor     = isSuccess ? "rgba(30,144,40,0.25)" : "rgba(180,30,30,0.25)";
        String borderColor = isSuccess ? "rgba(50,200,80,0.4)"  : "rgba(255,80,80,0.4)";
        settingsNotificationLabel.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
        settingsNotification.setStyle(
                "-fx-background-color: " + bgColor + "; -fx-background-radius: 20; " +
                        "-fx-padding: 6 16; -fx-border-color: " + borderColor + "; " +
                        "-fx-border-radius: 20; -fx-border-width: 1;");
        settingsNotification.setVisible(true);
        settingsNotification.setManaged(true);

        if (settingsNotificationTimeline != null) settingsNotificationTimeline.stop();
        settingsNotificationTimeline = new Timeline(
                new KeyFrame(Duration.seconds(4), ev -> {
                    settingsNotification.setVisible(false);
                    settingsNotification.setManaged(false);
                }));
        settingsNotificationTimeline.play();
    }

    // ─────────────────────────────────────────────────────────────
    // Settings — password pane
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleTogglePasswordPane(ActionEvent ignoredEvent) {
        boolean wasVisible = passwordPane.isVisible();
        avatarPane.setVisible(false);
        avatarPane.setManaged(false);
        passwordPane.setVisible(!wasVisible);
        passwordPane.setManaged(!wasVisible);
        sidePaneDivider.setVisible(!wasVisible);
        sidePaneDivider.setManaged(!wasVisible);
        if (!passwordPane.isVisible()) clearPasswordFields();
    }

    @FXML
    private void handleSavePassword(ActionEvent ignoredEvent) {
        String oldPass  = oldPasswordField.getText();
        String newPass  = newPasswordField.getText();
        String confirm  = confirmPasswordField.getText();
        String username = platformCore.getCurrentUser();

        if (oldPass.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            passwordErrorLabel.setText("All fields are required.");
            return;
        }
        if (!platformCore.verifyPassword(username, oldPass)) {
            passwordErrorLabel.setText("Current password is incorrect.");
            return;
        }
        if (!newPass.equals(confirm)) {
            passwordErrorLabel.setText("New passwords do not match.");
            return;
        }
        boolean success = platformCore.updatePassword(username, newPass);
        if (success) {
            clearPasswordFields();
            passwordPane.setVisible(false);
            passwordPane.setManaged(false);
            sidePaneDivider.setVisible(false);
            sidePaneDivider.setManaged(false);
            showSettingsNotification("Password updated successfully!", true);
        } else {
            passwordErrorLabel.setText(
                    "Password must be 8+ chars with uppercase, lowercase, number, and special character.");
        }
    }

    private void clearPasswordFields() {
        if (oldPasswordField     != null) oldPasswordField.clear();
        if (newPasswordField     != null) newPasswordField.clear();
        if (confirmPasswordField != null) confirmPasswordField.clear();
        if (passwordErrorLabel   != null) passwordErrorLabel.setText("");
    }

    // ─────────────────────────────────────────────────────────────
    // Settings — avatar pane
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleToggleAvatarPane(ActionEvent ignoredEvent) {
        boolean wasVisible = avatarPane.isVisible();
        passwordPane.setVisible(false);
        passwordPane.setManaged(false);
        avatarPane.setVisible(!wasVisible);
        avatarPane.setManaged(!wasVisible);
        sidePaneDivider.setVisible(!wasVisible);
        sidePaneDivider.setManaged(!wasVisible);
    }

    @FXML private void handleSelectSettingsAvatar1(MouseEvent ignoredEvent) { applySettingsAvatar("/images/avatar1.png"); }
    @FXML private void handleSelectSettingsAvatar2(MouseEvent ignoredEvent) { applySettingsAvatar("/images/avatar2.png"); }
    @FXML private void handleSelectSettingsAvatar3(MouseEvent ignoredEvent) { applySettingsAvatar("/images/avatar3.png"); }
    @FXML private void handleSelectSettingsAvatar4(MouseEvent ignoredEvent) { applySettingsAvatar("/images/avatar4.png"); }

    private void applySettingsAvatar(String avatarPath) {
        PlayerProfile profile = platformCore.getCurrentProfile();
        if (profile == null) return;

        profile.setAvatarPath(avatarPath);
        platformCore.getUserRepository().save(profile);

        Image img = new Image(getClass().getResource(avatarPath).toExternalForm());
        settingsCurrentAvatarImage.setImage(img);
        userAvatarImage.setImage(img);

        settingsAvatarCard1.setStyle(SETTINGS_AVATAR_IDLE);
        settingsAvatarCard2.setStyle(SETTINGS_AVATAR_IDLE);
        settingsAvatarCard3.setStyle(SETTINGS_AVATAR_IDLE);
        settingsAvatarCard4.setStyle(SETTINGS_AVATAR_IDLE);
        switch (avatarPath) {
            case "/images/avatar1.png" -> settingsAvatarCard1.setStyle(SETTINGS_AVATAR_SELECTED);
            case "/images/avatar2.png" -> settingsAvatarCard2.setStyle(SETTINGS_AVATAR_SELECTED);
            case "/images/avatar3.png" -> settingsAvatarCard3.setStyle(SETTINGS_AVATAR_SELECTED);
            case "/images/avatar4.png" -> settingsAvatarCard4.setStyle(SETTINGS_AVATAR_SELECTED);
        }

        avatarNotificationLabel.setText("Avatar updated!");
        avatarNotification.setVisible(true);
        avatarNotification.setManaged(true);
        if (avatarNotificationTimeline != null) avatarNotificationTimeline.stop();
        avatarNotificationTimeline = new Timeline(
                new KeyFrame(Duration.seconds(3), ev -> {
                    avatarNotification.setVisible(false);
                    avatarNotification.setManaged(false);
                }));
        avatarNotificationTimeline.play();
    }

    // ─────────────────────────────────────────────────────────────
    // Friends overlay
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleOpenFriends(ActionEvent ignoredEvent) {
        if (addFriendField != null) addFriendField.clear();
        friendsNotification.setVisible(false);
        friendsNotification.setManaged(false);
        populateFriendsList();
        friendsOverlay.setVisible(true);
        friendsOverlay.setManaged(true);
    }

    @FXML
    private void handleCloseFriends(ActionEvent ignoredEvent) {
        closeFriends();
    }

    @FXML
    private void handleFriendsOverlayDimClick(MouseEvent ignoredEvent) {
        closeFriends();
    }

    private void closeFriends() {
        friendsOverlay.setVisible(false);
        friendsOverlay.setManaged(false);
    }

    @FXML
    private void handleAddFriend(ActionEvent ignoredEvent) {
        if (addFriendField == null) return;
        String target = addFriendField.getText().trim();
        if (target.isEmpty()) return;

        String error = platformCore.sendFriendRequest(target);
        if (error == null) {
            showFriendsNotification("Friend request sent to " + target + "!", true);
        } else {
            showFriendsNotification(error, false);
        }
        addFriendField.clear();
    }

    private void updateFriendsBadge() {
        if (friendsRequestBadge == null) return;
        boolean hasPending = !platformCore.getPendingFriendRequests().isEmpty();
        friendsRequestBadge.setVisible(hasPending);
        friendsRequestBadge.setManaged(false); // always unmanaged — overlay only
    }

    /**
     * Re-read the current user's profile from disk to detect cross-instance
     * changes (e.g. friend requests sent from another running instance).
     * Fires a toast + badge update for each newly detected request.
     */
    private void syncProfileFromDisk() {
        List<String> newRequests = platformCore.reloadProfileFromDisk();
        for (String sender : newRequests) {
            showToast("\uD83D\uDC64", "Friend Request", sender + " sent you a friend request!", "#40d090");
        }
    }

    private void populateFriendsList() {
        // Pending requests
        if (pendingRequestsList != null) {
            pendingRequestsList.getChildren().clear();
            for (String sender : platformCore.getPendingFriendRequests()) {
                pendingRequestsList.getChildren().add(buildPendingRow(sender));
            }
        }

        // Friends sorted into online / offline
        onlineFriendsList.getChildren().clear();
        offlineFriendsList.getChildren().clear();
        for (PlayerProfile friend : platformCore.getFriends()) {
            boolean online = platformCore.isPlayerOnline(friend.getUsername());
            if (online) {
                onlineFriendsList.getChildren().add(buildFriendRow(friend, true));
            } else {
                offlineFriendsList.getChildren().add(buildFriendRow(friend, false));
            }
        }

        updateFriendsBadge();
    }

    private HBox buildFriendRow(PlayerProfile friend, boolean online) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        String statusText = platformCore.getPlayerStatusText(friend.getUsername());
        boolean inGame = statusText.equals("In Game") || statusText.equals("In Queue") || statusText.equals("In Lobby");

        String rowBg      = online ? "rgba(10,25,45,0.8)" : "rgba(10,25,45,0.6)";
        String border     = online ? "rgba(77,166,255,0.15)" : "rgba(77,166,255,0.08)";
        String baseStyle  = "-fx-background-color: " + rowBg + "; -fx-background-radius: 10; " +
                "-fx-border-color: " + border + "; -fx-border-width: 1; " +
                "-fx-border-radius: 10; -fx-padding: 9 12;";
        row.setStyle(online ? baseStyle : baseStyle + " -fx-opacity: 0.7;");

        String avatarBorder = inGame ? "rgba(255,165,0,0.6)" : (online ? "rgba(64,208,144,0.6)" : "rgba(77,166,255,0.2)");
        StackPane avatar = new StackPane();
        avatar.setPrefSize(32, 32);
        avatar.setStyle("-fx-background-color: rgba(10,25,45,0.92); -fx-background-radius: 16; " +
                "-fx-border-color: " + avatarBorder +
                "; -fx-border-width: 1.5; -fx-border-radius: 16;");
        Label initial = new Label(friend.getUsername().substring(0, 1).toUpperCase());
        initial.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " +
                (online ? "#4da6ff" : "#7fbfff") + ";");
        avatar.getChildren().add(initial);

        VBox info = new VBox(2);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameLabel = new Label(friend.getUsername());
        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                (online ? "#e6f3ff" : "#b9d9ff") + ";");

        String statusColor = inGame ? "#ffa500" : (online ? "#40d090" : "#3d6080");
        String statusDot   = online ? "●" : "○";
        Label statusLabel = new Label(statusDot + " " + statusText);
        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + statusColor + ";");
        info.getChildren().addAll(nameLabel, statusLabel);

        Button removeBtn = new Button("Remove");
        removeBtn.setPrefSize(70, 28);
        removeBtn.setStyle("-fx-background-color: rgba(180,30,30,0.25); -fx-text-fill: #ff6b6b; " +
                "-fx-font-weight: bold; -fx-font-size: 10px; -fx-background-radius: 8; -fx-cursor: hand;");
        removeBtn.setOnAction(e -> {
            platformCore.removeFriend(friend.getUsername());
            populateFriendsList();
        });

        row.getChildren().addAll(avatar, info, removeBtn);
        return row;
    }

    private HBox buildPendingRow(String senderUsername) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: rgba(30,60,20,0.4); -fx-background-radius: 10; " +
                "-fx-border-color: rgba(64,208,144,0.25); -fx-border-width: 1; " +
                "-fx-border-radius: 10; -fx-padding: 9 12;");

        StackPane avatar = new StackPane();
        avatar.setPrefSize(32, 32);
        avatar.setStyle("-fx-background-color: rgba(10,25,45,0.92); -fx-background-radius: 16; " +
                "-fx-border-color: rgba(64,208,144,0.5); -fx-border-width: 1.5; -fx-border-radius: 16;");
        Label initial = new Label(senderUsername.substring(0, 1).toUpperCase());
        initial.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #40d090;");
        avatar.getChildren().add(initial);

        VBox info = new VBox(2);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameLabel = new Label(senderUsername);
        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #e6f3ff;");
        Label reqLabel = new Label("Wants to be friends");
        reqLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #40d090;");
        info.getChildren().addAll(nameLabel, reqLabel);

        Button acceptBtn = new Button("✓");
        acceptBtn.setPrefSize(32, 28);
        acceptBtn.setStyle("-fx-background-color: rgba(30,144,40,0.5); -fx-text-fill: #40d090; " +
                "-fx-font-weight: bold; -fx-font-size: 12px; -fx-background-radius: 8; -fx-cursor: hand;");
        acceptBtn.setOnAction(e -> {
            platformCore.acceptFriendRequest(senderUsername);
            populateFriendsList();
        });

        Button declineBtn = new Button("✕");
        declineBtn.setPrefSize(32, 28);
        declineBtn.setStyle("-fx-background-color: rgba(180,30,30,0.25); -fx-text-fill: #ff6b6b; " +
                "-fx-font-weight: bold; -fx-font-size: 12px; -fx-background-radius: 8; -fx-cursor: hand;");
        declineBtn.setOnAction(e -> {
            platformCore.declineFriendRequest(senderUsername);
            populateFriendsList();
        });

        row.getChildren().addAll(avatar, info, acceptBtn, declineBtn);
        return row;
    }

    private void showFriendsNotification(String message, boolean isSuccess) {
        friendsNotificationLabel.setText(message);
        String textColor   = isSuccess ? "#40d090"              : "#ff6b6b";
        String bgColor     = isSuccess ? "rgba(30,144,40,0.25)" : "rgba(180,30,30,0.25)";
        String borderColor = isSuccess ? "rgba(50,200,80,0.4)"  : "rgba(255,80,80,0.4)";
        friendsNotificationLabel.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
        friendsNotification.setStyle(
                "-fx-background-color: " + bgColor + "; -fx-background-radius: 20; " +
                        "-fx-padding: 6 16; -fx-border-color: " + borderColor + "; " +
                        "-fx-border-radius: 20; -fx-border-width: 1;");
        friendsNotification.setVisible(true);
        friendsNotification.setManaged(true);

        if (friendsNotificationTimeline != null) friendsNotificationTimeline.stop();
        friendsNotificationTimeline = new Timeline(
                new KeyFrame(Duration.seconds(4), ev -> {
                    friendsNotification.setVisible(false);
                    friendsNotification.setManaged(false);
                }));
        friendsNotificationTimeline.play();
    }

    // ─────────────────────────────────────────────────────────────
    // User card
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleUserCardClick(MouseEvent ignoredEvent) {
        boolean show = !userCardDetails.isVisible();
        userCardDetails.setVisible(show);
        userCardDetails.setManaged(show);
    }

    // ─────────────────────────────────────────────────────────────
    // Activity panel
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handleToggleActivity(ActionEvent ignoredEvent) {
        activityPanelVisible = !activityPanelVisible;
        rightPanel.setVisible(activityPanelVisible);
        rightPanel.setManaged(activityPanelVisible);
        activityToggleBtn.setText(activityPanelVisible ? "›" : "‹");
    }

    // ─────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void handlePlayClick(ActionEvent event) {
        String gameType = (String) ((Button) event.getSource()).getUserData();
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

    @FXML
    private void handleViewGameListClick(ActionEvent event) {
        switchScene(event, "/fxml/ViewGameList.fxml");
    }

    @FXML
    private void handleProfileClick(ActionEvent ignoredEvent) { /* TODO */ }

    @FXML
    private void handleLeaderboardClick(ActionEvent ignoredEvent) { /* TODO */ }

    @FXML
    private void handleLogOutClick(ActionEvent event) {
        if (friendsPollTimeline != null) friendsPollTimeline.stop();
        if (carouselTimeline != null) carouselTimeline.stop();
        platformCore.logout(platformCore.getCurrentUser());
        switchScene(event, "/fxml/LoginPage.fxml");
    }

    private void switchScene(ActionEvent event, String fxmlPath) {
        try {
            platformCore.getNotificationService().setListener(null);
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Match Found modal
    // ─────────────────────────────────────────────────────────────

    private void showMatchFoundModal(MatchFoundEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MatchFound.fxml"));
            Parent root = loader.load();
            MatchFoundController ctrl = loader.getController();

            String gameType = event.getGameType();
            List<PlayerData> players = new ArrayList<>();
            for (String id : event.getPlayers()) {
                String initials = id.length() >= 2 ? id.substring(0, 2).toUpperCase() : id.toUpperCase();
                players.add(new PlayerData(id, initials, false));
            }
            ctrl.initMatch(formatGameName(gameType), players);

            Stage matchStage = new Stage();
            matchStage.initModality(Modality.APPLICATION_MODAL);
            matchStage.setTitle("Match Found!");
            matchStage.setScene(new Scene(root));
            matchStage.setResizable(false);

            ctrl.setOnAccepted(() -> Platform.runLater(() -> {
                ctrl.stopCountdown();
                matchStage.close();
                navigateToGame(event, gameType);
            }));
            ctrl.setOnDeclined(() -> Platform.runLater(matchStage::close));
            ctrl.setOnTimeout(() -> Platform.runLater(matchStage::close));

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

            Stage stage = (Stage) usernameLabel.getScene().getWindow();
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

    private String formatGameName(String raw) {
        if (raw == null) return "—";
        return switch (raw.toLowerCase()) {
            case "tictactoe"   -> "Tic-Tac-Toe";
            case "connectfour" -> "Connect Four";
            case "checkers"    -> "Checkers";
            default            -> raw;
        };
    }

    private String gameColor(String raw) {
        if (raw == null) return "#7fbfff";
        return switch (raw.toLowerCase()) {
            case "tictactoe"   -> "#4da6ff";
            case "connectfour" -> "#ff8040";
            case "checkers"    -> "#40d090";
            default            -> "#7fbfff";
        };
    }

    private String rankShieldPath(String rank) {
        if (rank == null) return "/images/BronzeRankShield.png";
        return switch (rank.toLowerCase()) {
            case "gold"   -> "/images/GoldRankSheild.png";
            case "silver" -> "/images/SilverRankShield.png";
            default       -> "/images/BronzeRankShield.png";
        };
    }
}
