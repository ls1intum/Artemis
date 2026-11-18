package de.tum.cit.aet.artemis.assessment.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * The body of an external submission result: the manual result an instructor enters for a student who submitted outside of Artemis.
 * <p>
 * The dialog posts the full client-side result object (successful, completionDate, ...); only the fields listed here are read,
 * everything else is computed by the server (assessor, assessment type, completion date, exercise). Unknown properties are
 * therefore ignored. The bare {@code @JsonInclude()} keeps an explicit empty feedback list on the wire.
 *
 * @param score     the score in percent
 * @param rated     whether the result counts towards the score
 * @param feedbacks the manual feedback items of the result
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude()
public record ExternalSubmissionResultDTO(Double score, Boolean rated, List<FeedbackDTO> feedbacks) {

    /**
     * Builds the transient result described by this request. The score is set last so {@code successful} is derived from it.
     *
     * @return the result carrying the instructor-supplied score, rated flag and feedback
     */
    public Result toEntity() {
        Result result = new Result();
        result.setRated(Boolean.TRUE.equals(rated));
        result.setScore(score);
        List<Feedback> feedbackEntities = new ArrayList<>();
        if (feedbacks != null) {
            feedbacks.stream().filter(Objects::nonNull).map(ExternalSubmissionResultDTO::toFeedback).forEach(feedbackEntities::add);
        }
        result.setFeedbacks(feedbackEntities);
        return result;
    }

    private static Feedback toFeedback(FeedbackDTO dto) {
        Feedback feedback = new Feedback();
        feedback.setId(dto.id());
        feedback.setText(dto.text());
        // setDetailText computes hasLongFeedbackText; a client-sent true must never be downgraded, a client-sent false never applied
        feedback.setDetailText(dto.detailText());
        if (dto.hasLongFeedbackText()) {
            feedback.setHasLongFeedbackText(true);
        }
        feedback.setReference(dto.reference());
        feedback.setCredits(dto.credits());
        feedback.setPositive(dto.positive());
        feedback.setType(dto.type());
        feedback.setVisibility(dto.visibility());
        if (dto.gradingInstruction() != null && dto.gradingInstruction().id() != null) {
            GradingInstruction gradingInstruction = new GradingInstruction();
            gradingInstruction.setId(dto.gradingInstruction().id());
            feedback.setGradingInstruction(gradingInstruction);
        }
        return feedback;
    }
}
