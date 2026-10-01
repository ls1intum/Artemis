package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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

        // First container finishes: its feedback is appended to a new, still in-progress result (no completion date yet).
        BuildResult resultA = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, resultA, false, "container_a", null).result();
        assertThat(aggregatedResult).isNotNull();
        assertThat(aggregatedResult.getCompletionDate()).as("the result stays in progress until every container finished").isNull();
        buildJobRepository.save(new BuildJob(buildJobFor("merge-0", "merge", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        // Second container finishes: it appends to the same result rather than creating a second one.
        BuildResult resultB = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResultAgain = programmingExerciseGradingService.appendContainerResult(participation, resultB, false, "container_b", aggregatedResult.getId()).result();
        assertThat(aggregatedResultAgain.getId()).as("all containers of one submission share a single result").isEqualTo(aggregatedResult.getId());
        buildJobRepository.save(new BuildJob(buildJobFor("merge-1", "merge", participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResultAgain));

        // Both containers have finished, counted over the build jobs of the build group, and both link to the shared result.
        assertThat(buildJobRepository.findAllByBuildGroupId("merge")).hasSize(2).allSatisfy(job -> {
            assertThat(job.getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
            assertThat(job.getResult()).isNotNull();
        });
        assertThat(buildJobRepository.countByResultId(aggregatedResultAgain.getId())).isEqualTo(2);

        // Finalizing marks the aggregated result complete. Neither container reported a test case, so no relevant test
        // case passed: the result is not successful, although every container ran.
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain.getId(), participation, true, false, ZonedDateTime.now());
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.isSuccessful()).isFalse();
    }

    /**
     * The lock-repository policy's result-time step only flips the rated flag on the result object; it is the save in
     * {@code finalizeContainerResult} that persists it. This suite calls finalize OUTSIDE a transaction on purpose: if the
     * save ran before the policies, the flag would only reach the database through the caller's dirty checking, and a
     * caller like this one would silently lose it.
     */
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

        // The submission being built now, with its single container appended and linked.
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

        // Persisted, not merely set on the returned object.
        Result persistedResult = resultRepository.findById(finalizedResult.getId()).orElseThrow();
        assertThat(persistedResult.getCompletionDate()).isNotNull();
        assertThat(persistedResult.isRated()).isFalse();
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

        // the instructor container finishes with a passing test
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

        // the instructor container's feedback survives the student container's crash
        assertThat(aggregatedResultAgain.getId()).isEqualTo(aggregatedResult.getId());
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(aggregatedResultAgain.getId())))
                .as("the instructor feedback is not lost when a sibling container crashes").isNotEmpty();

        // the crashed container's build logs are preserved and labeled with its container name
        ProgrammingSubmission reloadedSubmission = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        List<BuildLogEntry> buildLogs = buildLogEntryService.getLatestBuildLogs(reloadedSubmission);
        assertThat(buildLogs).anySatisfy(logEntry -> {
            assertThat(logEntry.getContainerName()).isEqualTo("student_tests");
            assertThat(logEntry.getLog()).contains("out of memory");
        });

        // finalizing once both containers finished marks the result complete but not successful
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

    /**
     * The merged result is successful only when every relevant test case passed. Every container ran and built here, but
     * the only test failed, so the finalized result must not be successful.
     */
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

        // the container ran to completion and built, but its only test failed
        var job = new LocalCIJobDTO(List.of(new LocalCITestJobDTO("failingTest", List.of("expected 2 but was 3"))), List.of());
        BuildResult containerResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(job), null, null, false, 0);
        Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, containerResult, true, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("failing-0", "failing", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("the build itself did not fail").isFalse();

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(finalizedResult.getScore()).isLessThan(100.0);
        assertThat(finalizedResult.isSuccessful()).as("a failing test makes the merged result unsuccessful").isFalse();
    }

    /**
     * The merged result is successful when every container ran and built and every relevant test case passed. The test
     * cases are partitioned across the containers here, each reporting only its own share as passed, so it is the union
     * of the containers' feedback that covers every test case: the finalized result must be successful.
     */
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

        // the exercise's registered test cases are partitioned across the two containers, and each container ran and
        // built and reports its share as passed
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

    /**
     * The containers of one build attempt merge under their build group, which the result processing identifies through
     * the group's build jobs. A second attempt of the same commit (a retry, or a re-push while the first attempt is still
     * merging) is a new group: its first container starts a result of its own instead of joining the earlier attempt's
     * still-open aggregate, so the two attempts never mix their feedback or their completion counts.
     */
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

        // Finalizing the second attempt leaves the first attempt's aggregate untouched and still in progress.
        Result finalizedSecond = programmingExerciseGradingService.finalizeContainerResult(secondAggregate.getId(), participation, true, false, ZonedDateTime.now());
        assertThat(finalizedSecond.getCompletionDate()).isNotNull();
        assertThat(resultRepository.findById(firstAggregate.getId()).orElseThrow().getCompletionDate()).isNull();
    }

    /**
     * The sweep finalizes a build group whose jobs have all finished while its aggregated result stayed in progress, which
     * is what a crash between the last container's link and its finalization leaves behind. A group that still has a
     * container queued is not complete and is left alone, and a second run finds nothing left to do.
     */
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

    /**
     * The scoring adds legacy feedback rows to the finalized result, here the submission penalty. The result is reported
     * to the client as it is, so those rows must carry ids like the typed rows do: the client identifies feedback by id.
     */
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

    /**
     * An attempt whose merge was interrupted between the aggregate's insert and its job's link leaves an automatic result
     * in progress on the submission that nothing refers to. It is the submission's newest result, but it must not hide
     * the tutor's assessment from the finalize of the next attempt, whose feedback belongs into that assessment.
     */
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
        // The leftover is deleted along the way. It was newer than the assessment, and with this build's own aggregate gone
        // after the merge it would otherwise stay the submission's latest result and be shown in place of the assessment.
        assertThat(resultRepository.findById(leftover.getId())).as("the abandoned aggregate is deleted").isEmpty();
        assertThat(programmingSubmissionRepository.findProgrammingSubmissionWithResultsById(submission.getId()).orElseThrow().getLatestResult().getId())
                .as("the assessment is the submission's latest result").isEqualTo(assessment.getId());
    }

    /**
     * Only an aggregate that no build job links to is abandoned, and it is only deleted while no job of the participation
     * is pending. The aggregate of an overlapping build that has not finalized yet is in progress as well, but its jobs
     * link to it, so the finalization of a newer build leaves it alone and the older build can still finalize.
     */
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

    /**
     * The first container of a build creates the build's aggregate before it links its job to it. In between, the
     * aggregate is in progress and linked by no job, like an abandoned one, but the container's job is still pending. A
     * newer build of the same submission that finalizes in that moment keeps the aggregate, and the older build finalizes
     * with its container's feedback once the job is linked.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTheAggregateOfAJobThatIsNotLinkedYetIsKept() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000099";
        submissionOf(participation, commitHash);

        // the older build's first container merged its feedback into a new aggregate, but its job is still building
        var passingJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("testClass[SortStrategy]", List.of())));
        BuildResult containerResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(passingJob), null, null, false, 0);
        Result olderAggregate = programmingExerciseGradingService.appendContainerResult(participation, containerResult, true, "container_a", null).result();
        BuildJob pendingJob = buildJobRepository.save(new BuildJob(buildJobFor("pending-0", "pending", participation, commitHash, "container_a"), BuildStatus.BUILDING, null));
        // a newer build of the same submission finalizes in the meantime
        Result newer = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null).result();
        buildJobRepository.save(new BuildJob(buildJobFor("overlap-0", "overlap", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, newer));

        programmingExerciseGradingService.finalizeContainerResult(newer.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(resultRepository.findById(olderAggregate.getId())).as("the aggregate of a pending job is kept").isPresent();

        // the older build's container saves its link, and the build finalizes with the container's feedback
        pendingJob.setBuildStatus(BuildStatus.SUCCESSFUL);
        pendingJob.setResult(olderAggregate);
        buildJobRepository.save(pendingJob);
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(olderAggregate.getId(), participation, true, false, ZonedDateTime.now());

        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.getPassedTestCaseCount()).as("the feedback of the container whose job was pending is kept").isEqualTo(1);
    }

    /**
     * The processing-map event that reports a job as building is delivered asynchronously and can arrive after the job's
     * result has been processed: an agent's event thread that a long build blocks delays it by the length of that build.
     * Reopening the finished job would make its build group look incomplete, so neither the merge of the last container
     * nor the sweep would ever finalize the aggregated result. A job that is still pending must take the transition.
     */
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

    /**
     * The check for missing jobs reads the pending jobs and marks those it cannot find in the queue as missing. A job
     * whose result is processed in between has finished by then and must stay finished: marked as missing, it would be
     * retried although it has a result, and its build group would never count as complete. A job that is still pending
     * takes the transition.
     */
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

    /**
     * The containers of a multi-container build link their jobs to one result. The result's build logs are available
     * when one of those jobs has a log file, whichever of them is looked at last.
     */
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

    /**
     * A build whose feedback is merged into a tutor's assessment leaves no automatic result behind, as on the
     * single-container path: a stored aggregate would be newer than the assessment and would be shown in its place. The
     * build's job links to the assessment instead, and every later build merges into the assessment as well.
     */
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

    /**
     * The logs of a failed build whose feedback is merged into a tutor's assessment are shown for the assessment, as the
     * logs of a failed single-container build are, and nothing is left under the deleted aggregate.
     */
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

    /**
     * A draft assessment has no completion date, like an aggregate still in progress. Once a build's feedback was merged
     * into the draft and its jobs link to it, the sweep over complete groups whose aggregate stayed in progress must
     * leave the group alone: it would otherwise score the draft as if it were the build's aggregate.
     */
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

    /**
     * A build group of two containers that lost one: the first container reported the given result and its job links to
     * the group's aggregated result, the job of the second went missing.
     *
     * @return the aggregated result of the group, still in progress
     */
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
        // the jobs of one build are submitted together
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

    /**
     * A container job that went missing is retried by the missing-job service, which triggers the whole build again. As
     * long as that retry is still to come, the group is left alone: its result is neither finalized nor deleted.
     */
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

    /**
     * Once the missing job is not retried any more, because it ran out of retries or left the retry window, nothing will
     * complete the group. It is finalized with the feedback of the container that reported, and the lost container
     * counts as failed to build, as a container whose job timed out does.
     */
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

    /**
     * A later job of the same participation stops the retry of a missing job. When that job builds another commit, the
     * student pushed again, nothing replaces the old build, and its group is finalized like one that ran out of retries.
     */
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

        // run as the missing-job service schedules it, with the retry policy that service retries by
        localCIMissingJobService.finalizeCompletedBuildGroups();

        assertThat(resultRepository.findById(aggregatedResult.getId()).orElseThrow().getCompletionDate()).isNotNull();
    }

    /**
     * The retry of a missing job triggers the commit again as a new build group, which replaces the old one. Finalizing
     * the old group would show the student the partial outcome of a build whose replacement is still running, so its
     * aggregated result is deleted with its logs, and its jobs lose their link.
     */
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

    /**
     * A build that was triggered without a commit, as the solution build after a push to an auxiliary repository is, is
     * replaced by its retry as well: the retry carries no commit either.
     */
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

    /**
     * The result of the lost container can still arrive after the sweep deleted the result of its replaced group. It must
     * not start a new result: that result would hold this container's feedback alone and be newer than the retry's. The
     * job keeps the status the sweep closed it with.
     */
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

    /**
     * A job can be declared missing while its result still waits to be processed, and its build is then retried. As long
     * as the sweep has not closed the job's group, the result is merged: dropping it would leave the siblings to finalize
     * a result without this container's feedback.
     */
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

    /**
     * A test case that several containers report, as a shared setup phase does, is kept once. When the containers
     * disagree, the failed report stands whichever container finishes first: the outcome and the score of the merged
     * result must not depend on the order in which the containers complete.
     */
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

    /**
     * Two builds of the same commit that overlap can finalize in either order. The submission's build-failed flag
     * follows the newest of their aggregates, so an older build that finalizes last does not overwrite what the newer
     * build wrote.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAnOlderBuildFinalizingLastDoesNotOverwriteTheNewerBuildsOutcome() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setProgrammingExercise(programmingExercise);
        String commitHash = "0000000000000000000000000000000000000012";
        long submissionId = submissionOf(participation, commitHash).getId();

        // the older build: its container failed to build; the newer build of the same commit: its container built fine
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

    /**
     * A solution build reconciles the exercise's test cases over the merged feedback: a test case no container reported
     * is deactivated as removed from the solution. A container whose result could not be merged reported nothing, and
     * its job is recorded as failed without the submission's build-failed flag being touched, so the reconciliation has
     * to be skipped on the job status as well. Otherwise the failed container's test cases would be deactivated as if
     * the solution had lost them, and every student graded afterwards would lose those tests.
     */
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

        // the instructor container reports its test; the student container's result could not be merged, so its job is
        // recorded as failed without a result link, which leaves the build-failed flag untouched
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
     * The build-failed flag of a submission is derived from the jobs of the group being finalized, not from whichever
     * container of whichever build wrote it last: a re-triggered build of the same commit that overlaps with an earlier
     * one must neither hide the earlier build's failure nor inherit it.
     */
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

    /**
     * Every build keeps its logs under its own aggregated result: a container that failed in an earlier build of the same
     * submission and succeeds now does not show its old logs next to the logs of a sibling that fails now, and the
     * earlier build's logs stay available for its result.
     */
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

    /**
     * Two builds of the same commit that overlap share the submission but not their logs: each build's logs are
     * attributed to its aggregated result, so neither build erases the other's logs when it starts, and the logs shown
     * for a result are that build's alone.
     */
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
        // the second build merges its first container while the first build is in progress
        var second = programmingExerciseGradingService.appendContainerResult(participation, okResult(commitHash), false, "container_a", null);
        assertThat(buildLogEntryService.getBuildLogs(reloaded, first.result().getId())).as("a build in progress keeps its logs").extracting(BuildLogEntry::getLog)
                .containsExactly("first build failed");
        programmingExerciseGradingService.appendContainerResult(participation, failedResult(commitHash, "second build failed"), false, "container_b", second.result().getId());

        assertThat(buildLogEntryService.getBuildLogs(reloaded, first.result().getId())).extracting(BuildLogEntry::getLog).containsExactly("first build failed");
        assertThat(buildLogEntryService.getBuildLogs(reloaded, second.result().getId())).extracting(BuildLogEntry::getLog).containsExactly("second build failed");
    }

    /**
     * A container without tests is judged by the exit code of its build script. A job that times out never reports one:
     * the agent constructs its result without an exit code. Such a container did not build, so its logs are kept for
     * the student and the submission is marked as build-failed, next to the feedback of the sibling that succeeded.
     */
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
        // the expected count is only read by the result processing, which these tests bypass by driving the grading service directly
        var buildGroup = new BuildJobQueueItem.BuildGroupMembership(buildGroupId, 2, containerName);
        return new BuildJobQueueItem(id, "plan", buildAgent, participation.getId(), programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId(),
                programmingExercise.getId(), 0, 1, null, repositoryInfo, jobTimingInfo, jobBuildConfig, null, buildGroup, null);
    }
}
