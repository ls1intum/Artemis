package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
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
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
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
    private PlatformTransactionManager transactionManager;

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
        Result aggregatedResult = appendContainerResultInTransaction(participation, resultA, false, "container_a", null);
        assertThat(aggregatedResult).isNotNull();
        assertThat(aggregatedResult.getCompletionDate()).as("the result stays in progress until every container finished").isNull();
        buildJobRepository.save(new BuildJob(buildJobFor("merge-0", "merge", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        // Second container finishes: it appends to the same result rather than creating a second one.
        BuildResult resultB = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result aggregatedResultAgain = appendContainerResultInTransaction(participation, resultB, false, "container_b", aggregatedResult.getId());
        assertThat(aggregatedResultAgain.getId()).as("all containers of one submission share a single result").isEqualTo(aggregatedResult.getId());
        buildJobRepository.save(new BuildJob(buildJobFor("merge-1", "merge", participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResultAgain));

        // Both containers have finished, counted over the build jobs of the build group, and both link to the shared result.
        assertThat(buildJobRepository.countByBuildGroupIdAndBuildStatusIn("merge", LocalCIResultProcessingService.FINISHED_BUILD_STATUSES)).isEqualTo(2);
        assertThat(buildJobRepository.countByResultId(aggregatedResultAgain.getId())).isEqualTo(2);
        assertThat(buildJobRepository.existsByBuildGroupIdAndBuildStatusNot("merge", BuildStatus.SUCCESSFUL)).isFalse();
        assertThat(buildJobRepository.existsByBuildGroupIdAndResultIsNull("merge")).isFalse();

        // Finalizing marks the aggregated result complete. Neither container reported a test case, so no relevant test
        // case passed: the result is not successful, although every container ran.
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain.getId(), participation, true, ZonedDateTime.now());
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
        Result aggregatedResult = appendContainerResultInTransaction(participation, containerResult, false, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("rated-0", "rated", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, ZonedDateTime.now());

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

        // a test-case feedback row exists only for a registered test case, so the instructor test is registered first
        testCaseRepository.save(new ProgrammingExerciseTestCase().testName("instructorTest").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));

        // the instructor container finishes with a passing test
        var instructorJob = new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("instructorTest", List.of())));
        BuildResult instructorResult = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(instructorJob), null, null, false, 0);
        Result aggregatedResult = appendContainerResultInTransaction(participation, instructorResult, true, "instructor_tests", null);
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(aggregatedResult.getId()))).as("the instructor container produced feedback").isNotEmpty();
        buildJobRepository.save(new BuildJob(buildJobFor("crash-0", "crash", participation, commitHash, "instructor_tests"), BuildStatus.SUCCESSFUL, aggregatedResult));

        // the student container crashes: no test feedback, but build logs and a non-zero exit code
        var crashLog = new BuildLogDTO(ZonedDateTime.now(), "student container terminated: out of memory");
        BuildResult studentResult = new BuildResult(null, commitHash, commitHash, false, ZonedDateTime.now(), List.of(), List.of(crashLog), null, true, 137);
        Result aggregatedResultAgain = appendContainerResultInTransaction(participation, studentResult, true, "student_tests", aggregatedResult.getId());
        buildJobRepository.save(new BuildJob(buildJobFor("crash-1", "crash", participation, commitHash, "student_tests"), BuildStatus.FAILED, aggregatedResultAgain));

        // the instructor container's feedback survives the student container's crash
        assertThat(aggregatedResultAgain.getId()).isEqualTo(aggregatedResult.getId());
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(aggregatedResultAgain.getId())))
                .as("the instructor feedback is not lost when a sibling container crashes").isNotEmpty();

        // the crashed container's build logs are preserved and labeled with its container name
        ProgrammingSubmission reloadedSubmission = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloadedSubmission.isBuildFailed()).isTrue();
        List<BuildLogEntry> buildLogs = buildLogEntryService.getLatestBuildLogs(reloadedSubmission);
        assertThat(buildLogs).anySatisfy(logEntry -> {
            assertThat(logEntry.getContainerName()).isEqualTo("student_tests");
            assertThat(logEntry.getLog()).contains("out of memory");
        });

        // finalizing once both containers finished marks the result complete but not successful
        boolean allContainersSucceeded = !buildJobRepository.existsByBuildGroupIdAndBuildStatusNot("crash", BuildStatus.SUCCESSFUL);
        assertThat(allContainersSucceeded).isFalse();
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain.getId(), participation, allContainersSucceeded,
                ZonedDateTime.now());
        assertThat(finalizedResult.getCompletionDate()).isNotNull();
        assertThat(finalizedResult.isSuccessful()).isFalse();
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
        Result aggregatedResult = appendContainerResultInTransaction(participation, containerResult, true, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("failing-0", "failing", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("the build itself did not fail").isFalse();

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResult.getId(), participation, true, ZonedDateTime.now());

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
        Result aggregatedResult = appendContainerResultInTransaction(participation, resultA, true, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("passing-0", "passing", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, aggregatedResult));

        var jobB = new LocalCIJobDTO(List.of(), shareB.stream().map(name -> new LocalCITestJobDTO(name, List.of())).toList());
        BuildResult resultB = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(jobB), null, null, false, 0);
        Result aggregatedResultAgain = appendContainerResultInTransaction(participation, resultB, true, "container_b", aggregatedResult.getId());
        buildJobRepository.save(new BuildJob(buildJobFor("passing-1", "passing", participation, commitHash, "container_b"), BuildStatus.SUCCESSFUL, aggregatedResultAgain));
        assertThat(programmingSubmissionRepository.findById(submission.getId()).orElseThrow().isBuildFailed()).as("no container failed to build").isFalse();

        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultAgain.getId(), participation, true, ZonedDateTime.now());

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
        Result firstAggregate = appendContainerResultInTransaction(participation, firstAttempt, false, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("attempt1-0", "attempt1", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, firstAggregate));

        // A second attempt of the same commit starts: no job of its group has merged yet, so its container gets no aggregate id.
        BuildResult secondAttempt = new BuildResult(null, commitHash, commitHash, true, ZonedDateTime.now(), List.of(), null, null, false, 0);
        Result secondAggregate = appendContainerResultInTransaction(participation, secondAttempt, false, "container_a", null);
        buildJobRepository.save(new BuildJob(buildJobFor("attempt2-0", "attempt2", participation, commitHash, "container_a"), BuildStatus.SUCCESSFUL, secondAggregate));

        assertThat(secondAggregate.getId()).as("a new attempt does not join the earlier attempt's open aggregate").isNotEqualTo(firstAggregate.getId());
        assertThat(buildJobRepository.findResultIdsOfBuildGroup("attempt1", PageRequest.of(0, 1))).containsExactly(firstAggregate.getId());
        assertThat(buildJobRepository.findResultIdsOfBuildGroup("attempt2", PageRequest.of(0, 1))).containsExactly(secondAggregate.getId());

        // Finalizing the second attempt leaves the first attempt's aggregate untouched and still in progress.
        Result finalizedSecond = programmingExerciseGradingService.finalizeContainerResult(secondAggregate.getId(), participation, true, ZonedDateTime.now());
        assertThat(finalizedSecond.getCompletionDate()).isNotNull();
        assertThat(resultRepository.findById(firstAggregate.getId()).orElseThrow().getCompletionDate()).isNull();
    }

    /**
     * Appends a container's result the way the result processing does: inside a transaction, which is the contract of
     * {@code appendContainerResult}. Detached, the first container's feedback rows are inserted twice: once through the
     * result, and once more through the submission's cascade to the same result instance, whose rows never received the
     * ids the first merge assigned to its copies. The duplicated test cases would then zero the score at finalization.
     */
    private Result appendContainerResultInTransaction(ProgrammingExerciseStudentParticipation participation, BuildResult containerResult, boolean testsExpected,
            String containerName, Long aggregatedResultId) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> programmingExerciseGradingService.appendContainerResult(participation, containerResult, testsExpected, containerName, aggregatedResultId));
    }

    private BuildJobQueueItem buildJobFor(String id, String buildGroupId, ProgrammingExerciseStudentParticipation participation, String commitHash, String containerName) {
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
