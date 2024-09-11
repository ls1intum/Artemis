package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.programming.service.localci.dto.BuildJobQueueItem;

/**
 * Service for Docker related operations in local CI
 */
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildAgentDockerService {

    private final ReentrantLock lock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(BuildAgentDockerService.class);

    private final DockerClient dockerClient;

    private final HazelcastInstance hazelcastInstance;

    private final BuildJobContainerService buildJobContainerService;

    private final TaskScheduler taskScheduler;

    private boolean isFirstCleanup = true;

    @Value("${artemis.continuous-integration.image-cleanup.enabled:false}")
    private Boolean imageCleanupEnabled;

    @Value("${artemis.continuous-integration.image-cleanup.expiry-days:2}")
    private int imageExpiryDays;

    @Value("${artemis.continuous-integration.image-cleanup.disk-space-threshold-mb:2000}")
    private int imageCleanupDiskSpaceThresholdMb;

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

    public BuildAgentDockerService(DockerClient dockerClient, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance,
            BuildJobContainerService buildJobContainerService, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
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
            catch (Exception ex) {
                log.error("Make sure Docker is running and configured properly! Error while listing containers for cleanup: {}", ex.getMessage(), ex);
                return;
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

            try {
                danglingBuildContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                        .filter(container -> container.getNames()[0].startsWith("/" + buildContainerPrefix)).filter(container -> (now - container.getCreated()) > ageThreshold)
                        .toList();
            }
            catch (Exception ex) {
                log.error("Make sure Docker is running! Error while listing containers for cleanup: {}", ex.getMessage(), ex);
                return;
            }
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
     * - If not available, acquiring a lock to prevent concurrent pulls.
     * - Checking for usable disk space and triggering image cleanup if the threshold is exceeded.
     * - Re-inspecting the image to confirm its absence after acquiring the lock.
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
    public void pullDockerImage(BuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) {
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
                checkUsableDiskSpaceThenCleanUp();

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
    private void checkImageArchitecture(String imageName, InspectImageResponse inspectImageResponse, BuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) {
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
     * - Getting a set of image names that are not associated with any running containers.
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

        Set<String> imageNames = getUnusedDockerImages();

        // Get map of docker images and their last build dates
        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

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

    /**
     * Checks for available disk space and triggers the cleanup of old Docker images if the available space falls below
     * {@link BuildAgentDockerService#imageCleanupDiskSpaceThresholdMb}.
     *
     * @implNote - We use the Docker root directory to check disk space availability. This is in case the Docker images are stored on a separate partition.
     *           - We need to iterate over the map entries since don't remove the oldest image from the map.
     */

    @Scheduled(fixedRateString = "${artemis.continuous-integration.image-cleanup.disk-space-check-interval-minutes:60}", initialDelayString = "${artemis.continuous-integration.image-cleanup.disk-space-check-interval-minutes:60}", timeUnit = TimeUnit.MINUTES)
    public void checkUsableDiskSpaceThenCleanUp() {
        if (!imageCleanupEnabled) {
            return;
        }
        try {
            // Get the Docker root directory to check disk space.
            File dockerRootDirectory = new File(Objects.requireNonNullElse(dockerClient.infoCmd().exec().getDockerRootDir(), "/"));
            long usableSpace = dockerRootDirectory.getUsableSpace();

            long threshold = convertMegabytesToBytes(imageCleanupDiskSpaceThresholdMb);

            if (usableSpace >= threshold) {
                return;
            }

            // Get map of docker images and their last build dates
            IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

            // Get unused images
            Set<String> unusedImages = getUnusedDockerImages();

            // Get a sorted list of images by last build date
            // We cast to ArrayList since we need the list to be mutable
            List<Map.Entry<String, ZonedDateTime>> sortedImagesByLastBuildDate = dockerImageCleanupInfo.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
            List<Map.Entry<String, ZonedDateTime>> mutableSortedImagesByLastBuildDate = new java.util.ArrayList<>(sortedImagesByLastBuildDate);

            if (mutableSortedImagesByLastBuildDate.isEmpty()) {
                return;
            }

            int deleteAttempts = 5;
            int totalAttempts = mutableSortedImagesByLastBuildDate.size(); // We limit the total number of attempts to avoid infinite loops
            Map.Entry<String, ZonedDateTime> oldestImage = mutableSortedImagesByLastBuildDate.getFirst();
            while (oldestImage != null && usableSpace < threshold && deleteAttempts > 0 && totalAttempts > 0) {
                if (unusedImages.contains(oldestImage.getKey())) {
                    log.info("Deleting docker image {}", oldestImage.getKey());
                    try {
                        dockerClient.removeImageCmd(oldestImage.getKey()).exec();
                        usableSpace = dockerRootDirectory.getUsableSpace();
                        deleteAttempts--;
                    }
                    catch (NotFoundException e) {
                        log.warn("Docker image {} not found during cleanup", oldestImage.getKey());
                    }
                }
                mutableSortedImagesByLastBuildDate.remove(oldestImage);
                oldestImage = mutableSortedImagesByLastBuildDate.getFirst();
                totalAttempts--;
            }
        }
        catch (Exception e) {
            log.error("Error while checking disk space for Docker image cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets a set of Docker image names that are not associated with any running containers.
     *
     * @return a set of image names that are not associated with any running containers.
     */
    private Set<String> getUnusedDockerImages() {
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
        return imageNames;
    }

    private long convertMegabytesToBytes(int mb) {
        long byteConversionRate = 1024L;
        return mb * byteConversionRate * byteConversionRate;
    }
}
