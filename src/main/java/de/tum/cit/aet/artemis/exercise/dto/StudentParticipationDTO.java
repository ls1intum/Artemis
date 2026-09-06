package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * DTO representing the REST response for a student participation.
 *
 * @param id                    the unique identifier of the participation
 * @param initializationState   the current initialization state, if available
 * @param initializationDate    the time at which initialization started, if available
 * @param individualDueDate     the individual due date, if configured
 * @param presentationScore     the presentation score, if assigned
 * @param testRun               whether this is an exam test run or course practice participation
 * @param type                  the polymorphic participation discriminator
 * @param submissionCount       the transient number of submissions, if calculated
 * @param participantName       the visible participant name, if authorized
 * @param participantIdentifier the visible participant identifier, if authorized
 * @param student               safe public student information, if authorized and initialized
 * @param team                  safe team information, if authorized and initialized
 * @param exercise              the minimal exercise context, if requested and initialized
 * @param submissions           initialized lean submissions, or absent when submissions were not loaded
 * @param repositoryUri         the programming repository URI, if applicable
 * @param buildPlanId           the programming build-plan identifier, if applicable
 * @param branch                the programming repository branch, if applicable
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentParticipationDTO(Long id, @Nullable InitializationState initializationState, @Nullable ZonedDateTime initializationDate,
        @Nullable ZonedDateTime individualDueDate, @Nullable Double presentationScore, boolean testRun, String type, @Nullable Integer submissionCount,
        @Nullable String participantName, @Nullable String participantIdentifier, @Nullable UserPublicInfoDTO student, @Nullable ParticipationTeamDTO team,
        @Nullable ParticipationExerciseContextDTO exercise, @Nullable List<ParticipationSubmissionDTO> submissions, @Nullable String repositoryUri, @Nullable String buildPlanId,
        @Nullable String branch) implements Serializable {

    /**
     * Maps a participation for an enclosing response without exposing its participant.
     *
     * @param participation the participation to map
     * @return the participation response, or {@code null} when the input is {@code null}
     */
    public static @Nullable StudentParticipationDTO of(@Nullable StudentParticipation participation) {
        return of(participation, false);
    }

    /**
     * Maps a participation for an enclosing response, optionally including its visible participant.
     *
     * @param participation  the participation to map
     * @param includeStudent whether initialized participant information should be included
     * @return the participation response, or {@code null} when the input is {@code null}
     */
    public static @Nullable StudentParticipationDTO of(@Nullable StudentParticipation participation, boolean includeStudent) {
        return participation != null ? of(participation, includeStudent, true, false) : null;
    }

    /**
     * Maps a newly started participation including its visible participant, exercise, and initialized submissions with subtype content.
     *
     * @param participation the newly started participation
     * @return the participation response
     */
    public static StudentParticipationDTO ofAfterStart(StudentParticipation participation) {
        return of(participation, true, true, true, true);
    }

    /**
     * Maps a resumed programming participation including its visible participant and exercise context.
     *
     * @param participation the resumed programming participation
     * @return the participation response
     */
    public static StudentParticipationDTO ofAfterResume(ProgrammingExerciseStudentParticipation participation) {
        return of(participation, true, true, false);
    }

    /**
     * Maps a participation for latest-result polling with initialized lean submissions and results.
     *
     * @param participation the participation loaded with its latest result
     * @return the polling response
     */
    public static StudentParticipationDTO ofWithLatestResult(StudentParticipation participation) {
        return of(participation, false, false, true);
    }

    /**
     * Maps a participation for its current owner, including safe participant and exercise information.
     *
     * @param participation the authorized participation loaded with team students when applicable
     * @return the current-user participation response
     */
    public static StudentParticipationDTO ofForCurrentUser(StudentParticipation participation) {
        return of(participation, true, true, false);
    }

    /**
     * Maps a participation after a scalar management update without initializing unrelated associations.
     *
     * @param participation the updated participation
     * @return the lean update response
     */
    public static StudentParticipationDTO ofAfterUpdate(StudentParticipation participation) {
        return of(participation, false, false, false);
    }

    /**
     * Maps a programming participation after its build plan was cleaned up.
     *
     * @param participation the updated programming participation
     * @return the lean cleanup response
     */
    public static StudentParticipationDTO ofAfterBuildPlanCleanup(ProgrammingExerciseStudentParticipation participation) {
        return of(participation, false, false, false);
    }

    private static StudentParticipationDTO of(StudentParticipation participation, boolean includeParticipant, boolean includeExercise, boolean includeSubmissions) {
        return of(participation, includeParticipant, includeExercise, includeSubmissions, false);
    }

    private static StudentParticipationDTO of(StudentParticipation participation, boolean includeParticipant, boolean includeExercise, boolean includeSubmissions,
            boolean includeSubmissionContent) {
        UserPublicInfoDTO studentDTO = null;
        ParticipationTeamDTO teamDTO = null;
        if (includeParticipant) {
            User student = participation.getStudent().filter(Hibernate::isInitialized).orElse(null);
            Team team = participation.getTeam().filter(Hibernate::isInitialized).orElse(null);
            studentDTO = student != null ? new UserPublicInfoDTO(student) : null;
            teamDTO = team != null ? ParticipationTeamDTO.of(team) : null;
        }

        String participantName = studentDTO != null || teamDTO != null ? participation.getParticipantName() : null;
        String participantIdentifier = studentDTO != null || teamDTO != null ? participation.getParticipantIdentifier() : null;

        ParticipationExerciseContextDTO exerciseDTO = null;
        Exercise exercise = participation.getExercise();
        if (includeExercise && exercise != null && Hibernate.isInitialized(exercise)) {
            exerciseDTO = ParticipationExerciseContextDTO.of(exercise);
        }

        List<ParticipationSubmissionDTO> submissionDTOs = null;
        if (includeSubmissions && Hibernate.isInitialized(participation.getSubmissions())) {
            submissionDTOs = participation.getSubmissions().stream().map(submission -> ParticipationSubmissionDTO.of(submission, includeSubmissionContent)).toList();
        }

        String repositoryUri = null;
        String buildPlanId = null;
        String branch = null;
        if (participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation) {
            repositoryUri = programmingParticipation.getRepositoryUri();
            buildPlanId = programmingParticipation.getBuildPlanId();
            branch = programmingParticipation.getBranch();
        }

        return new StudentParticipationDTO(participation.getId(), participation.getInitializationState(), participation.getInitializationDate(),
                participation.getIndividualDueDate(), participation.getPresentationScore(), participation.isTestRun(), participation.getType(), participation.getSubmissionCount(),
                participantName, participantIdentifier, studentDTO, teamDTO, exerciseDTO, submissionDTOs, repositoryUri, buildPlanId, branch);
    }
}
