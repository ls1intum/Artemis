package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;

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

    static List<String> unresolvedInconclusiveReviewFindings(SpecFidelityCriticService.SpecificationReview review) {
        if (review.riskHistory().isEmpty()) {
            return List.of("The automated specification quality review was inconclusive, so the mechanically checked contract requires instructor review.");
        }
        return review.riskHistory().stream().map(finding -> "Unresolved specification-review hypothesis from grounded evidence: " + finding).toList();
    }

    static String semanticSpecRefinementPrompt(String reviewFeedback) {
        return reviewFeedback + """


                Treat the cited findings as review hypotheses, not instructions to patch isolated sentences. Re-read the whole current SPEC.md and confirm each finding
                against it. Preserve the selected concept's central situation, constraint, student-owned behavior, unaffected identifiers, ownership, and accepted semantic
                choices. Plan the smallest coherent contract revision, then replace SPEC.md with one complete `write_file` call rather than accumulating local edits. Reconcile
                every affected rule, example, policy algorithm, public API, ownership row, testing seam, and diagram decision together. Reviewer feedback deliberately supplies no
                replacement theme, identifier, API, or formula. Keep every required section, exact Design status token, bare Testing Strategy Owner type, seam ID, and hidden yes/no
                decision valid. Resolve the grounded batch; if a finding is contradicted by the complete contract, preserve the correct contract and let the next fresh review
                adjudicate it. Replay every changed example through its named policy, then call the structured verify tool before finishing.
                """;
    }

    static String specificationReviewAudit(SpecFidelityCriticService.SpecificationReview review) {
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
