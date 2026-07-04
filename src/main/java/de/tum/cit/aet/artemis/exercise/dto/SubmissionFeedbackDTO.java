package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;

/**
 * DTO representing feedback nested in a submission response.
 *
 * @param id                  the feedback identifier
 * @param text                the short feedback text, if available
 * @param detailText          the detailed feedback text, if available
 * @param hasLongFeedbackText whether the complete detail text is stored separately
 * @param reference           the assessed element reference, if available
 * @param credits             the awarded credits, if available
 * @param positive            whether the feedback is positive, if specified
 * @param type                the feedback type, if available
 * @param visibility          the feedback visibility, if available
 * @param testCaseName        the initialized programming test-case name, if available
 * @param gradingInstruction  the initialized grading instruction, if available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionFeedbackDTO(Long id, @Nullable String text, @Nullable String detailText, boolean hasLongFeedbackText, @Nullable String reference, @Nullable Double credits,
        @Nullable Boolean positive, @Nullable FeedbackType type, @Nullable Visibility visibility, @Nullable String testCaseName, @Nullable GradingInstructionDTO gradingInstruction)
        implements Serializable {

    /**
     * Maps initialized feedback data without exposing entity back-references.
     *
     * @param feedback the feedback to map
     * @return the submission feedback DTO
     */
    public static SubmissionFeedbackDTO of(Feedback feedback) {
        Objects.requireNonNull(feedback, "The feedback must be set");

        String testCaseName = feedback.getTestCase() != null && Hibernate.isInitialized(feedback.getTestCase()) ? feedback.getTestCase().getTestName() : null;
        GradingInstructionDTO gradingInstruction = feedback.getGradingInstruction() != null && Hibernate.isInitialized(feedback.getGradingInstruction())
                ? GradingInstructionDTO.of(feedback.getGradingInstruction())
                : null;

        return new SubmissionFeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility(), testCaseName, gradingInstruction);
    }
}
