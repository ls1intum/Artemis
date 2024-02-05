package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;

import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild;

/**
 * Service for Docker related operations in local CI
 */
@Service
@Profile("buildagent")

public class LocalCIDockerService {

    private final ReentrantLock lock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(LocalCIDockerService.class);

    private final DockerClient dockerClient;

    private final BuildJobRepository buildJobRepository;

    @Value("${artemis.continuous-integration.image-cleanup.enabled:false}")
    private Boolean imageCleanupEnabled;

    @Value("${artemis.continuous-integration.image-cleanup.expiry-days:2}")
    private int imageExpiryDays;

    public LocalCIDockerService(DockerClient dockerClient, BuildJobRepository buildJobRepository) {
        this.dockerClient = dockerClient;
        this.buildJobRepository = buildJobRepository;
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

        // Get list of all running containers
        List<Container> containers = dockerClient.listContainersCmd().exec();

        // Create a set of image IDs of containers in use
        Set<String> imageIdsInUse = containers.stream().map(Container::getImageId).collect(Collectors.toSet());

        // Get list of all images
        List<Image> allImages = dockerClient.listImagesCmd().exec();

        // Filter out images that are in use
        List<Image> unusedImages = allImages.stream().filter(image -> !imageIdsInUse.contains(image.getId())).toList();

        List<String> imageName = new ArrayList<>();
        for (Image image : unusedImages) {
            String[] imageRepoTags = image.getRepoTags();
            if (imageRepoTags != null) {
                imageName.addAll(Arrays.asList(imageRepoTags));
            }
        }

        Set<DockerImageBuild> lastBuildDatesForDockerImages = buildJobRepository.findLastBuildDatesForDockerImages(imageName);

        // Delete images that have not been used for more than imageExpiryDays days
        for (DockerImageBuild dockerImageBuild : lastBuildDatesForDockerImages) {
            if (dockerImageBuild.lastBuildCompletionDate().isBefore(ZonedDateTime.now().minus(imageExpiryDays, ChronoUnit.DAYS))) {
                log.info("Deleting docker image {}", dockerImageBuild.dockerImage());
                try {
                    dockerClient.removeImageCmd(dockerImageBuild.dockerImage()).exec();
                }
                catch (NotFoundException e) {
                    log.warn("Docker image {} not found", dockerImageBuild.dockerImage());
                }
            }
        }
    }
}
