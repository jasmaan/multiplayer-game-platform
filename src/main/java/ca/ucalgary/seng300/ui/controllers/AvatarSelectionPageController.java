package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.core.PlayerProfile;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class AvatarSelectionPageController {

    private static final String DEFAULT_AVATAR  = "/images/gameDinoLogo.png";
    private static final String AVATAR_1        = "/images/avatar1.png";
    private static final String AVATAR_2        = "/images/avatar2.png";
    private static final String AVATAR_3        = "/images/avatar3.png";
    private static final String AVATAR_4        = "/images/avatar4.png";

    private static final String CARD_SELECTED =
            "-fx-background-color: rgba(77,166,255,0.12); -fx-background-radius: 16; " +
            "-fx-border-color: rgba(77,166,255,0.6); -fx-border-width: 2.5; " +
            "-fx-border-radius: 16; -fx-padding: 18 16; -fx-cursor: hand;";
    private static final String CARD_IDLE =
            "-fx-background-color: rgba(10,25,45,0.8); -fx-background-radius: 16; " +
            "-fx-border-color: rgba(77,166,255,0.15); -fx-border-width: 1.5; " +
            "-fx-border-radius: 16; -fx-padding: 18 16; -fx-cursor: hand;";

    private PlatformCore platformCore;
    private String selectedAvatar = AVATAR_1;   // default pre-selection

    @FXML private VBox  card1;
    @FXML private VBox  card2;
    @FXML private VBox  card3;
    @FXML private VBox  card4;
    @FXML private Label check1;
    @FXML private Label check2;
    @FXML private Label check3;
    @FXML private Label check4;

    public void setPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
    }

    // ── Selection handlers ────────────────────────────────────────

    @FXML private void handleSelectAvatar1() { select(AVATAR_1, card1, check1); }
    @FXML private void handleSelectAvatar2() { select(AVATAR_2, card2, check2); }
    @FXML private void handleSelectAvatar3() { select(AVATAR_3, card3, check3); }
    @FXML private void handleSelectAvatar4() { select(AVATAR_4, card4, check4); }

    private void select(String avatarPath, VBox activeCard, Label activeCheck) {
        selectedAvatar = avatarPath;

        VBox[]  cards  = {card1, card2, card3, card4};
        Label[] checks = {check1, check2, check3, check4};
        for (int i = 0; i < cards.length; i++) {
            cards[i].setStyle(CARD_IDLE);
            checks[i].setVisible(false);
            checks[i].setManaged(false);
        }

        activeCard.setStyle(CARD_SELECTED);
        activeCheck.setVisible(true);
        activeCheck.setManaged(true);
    }

    // ── Navigation ────────────────────────────────────────────────

    @FXML
    private void handleConfirm(ActionEvent event) {
        saveAndProceed(selectedAvatar, event);
    }

    @FXML
    private void handleSkip(ActionEvent event) {
        saveAndProceed(DEFAULT_AVATAR, event);
    }

    private void saveAndProceed(String avatarPath, ActionEvent event) {
        PlayerProfile profile = platformCore.getCurrentProfile();
        if (profile != null) {
            profile.setAvatarPath(avatarPath);
            platformCore.getUserRepository().save(profile);
        }
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
}
