package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.exception.LocalCIException;

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

    @Value("${artemis.continuous-integration.image-cleanup.enabled:false}")
    private Boolean imageCleanupEnabled;

    @Value("${artemis.continuous-integration.image-cleanup.expiry-days:2}")
    private int imageExpiryDays;

    @Value("${artemis.continuous-integration.build-container-prefix:local-ci-}")
    private String buildContainerPrefix;

    public LocalCIDockerService(DockerClient dockerClient, HazelcastInstance hazelcastInstance) {
        this.dockerClient = dockerClient;
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Removes all stranded build containers after the application has started
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // NOTE: we delay this after startup, because this can take several seconds and can block the startup of the build agent otherwise
        // remove all stranded build containers after 10s
        var executor = Executors.newScheduledThreadPool(1);
        executor.schedule(() -> {
            log.info("Start cleanup stranded build containers");
            var buildContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(container -> container.getNames()[0].startsWith("/" + buildContainerPrefix)).toList();
            log.info("Found {} stranded build containers", buildContainers.size());
            buildContainers.forEach(container -> dockerClient.removeContainerCmd(container.getId()).withForce(true).exec());
            log.info("Cleanup stranded build containers done");
        }, 10, TimeUnit.SECONDS);
    }

    /**
     * Pulls a docker image if it is not already present on the system
     * Uses a lock to prevent multiple threads from pulling the same image
     *
     * @param imageName the name of the docker image
     */
    public void pullDockerImage(String imageName) {
        try {
            log.info("Inspecting docker image {}", imageName);
            dockerClient.inspectImageCmd(imageName).exec();
        }
        catch (NotFoundException e) {
            lock.lock();

            // Check again if image was pulled in the meantime
            try {
                log.info("Inspecting docker image {} again", imageName);
                dockerClient.inspectImageCmd(imageName).exec();
            }
            catch (NotFoundException e2) {
                log.info("Pulling docker image {}", imageName);
                try {
                    dockerClient.pullImageCmd(imageName).exec(new PullImageResultCallback()).awaitCompletion();
                }
                catch (InterruptedException ie) {
                    throw new LocalCIException("Interrupted while pulling docker image " + imageName, ie);
                }
                catch (Exception e3) {
                    throw new LocalCIException("Error while pulling docker image " + imageName, e3);
                }
            }
            finally {
                lock.unlock();
            }

        }
        catch (BadRequestException e) {
            throw new LocalCIException("Error while inspecting docker image " + imageName, e);
        }
    }

    /**
     * Deletes all docker images that have not been used for more than {@link #imageExpiryDays} days on a schedule
     * If not otherwise specified, the schedule is set to 3:00 AM every day
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
                if (dockerImageCleanupInfo.get(dockerImage).isBefore(ZonedDateTime.now().minus(imageExpiryDays, ChronoUnit.DAYS))) {
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
