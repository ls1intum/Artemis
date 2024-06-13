package de.tum.in.www1.artemis.web.rest.dto.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseMasteryCalculationDTO(Exercise exercise, StudentScore studentScore, TeamScore teamScore, long submissionCount) {

    public ParticipantScore participantScore() {
        return studentScore != null ? studentScore : teamScore;
    }
}
