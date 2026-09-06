package de.tum.cit.aet.artemis.core.domain;

/**
 * The namespace a tracked feature belongs to. Part of the unique key of {@link TrackedFeature}, so the three kinds
 * cannot collide even if they happen to produce the same identifier.
 */
public enum FeatureKind {

    /**
     * A Spring MVC endpoint. Discovered automatically at startup, identified by its HTTP verb and canonical templated
     * path, so no instrumentation is needed to cover the whole API.
     */
    REST,

    /**
     * A git operation served by the embedded LocalVC git server. Tracked separately because those requests never reach
     * a Spring MVC handler, which is also why Micrometer cannot report them usefully.
     */
    GIT,

    /**
     * Server-initiated work that no user request triggers, for instance a scheduled job or an asynchronous pipeline.
     * These are the only features that need an explicit call to record them.
     */
    BACKGROUND
}
