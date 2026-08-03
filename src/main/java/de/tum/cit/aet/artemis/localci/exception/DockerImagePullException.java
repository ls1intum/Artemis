package de.tum.cit.aet.artemis.localci.exception;

/**
 * Identifies failures that occur while preparing a Docker image for a LocalCI build.
 * These failures do not indicate that the build agent itself is unhealthy.
 */
public class DockerImagePullException extends ImagePullException {

    public DockerImagePullException(String message, Throwable cause) {
        super(message, cause);
    }
}
