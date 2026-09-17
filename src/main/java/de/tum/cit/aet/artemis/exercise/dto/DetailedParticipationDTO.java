package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * A student or team participation, as the exercise details nest it under the exercise and the participation submissions
 * nest it in each submission.
 * <p>
 * {@code type} is the Jackson discriminator of the {@code Participation} hierarchy, so it is always set. The repository
 * components are set for programming participations only. The entity wrote {@code userIndependentRepositoryUri} even when
 * it was {@code null}; this record leaves the key off then, like
 * {@link de.tum.cit.aet.artemis.programming.dto.ProgrammingParticipationLatestResultDTO} does, and the client reads both
 * the same way.
 *
 * @param type                         the participation discriminator: {@code student} or {@code programming}
 * @param id                           the id of the participation
 * @param initializationState          the lifecycle state
 * @param initializationDate           when the participation was initialized
 * @param individualDueDate            the individual due date, if one was granted
 * @param testRun                      whether it is a test run or practice participation
 * @param attempt                      the attempt counter
 * @param submissionCount              the number of submissions, where a route counted them
 * @param presentationScore            the presentation score
 * @param participantIdentifier        the login of the student or the short name of the team
 * @param participantName              the name of the student or the team
 * @param student                      the student of an individual participation
 * @param team                         the team of a team participation
 * @param exercise                     the exercise, on the participation submissions only
 * @param submissions                  the submissions with their results, on the exercise details only
 * @param repositoryUri                the repository, for programming participations
 * @param buildPlanId                  the build plan, for programming participations
 * @param branch                       the branch, for programming participations
 * @param userIndependentRepositoryUri the repository without credentials, for programming participations
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DetailedParticipationDTO(String type, Long id, @Nullable InitializationState initializationState, @Nullable ZonedDateTime initializationDate,
        @Nullable ZonedDateTime individualDueDate, boolean testRun, int attempt, @Nullable Integer submissionCount, @Nullable Double presentationScore,
        @Nullable String participantIdentifier, @Nullable String participantName, @Nullable ParticipantUserDTO student, @Nullable ParticipantTeamDTO team,
        @Nullable ExerciseResponseDTO exercise, @Nullable List<DetailedSubmissionDTO> submissions, @Nullable String repositoryUri, @Nullable String buildPlanId,
        @Nullable String branch, @Nullable String userIndependentRepositoryUri) {

    /**
     * Maps an already filtered participation with its submissions, for a participation nested under its exercise.
     *
     * @param participation the participation to map
     * @return the participation as the exercise details carry it
     */
    public static DetailedParticipationDTO withSubmissions(StudentParticipation participation) {
        List<DetailedSubmissionDTO> submissions = Hibernate.isInitialized(participation.getSubmissions())
                ? participation.getSubmissions().stream().map(submission -> DetailedSubmissionDTO.of(submission, null)).toList()
                : null;
        return of(participation, null, submissions);
    }

    /**
     * Maps a participation with its exercise, for a participation nested in one of its submissions.
     *
     * @param participation the participation to map
     * @return the participation as the participation submissions carry it
     */
    public static DetailedParticipationDTO withExercise(StudentParticipation participation) {
        ExerciseResponseDTO exercise = participation.getExercise() != null && Hibernate.isInitialized(participation.getExercise())
                ? ExerciseResponseDTO.of(participation.getExercise())
                : null;
        return of(participation, exercise, null);
    }

    private static DetailedParticipationDTO of(StudentParticipation participation, @Nullable ExerciseResponseDTO exercise, @Nullable List<DetailedSubmissionDTO> submissions) {
        String repositoryUri = null;
        String buildPlanId = null;
        String branch = null;
        String userIndependentRepositoryUri = null;
        if (participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation) {
            repositoryUri = programmingParticipation.getRepositoryUri();
            buildPlanId = programmingParticipation.getBuildPlanId();
            branch = programmingParticipation.getBranch();
            userIndependentRepositoryUri = programmingParticipation.getUserIndependentRepositoryUri();
        }
        return new DetailedParticipationDTO(participation.getType(), participation.getId(), participation.getInitializationState(), participation.getInitializationDate(),
                participation.getIndividualDueDate(), participation.isTestRun(), participation.getAttempt(), participation.getSubmissionCount(),
                participation.getPresentationScore(), participation.getParticipantIdentifier(), participation.getParticipantName(),
                ParticipantUserDTO.of(participation.getStudent().orElse(null)), ParticipantTeamDTO.of(participation.getTeam().orElse(null)), exercise, submissions, repositoryUri,
                buildPlanId, branch, userIndependentRepositoryUri);
    }
}
