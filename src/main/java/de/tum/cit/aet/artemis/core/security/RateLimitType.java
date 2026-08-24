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
