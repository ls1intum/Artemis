package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdictReview;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentPointDTO(Long id, User student, Exercise exercise, String verdict, IrisVerdictReview verdictReview, Double verifiedScore, Double verifiedScoreOld,
        List<String> reasoning, IrisPipeEvent lastEvent, Double verifiedPoints, Double verifiedPointsOld) {
}
