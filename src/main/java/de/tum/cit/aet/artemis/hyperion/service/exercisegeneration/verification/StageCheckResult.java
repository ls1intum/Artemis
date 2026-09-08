package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import org.jspecify.annotations.Nullable;

/**
 * One staged-generation gate's outcome, returned by {@link StageCheckService#check}.
 *
 * @param passed      whether the stage's artifact passed its mechanical gate
 * @param observation on failure, the reasons the gate rejected the artifact; on a pass, an optional informational note, empty when there is nothing to add
 * @param report      the TESTS stage's full {@link AgentVerifyReport}, so its exact test names can be threaded into the STATEMENT stage's task-binding resolution; {@code null}
 *                        for every other stage
 */
public record StageCheckResult(boolean passed, String observation, @Nullable AgentVerifyReport report) {

    public static StageCheckResult passed(String observation) {
        return new StageCheckResult(true, observation, null);
    }

    public static StageCheckResult failed(String observation) {
        return new StageCheckResult(false, observation, null);
    }
}
