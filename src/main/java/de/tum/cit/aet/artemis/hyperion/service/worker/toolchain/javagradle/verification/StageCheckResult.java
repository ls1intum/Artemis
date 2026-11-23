package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import org.jspecify.annotations.Nullable;

/**
 * One staged-generation gate's outcome, returned by {@link StageChecks#check}.
 *
 * @param outcome            whether the artifact passed, needs an artifact repair, or needs coherent contract review from executable evidence
 * @param observation        gate evidence and actionable feedback
 * @param specificationCheck the first failing mandatory SPEC check, when available
 * @param report             the TESTS differential report, whose exact names also form the statement handoff; null for other stages
 */
public record StageCheckResult(Outcome outcome, String observation, @Nullable AgentVerifyReport report, @Nullable SpecificationCheck specificationCheck) {

    public StageCheckResult(Outcome outcome, String observation, @Nullable AgentVerifyReport report) {
        this(outcome, observation, report, null);
    }

    /** Ordered mandatory SPEC checks. A later failing check proves that the earlier checks passed on this candidate. */
    public enum SpecificationCheck {
        CONTENT, SECTIONS, STATEMENT_SEPARATION, EXAMPLES, DESIGN, OWNERSHIP_STATUS, UNIQUE_TYPES, TYPE_NAMES, PUBLIC_API, OWNED_MEMBERS, TYPE_DEPENDENCIES, WORK_SEAMS,
        OWNER_COLUMN, RESPONSIBILITY_COLUMN, WEIGHT_COLUMN, SEAM_IDS, UNIQUE_SEAMS, OWNER_NAMES, RESPONSIBILITIES, WEIGHTS, KNOWN_OWNERS, LEARNER_OWNERS, HIDDEN_VARIANTS,
        HIDDEN_DEADLINE, UNIQUE_RISK_SEAMS, RISK_COVERAGE, RISK_PARTITIONS, UNIQUE_RISK_PARTITIONS
    }

    public enum Outcome {
        PASSED, ARTIFACT_FAILURE, ASSESSMENT_FAILURE, OWNERSHIP_CONFLICT, SPECIFICATION_CONFLICT
    }

    public StageCheckResult(boolean passed, String observation, @Nullable AgentVerifyReport report) {
        this(passed ? Outcome.PASSED : Outcome.ARTIFACT_FAILURE, observation, report);
    }

    public boolean passed() {
        return outcome == Outcome.PASSED;
    }

    public boolean contractRedesignRequired() {
        return outcome == Outcome.ASSESSMENT_FAILURE || outcome == Outcome.OWNERSHIP_CONFLICT || outcome == Outcome.SPECIFICATION_CONFLICT;
    }

    public static StageCheckResult redesignRequired(String observation, AgentVerifyReport report) {
        return new StageCheckResult(Outcome.ASSESSMENT_FAILURE, observation, report);
    }

    public static StageCheckResult ownershipRedesignRequired(String observation, @Nullable AgentVerifyReport report) {
        return new StageCheckResult(Outcome.OWNERSHIP_CONFLICT, observation, report);
    }

    public static StageCheckResult specificationRedesignRequired(String observation, AgentVerifyReport report) {
        return new StageCheckResult(Outcome.SPECIFICATION_CONFLICT, observation, report);
    }

    public static StageCheckResult passed(String observation) {
        return new StageCheckResult(Outcome.PASSED, observation, null);
    }

    public static StageCheckResult failedSpecification(SpecificationCheck check, String observation) {
        return new StageCheckResult(Outcome.ARTIFACT_FAILURE, observation, null, check);
    }

    public static StageCheckResult failed(String observation) {
        return new StageCheckResult(Outcome.ARTIFACT_FAILURE, observation, null);
    }
}
