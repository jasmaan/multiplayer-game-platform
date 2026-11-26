package ca.ucalgary.seng300.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


class ChatValidatorTest {

    private ChatValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ChatValidator();
    }

    @Test
    void validMessage_returnsSuccess() {
        Validation result = validator.validate("Hello everyone!");
        assertTrue(result.isValid());
        assertEquals(ValidationError.NONE, result.getErrorCode());
    }

    @Test
    void emptyMessage_returnsEmptyMessageError() {
        Validation result = validator.validate("");
        assertFalse(result.isValid());
        assertEquals(ValidationError.EMPTY_MESSAGE, result.getErrorCode());
    }

    @Test
    void nullMessage_returnsEmptyMessageError() {
        Validation result = validator.validate(null);
        assertFalse(result.isValid());
        assertEquals(ValidationError.EMPTY_MESSAGE, result.getErrorCode());
    }


    @Test
    void whitespaceOnlyMessage_returnsEmptyMessageError() {
        Validation result = validator.validate("   ");
        assertFalse(result.isValid());
        assertEquals(ValidationError.EMPTY_MESSAGE, result.getErrorCode());
    }

    @Test
    void messageTooLong_returnsTooLongError() {
        String longMessage = "a".repeat(201);
        Validation result = validator.validate(longMessage);
        assertFalse(result.isValid());
        assertEquals(ValidationError.TOO_LONG, result.getErrorCode());
    }

    @Test
    void messageExactlyMaxLength_returnsSuccess() {
        String maxMessage = "a".repeat(200);
        Validation result = validator.validate(maxMessage);
        assertTrue(result.isValid());
    }

    @Test
    void bannedWord_returnsBannedWordError() {
        Validation result = validator.validate("This contains badword1");
        assertFalse(result.isValid());
        assertEquals(ValidationError.BANNED_WORD, result.getErrorCode());
    }

    @Test
    void bannedWordUpperCase_returnsBannedWordError() {
        Validation result = validator.validate("This contains BADWORD1");
        assertFalse(result.isValid());
        assertEquals(ValidationError.BANNED_WORD, result.getErrorCode());
    }

    @Test
    void validMessageWithSpaces_returnsSuccess() {
        Validation result = validator.validate("  sup team  ");
        assertTrue(result.isValid());
        assertEquals(ValidationError.NONE, result.getErrorCode());
    }



}