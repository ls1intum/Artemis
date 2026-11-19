package de.tum.cit.aet.artemis.aiworker.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;

/**
 * Prepares the configured sandbox image using the build agents' local-cache-first pull policy.
 * Resolves tags to a local image ID before worker admission, so running jobs cannot switch images.
 */
final class WorkerSandboxImageResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkerSandboxImageResolver.class);

    private static final Pattern IMAGE_ID_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");

    private static final long PULL_TIMEOUT_MINUTES = 30;

    private WorkerSandboxImageResolver() {
    }

    /**
     * Uses a cached image or pulls the configured reference, then binds the worker to its immutable local image ID.
     *
     * @param dockerClient the worker's Docker client
     * @param reference    the configured versioned tag, registry digest or local image ID
     * @return the immutable local image ID
     */
    static String resolve(DockerClient dockerClient, String reference) {
        try {
            return inspectImageId(dockerClient, reference);
        }
        catch (NotFoundException e) {
            if (IMAGE_ID_PATTERN.matcher(reference).matches()) {
                throw new IllegalStateException("Local sandbox image " + reference + " is not available. Load it on this Docker daemon or configure AI_WORKER_SANDBOX_IMAGE.", e);
            }
            log.info("Pulling sandbox image {}", reference);
        }
        try (var pull = dockerClient.pullImageCmd(reference); var callback = new PullImageResultCallback()) {
            if (!pull.exec(callback).awaitCompletion(PULL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Pulling the sandbox image " + reference + " did not finish within " + PULL_TIMEOUT_MINUTES + " minutes.");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pulling the sandbox image " + reference, e);
        }
        catch (DockerException | IOException e) {
            throw new IllegalStateException(
                    "Could not pull the sandbox image " + reference + ". Check registry access or configure artemis.aiworker.image (AI_WORKER_SANDBOX_IMAGE).", e);
        }
        return inspectImageId(dockerClient, reference);
    }

    private static String inspectImageId(DockerClient dockerClient, String reference) {
        try (var inspect = dockerClient.inspectImageCmd(reference)) {
            String id = inspect.exec().getId();
            if (id == null || !IMAGE_ID_PATTERN.matcher(id).matches()) {
                throw new IllegalStateException("Sandbox image " + reference + " has no valid immutable image ID");
            }
            return id;
        }
    }
}
