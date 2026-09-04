package de.tum.cit.aet.artemis.assessment.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentUploadErrorType;

/**
 * A single validation error encountered while parsing a upload of manual assessments.
 * <p>
 * Invariant: {@code type} is never {@code null}; {@code identifier} and {@code detail} may be {@code null}.
 *
 * @param identifier the offending entry, i.e. the student identifier of the CSV row or the name of the text file (may be {@code null} for file-independent errors such as a
 *                       missing CSV)
 * @param type       the type of validation error
 * @param detail     optional additional context, e.g. the invalid value that was encountered
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentUploadErrorDTO(@Nullable String identifier, AssessmentUploadErrorType type, @Nullable String detail) {

    /**
     * Creates a validation error encountered during an assessment upload.
     * <p>
     * <b>Precondition:</b> {@code type} is non-{@code null}; {@code identifier} and {@code detail} may be {@code null}.
     *
     * @throws IllegalArgumentException if {@code type} is {@code null}
     */
    public AssessmentUploadErrorDTO {
        if (type == null) {
            throw new IllegalArgumentException("The assessment upload error type must not be null");
        }
    }

    /**
     * Creates a file-independent error that is not tied to a specific CSV row or text file.
     * <p>
     * <b>Precondition:</b> {@code type} is non-{@code null}.
     *
     * @param type the type of validation error
     * @return the error
     */
    public static AssessmentUploadErrorDTO of(final AssessmentUploadErrorType type) {
        return new AssessmentUploadErrorDTO(null, type, null);
    }

    /**
     * Creates an error without additional detail.
     * <p>
     * <b>Precondition:</b> {@code type} is non-{@code null}.
     *
     * @param identifier the offending entry, or {@code null} for a file-independent error
     * @param type       the type of validation error
     * @return the error
     */
    public static AssessmentUploadErrorDTO of(@Nullable final String identifier, final AssessmentUploadErrorType type) {
        return new AssessmentUploadErrorDTO(identifier, type, null);
    }

    /**
     * Creates an error with additional detail.
     * <p>
     * <b>Precondition:</b> {@code type} is non-{@code null}.
     *
     * @param identifier the offending entry, or {@code null} for a file-independent error
     * @param type       the type of validation error
     * @param detail     additional context, e.g. the invalid value that was encountered
     * @return the error
     */
    public static AssessmentUploadErrorDTO of(@Nullable final String identifier, final AssessmentUploadErrorType type, @Nullable final String detail) {
        return new AssessmentUploadErrorDTO(identifier, type, detail);
    }
}
