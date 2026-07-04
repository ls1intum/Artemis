package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: builds the type-specific toolset the agent loop uses during TRANSFORMING/REPAIRING
 * (plan Sections 2.3 and 2.5). Tools are Spring AI {@link ToolCallback}s registered on the ChatClient call.
 */
public interface VariantToolsetFactory {

    /**
     * Creates the tool callbacks bound to the given (already provisioned) variant exercise.
     *
     * TODO (Opus, programming — plan Section 2.5 toolset table + Section 3 TRANSFORMING/VERIFYING rows):
     * - listFiles(repoType), readFile(repoType, path): over GitService.getOrCheckoutRepository /
     * RepositoryService.getFiles on the VARIANT's repos (never the source).
     * - applyEdit(repoType, path, search, replace) and writeFile(repoType, path, content): diff-style edits of
     * existing files — "transform, don't regenerate" is the main consistency lever (Section 7). Return
     * validation errors (no match / ambiguous match) TO THE MODEL as the tool result (Section 6 row 2).
     * - runBuild(repoType): commit + push (GitService.commitAndPush), trigger CI
     * (ContinuousIntegrationTriggerService.triggerBuild), wait via the extracted
     * HyperionBuildVerificationService (waitUntilRemoteHasCommit / waitForBuildResult, Section 3 refactor note).
     * - getBuildAndTestResults(repoType): compiler output + failed test names/messages from the latest build.
     * - finish(summary): signals the agent is done with this round; summary becomes part of the step output.
     * Enforce the build-dependency constraint: a test-repo edit invalidates ALL previously green results — the
     * factory/tools must flag that so the verifier re-runs both builds (Section 3, "Build-dependency constraint").
     *
     * TODO (Sonnet, quiz — plan Section 2.5 toolset table + Section 4 TRANSFORMING row):
     * - getQuestions(): serialized current questions of the variant.
     * - updateQuestion(index, questionJson): JSON per question, schema-validated against GeneratedQuizQuestionDTO;
     * schema violations go back to the model as the tool result (Section 6 row 2). DnD: only text/mapping edits,
     * images carried over (Section 1 non-goals).
     * - validateQuiz(): wraps QuizExercise.isValid() + per-question error details (cheap, synchronous).
     * - finish(summary).
     *
     * @param variant the provisioned variant exercise the tools operate on
     * @param job     the running job — tools check job.cancelRequested between calls and record telemetry
     * @return the tool callbacks for one agent round
     */
    List<ToolCallback> createTools(Exercise variant, VariantJob job);
}
