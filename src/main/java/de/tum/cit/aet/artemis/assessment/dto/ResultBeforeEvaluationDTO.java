package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.List;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

public record ResultBeforeEvaluationDTO(Long id, ZonedDateTime completionDate, Boolean rated, Submission submission, List<Feedback> feedbacks) {

}
