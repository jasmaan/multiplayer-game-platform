package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.core.PlatformCore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.IOException;

public class RegistrationPageController
{
    @FXML private TextField firstName;
    @FXML private TextField lastName;
    @FXML private PasswordField password;
    @FXML private TextField passwordVisible;
    @FXML private PasswordField confirmPassword;
    @FXML private Label errorLabel;
    @FXML private Button viewPasswordButton;

    private boolean passwordShowing = false;
    private PlatformCore platformCore;

    public void setPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
    }

    // Default field style matching dark theme
    private static final String DEFAULT_STYLE = "-fx-background-color: #0f2942; -fx-text-fill: white; -fx-prompt-text-fill: #7fbfff; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: rgba(77,166,255,0.25); -fx-padding: 0 12 0 12;";
    private static final String ERROR_STYLE = "-fx-background-color: #0f2942; -fx-text-fill: white; -fx-prompt-text-fill: #7fbfff; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: red; -fx-border-width: 1.5; -fx-padding: 0 12 0 12;";

    @FXML
    public void initialize()
    {
        // Keep passwordVisible and password in sync
        passwordVisible.setManaged(false);
        passwordVisible.setVisible(false);
        passwordVisible.textProperty().bindBidirectional(password.textProperty());
    }

    @FXML
    public void handleSignUpClick(ActionEvent actionEvent)
    {
        clearErrors();

        String first = firstName.getText().trim();
        String last = lastName.getText().trim();
        String pass = password.getText();
        String confirm = confirmPassword.getText();

        // Check first name not empty
        if (first.isEmpty())
        {
            setError(firstName, "First name is required");
            return;
        }

        // Check last name not empty
        if (last.isEmpty())
        {
            setError(lastName, "Last name is required");
            return;
        }

        // Check password meets all requirements
        if (pass.length() < 8)
        {
            setError(password, "Password too short (min 8 characters)");
            return;
        }
        if (!pass.matches(".*[A-Z].*"))
        {
            setError(password, "Password must contain an uppercase letter");
            return;
        }
        if (!pass.matches(".*[a-z].*"))
        {
            setError(password, "Password must contain a lowercase letter");
            return;
        }
        if (!pass.matches(".*\\d.*"))
        {
            setError(password, "Password must contain a number");
            return;
        }
        if (!pass.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*"))
        {
            setError(password, "Password must contain a special character");
            return;
        }

        // Check passwords match
        if (!pass.equals(confirm))
        {
            setError(confirmPassword, "Passwords do not match");
            return;
        }

        // All checks pass
        String newUsername = platformCore.registerPlayer(first, last, pass);
        if (newUsername != null) {
            platformCore.login(newUsername, pass);
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AvatarSelectionPage.fxml"));
            Parent root = loader.load();
            AvatarSelectionPageController controller = loader.getController();
            controller.setPlatformCore(platformCore);
            Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleSignInClick(ActionEvent actionEvent)
    {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginPage.fxml"));
            Parent root = loader.load();
            LoginPageController controller = loader.getController();
            controller.setPlatformCore(platformCore);
            Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleViewPassword(ActionEvent actionEvent)
    {
        passwordShowing = !passwordShowing;

        if (passwordShowing)
        {
            // Show plain text field, hide password field
            passwordVisible.setVisible(true);
            passwordVisible.setManaged(true);
            password.setVisible(false);
            password.setManaged(false);
            viewPasswordButton.setText("🙈");
        }
        else
        {
            // Show password field, hide plain text field
            password.setVisible(true);
            password.setManaged(true);
            passwordVisible.setVisible(false);
            passwordVisible.setManaged(false);
            viewPasswordButton.setText("👁");
        }
    }

    private void setError(Control field, String message)
    {
        field.setStyle(ERROR_STYLE);
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 11px;");
    }

    private void clearErrors()
    {
        firstName.setStyle(DEFAULT_STYLE);
        lastName.setStyle(DEFAULT_STYLE);
        password.setStyle(DEFAULT_STYLE);
        confirmPassword.setStyle(DEFAULT_STYLE);
        errorLabel.setText("");
    }
}