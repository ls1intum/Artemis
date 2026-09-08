package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/**
 * Writes the instruction the agent is given for its next attempt, from the verdict that ended the previous one.
 * <p>
 * Each verdict gets its own wording because each demands a different repair posture: a rejected candidate is told what broke, a reviewed one is told which findings to
 * resolve without disturbing verified work, and an accepted one is merely offered extra tests it may decline. Keeping these apart from {@link GenerationAttemptLoop} lets a
 * wording change be read against the verdict it answers rather than against the loop that dispatches it.
 */
final class RepairPromptComposer {

    private final int maxGenerationAttempts;

    private final GenerationMode mode;

    private final String authoringBrief;

    private final SpecFidelityCriticService specFidelityCritic;

    RepairPromptComposer(int maxGenerationAttempts, GenerationMode mode, String authoringBrief, SpecFidelityCriticService specFidelityCritic) {
        this.maxGenerationAttempts = maxGenerationAttempts;
        this.mode = mode;
        this.authoringBrief = authoringBrief;
        this.specFidelityCritic = specFidelityCritic;
    }

    /** Frames the prompt built after {@code completedAttempt}, which drives the attempt after it. */
    String attemptFraming(int completedAttempt) {
        int upcomingAttempt = completedAttempt + 1;
        boolean finalAttempt = upcomingAttempt >= maxGenerationAttempts;
        return "Repair attempt " + upcomingAttempt + " of " + maxGenerationAttempts
                + (finalAttempt ? " — this is the FINAL attempt; prioritise the blocking findings (especially any repeated from earlier reviews) over cosmetic ones. " : ". ");
    }

    /**
     * The prompt for the one witness-adoption round. Framed as an offer rather than a defect list, because the candidate already passed every gate, and declining is allowed so
     * the agent is not pushed into restating a case its suite already makes.
     */
    String witnessAdoption(int completedAttempt, @Nullable String specSnapshot, SemanticRepairBatch batch) {
        return attemptFraming(completedAttempt)
                + "Your previous attempt is fully verified and accepted; nothing is broken. An independent reviewer derived the tests below from the "
                + "approved specification and the server has already run each one against your reference solution, which passes them. Add each test to the graded suite unless an "
                + "existing assertion already distinguishes exactly the same wrong implementation, in which case leave the suite as it is and say which test covers it. Change "
                + "nothing else: the solution, template, statement and every existing test stay as they are. When you add a test, add its exact method name to test-plan.json with "
                + "the same approved seam, weight, and visibility as the witness it strengthens. Then call the structured `verify` tool, and call submit when it "
                + "reports MECHANICAL PRECHECK: PASS.\n\nThe instructor source requirements are:\n" + authoringBrief + GenerationReviewSupport.specContractSection(specSnapshot)
                + specFidelityCritic.renderForRetryPrompt(batch.report());
    }

    String semanticRepair(int completedAttempt, @Nullable String specSnapshot, SemanticRepairBatch batch) {
        String scopeGuidance = mode == GenerationMode.ADAPT ? " Preserve all content outside the requested adaptation." : "";
        return attemptFraming(completedAttempt) + "Your previous attempt passed mechanical verification, but the automated full-artifact review found review blockers."
                + scopeGuidance
                + " Preserve the mechanically correct work: do not restart or rewrite unrelated files. Begin with only the artifact(s) explicitly implicated by each finding's evidence. "
                + batch.guidance()
                + "After that smallest edit, call the structured `verify` tool; expand the repair surface only if its report identifies a concrete cross-artifact inconsistency caused by the edit. "
                + "Keep every unaffected requirement, API, test, and example. The template is expected to fail behavioural and structural tests at approved TODOs and absent "
                + "student-creates types—never make those tests pass merely because a raw template build exits non-zero. `verify`, not a raw build exit code, is the acceptance verdict. "
                + "If you add, rename, or remove a behavioral test, update test-plan.json in the same edit so it maps every exact test method name. "
                + "Call submit when it reports MECHANICAL PRECHECK: PASS.\n\nThe instructor " + "source requirements are:\n" + authoringBrief
                + GenerationReviewSupport.specContractSection(specSnapshot) + specFidelityCritic.renderForRetryPrompt(batch.report());
    }

    /**
     * @param precedingRepair the quality repair this rejection followed, or {@code null} if no repair round had started; a new assertion from that repair is the likeliest cause
     *                            of the rejection, so it is audited before production code is touched
     */
    String mechanicalRejection(int completedAttempt, @Nullable String specSnapshot, String verificationReport, @Nullable SemanticRepairBatch precedingRepair,
            SpecFidelityReport report) {
        String semanticCorrectionGuidance = precedingRepair == null ? ""
                : "\n\nThis rejection followed a " + precedingRepair.surface().name().toLowerCase(Locale.ROOT)
                        + " quality repair. Before changing production code, audit the new assertion against the frozen contract. If the assertion invents behavior the contract "
                        + "does not require, fix or remove the unsupported assertion first. " + precedingRepair.guidance();
        return attemptFraming(completedAttempt) + "Your previous attempt was rejected by the differential verifier:\n" + verificationReport
                + "\n\nThe workspace still contains all your files. Read the relevant files, fix exactly these issues, call the structured `verify` tool, then submit when it reports "
                + "MECHANICAL PRECHECK: PASS. If a reason names a forbidden, duplicate, or abandoned path, delete it; replacing it with a "
                + "placeholder does not remove the violation. Make the smallest coherent repair, leave unrelated files unchanged, and preserve the source requirements below.\n\n"
                + authoringBrief + GenerationReviewSupport.specContractSection(specSnapshot) + semanticCorrectionGuidance + specFidelityCritic.renderForRetryPrompt(report);
    }
}
