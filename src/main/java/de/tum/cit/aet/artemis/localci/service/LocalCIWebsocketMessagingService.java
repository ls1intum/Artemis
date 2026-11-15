package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.ADMIN_BUILD_AGENT;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.ADMIN_BUILD_AGENTS;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.ADMIN_BUILD_JOB;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.ADMIN_FINISHED_JOBS;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.ADMIN_QUEUED_JOBS;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.ADMIN_RUNNING_JOBS;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.COURSE_BUILD_JOB;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.COURSE_FINISHED_JOBS;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.COURSE_QUEUED_JOBS;
import static de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics.COURSE_RUNNING_JOBS;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

/**
 * This service sends out websocket messages for the local continuous integration system.
 * It is used to send queued and running build jobs to the client.
 * It is also used to send build agent information to the client.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIWebsocketMessagingService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIWebsocketMessagingService.class);

    private final WebsocketMessagingService websocketMessagingService;

    /**
     * Constructor for dependency injection
     *
     * @param websocketMessagingService the websocket messaging service
     */
    public LocalCIWebsocketMessagingService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends the queued build jobs for the given course over websocket.
     *
     * @param courseId      the id of the course for which to send the queued build jobs
     * @param buildJobQueue the queued build jobs
     */

    public void sendQueuedBuildJobsForCourse(long courseId, List<BuildJobQueueItem> buildJobQueue) {
        var channel = COURSE_QUEUED_JOBS.at(courseId);
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    /**
     * Sends the running build jobs for the given course over websocket.
     *
     * @param courseId         the id of the course for which to send the running build jobs
     * @param buildJobsRunning the running build jobs
     */
    public void sendRunningBuildJobsForCourse(long courseId, List<BuildJobQueueItem> buildJobsRunning) {
        var channel = COURSE_RUNNING_JOBS.at(courseId);
        log.debug("Sending message on topic {}: {}", channel, buildJobsRunning);
        websocketMessagingService.sendMessage(channel, buildJobsRunning);
    }

    /**
     * Sends the queued build jobs over websocket. This is only allowed for admins.
     *
     * @param buildJobQueue the queued build jobs
     */
    public void sendQueuedBuildJobs(List<BuildJobQueueItem> buildJobQueue) {
        var channel = ADMIN_QUEUED_JOBS.at();
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    /**
     * Sends the running build jobs over websocket. This is only allowed for admins.
     *
     * @param buildJobQueue the running build jobs
     */
    public void sendRunningBuildJobs(List<BuildJobQueueItem> buildJobQueue) {
        var channel = ADMIN_RUNNING_JOBS.at();
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    /**
     * Sends the build agent information over websocket. This is only allowed for admins.
     *
     * @param buildAgentInfo the build agent information
     */
    public void sendBuildAgentSummary(List<BuildAgentInformation> buildAgentInfo) {
        var channel = ADMIN_BUILD_AGENTS.at();
        log.debug("Sending message on topic {}: {}", channel, buildAgentInfo);
        websocketMessagingService.sendMessage(channel, buildAgentInfo);
    }

    /**
     * Sends an individual build job update over websocket.
     * Sends to both the admin topic and the course-specific topic.
     *
     * @param buildJob the build job to send the update for
     */
    public void sendBuildJobUpdate(BuildJobQueueItem buildJob) {
        var adminChannel = ADMIN_BUILD_JOB.at(buildJob.id());
        log.debug("Sending build job update on topic {}", adminChannel);
        websocketMessagingService.sendMessage(adminChannel, buildJob);

        var courseChannel = COURSE_BUILD_JOB.at(buildJob.courseId(), buildJob.id());
        log.debug("Sending build job update on topic {}", courseChannel);
        websocketMessagingService.sendMessage(courseChannel, buildJob);
    }

    /**
     * Sends a finished build job notification over websocket.
     * This notifies clients that a new finished build job is available and should be added to their list.
     * Sends to both the admin topic and the course-specific topic.
     *
     * @param finishedBuildJob the finished build job DTO to send
     */
    public void sendFinishedBuildJobUpdate(FinishedBuildJobDTO finishedBuildJob) {
        var adminChannel = ADMIN_FINISHED_JOBS.at();
        log.debug("Sending finished build job update on topic {}", adminChannel);
        websocketMessagingService.sendMessage(adminChannel, finishedBuildJob);

        var courseChannel = COURSE_FINISHED_JOBS.at(finishedBuildJob.courseId());
        log.debug("Sending finished build job update on topic {}", courseChannel);
        websocketMessagingService.sendMessage(courseChannel, finishedBuildJob);
    }

    /**
     * Sends a finished build job update to the individual build job topic.
     * This is used to notify the build job detail page when a job finishes.
     * Sends to both the admin topic and the course-specific topic.
     *
     * @param finishedBuildJob the finished build job DTO to send
     */
    public void sendFinishedBuildJobDetailUpdate(FinishedBuildJobDTO finishedBuildJob) {
        var adminChannel = ADMIN_BUILD_JOB.at(finishedBuildJob.id());
        log.debug("Sending finished build job detail update on topic {}", adminChannel);
        websocketMessagingService.sendMessage(adminChannel, finishedBuildJob);

        var courseChannel = COURSE_BUILD_JOB.at(finishedBuildJob.courseId(), finishedBuildJob.id());
        log.debug("Sending finished build job detail update on topic {}", courseChannel);
        websocketMessagingService.sendMessage(courseChannel, finishedBuildJob);
    }

    public void sendBuildAgentDetails(BuildAgentInformation buildAgentDetails) {
        var channel = ADMIN_BUILD_AGENT.at(buildAgentDetails.buildAgent().name());
        log.debug("Sending message on topic {}: {}", channel, buildAgentDetails);
        websocketMessagingService.sendMessage(channel, buildAgentDetails);
    }
}
