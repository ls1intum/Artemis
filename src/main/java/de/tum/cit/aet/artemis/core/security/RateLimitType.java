package de.tum.cit.aet.artemis.core.security;

/**
 * Enum defining different rate limit types with their default requests per minute (RPM) values.
 * These values can be overridden through application configuration.
 */
public enum RateLimitType {

    /**
     * Rate limit for unauthenticated authentication endpoints,
     * such as user registration and password reset requests.
     * <p>
     * Default: 5 requests per minute per client.
     */
    ACCOUNT_MANAGEMENT(5),

    /**
     * Rate limit for authenticated login endpoints,
     * including REST and Git-based authentication.
     * <p>
     * Default: 30 requests per minute per client.
     */
    AUTHENTICATION(30),

    /**
     * Rate limit for the unauthenticated login-options lookup that drives the identifier-first login form.
     * <p>
     * Kept in its own bucket rather than sharing {@link #AUTHENTICATION}: the client calls this once per login attempt,
     * immediately before authenticating, so sharing a bucket would halve the budget a real user has for logging in, and
     * git authentication would draw down the same allowance again.
     * <p>
     * The endpoint answers whether an identifier belongs to an internal account, so the budget is deliberately no larger
     * than the login budget it precedes. It stays at 30 rather than dropping to {@link #ACCOUNT_MANAGEMENT} levels because
     * a whole campus network can share one source address behind NAT, and this call sits in front of every single login.
     * <p>
     * Default: 30 requests per minute per client.
     */
    LOGIN_OPTIONS(30),

    /**
     * Rate limit for the stateless problem-statement rendering endpoint.
     * The endpoint does PlantUML rendering and HTML sanitization, which are comparatively expensive,
     * so the rate limit exists to bound abuse from authenticated clients.
     * <p>
     * Default: 30 requests per minute per client.
     */
    PROBLEM_STATEMENT_RENDERING(30),

    /**
     * Rate limit for the build agent clone-token check on the git https path.
     * <p>
     * That check runs ahead of {@link #AUTHENTICATION} on purpose, because throttling agents would stall every build
     * during an exam peak. Its cheap gates - the {@code bjct-} prefix, a single-key agent lookup and the build agent
     * network allowlist - reject anything that is not a plausible agent credential from an allowed network, but past
     * them it reads the whole distributed processing job map to find the presented token. A caller inside the build
     * agent networks who knows a registered agent name - it is an identifier, shown in the admin UI - could otherwise
     * force that read in a loop with arbitrary passwords, unbounded. This limit bounds it <em>where rate limiting is
     * switched on at all</em>: {@code artemis.rate-limiting.enabled} defaults to false, and on an installation that
     * leaves it there the cheap gates above are the only bound on reaching the scan.
     * <p>
     * It is a bound on guessing, and sized like one, because two things keep it away from real agents. An address that
     * some build agent is registered at is exempt, which tracks the agents automatically rather than through a
     * hand-maintained list. And only a check that <em>declines</em> spends budget, so an agent whose checks succeed
     * never approaches the limit no matter how many repositories it clones - which matters for an agent with no
     * registration to be exempt by, such as one sharing a JVM with a core node.
     * <p>
     * Default: 30 requests per minute per client, the same order as the other credential-checking limits. Rate limiting
     * is off unless {@code artemis.rate-limiting.enabled} is set; where it is on,
     * {@code artemis.rate-limiting.build-agent-clone-token-requests-per-minute} overrides this.
     */
    BUILD_AGENT_CLONE_TOKEN(30),

    /**
     * Rate limit for AI pipeline endpoints triggered by search-as-you-type interactions.
     * The Iris answer component uses a 600 ms debounce, giving a theoretical human maximum
     * of ~100 RPM; 120 RPM sits just above that to allow comfortable real-world use
     * while blocking automated abuse.
     * <p>
     * Default: 120 requests per minute per client.
     */
    AI_SEARCH_PIPELINE(120);

    private final int defaultRpm;

    RateLimitType(int defaultRpm) {
        this.defaultRpm = defaultRpm;
    }

    /**
     * Gets the default requests per minute for this rate limit type.
     *
     * @return the default RPM value
     */
    public int getDefaultRpm() {
        return defaultRpm;
    }
}
