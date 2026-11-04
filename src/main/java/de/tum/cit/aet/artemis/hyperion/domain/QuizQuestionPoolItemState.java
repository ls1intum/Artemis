package de.tum.cit.aet.artemis.hyperion.domain;

/**
 * Lifecycle of one intended pool question.
 * <p>
 * {@link #GENERATING} and {@link #FILTERING} are claimed states held only while a worker is acting on the item. A claim older
 * than the stale threshold means the worker died, and the item is returned to the preceding stable state at the next build.
 */
public enum QuizQuestionPoolItemState {

    /** Awaiting generation. */
    PENDING,

    /** Claimed for generation. */
    GENERATING,

    /** Generated and stored, awaiting the filter. */
    GENERATED,

    /** Claimed for filtering. */
    FILTERING,

    /** Generated and judged; terminal. */
    FILTERED,

    /** Generation failed permanently; terminal unless the failure was transient. */
    FAILED_GENERATION,

    /** Filtering failed permanently; terminal unless the failure was transient. */
    FAILED_FILTER
}
