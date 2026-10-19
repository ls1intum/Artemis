package de.tum.cit.aet.artemis.fileupload.dto;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;

/**
 * DTO representing feedback for a file upload submission.
 *
 * @param id                  the ID of the feedback
 * @param text                the general text feedback / category
 * @param detailText          the detailed feedback text
 * @param hasLongFeedbackText whether the feedback has a long detail text stored separately
 * @param reference           the reference to a specific element in the submission (if applicable)
 * @param credits             the score credits (points) awarded or deducted
 * @param positive            whether the feedback is positive
 * @param type                the feedback type (e.g. MANUAL, AUTOMATIC)
 * @param visibility          the visibility of the feedback (e.g. ALWAYS, AFTER_DUE_DATE)
 * @param gradingInstruction  the structured grading instruction linked to this feedback
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadFeedbackDTO(Long id, @Nullable String text, @Nullable String detailText, @Nullable Boolean hasLongFeedbackText, @Nullable String reference,
        @Nullable Double credits, @Nullable Boolean positive, @Nullable FeedbackType type, @Nullable Visibility visibility, @Nullable GradingInstructionDTO gradingInstruction) {

    /**
     * Factory method to create a {@link FileUploadFeedbackDTO} from a {@link Feedback} entity.
     *
     * @param feedback the feedback entity to map, can be null
     * @return the mapped DTO, or null if the input was null
     */
    public static @Nullable FileUploadFeedbackDTO of(@Nullable Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        GradingInstruction gradingInstruction = feedback.getGradingInstruction();
        GradingInstructionDTO gradingInstructionDTO = gradingInstruction != null && Hibernate.isInitialized(gradingInstruction) ? GradingInstructionDTO.of(gradingInstruction)
                : null;
        return new FileUploadFeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility(), gradingInstructionDTO);
    }
}
