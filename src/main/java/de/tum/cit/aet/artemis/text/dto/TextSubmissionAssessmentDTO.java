package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Read DTO for a {@link TextSubmission} as exposed in the tutor assessment views (for-assessment, without-assessment, text-editor).
 * <p>
 * The {@code results} use the assessment {@link ResultDTO} so the assessor is carried to the client. The caller is expected to
 * have already locked / filtered / hidden details on the entity before invoking the factory.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextSubmissionAssessmentDTO(Long id, Boolean submitted, ZonedDateTime submissionDate, String text, Language language, List<TextBlockDTO> blocks,
        List<ResultDTO> results) implements Serializable {

    /**
     * Converts a {@link TextSubmission} into a {@link TextSubmissionAssessmentDTO}.
     *
     * @param submission to convert
     * @return the converted DTO, or {@code null} if the submission is {@code null}
     */
    public static TextSubmissionAssessmentDTO of(TextSubmission submission) {
        if (submission == null) {
            return null;
        }

        List<TextBlockDTO> blocks = null;
        if (Hibernate.isInitialized(submission.getBlocks()) && submission.getBlocks() != null) {
            blocks = submission.getBlocks().stream().filter(Objects::nonNull).map(TextBlockDTO::of).toList();
        }

        List<ResultDTO> results = null;
        if (Hibernate.isInitialized(submission.getResults()) && submission.getResults() != null) {
            results = submission.getResults().stream().filter(Objects::nonNull).map(ResultDTO::of).toList();
        }

        return new TextSubmissionAssessmentDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), submission.getText(), submission.getLanguage(), blocks,
                results);
    }
}
