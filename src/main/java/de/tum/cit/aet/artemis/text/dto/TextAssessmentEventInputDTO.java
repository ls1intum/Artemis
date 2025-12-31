package de.tum.cit.aet.artemis.text.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.text.domain.TextAssessmentEvent;
import de.tum.cit.aet.artemis.text.domain.TextBlockType;

/**
 * DTO for creating TextAssessmentEvent entries.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextAssessmentEventInputDTO(@NotNull Long userId, @NotNull TextAssessmentEventType eventType, @Nullable FeedbackType feedbackType,
        @Nullable TextBlockType segmentType, @NotNull Long courseId, @NotNull Long textExerciseId, @NotNull Long participationId, @NotNull Long submissionId) {

    /**
     * Creates a new TextAssessmentEvent entity from this DTO.
     *
     * @return a new TextAssessmentEvent entity
     */
    public TextAssessmentEvent toEntity() {
        TextAssessmentEvent event = new TextAssessmentEvent();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setFeedbackType(feedbackType);
        event.setSegmentType(segmentType);
        event.setCourseId(courseId);
        event.setTextExerciseId(textExerciseId);
        event.setParticipationId(participationId);
        event.setSubmissionId(submissionId);
        return event;
    }
}
