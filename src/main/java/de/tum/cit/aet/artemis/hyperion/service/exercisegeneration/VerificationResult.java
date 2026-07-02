package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.List;

/**
 * Outcome of the out-of-band authoritative verification of a generated exercise.
 * <p>
 * The exercise is accepted only when the differential oracle holds: the solution compiles and passes all tests, and the template compiles but fails them (a student starting
 * from the template has not yet done the work). A passing template, or a test suite that is empty or trivially satisfied, is rejected. The {@code reasons} list explains any
 * failure in human-readable terms that can both be shown to the instructor and fed back to the agent for another iteration.
 *
 * @param accepted       whether the exercise passed all gates
 * @param solutionPassed whether the solution compiled and passed all tests
 * @param templateFailed whether the template compiled and (correctly) failed the tests
 * @param testCount      the number of tests discovered (must be greater than zero)
 * @param reasons        human-readable explanations of any failed gate (empty when accepted)
 */
public record VerificationResult(boolean accepted, boolean solutionPassed, boolean templateFailed, int testCount, List<String> reasons) {

    /**
     * @return a compact report suitable both for the instructor-facing transcript and for feeding back to the agent
     */
    public String report() {
        if (accepted) {
            return "Verification passed: the solution passes all " + testCount + " tests and the template correctly fails them.";
        }
        return "Verification failed:\n- " + String.join("\n- ", reasons);
    }
}
