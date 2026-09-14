package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

/** Why the worker stopped generating candidates. Names map to the public terminal contract. */
enum TerminationReason {
    /** The candidate was mechanically verified and its quality review found nothing blocking. */
    CONVERGED,
    /** Every semantic repair round the run was allowed had been spent while blocking findings remained. */
    REPAIR_BUDGET_EXHAUSTED,
    /** The last authoring attempt of the run had been made. */
    ATTEMPT_CAP_REACHED,
    /** The bounded mechanical repair phase ran out before any candidate ever built and graded. */
    MECHANICAL_REPAIR_EXHAUSTED,
    /** A semantic repair broke mechanical verification and the single narrow correction granted afterwards did not restore it. */
    POST_REPAIR_CORRECTION_EXHAUSTED,
    /** Blocking findings remained with repair budget still unspent, because none of them maps to a repairable surface. */
    NO_SCHEDULABLE_SURFACE,
    /** The quality review never returned a usable verdict, even after its one retry, so no repair could be scheduled from it. */
    REVIEW_UNAVAILABLE,
    /** The agent submitted the previously rejected candidate unchanged, so re-verifying it could only repeat the same verdict. */
    UNCHANGED_CANDIDATE_RESUBMITTED,
    /** A semantic repair introduced a new blocker or failed to remove any existing blocker, so the previous reviewed checkpoint was retained. */
    REPAIR_DID_NOT_IMPROVE,
    /** Concept exploration completed, but no candidate satisfied the instructor brief and learning-fit review, and none was usable as a fallback either. */
    NO_ADMISSIBLE_CONCEPT,
    /**
     * Concept exploration admitted no candidate, so the run proceeded with the least-rejected one and stopped with the reviewer's objections attached. Refines
     * {@link #NO_SCHEDULABLE_SURFACE}: the contested artifact is the exercise idea rather than any file the repair loop could edit.
     */
    CONCEPT_ADMITTED_WITH_FINDINGS,
    /** The run was stopped cooperatively (instructor cancellation, lost job ownership, or an unclassified stop signal). */
    CANCELLED,
    /** The run exceeded its configured wall-clock budget. */
    DEADLINE_EXCEEDED,
    /** The run exceeded its configured provider token budget. */
    TOKEN_BUDGET_EXHAUSTED,
    /** The agent loop itself ended in an error before a verdict could be reached. */
    AGENT_ERROR,
    /** The sandbox or build environment could not host the run, so no attempt was made. */
    ENVIRONMENT_UNAVAILABLE,
    /** The run failed with an unexpected exception. */
    RUN_FAILED,
    /** The job never reached the attempt loop (superseded, expired, or its exercise was no longer generatable). */
    NOT_STARTED
}
