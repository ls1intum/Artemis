package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;

/**
 * The instructions and instructor-facing notes the specification stage produces from a concept selection or a review verdict.
 * <p>
 * These are pure functions of their arguments, kept apart from {@link StagedGenerationRunner} so that a wording change can be read and reviewed without the surrounding stage
 * control flow.
 */
final class SpecificationReviewPrompts {

    private SpecificationReviewPrompts() {
    }

    /**
     * Renders one instructor-facing note per objection the concept review raised, led by a note naming the candidate the run actually proceeded with.
     * <p>
     * Findings are carried verbatim, never summarized, so that the broad selection review ({@code "Candidate N: ..."}) and the focused admission audit
     * ({@code "Selected concept failed focused admission: ..."}) stay distinguishable, and so a reviewer can still see the objections raised against candidates this run did not
     * build.
     */
    static List<String> conceptAdmissionNotes(ExerciseConceptSelector.ConceptFallback fallback) {
        List<String> notes = new ArrayList<>();
        notes.add("The concept review admitted no candidate. This exercise was built from candidate " + fallback.candidate()
                + ", which the review rejected least, so the design below is a draft the review objected to rather than one it approved.");
        fallback.findings().stream().filter(finding -> !finding.isBlank()).map(String::strip).forEach(notes::add);
        return List.copyOf(notes);
    }

    /**
     * Renders the instructor-facing notes for a run whose specification review asked for a different concept, and whose replacement discovery then produced no candidate to
     * switch to. The run keeps the concept and specification it already had, so the objection is the instructor's decision to make and travels verbatim.
     */
    static List<String> conceptReselectionUnavailableNotes(SpecFidelityCritic.SpecificationReview review) {
        List<String> notes = new ArrayList<>();
        notes.add("The specification review asked for a different exercise concept, and replacement concept discovery produced no candidate to switch to. This exercise was "
                + "built from the concept the review objected to, so judging whether the objection matters is left to you.");
        review.findings().stream().filter(finding -> !finding.isBlank()).map(String::strip).forEach(notes::add);
        return List.copyOf(notes);
    }

    static List<String> unresolvedInconclusiveReviewFindings(SpecFidelityCritic.SpecificationReview review) {
        if (review.riskHistory().isEmpty()) {
            return List.of("The automated specification quality review was inconclusive, so the mechanically checked contract requires instructor review.");
        }
        return review.riskHistory().stream().map(finding -> "Unresolved specification-review hypothesis from grounded evidence: " + finding).toList();
    }

    static SpecFidelityCritic.SpecificationReview executedAssessmentReview(String evidence) {
        String bounded = evidence.length() <= 6_000 ? evidence : evidence.substring(0, 6_000) + " [additional diagnostic text omitted]";
        String finding = "Executed assessment counterexample against the preceding candidate: " + bounded;
        // The critic's bounded F history requires adjudication against the replacement SPEC, not a claim that the old candidate was sound.
        return new SpecFidelityCritic.SpecificationReview(false, false, true, List.of(finding), "Assessment redesign requires explicit counterexample adjudication.", null,
                List.of(finding));
    }

    static String contractConsistencyRepairPrompt(String evidence) {
        return "An execution-informed review found a defect in the approved learning contract. Preserve the instructor brief and its original learner goals. "
                + "Revise the conflicting rules, worked examples, learner ownership and assessment together; do not add per-example exceptions to an implementation, "
                + "copy an erroneous expected value into code, or remove a learner goal to make grading pass. Reconcile the quoted evidence, then submit the complete SPEC.\n"
                + evidence;
    }

    static String ownershipRepairPrompt(String executedEvidence) {
        return semanticSpecRefinementPrompt(executedEvidence) + """

                This repair resolves a supplied-code ownership conflict, not an assessment bypass. Make the starter compile while preserving the brief's learner responsibilities.
                Reconcile supplied demonstrations, ownership rows and their public API without adding learner work merely to satisfy supplied callers. Preserve unaffected rules,
                edge-case results, ordering choices and worked-example inputs. Do not redesign an unrelated operation or replace its accepted semantics with a different policy.
                Recalculate examples against their actual setup; an example that contradicts an unaffected rule must be corrected to that rule. Rebuild the affected artifacts
                against the revised ownership contract and keep every mandatory verification gate.
                """;
    }

    static String assessmentRedesignPrompt(String executedEvidence) {
        return executedEvidence
                + """

                        The server has reopened SPEC.md because executable evidence disproved its assessment or supplied-code ownership. Revise the coherent contract before rebuilding its artifacts.
                        When supplied code needs a learner-created type absent from the starter, reconcile the whole design instead of changing one ownership row. Preserve supplied
                        support as supplied work; do not add learner TODOs merely to make it compile. Reconcile all ownership claims, examples, entry points and grading seams.
                        Preserve the instructor brief, learner responsibilities and prerequisites. Choose runtime observations of the learner-owned behavior that the supported harness can
                        execute; tests of supplied collaborators alone cannot establish student integration. A supplied observation facility may expose otherwise-local operations without
                        adding learner work. Do not require source or bytecode inspection, introduce untaught syntax, remove a learning objective, or replace it with output transcription.
                        Observe the same learner execution that each graded entry point performs. A separate helper with an independently implemented scenario cannot prove what
                        another entry point does. Declare one execution path and supported intermediate observations that distinguish omitted actions even when final states agree.
                        An assessment failure does not show that the learner's task is impossible. Do not move learner-chosen cases or assertions into supplied code, rename away the
                        counterexample's learner entry point, or make the probe inapplicable. For learner-written JUnit tests, reference/style/student-tests.md demonstrates supported grading.
                        Remove incidental normative constraints that the brief does not require. Reconcile Rules, Public API, Design, Testing Strategy and risk partitions together.
                        The revised specification receives the normal consistency and semantic review; the runtime counterexample is checked again before statement authoring.
                        """;
    }

    static String semanticSpecRefinementPrompt(String reviewFeedback) {
        return reviewFeedback + """


                Treat the cited findings as review hypotheses, not instructions to patch isolated sentences. Re-read the whole current SPEC.md and confirm each finding
                against it. Preserve the selected concept's central situation, constraint, student-owned behavior, unaffected identifiers, ownership, and accepted semantic
                choices. Plan the smallest coherent contract revision, then replace SPEC.md with one complete `write_file` call rather than accumulating local edits. Reconcile
                every affected rule, example, policy algorithm, public API, ownership row, and testing seam together. Reviewer feedback deliberately supplies no
                replacement theme, identifier, API, or formula. Keep every required section, exact Design status token, bare Testing Strategy Owner type, seam ID, and hidden yes/no
                decision valid. Resolve the grounded batch; if a finding is contradicted by the complete contract, preserve the correct contract and let the next fresh review
                adjudicate it. Replay every changed example through its named policy, then call the structured verify tool before finishing.
                """;
    }

    static String specificationReviewAudit(SpecFidelityCritic.SpecificationReview review) {
        if (!review.complete()) {
            return "The specification review did not produce a complete grounded verdict."
                    + (review.auditSummary().isBlank() ? "" : "\n\nValidation detail: " + review.auditSummary());
        }
        if (review.accepted()) {
            return "The specification review accepted the candidate with no blocking findings." + (review.auditSummary().isBlank() ? "" : "\n\n" + review.auditSummary());
        }
        return (review.auditSummary().isBlank() ? "" : review.auditSummary() + "\n\n") + review.feedback();
    }

    static String freshSemanticSpecPrompt(String sourceBrief, @Nullable String selectedConcept) {
        return """
                Create a fresh specification from the instructor brief and newly reviewed concept below. A previous plan was discarded; it is deliberately absent from this fresh
                context. Instantiate the selected generator-authored concept coherently in one complete SPEC.md without adding unrelated complexity, then call the structured
                verify tool.

                INSTRUCTOR BRIEF:
                """ + sourceBrief.strip() + "\n\nNEWLY SELECTED GENERATOR-AUTHORED CONCEPT:\n"
                + (selectedConcept == null ? "No reviewed replacement concept is available; preserve the instructor brief exactly." : selectedConcept.strip());
    }

    static String specPromptWithSelectedConcept(String briefPrompt, @Nullable String selectedConcept) {
        if (selectedConcept == null || selectedConcept.isBlank()) {
            return briefPrompt;
        }
        return briefPrompt + """


                SELECTED GENERATOR-AUTHORED CONCEPT (planning input, not yet an approved contract):
                ---
                """ + selectedConcept.strip() + """

                ---
                Instantiate this concept coherently in SPEC.md. The instructor brief remains authoritative, and the normal specification reviewer must still approve the result.
                """;
    }

    static String semanticSpecCorrectionPrompt(String reviewFeedback, String mechanicalFeedback) {
        return """
                The semantic revision is incomplete and does not yet pass SPEC.md's mechanical consistency check. Continue the SAME bounded revision; do not merely patch the
                parser-visible symptom or open unrelated design choices. Re-read the whole current file, finish every original semantic repair coherently, and use one full
                write_file rewrite if incremental edits have left mixed vocabulary or identifiers. Preserve unaffected domain, API, ownership, examples, and grading intent.
                Keep every required section, exact Design status token, bare Testing Strategy Owner type, seam ID, and hidden yes/no decision valid. Call the structured verify
                tool and finish only after it passes.

                ORIGINAL SEMANTIC REVIEW:
                """ + reviewFeedback + "\n\nMECHANICAL DEFECT IN THE CURRENT REVISION:\n" + mechanicalFeedback;
    }
}
