package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.FailedBuildLogService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class LocalCIResultServiceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localciresultservice";

    @Autowired
    private TestCaseFeedbackRepository testCaseFeedbackRepository;

    @Autowired
    private ProgrammingExerciseGradingService programmingExerciseGradingService;

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Autowired
    private FailedBuildLogService failedBuildLogService;

    @Autowired
    private LocalCIResultProcessingService localCIResultProcessingService;

    @Autowired
    private ResultService resultService;

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testContainerResultsAggregateIntoOneResultAndFinalizeWhenComplete() throws Exception {
        BuildContainerDTO containerA = new BuildContainerDTO("container_a", "image-a:1",
                List.of(new BuildPhaseDTO("phase_a", "echo a", BuildPhaseCondition.ALWAYS, false, List.of("results/a/*.xml"))));
        BuildContainerDTO containerB = new BuildContainerDTO("container_b", "image-b:2",
                List.of(new BuildPhaseDTO("phase_b", "echo b", BuildPhaseCondition.ALWAYS, false, List.of("results/b/*.xml"))));
        ProgrammingExerciseBuildConfig buildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise.getId());
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

        BuildResult resultA = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, resultA, false, "container_a", null).result();
        assertThat(aggregatedResult).isNotNull();
        assertThat(aggregatedResult.getCompletionDate()).as("the result stays in progress until every container finished").isNull();
        buildJobRepository.save(new BuildJob(buildJobFor("merge-0", "merge", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        BuildResult resultB = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResultAgain = programmingExerciseGradingService.appendContainerResult(participation, resultB, false, "container_b", aggregatedResult.getId()).result();
        assertThat(aggregatedResultAgain.getId()).as("all containers of one submission share a single result").isEqualTo(aggregatedResult.getId());
        buildJobRepository.save(new BuildJob(buildJobFor("merge-1", "merge", participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResultAgain));

        assertThat(buildJobRepository.findAllByBuildGroupId("merge")).hasSize(2).allSatisfy(job -> {
            assertThat(job.getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
            assertThat(job.getResult()).isNotNull();
        });
        assertThat(buildJobRepository.countByResultId(aggregatedResultAgain.getId())).isEqualTo(2);

        // no container reported a test case, so the result is not successful
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain.getId(), participation, true, false, ZonedDateTime.now());
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.isSuccessful()).isFalse();
    }

    /** Finalize itself persists the lock-repository policy's unrated flag; this test calls it outside a transaction. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFinalizePersistsTheUnratedFlagOfTheLockRepositoryPolicyWithoutATransaction() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // A limit of one, already exceeded: an earlier submission of this participation has a result of its own.
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setSubmissionLimit(1);
        lockRepositoryPolicy.setActive(true);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);
        ProgrammingSubmission earlierSubmission = new ProgrammingSubmission();
        earlierSubmission.setCommitHash("0000000000000000000000000000000000000001");
        earlierSubmission.setSubmissionDate(ZonedDateTime.now().minusMinutes(5));
        earlierSubmission.setType(SubmissionType.MANUAL);
        earlierSubmission.setParticipation(participation);
        earlierSubmission = programmingSubmissionRepository.save(earlierSubmission);
        Result earlierResult = new Result();
        earlierResult.setAssessmentType(AssessmentType.AUTOMATIC);
        earlierResult.setCompletionDate(ZonedDateTime.now().minusMinutes(4));
        earlierResult.setSubmission(earlierSubmission);
        earlierResult.setExerciseId(programmingExercise.getId());
        resultRepository.save(earlierResult);

        String commitHash = "0000000000000000000000000000000000000002";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepository.save(submission);
        BuildResult containerResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, containerResult, false, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("rated-0", "rated", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());

        Result persistedResult = resultRepository.findById(finalizedResult.getId()).orElseThrow();
        assertThat(persistedResult.getCompletionDate()).isNotNull();
        assertThat(persistedResult.isRated()).isFalse();
    }

    /** A crashed sibling keeps the instructor container's feedback, labels its own logs and fails the finalized result. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInstructorResultsPreservedWhenStudentContainerCrashes() throws Exception {
        BuildContainerDTO instructorContainer = new BuildContainerDTO("instructor_tests", "image-a:1",
                List.of(new BuildPhaseDTO("phase_a", "echo a", BuildPhaseCondition.ALWAYS, false, List.of("results/a/*.xml"))));
        BuildContainerDTO studentContainer = new BuildContainerDTO("student_tests", "image-b:2",
                List.of(new BuildPhaseDTO("phase_b", "echo b", BuildPhaseCondition.ALWAYS, false, List.of("results/b/*.xml"))));
        ProgrammingExerciseBuildConfig buildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise.getId());
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

        // a test-case feedback row exists only for a registered test case, so the instructor test is registered first
        testCaseRepository.save(new ProgrammingExerciseTestCase().testName("instructorTest").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));

        var instructorJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("instructorTest", List.of())));
        BuildResult instructorResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(instructorJob), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, instructorResult, true, "instructor_tests", null).result();
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(aggregatedResult.getId()))).as("the instructor container produced feedback").isNotEmpty();
        buildJobRepository.save(new BuildJob(buildJobFor("crash-0", "crash", participation, commitHash, "instructor_tests"), BuildStatus.SUCCESSFUL, aggregatedResult));

        // the student container crashes: no test feedback, but build logs and a non-zero exit code
        var crashLog = new BuildLogDTO(ZonedDateTime.now(), "student container terminated: out of memory");
        BuildResult studentResult = new BuildResult(null, commitHash, commitHash, false, ZonedDateTime.now(), List.of(), List.of(crashLog), null, true, 137);
        Result aggregatedResultAgain = programmingExerciseGradingService.appendContainerResult(participation, studentResult, true, "student_tests", aggregatedResult.getId())
                .result();
        buildJobRepository.save(new BuildJob(buildJobFor("crash-1", "crash", participation, commitHash, "student_tests"), BuildStatus.FAILED, aggregatedResultAgain, true));

        assertThat(aggregatedResultAgain.getId()).isEqualTo(aggregatedResult.getId());
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(aggregatedResultAgain.getId())))
                .as("the instructor feedback is not lost when a sibling container crashes").isNotEmpty();

        ProgrammingSubmission reloadedSubmission = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        List<BuildLogEntry> buildLogs = buildLogEntryService.getLatestBuildLogs(reloadedSubmission);
        assertThat(buildLogs).anySatisfy(logEntry -> {
            assertThat(logEntry.getContainerName()).isEqualTo("student_tests");
            assertThat(logEntry.getLog()).contains("out of memory");
        });

        List<BuildJob> crashJobs = buildJobRepository.findAllByBuildGroupId("crash");
        boolean allContainersSucceeded = crashJobs.stream().allMatch(job -> job.getBuildStatus() == BuildStatus.SUCCESSFUL);
        assertThat(allContainersSucceeded).isFalse();
        boolean anyContainerFailedToBuild = crashJobs.stream().anyMatch(BuildJob::isBuildFailed);
        assertThat(anyContainerFailedToBuild).as("the crashed container's verdict is read from the group's jobs").isTrue();
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain.getId(), participation, allContainersSucceeded,
                anyContainerFailedToBuild, ZonedDateTime.now());
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.isSuccessful()).isFalse();
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("written to the submission when the group finalizes").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFinalizedResultIsNotSuccessfulWhenATestFailedAlthoughEveryContainerRan() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);

        String commitHash = "0000000000000000000000000000000000000004";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(participation);
        programmingSubmissionRepository.save(submission);

        testCaseRepository.save(new ProgrammingExerciseTestCase().testName("failingTest").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));

        var job = new LocalCIJobDTO(List.of(new LocalCITestJobDTO("failingTest", List.of("expected 2 but was 3"))), List.of());
        BuildResult containerResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(job), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, containerResult, true, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("failing-0", "failing", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("the build itself did not fail").isFalse();

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(finalizedResult.getScore()).isLessThan(100.0);
        assertThat(finalizedResult.isSuccessful()).as("a failing test makes the merged result unsuccessful").isFalse();
    }

    /** The test cases are split across two containers, so only their merged feedback covers every test case. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFinalizedResultIsSuccessfulWhenEveryContainerRanAndEveryTestPassed() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);

        String commitHash = "0000000000000000000000000000000000000005";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(participation);
        programmingSubmissionRepository.save(submission);

        List<String> testNames = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true).stream().map(ProgrammingExerciseTestCase::getTestName).sorted()
                .toList();
        assertThat(testNames).hasSizeGreaterThan(1);
        List<String> shareA = testNames.subList(0, testNames.size() / 2);
        List<String> shareB = testNames.subList(testNames.size() / 2, testNames.size());

        var jobA = new LocalCIJobDTO(List.of(), shareA.stream().map(name -> new LocalCITestJobDTO(name, List.of())).toList());
        BuildResult resultA = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(jobA), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, resultA, true, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("passing-0", "passing", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        var jobB = new LocalCIJobDTO(List.of(), shareB.stream().map(name -> new LocalCITestJobDTO(name, List.of())).toList());
        BuildResult resultB = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(jobB), null, null, false, 0);
        Result aggregatedResultAgain = programmingExerciseGradingService.appendContainerResult(participation, resultB, true, "container_b", aggregatedResult.getId()).result();
        buildJobRepository.save(new BuildJob(buildJobFor("passing-1", "passing", participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResultAgain));
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("no container failed to build").isFalse();

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(finalizedResult.getScore()).isEqualTo(100.0);
        assertThat(finalizedResult.getTestCaseCount()).isEqualTo(testNames.size());
        assertThat(finalizedResult.getPassedTestCaseCount()).isEqualTo(testNames.size());
        assertThat(finalizedResult.isSuccessful()).as("every container ran and every test case passed").isTrue();
    }

    /** A retry or re-push of the same commit is a new build group with an aggregate of its own. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEachBuildAttemptAggregatesIntoItsOwnResult() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);

        String commitHash = "0000000000000000000000000000000000000003";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(participation);
        programmingSubmissionRepository.save(submission);

        // The first attempt's container opens the attempt's aggregate; its sibling has not reported yet.
        BuildResult firstAttempt = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result firstAggregate = programmingExerciseGradingService.appendContainerResult(participation, firstAttempt, false, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("attempt1-0", "attempt1", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, firstAggregate));

        // A second attempt of the same commit starts: no job of its group has merged yet, so its container gets no aggregate id.
        BuildResult secondAttempt = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result secondAggregate = programmingExerciseGradingService.appendContainerResult(participation, secondAttempt, false, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("attempt2-0", "attempt2", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, secondAggregate));

        assertThat(secondAggregate.getId()).as("a new attempt does not join the earlier attempt's open aggregate").isNotEqualTo(firstAggregate.getId());
        assertThat(buildJobRepository.findResultIdsOfBuildGroup("attempt1", PageRequest.of(0, 1))).containsExactly(firstAggregate.getId());
        assertThat(buildJobRepository.findResultIdsOfBuildGroup("attempt2", PageRequest.of(0, 1))).containsExactly(secondAggregate.getId());

        Result finalizedSecond = programmingExerciseGradingService.finalizeContainerResult(secondAggregate.getId(), participation, true, false, ZonedDateTime.now());
        assertThat(finalizedSecond.getCompletionDate()).isNotNull();
        assertThat(resultRepository.findById(firstAggregate.getId()).orElseThrow().getCompletionDate()).isNull();
    }

    /** The sweep finalizes a complete group whose aggregate stayed in progress and skips one with a queued container. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSweepFinalizesACompleteGroupWhoseAggregatedResultStayedInProgress() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        ProgrammingSubmission completeSubmission = submissionOf(participation, "0000000000000000000000000000000000000006");
        ProgrammingSubmission openSubmission = submissionOf(participation, "0000000000000000000000000000000000000007");

        // both containers of the first group merged and their jobs finished minutes ago, but nothing finalized the result;
        // the first container reported one of the exercise's test cases, so the finalized result carries a score
        String completeCommit = completeSubmission.getCommitHash();
        var passingJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("testClass[SortStrategy]", List.of())));
        BuildResult resultA = new BuildResult(null, completeCommit, completeCommit, true, ZonedDateTime.now(), List.of(passingJob), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, resultA, true, "container_a", null).result();
        saveFinishedJob(buildJobFor("sweep-0", "sweep", participation, completeCommit, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult,
                ZonedDateTime.now().minusMinutes(5));
        BuildResult resultB = new BuildResult(null, completeCommit, completeCommit, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResultAgain = programmingExerciseGradingService.appendContainerResult(participation, resultB, false, "container_b", aggregatedResult.getId()).result();
        saveFinishedJob(buildJobFor("sweep-1", "sweep", participation, completeCommit, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResultAgain,
                ZonedDateTime.now().minusMinutes(4));

        // the second group's first container merged just as long ago, but its sibling is still queued
        String openCommit = openSubmission.getCommitHash();
        BuildResult openResult = new BuildResult(null, openCommit, openCommit, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result openAggregate = programmingExerciseGradingService.appendContainerResult(participation, openResult, false, "container_a", null).result();
        saveFinishedJob(buildJobFor("open-0", "open", participation, openCommit, "container_a"), BuildStatus.SUCCESSFUL, openAggregate, ZonedDateTime.now().minusMinutes(5));
        buildJobRepository.save(new BuildJob(buildJobFor("open-1", "open", participation, openCommit, "container_b"), BuildStatus.QUEUED, null));

        assertThat(sweep()).as("one complete group with an in-progress result").isEqualTo(1);

        Result finalizedResult = resultRepository.findById(aggregatedResult.getId()).orElseThrow();
        assertThat(finalizedResult.getCompletionDate()).as("finalized with the completion date of its last job, not with the time of the sweep").isNotNull()
                .isBefore(ZonedDateTime.now().minusMinutes(3));
        assertThat(finalizedResult.getScore()).as("scored over the merged feedback").isGreaterThan(0.0);
        assertThat(resultRepository.findById(openAggregate.getId()).orElseThrow().getCompletionDate()).as("a group with a queued container is not complete").isNull();
        assertThat(sweep()).as("nothing left to finalize").isZero();
    }

    /** The penalty feedback the scoring adds carries ids, by which the client identifies feedback. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFinalizedResultCarriesTheIdsOfTheFeedbackTheScoringAdded() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        // one point per submission beyond a limit of one; an earlier submission with a result of its own exceeds it
        SubmissionPenaltyPolicy penaltyPolicy = new SubmissionPenaltyPolicy();
        penaltyPolicy.setSubmissionLimit(1);
        penaltyPolicy.setExceedingPenalty(1.0);
        penaltyPolicy.setActive(true);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(penaltyPolicy, programmingExercise);
        ProgrammingSubmission earlierSubmission = submissionOf(participation, "0000000000000000000000000000000000000008");
        Result earlierResult = new Result();
        earlierResult.setAssessmentType(AssessmentType.AUTOMATIC);
        earlierResult.setCompletionDate(ZonedDateTime.now().minusMinutes(4));
        earlierResult.setSubmission(earlierSubmission);
        earlierResult.setExerciseId(programmingExercise.getId());
        resultRepository.save(earlierResult);

        String commitHash = submissionOf(participation, "0000000000000000000000000000000000000009").getCommitHash();
        var passingJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("testClass[SortStrategy]", List.of())));
        BuildResult containerResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(passingJob), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, containerResult, true, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("penalty-0", "penalty", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(finalizedResult.getFeedbacks()).as("the penalty feedback the scoring added")
                .anyMatch(feedback -> feedback.getText() != null && feedback.getText().startsWith(Feedback.SUBMISSION_POLICY_FEEDBACK_IDENTIFIER));
        assertThat(finalizedResult.getFeedbacks()).as("reported with the ids the client identifies feedback by").allMatch(feedback -> feedback.getId() != null);
    }

    /** An interrupted merge's unlinked leftover is deleted, so the assessment stays the latest result. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAnInProgressLeftoverDoesNotHideTheAssessmentTheFinalizedFeedbackIsMergedInto() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        ProgrammingSubmission submission = submissionOf(participation, "0000000000000000000000000000000000000010");
        Result assessment = new Result();
        assessment.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        assessment.setCompletionDate(ZonedDateTime.now().minusMinutes(10));
        assessment.setSubmission(submission);
        assessment.setExerciseId(programmingExercise.getId());
        assessment = resultRepository.save(assessment);
        // the newer leftover: automatic, never completed, linked by no job
        Result leftover = new Result();
        leftover.setAssessmentType(AssessmentType.AUTOMATIC);
        leftover.setCompletionDate(null);
        leftover.setSubmission(submission);
        leftover.setExerciseId(programmingExercise.getId());
        leftover = resultRepository.save(leftover);

        String commitHash = submission.getCommitHash();
        var passingJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("testClass[SortStrategy]", List.of())));
        BuildResult containerResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(passingJob), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, containerResult, true, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("leftover-0", "leftover", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        Result reportedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(reportedResult.getId()).as("the feedback was merged into the assessment, which is the result to report").isEqualTo(assessment.getId());
        assertThat(resultRepository.findById(leftover.getId())).as("the abandoned aggregate is deleted").isEmpty();
        assertThat(programmingSubmissionRepository.findProgrammingSubmissionWithResultsById(submission.getId()).orElseThrow().getLatestResult().getId())
                .as("the assessment is the submission's latest result").isEqualTo(assessment.getId());
    }

    /** Finalizing a newer build deletes only unlinked leftovers; the linked aggregate of a build in progress is kept. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheAggregateOfAnOverlappingBuildInProgressIsKept() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000017";
        submissionOf(participation, commitHash);

        // the older build merged its container and has not finalized yet; an even older build was interrupted before its
        // job was linked
        Result abandoned = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null).result();
        Result running = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("running-0", "running", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, running));
        Result newer = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("newer-0", "newer", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, newer));

        programmingExerciseGradingService.finalizeContainerResult(newer.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(resultRepository.findById(abandoned.getId())).as("no job links to the abandoned aggregate").isEmpty();
        assertThat(resultRepository.findById(running.getId())).as("the running build keeps its aggregate").isPresent();
        assertThat(programmingExerciseGradingService.finalizeContainerResult(running.getId(), participation, true, false, ZonedDateTime.now()).getCompletionDate()).isNotNull();
    }

    /** A build whose containers reported no test is finalized with a score of zero, so students see a failed build, not no result. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testABuildWithoutTestFeedbackIsFinalizedWithAScoreOfZero() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000098";
        submissionOf(participation, commitHash);
        // neither container compiles, so neither reports a test case
        var first = programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "container_a does not compile"), true, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("no-tests-0", "no-tests", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, first.result(), true));
        var second = programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "container_b does not compile"), true, "container_b",
                first.result().getId());
        buildJobRepository.save(new BuildJob(buildJobFor("no-tests-1", "no-tests", participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, second.result(), true));

        programmingExerciseGradingService.finalizeContainerResult(first.result().getId(), participation, true, true, ZonedDateTime.now());

        Result finalizedResult = resultRepository.findById(first.result().getId()).orElseThrow();
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.getScore()).as("the stored result carries a score").isZero();
        assertThat(finalizedResult.isSuccessful()).isFalse();
    }

    /**
     * A container that creates its group's aggregate holds the participation's lock until its job links to it, so a newer
     * build's finalization waits instead of deleting the unlinked aggregate as abandoned. The container's job was declared
     * missing while its result waited to be processed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheFinalizationOfANewerBuildWaitsForAContainerThatLinksItsAggregate() throws Exception {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000099";
        submissionOf(participation, commitHash);
        ZonedDateTime submissionDate = ZonedDateTime.now().minusMinutes(10);
        // the older build's container has created the aggregate, and its job is not linked to it yet
        Result aggregateInTheMaking = programmingExerciseGradingService.appendContainerResult(participation, passedResult(commitHash), true, "container_a", null).result();
        saveGroupJob("linking-0", "linking", participation, commitHash, BuildStatus.MISSING, null, false, submissionDate, 1);
        // a newer build of the same commit is complete, and the sweep finalizes it
        Result newer = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null).result();
        saveGroupJob("complete-0", "complete", participation, commitHash, BuildStatus.SUCCESSFUL, newer, false, submissionDate, 0);
        saveGroupJob("complete-1", "complete", participation, commitHash, BuildStatus.SUCCESSFUL, newer, false, submissionDate, 0);

        DistributedMap<String, Boolean> locks = distributedDataAccessService.getResultAggregationLockMap();
        String participationLock = LocalCIResultProcessingService.participationLockKey(participation.getId());
        SecurityContext securityContext = SecurityContextHolder.getContext();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch linkAllowed = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> linking = executor.submit(() -> {
                locks.lock(participationLock);
                try {
                    locked.countDown();
                    linkAllowed.await();
                    BuildJob job = buildJobRepository.findByBuildJobId("linking-0").orElseThrow();
                    job.setBuildStatus(BuildStatus.SUCCESSFUL);
                    job.setResult(aggregateInTheMaking);
                    buildJobRepository.save(job);
                }
                finally {
                    locks.unlock(participationLock);
                }
                return null;
            });
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Integer> sweeping = executor.submit(() -> {
                SecurityContextHolder.setContext(securityContext);
                return sweep();
            });

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).until(() -> !sweeping.isDone());
            linkAllowed.countDown();

            assertThat(sweeping.get(30, TimeUnit.SECONDS)).as("the newer build is finalized once the container linked its job").isEqualTo(1);
            linking.get(30, TimeUnit.SECONDS);
        }
        finally {
            linkAllowed.countDown();
            executor.shutdownNow();
        }
        assertThat(resultRepository.findById(aggregateInTheMaking.getId())).as("the aggregate in the making is kept").isPresent();
    }

    /** A late BUILDING event leaves a finished job finished and still starts a queued one. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testALateBuildingEventDoesNotReopenAFinishedContainerJob() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String commitHash = "abcdefabcdefabcdefabcdefabcdefabcdefabcd";
        buildJobRepository.save(new BuildJob(buildJobFor("late-0", "late", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, null));
        buildJobRepository.save(new BuildJob(buildJobFor("late-1", "late", participation, commitHash, "container_b"), BuildStatus.QUEUED, null));

        ZonedDateTime buildStartDate = ZonedDateTime.now();
        buildJobRepository.updateBuildJobStatusWithBuildStartDate("late-0", BuildStatus.BUILDING, buildStartDate);
        buildJobRepository.updateBuildJobStatusWithBuildStartDate("late-1", BuildStatus.BUILDING, buildStartDate);

        assertThat(buildJobRepository.findByBuildJobId("late-0")).map(BuildJob::getBuildStatus).as("a finished job stays finished").contains(BuildStatus.SUCCESSFUL);
        assertThat(buildJobRepository.findByBuildJobId("late-1")).map(BuildJob::getBuildStatus).as("a queued job starts building").contains(BuildStatus.BUILDING);
        assertThat(buildJobRepository.findAllByBuildGroupId("late")).filteredOn(job -> LocalCIResultProcessingService.FINISHED_BUILD_STATUSES.contains(job.getBuildStatus()))
                .hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAJobThatFinishedMeanwhileIsNotMarkedAsMissing() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String commitHash = "abcdefabcdefabcdefabcdefabcdefabcdefabce";
        buildJobRepository.save(new BuildJob(buildJobFor("stale-0", "stale", participation, commitHash, "container_a"), BuildStatus.TIMEOUT, null));
        buildJobRepository.save(new BuildJob(buildJobFor("stale-1", "stale", participation, commitHash, "container_b"), BuildStatus.BUILDING, null));

        buildJobRepository.updateBuildJobStatus("stale-0", BuildStatus.MISSING);
        buildJobRepository.updateBuildJobStatus("stale-1", BuildStatus.MISSING);

        assertThat(buildJobRepository.findByBuildJobId("stale-0")).map(BuildJob::getBuildStatus).as("a finished job stays finished").contains(BuildStatus.TIMEOUT);
        assertThat(buildJobRepository.findByBuildJobId("stale-1")).map(BuildJob::getBuildStatus).as("a pending job is marked as missing").contains(BuildStatus.MISSING);
    }

    /** A merged result's logs are available when any one of its jobs has a log file. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheLogsOfAMergedResultAreAvailableWhenOneOfItsJobsHasALogFile() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000019";
        submissionOf(participation, commitHash);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null).result();
        String group = "avail-" + participation.getId();
        for (int container = 0; container < 3; container++) {
            buildJobRepository
                    .save(new BuildJob(buildJobFor(group + "-" + container, group, participation, commitHash, "container_" + container), BuildStatus.SUCCESSFUL, aggregatedResult));
        }
        // only the second container wrote a log file
        buildLogEntryService.saveBuildLogsToFile(List.of(new BuildLogDTO(ZonedDateTime.now(), "log of the second container")), group + "-1", programmingExercise);

        assertThat(resultService.getLogsAvailabilityForResults(participation.getId())).containsEntry(aggregatedResult.getId(), group + "-1");
    }

    /** Each build merged into an assessment deletes its aggregate and relinks its jobs to the assessment. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testABuildMergedIntoTheAssessmentLeavesTheAssessmentAsTheLatestResult() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        ProgrammingSubmission submission = submissionOf(participation, "0000000000000000000000000000000000000011");
        Result assessment = assessmentOf(submission, ZonedDateTime.now().minusMinutes(10));
        String commitHash = submission.getCommitHash();

        for (int build = 1; build <= 2; build++) {
            var passingJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("testClass[SortStrategy]", List.of())));
            BuildResult containerResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(passingJob), null, null, false, 0);
            Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, containerResult, true, "container_a", null).result();
            buildJobRepository.save(
                    new BuildJob(buildJobFor("assess-" + build + "-0", "assess-" + build, participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

            Result reportedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());

            assertThat(reportedResult.getId()).as("build %d merges into the assessment", build).isEqualTo(assessment.getId());
            assertThat(resultRepository.findById(aggregatedResult.getId())).as("the aggregate of build %d is deleted", build).isEmpty();
            assertThat(buildJobRepository.findAllByBuildGroupId("assess-" + build)).extracting(job -> job.getResult().getId()).containsExactly(assessment.getId());
            // what the build overview is sent after the finalization: the job with the assessment, not with the deleted aggregate
            assertThat(buildJobRepository.findWithDataByBuildGroupId("assess-" + build)).extracting(job -> FinishedBuildJobDTO.of(job).submissionResult().id())
                    .containsExactly(assessment.getId());
            assertThat(programmingSubmissionRepository.findProgrammingSubmissionWithResultsById(submission.getId()).orElseThrow().getLatestResult().getId())
                    .as("the assessment stays the submission's latest result after build %d", build).isEqualTo(assessment.getId());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheLogsOfAFailedBuildMoveToTheAssessmentItWasMergedInto() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000013";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);
        Result assessment = assessmentOf(submission, ZonedDateTime.now().minusMinutes(10));

        var appended = programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "container_a failed"), false, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("logs-0", "logs", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, appended.result(), true));
        programmingExerciseGradingService.finalizeContainerResult(appended.result().getId(), participation, true, true, ZonedDateTime.now());

        ProgrammingSubmission reloaded = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(buildLogEntryService.getBuildLogs(reloaded, assessment.getId())).extracting(BuildLogEntry::getLog, BuildLogEntry::getContainerName)
                .containsExactly(tuple("container_a failed", "container_a"));
        assertThat(buildLogEntryService.getBuildLogs(reloaded, appended.result().getId())).isEmpty();
    }

    /** A draft has no completion date either; the sweep must not finalize a group merged into it. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheSweepLeavesAGroupMergedIntoADraftAssessmentAlone() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000014";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);
        Result draft = assessmentOf(submission, null);

        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null).result();
        saveFinishedJob(buildJobFor("draft-0", "draft", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult, ZonedDateTime.now().minusMinutes(5));
        Result reportedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());
        assertThat(reportedResult.getId()).isEqualTo(draft.getId());

        assertThat(sweep()).as("the draft is not an aggregate in progress").isZero();
        assertThat(resultRepository.findById(draft.getId()).orElseThrow().getCompletionDate()).as("the draft stays open").isNull();
    }

    /** runs the sweep with the retry policy of the missing-job service: a missing job is retried three times, within an hour */
    private int sweep() {
        return localCIResultProcessingService.finalizeCompletedBuildGroups(3, ZonedDateTime.now().minusHours(1));
    }

    /** a group of two whose first job reported the given result and links to the returned aggregate, while the second job went missing */
    private Result groupThatLostAContainer(String buildGroupId, ProgrammingExerciseStudentParticipation participation, @Nullable String commitHashOfJobs,
            BuildResult reportedResult, ZonedDateTime submissionDate, int retryCountOfMissingJob) {
        var appended = programmingExerciseGradingService.appendContainerResult(participation, reportedResult, true, "container_a", null);
        saveGroupJob(buildGroupId + "-0", buildGroupId, participation, commitHashOfJobs, BuildStatus.SUCCESSFUL, appended.result(), appended.containerFailed(), submissionDate, 0);
        saveGroupJob(buildGroupId + "-1", buildGroupId, participation, commitHashOfJobs, BuildStatus.MISSING, null, false, submissionDate, retryCountOfMissingJob);
        return appended.result();
    }

    /** saves a job of a build group as the trigger, the result processing and the missing-job check leave it */
    private void saveGroupJob(String id, String buildGroupId, ProgrammingExerciseParticipation participation, @Nullable String commitHash, BuildStatus buildStatus,
            @Nullable Result result, boolean buildFailed, ZonedDateTime submissionDate, int retryCount) {
        BuildJob job = new BuildJob(buildJobFor(id, buildGroupId, participation, commitHash, "container"), buildStatus, result, buildFailed);
        job.setBuildSubmissionDate(submissionDate);
        job.setRetryCount(retryCount);
        // a job that reported did so minutes ago, which is longer than the grace period of the sweep
        job.setBuildCompletionDate(LocalCIResultProcessingService.FINISHED_BUILD_STATUSES.contains(buildStatus) ? ZonedDateTime.now().minusMinutes(5) : null);
        buildJobRepository.save(job);
    }

    /** a container result that built and passed one of the exercise's test cases */
    private static BuildResult passedResult(String commitHash) {
        var passingJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("testClass[SortStrategy]", List.of())));
        return new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(passingJob), null, null, false, 0);
    }

    /** The sweep neither finalizes nor deletes the result of a group whose missing job is still to be retried. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheSweepLeavesAGroupAloneWhoseMissingJobIsStillRetried() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000020";
        submissionOf(participation, commitHash);
        Result aggregatedResult = groupThatLostAContainer("retried", participation, commitHash, passedResult(commitHash), ZonedDateTime.now().minusMinutes(10), 0);

        assertThat(sweep()).isZero();

        assertThat(resultRepository.findById(aggregatedResult.getId()).orElseThrow().getCompletionDate()).as("the result waits for the retry").isNull();
    }

    /** A group whose missing job ran out of retries or left the retry window is finalized, and the lost container counts as failed to build. */
    @ParameterizedTest
    @CsvSource({ "3, 10", "0, 90" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheSweepFinalizesAGroupWhoseMissingJobIsNotRetriedAnyMore(int retryCount, int minutesSinceSubmission) {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000021";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);
        Result aggregatedResult = groupThatLostAContainer("given-up", participation, commitHash, passedResult(commitHash), ZonedDateTime.now().minusMinutes(minutesSinceSubmission),
                retryCount);

        assertThat(sweep()).isEqualTo(1);

        Result finalizedResult = resultRepository.findById(aggregatedResult.getId()).orElseThrow();
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.isSuccessful()).isFalse();
        assertThat(finalizedResult.getPassedTestCaseCount()).as("the feedback of the container that reported is kept").isEqualTo(1);
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("the lost container did not build").isTrue();
        assertThat(buildJobRepository.findByBuildJobId("given-up-1")).map(BuildJob::getBuildStatus).as("the lost job is closed").contains(BuildStatus.ERROR);
        assertThat(sweep()).as("nothing left to close").isZero();
    }

    /** A later job of another commit stops the retry of a missing job without replacing its build, so the sweep finalizes the group. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheSweepFinalizesAGroupThatLostAContainerWhenAnotherCommitWasPushed() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000022";
        submissionOf(participation, commitHash);
        ZonedDateTime submissionDate = ZonedDateTime.now().minusMinutes(10);
        Result aggregatedResult = groupThatLostAContainer("pushed", participation, commitHash, passedResult(commitHash), submissionDate, 0);
        // the build of the next push, still queued
        saveGroupJob("next-0", "next", participation, "0000000000000000000000000000000000000023", BuildStatus.QUEUED, null, false, submissionDate.plusMinutes(1), 0);

        // the scheduled method, with the missing-job service's own retry policy
        localCIMissingJobService.finalizeCompletedBuildGroups();

        assertThat(resultRepository.findById(aggregatedResult.getId()).orElseThrow().getCompletionDate()).isNotNull();
    }

    /** A group whose lost job was retried as a new group of the same commit is replaced: the sweep deletes its result and logs and unlinks its jobs. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheSweepDeletesTheResultOfAGroupThatLostAContainerAndWasRetried() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000024";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);
        ZonedDateTime submissionDate = ZonedDateTime.now().minusMinutes(10);
        // the container that reported failed to build, so the group's result has build logs
        Result aggregatedResult = groupThatLostAContainer("replaced", participation, commitHash, failedResult(commitHash, "container_a failed"), submissionDate, 1);
        ProgrammingSubmission reloaded = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(buildLogEntryService.getBuildLogs(reloaded, aggregatedResult.getId())).isNotEmpty();
        // the retry: the same commit, triggered again as a new group
        saveGroupJob("retry-0", "retry", participation, commitHash, BuildStatus.QUEUED, null, false, submissionDate.plusMinutes(6), 1);
        saveGroupJob("retry-1", "retry", participation, commitHash, BuildStatus.QUEUED, null, false, submissionDate.plusMinutes(6), 1);

        assertThat(sweep()).as("the replaced group is not finalized").isZero();

        assertThat(resultRepository.findById(aggregatedResult.getId())).as("the result of the replaced build is deleted").isEmpty();
        assertThat(buildJobRepository.findAllByBuildGroupId("replaced")).hasSize(2).extracting(job -> job.getResult() == null ? null : job.getResult().getId())
                .as("its jobs no longer link to a result").containsOnlyNulls();
        assertThat(buildJobRepository.findByBuildJobId("replaced-1")).map(BuildJob::getBuildStatus).as("the lost job is cancelled").contains(BuildStatus.CANCELLED);
        assertThat(buildLogEntryService.getBuildLogs(reloaded, aggregatedResult.getId())).as("its logs are deleted with it").isEmpty();
        assertThat(programmingSubmissionRepository.findProgrammingSubmissionWithResultsById(submission.getId()).orElseThrow().getResults()).isEmpty();
    }

    /** A build triggered without a commit, as after an auxiliary push, is replaced by its retry, which carries no commit either. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testABuildWithoutACommitIsReplacedByItsRetry() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000025";
        submissionOf(participation, commitHash);
        ZonedDateTime submissionDate = ZonedDateTime.now().minusMinutes(10);
        Result aggregatedResult = groupThatLostAContainer("no-commit", participation, null, passedResult(commitHash), submissionDate, 1);
        saveGroupJob("no-commit-retry-0", "no-commit-retry", participation, null, BuildStatus.QUEUED, null, false, submissionDate.plusMinutes(6), 1);

        assertThat(sweep()).isZero();

        assertThat(resultRepository.findById(aggregatedResult.getId())).isEmpty();
    }

    /** A late result of a lost container whose replaced group the sweep deleted starts no new result, and its job stays cancelled. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testALateResultOfAReplacedGroupDoesNotStartANewResult() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000026";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);
        ZonedDateTime submissionDate = ZonedDateTime.now().minusMinutes(10);
        groupThatLostAContainer("late", participation, commitHash, passedResult(commitHash), submissionDate, 1);
        saveGroupJob("late-retry-0", "late-retry", participation, commitHash, BuildStatus.QUEUED, null, false, submissionDate.plusMinutes(6), 1);
        assertThat(sweep()).isZero();
        assertThat(programmingSubmissionRepository.findProgrammingSubmissionWithResultsById(submission.getId()).orElseThrow().getResults()).isEmpty();

        // the lost container reports after all; its logs are still stored, which shows that the result was processed
        BuildJobQueueItem lateJob = buildJobFor("late-1", "late", participation, commitHash, "container_b");
        var lateLogs = List.of(new BuildLogDTO(ZonedDateTime.now(), "container_b finished late"));
        distributedDataAccessService.getDistributedBuildResultQueue().add(new ResultQueueItem(passedResult(commitHash), lateJob, lateLogs, null));

        await().atMost(30, TimeUnit.SECONDS).until(() -> buildLogEntryService.retrieveBuildLogsFromFileForBuildJob("late-1") != null);
        assertThat(buildJobRepository.findByBuildJobId("late-1")).map(BuildJob::getBuildStatus).as("the late job stays cancelled").contains(BuildStatus.CANCELLED);
        assertThat(buildJobRepository.findByBuildJobId("late-1").map(BuildJob::getResult).map(Result::getId)).as("the late job links to no result").isEmpty();
        assertThat(programmingSubmissionRepository.findProgrammingSubmissionWithResultsById(submission.getId()).orElseThrow().getResults())
                .as("no result is started for the replaced build").isEmpty();
    }

    /** A late result of a missing job is merged while the sweep has not closed its group, even though its build was retried. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testALateResultOfAMissingJobWhoseGroupIsStillOpenIsMerged() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000027";
        submissionOf(participation, commitHash);
        ZonedDateTime submissionDate = ZonedDateTime.now().minusMinutes(10);
        // the first container was declared missing, the second is still building, and the build was retried
        saveGroupJob("unclosed-0", "unclosed", participation, commitHash, BuildStatus.MISSING, null, false, submissionDate, 1);
        saveGroupJob("unclosed-1", "unclosed", participation, commitHash, BuildStatus.BUILDING, null, false, submissionDate, 0);
        saveGroupJob("unclosed-retry-0", "unclosed-retry", participation, commitHash, BuildStatus.QUEUED, null, false, submissionDate.plusMinutes(6), 1);

        BuildJobQueueItem lateJob = buildJobFor("unclosed-0", "unclosed", participation, commitHash, "container_a");
        distributedDataAccessService.getDistributedBuildResultQueue().add(new ResultQueueItem(passedResult(commitHash), lateJob, List.of(), null));

        await().atMost(30, TimeUnit.SECONDS).until(() -> buildJobRepository.findByBuildJobId("unclosed-0").map(BuildJob::getBuildStatus).orElse(null) == BuildStatus.SUCCESSFUL);
        Long aggregatedResultId = buildJobRepository.findByBuildJobId("unclosed-0").map(BuildJob::getResult).map(Result::getId).orElseThrow();
        assertThat(resultRepository.findById(aggregatedResultId).orElseThrow().getCompletionDate()).as("the result waits for the second container").isNull();
    }

    /** A test case two containers report is kept once, and a failed report wins in either completion order. */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAFailedReportOfASharedTestCaseStandsInEitherCompletionOrder(boolean failingContainerFirst) {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = failingContainerFirst ? "0000000000000000000000000000000000000015" : "0000000000000000000000000000000000000016";
        submissionOf(participation, commitHash);
        String group = "shared-" + failingContainerFirst;
        String sharedTest = "testClass[SortStrategy]";
        var passed = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO(sharedTest, List.of())));
        var failed = new LocalCIJobDTO(List.of(new LocalCITestJobDTO(sharedTest, List.of("the shared check failed"))), List.of());

        BuildResult first = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(failingContainerFirst ? failed : passed), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, first, true, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor(group + "-0", group, participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));
        BuildResult second = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(failingContainerFirst ? passed : failed), null, null, false, 0);
        programmingExerciseGradingService.appendContainerResult(participation, second, true, "container_b", aggregatedResult.getId());
        buildJobRepository.save(new BuildJob(buildJobFor(group + "-1", group, participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResult));

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(testCaseFeedbackRepository.findWithTestCaseAndMessageByResultId(aggregatedResult.getId()))
                .filteredOn(feedback -> sharedTest.equals(feedback.getTestCase().getTestName())).extracting(TestCaseFeedback::isPositive).containsExactly(false);
        assertThat(finalizedResult.getPassedTestCaseCount()).isZero();
        assertThat(finalizedResult.getScore()).isZero();
    }

    /** a tutor's semi-automatic assessment of the submission, completed at the given date or a draft if it is null */
    private Result assessmentOf(ProgrammingSubmission submission, @Nullable ZonedDateTime completionDate) {
        Result assessment = new Result();
        assessment.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        assessment.setCompletionDate(completionDate);
        assessment.setSubmission(submission);
        assessment.setExerciseId(programmingExercise.getId());
        return resultRepository.save(assessment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAnOlderBuildFinalizingLastDoesNotOverwriteTheNewerBuildsOutcome() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000012";
        long submissionId = submissionOf(participation, commitHash).getId();

        var older = programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "older build failed"), false, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("order-a-0", "order-a", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, older.result(), true));
        var newer = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("order-b-0", "order-b", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, newer.result(), false));

        programmingExerciseGradingService.finalizeContainerResult(newer.result().getId(), participation, true, anyContainerFailedToBuild("order-b"), ZonedDateTime.now());
        assertThat(programmingSubmissionRepository.findById(submissionId).orElseThrow().isBuildFailed()).isFalse();

        programmingExerciseGradingService.finalizeContainerResult(older.result().getId(), participation, true, anyContainerFailedToBuild("order-a"), ZonedDateTime.now());
        assertThat(programmingSubmissionRepository.findById(submissionId).orElseThrow().isBuildFailed()).as("the older build does not overwrite the newer build's outcome")
                .isFalse();
    }

    /** what the result processing derives for a group when it finalizes it, see LocalCIResultProcessingService#finalizeIfGroupComplete */
    private boolean anyContainerFailedToBuild(String buildGroupId) {
        return buildJobRepository.findAllByBuildGroupId(buildGroupId).stream().anyMatch(BuildJob::isBuildFailed);
    }

    private ProgrammingSubmission submissionOf(ProgrammingExerciseStudentParticipation participation, String commitHash) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(participation);
        return programmingSubmissionRepository.save(submission);
    }

    private void saveFinishedJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, Result result, ZonedDateTime completionDate) {
        BuildJob buildJob = new BuildJob(queueItem, buildStatus, result);
        buildJob.setBuildCompletionDate(completionDate);
        buildJobRepository.save(buildJob);
    }

    /** A container job that failed without failing the build still skips the solution's test-case reconciliation. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSolutionTestCasesAreKeptWhenAContainerJobFailedWithoutFailingTheBuild() {
        solutionParticipation.setProgrammingExercise(programmingExercise);
        String commitHash = "000000000000000000000000000000000000000c";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(solutionParticipation);
        submission = programmingSubmissionRepository.save(submission);
        testCaseRepository.save(new ProgrammingExerciseTestCase().testName("instructorTest").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCaseRepository.save(new ProgrammingExerciseTestCase().testName("studentTest").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));

        // the student container's merge failed: its job is ERROR and unlinked
        var instructorJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("instructorTest", List.of())));
        BuildResult instructorResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(instructorJob), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(solutionParticipation, instructorResult, true, "instructor_tests", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("recon-0", "recon", solutionParticipation, commitHash, "instructor_tests"), BuildStatus.SUCCESSFUL, aggregatedResult));
        buildJobRepository.save(new BuildJob(buildJobFor("recon-1", "recon", solutionParticipation, commitHash, "student_tests"), BuildStatus.ERROR, null));
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("a failed merge does not fail the build").isFalse();

        List<String> activeBefore = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true).stream().map(ProgrammingExerciseTestCase::getTestName).toList();

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), solutionParticipation, false, false, ZonedDateTime.now());

        assertThat(finalizedResult.isSuccessful()).isFalse();
        assertThat(testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true)).extracting(ProgrammingExerciseTestCase::getTestName)
                .as("no test case is deactivated, the unreported one of the failed container included").containsExactlyInAnyOrderElementsOf(activeBefore).contains("studentTest");
    }

    /**
     * The solution and template participations store no exercise, so the logs of a container must be filed under the
     * exercise of the aggregated result, where the feedback dialog reads them. With an earlier build of the commit, the
     * submission a build result is matched to carries that build's result as a skeleton whose exercise id is 0; on the
     * commit's first build it carries no result at all.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheLogsOfASolutionBuildAreStoredUnderItsExercise(boolean withEarlierBuild) {
        solutionParticipation.setProgrammingExercise(programmingExercise);
        String commitHash = "000000000000000000000000000000000000000d";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(solutionParticipation);
        submission = programmingSubmissionRepository.save(submission);
        if (withEarlierBuild) {
            Result earlierResult = new Result();
            earlierResult.setAssessmentType(AssessmentType.AUTOMATIC);
            earlierResult.setCompletionDate(ZonedDateTime.now().minusMinutes(10));
            earlierResult.setSubmission(submission);
            earlierResult.setExerciseId(programmingExercise.getId());
            resultRepository.save(earlierResult);
        }

        var appended = programmingExerciseGradingService.appendContainerResult(solutionParticipation, failedResult(commitHash, "solution container failed"), false, "container_a",
                null);

        assertThat(failedBuildLogService.getBuildLogs(programmingExercise.getId(), submission.getId(), appended.result().getId())).as("filed under the exercise")
                .hasValueSatisfying(buildLogs -> assertThat(buildLogs).extracting(BuildLogEntry::getLog, BuildLogEntry::getContainerName)
                        .containsExactly(tuple("solution container failed", "container_a")));
        ProgrammingSubmission reloaded = programmingSubmissionRepository.findProgrammingSubmissionWithResultsById(submission.getId()).orElseThrow();
        assertThat(buildLogEntryService.getBuildLogs(reloaded, appended.result().getId())).as("found where the feedback dialog reads them").extracting(BuildLogEntry::getLog)
                .containsExactly("solution container failed");

        programmingExerciseGradingService.discardContainerResult(appended.result().getId());
        assertThat(failedBuildLogService.getBuildLogs(programmingExercise.getId(), submission.getId(), appended.result().getId())).as("deleted from there with the result")
                .isEmpty();
    }

    /** Two overlapping builds of one commit neither hide nor inherit each other's build failure. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheBuildFailedFlagFollowsTheGroupBeingFinalizedWhenTwoBuildsOverlap() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "000000000000000000000000000000000000000d";
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepository.save(submission);
        long submissionId = submission.getId();

        // the first build: its only container fails to build
        BuildResult failedResult = new BuildResult(null, commitHash, commitHash, false, ZonedDateTime.now(), List.of(), List.of(new BuildLogDTO(ZonedDateTime.now(), "failed")),
                null, true, 1);
        var appendedFirst = programmingExerciseGradingService.appendContainerResult(participation, failedResult, false, "container_a", null);
        assertThat(appendedFirst.containerFailed()).isTrue();
        buildJobRepository
                .save(new BuildJob(buildJobFor("overlap-a-0", "overlap-a", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, appendedFirst.result(), true));

        // a re-triggered build of the same commit merges before the first one finalizes, and its container builds fine
        BuildResult okResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        var appendedSecond = programmingExerciseGradingService.appendContainerResult(participation, okResult, false, "container_a", null);
        assertThat(appendedSecond.containerFailed()).isFalse();
        buildJobRepository
                .save(new BuildJob(buildJobFor("overlap-b-0", "overlap-b", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, appendedSecond.result(), false));

        Result finalizedFirst = programmingExerciseGradingService.finalizeContainerResult(appendedFirst.result().getId(), participation, true,
                anyContainerFailedToBuild("overlap-a"), ZonedDateTime.now());
        assertThat(finalizedFirst.isSuccessful()).isFalse();
        assertThat(programmingSubmissionRepository.findById(submissionId).orElseThrow().isBuildFailed()).as("the first build's failure is not hidden by the second build").isTrue();

        programmingExerciseGradingService.finalizeContainerResult(appendedSecond.result().getId(), participation, true, anyContainerFailedToBuild("overlap-b"),
                ZonedDateTime.now());
        assertThat(programmingSubmissionRepository.findById(submissionId).orElseThrow().isBuildFailed()).as("the second build does not inherit the first build's failure")
                .isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEveryBuildKeepsItsLogsUnderItsOwnResult() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "000000000000000000000000000000000000000e";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);
        ProgrammingSubmission reloaded = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();

        // an earlier build: container_a failed and left its logs, and the build is over
        var earlier = programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "container_a failed earlier"), false, "container_a", null);
        programmingExerciseGradingService.finalizeContainerResult(earlier.result().getId(), participation, true, true, ZonedDateTime.now());

        // the new build: container_a succeeds and merges first, then container_b fails
        var appendedA = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null);
        programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "container_b failed now"), false, "container_b",
                appendedA.result().getId());

        assertThat(buildLogEntryService.getBuildLogs(reloaded, appendedA.result().getId())).extracting(BuildLogEntry::getLog).containsExactly("container_b failed now");
        assertThat(buildLogEntryService.getBuildLogs(reloaded, appendedA.result().getId())).extracting(BuildLogEntry::getContainerName).containsExactly("container_b");
        assertThat(buildLogEntryService.getBuildLogs(reloaded, earlier.result().getId())).extracting(BuildLogEntry::getLog).containsExactly("container_a failed earlier");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheLogsOfAnOverlappingBuildAreNeitherErasedNorShown() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "000000000000000000000000000000000000000f";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);
        ProgrammingSubmission reloaded = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();

        // the first build: container_a fails and the build is still merging when the second build starts
        var first = programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "first build failed"), false, "container_a", null);
        var second = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null);
        assertThat(buildLogEntryService.getBuildLogs(reloaded, first.result().getId())).as("a build in progress keeps its logs").extracting(BuildLogEntry::getLog)
                .containsExactly("first build failed");
        programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "second build failed"), false, "container_b", second.result().getId());

        assertThat(buildLogEntryService.getBuildLogs(reloaded, first.result().getId())).extracting(BuildLogEntry::getLog).containsExactly("first build failed");
        assertThat(buildLogEntryService.getBuildLogs(reloaded, second.result().getId())).extracting(BuildLogEntry::getLog).containsExactly("second build failed");
    }

    /** A compile-only container that timed out has no exit code, so it counts as failed to build and keeps its logs. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testACompileOnlyContainerThatTimedOutCountsAsFailedToBuildAndKeepsItsLogs() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000018";
        ProgrammingSubmission submission = submissionOf(participation, commitHash);

        var passingJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("testClass[SortStrategy]", List.of())));
        BuildResult testsResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(passingJob), null, null, false, 0);
        var tests = programmingExerciseGradingService.appendContainerResult(participation, testsResult, true, "tests", null);
        buildJobRepository
                .save(new BuildJob(buildJobFor("timeout-0", "timeout", participation, commitHash, "tests"), BuildStatus.SUCCESSFUL, tests.result(), tests.containerFailed()));
        // the result the build agent reports for a job that timed out: the logs so far, no exit code
        BuildResult timedOutResult = new BuildResult(null, commitHash, commitHash, List.of(new BuildLogDTO(ZonedDateTime.now(), "compiling ...")), false);
        var compile = programmingExerciseGradingService.appendContainerResult(participation, timedOutResult, false, "compile", tests.result().getId());
        buildJobRepository
                .save(new BuildJob(buildJobFor("timeout-1", "timeout", participation, commitHash, "compile"), BuildStatus.TIMEOUT, compile.result(), compile.containerFailed()));

        assertThat(tests.containerFailed()).isFalse();
        assertThat(compile.containerFailed()).as("a job without an exit code did not build").isTrue();

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(tests.result().getId(), participation, false, anyContainerFailedToBuild("timeout"),
                ZonedDateTime.now());

        ProgrammingSubmission reloaded = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.isBuildFailed()).isTrue();
        assertThat(finalizedResult.isSuccessful()).isFalse();
        assertThat(finalizedResult.getPassedTestCaseCount()).as("the sibling's feedback is kept").isEqualTo(1);
        assertThat(buildLogEntryService.getBuildLogs(reloaded, tests.result().getId())).extracting(BuildLogEntry::getLog, BuildLogEntry::getContainerName)
                .containsExactly(tuple("compiling ...", "compile"));
    }

    /** a container result that failed to build with one log line, as a crashed container reports */
    private static BuildResult failedResult(String commitHash, String logLine) {
        return new BuildResult(null, commitHash, commitHash, false, ZonedDateTime.now(), List.of(), List.of(new BuildLogDTO(ZonedDateTime.now(), logLine)), null, true, 1);
    }

    /** a container result that built fine and reported no test */
    private static BuildResult okResult(String commitHash) {
        return new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
    }

    private BuildJobQueueItem buildJobFor(String id, String buildGroupId, ProgrammingExerciseParticipation participation, String commitHash, String containerName) {
        BuildAgentDTO buildAgent = new BuildAgentDTO(null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, null, null, null, null, null);
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 0);
        BuildConfig jobBuildConfig = new BuildConfig(null, "image", commitHash, commitHash, commitHash, null, null, null, false, false, null, 0, null, null, null, null);
        // the expected count is only read by the result processing, which most of these tests bypass by driving the grading service directly
        var buildGroup = new BuildJobQueueItem.BuildGroupMembership(buildGroupId, 2, containerName);
        return new BuildJobQueueItem(id, "plan", buildAgent, participation.getId(), programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId(),
                programmingExercise.getId(), 0, 1, null, repositoryInfo, jobTimingInfo, jobBuildConfig, null, buildGroup, null);
    }
}
