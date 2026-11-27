package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Differential verification and integrity-gate result for a frozen candidate. Diagnostics remain untrusted text. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VerificationResult(boolean mechanicallyVerified, boolean solutionPassed, boolean templateFailed, int testCount, @JsonInclude List<String> reasons,
        @JsonInclude List<TestFailureEvidence> templateFailureEvidence) {

    /** Opens every reason that reports a server-side defect: one no authoring repair can fix, so the run stops instead of asking for another attempt. */
    public static final String SERVER_DEFECT_PREFIX = "Server defect, not an authoring error:";

    public VerificationResult {
        reasons = List.copyOf(reasons);
        templateFailureEvidence = List.copyOf(templateFailureEvidence);
        if (testCount < 0 || (mechanicallyVerified && (!solutionPassed || !templateFailed || testCount == 0 || !reasons.isEmpty()))) {
            throw new IllegalArgumentException("A verified result must satisfy every differential gate");
        }
    }

    public VerificationResult(boolean mechanicallyVerified, boolean solutionPassed, boolean templateFailed, int testCount, List<String> reasons) {
        this(mechanicallyVerified, solutionPassed, templateFailed, testCount, reasons, List.of());
    }

    /** @return whether verification failed on a server-side defect that no authoring repair can fix */
    @JsonIgnore
    public boolean serverDefect() {
        return !mechanicallyVerified && reasons.stream().anyMatch(reason -> reason.startsWith(SERVER_DEFECT_PREFIX));
    }

    /** @return the report used in instructor progress and agent repair feedback */
    public String report() {
        return mechanicallyVerified ? "Verification passed: the solution passes all " + testCount + " tests and the template correctly fails them."
                : "Verification failed:\n- " + String.join("\n- ", reasons);
    }

    /** A template failure location and diagnostic, not executable instructions. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestFailureEvidence(@JsonInclude String testName, @JsonInclude String message) {
    }
}
