package de.tum.cit.aet.artemis.localci.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;

/**
 * The websocket topics of the build queue. The administrator topics cover the jobs of all courses and the build agents, the course topics only the jobs of one course.
 */
@Profile(PROFILE_LOCALCI)
@Lazy
@Component
public class LocalCIWebsocketTopics implements WebsocketTopicProvider {

    public static final WebsocketTopic ADMIN_QUEUED_JOBS = WebsocketTopic.of("/topic/admin/queued-jobs", WebsocketTopicAccess.administrator()).withCompression();

    public static final WebsocketTopic ADMIN_RUNNING_JOBS = WebsocketTopic.of("/topic/admin/running-jobs", WebsocketTopicAccess.administrator()).withCompression();

    public static final WebsocketTopic ADMIN_FINISHED_JOBS = WebsocketTopic.of("/topic/admin/finished-jobs", WebsocketTopicAccess.administrator());

    public static final WebsocketTopic ADMIN_BUILD_JOB = WebsocketTopic.of("/topic/admin/build-job/{buildJobId}", WebsocketTopicAccess.administrator());

    public static final WebsocketTopic ADMIN_BUILD_AGENTS = WebsocketTopic.of("/topic/admin/build-agents", WebsocketTopicAccess.administrator()).withCompression();

    /**
     * The details of one build agent, including the running jobs of all courses on it.
     */
    public static final WebsocketTopic ADMIN_BUILD_AGENT = WebsocketTopic.of("/topic/admin/build-agent/{buildAgentName}", WebsocketTopicAccess.administrator()).withCompression();

    public static final WebsocketTopic COURSE_QUEUED_JOBS = WebsocketTopic.of("/topic/courses/{courseId}/queued-jobs", WebsocketTopicAccess.atLeastInstructorInCourse("courseId"))
            .withCompression();

    public static final WebsocketTopic COURSE_RUNNING_JOBS = WebsocketTopic.of("/topic/courses/{courseId}/running-jobs", WebsocketTopicAccess.atLeastInstructorInCourse("courseId"))
            .withCompression();

    public static final WebsocketTopic COURSE_FINISHED_JOBS = WebsocketTopic.of("/topic/courses/{courseId}/finished-jobs",
            WebsocketTopicAccess.atLeastInstructorInCourse("courseId"));

    public static final WebsocketTopic COURSE_BUILD_JOB = WebsocketTopic.of("/topic/courses/{courseId}/build-job/{buildJobId}",
            WebsocketTopicAccess.atLeastInstructorInCourse("courseId"));
}
