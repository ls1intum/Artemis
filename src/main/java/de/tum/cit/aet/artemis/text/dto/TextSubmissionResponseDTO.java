package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationDTO;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Read DTO for a {@link TextSubmission} returned to the client.
 * <p>
 * Lazy associations (results, blocks, participation) are guarded with {@link Hibernate#isInitialized} so uninitialized
 * proxies map to {@code null}/empty rather than triggering a lazy load. The caller is expected to have trimmed/filtered
 * the entity (e.g. removed null results) before invoking the factory.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextSubmissionResponseDTO(Long id, String submissionExerciseType, String text, Language language, Boolean submitted, ZonedDateTime submissionDate,
        SubmissionType type, Boolean exampleSubmission, StudentParticipationDTO participation, List<ResultDTO> results, List<TextBlockDTO> blocks) implements Serializable {

    /**
     * Converts a {@link TextSubmission} into a {@link TextSubmissionResponseDTO} without the participation's student.
     *
     * @param submission to convert
     * @return the converted DTO, or {@code null} if the submission is {@code null}
     */
    public static TextSubmissionResponseDTO of(TextSubmission submission) {
        return of(submission, false);
    }

    /**
     * Converts a {@link TextSubmission} into a {@link TextSubmissionResponseDTO}.
     *
     * @param submission     to convert
     * @param includeStudent whether the participation's student should be included
     * @return the converted DTO, or {@code null} if the submission is {@code null}
     */
    public static TextSubmissionResponseDTO of(TextSubmission submission, boolean includeStudent) {
        if (submission == null) {
            return null;
        }

        List<ResultDTO> results = null;
        if (Hibernate.isInitialized(submission.getResults()) && submission.getResults() != null) {
            results = submission.getResults().stream().filter(java.util.Objects::nonNull).map(ResultDTO::of).toList();
        }

        List<TextBlockDTO> blocks = null;
        if (Hibernate.isInitialized(submission.getBlocks()) && submission.getBlocks() != null) {
            blocks = submission.getBlocks().stream().filter(java.util.Objects::nonNull).map(TextBlockDTO::of).toList();
        }

        StudentParticipationDTO participation = null;
        if (Hibernate.isInitialized(submission.getParticipation()) && submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            participation = StudentParticipationDTO.of(studentParticipation, includeStudent);
        }

        return new TextSubmissionResponseDTO(submission.getId(), submission.getSubmissionExerciseType(), submission.getText(), submission.getLanguage(), submission.isSubmitted(),
                submission.getSubmissionDate(), submission.getType(), submission.isExampleSubmission(), participation, results, blocks);
    }
}
