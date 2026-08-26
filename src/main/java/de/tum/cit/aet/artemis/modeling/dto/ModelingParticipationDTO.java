package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.TeamDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

/**
 * Read DTO for a {@link StudentParticipation} as exposed by the modeling submission endpoints (save/submit response,
 * submission list, single submission, without-assessment, latest-modeling-submission, submissions-with-results).
 * <p>
 * Unlike the text module this DTO carries <b>no</b> {@code submissions} component: the modeling client keeps the
 * submission at the top level of the response and rebuilds {@code participation.submissions = [submission]} itself, so
 * nesting the submissions here would create a DTO cycle. The {@code student}/{@code team} owner is only mapped when
 * {@code includeStudent} is set (owner and instructors), mirroring the previous {@code hideDetails} /
 * {@code filterSensitiveInformation} behavior. The {@code exercise} is mapped from the participation's exercise, which
 * the controller has already filtered/prepared in place (grading criteria loaded for tutors, sensitive information
 * removed for students, {@code exerciseGroup.exam} detached for exam editors); the list endpoint detaches the exercise
 * entirely, so it maps to {@code null} there.
 *
 * @param id                  the participation id
 * @param type                the participation type discriminator
 * @param testRun             whether this is a test-run participation
 * @param initializationState the participation state
 * @param initializationDate  when the participation was initialized
 * @param individualDueDate   the individual due date, if any
 * @param student             the owning student (only when {@code includeStudent})
 * @param team                the owning team (only when {@code includeStudent})
 * @param exercise            the (already filtered) modeling exercise, or {@code null} when detached
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingParticipationDTO(Long id, String type, boolean testRun, InitializationState initializationState, ZonedDateTime initializationDate,
        ZonedDateTime individualDueDate, UserNameDTO student, TeamDTO team, ModelingExerciseResponseDTO exercise) implements Serializable {

    /**
     * Converts a {@link StudentParticipation} into a {@link ModelingParticipationDTO}.
     *
     * @param participation  the participation to convert (may be {@code null})
     * @param includeStudent whether the owning student/team should be included
     * @return the converted DTO, or {@code null} if the participation is {@code null}
     */
    public static ModelingParticipationDTO of(StudentParticipation participation, boolean includeStudent) {
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

        ModelingExerciseResponseDTO exercise = null;
        if (participation.getExercise() instanceof ModelingExercise modelingExercise && Hibernate.isInitialized(modelingExercise)) {
            exercise = ModelingExerciseResponseDTO.of(modelingExercise);
        }

        return new ModelingParticipationDTO(participation.getId(), participation.getType(), participation.isTestRun(), participation.getInitializationState(),
                participation.getInitializationDate(), participation.getIndividualDueDate(), student, team, exercise);
    }
}
