package ca.ucalgary.seng300.ui.controllers;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.validation.ChatValidator;
import ca.ucalgary.seng300.validation.Validation;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class ChatController {

    @FXML private VBox messageContainer;
    @FXML private TextField messageInput;
    @FXML private ScrollPane scrollPane;
    @FXML private Label errorLabel;

    private PlatformCore platformCore;
    private String sessionId;
    private final ChatValidator chatValidator = new ChatValidator();

    public void setPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;

        // Register callback so incoming messages appear in the chat
        if (platformCore != null) {
            platformCore.setChatMessageCallback(sessionId, event -> {
                String sender = event.getSenderId();
                boolean isCurrentUser = sender.equals(platformCore.getCurrentUser());
                addMessage(sender, event.getText(), isCurrentUser);
            });
        }
    }

    @FXML
    public void initialize() {
        // Auto scroll to bottom when new messages are added
        messageContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            scrollPane.setVvalue(1.0);
        });

        // Allow sending with Enter key
        messageInput.setOnAction(event -> handleSendClick(null));

        // Add a welcome message
        addSystemMessage("Welcome to in-game chat!");
    }

    @FXML
    public void handleSendClick(ActionEvent actionEvent) {
        String text = messageInput.getText().trim();

        // Validate message using ChatValidator
        Validation result = chatValidator.validate(text);
        if (!result.isValid()) {
            showError(result.getMessage());
            return;
        }

        clearError();

        // Send via PlatformCore if available, otherwise show locally
        if (platformCore != null && sessionId != null) {
            platformCore.sendChatMessage(sessionId, platformCore.getCurrentUser(), text);
        } else {
            addMessage("You", text, true);
        }

        messageInput.clear();
    }

    @FXML
    public void handleCloseClick(ActionEvent actionEvent) {
        // Unregister chat callback when closing
        if (platformCore != null && sessionId != null) {
            platformCore.setChatMessageCallback(sessionId, null);
        }
        messageContainer.getScene().getWindow().hide();
    }

    private void addMessage(String sender, String text, boolean isCurrentUser) {
        VBox bubble = new VBox(3);
        bubble.setMaxWidth(220);

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #7fbfff;");

        TextFlow textFlow = new TextFlow(new Text(text));
        textFlow.setStyle(
                isCurrentUser
                        ? "-fx-background-color: #1e90ff; -fx-background-radius: 10; -fx-padding: 8 12 8 12;"
                        : "-fx-background-color: #0f2942; -fx-background-radius: 10; -fx-padding: 8 12 8 12;"
        );
        ((Text) textFlow.getChildren().get(0)).setFill(Color.WHITE);
        textFlow.setMaxWidth(220);

        bubble.getChildren().addAll(senderLabel, textFlow);

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(isCurrentUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageContainer.getChildren().add(wrapper);
    }

    private void addSystemMessage(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #7fbfff; -fx-font-size: 11px; -fx-font-style: italic;");
        HBox wrapper = new HBox(label);
        wrapper.setAlignment(Pos.CENTER);
        messageContainer.getChildren().add(wrapper);
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 11px;");
        }
    }

    private void clearError() {
        if (errorLabel != null) {
            errorLabel.setText("");
        }
    }

    // Call this from another controller to add incoming messages
    public void receiveMessage(String sender, String text) {
        addMessage(sender, text, false);
    }
}