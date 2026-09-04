package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of a upload of manual assessments for a programming exercise.
 * <p>
 * If {@link #errors()} is non-empty the upload was rejected as a whole and nothing was stored (all-or-nothing). Otherwise the assessments were created and
 * {@link #numberOfCreatedAssessments()} together with {@link #createdStudentIdentifiers()} describe what was stored.
 * <p>
 * Invariant: {@code errors} is empty if and only if the upload succeeded; on success {@code numberOfCreatedAssessments == createdStudentIdentifiers.size()}, and on failure both
 * {@code numberOfCreatedAssessments == 0} and {@code createdStudentIdentifiers} is empty.
 *
 * @param numberOfCreatedAssessments the number of manual assessments that were created or overwritten
 * @param createdStudentIdentifiers  the student identifiers (in {@code <participationId>-<login>} form) for which an assessment was created or overwritten
 * @param errors                     the validation errors that caused the upload to be rejected; empty on success
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentUploadResultDTO(int numberOfCreatedAssessments, List<String> createdStudentIdentifiers, List<AssessmentUploadErrorDTO> errors) {

    /**
     * Creates the result of an assessment upload.
     * <p>
     * <b>Preconditions:</b> both lists and all their elements are non-{@code null}. For a successful result, {@code errors} is empty and
     * {@code numberOfCreatedAssessments} equals the number of identifiers. For a failed result, {@code errors} is non-empty, the number is zero, and the identifier list is empty.
     *
     * @throws IllegalArgumentException if a precondition is violated
     */
    public AssessmentUploadResultDTO {
        if (createdStudentIdentifiers == null || errors == null) {
            throw new IllegalArgumentException("Created student identifiers and errors must not be null");
        }
        if (createdStudentIdentifiers.stream().anyMatch(identifier -> identifier == null) || errors.stream().anyMatch(error -> error == null)) {
            throw new IllegalArgumentException("Created student identifiers and errors must not contain null elements");
        }
        if (errors.isEmpty() && numberOfCreatedAssessments != createdStudentIdentifiers.size()) {
            throw new IllegalArgumentException("The number of created assessments must match the number of student identifiers on success");
        }
        if (!errors.isEmpty() && (numberOfCreatedAssessments != 0 || !createdStudentIdentifiers.isEmpty())) {
            throw new IllegalArgumentException("A failed assessment upload must not report created assessments");
        }
        createdStudentIdentifiers = List.copyOf(createdStudentIdentifiers);
        errors = List.copyOf(errors);
    }

    /**
     * Creates a success result for a completed upload.
     * <p>
     * <b>Precondition:</b> {@code createdStudentIdentifiers} is non-{@code null} and lists exactly the participants for which an assessment was created or overwritten.
     * <p>
     * <b>Postcondition:</b> the returned result has no errors and its {@code numberOfCreatedAssessments} equals the size of the given list.
     *
     * @param createdStudentIdentifiers the identifiers for which an assessment was created or overwritten
     * @return a success result
     */
    public static AssessmentUploadResultDTO success(final List<String> createdStudentIdentifiers) {
        if (createdStudentIdentifiers == null) {
            throw new IllegalArgumentException("The created student identifiers must not be null");
        }
        return new AssessmentUploadResultDTO(createdStudentIdentifiers.size(), createdStudentIdentifiers, List.of());
    }

    /**
     * Creates a failure result for a rejected upload.
     * <p>
     * <b>Precondition:</b> {@code errors} is non-{@code null} and non-empty.
     * <p>
     * <b>Postcondition:</b> the returned result carries the given errors, reports zero created assessments and an empty identifier list (nothing was stored).
     *
     * @param errors the validation errors that caused the rejection
     * @return a failure result
     */
    public static AssessmentUploadResultDTO failure(final List<AssessmentUploadErrorDTO> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("The assessment upload errors must not be null or empty");
        }
        return new AssessmentUploadResultDTO(0, List.of(), errors);
    }
}
