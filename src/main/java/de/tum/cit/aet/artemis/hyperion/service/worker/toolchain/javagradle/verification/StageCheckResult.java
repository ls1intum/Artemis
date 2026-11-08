package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import org.jspecify.annotations.Nullable;

/**
 * One staged-generation gate's outcome, returned by {@link StageChecks#check}.
 *
 * @param outcome     whether the artifact passed, needs an artifact repair, or needs coherent contract review from executable evidence
 * @param observation gate evidence and actionable feedback
 * @param report      the TESTS differential report, whose exact names also form the statement handoff; null for other stages
 */
public record StageCheckResult(Outcome outcome, String observation, @Nullable AgentVerifyReport report) {

    public enum Outcome {
        PASSED, ARTIFACT_FAILURE, ASSESSMENT_FAILURE, OWNERSHIP_CONFLICT
    }

    public StageCheckResult(boolean passed, String observation, @Nullable AgentVerifyReport report) {
        this(passed ? Outcome.PASSED : Outcome.ARTIFACT_FAILURE, observation, report);
    }

    public boolean passed() {
        return outcome == Outcome.PASSED;
    }

    public boolean contractRedesignRequired() {
        return outcome == Outcome.ASSESSMENT_FAILURE || outcome == Outcome.OWNERSHIP_CONFLICT;
    }

    public static StageCheckResult redesignRequired(String observation, AgentVerifyReport report) {
        return new StageCheckResult(Outcome.ASSESSMENT_FAILURE, observation, report);
    }

    public static StageCheckResult ownershipRedesignRequired(String observation, @Nullable AgentVerifyReport report) {
        return new StageCheckResult(Outcome.OWNERSHIP_CONFLICT, observation, report);
    }

    public static StageCheckResult passed(String observation) {
        return new StageCheckResult(Outcome.PASSED, observation, null);
    }

    public static StageCheckResult failed(String observation) {
        return new StageCheckResult(Outcome.ARTIFACT_FAILURE, observation, null);
    }
}
