package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class LocalCIResultServiceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localciresultservice";

    @Autowired
    private ProgrammingExerciseGradingService programmingExerciseGradingService;

    @Autowired
    private BuildJobRepository buildJobRepository;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    void testThrowsExceptionWhenResultIsNotLocalCIBuildResult() {
        var wrongBuildResult = ProgrammingExerciseFactory.generateTestResultDTO("some-name", "some-repository", ZonedDateTime.now().minusSeconds(10),
                programmingExercise.getProgrammingLanguage(), false, List.of(), List.of(), null, null, null);
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> localCIResultService.convertBuildResult(wrongBuildResult))
                .withMessage("The request body is not of type LocalCIBuildResult");
    }

    /**
     * The containers of a multi-container build all contribute to a single result of one submission: each container
     * appends its feedback to the same in-progress result, and the result is only finalized once every container has
     * finished, counted via the build jobs linked to the result.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testContainerResultsAggregateIntoOneResultAndFinalizeWhenComplete() throws Exception {
        // Configure the exercise with two containers so the expected container count resolves to two.
        BuildContainerDTO containerA = new BuildContainerDTO("container_a", "image-a:1",
                List.of(new BuildPhaseDTO("phase_a", "echo a", BuildPhaseCondition.ALWAYS, false, List.of("results/a/*.xml"))));
        BuildContainerDTO containerB = new BuildContainerDTO("container_b", "image-b:2",
                List.of(new BuildPhaseDTO("phase_b", "echo b", BuildPhaseCondition.ALWAYS, false, List.of("results/b/*.xml"))));
        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
        buildConfig.setBuildPlanConfiguration(new BuildPlanPhasesDTO(null, null, List.of(containerA, containerB)).toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(buildConfig);

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);

        String commitHash = "1234567890123456789012345678901234567890";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepository.save(submission);

        // First container finishes: its feedback is appended to a new, still in-progress result (no completion date yet).
        BuildResult resultA = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, resultA, false, 2, "container_a");
        assertThat(aggregatedResult).isNotNull();
        assertThat(aggregatedResult.getCompletionDate()).as("the result stays in progress until every container finished").isNull();
        buildJobRepository.save(new BuildJob(buildJobFor("job-a", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        // Second container finishes: it appends to the same result rather than creating a second one.
        BuildResult resultB = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResultAgain = programmingExerciseGradingService.appendContainerResult(participation, resultB, false, 2, "container_b");
        assertThat(aggregatedResultAgain.getId()).as("all containers of one submission share a single result").isEqualTo(aggregatedResult.getId());
        buildJobRepository.save(new BuildJob(buildJobFor("job-b", participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResultAgain));

        // The submission now expects two containers and both have finished (counted via the build jobs linked to the result).
        ProgrammingSubmission reloadedSubmission = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloadedSubmission.getExpectedContainerCount()).isEqualTo(2);
        assertThat(buildJobRepository.countByResultId(aggregatedResultAgain.getId())).isEqualTo(2);
        assertThat(buildJobRepository.existsByResultIdAndBuildStatusNot(aggregatedResultAgain.getId(), BuildStatus.SUCCESSFUL)).isFalse();

        // Finalizing marks the aggregated result complete and successful.
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain, participation, true, ZonedDateTime.now());
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.isSuccessful()).isTrue();
    }

    /**
     * When the student-tests container crashes, the instructor-tests container's result must survive: its feedback stays
     * on the shared result, the crashed container's build logs are kept and labeled with its name, and the aggregated
     * result is finalized as failed rather than being lost.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInstructorResultsPreservedWhenStudentContainerCrashes() throws Exception {
        BuildContainerDTO instructorContainer = new BuildContainerDTO("instructor_tests", "image-a:1",
                List.of(new BuildPhaseDTO("phase_a", "echo a", BuildPhaseCondition.ALWAYS, false, List.of("results/a/*.xml"))));
        BuildContainerDTO studentContainer = new BuildContainerDTO("student_tests", "image-b:2",
                List.of(new BuildPhaseDTO("phase_b", "echo b", BuildPhaseCondition.ALWAYS, false, List.of("results/b/*.xml"))));
        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
        buildConfig.setBuildPlanConfiguration(new BuildPlanPhasesDTO(null, null, List.of(instructorContainer, studentContainer)).toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(buildConfig);

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);

        String commitHash = "1234567890123456789012345678901234567890";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepository.save(submission);

        // the instructor container finishes with a passing test
        var instructorJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("instructorTest", List.of())));
        BuildResult instructorResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(instructorJob), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, instructorResult, true, 2, "instructor_tests");
        assertThat(aggregatedResult.getFeedbacks()).as("the instructor container produced feedback").isNotEmpty();
        buildJobRepository.save(new BuildJob(buildJobFor("job-instructor", participation, commitHash, "instructor_tests"), BuildStatus.SUCCESSFUL, aggregatedResult));

        // the student container crashes: no test feedback, but build logs and a non-zero exit code
        var crashLog = new BuildLogDTO(ZonedDateTime.now(), "student container terminated: out of memory");
        BuildResult studentResult = new BuildResult(null, commitHash, commitHash, false, ZonedDateTime.now(), List.of(), List.of(crashLog), null, true, 137);
        Result aggregatedResultAgain = programmingExerciseGradingService.appendContainerResult(participation, studentResult, true, 2, "student_tests");
        buildJobRepository.save(new BuildJob(buildJobFor("job-student", participation, commitHash, "student_tests"), BuildStatus.FAILED, aggregatedResultAgain));

        // the instructor container's feedback survives the student container's crash
        assertThat(aggregatedResultAgain.getId()).isEqualTo(aggregatedResult.getId());
        assertThat(aggregatedResultAgain.getFeedbacks()).as("the instructor feedback is not lost when a sibling container crashes").isNotEmpty();

        // the crashed container's build logs are preserved and labeled with its container name
        ProgrammingSubmission reloadedSubmission = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloadedSubmission.isBuildFailed()).isTrue();
        List<BuildLogEntry> buildLogs = buildLogEntryService.getLatestBuildLogs(reloadedSubmission);
        assertThat(buildLogs).anySatisfy(logEntry -> {
            assertThat(logEntry.getContainerName()).isEqualTo("student_tests");
            assertThat(logEntry.getLog()).contains("out of memory");
        });

        // finalizing once both containers finished marks the result complete but not successful
        boolean allContainersSucceeded = !buildJobRepository.existsByResultIdAndBuildStatusNot(aggregatedResultAgain.getId(), BuildStatus.SUCCESSFUL);
        assertThat(allContainersSucceeded).isFalse();
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain, participation, allContainersSucceeded, ZonedDateTime.now());
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.isSuccessful()).isFalse();
    }

    private BuildJobQueueItem buildJobFor(String id, ProgrammingExerciseStudentParticipation participation, String commitHash, String containerName) {
        BuildAgentDTO buildAgent = new BuildAgentDTO(null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, null, null, null, null, null);
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 0);
        BuildConfig jobBuildConfig = new BuildConfig(null, "image", commitHash, commitHash, commitHash, null, null, null, false, false, null, 0, null, null, null, null);
        return new BuildJobQueueItem(id, "plan", buildAgent, participation.getId(), programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId(),
                programmingExercise.getId(), 0, 1, null, repositoryInfo, jobTimingInfo, jobBuildConfig, null, null, containerName, null);
    }
}
