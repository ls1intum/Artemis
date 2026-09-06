package de.tum.cit.aet.artemis.localci.exception;

/**
 * Identifies failures that occur while pulling the exercise image for a LocalCI build, independently of the configured build runner.
 * These failures point at the exercise configuration or the registry, not at an unhealthy build agent, so they must not count towards
 * the consecutive failure counter that pauses an agent.
 */
public class ImagePullException extends LocalCIException {

    public ImagePullException(String message) {
        super(message);
    }

    public ImagePullException(String message, Throwable cause) {
        super(message, cause);
    }
}
