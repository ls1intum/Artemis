package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

public record StudentQuizParticipationBaseDTO(InitializationState initializationState, ZonedDateTime initializationDate) {

    public static StudentQuizParticipationBaseDTO of(final StudentParticipation studentParticipation) {
        return new StudentQuizParticipationBaseDTO(studentParticipation.getInitializationState(), studentParticipation.getInitializationDate());
    }

}
