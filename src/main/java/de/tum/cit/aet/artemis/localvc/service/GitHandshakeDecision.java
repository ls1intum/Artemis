package de.tum.cit.aet.artemis.localvc.service;

/**
 * The outcome of the pre-resolver admission check for a git HTTP request, decided before JGit resolves the repository.
 * <p>
 * Keeping the outcomes as a typed, exhaustive set is deliberate: a {@link BuildAgentClone} does not mean "ordinary
 * authentication passed", it means "ordinary authentication must be skipped". Modelling that as the same state as an
 * authenticated user would either throttle build agents or authorise a request through the wrong path.
 */
public sealed interface GitHandshakeDecision {

    /**
     * A build agent authenticated via its clone token or the shared build-agent credential. Ordinary user
     * authentication and authorization must be skipped, exactly as the build-agent shortcut does today.
     */
    record BuildAgentClone() implements GitHandshakeDecision {
    }

    /**
     * The request carries credentials and is not a build-agent request, so it must go through ordinary user
     * authentication and authorization.
     */
    record OrdinaryAuthentication() implements GitHandshakeDecision {
    }

    /**
     * No authorization header was presented. The caller must be answered with 401 so a git client sends its credentials.
     *
     * @param expectedHandshake whether this is the expected first handshake request that every clone performs
     */
    record Unauthenticated(boolean expectedHandshake) implements GitHandshakeDecision {
    }

    /**
     * The authentication rate limit for this client is exhausted. The caller must be answered with 429, identically for
     * an existing and a missing repository, so repository existence cannot be inferred from the throttled response.
     *
     * @param retryAfterSeconds the number of seconds after which the client may retry
     */
    record RateLimited(long retryAfterSeconds) implements GitHandshakeDecision {
    }
}
