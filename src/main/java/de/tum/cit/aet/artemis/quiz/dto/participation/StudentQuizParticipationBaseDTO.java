package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.DomainObjectDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record StudentQuizParticipationBaseDTO(Long id, String type, InitializationState initializationState, ZonedDateTime initializationDate, DomainObjectDTO student) {

    public static StudentQuizParticipationBaseDTO of(final StudentParticipation studentParticipation) {
        User student = studentParticipation.getStudent().orElse(null);
        DomainObjectDTO domainObjectDTO = null;
        if (student != null) {
            domainObjectDTO = DomainObjectDTO.of(student);
        }
        return new StudentQuizParticipationBaseDTO(studentParticipation.getId(), studentParticipation.getType(), studentParticipation.getInitializationState(),
                studentParticipation.getInitializationDate(), domainObjectDTO);
    }

}
