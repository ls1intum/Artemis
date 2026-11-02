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
     * switched on</em>, which it is unless an operator sets {@code artemis.rate-limiting.enabled} to false. On an installation
     * that switches it off, the cheap gates above are the only bound on reaching the scan.
     * <p>
     * It is a bound on guessing, and sized like one, because two things keep it away from real agents. An address that
     * some build agent is registered at is exempt, which tracks the agents automatically rather than through a
     * hand-maintained list. And only a check that <em>declines</em> spends budget, so an agent whose checks succeed
     * never approaches the limit no matter how many repositories it clones - which matters for an agent with no
     * registration to be exempt by, such as one sharing a JVM with a core node.
     * <p>
     * Default: 30 requests per minute per client, the same order as the other credential-checking limits. Rate limiting
     * is on unless {@code artemis.rate-limiting.enabled} is set to false;
     * {@code artemis.rate-limiting.build-agent-clone-token-requests-per-minute} overrides the default.
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
    AI_SEARCH_PIPELINE(120),

    /**
     * Rate limit for the proactive struggle-intervention trigger, which dispatches a Pyris pipeline that need not
     * persist a message, and so can spend an LLM call without touching the message-counted Iris budget.
     * <p>
     * Sized for a shared address, not for one student: the limit is per client address, a lecture hall can sit
     * behind one NAT, and the editor's detector cooldown keeps a real student near one trigger every two minutes.
     * A single abusive student is bounded instead by the admission cooldown, which runs per student, exercise and
     * intent.
     * <p>
     * Default: 300 requests per minute per client, overridable with
     * {@code artemis.rate-limiting.struggle-intervention-requests-per-minute}.
     */
    STRUGGLE_INTERVENTION(300),

    /**
     * Rate limit for the slides-and-videos search behind global search. Each request embeds the query and searches the
     * content index, and the search palette sends one per pause in typing.
     * <p>
     * Sized for a shared address, like {@link #STRUGGLE_INTERVENTION}: the limit is per client address, and a lecture
     * hall can sit behind one NAT.
     * <p>
     * Default: 300 requests per minute per client, overridable with
     * {@code artemis.rate-limiting.content-search-requests-per-minute}.
     */
    CONTENT_SEARCH(300),

    /**
     * Rate limit for the editor-facing fetch of an online resource's metadata. Each request makes the server open an
     * outbound connection to a user-supplied link, so the limit bounds that use of the server as an HTTP client.
     * <p>
     * Kept in its own bucket rather than sharing {@link #AUTHENTICATION}, so that editors previewing links never draw
     * down the allowance that logins and git authentication of everyone behind the same address depend on.
     * <p>
     * Default: 30 requests per minute per client, overridable with
     * {@code artemis.rate-limiting.online-resource-fetch-requests-per-minute}.
     */
    ONLINE_RESOURCE_FETCH(30),

    /**
     * Rate limit for client error reports sent by clients that are not authenticated, such as an error on the login page
     * or an error that prevents the application from starting. Reports of authenticated users are not limited by this.
     * <p>
     * Every report writes a log entry, so the limit bounds how fast one address can fill the log. It is sized for a
     * shared address, not for one user: a lecture hall can sit behind one NAT, and the client already drops duplicate
     * reports and caps the reports of a page.
     * <p>
     * Unlike every other type, this limit cannot be switched off: it applies even if {@code artemis.rate-limiting.enabled} is
     * {@code false} or the rate limit feature is toggled off, because the endpoint is open to everybody and an installation
     * that disables rate limiting for its logins must not leave its log open to flooding. Only an address in
     * {@code artemis.rate-limiting.exempt-addresses} is not limited.
     * <p>
     * The limit counts the TCP peer as Tomcat resolved it, and never reads a forwarding header itself, so that a sender cannot choose its own
     * budget. A request that exceeds it is answered with 429 and is not logged above debug level, because a limit that bounds the log must
     * not write a line for every rejection.
     * <p>
     * Default: 60 requests per minute per client, overridable with
     * {@code artemis.rate-limiting.client-error-report-requests-per-minute}.
     */
    CLIENT_ERROR_REPORT(60, true, false);

    private final int defaultRpm;

    private final boolean alwaysEnforced;

    private final boolean rejectionLogged;

    RateLimitType(int defaultRpm) {
        this(defaultRpm, false, true);
    }

    RateLimitType(int defaultRpm, boolean alwaysEnforced, boolean rejectionLogged) {
        this.defaultRpm = defaultRpm;
        this.alwaysEnforced = alwaysEnforced;
        this.rejectionLogged = rejectionLogged;
    }

    /**
     * Whether a request that exceeds the limit is logged as a warning. It is for the limits that guard logins and accounts, where a
     * rejection can be the sign of an attack. A limit that bounds the size of the log itself is not, as that would let the sender fill it.
     *
     * @return {@code true} if every rejection is logged as a warning
     */
    public boolean isRejectionLogged() {
        return rejectionLogged;
    }

    /**
     * Whether the limit is enforced even if rate limiting is switched off globally or toggled off as a feature.
     *
     * @return {@code true} if this limit cannot be disabled
     */
    public boolean isAlwaysEnforced() {
        return alwaysEnforced;
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
