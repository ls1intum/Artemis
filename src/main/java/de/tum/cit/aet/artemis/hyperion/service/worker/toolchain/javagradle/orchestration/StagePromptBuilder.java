package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ApprovedOwnershipGate;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;

/** Projects only the current brief, contract, artifact handoff and gate evidence into a stage conversation. */
final class StagePromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(StagePromptBuilder.class);

    private StagePromptBuilder() {
    }

    /**
     * Builds a stage's user prompt. When no conversation is carried this re-injects the current SPEC.md and workspace layout; a carried conversation already has that context.
     * {@code retryFeedback} is the previous failed attempt's gate report, folded into a fresh retry prompt.
     */
    static String build(GenerationStage stage, String briefPrompt, InteractiveSandbox sandbox, String sessionId, boolean carriesConversation, Set<String> seededStructuralTestNames,
            @Nullable String retryFeedback, ApprovedSpecRegistry approvedSpecs) {
        StringBuilder prompt = new StringBuilder(briefPrompt);
        if (carriesConversation) {
            prompt.append("\n\nContinue this session for the ").append(stage.displayName())
                    .append(" stage: follow that stage's instructions in the system prompt above. The specification and workspace state from earlier stages are already in this "
                            + "conversation; re-read a file only if you need to confirm its exact current contents.");
        }
        else {
            String specDocument = Objects.requireNonNullElse(GenerationWorkspace.readRootArtifact(sandbox, sessionId, "SPEC.md"), "");
            if (!specDocument.isBlank()) {
                String label = stage == GenerationStage.STATEMENT ? "APPROVED EXERCISE CONTRACT — INTERNAL SOURCE; REWRITE IT SELF-CONTAINED AND NEVER NAME THIS SOURCE"
                        : "CURRENT SPEC.md";
                prompt.append("\n\n=== ").append(label).append(" ===\n").append(specDocument.strip()).append("\n=== END ").append(label).append(" ===");
            }
            if (stage != GenerationStage.STATEMENT) {
                String layout = execRead(sandbox, sessionId, "sh", "-c", "find " + GenerationWorkspace.WORKSPACE + " -maxdepth 3 -type f -not -path '*/build/*' | head -80");
                if (!layout.isBlank()) {
                    prompt.append("\n\n=== CURRENT WORKSPACE LAYOUT ===\n").append(layout.strip()).append("\n=== END WORKSPACE LAYOUT ===");
                }
            }
        }
        if (stage == GenerationStage.STATEMENT) {
            String handoff = StatementHandoffProjection.project(Objects.requireNonNullElse(GenerationWorkspace.readRootArtifact(sandbox, sessionId, "test-plan.json"), ""),
                    seededStructuralTestNames);
            if (!handoff.isBlank()) {
                prompt.append("\n\n").append(handoff);
            }
            prompt.append("\n\n").append(StatementHandoffProjection.diagramDirective(ApprovedOwnershipGate.diagramRequired(approvedSpecs.approved(sessionId).orElse(null))));
        }
        if (retryFeedback != null && !retryFeedback.isBlank()) {
            prompt.append("\n\n=== GATE FEEDBACK FROM THE PREVIOUS ATTEMPT AT THIS STAGE ===\n").append(retryFeedback).append("\n=== END GATE FEEDBACK ===");
        }
        return prompt.toString();
    }

    private static String execRead(InteractiveSandbox sandbox, String sessionId, String... command) {
        try {
            var result = sandbox.exec(sessionId, GenerationWorkspace.SANDBOX_READ_TIMEOUT, command);
            return result.isSuccess() && result.stdout() != null ? result.stdout() : "";
        }
        catch (RuntimeException e) {
            log.debug("Staged generation read failed ({}): {}", String.join(" ", command), e.getMessage());
            return "";
        }
    }

}
