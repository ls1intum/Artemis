package de.tum.cit.aet.artemis.admin.domain;

/**
 * What the usage of one {@link de.tum.cit.aet.artemis.core.service.featureusage.UserFeature} over a window amounts to.
 * <p>
 * Only actions and views count as use. Automatic calls by the client and calls by other systems are reported, but a
 * feature that only received those was not used by anybody: that is the case of a status probe on a page everybody
 * visits, which made features nobody used look like the busiest ones on the page.
 */
public enum FeatureUsageStatus {

    /** At least one action or view in the window. */
    USED,

    /** No action and no view, but automatic or system calls: the feature is wired into pages, but nobody used it. */
    ONLY_AUTOMATIC,

    /** Offered, but not a single call in the window. */
    UNUSED,

    /**
     * No endpoint of the feature is registered in this deployment, usually because its module is disabled here. Features
     * that are recorded outside REST only enter the inventory on their first use and count as not available until then.
     */
    NOT_AVAILABLE
}
