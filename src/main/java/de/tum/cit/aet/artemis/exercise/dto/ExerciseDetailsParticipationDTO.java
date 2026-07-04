package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * DTO containing a student participation and its already-filtered submission history for the exercise details view.
 *
 * @param id                  the participation identifier
 * @param type                the participation discriminator
 * @param testRun             whether this is a test-run or practice participation
 * @param initializationState the participation initialization state, if available
 * @param initializationDate  the initialization date, if available
 * @param individualDueDate   the individual due date, if available
 * @param presentationScore   the presentation score, if available
 * @param student             the student identity, when initialized and applicable
 * @param team                the team identity and initialized members, when applicable
 * @param submissions         the initialized, already-filtered submission history, or absent when not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsParticipationDTO(Long id, String type, boolean testRun, @Nullable InitializationState initializationState, @Nullable ZonedDateTime initializationDate,
        @Nullable ZonedDateTime individualDueDate, @Nullable Double presentationScore, @Nullable UserNameDTO student, @Nullable TeamDTO team,
        @Nullable List<SubmissionResponseDTO> submissions) {

    /**
     * Maps a student participation without traversing uninitialized associations or creating response cycles.
     *
     * @param participation the participation to map
     * @return the exercise-details participation DTO
     */
    public static ExerciseDetailsParticipationDTO of(StudentParticipation participation) {
        Objects.requireNonNull(participation, "The participation must be set");

        User student = participation.getStudent().orElse(null);
        UserNameDTO studentDTO = student != null && Hibernate.isInitialized(student) ? UserNameDTO.of(student) : null;
        Team team = participation.getTeam().orElse(null);
        TeamDTO teamDTO = team != null && Hibernate.isInitialized(team) ? TeamDTO.of(team) : null;
        List<SubmissionResponseDTO> submissions = Hibernate.isInitialized(participation.getSubmissions())
                ? participation.getSubmissions().stream().filter(Objects::nonNull).map(SubmissionResponseDTO::ofForExerciseDetails).toList()
                : null;

        return new ExerciseDetailsParticipationDTO(participation.getId(), participation.getType(), participation.isTestRun(), participation.getInitializationState(),
                participation.getInitializationDate(), participation.getIndividualDueDate(), participation.getPresentationScore(), studentDTO, teamDTO, submissions);
    }
}
