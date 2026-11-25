package de.tum.cit.aet.artemis.iris.domain.message;

/**
 * The step of the solution workflow that the latest student message is, derived by Artemis from the stored choices.
 * Pyris acts on it as given; without a step it runs its classifier.
 */
public enum IrisSolutionStep {
    /** The student clicked "try it myself with help" on the latest offer. */
    TRY_MYSELF,
    /** The student clicked "show me the complete solution"; the instructor solution is attached. */
    SHOW_SOLUTION,
    /** The message follows a delivered solution, which asked the student to explain it in their own words. */
    EXPLAIN_SOLUTION
}
