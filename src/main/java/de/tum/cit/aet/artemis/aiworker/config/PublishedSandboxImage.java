package de.tum.cit.aet.artemis.aiworker.config;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;

/**
 * Resolves the sandbox image that CI publishes for each Artemis commit, so a worker needs no image configuration, just as a build agent pulls its build images.
 * The tag is resolved once at startup to its registry digest; every claim and session then uses that immutable reference.
 */
final class PublishedSandboxImage {

    static final String REPOSITORY = "ghcr.io/ls1intum/artemis-hyperion-sandbox";

    private static final Logger log = LoggerFactory.getLogger(PublishedSandboxImage.class);

    private static final Pattern COMMIT_ID = Pattern.compile("[0-9a-f]{40}");

    private static final long PULL_TIMEOUT_MINUTES = 30;

    private PublishedSandboxImage() {
    }

    /**
     * Pulls the published sandbox image of the given commit if the daemon does not have it yet and returns its digest reference.
     *
     * @param dockerClient the worker's Docker client
     * @param commitId     the full commit ID of the running Artemis build
     * @return the image as {@code ghcr.io/ls1intum/artemis-hyperion-sandbox@sha256:<digest>}
     */
    static String resolve(DockerClient dockerClient, String commitId) {
        if (commitId == null || !COMMIT_ID.matcher(commitId).matches()) {
            throw new IllegalStateException(
                    "This Artemis build has no full commit ID, so the published sandbox image cannot be selected. Configure artemis.aiworker.image (AI_WORKER_SANDBOX_IMAGE).");
        }
        String reference = REPOSITORY + ":sha-" + commitId;
        // Pull unless the daemon already has this image with its registry digest; a locally built or re-tagged image has none.
        Optional<String> digest = registryDigest(dockerClient, reference);
        if (digest.isEmpty()) {
            log.info("Pulling the published sandbox image {}", reference);
            try {
                if (!dockerClient.pullImageCmd(reference).exec(new PullImageResultCallback()).awaitCompletion(PULL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    throw new IllegalStateException("Pulling the sandbox image " + reference + " did not finish within " + PULL_TIMEOUT_MINUTES + " minutes.");
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while pulling the sandbox image " + reference, e);
            }
            catch (DockerException e) {
                throw new IllegalStateException("Could not pull the sandbox image " + reference
                        + ". CI publishes it for develop and release builds; for other builds, configure artemis.aiworker.image (AI_WORKER_SANDBOX_IMAGE).", e);
            }
            digest = registryDigest(dockerClient, reference);
        }
        return digest.orElseThrow(() -> new IllegalStateException("The sandbox image " + reference + " has no registry digest after pulling it."));
    }

    /** The image's digest in the published repository, or empty when the daemon does not have the image or has it without that digest. */
    private static Optional<String> registryDigest(DockerClient dockerClient, String reference) {
        try (var inspect = dockerClient.inspectImageCmd(reference)) {
            List<String> digests = inspect.exec().getRepoDigests();
            return digests == null ? Optional.empty() : digests.stream().filter(digest -> digest.startsWith(REPOSITORY + "@sha256:")).findFirst();
        }
        catch (NotFoundException e) {
            return Optional.empty();
        }
    }
}
