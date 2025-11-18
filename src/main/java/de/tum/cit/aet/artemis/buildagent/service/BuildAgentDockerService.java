package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.nio.file.Files;
import java.nio.file.Path;
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

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

/**
 * Service for Docker related operations in local CI
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildAgentDockerService {

    private final ReentrantLock lock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(BuildAgentDockerService.class);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final DistributedDataAccessService distributedDataAccessService;

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

    public BuildAgentDockerService(BuildAgentConfiguration buildAgentConfiguration, DistributedDataAccessService distributedDataAccessService,
            BuildJobContainerService buildJobContainerService, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildJobContainerService = buildJobContainerService;
        this.taskScheduler = taskScheduler;
    }

    // EventListener cannot be used here, as the bean is lazy
    // https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation
    @PostConstruct
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

        if (dockerClientNotAvailable("Cannot clean up dangling build containers.")) {
            return;
        }

        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        if (isFirstCleanup) {
            // Cleanup all dangling build containers after the application has started
            try {
                danglingBuildContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                        .filter(container -> container.getNames()[0].startsWith("/" + buildContainerPrefix)).toList();
            }
            catch (Exception ex) {
                if (DockerUtil.isDockerNotAvailable(ex)) {
                    log.error("Cannot connect to Docker Host. Make sure Docker is running and configured properly! Error while listing containers for cleanup: {}",
                            ex.getMessage());
                    return;
                }
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

        @Override
        public void onNext(PullResponseItem item) {
            String msg = "~~~~~~~~~~~~~~~~~~~~ Pull image progress: " + item.getStatus() + " ~~~~~~~~~~~~~~~~~~~~";
            log.debug(msg);
            super.onNext(item);
        }

        @Override
        public void onComplete() {
            String msg = "~~~~~~~~~~~~~~~~~~~~ Pull image complete ~~~~~~~~~~~~~~~~~~~~";
            log.debug(msg);
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
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        final String imageName = buildJob.buildConfig().dockerImage();
        try (var inspectImageCommand = dockerClient.inspectImageCmd(imageName)) {
            // First check if the image is already available
            String msg = "~~~~~~~~~~~~~~~~~~~~ Inspecting docker image " + imageName + " ~~~~~~~~~~~~~~~~~~~~";
            log.info(msg);
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            var inspectImageResponse = inspectImageCommand.exec();
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
                    var exec = command.exec(new MyPullImageResultCallback());
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
            catch (Exception ex) {
                if (DockerUtil.isDockerNotAvailable(ex)) {
                    log.error("Cannot connect to Docker Host. Make sure Docker is running and configured properly! Error while inspecting image: {}", ex.getMessage());
                }
                throw new LocalCIException("Cannot connect to Docker Host. Make sure Docker is running and configured properly!", ex);
                // Do not proceed if Docker is not running
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
        Map<String, ZonedDateTime> dockerImageCleanupInfo = distributedDataAccessService.getDockerImageCleanupInfoMap();

        // Delete images that have not been used for more than imageExpiryDays days
        for (String dockerImage : dockerImageCleanupInfo.keySet()) {
            if (imageNames.contains(dockerImage)) {
                if (dockerImageCleanupInfo.get(dockerImage).isBefore(ZonedDateTime.now().minusDays(imageExpiryDays))) {
                    log.info("Remove docker image {} because it was not used for at least {} days", dockerImage, imageExpiryDays);
                    try (final var removeCommand = buildAgentConfiguration.getDockerClient().removeImageCmd(dockerImage)) {
                        removeCommand.exec();
                    }
                    catch (NotFoundException e) {
                        log.warn("Docker image {} not found during cleaning up old docker images", dockerImage);
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
        if (!imageCleanupEnabled || dockerClientNotAvailable("Cannot check disk space for Docker image cleanup.")) {
            return;
        }

        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try {
            // Get the Docker root directory to check disk space.
            Path dockerRootDirectory = Path.of(Objects.requireNonNullElse(dockerClient.infoCmd().exec().getDockerRootDir(), "/"));
            long usableSpace = Files.getFileStore(dockerRootDirectory).getUsableSpace();

            long threshold = convertMegabytesToBytes(imageCleanupDiskSpaceThresholdMb);

            if (usableSpace >= threshold) {
                return;
            }

            // Get map of docker images and their last build dates
            Map<String, ZonedDateTime> dockerImageCleanupInfo = distributedDataAccessService.getDockerImageCleanupInfoMap();

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
                    log.info("Remove oldest docker image {} to cleanup disk space to avoid filling up the hard disk", oldestImage.getKey());
                    try {
                        dockerClient.removeImageCmd(oldestImage.getKey()).exec();
                        usableSpace = Files.getFileStore(dockerRootDirectory).getUsableSpace();
                        deleteAttempts--;
                    }
                    catch (NotFoundException e) {
                        log.warn("Docker image {} not found during disk cleanup", oldestImage.getKey());
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
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        if (dockerClientNotAvailable("Cannot get unused Docker images")) {
            return Set.of();
        }

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

    private boolean dockerClientNotAvailable(String additionalLogInfo) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        if (dockerClient == null) {
            BuildAgentInformation.BuildAgentStatus status = distributedDataAccessService.getLocalBuildAgentStatus();
            if ((status == BuildAgentInformation.BuildAgentStatus.PAUSED || status == BuildAgentInformation.BuildAgentStatus.SELF_PAUSED)) {
                log.info("Docker client is not available because the build agent is paused. {} This is expected behavior.", additionalLogInfo);
                return true;
            }
            log.error("Docker client is not available. {}", additionalLogInfo);
            return true;
        }
        return false;
    }
}
