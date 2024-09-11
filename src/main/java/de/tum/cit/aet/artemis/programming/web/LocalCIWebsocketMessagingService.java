package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.service.localci.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.programming.service.localci.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;

/**
 * This service sends out websocket messages for the local continuous integration system.
 * It is used to send queued and running build jobs to the client.
 * It is also used to send build agent information to the client.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIWebsocketMessagingService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIWebsocketMessagingService.class);

    private final WebsocketMessagingService websocketMessagingService;

    private static final Pattern COURSE_DESTINATION_PATTERN = Pattern.compile("^/topic/courses/(\\d+)/(queued-jobs|running-jobs)$");

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
        String channel = "/topic/courses/" + courseId + "/queued-jobs";
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
        String channel = "/topic/courses/" + courseId + "/running-jobs";
        log.debug("Sending message on topic {}: {}", channel, buildJobsRunning);
        websocketMessagingService.sendMessage(channel, buildJobsRunning);
    }

    /**
     * Sends the queued build jobs over websocket. This is only allowed for admins.
     *
     * @param buildJobQueue the queued build jobs
     */
    public void sendQueuedBuildJobs(List<BuildJobQueueItem> buildJobQueue) {
        String channel = "/topic/admin/queued-jobs";
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    /**
     * Sends the running build jobs over websocket. This is only allowed for admins.
     *
     * @param buildJobQueue the running build jobs
     */
    public void sendRunningBuildJobs(List<BuildJobQueueItem> buildJobQueue) {
        String channel = "/topic/admin/running-jobs";
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    /**
     * Sends the build agent information over websocket. This is only allowed for admins.
     *
     * @param buildAgentInfo the build agent information
     */
    public void sendBuildAgentSummary(List<BuildAgentInformation> buildAgentInfo) {
        String channel = "/topic/admin/build-agents";
        log.debug("Sending message on topic {}: {}", channel, buildAgentInfo);
        // TODO: convert into a proper DTO and strip unnecessary information, e.g. build config, because it's not shown in the client and contains too much information
        websocketMessagingService.sendMessage(channel, buildAgentInfo);
    }

    public void sendBuildAgentDetails(BuildAgentInformation buildAgentDetails) {
        String channel = "/topic/admin/build-agent/" + buildAgentDetails.name();
        log.debug("Sending message on topic {}: {}", channel, buildAgentDetails);
        websocketMessagingService.sendMessage(channel, buildAgentDetails);
    }

    /**
     * Checks if the given destination is a build queue admin destination.
     * This is the case if the destination is either /topic/admin/queued-jobs or /topic/admin/running-jobs.
     *
     * @param destination the destination to check
     * @return true if the destination is a build queue admin destination, false otherwise
     */
    public static boolean isBuildQueueAdminDestination(String destination) {
        return "/topic/admin/queued-jobs".equals(destination) || "/topic/admin/running-jobs".equals(destination);
    }

    /**
     * Checks if the given destination is a build queue course destination. This is the case if the destination is either
     * /topic/courses/{courseId}/queued-jobs or /topic/courses/{courseId}/running-jobs.
     * If the destination is a build queue course destination, the courseId is returned.
     *
     * @param destination the destination to check
     * @return the courseId if the destination is a build queue course destination, empty otherwise
     */
    public static Optional<Long> isBuildQueueCourseDestination(String destination) {
        // Define a pattern to match the expected course-related topic format
        Matcher matcher = COURSE_DESTINATION_PATTERN.matcher(destination);

        // Check if the destination matches the pattern
        if (matcher.matches()) {
            // Extract the courseId from the matched groups
            return Optional.of(Long.parseLong(matcher.group(1)));
        }
        return Optional.empty();
    }

    /**
     * Checks if the given destination is a build agent destination.
     * This is the case if the destination is /topic/admin/build-agents.
     *
     * @param destination the destination to check
     * @return true if the destination is a build agent destination, false otherwise
     */
    public static boolean isBuildAgentDestination(String destination) {
        return "/topic/admin/build-agents".equals(destination);
    }
}
