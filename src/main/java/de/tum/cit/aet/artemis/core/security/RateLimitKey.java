package de.tum.cit.aet.artemis.core.security;

/**
 * Identity that a rate limit counts requests against.
 * <p>
 * The bucket key is built from this identity, so it decides who shares a budget. Most limits protect an
 * unauthenticated or credential-checking path and therefore count per client address ({@link #IP}). Limits on
 * authenticated, per-user work count per user ({@link #USER}) instead, so that many users behind one shared
 * address (for example a whole campus network behind NAT) do not drain a single common budget.
 */
public enum RateLimitKey {

    /**
     * Count per client address. The default, and the right shape for unauthenticated or credential-checking
     * endpoints where no stable user identity is available yet.
     */
    IP,

    /**
     * Count per authenticated user. Suitable for authenticated endpoints whose cost is attributable to the acting
     * user, so that users sharing a source address do not throttle one another.
     */
    USER
}
