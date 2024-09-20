package de.tum.cit.aet.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobsStatisticsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.SharedQueueProcessingService;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.PageableSearchDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.util.PageableSearchUtilService;

class LocalCIResourceIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    protected BuildJobQueueItem job1;

    protected BuildJobQueueItem job2;

    protected BuildAgentInformation agent1;

    protected BuildJob finishedJob1;

    protected BuildJob finishedJob2;

    protected BuildJob finishedJob3;

    protected BuildJob finishedJobForLogs;

    @Autowired
    @Qualifier("hazelcastInstance")
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private SharedQueueProcessingService sharedQueueProcessingService;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    protected IQueue<BuildJobQueueItem> queuedJobs;

    protected IMap<String, BuildJobQueueItem> processingJobs;

    protected IMap<String, BuildAgentInformation> buildAgentInformation;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @BeforeEach
    void createJobs() {
        // temporarily remove listener to avoid triggering build job processing
        sharedQueueProcessingService.removeListener();

        JobTimingInfo jobTimingInfo1 = new JobTimingInfo(ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(2), ZonedDateTime.now().plusMinutes(3));
        JobTimingInfo jobTimingInfo2 = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(2));
        JobTimingInfo jobTimingInfo3 = new JobTimingInfo(ZonedDateTime.now().minusMinutes(10), ZonedDateTime.now().minusMinutes(9), ZonedDateTime.now().plusSeconds(150));

        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test", "test", "test", "test", "test", null, null, false, false, false, null, 0, null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("test", null, RepositoryType.USER, "test", "test", "test", null, null);

        job1 = new BuildJobQueueItem("1", "job1", "address1", 1, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo1, buildConfig, null);
        job2 = new BuildJobQueueItem("2", "job2", "address1", 2, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo2, buildConfig, null);
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        agent1 = new BuildAgentInformation(memberAddress, 1, 0, new ArrayList<>(List.of(job1)), false, new ArrayList<>(List.of(job2)), null);
        BuildJobQueueItem finishedJobQueueItem1 = new BuildJobQueueItem("3", "job3", "address1", 3, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo1,
                buildConfig, null);
        BuildJobQueueItem finishedJobQueueItem2 = new BuildJobQueueItem("4", "job4", "address1", 4, course.getId() + 1, 1, 1, 1, BuildStatus.FAILED, repositoryInfo, jobTimingInfo2,
                buildConfig, null);
        BuildJobQueueItem finishedJobQueueItem3 = new BuildJobQueueItem("5", "job5", "address1", 5, course.getId() + 2, 1, 1, 1, BuildStatus.FAILED, repositoryInfo, jobTimingInfo3,
                buildConfig, null);
        BuildJobQueueItem finishedJobQueueItemForLogs = new BuildJobQueueItem("6", "job5", "address1", 5, course.getId(), programmingExercise.getId(), 1, 1, BuildStatus.FAILED,
                repositoryInfo, jobTimingInfo3, buildConfig, null);
        var result1 = new Result().successful(true).rated(true).score(100D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());
        var result2 = new Result().successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());
        var result3 = new Result().successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());
        var resultForLogs = new Result().successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());

        resultRepository.save(result1);
        resultRepository.save(result2);
        resultRepository.save(result3);
        resultRepository.save(resultForLogs);

        finishedJob1 = new BuildJob(finishedJobQueueItem1, BuildStatus.SUCCESSFUL, result1);
        finishedJob2 = new BuildJob(finishedJobQueueItem2, BuildStatus.FAILED, result2);
        finishedJob3 = new BuildJob(finishedJobQueueItem3, BuildStatus.FAILED, result3);
        finishedJobForLogs = new BuildJob(finishedJobQueueItemForLogs, BuildStatus.FAILED, resultForLogs);

        queuedJobs = hazelcastInstance.getQueue("buildJobQueue");
        processingJobs = hazelcastInstance.getMap("processingJobs");
        buildAgentInformation = hazelcastInstance.getMap("buildAgentInformation");

        processingJobs.put(job1.id(), job1);
        processingJobs.put(job2.id(), job2);

        buildAgentInformation.put(memberAddress, agent1);
    }

    @AfterEach
    void clearDataStructures() {
        sharedQueueProcessingService.init();
        queuedJobs.clear();
        processingJobs.clear();
        buildAgentInformation.clear();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetQueuedBuildJobs_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/admin/queued-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).isEmpty();
        // Adding a lot of jobs as they get processed very quickly due to mocking
        queuedJobs.addAll(List.of(job1, job2));
        var retrievedJobs1 = request.get("/api/admin/queued-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs1).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobs_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/queued-jobs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetRunningBuildJobs_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/admin/running-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobsForCourse_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/courses/" + course.getId() + "/queued-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).isEmpty();
        // Adding a lot of jobs as they get processed very quickly due to mocking
        queuedJobs.addAll(List.of(job1, job2));
        var retrievedJobs1 = request.get("/api/courses/" + course.getId() + "/queued-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs1).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobsForCourse_wrongInstructorAccessForbidden() throws Exception {
        request.get("/api/courses/" + course.getId() + "/queued-jobs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetRunningBuildJobsForCourse_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/courses/" + course.getId() + "/running-jobs", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetRunningBuildJobsForCourse_wrongInstructorAccessForbidden() throws Exception {
        request.get("/api/courses/" + course.getId() + "/running-jobs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetBuildAgents_returnsAgents() throws Exception {
        var retrievedAgents = request.get("/api/admin/build-agents", HttpStatus.OK, List.class);
        assertThat(retrievedAgents).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetBuildAgentDetails_returnsAgent() throws Exception {
        var retrievedAgent = request.get("/api/admin/build-agent?agentName=" + agent1.name(), HttpStatus.OK, BuildAgentInformation.class);
        assertThat(retrievedAgent.name()).isEqualTo(agent1.name());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelProcessingBuildJob() throws Exception {
        BuildJobQueueItem buildJob = processingJobs.get(job1.id());
        request.delete("/api/admin/cancel-job/" + buildJob.id(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelQueuedBuildJob() throws Exception {
        queuedJobs.put(job1);
        request.delete("/api/admin/cancel-job/" + job1.id(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelAllQueuedBuildJobs() throws Exception {
        request.delete("/api/admin/cancel-all-queued-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelAllRunningBuildJobs() throws Exception {
        request.delete("/api/admin/cancel-all-running-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCancelBuildJobForCourse() throws Exception {
        BuildJobQueueItem buildJob = processingJobs.get(job1.id());
        request.delete("/api/courses/" + course.getId() + "/cancel-job/" + buildJob.id(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCancelAllQueuedBuildJobsForCourse() throws Exception {
        queuedJobs.put(job1);
        queuedJobs.put(job2);
        request.delete("/api/courses/" + course.getId() + "/cancel-all-queued-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCancelAllRunningBuildJobsForCourse() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/cancel-all-running-jobs", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelAllRunningBuildJobsForAgent() throws Exception {
        request.delete("/api/admin/cancel-all-running-jobs-for-agent?agentName=" + agent1.name(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetFinishedBuildJobs_returnsSortedJobs() throws Exception {
        buildJobRepository.deleteAll();
        buildJobRepository.save(finishedJob1);
        buildJobRepository.save(finishedJob2);
        buildJobRepository.save(finishedJob3);
        PageableSearchDTO<String> pageableSearchDTO = pageableSearchUtilService.configureFinishedJobsSearchDTO();
        pageableSearchDTO.setSortingOrder(SortingOrder.ASCENDING);
        var result = request.getList("/api/admin/finished-jobs", HttpStatus.OK, FinishedBuildJobDTO.class, pageableSearchUtilService.searchMapping(pageableSearchDTO, "pageable"));
        assertThat(result).hasSize(3);
        assertThat(result.getFirst().id()).isEqualTo(finishedJob2.getBuildJobId());
        assertThat(result.get(1).id()).isEqualTo(finishedJob3.getBuildJobId());
        assertThat(result.get(2).id()).isEqualTo(finishedJob1.getBuildJobId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetFinishedBuildJobs_returnsFilteredJobs() throws Exception {
        buildJobRepository.deleteAll();

        // Create a failed job to filter for
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(1).plusMinutes(2),
                ZonedDateTime.now().plusDays(1).plusMinutes(10));
        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test", "test", "test", "test", "test", null, null, false, false, false, null, 0, null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("test", null, RepositoryType.USER, "test", "test", "test", null, null);
        var failedJob1 = new BuildJobQueueItem("5", "job5", "address1", 1, course.getId(), 1, 1, 1, BuildStatus.FAILED, repositoryInfo, jobTimingInfo, buildConfig, null);
        var jobResult = new Result().successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());
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
        searchParams.add("startDate", jobTimingInfo.buildStartDate().minusSeconds(10).toString());
        searchParams.add("endDate", jobTimingInfo.buildCompletionDate().plusSeconds(10).toString());
        searchParams.add("searchTerm", "short");
        searchParams.add("buildDurationLower", "120");
        searchParams.add("buildDurationUpper", "600");

        // Check that only the failed job is returned
        var result = request.getList("/api/admin/finished-jobs", HttpStatus.OK, FinishedBuildJobDTO.class, searchParams);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(failedFinishedJob.getBuildJobId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFinishedBuildJobsForCourse_returnsJobs() throws Exception {
        buildJobRepository.deleteAll();
        buildJobRepository.save(finishedJob1);
        buildJobRepository.save(finishedJob2);
        PageableSearchDTO<String> pageableSearchDTO = pageableSearchUtilService.configureFinishedJobsSearchDTO();
        var result = request.getList("/api/courses/" + course.getId() + "/finished-jobs", HttpStatus.OK, FinishedBuildJobDTO.class,
                pageableSearchUtilService.searchMapping(pageableSearchDTO, "pageable"));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(finishedJob1.getBuildJobId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildAgents_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/build-agents", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildLogsForResult() throws Exception {
        try {
            buildJobRepository.save(finishedJobForLogs);
            BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), "Dummy log");
            buildLogEntryService.saveBuildLogsToFile(List.of(buildLogEntry), "6", programmingExercise);
            var response = request.get("/api/build-log/6", HttpStatus.OK, String.class);
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
        buildJobRepository.deleteAll();
        buildJobRepository.save(finishedJob1);
        buildJobRepository.save(finishedJob2);
        var response = request.get("/api/admin/build-job-statistics", HttpStatus.OK, BuildJobsStatisticsDTO.class);
        assertThat(response).isNotNull();
        assertThat(response.totalBuilds()).isEqualTo(2);
        assertThat(response.successfulBuilds()).isEqualTo(1);
        assertThat(response.failedBuilds()).isEqualTo(1);
        assertThat(response.cancelledBuilds()).isEqualTo(0);
    }
}
