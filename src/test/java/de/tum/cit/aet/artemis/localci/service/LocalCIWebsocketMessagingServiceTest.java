package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.localci.dto.CourseBuildJobDTO;
import de.tum.cit.aet.artemis.localci.dto.CourseFinishedBuildJobDTO;
import de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * The course topics carry a reduced view of a build job, the administration topics the complete one.
 */
class LocalCIWebsocketMessagingServiceTest {

    private static final long COURSE_ID = 7L;

    private WebsocketMessagingService websocketMessagingService;

    private LocalCIWebsocketMessagingService messagingService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        messagingService = new LocalCIWebsocketMessagingService(websocketMessagingService);
    }

    @Test
    void buildJobUpdateSendsTheReducedViewToTheCourseTopicOnly() {
        BuildJobQueueItem job = new BuildJobQueueItem("job1", "job1", new BuildAgentDTO("agent1", "[10.0.0.1]:5701", "Agent One"), 1L, COURSE_ID, 3L, 0, 1, BuildStatus.BUILDING,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "https://example.org/assignment.git", "https://example.org/tests.git",
                        "https://example.org/solution.git", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now(), null, null, null, 0),
                new BuildConfig("echo build", "image:1", "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, "assignment", "tests", "solution", null),
                null, null, null);

        messagingService.sendBuildJobUpdate(job);

        ArgumentCaptor<WebsocketDestination> destinations = ArgumentCaptor.captor();
        ArgumentCaptor<Object> payloads = ArgumentCaptor.captor();
        verify(websocketMessagingService, times(2)).sendMessage(destinations.capture(), payloads.capture());
        int admin = destinations.getAllValues().indexOf(LocalCIWebsocketTopics.ADMIN_BUILD_JOB.at("job1"));
        int course = destinations.getAllValues().indexOf(LocalCIWebsocketTopics.COURSE_BUILD_JOB.at(COURSE_ID, "job1"));
        assertThat(admin).isNotNegative();
        assertThat(course).isNotNegative();
        assertThat(payloads.getAllValues().get(admin)).isInstanceOf(BuildJobQueueItem.class);
        assertThat(payloads.getAllValues().get(course)).isInstanceOf(CourseBuildJobDTO.class);

        String courseJson = JsonObjectMapper.get().writeValueAsString(payloads.getAllValues().get(course));
        assertThat(courseJson).contains("job1", "Agent One", "commit");
        assertThat(courseJson).doesNotContain("echo build", "image:1", "example.org", "10.0.0.1", "memberAddress", "assignmentRepositoryUri");
    }

    @Test
    void finishedBuildJobUpdateSendsTheDisplayNameInsteadOfTheAddressToTheCourseTopic() {
        FinishedBuildJobDTO job = new FinishedBuildJobDTO("job2", "job2", "[10.0.0.1]:5701", 1L, COURSE_ID, 3L, BuildStatus.SUCCESSFUL, RepositoryType.USER, "repo",
                RepositoryType.USER, ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), "commit", null, "Original Agent One");
        // A replacement agent on the same host must not overwrite the name recorded for this job.
        var agent = new BuildAgentInformation(new BuildAgentDTO("agent1", "[10.0.0.1]:5999", "Agent One"), 1, 0, List.of(), null, null, null, 0);

        messagingService.sendFinishedBuildJobUpdate(job, List.of(agent));
        messagingService.sendFinishedBuildJobDetailUpdate(job, List.of());

        ArgumentCaptor<WebsocketDestination> destinations = ArgumentCaptor.captor();
        ArgumentCaptor<Object> payloads = ArgumentCaptor.captor();
        verify(websocketMessagingService, times(4)).sendMessage(destinations.capture(), payloads.capture());
        Map<WebsocketDestination, Object> byDestination = new HashMap<>();
        for (int i = 0; i < destinations.getAllValues().size(); i++) {
            byDestination.put(destinations.getAllValues().get(i), payloads.getAllValues().get(i));
        }
        assertThat(byDestination.get(LocalCIWebsocketTopics.ADMIN_FINISHED_JOBS.at())).isEqualTo(job);
        assertThat(byDestination.get(LocalCIWebsocketTopics.ADMIN_BUILD_JOB.at("job2"))).isEqualTo(job);
        var courseList = (CourseFinishedBuildJobDTO) byDestination.get(LocalCIWebsocketTopics.COURSE_FINISHED_JOBS.at(COURSE_ID));
        assertThat(courseList.buildAgentDisplayName()).isEqualTo("Original Agent One");
        assertThat(JsonObjectMapper.get().writeValueAsString(courseList)).contains("Original Agent One").doesNotContain("10.0.0.1", "buildAgentAddress");
        // The recorded name survives the agent leaving and takes precedence over a current agent on the same host.
        var courseDetail = (CourseFinishedBuildJobDTO) byDestination.get(LocalCIWebsocketTopics.COURSE_BUILD_JOB.at(COURSE_ID, "job2"));
        assertThat(courseDetail.buildAgentDisplayName()).isEqualTo("Original Agent One");
        assertThat(JsonObjectMapper.get().writeValueAsString(courseDetail)).contains("Original Agent One").doesNotContain("10.0.0.1", "buildAgentAddress");
    }

    @Test
    void finishedBuildJobChangeSendsTheSameReducedViewToTheCourseTopicAsTheAnnouncement() {
        FinishedBuildJobDTO job = new FinishedBuildJobDTO("job3", "job3", "[10.0.0.1]:5701", 1L, COURSE_ID, 3L, BuildStatus.SUCCESSFUL, RepositoryType.USER, "repo",
                RepositoryType.USER, ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), "commit", null, "Agent One");

        messagingService.sendFinishedBuildJobChange(job, List.of());

        ArgumentCaptor<WebsocketDestination> destinations = ArgumentCaptor.captor();
        ArgumentCaptor<Object> payloads = ArgumentCaptor.captor();
        verify(websocketMessagingService, times(2)).sendMessage(destinations.capture(), payloads.capture());
        int admin = destinations.getAllValues().indexOf(LocalCIWebsocketTopics.ADMIN_FINISHED_JOB_UPDATES.at());
        int course = destinations.getAllValues().indexOf(LocalCIWebsocketTopics.COURSE_FINISHED_JOB_UPDATES.at(COURSE_ID));
        assertThat(payloads.getAllValues().get(admin)).isEqualTo(job);
        var courseChange = (CourseFinishedBuildJobDTO) payloads.getAllValues().get(course);
        assertThat(courseChange.buildAgentDisplayName()).isEqualTo("Agent One");
        assertThat(JsonObjectMapper.get().writeValueAsString(courseChange)).contains("Agent One").doesNotContain("10.0.0.1", "buildAgentAddress");
    }
}
