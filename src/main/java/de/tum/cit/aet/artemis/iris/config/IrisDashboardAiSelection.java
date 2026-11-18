package de.tum.cit.aet.artemis.iris.config;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * Restricts the Iris admin dashboard to users by their AI selection.
 * <p>
 * Artemis stores the selection per user only, not per session or message, so the filter uses each user's current
 * selection. A user who switched later is counted under the current choice for all of their past activity. Sessions and messages
 * belong to the session's user, token costs to the user of the usage trace.
 */
public enum IrisDashboardAiSelection {

    /** No restriction: every user, including users without a recorded selection. */
    ALL(null),
    /** Users whose current selection is {@link AiSelectionDecision#CLOUD_AI}. */
    CLOUD(AiSelectionDecision.CLOUD_AI),
    /** Users whose current selection is {@link AiSelectionDecision#LOCAL_AI}. */
    LOCAL(AiSelectionDecision.LOCAL_AI);

    /**
     * The value the dashboard queries compare against. They skip the filter for this value. It is never null, because
     * PostgreSQL cannot determine the type of a parameter that is only compared with {@code IS NULL}.
     */
    private static final String NO_FILTER = "ALL";

    private final AiSelectionDecision decision;

    IrisDashboardAiSelection(AiSelectionDecision decision) {
        this.decision = decision;
    }

    /**
     * @return the value to bind to the {@code aiSelection} parameter of the dashboard queries
     */
    public String queryValue() {
        return decision == null ? NO_FILTER : decision.name();
    }
}
