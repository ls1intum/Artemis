package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.dto.TeamDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * A student (or team) participation in a programming exercise.
 * <p>
 * {@code exercise} is {@code null} when the participation is embedded under an exercise — that is the cycle break the
 * entity wire achieves with {@code @JsonIgnoreProperties}. It is populated when the participation itself is the
 * response root, where the client reads the nested exercise (and its course group names) for access rights and
 * navigation. {@code team} carries the team members because the client verifies participation ownership against
 * {@code team.students[*].login}; {@code student} is the individual counterpart of that check — the clone/code button
 * decides that a repository is the caller's own (and must therefore use the participation token rather than the staff
 * token) solely from {@code participation.student.login}, which matters for an instructor's own exam test run.
 *
 * @param id                    the participation id
 * @param type                  the constant discriminator {@code "programming"}
 * @param initializationState   the participation's lifecycle state
 * @param initializationDate    when the participation was initialized
 * @param individualDueDate     the individual due date, if one was granted
 * @param testRun               whether this is an instructor test run (exam mode)
 * @param repositoryUri         the URI of the participation's repository
 * @param branch                the default branch of the participation's repository
 * @param buildPlanId           the id of the participation's build plan
 * @param participantName       the display name of the student or team
 * @param participantIdentifier the login of the student or the short name of the team
 * @param student               the individual participant; {@code null} for team participations
 * @param team                  the team participant including its members; {@code null} for individual participations
 * @param submissionCount       the number of submissions, when the endpoint sends the count instead of the submissions
 * @param exercise              the nested exercise; {@code null} when the participation is embedded under an exercise
 * @param submissions           the participation's submissions with their results; {@code null} when not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseStudentParticipationDTO(Long id, String type, InitializationState initializationState, ZonedDateTime initializationDate,
        ZonedDateTime individualDueDate, Boolean testRun, String repositoryUri, String branch, String buildPlanId, String participantName, String participantIdentifier,
        UserNameDTO student, TeamDTO team, Integer submissionCount, ProgrammingExerciseResponseDTO exercise, List<ProgrammingSubmissionWithResultsDTO> submissions)
        implements Serializable {

    /**
     * The constant Jackson subtype id of {@link ProgrammingExerciseStudentParticipation}.
     */
    public static final String TYPE = "programming";

    /**
     * Converts a student participation without its nested exercise. Use this whenever the participation is embedded
     * under an exercise, so the exercise is not re-emitted.
     *
     * @param participation the participation to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation) {
        return of(participation, null);
    }

    /**
     * Converts a student participation with an explicitly built nested exercise, mapping the submissions from the
     * participation itself. Callers that filter submissions or results use
     * {@link #of(ProgrammingExerciseStudentParticipation, ProgrammingExerciseResponseDTO, List)} instead.
     *
     * @param participation the participation to convert (may be {@code null})
     * @param exercise      the already-mapped nested exercise (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation, ProgrammingExerciseResponseDTO exercise) {
        if (participation == null || !Hibernate.isInitialized(participation)) {
            return null;
        }
        return of(participation, exercise, mapSubmissions(participation));
    }

    /**
     * Converts a student participation with an explicitly built nested exercise and submission list. Callers that
     * filter submissions or results (exam masking, sensitive-information filtering) use this overload so they never
     * have to mutate the managed entity graph to shape the JSON.
     *
     * @param participation the participation to convert (may be {@code null})
     * @param exercise      the already-mapped nested exercise (may be {@code null})
     * @param submissions   the already-mapped submissions (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation, ProgrammingExerciseResponseDTO exercise,
            List<ProgrammingSubmissionWithResultsDTO> submissions) {
        if (participation == null || !Hibernate.isInitialized(participation)) {
            return null;
        }
        Team teamEntity = participation.getTeam().orElse(null);
        TeamDTO team = teamEntity != null && Hibernate.isInitialized(teamEntity) ? TeamDTO.of(teamEntity) : null;
        // the client identifies its own repository through student.login; the slot must never trigger a lazy load
        UserNameDTO student = participation.getStudent().filter(Hibernate::isInitialized).map(UserNameDTO::of).orElse(null);
        return new ProgrammingExerciseStudentParticipationDTO(participation.getId(), TYPE, participation.getInitializationState(), participation.getInitializationDate(),
                participation.getIndividualDueDate(), participation.isTestRun(), participation.getRepositoryUri(), participation.getBranch(), participation.getBuildPlanId(),
                participation.getParticipantName(), participation.getParticipantIdentifier(), student, team, participation.getSubmissionCount(), exercise, submissions);
    }

    private static List<ProgrammingSubmissionWithResultsDTO> mapSubmissions(ProgrammingExerciseStudentParticipation participation) {
        if (participation.getSubmissions() == null || !Hibernate.isInitialized(participation.getSubmissions())) {
            return null;
        }
        return participation.getSubmissions().stream().filter(ProgrammingSubmission.class::isInstance).map(ProgrammingSubmission.class::cast)
                .map(ProgrammingSubmissionWithResultsDTO::of).toList();
    }
}
