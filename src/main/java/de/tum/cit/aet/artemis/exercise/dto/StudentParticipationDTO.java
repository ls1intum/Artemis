package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * DTO containing {@link StudentParticipation} information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentParticipationDTO(Long id, String type, boolean testRun, InitializationState initializationState, ZonedDateTime initializationDate,
        ZonedDateTime individualDueDate, Double presentationScore, UserNameDTO student, TeamDTO team, ParticipationDTO.ParticipationExerciseDTO exercise) implements Serializable {

    public static StudentParticipationDTO of(StudentParticipation participation) {
        return of(participation, false);
    }

    /**
     * Converts a {@link StudentParticipation} into a {@link StudentParticipationDTO}.
     *
     * @param participation  the participation to convert
     * @param includeStudent whether the student should be included; when {@code false} the student is omitted (e.g. for tutors)
     * @return the converted DTO, or {@code null} if the participation is {@code null}
     */
    public static StudentParticipationDTO of(StudentParticipation participation, boolean includeStudent) {
        if (participation == null) {
            return null;
        }
        UserNameDTO student = null;
        TeamDTO team = null;
        if (includeStudent) {
            if (Hibernate.isInitialized(participation.getStudent().orElse(null))) {
                student = UserNameDTO.of(participation.getStudent().orElse(null));
            }
            if (Hibernate.isInitialized(participation.getTeam().orElse(null))) {
                team = TeamDTO.of(participation.getTeam().orElse(null));
            }
        }
        ParticipationDTO.ParticipationExerciseDTO exercise = null;
        if (Hibernate.isInitialized(participation.getExercise())) {
            exercise = ParticipationDTO.ParticipationExerciseDTO.of(participation.getExercise());
        }
        return new StudentParticipationDTO(participation.getId(), participation.getType(), participation.isTestRun(), participation.getInitializationState(),
                participation.getInitializationDate(), participation.getIndividualDueDate(), participation.getPresentationScore(), student, team, exercise);
    }
}
