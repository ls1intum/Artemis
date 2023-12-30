package de.tum.in.www1.artemis.web.websocket.localci;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildAgentInformation;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;

@Service
@Profile("localci")
public class LocalCIBuildQueueWebsocketService {

    private final Logger log = LoggerFactory.getLogger(LocalCIBuildQueueWebsocketService.class);

    private final WebsocketMessagingService websocketMessagingService;

    public LocalCIBuildQueueWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    public void sendQueuedBuildJobsForCourse(long courseId, List<LocalCIBuildJobQueueItem> buildJobQueue) {
        String channel = "/topic/build-job-queue/queued/" + courseId;
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    public void sendRunningBuildJobsForCourse(long courseId, List<LocalCIBuildJobQueueItem> buildJobsRunning) {
        String channel = "/topic/build-job-queue/running/" + courseId;
        log.debug("Sending message on topic {}: {}", channel, buildJobsRunning);
        websocketMessagingService.sendMessage(channel, buildJobsRunning);
    }

    public void sendQueuedBuildJobs(List<LocalCIBuildJobQueueItem> buildJobQueue) {
        String channel = "/topic/admin/build-job-queue/queued";
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    public void sendRunningBuildJobs(List<LocalCIBuildJobQueueItem> buildJobQueue) {
        String channel = "/topic/admin/build-job-queue/running";
        log.debug("Sending message on topic {}: {}", channel, buildJobQueue);
        websocketMessagingService.sendMessage(channel, buildJobQueue);
    }

    public void sendBuildAgentInformation(List<LocalCIBuildAgentInformation> buildAgentInfo) {
        String channel = "/topic/admin/build-agents";
        log.debug("Sending message on topic {}: {}", channel, buildAgentInfo);
        websocketMessagingService.sendMessage(channel, buildAgentInfo);
    }
}
