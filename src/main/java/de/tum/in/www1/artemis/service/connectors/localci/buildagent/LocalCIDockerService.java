package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

/**
 * Service for Docker related operations in local CI
 */
@Service
@Profile(PROFILE_BUILDAGENT)
public class LocalCIDockerService {

    private final ReentrantLock lock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(LocalCIDockerService.class);

    private final DockerClient dockerClient;

    private final HazelcastInstance hazelcastInstance;

    private final BuildJobContainerService buildJobContainerService;

    private final TaskScheduler taskScheduler;

    private boolean isFirstCleanup = true;

    @Value("${artemis.continuous-integration.image-cleanup.enabled:false}")
    private Boolean imageCleanupEnabled;

    @Value("${artemis.continuous-integration.image-cleanup.expiry-days:2}")
    private int imageExpiryDays;

    @Value("${artemis.continuous-integration.build-container-prefix:local-ci-}")
    private String buildContainerPrefix;

    // with the default value, containers running for longer than 5 minutes when the cleanup starts
    @Value("${artemis.continuous-integration.container-cleanup.expiry-minutes:5}")
    private int containerExpiryMinutes;

    // With the default value, the cleanup is triggered every 60 minutes
    @Value("${artemis.continuous-integration.container-cleanup.cleanup-schedule-minutes:60}")
    private int containerCleanupScheduleMinutes;

    // The image architecture that is supported by the build agent
    // amd64 is the default value, as this is the architecture of Intel and AMD CPUs, which most systems still use
    @Value("${artemis.continuous-integration.image-architecture:amd64}")
    private String imageArchitecture;

    public LocalCIDockerService(DockerClient dockerClient, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, BuildJobContainerService buildJobContainerService,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.dockerClient = dockerClient;
        this.hazelcastInstance = hazelcastInstance;
        this.buildJobContainerService = buildJobContainerService;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // Schedule the cleanup of dangling build containers once 10 seconds after the application has started and then every containerCleanupScheduleMinutes minutes
        taskScheduler.scheduleAtFixedRate(this::cleanUpContainers, Instant.now().plusSeconds(10), Duration.ofMinutes(containerCleanupScheduleMinutes));
    }

    /**
     * Cleans up dangling build containers from the system. This method differentiates between the initial cleanup
     * and subsequent cleanups to handle containers differently based on their age and status.
     * <p>
     * For the initial cleanup, it removes all containers that match the build container prefix, assuming these containers
     * are left from before the application started. For subsequent cleanups, it only removes containers that are older
     * than a specified age threshold (defaulted to 5 minutes), targeting containers likely stuck or inactive.
     * <p>
     * Detailed steps include:
     * - Logging the start of the cleanup process.
     * - Determining whether it's the initial or a subsequent cleanup.
     * - Listing all containers, filtering them based on name prefix and, for subsequent cleanups, their age.
     * - Forcibly removing the identified dangling containers.
     * - Logging the results and completion of the cleanup process.
     *
     * @implNote The method uses Docker commands to list and remove containers. It handles state changes using a flag
     *           (`isFirstCleanup`) to toggle the cleanup logic between the initial and subsequent runs.
     */
    public void cleanUpContainers() {
        List<Container> danglingBuildContainers;
        log.info("Start cleanup dangling build containers");
        if (isFirstCleanup) {
            // Cleanup all dangling build containers after the application has started
            try {
                danglingBuildContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                        .filter(container -> container.getNames()[0].startsWith("/" + buildContainerPrefix)).toList();
            }
            finally {
                isFirstCleanup = false;
            }
        }
        else {
            // Cleanup all containers that are older than 5 minutes (or ageThreshold) for all subsequent cleanups
            // Get current time in seconds
            long now = Instant.now().getEpochSecond();

            // Threshold for "stuck" containers in seconds
            long ageThreshold = containerExpiryMinutes * 60L;

            danglingBuildContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(container -> container.getNames()[0].startsWith("/" + buildContainerPrefix)).filter(container -> (now - container.getCreated()) > ageThreshold)
                    .toList();
        }

        if (!danglingBuildContainers.isEmpty()) {
            log.info("Found {} dangling build containers", danglingBuildContainers.size());
            danglingBuildContainers.forEach(container -> buildJobContainerService.stopUnresponsiveContainer(container.getId()));
        }
        log.info("Cleanup dangling build containers done");
    }

    /**
     * Callback that allows us to provide more information about the docker pull operation results
     */
    public static class MyPullImageResultCallback extends PullImageResultCallback {

        private final String buildJobId;

        private final BuildLogsMap buildLogsMap;

        MyPullImageResultCallback(String buildJobId, BuildLogsMap buildLogsMap) {
            this.buildJobId = buildJobId;
            this.buildLogsMap = buildLogsMap;
        }

        @Override
        public void onNext(PullResponseItem item) {
            String msg = "~~~~~~~~~~~~~~~~~~~~ Pull image progress: " + item.getStatus() + " ~~~~~~~~~~~~~~~~~~~~";
            log.info(msg);
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            super.onNext(item);
        }

        @Override
        public void onComplete() {
            String msg = "~~~~~~~~~~~~~~~~~~~~ Pull image complete ~~~~~~~~~~~~~~~~~~~~";
            log.info(msg);
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            super.onComplete();
        }
    }

    /**
     * Attempts to pull a specified Docker image associated with a build job if it is not already present on the local system.
     * This method uses a locking mechanism to ensure that the Docker image is not concurrently pulled by multiple threads.
     * <p>
     * The process includes:
     * - Checking if the Docker image is already available locally.
     * - If not available, acquiring a lock and checking again to handle any race conditions.
     * - Pulling the image if both checks confirm its absence.
     * - Logging the operations and their outcomes to build logs for user visibility.
     * <p>
     * This method handles specific exceptions that might occur during the Docker operations, such as NotFoundException or BadRequestException,
     * by attempting to pull the image within a locked section. Other exceptions, including interruptions during the pull process,
     * are caught and rethrown as a LocalCIException with appropriate messages.
     *
     * @param buildJob     the build job that includes the configuration with the name of the Docker image.
     * @param buildLogsMap a map for appending log entries related to the build process, facilitating real-time logging for end users.
     * @throws LocalCIException if the image pull is interrupted or fails due to other exceptions.
     */
    public void pullDockerImage(LocalCIBuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) {
        final String imageName = buildJob.buildConfig().dockerImage();
        try {
            // First check if the image is already available
            String msg = "~~~~~~~~~~~~~~~~~~~~ Inspecting docker image " + imageName + " ~~~~~~~~~~~~~~~~~~~~";
            log.info(msg);
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            var inspectImageResponse = dockerClient.inspectImageCmd(imageName).exec();
            checkImageArchitecture(imageName, inspectImageResponse, buildJob, buildLogsMap);
        }
        catch (NotFoundException | BadRequestException e) {
            lock.lock();

            // Check again if image was pulled in the meantime
            try {
                String msg = "~~~~~~~~~~~~~~~~~~~~ Inspecting docker image " + imageName + " again with a lock due to error " + e.getMessage() + " ~~~~~~~~~~~~~~~~~~~~";
                log.info(msg);
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
                var inspectImageResponse = dockerClient.inspectImageCmd(imageName).exec();
                checkImageArchitecture(imageName, inspectImageResponse, buildJob, buildLogsMap);
            }
            catch (NotFoundException | BadRequestException e2) {
                long start = System.nanoTime();
                String msg = "~~~~~~~~~~~~~~~~~~~~ Pulling docker image " + imageName + " with a lock after error " + e.getMessage() + " ~~~~~~~~~~~~~~~~~~~~";
                log.info(msg);
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);

                try {
                    // Only pull the image if the inspect command failed
                    var command = dockerClient.pullImageCmd(imageName).withPlatform(imageArchitecture);
                    var exec = command.exec(new MyPullImageResultCallback(buildJob.id(), buildLogsMap));
                    exec.awaitCompletion();

                    // Check if the image is compatible with the current architecture
                    var inspectImageResponse = dockerClient.inspectImageCmd(imageName).exec();
                    checkImageArchitecture(imageName, inspectImageResponse, buildJob, buildLogsMap);
                }
                catch (InterruptedException ie) {
                    throw new LocalCIException("Interrupted while pulling docker image " + imageName, ie);
                }
                catch (Exception ex) {
                    throw new LocalCIException("Error while pulling docker image " + imageName, ex);
                }
                String msg2 = "~~~~~~~~~~~~~~~~~~~~ Pulling docker image " + imageName + " done after " + TimeLogUtil.formatDurationFrom(start) + " ~~~~~~~~~~~~~~~~~~~~";
                log.info(msg2);
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg2);
            }
            finally {
                lock.unlock();
            }
        }
    }

    /**
     * Checks if the architecture of the Docker image is compatible with the current system.
     *
     * @param imageName            the name of the Docker image
     * @param inspectImageResponse the response from the inspect image command
     * @param buildJob             the build job that includes the configuration with the name of the Docker image
     * @param buildLogsMap         a map for appending log entries related to the build process
     */
    private void checkImageArchitecture(String imageName, InspectImageResponse inspectImageResponse, LocalCIBuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) {
        if (!imageArchitecture.equals(inspectImageResponse.getArch())) {
            var msg = "Docker image " + imageName + " is not compatible with the current architecture. Needed 'linux/" + imageArchitecture + "', but got '"
                    + inspectImageResponse.getArch() + "'";
            log.error(msg);
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            throw new LocalCIException(msg);
        }
    }

    /**
     * Schedules the deletion of Docker images that have not been used within a specified number of days, determined by {@link #imageExpiryDays}.
     * The default schedule for this cleanup task is daily at 3:00 AM, configurable via the 'cleanup-schedule-time' property in the application settings.
     * <p>
     * The process involves:
     * - Checking if image cleanup is enabled; if disabled, the operation is aborted.
     * - Retrieving a map of Docker images and their last usage dates.
     * - Listing all currently running containers to ensure their images are not deleted.
     * - Identifying all Docker images that are not currently in use.
     * - Removing images that have exceeded the configured expiry days and are not associated with any running containers.
     * <p>
     * Exception handling includes catching NotFoundException for cases where images are already deleted or not found during the cleanup process.
     *
     * @implNote This method relies on Docker commands to list images and containers, and uses Hazelcast for managing image usage data.
     * @throws NotFoundException if an attempt is made to delete an image that no longer exists on the Docker host.
     */

    @Scheduled(cron = "${artemis.continuous-integration.image-cleanup.cleanup-schedule-time:0 0 3 * * *}")
    public void deleteOldDockerImages() {

        if (!imageCleanupEnabled) {
            log.info("Docker image cleanup is disabled");
            return;
        }

        // Get map of docker images and their last build dates
        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

        // Get list of all running containers
        List<Container> containers = dockerClient.listContainersCmd().exec();

        // Create a set of image IDs of containers in use
        Set<String> imageIdsInUse = containers.stream().map(Container::getImageId).collect(Collectors.toSet());

        // Get list of all images
        List<Image> allImages = dockerClient.listImagesCmd().exec();

        // Filter out images that are in use
        List<Image> unusedImages = allImages.stream().filter(image -> !imageIdsInUse.contains(image.getId())).toList();

        Set<String> imageNames = new HashSet<>();
        for (Image image : unusedImages) {
            String[] imageRepoTags = image.getRepoTags();
            if (imageRepoTags != null) {
                Collections.addAll(imageNames, imageRepoTags);
            }
        }

        // Delete images that have not been used for more than imageExpiryDays days
        for (String dockerImage : dockerImageCleanupInfo.keySet()) {
            if (imageNames.contains(dockerImage)) {
                if (dockerImageCleanupInfo.get(dockerImage).isBefore(ZonedDateTime.now().minusDays(imageExpiryDays))) {
                    log.info("Deleting docker image {}", dockerImage);
                    try {
                        dockerClient.removeImageCmd(dockerImage).exec();
                    }
                    catch (NotFoundException e) {
                        log.warn("Docker image {} not found during cleanup", dockerImage);
                    }
                }
            }
        }
    }
}
