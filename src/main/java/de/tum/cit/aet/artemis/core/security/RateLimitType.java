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
     * during an exam peak. Its cheap gates (a single-key agent lookup and the build agent network allowlist) reject
     * anything that is not a registered agent calling from an allowed network, but past them it reads the whole
     * distributed processing job map to find the presented token. A caller inside the build agent networks who knows a
     * registered agent name - it is an identifier, shown in the admin UI - could otherwise force that read in a loop
     * with arbitrary passwords, unbounded. This limit bounds it.
     * <p>
     * Deliberately generous: a single agent clones several repositories per build job and runs jobs concurrently, so
     * the limit has to sit far above real agent traffic to avoid becoming the stall it exists to prevent. It is a
     * ceiling on abuse, not a throttle on agents.
     * <p>
     * Default: 300 requests per minute per client.
     */
    BUILD_AGENT_CLONE_TOKEN(300),

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
