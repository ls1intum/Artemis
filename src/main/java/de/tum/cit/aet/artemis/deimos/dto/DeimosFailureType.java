package de.tum.cit.aet.artemis.deimos.dto;

/**
 * Why a single participation could not be classified.
 * <p>
 * Deimos previously collapsed every unsuccessful participation into one {@code failed} counter, which made a transient
 * infrastructure problem indistinguishable from "there was nothing to analyse". For a tool whose output is used to
 * decide whether a student behaved maliciously, that difference matters: a silently failed run looks exactly like a
 * clean one.
 */
public enum DeimosFailureType {

    /**
     * The participation has no submission snapshots, or no observed change against the exercise template.
     * This is a legitimate outcome, not an error.
     */
    NO_SNAPSHOT_HISTORY,

    /**
     * The repository could not be read, a snapshot could not be resolved, or the diff could not be built.
     * Distinct from {@link #NO_SNAPSHOT_HISTORY}: something went wrong and the participation was not actually examined.
     */
    SNAPSHOT_HISTORY_ERROR,

    /**
     * The LLM did not answer within the configured timeout, after the SDK's transport retries.
     */
    LLM_TIMEOUT,

    /**
     * The LLM endpoint rejected the request with a rate limit, after the SDK's transport retries.
     */
    LLM_RATE_LIMITED,

    /**
     * The LLM answered, but no valid verdict could be extracted from its response.
     */
    LLM_UNPARSEABLE,

    /**
     * Any other failure while calling the LLM.
     */
    LLM_ERROR,

    /**
     * Anything not covered by the categories above.
     */
    OTHER
}
