package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Everything a build trigger reads off a student participation and its newest submission, and nothing else.
 * <p>
 * Loading the participation entities instead makes the database resolve their eager associations: one full user row per
 * participation, including the password hash and the VCS access token, none of which a build trigger looks at. For an
 * exercise with a thousand participations that is a thousand extra round trips on top of the one that fetched the
 * participations. This projection is a single query and about a third of the bytes.
 *
 * @param participationId     the participation to build
 * @param repositoryUri       the assignment repository to check out
 * @param buildPlanId         the build plan of the participation, null if the exercise was never initialized for it
 * @param branch              the branch of the assignment repository
 * @param initializationState how far the participation was initialized, which decides whether it has to be resumed
 * @param individualDueDate   the participation's own due date, null when only the exercise-wide one applies
 * @param testRun             whether the participation belongs to an exam test run or to practice mode
 * @param studentId           the owning student, null for a team participation
 * @param studentLogin        the login of the owning student, used to address the websocket message
 * @param teamId              the owning team, null for an individual participation
 * @param submissionId        the newest submission of the participation
 * @param submissionType      how the newest submission came to be
 * @param submissionDate      when the newest submission was made
 * @param commitHash          the commit the newest submission points at
 * @param submitted           whether the newest submission was submitted
 * @param buildFailed         whether the build of the newest submission failed
 * @param exampleSubmission   whether the newest submission is an example submission
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationBuildTriggerDTO(long participationId, @Nullable String repositoryUri, @Nullable String buildPlanId, @Nullable String branch,
        @Nullable InitializationState initializationState, @Nullable ZonedDateTime individualDueDate, @Nullable Boolean testRun, @Nullable Long studentId,
        @Nullable String studentLogin, @Nullable Long teamId, long submissionId, @Nullable SubmissionType submissionType, @Nullable ZonedDateTime submissionDate,
        @Nullable String commitHash, @Nullable Boolean submitted, @Nullable Boolean buildFailed, @Nullable Boolean exampleSubmission) {

    /**
     * Whether the participation has to be resumed before its build can be triggered, which is the case when the
     * exercise was never initialized for it or its build plan was cleaned up in the meantime.
     *
     * @return true if the participation needs to be resumed first
     */
    public boolean needsResume() {
        return buildPlanId == null || initializationState == null || !initializationState.hasCompletedState(InitializationState.INITIALIZED);
    }

    /**
     * Builds the detached participation the build trigger works on, carrying its newest submission and the exercise the
     * caller loaded once for the whole batch.
     * <p>
     * Only the fields the trigger path reads are set, which is the point of the projection: the participation's own
     * columns, the login of the owning student, the id of the owning team, and the newest submission. A team is set
     * with its id alone because the notification reloads it with its students, which is what the entity based path did
     * as well.
     * <p>
     * The result is never passed to a repository. A participation that has to be resumed is written back to the
     * database, and {@link #needsResume()} identifies those so that the caller can load the real entity for them
     * instead.
     *
     * @param exercise the exercise of the participation, loaded with the associations the trigger reads off it
     * @return a detached participation holding the trigger inputs of this projection
     */
    public ProgrammingExerciseStudentParticipation toDetachedParticipation(ProgrammingExercise exercise) {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(participationId);
        participation.setRepositoryUri(repositoryUri);
        participation.setBuildPlanId(buildPlanId);
        participation.setBranch(branch);
        participation.setInitializationState(initializationState);
        participation.setIndividualDueDate(individualDueDate);
        participation.setTestRun(testRun != null && testRun);
        participation.setProgrammingExercise(exercise);
        if (studentId != null) {
            var student = new User();
            student.setId(studentId);
            student.setLogin(studentLogin);
            participation.setParticipant(student);
        }
        else if (teamId != null) {
            var team = new Team();
            team.setId(teamId);
            participation.setParticipant(team);
        }
        participation.addSubmission(toDetachedSubmission());
        return participation;
    }

    private ProgrammingSubmission toDetachedSubmission() {
        var submission = new ProgrammingSubmission();
        submission.setId(submissionId);
        submission.setType(submissionType);
        submission.setSubmissionDate(submissionDate);
        submission.setCommitHash(commitHash);
        // Nullable in the database and nullable on the entity, so they are passed through rather than coerced: the
        // websocket payload omits them when they are null and would otherwise start carrying false.
        submission.setSubmitted(submitted);
        submission.setExampleSubmission(exampleSubmission);
        submission.setBuildFailed(buildFailed != null && buildFailed);
        // Participation.addSubmission sets the back reference.
        return submission;
    }
}
