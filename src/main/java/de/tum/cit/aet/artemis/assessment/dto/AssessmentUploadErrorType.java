package de.tum.cit.aet.artemis.assessment.dto;

/**
 * Types of validation errors that can occur while parsing a upload of manual assessments for a programming exercise.
 * <p>
 * The upload is processed all-or-nothing: if any entry produces one of these errors, nothing is stored and all collected errors are reported back to the instructor.
 */
public enum AssessmentUploadErrorType {

    /**
     * The uploaded zip file does not contain an {@code assessment-scores.csv} file.
     */
    MISSING_CSV,

    /**
     * The uploaded zip file contains more than one {@code assessment-scores.csv} file.
     */
    MULTIPLE_CSV,

    /**
     * The {@code assessment-scores.csv} file could not be parsed or does not contain any data rows.
     */
    EMPTY_CSV,

    /**
     * The {@code assessment-scores.csv} file does not contain the mandatory {@code Overall points} column.
     */
    MISSING_OVERALL_POINTS_COLUMN,

    /**
     * A row of the CSV file does not contain a student identifier in its first column.
     */
    MISSING_IDENTIFIER,

    /**
     * A student identifier is not in the expected {@code <participationId>-<login>} format (e.g. the leading participation id is not numeric).
     */
    INVALID_IDENTIFIER_FORMAT,

    /**
     * The same student identifier appears in more than one row of the CSV file.
     */
    DUPLICATE_IDENTIFIER,

    /**
     * No participation with the given participation id exists.
     */
    PARTICIPATION_NOT_FOUND,

    /**
     * A participation with the given participation id exists, but it belongs to a different exercise.
     */
    PARTICIPATION_WRONG_EXERCISE,

    /**
     * The login part of the identifier does not match the participant (student login or team short name) of the resolved participation.
     */
    IDENTIFIER_MISMATCH,

    /**
     * The value of the {@code Overall points} column is missing, not a number, or negative.
     */
    INVALID_POINTS,

    /**
     * No {@code .txt} file in the zip matches the student identifier of a CSV row.
     */
    MISSING_TEXT_FILE,

    /**
     * A {@code .txt} file in the zip does not match any student identifier of the CSV rows.
     */
    UNMATCHED_TEXT_FILE
}
