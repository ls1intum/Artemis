package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;

/**
 * Outcome of the authoritative verification of a generated exercise. Mechanical verification passes only when the differential oracle holds: the solution compiles and passes all
 * tests, and the template compiles but fails them, because a student starting from the template has not yet done the work. The {@code reasons} are worded so the same text can be
 * shown to the instructor and fed back to the agent for another iteration.
 *
 * @param mechanicallyVerified    whether the exercise passed all mechanical gates
 * @param solutionPassed          whether the solution compiled and passed all tests
 * @param templateFailed          whether the template compiled and (correctly) failed the tests
 * @param testCount               the number of tests discovered (must be greater than zero)
 * @param reasons                 human-readable explanations of any failed gate (empty when mechanically verified)
 * @param templateFailureEvidence untrusted failure diagnostics collected from the incomplete template, for reviewer navigation
 */
public record VerificationResult(boolean mechanicallyVerified, boolean solutionPassed, boolean templateFailed, int testCount, List<String> reasons,
        List<AgentVerifyReport.TestFailureEvidence> templateFailureEvidence) {

    public VerificationResult(boolean mechanicallyVerified, boolean solutionPassed, boolean templateFailed, int testCount, List<String> reasons) {
        this(mechanicallyVerified, solutionPassed, templateFailed, testCount, reasons, List.of());
    }

    /**
     * @return a compact report for the instructor-facing transcript and the agent alike
     */
    public String report() {
        if (mechanicallyVerified) {
            return "Verification passed: the solution passes all " + testCount + " tests and the template correctly fails them.";
        }
        return "Verification failed:\n- " + String.join("\n- ", reasons);
    }
}
