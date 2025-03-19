package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.DomainObjectDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

record StudentQuizParticipationBaseDTO(Long id, InitializationState initializationState, ZonedDateTime initializationDate, DomainObjectDTO student) {

    public static StudentQuizParticipationBaseDTO of(final StudentParticipation studentParticipation) {
        User student = studentParticipation.getStudent().orElse(null);
        DomainObjectDTO domainObjectDTO = null;
        if (student != null) {
            domainObjectDTO = DomainObjectDTO.of(student);
        }
        return new StudentQuizParticipationBaseDTO(studentParticipation.getId(), studentParticipation.getInitializationState(), studentParticipation.getInitializationDate(),
                domainObjectDTO);
    }

}
