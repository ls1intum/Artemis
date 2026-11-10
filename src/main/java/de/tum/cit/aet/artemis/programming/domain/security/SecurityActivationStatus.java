package de.tum.cit.aet.artemis.programming.domain.security;

/**
 * The settled state of the Security Framework for a programming exercise, derived from its exercise-tests repository.
 * <ul>
 * <li>{@code CONFIGURED}: at least one test class has an active Ares policy, and the files Ares needs are present and plausible. This is a static check of the
 * repository only: it does not prove that Ares accepts the policy. Only a result that Ares itself reports from a build can confirm that (decision B11 of
 * Ares2 issue #386).</li>
 * <li>{@code INACTIVE}: every test class has switched its policy off.</li>
 * <li>{@code MISCONFIGURED}: an active policy points to a policy file that is missing, invalid or unreadable, so Ares fails the tests.</li>
 * <li>{@code UNSUPPORTED}: the tests use Ares in a way the Security Framework cannot manage automatically, e.g. Ares 1 or no Ares at all.</li>
 * </ul>
 * The transient client states (GENERATING while a policy is being committed, DELETING while it is being
 * removed) and the ERROR state exist purely in the client while an HTTP request is in flight or has failed.
 */
public enum SecurityActivationStatus {
    INACTIVE, CONFIGURED, MISCONFIGURED, UNSUPPORTED
}
