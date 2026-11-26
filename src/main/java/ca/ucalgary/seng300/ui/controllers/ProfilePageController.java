package ca.ucalgary.seng300.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class ProfilePageController {
    @FXML
    private Label usernameLabel;

    @FXML
    private Label firstNameLabel;

    @FXML
    private Label lastNameLabel;

    @FXML
    private Label rankLabel;

    @FXML
    private Label matchesPlayedLabel;

    @FXML
    private Label winsLabel;

    @FXML
    private Button returnButton;

    @FXML
    public void handleReturnButtonClick(ActionEvent actionEvent) {
        // TODO: handle returning to previous page
    }
}
