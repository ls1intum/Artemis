package de.tum.cit.aet.artemis.iris.domain.message;

/** The student's explicit response to an offer of the solution workflow. */
public enum IrisSolutionChoice {
    /** Solution offer: work on it with help. */
    TRY_MYSELF,
    /** Solution offer: see the complete solution. */
    SHOW_SOLUTION,
    /** Summary offer after a delivered solution: the student summarizes it in their own words as practice. */
    SUMMARIZE,
    /** Summary offer after a delivered solution: decline; Iris does not answer the click. */
    SKIP_SUMMARY
}
