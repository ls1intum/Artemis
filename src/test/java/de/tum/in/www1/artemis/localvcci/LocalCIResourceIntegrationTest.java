package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildAgentInformation;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;

class LocalCIResourceIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    protected LocalCIBuildJobQueueItem job1;

    protected LocalCIBuildJobQueueItem job2;

    protected LocalCIBuildAgentInformation agent1;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    protected IQueue<LocalCIBuildJobQueueItem> queuedJobs;

    protected IMap<Long, LocalCIBuildJobQueueItem> processingJobs;

    protected IMap<String, LocalCIBuildAgentInformation> buildAgentInformation;

    @BeforeEach
    void createJobs() {

        job1 = new LocalCIBuildJobQueueItem("job1", 1, "test", "test", ZonedDateTime.now(), 1, course.getId(), false);
        job2 = new LocalCIBuildJobQueueItem("job2", 2, "test", "test", ZonedDateTime.now(), 1, course.getId(), false);

        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        agent1 = new LocalCIBuildAgentInformation(memberAddress, 1, 0, null);

        queuedJobs = hazelcastInstance.getQueue("buildJobQueue");
        processingJobs = hazelcastInstance.getMap("processingJobs");
        buildAgentInformation = hazelcastInstance.getMap("buildAgentInformation");

        processingJobs.put(1L, job1);
        processingJobs.put(2L, job2);

        buildAgentInformation.put(memberAddress, agent1);
    }

    @AfterEach
    void clearDataStructures() {
        queuedJobs.clear();
        processingJobs.clear();
        buildAgentInformation.clear();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetQueuedBuildJobs_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/admin/build-job-queue/queued", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).isEmpty();
        // Adding a lot of jobs as they get processed very quickly due to mocking
        queuedJobs.addAll(List.of(job1, job2, job1, job2, job1, job2));
        var retrievedJobs1 = request.get("/api/admin/build-job-queue/queued", HttpStatus.OK, List.class);
        assertThat(retrievedJobs1).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobs_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/build-job-queue/queued", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetRunningBuildJobs_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/admin/build-job-queue/running", HttpStatus.OK, List.class);
        assertThat(retrievedJobs).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobsForCourse_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/build-job-queue/queued/" + course.getId(), HttpStatus.OK, List.class);
        assertThat(retrievedJobs).isEmpty();
        // Adding a lot of jobs as they get processed very quickly due to mocking
        queuedJobs.addAll(List.of(job1, job2, job1, job2, job1, job2));
        var retrievedJobs1 = request.get("/api/build-job-queue/queued/" + course.getId(), HttpStatus.OK, List.class);
        assertThat(retrievedJobs1).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetQueuedBuildJobsForCourse_wrongInstructorAccessForbidden() throws Exception {
        request.get("/api/build-job-queue/queued/" + course.getId(), HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetRunningBuildJobsForCourse_returnsJobs() throws Exception {
        var retrievedJobs = request.get("/api/build-job-queue/running/" + course.getId(), HttpStatus.OK, List.class);
        assertThat(retrievedJobs).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetRunningBuildJobsForCourse_wrongInstructorAccessForbidden() throws Exception {
        request.get("/api/build-job-queue/running/" + course.getId(), HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetBuildAgents_returnsAgents() throws Exception {
        var retrievedAgents = request.get("/api/admin/build-job-queue/build-agents", HttpStatus.OK, List.class);
        assertThat(retrievedAgents).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildAgents_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/build-job-queue/build-agents", HttpStatus.FORBIDDEN, List.class);
    }
}
