package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.text.domain.TextAssessmentEvent;
import de.tum.cit.aet.artemis.text.domain.TextBlockType;

/**
 * DTO containing {@link TextAssessmentEvent} information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextAssessmentEventDTO(Long id, Long userId, Instant timestamp, TextAssessmentEventType eventType, FeedbackType feedbackType, TextBlockType segmentType,
        Long courseId, Long textExerciseId, Long participationId, Long submissionId) implements Serializable {

    /**
     * Converts a {@link TextAssessmentEvent} into a {@link TextAssessmentEventDTO}.
     *
     * @param event the event to convert
     * @return the converted DTO, or {@code null} if the event is {@code null}
     */
    public static TextAssessmentEventDTO of(TextAssessmentEvent event) {
        if (event == null) {
            return null;
        }
        return new TextAssessmentEventDTO(event.getId(), event.getUserId(), event.getTimestamp(), event.getEventType(), event.getFeedbackType(), event.getSegmentType(),
                event.getCourseId(), event.getTextExerciseId(), event.getParticipationId(), event.getSubmissionId());
    }
}
