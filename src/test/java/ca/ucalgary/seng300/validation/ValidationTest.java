package ca.ucalgary.seng300.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    @Test
    void success_isValid_returnsTrue() {
        Validation result = Validation.success();
        assertTrue(result.isValid());
    }

    @Test
    void success_errorCode_isNone() {
        Validation result = Validation.success();
        assertEquals(ValidationError.NONE, result.getErrorCode());
    }

    @Test
    void failure_isValid_returnsFalse() {
        Validation result = Validation.failure(ValidationError.TOO_SHORT, "Too short");
        assertFalse(result.isValid());
    }

    @Test
    void constructor_nullErrorCode_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Validation(null, "message")
        );
    }

    @Test
    void getErrorCode_returnsCorrectValue() {
        Validation result = Validation.failure(ValidationError.TOO_SHORT, "Too short");
        assertEquals(ValidationError.TOO_SHORT, result.getErrorCode());
    }

    @Test
    void getMessage_returnsCorrectMessage() {
        Validation result = Validation.failure(ValidationError.TOO_SHORT, "Too short");
        assertEquals("Too short", result.getMessage());
    }


    @Test
    void success_message_isValid() {
        Validation result = Validation.success();
        assertEquals("Valid", result.getMessage());
    }


    @Test
    void toString_containsErrorCode() {
        Validation result = Validation.failure(ValidationError.TOO_SHORT, "Too short");
        assertTrue(result.toString().contains("TOO_SHORT"));
    }

    @Test
    void toString_containsMessage() {
        Validation result = Validation.failure(ValidationError.TOO_SHORT, "Too short");
        assertTrue(result.toString().contains("Too short"));
    }

    @Test
    void constructor_nullMessage_doesNotThrow() {
        assertDoesNotThrow(() ->
                new Validation(ValidationError.NONE, null)
        );
    }

    @Test
    void failure_errorCode_and_message_matchGivenValues() {
        Validation result = Validation.failure(ValidationError.INVALID_FORMAT, "Invalid input");
        assertEquals(ValidationError.INVALID_FORMAT, result.getErrorCode());
        assertEquals("Invalid input", result.getMessage());
    }


}