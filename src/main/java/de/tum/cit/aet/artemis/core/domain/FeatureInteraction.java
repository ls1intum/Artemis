package de.tum.cit.aet.artemis.core.domain;

/**
 * What a call to a tracked feature says about its use.
 * <p>
 * A raw call count cannot answer whether a feature is used. A status probe that a page sends on every load produces far
 * more calls than the action that feature exists for, so a feature nobody uses can look like the busiest one on the page.
 * The interaction separates the calls that reflect a decision of the user from the ones that do not, and the report counts
 * them separately.
 * <p>
 * The interaction is a property of the endpoint, not of the individual call: it is derived once at startup and stored on
 * {@link TrackedFeature}. An endpoint that serves two purposes, such as a real action and a status probe, has to be split
 * into two endpoints for the report to tell them apart.
 */
public enum FeatureInteraction {

    /**
     * The user did something with the feature: created, submitted, started or generated something. The default for every
     * endpoint that is not a {@code GET} or {@code HEAD}.
     */
    ACTION,

    /**
     * The user looked at something the feature shows. The default for {@code GET} and {@code HEAD} endpoints.
     */
    VIEW,

    /**
     * The client called the endpoint on its own, without the user engaging with the feature: a status probe, a badge, a
     * poll, a title lookup for a breadcrumb. Reported, but never counted as use.
     */
    AUTOMATIC,

    /**
     * Another system called the endpoint: a callback from an external service, a build agent, a calendar client fetching a
     * subscription. Reported, but never counted as use. The default for {@code @Internal} endpoints.
     */
    SYSTEM
}
