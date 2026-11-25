package de.tum.cit.aet.artemis.core.config;

/**
 * Inlined replacement for {@code tech.jhipster.config.JHipsterConstants}.
 * <p>
 * Defines Spring profile constants used throughout the application.
 */
public final class ArtemisConstants {

    public static final String SPRING_PROFILE_DEVELOPMENT = "dev";

    public static final String SPRING_PROFILE_TEST = "test";

    public static final String SPRING_PROFILE_E2E = "e2e";

    public static final String SPRING_PROFILE_PRODUCTION = "prod";

    public static final String SPRING_PROFILE_CLOUD = "cloud";

    public static final String SPRING_PROFILE_HEROKU = "heroku";

    public static final String SPRING_PROFILE_API_DOCS = "api-docs";

    public static final String SPRING_PROFILE_NO_LIQUIBASE = "no-liquibase";

    public static final String SPRING_PROFILE_K8S = "k8s";

    /**
     * Activates the runtime query detector. Only used in CI E2E test runs.
     * When active, the queries of every HTTP request are counted via a datasource-proxy wrapper
     * and queries that a request repeats are collected for PR reporting.
     */
    public static final String SPRING_PROFILE_E2E_PERFORMANCE = "e2e-performance";

    private ArtemisConstants() {
    }
}
