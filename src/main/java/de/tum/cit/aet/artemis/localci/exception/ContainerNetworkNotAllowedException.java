package de.tum.cit.aet.artemis.localci.exception;

/**
 * Thrown when a build requests a container network that the build agent does not allow. The failure is a property of the exercise and not of the build agent, so it does not count
 * towards the consecutive failures after which the agent pauses itself.
 */
public class ContainerNetworkNotAllowedException extends LocalCIException {

    public ContainerNetworkNotAllowedException(String message) {
        super(message);
    }
}
