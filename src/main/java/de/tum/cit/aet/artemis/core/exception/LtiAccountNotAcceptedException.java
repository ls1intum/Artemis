package de.tum.cit.aet.artemis.core.exception;

/**
 * Exception thrown when an account that a launch names may not be signed in from the launch. The person signs in by hand, like for any account that is already in use.
 * It is told apart from its parent, so that a launch of such an account leaves nothing behind.
 */
public class LtiAccountNotAcceptedException extends LtiEmailAlreadyInUseException {
}
