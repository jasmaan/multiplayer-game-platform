package ca.ucalgary.seng300.ui.controllers;
import ca.ucalgary.seng300.client.NetworkClient;
import ca.ucalgary.seng300.core.PlatformCore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Hyperlink;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Map;
import javafx.scene.control.Label;


public class LoginPageController {

    @FXML
    private TextField username;

    @FXML
    private PasswordField password;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    private PlatformCore platformCore;

    public void setPlatformCore(PlatformCore platformCore){
        this.platformCore = platformCore;

    }
    private PlatformCore getPlatformCore(){
        if (platformCore == null) {
            platformCore = new PlatformCore();
        }
        return platformCore;
    }
    @FXML
    public Hyperlink createAccountHyperlink;

    @FXML
    private void handleLoginClick(ActionEvent event) {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        String enteredUsername = username.getText().trim();
        String enteredPassword = password.getText();

        if (enteredUsername.isEmpty() || enteredPassword.isEmpty()) {
            errorLabel.setText("Please enter both username and password.");
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            return;
        }

        // Try server login first; if server is unreachable, fall back to local.
        NetworkClient nc = NetworkClient.getInstance();
        Map<String, Object> serverResp = nc.login(enteredUsername, enteredPassword);
        if (serverResp != null) {
            String token    = (String) serverResp.get("token");
            String playerId = (String) serverResp.get("playerId");
            nc.connect(token, playerId);
        }

        boolean loginSucceeded = getPlatformCore().login(enteredUsername, enteredPassword);

        if (!loginSucceeded) {
            nc.disconnect(); // undo network connection if local auth fails
            errorLabel.setText("Invalid username or password.");
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            return;
        }
        switchScene(event, "/fxml/DashboardPage.fxml");
    }
    @FXML
    private void handleCreateAccountClick(ActionEvent event) {
        switchScene(event, "/fxml/RegistrationPage.fxml");
    }
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Login Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    private void switchScene(ActionEvent event, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Object controller = loader.getController();

            if (controller instanceof DashboardPageController dashboardController) {
                dashboardController.setPlatformCore(getPlatformCore());
            } else if (controller instanceof RegistrationPageController registrationController) {
                registrationController.setPlatformCore(getPlatformCore());
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        }
        catch(IOException e) {
            showError("Unable to load page: " + fxmlPath);
        }

    }

}
