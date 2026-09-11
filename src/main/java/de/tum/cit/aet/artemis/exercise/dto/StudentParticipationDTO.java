package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * DTO containing {@link StudentParticipation} information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentParticipationDTO(Long id, String type, boolean testRun, InitializationState initializationState, ZonedDateTime initializationDate,
        ZonedDateTime individualDueDate, Double presentationScore, UserNameDTO student, TeamDTO team, ParticipationDTO.ParticipationExerciseDTO exercise) implements Serializable {

    /**
     * Builds the participation a submit response reports, from the projection the save was made against.
     * <p>
     * The submit path never loads the participation as an entity - doing so drags the exercise, its course and, for an
     * exam exercise, the exercise group with its exam and that exam's course along - so the response is mapped from the
     * projected columns plus the exercise and participant the caller already holds.
     *
     * @param target             the projected participation the submission was saved against
     * @param exercise           the exercise the submission belongs to
     * @param participant        the student or team the participation belongs to, may be null when it must be withheld
     * @param includeParticipant whether the participant may be reported; a tutor assessing must not see it
     * @return the participation as the response reports it
     */
    public static StudentParticipationDTO of(StudentParticipationSubmitTargetDTO target, Exercise exercise, @Nullable Participant participant, boolean includeParticipant) {
        UserNameDTO student = null;
        TeamDTO team = null;
        if (includeParticipant) {
            if (participant instanceof User user) {
                student = UserNameDTO.of(user);
            }
            else if (participant instanceof Team participantTeam) {
                team = TeamDTO.of(participantTeam);
            }
        }
        return new StudentParticipationDTO(target.id(), StudentParticipation.TYPE, target.testRun(), target.initializationState(), target.initializationDate(),
                target.individualDueDate(), target.presentationScore(), student, team, ParticipationDTO.ParticipationExerciseDTO.of(exercise));
    }

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
