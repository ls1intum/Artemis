package de.tum.in.www1.artemis.service.connectors.localci;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.repository.BuildJobRepository;

/**
 * Service for Docker related operations in local CI
 */
@Service
@Profile("localci")

public class LocalCIDockerService {

    private final ReentrantLock lock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(LocalCIDockerService.class);

    private final DockerClient dockerClient;

    private final BuildJobRepository buildJobRepository;

    public LocalCIDockerService(DockerClient dockerClient, BuildJobRepository buildJobRepository) {
        this.dockerClient = dockerClient;
        this.buildJobRepository = buildJobRepository;
    }

    @PostConstruct
    public void init() {
        deleteOldDockerImages();
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

    // delete old docker images every day at 3am
    @Scheduled(cron = "0 0 3 * * *")
    public void deleteOldDockerImages() {

        // Get list of all running containers
        List<Container> containers = dockerClient.listContainersCmd().exec();

        // Create a set of image IDs of containers in use
        Set<String> imageIdsInUse = containers.stream().map(Container::getImageId).collect(Collectors.toSet());

        // Get list of all images
        List<Image> allImages = dockerClient.listImagesCmd().exec();

        // Filter out images that are in use
        List<Image> unusedImages = allImages.stream().filter(image -> !imageIdsInUse.contains(image.getId())).toList();

        for (Image image : unusedImages) {
            String[] imageRepoTags = image.getRepoTags();
            if (imageRepoTags != null) {
                for (String imageRepoTag : imageRepoTags) {
                    Optional<BuildJob> buildJob = buildJobRepository.findFirstByDockerImageOrderByBuildStartDateDesc(imageRepoTag);

                    if (buildJob.isPresent()) {
                        ZonedDateTime buildJobStartDate = buildJob.get().getBuildStartDate();
                        ZonedDateTime now = ZonedDateTime.now(buildJobStartDate.getZone());

                        if (ChronoUnit.DAYS.between(buildJobStartDate, now) > 2) {
                            log.info("Removing image {}", imageRepoTag);
                            try {
                                dockerClient.removeImageCmd(imageRepoTag).exec();
                            }
                            catch (NotFoundException e) {
                                log.warn("Image {} not found", imageRepoTag);
                            }
                        }
                    }
                }
            }
        }
    }
}
