package ca.ucalgary.seng300.validation;

/**
 * Validation class represents the result of a validation check.
 * It standardizes how success or failure is returned using an error code and message.
 */
public class Validation {

    private final ValidationError errorCode;
    private final String message;

    /**
     * Constructor
     * @param errorCode the validation error code
     * @param message the validation message
     */
    public Validation(ValidationError errorCode, String message) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode cannot be null");
        }
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * Check if validation passed.
     * @return true if errorCode is NONE
     */
    public boolean isValid() {
        return errorCode == ValidationError.NONE;
    }

    /**
     * @return the validation error code
     */
    public ValidationError getErrorCode() {
        return errorCode;
    }

    /**
     * @return the validation message
     */
    public String getMessage() {
        return message;
    }


    /**
     * Creates a successful validation result.
     * @return Validation with errorCode = NONE
     */
    public static Validation success() {
        return new Validation(ValidationError.NONE, "Valid");
    }

    /**
     * Creates a failed validation result with a specific error.
     * @param errorCode the reason for failure
     * @param message human-readable description of the failure
     * @return Validation with the given errorCode
     */
    public static Validation failure(ValidationError errorCode, String message) {
        return new Validation(errorCode, message);
    }

    /**
     * Returns a readable string for debugging.
     */
    @Override
    public String toString() {
        return "Validation{" +
                "errorCode=" + errorCode +
                ", message='" + message + '\'' +
                '}';
    }

}