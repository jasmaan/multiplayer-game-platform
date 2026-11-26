package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.core.PlayerProfile;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class SettingsPageController {
    private static final String STYLE_DEFAULT =
            "-fx-background-color: rgba(77,166,255,0.12); -fx-background-radius: 10;" +
                    " -fx-border-color: rgba(77,166,255,0.25); -fx-border-radius: 10;" +
                    " -fx-border-width: 1; -fx-cursor: hand; -fx-padding: 6;";

    private static final String STYLE_SELECTED =
            "-fx-background-color: rgba(77,166,255,0.30); -fx-background-radius: 10;" +
                    " -fx-border-color: #4da6ff; -fx-border-radius: 10;" +
                    " -fx-border-width: 2; -fx-cursor: hand; -fx-padding: 6;";

    private PlatformCore platformCore;
    private String selectedAvatarPath;
    private final ToggleGroup avatarToggleGroup = new ToggleGroup();

    public void setPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
    }

    @FXML private VBox avatarPane;
    @FXML private Button changeEmailButton;
    @FXML private Button changePasswordButton;
    @FXML private Button logOutButton;
    @FXML private Button returnButton;

    @FXML private TextField settingsUsernameField;
    @FXML private Button editUsernameButton;
    @FXML private Button saveUsernameButton;
    @FXML private HBox settingsNotification;
    @FXML private Label settingsNotificationLabel;

    @FXML private ToggleButton avatarButton1;
    @FXML private ToggleButton avatarButton2;
    @FXML private ToggleButton avatarButton3;
    @FXML private ToggleButton avatarButton4;

    @FXML
    public void initialize() {
        avatarPane.setVisible(false);
        avatarPane.setManaged(false);

        avatarButton1.setToggleGroup(avatarToggleGroup);
        avatarButton2.setToggleGroup(avatarToggleGroup);
        avatarButton3.setToggleGroup(avatarToggleGroup);
        avatarButton4.setToggleGroup(avatarToggleGroup);

        avatarToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            for (Toggle t : avatarToggleGroup.getToggles()) {
                ((ToggleButton) t).setStyle(t == newToggle ? STYLE_SELECTED : STYLE_DEFAULT);
            }
        });
    }

    @FXML
    public void handleSettingsEdit(ActionEvent actionEvent) {}

    @FXML
    public void handleSettingsSave(ActionEvent actionEvent) {}

    @FXML
    public void handleSelectSettingsAvatar1(ActionEvent actionEvent) {
        selectedAvatarPath = "/images/avatar1.png";
    }

    @FXML
    public void handleSelectSettingsAvatar2(ActionEvent actionEvent) {
        selectedAvatarPath = "/images/avatar2.png";
    }

    @FXML
    public void handleSelectSettingsAvatar3(ActionEvent actionEvent) {
        selectedAvatarPath = "/images/avatar3.png";
    }

    @FXML
    public void handleSelectSettingsAvatar4(ActionEvent actionEvent) {
        selectedAvatarPath = "/images/avatar4.png";
    }

    @FXML
    public void applySettingsAvatar(ActionEvent actionEvent) {
        if (platformCore == null || selectedAvatarPath == null) return;
        PlayerProfile profile = platformCore.getCurrentProfile();
        if (profile == null) return;
        profile.setAvatarPath(selectedAvatarPath);
        platformCore.getUserRepository().save(profile);
        avatarPane.setVisible(false);
        avatarPane.setManaged(false);
    }

    @FXML
    public void handleChangeAvatarClick(ActionEvent actionEvent) {
        avatarPane.setVisible(true);
        avatarPane.setManaged(true);
    }

    @FXML
    public void handleCancelAvatarClick(ActionEvent actionEvent) {
        avatarPane.setVisible(false);
        avatarPane.setManaged(false);
    }

    @FXML
    public void handleChangeEmailClick(ActionEvent actionEvent) {}

    @FXML
    public void handleChangePasswordClick(ActionEvent actionEvent) {
    }

    @FXML
    public void handleLogOutClick(ActionEvent actionEvent) {}

    @FXML
    public void handleReturnButtonClick(ActionEvent actionEvent) {}
}