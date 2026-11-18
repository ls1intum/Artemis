package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Read DTO for the "text submission without assessment" endpoint. It mirrors the previous entity payload shape where the
 * client resolves the {@link de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation} via
 * {@code submission.participation}; the participation carries the exercise and the locked submission (with its results
 * including the assessor) needed by the tutor assessment editor.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextSubmissionWithoutAssessmentDTO(Long id, Boolean submitted, ZonedDateTime submissionDate, String text, Language language, TextParticipationDTO participation)
        implements Serializable {

    /**
     * Builds the DTO from the locked submission and the already-prepared participation DTO.
     *
     * @param submission    the locked text submission (already hidden/filtered by the caller)
     * @param participation the participation DTO carrying the exercise and submission graph
     * @return the converted DTO, or {@code null} if the submission is {@code null}
     */
    public static TextSubmissionWithoutAssessmentDTO of(TextSubmission submission, TextParticipationDTO participation) {
        if (submission == null) {
            return null;
        }
        return new TextSubmissionWithoutAssessmentDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), submission.getText(), submission.getLanguage(),
                participation);
    }
}
