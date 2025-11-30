package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobsStatisticsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.PageableSearchDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;

// TestInstance.Lifecycle.PER_CLASS allows all test methods in this class to share the same instance of the test class.
// This reduces the overhead of repeatedly creating and tearing down a new Spring application context for each test method.
// This is especially useful when the test setup is expensive or when we want to share resources, such as database connections or mock objects, across multiple tests.
// In this case, we want to share the same GitService and UsernamePasswordCredentialsProvider.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

// ExecutionMode.SAME_THREAD ensures that all tests within this class are executed sequentially in the same thread, rather than in parallel or in a different thread.
// This is important in the context of LocalCI because it avoids potential race conditions or inconsistencies that could arise if multiple test methods are executed
// concurrently. For example, it prevents overloading the LocalCI's result processing system with too many build job results at the same time, which could lead to flaky tests
// or timeouts. By keeping everything in the same thread, we maintain more predictable and stable test behavior, while not increasing the test execution time significantly.
@Execution(ExecutionMode.SAME_THREAD)
class LocalCIResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final Logger log = LoggerFactory.getLogger(LocalCIResourceIntegrationTest.class);

    private static final String TEST_PREFIX = "localciresourceint";

    private ThreadPoolExecutor testExecutor;

    protected BuildJobQueueItem job1;

    protected BuildJobQueueItem job2;

    protected BuildAgentInformation agent1;

    protected BuildJob finishedJob1;

    protected BuildJob finishedJob2;

    protected BuildJob finishedJob3;

    protected BuildJob finishedJobForLogs;

    protected DistributedQueue<BuildJobQueueItem> queuedJobs;

    protected DistributedMap<String, BuildJobQueueItem> processingJobs;

    protected DistributedMap<String, BuildAgentInformation> buildAgentInformation;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.build-agent.display-name:}")
    private String buildAgentDisplayName;

    private BuildAgentDTO buildAgent;

    @BeforeEach
    void createJobs() {
        // Create a test executor with a single thread
        testExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        // Mock the getBuildExecutor() method to return our test executor
        doReturn(testExecutor).when(buildAgentConfiguration).getBuildExecutor();

        buildJobRepository.deleteAll();
        // temporarily remove listener to avoid triggering build job processing
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();

        JobTimingInfo jobTimingInfo1 = new JobTimingInfo(ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(2), ZonedDateTime.now().plusMinutes(3), null, 20);
        JobTimingInfo jobTimingInfo2 = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(2), null, 20);
        JobTimingInfo jobTimingInfo3 = new JobTimingInfo(ZonedDateTime.now().minusMinutes(10), ZonedDateTime.now().minusMinutes(9), ZonedDateTime.now().plusSeconds(150), null, 20);

        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("test", null, RepositoryType.USER, "test", "test", "test", null, null);

        String memberAddress = distributedDataAccessService.getLocalMemberAddress();
        buildAgent = new BuildAgentDTO(buildAgentShortName, memberAddress, buildAgentDisplayName);

        job1 = new BuildJobQueueItem("1", "job1", buildAgent, 1, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo1, buildConfig, null);
        job2 = new BuildJobQueueItem("2", "job2", buildAgent, 2, course.getId(), 1, 1, 2, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo2, buildConfig, null);
        agent1 = new BuildAgentInformation(buildAgent, 2, 1, new ArrayList<>(List.of(job1)), BuildAgentStatus.IDLE, null, null,
                buildAgentConfiguration.getPauseAfterConsecutiveFailedJobs());
        BuildJobQueueItem finishedJobQueueItem1 = new BuildJobQueueItem("3", "job3", buildAgent, 3, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo1,
                buildConfig, null);
        BuildJobQueueItem finishedJobQueueItem2 = new BuildJobQueueItem("4", "job4", buildAgent, 4, course.getId() + 1, 1, 1, 1, BuildStatus.FAILED, repositoryInfo, jobTimingInfo2,
                buildConfig, null);
        BuildJobQueueItem finishedJobQueueItem3 = new BuildJobQueueItem("5", "job5", buildAgent, 5, course.getId() + 2, 1, 1, 1, BuildStatus.FAILED, repositoryInfo, jobTimingInfo3,
                buildConfig, null);
        BuildJobQueueItem finishedJobQueueItemForLogs = new BuildJobQueueItem("6", "job5", buildAgent, 5, course.getId(), programmingExercise.getId(), 1, 1, BuildStatus.FAILED,
                repositoryInfo, jobTimingInfo3, buildConfig, null);

        var submission1 = ParticipationFactory.generateProgrammingSubmission(true);
        var result1 = this.programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, submission1, TEST_PREFIX + "student1");
        result1.successful(true).rated(true).score(100D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());

        var submission2 = ParticipationFactory.generateProgrammingSubmission(true);
        var result2 = this.programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, submission2, TEST_PREFIX + "student1");
        result2.successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());

        var submission3 = ParticipationFactory.generateProgrammingSubmission(true);
        var result3 = this.programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, submission3, TEST_PREFIX + "student1");
        result3.successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());

        var submissionForLogs = ParticipationFactory.generateProgrammingSubmission(true);
        var resultForLogs = this.programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, submissionForLogs, TEST_PREFIX + "student1");
        resultForLogs.successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());

        resultRepository.save(result1);
        resultRepository.save(result2);
        resultRepository.save(result3);
        resultRepository.save(resultForLogs);

        finishedJob1 = new BuildJob(finishedJobQueueItem1, BuildStatus.SUCCESSFUL, result1);
        finishedJob2 = new BuildJob(finishedJobQueueItem2, BuildStatus.FAILED, result2);
        finishedJob3 = new BuildJob(finishedJobQueueItem3, BuildStatus.FAILED, result3);
        finishedJobForLogs = new BuildJob(finishedJobQueueItemForLogs, BuildStatus.FAILED, resultForLogs);

        queuedJobs = distributedDataAccessService.getDistributedBuildJobQueue();
        processingJobs = distributedDataAccessService.getDistributedProcessingJobs();
        buildAgentInformation = distributedDataAccessService.getDistributedBuildAgentInformation();

        processingJobs.put(job1.id(), job1);
        processingJobs.put(job2.id(), job2);

        buildAgentInformation.put(memberAddress, agent1);
    }

    @AfterEach
    void clearDataStructures() {
        // Shutdown the test executor
        if (testExecutor != null) {
            testExecutor.shutdown();
            try {
                testExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        sharedQueueProcessingService.init();
        queuedJobs.clear();
        processingJobs.clear();
        buildAgentInformation.clear();
        buildJobRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetQueuedBuildJobs_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/core/admin/queued-jobs", HttpStatus.OK, List.class);
        // Adding a lot of jobs as they get processed very quickly due to mocking
        queuedJobs.addAll(List.of(job1, job2));
        var retrievedJobs1 = request.get("/api/core/admin/queued-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs1).hasSize(retrievedJobs.size() + 2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobs_instructorAccessForbidden() throws Exception {
        request.get("/api/core/admin/queued-jobs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetRunningBuildJobs_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/core/admin/running-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobsForCourse_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/programming/courses/" + course.getId() + "/queued-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).isEmpty();
        // Adding a lot of jobs as they get processed very quickly due to mocking
        queuedJobs.addAll(List.of(job1, job2));
        var retrievedJobs1 = request.get("/api/programming/courses/" + course.getId() + "/queued-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs1).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobsForCourse_wrongInstructorAccessForbidden() throws Exception {
        request.get("/api/programming/courses/" + course.getId() + "/queued-jobs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetRunningBuildJobsForCourse_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/programming/courses/" + course.getId() + "/running-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetRunningBuildJobsForCourse_wrongInstructorAccessForbidden() throws Exception {
        request.get("/api/programming/courses/" + course.getId() + "/running-jobs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetBuildAgents_returnsAgents() throws Exception {
        var retrievedAgents = request.get("/api/core/admin/build-agents", HttpStatus.OK, List.class);
        assertThat(retrievedAgents).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetBuildAgentDetails_returnsAgent() throws Exception {
        var retrievedAgent = request.get("/api/core/admin/build-agent?agentName=" + agent1.buildAgent().name(), HttpStatus.OK, BuildAgentInformation.class);
        assertThat(retrievedAgent.buildAgent().name()).isEqualTo(agent1.buildAgent().name());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelProcessingBuildJob() throws Exception {
        BuildJobQueueItem buildJob = processingJobs.get(job1.id());
        request.delete("/api/core/admin/cancel-job/" + buildJob.id(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelQueuedBuildJob() throws Exception {
        queuedJobs.add(job1);
        request.delete("/api/core/admin/cancel-job/" + job1.id(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelAllQueuedBuildJobs() throws Exception {
        request.delete("/api/core/admin/cancel-all-queued-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelAllRunningBuildJobs() throws Exception {
        request.delete("/api/core/admin/cancel-all-running-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCancelBuildJobForCourse() throws Exception {
        BuildJobQueueItem buildJob = processingJobs.get(job1.id());
        request.delete("/api/programming/courses/" + course.getId() + "/cancel-job/" + buildJob.id(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCancelAllQueuedBuildJobsForCourse() throws Exception {
        queuedJobs.add(job1);
        queuedJobs.add(job2);
        request.delete("/api/programming/courses/" + course.getId() + "/cancel-all-queued-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCancelAllRunningBuildJobsForCourse() throws Exception {
        request.delete("/api/programming/courses/" + course.getId() + "/cancel-all-running-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelAllRunningBuildJobsForAgent() throws Exception {
        request.delete("/api/core/admin/cancel-all-running-jobs-for-agent?agentName=" + agent1.buildAgent().name(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetFinishedBuildJobs_returnsSortedJobs() throws Exception {
        buildJobRepository.save(finishedJob1);
        buildJobRepository.save(finishedJob2);
        buildJobRepository.save(finishedJob3);
        PageableSearchDTO<String> pageableSearchDTO = pageableSearchUtilService.configureFinishedJobsSearchDTO();
        pageableSearchDTO.setSortingOrder(SortingOrder.ASCENDING);
        var result = request.getList("/api/core/admin/finished-jobs", HttpStatus.OK, FinishedBuildJobDTO.class,
                pageableSearchUtilService.searchMapping(pageableSearchDTO, "pageable"));

        assertThat(result).isNotEmpty();

        // Ensure the saved jobs appear in the result list and are in the correct order, even if other jobs are in between
        List<String> resultIds = result.stream().map(FinishedBuildJobDTO::id).toList();
        List<String> expectedOrderedIds = List.of(finishedJob2.getBuildJobId(), finishedJob3.getBuildJobId(), finishedJob1.getBuildJobId());

        assertThat(resultIds).containsAll(expectedOrderedIds);
        assertThat(IntStream.range(0, expectedOrderedIds.size() - 1).allMatch(i -> resultIds.indexOf(expectedOrderedIds.get(i)) < resultIds.indexOf(expectedOrderedIds.get(i + 1))))
                .isTrue();

        // Ensure the jobs are sorted by buildCompletionDate in ascending order
        List<ZonedDateTime> completionDates = result.stream().map(FinishedBuildJobDTO::buildCompletionDate).toList();
        assertThat(completionDates).isSorted();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetFinishedBuildJobs_returnsFilteredJobs() throws Exception {

        // Create a failed job to filter for
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(1).plusMinutes(2),
                ZonedDateTime.now().plusDays(1).plusMinutes(10), null, 0);
        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("test", null, RepositoryType.USER, "test", "test", "test", null, null);
        var failedJob1 = new BuildJobQueueItem("5", "job5", buildAgent, 1, course.getId(), 1, 1, 1, BuildStatus.FAILED, repositoryInfo, jobTimingInfo, buildConfig, null);

        var submission = ParticipationFactory.generateProgrammingSubmission(true);
        var jobResult = this.programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, submission, TEST_PREFIX + "student1");
        jobResult.successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());
        var failedFinishedJob = new BuildJob(failedJob1, BuildStatus.FAILED, jobResult);

        // Save the jobs
        buildJobRepository.save(finishedJob1);
        buildJobRepository.save(finishedJob2);
        resultRepository.save(jobResult);
        buildJobRepository.save(failedFinishedJob);

        // Filter for the failed job
        PageableSearchDTO<String> pageableSearchDTO = pageableSearchUtilService.configureFinishedJobsSearchDTO();
        LinkedMultiValueMap<String, String> searchParams = pageableSearchUtilService.searchMapping(pageableSearchDTO, "pageable");
        searchParams.add("buildStatus", "FAILED");
        searchParams.add("startDate", jobTimingInfo.submissionDate().minusSeconds(10).toString());
        searchParams.add("endDate", jobTimingInfo.submissionDate().plusSeconds(10).toString());
        searchParams.add("searchTerm", "short");
        searchParams.add("buildDurationLower", "120");
        searchParams.add("buildDurationUpper", "600");

        // Check that only the failed job is returned
        var result = request.getList("/api/core/admin/finished-jobs", HttpStatus.OK, FinishedBuildJobDTO.class, searchParams);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(failedFinishedJob.getBuildJobId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFinishedBuildJobsForCourse_returnsJobs() throws Exception {
        buildJobRepository.save(finishedJob1);
        buildJobRepository.save(finishedJob2);
        PageableSearchDTO<String> pageableSearchDTO = pageableSearchUtilService.configureFinishedJobsSearchDTO();
        var result = request.getList("/api/programming/courses/" + course.getId() + "/finished-jobs", HttpStatus.OK, FinishedBuildJobDTO.class,
                pageableSearchUtilService.searchMapping(pageableSearchDTO, "pageable"));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(finishedJob1.getBuildJobId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildAgents_instructorAccessForbidden() throws Exception {
        request.get("/api/core/admin/build-agents", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildLogsForResult() throws Exception {
        try {
            buildJobRepository.save(finishedJobForLogs);
            BuildLogDTO buildLogEntry = new BuildLogDTO(ZonedDateTime.now(), "Dummy log");
            buildLogEntryService.saveBuildLogsToFile(List.of(buildLogEntry), "6", programmingExercise);
            var response = request.get("/api/programming/build-log/6", HttpStatus.OK, String.class);
            assertThat(response).contains("Dummy log");
        }
        finally {
            Path buildLogFile = Path.of("build-logs").resolve(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName())
                    .resolve(programmingExercise.getShortName()).resolve("6.log");
            Files.deleteIfExists(buildLogFile);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetBuildJobStatistics() throws Exception {
        buildJobRepository.save(finishedJob1);
        buildJobRepository.save(finishedJob2);
        var response = request.get("/api/core/admin/build-job-statistics", HttpStatus.OK, BuildJobsStatisticsDTO.class);
        assertThat(response).isNotNull();
        assertThat(response.totalBuilds()).isGreaterThanOrEqualTo(2);
        assertThat(response.successfulBuilds()).isGreaterThanOrEqualTo(1);
        assertThat(response.failedBuilds()).isGreaterThanOrEqualTo(1);
        assertThat(response.cancelledBuilds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testPauseBuildAgent() throws Exception {
        // We need to clear the processing jobs to avoid the agent being set to ACTIVE again
        processingJobs.clear();

        request.put("/api/core/admin/agents/" + URLEncoder.encode(agent1.buildAgent().name(), StandardCharsets.UTF_8) + "/pause", null, HttpStatus.NO_CONTENT);
        await().atMost(Duration.ofSeconds(30)) // temporarily increase to debug
                .pollInterval(Duration.ofMillis(200)).until(() -> {
                    var agent = buildAgentInformation.get(agent1.buildAgent().memberAddress());
                    log.info("Current status of agent after pause operation {} : {}", agent.buildAgent().displayName(), agent.status());
                    return agent.status() == BuildAgentStatus.PAUSED;
                });

        request.put("/api/core/admin/agents/" + URLEncoder.encode(agent1.buildAgent().name(), StandardCharsets.UTF_8) + "/resume", null, HttpStatus.NO_CONTENT);
        await().atMost(Duration.ofSeconds(30)) // temporarily increase to debug
                .pollInterval(Duration.ofMillis(200)).until(() -> {
                    var agent = buildAgentInformation.get(agent1.buildAgent().memberAddress());
                    log.info("Current status of agent after resume operation {} : {}", agent.buildAgent().displayName(), agent.status());
                    return agent.status() == BuildAgentStatus.IDLE;
                });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testPauseAllBuildAgents() throws Exception {
        // We need to clear the processing jobs to avoid the agent being set to ACTIVE again
        processingJobs.clear();

        request.put("/api/core/admin/agents/pause-all", null, HttpStatus.NO_CONTENT);
        await().atMost(Duration.ofSeconds(30)) // temporarily increase to debug
                .pollInterval(Duration.ofMillis(200)).until(() -> {
                    var agents = buildAgentInformation.values();
                    printAgentInformation(agents);
                    return agents.stream().allMatch(agent -> agent.status() == BuildAgentStatus.PAUSED);
                });

        request.put("/api/core/admin/agents/resume-all", null, HttpStatus.NO_CONTENT);
        await().atMost(Duration.ofSeconds(30)) // temporarily increase to debug
                .pollInterval(Duration.ofMillis(200)).until(() -> {
                    var agents = buildAgentInformation.values();
                    printAgentInformation(agents);
                    return agents.stream().allMatch(agent -> agent.status() == BuildAgentStatus.IDLE);
                });
    }

    private static void printAgentInformation(Collection<BuildAgentInformation> agents) {
        log.info("Current statuses: {}", agents.stream().map(agent -> agent.buildAgent().displayName() + "=" + agent.status()).toList());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildJob() {
        var now = ZonedDateTime.now();
        JobTimingInfo jobTimingInfo1 = new JobTimingInfo(now, now, null, now.plusSeconds(24), 24);
        JobTimingInfo jobTimingInfo2 = new JobTimingInfo(now, now.plusSeconds(5), null, now.plusSeconds(29), 24);
        JobTimingInfo jobTimingInfo3 = new JobTimingInfo(now.plusSeconds(1), null, null, null, 24);
        JobTimingInfo jobTimingInfo4 = new JobTimingInfo(now.plusSeconds(2), null, null, null, 24);
        JobTimingInfo jobTimingInfo5 = new JobTimingInfo(now.plusSeconds(3), null, null, null, 24);

        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("test", null, RepositoryType.USER, "test", "test", "test", null, null);

        var job1 = new BuildJobQueueItem("1", "job1", buildAgent, 1, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo1, buildConfig, null);
        var job2 = new BuildJobQueueItem("2", "job2", buildAgent, 2, course.getId(), 1, 1, 2, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo2, buildConfig, null);
        var job3 = new BuildJobQueueItem("3", "job3", buildAgent, 2, course.getId(), 1, 1, 2, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo3, buildConfig, null);
        var job4 = new BuildJobQueueItem("4", "job4", buildAgent, 2, course.getId(), 1, 1, 2, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo4, buildConfig, null);
        var job5 = new BuildJobQueueItem("5", "job5", buildAgent, 2, course.getId(), 1, 1, 2, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo5, buildConfig, null);

        processingJobs.clear();
        processingJobs.put(job1.id(), job1);
        processingJobs.put(job2.id(), job2);
        queuedJobs.clear();
        queuedJobs.add(job3);
        queuedJobs.add(job4);
        queuedJobs.add(job5);

        agent1 = new BuildAgentInformation(buildAgent, 2, 2, new ArrayList<>(List.of(job1, job2)), BuildAgentStatus.ACTIVE, null, null,
                buildAgentConfiguration.getPauseAfterConsecutiveFailedJobs());
        buildAgentInformation.put(buildAgent.memberAddress(), agent1);

        var queueDurationEstimation = sharedQueueManagementService.getBuildJobEstimatedStartDate(job4.participationId());
        assertThat(queueDurationEstimation).isCloseTo(now.plusSeconds(48), within(2, ChronoUnit.SECONDS));
    }
}
