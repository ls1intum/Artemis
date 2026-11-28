package de.tum.cit.aet.artemis.iris.domain.message;

/**
 * The step of the solution workflow that the latest student message is, derived by Artemis from the stored offers and choices.
 * Pyris acts on it as given; without a step it runs its classifier.
 */
public enum IrisSolutionStep {
    /** The student clicked "try it myself with help" on the latest offer. */
    TRY_MYSELF,
    /** The student clicked "show me the complete solution"; the instructor solution is attached. */
    SHOW_SOLUTION,
    /** The student answered the latest solution offer in text instead of clicking; Pyris reads the choice from it, so the instructor solution is attached. */
    OFFER_REPLY,
    /** The student agreed to summarize the delivered solution in their own words; Iris asks them to. */
    SUMMARIZE,
    /** The student answered the summary offer in text instead of clicking; Pyris reads the choice from it. */
    SUMMARY_REPLY,
    /** The message follows the request to explain the solution's key idea in the student's own words. */
    EXPLAIN_SOLUTION
}
