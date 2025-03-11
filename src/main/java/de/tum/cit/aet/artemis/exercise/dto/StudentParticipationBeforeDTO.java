package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentParticipationBeforeDTO(@JsonUnwrapped ParticipationBeforeDTO participationBeforeDTO, User student, Team team) {

    public static StudentParticipationBeforeDTO of(StudentParticipation studentParticipation) {
        return new StudentParticipationBeforeDTO(ParticipationBeforeDTO.of(studentParticipation), studentParticipation.getStudent().orElse(null),
                studentParticipation.getTeam().orElse(null));
    }

}
