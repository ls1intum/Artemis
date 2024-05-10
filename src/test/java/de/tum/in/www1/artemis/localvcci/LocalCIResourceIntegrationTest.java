package de.tum.in.www1.artemis.localvcci;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.localci.buildagent.SharedQueueProcessingService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildConfig;
import de.tum.in.www1.artemis.service.connectors.localci.dto.JobTimingInfo;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildAgentInformation;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.RepositoryInfo;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;

class LocalCIResourceIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    protected LocalCIBuildJobQueueItem job1;

    protected LocalCIBuildJobQueueItem job2;

    protected LocalCIBuildAgentInformation agent1;

    protected BuildJob finishedJob1;

    protected BuildJob finishedJob2;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private SharedQueueProcessingService sharedQueueProcessingService;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    protected IQueue<LocalCIBuildJobQueueItem> queuedJobs;

    protected IMap<Long, LocalCIBuildJobQueueItem> processingJobs;

    protected IMap<String, LocalCIBuildAgentInformation> buildAgentInformation;

    @Autowired
    protected BuildJobRepository buildJobRepository;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private ResultRepository resultRepository;

    @BeforeEach
    void createJobs() {
        // temporarily remove listener to avoid triggering build job processing
        sharedQueueProcessingService.removeListener();

        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(2));
        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test", "test", "test", "test", "test", null, null, false, false, false, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("test", null, RepositoryType.USER, "test", "test", "test", null, null);

        job1 = new LocalCIBuildJobQueueItem("1", "job1", "address1", 1, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo, buildConfig, null);
        job2 = new LocalCIBuildJobQueueItem("2", "job2", "address1", 2, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo, buildConfig, null);
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        agent1 = new LocalCIBuildAgentInformation(memberAddress, 1, 0, new ArrayList<>(List.of(job1)), false, new ArrayList<>(List.of(job2)));
        LocalCIBuildJobQueueItem finishedJobQueueItem1 = new LocalCIBuildJobQueueItem("3", "job3", "address1", 3, course.getId(), 1, 1, 1, BuildStatus.SUCCESSFUL, repositoryInfo,
                jobTimingInfo, buildConfig, null);
        LocalCIBuildJobQueueItem finishedJobQueueItem2 = new LocalCIBuildJobQueueItem("4", "job4", "address1", 4, course.getId() + 1, 1, 1, 1, BuildStatus.FAILED, repositoryInfo,
                jobTimingInfo, buildConfig, null);
        var result1 = new Result().successful(true).rated(true).score(100D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());
        var result2 = new Result().successful(false).rated(true).score(0D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());

        resultRepository.save(result1);
        resultRepository.save(result2);

        finishedJob1 = new BuildJob(finishedJobQueueItem1, BuildStatus.SUCCESSFUL, result1);
        finishedJob2 = new BuildJob(finishedJobQueueItem2, BuildStatus.FAILED, result2);

        queuedJobs = hazelcastInstance.getQueue("buildJobQueue");
        processingJobs = hazelcastInstance.getMap("processingJobs");
        buildAgentInformation = hazelcastInstance.getMap("buildAgentInformation");

        processingJobs.put(1L, job1);
        processingJobs.put(2L, job2);

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
        var retrievedAgent = request.get("/api/admin/build-agent?agentName=" + agent1.name(), HttpStatus.OK, LocalCIBuildAgentInformation.class);
        assertThat(retrievedAgent.name()).isEqualTo(agent1.name());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelBuildJob() throws Exception {
        LocalCIBuildJobQueueItem buildJob = processingJobs.get(1L);
        request.delete("/api/admin/cancel-job/" + buildJob.id(), HttpStatus.NO_CONTENT);
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
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCancelAllRunningBuildJobsForAgent() throws Exception {
        request.delete("/api/admin/cancel-all-running-jobs-for-agent?agentName=" + agent1.name(), HttpStatus.NO_CONTENT);
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
            BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), "Dummy log");
            buildLogEntryService.saveBuildLogsToFile(List.of(buildLogEntry), "0");
            var response = request.get("/api/build-log/0", HttpStatus.OK, String.class);
            assertThat(response).contains("Dummy log");
        }
        finally {
            Path buildLogFile = Path.of("build-logs", "0.log");
            Files.deleteIfExists(buildLogFile);
        }
    }

}
