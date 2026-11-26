package ca.ucalgary.seng300.validation;

public enum ValidationError {

    NONE,

    // Password validation
    TOO_SHORT,
    MISSING_UPPERCASE,
    MISSING_LOWERCASE,
    MISSING_DIGIT,
    MISSING_SPECIAL_CHAR,

    // Game validation
    NOT_YOUR_TURN,
    OUT_OF_BOUNDS,
    CELL_OCCUPIED,
    COLUMN_FULL,
    INVALID_FORMAT,
    GAME_NOT_ACTIVE,
    UNAUTHORIZED_PLAYER,
    INVALID_MOVE,

    // Auth validation
    USERNAME_TAKEN,
    INVALID_CREDENTIALS,
    EMAIL_FORMAT_INVALID,
    ACCOUNT_NOT_FOUND,

    // Chat validation
    EMPTY_MESSAGE,
    TOO_LONG,
    BANNED_WORD
}