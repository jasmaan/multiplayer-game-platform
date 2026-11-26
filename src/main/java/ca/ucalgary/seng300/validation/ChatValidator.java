package ca.ucalgary.seng300.validation;

import java.util.List;

/**
 * ChatValidator validates chat messages before they are broadcast.
 * Checks message length, banned words, and empty input.
 */
public class ChatValidator {
    // Placeholder banned words used to demonstrate filtering.
    // Show that it has the feature to filter certain banned words
    private static final List<String> BANNED_WORDS = List.of(
            "badword1", "badword2", "badword3"

    );

    private static final int MAX_LENGTH = 200; // Maximum allowed characters per message

    /**
     * Validates a chat message.
     * @param message the message to validate
     * @return Validation result with errorCode = NONE if valid
     */
    public Validation validate(String message) {
        if (!isNotEmpty(message)) {
            return Validation.failure(ValidationError.EMPTY_MESSAGE, "Message cannot be empty.");
        }
        if (!hasValidLength(message)) {
            return Validation.failure(ValidationError.TOO_LONG, "Message exceeds " + MAX_LENGTH + " characters.");
        }
        if (containsBannedWords(message)) {
            return Validation.failure(ValidationError.BANNED_WORD, "Message contains a banned word.");
        }
        return Validation.success();
    }

    private boolean isNotEmpty(String message) {
        return message != null && !message.trim().isEmpty();
    }

    private boolean hasValidLength(String message) {
        return message.length() <= MAX_LENGTH;
    }

    private boolean containsBannedWords(String message) {
        String lowerMessage = message.toLowerCase();
        for (String word : BANNED_WORDS) {
            if (lowerMessage.contains(word)) {
                return true;
            }
        }
        return false;
    }
}