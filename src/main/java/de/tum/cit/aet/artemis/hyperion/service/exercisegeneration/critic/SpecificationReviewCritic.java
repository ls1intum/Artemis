package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.requireReviewTextSafe;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.truncate;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.truncateLearningEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService.SpecificationReview;

/**
 * The pre-freeze specification review pass: one bounded, tool-free review of the mechanically valid candidate SPEC.md against the instructor brief and, when one exists, the
 * selected concept.
 * <p>
 * This is the last checkpoint before the specification becomes binding authority for the solution, template, tests, and problem statement, so a planning defect that survives it is
 * faithfully implemented by every later artifact. The pass reports only high-confidence planning defects and one mandatory learning-fit judgment; it diagnoses properties and never
 * supplies replacement design content.
 */
class SpecificationReviewCritic {

    private static final Logger log = LoggerFactory.getLogger(SpecificationReviewCritic.class);

    private static final String SPECIFICATION_REVIEW_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/specification_review_system.st";

    /**
     * Sized for the declared response shape: six finding arrays (omissions, conflicts, internal conflicts, example checks, ambiguities, unsupported constraints), each entry
     * carrying evidence IDs and one short reason, plus the learning-fit object (five ID lists, three prose fields, five booleans, one direction) and the concept-alignment
     * object. Findings are capped at {@link #SPECIFICATION_REVIEW_MAX_FINDINGS} anyway, so a full critic-sized budget would buy length rather than evidence — but the cap covers
     * hidden reasoning too, and a response cut off by it is unparseable and costs the correction pass below.
     */
    private static final int SPECIFICATION_REVIEW_MAX_OUTPUT_TOKENS = 8_192;

    /** Keeps a SPEC repair focused even when the reviewer reports more valid defects than requested. */
    private static final int SPECIFICATION_REVIEW_MAX_FINDINGS = 4;

    private static final String SPECIFICATION_REVIEW_CORRECTION = """

            Your previous response was malformed, incomplete, or cited an unknown or wrong-source evidence ID. Re-review the same evidence from scratch and return one complete
            JSON verdict. Cite only the server-generated B, C, and E IDs exactly as shown. Do not copy source text, invent IDs, or refer to the previous response.
            """;

    private record SpecificationReviewResponse(@Nullable List<SpecificationReviewItem> omissions, @Nullable List<SpecificationReviewItem> conflicts,
            @Nullable List<SpecificationInternalConflictItem> internalConflicts, @Nullable List<SpecificationExampleCheckItem> exampleChecks,
            @Nullable List<SpecificationReviewItem> ambiguities, @Nullable List<SpecificationReviewItem> unsupportedConstraints, @Nullable SpecificationLearningFitItem learningFit,
            @Nullable SpecificationConceptAlignmentItem conceptAlignment) {
    }

    private record SpecificationLearningFitItem(@Nullable List<String> briefEvidenceIds, @Nullable List<String> specEvidenceIds, @Nullable List<String> objectiveEvidenceIds,
            @Nullable List<String> studentOwnershipEvidenceIds, @Nullable List<String> assessmentEvidenceIds, @Nullable String objectiveMechanism,
            @Nullable String remainingStudentReasoning, @Nullable String domainGrounding, @Nullable Boolean learnerOwnsObjectiveMechanism, @Nullable Boolean objectiveObservable,
            @Nullable Boolean difficultySufficient, @Nullable Boolean domainGrounded, @Nullable Boolean sufficient, @Nullable SpecificationLearningFitDirection direction) {
    }

    private enum SpecificationLearningFitDirection {
        SUFFICIENT, TOO_SHALLOW, TOO_COMPLEX, MISALIGNED
    }

    private enum SpecificationConceptDisposition {
        ALIGNED, SPEC_REPAIR, CONCEPT_RESELECTION
    }

    private record SpecificationConceptAlignmentItem(@Nullable List<String> briefEvidenceIds, @Nullable List<String> conceptEvidenceIds, @Nullable List<String> specEvidenceIds,
            @Nullable SpecificationConceptDisposition disposition, @Nullable String reason) {
    }

    private record SpecificationExampleCheckItem(@Nullable String exampleEvidenceId, @Nullable String replayedOutcome, @Nullable Boolean consistent, @Nullable String reason) {
    }

    private record SpecificationReviewEvidence(EvidenceSource brief, EvidenceSource concept, EvidenceSource specification) {

        private static SpecificationReviewEvidence from(String brief, @Nullable String concept, String specification) {
            return new SpecificationReviewEvidence(EvidenceSource.from("B", brief), EvidenceSource.from("C", concept), EvidenceSource.from("E", specification));
        }

        private boolean hasConcept() {
            return !concept.passages().isEmpty();
        }

    }

    private record SpecificationReviewItem(@Nullable List<String> briefEvidenceIds, @Nullable List<String> specEvidenceIds, @Nullable String reason) {
    }

    private record SpecificationInternalConflictItem(@Nullable List<String> firstSpecEvidenceIds, @Nullable List<String> secondSpecEvidenceIds, @Nullable String reason) {
    }

    private final ReviewerClient reviewer;

    private final ObjectMapper objectMapper;

    SpecificationReviewCritic(ReviewerClient reviewer, ObjectMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    /**
     * Reviews the cheapest irreversible boundary: the mechanically valid candidate SPEC before it becomes authority for solution, template, tests, and statement.
     *
     * @param brief         raw instructor brief, the scope authority
     * @param specification mechanically valid candidate specification
     * @param usageSink     optional token-usage sink
     * @param cancelled     cooperative cancellation signal
     * @return complete grounded findings, or an incomplete verdict when no trustworthy review was available
     */
    SpecificationReview reviewSpecification(String brief, String specification, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return reviewSpecification(brief, null, specification, usageSink, cancelled);
    }

    /**
     * Reviews a mechanically valid candidate specification against the brief and its selected concept before the specification becomes authoritative.
     *
     * @param brief           the instructor brief
     * @param selectedConcept the reviewed concept that led to the specification, or {@code null}
     * @param specification   the candidate SPEC.md
     * @param usageSink       optional token-usage sink
     * @param cancelled       cooperative cancellation signal
     * @return complete grounded findings, or an incomplete verdict when no trustworthy review was available
     */
    SpecificationReview reviewSpecification(String brief, @Nullable String selectedConcept, String specification, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        requireReviewTextSafe("spec-review/brief", brief);
        if (selectedConcept != null) {
            requireReviewTextSafe("spec-review/selected-concept", selectedConcept);
        }
        requireReviewTextSafe("spec-review/SPEC.md", specification);
        if (cancelled.getAsBoolean()) {
            return new SpecificationReview(false, List.of());
        }
        if (!reviewer.configured() || brief.isBlank() || specification.isBlank()) {
            return new SpecificationReview(false, List.of());
        }
        SpecificationReviewEvidence evidence = SpecificationReviewEvidence.from(brief, selectedConcept, specification);
        String conceptPrompt = evidence.hasConcept()
                ? "\n\nSELECTED GENERATOR-AUTHORED CONCEPT EVIDENCE (process provenance, not scope authority):\n" + evidence.concept().promptText()
                : "";
        String userPrompt = "INSTRUCTOR BRIEF EVIDENCE (sole authority):\n" + evidence.brief().promptText() + conceptPrompt + "\n\nCANDIDATE SPECIFICATION EVIDENCE:\n"
                + evidence.specification().promptText() + "\n\nReturn the complete JSON verdict specified by the system prompt.";
        try {
            String response = reviewer.call(SPECIFICATION_REVIEW_SYSTEM_PROMPT_TEMPLATE, userPrompt, usageSink, SPECIFICATION_REVIEW_MAX_OUTPUT_TOKENS);
            SpecificationReviewResponse parsed = readSpecificationReviewResponse(response);
            SpecificationReview review = parseSpecificationReview(parsed, evidence);
            if (review.complete()) {
                return review;
            }
            if (cancelled.getAsBoolean()) {
                return review;
            }
            String correctedResponse = reviewer.call(SPECIFICATION_REVIEW_SYSTEM_PROMPT_TEMPLATE,
                    userPrompt + SPECIFICATION_REVIEW_CORRECTION + "\n\nSERVER VALIDATION FAILURE TO CORRECT:\n" + review.auditSummary(), usageSink,
                    SPECIFICATION_REVIEW_MAX_OUTPUT_TOKENS);
            SpecificationReviewResponse correctedParsed = readSpecificationReviewResponse(correctedResponse);
            return parseSpecificationReview(correctedParsed, evidence);
        }
        catch (RuntimeException e) {
            log.warn("Specification review failed: {}", e.getMessage());
            return incompleteSpecificationReview("Reviewer call failed: " + safeFailureDetail(e));
        }
    }

    private @Nullable SpecificationReviewResponse readSpecificationReviewResponse(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(extractJsonPayload(text), SpecificationReviewResponse.class);
        }
        catch (Exception e) {
            log.debug("Specification review JSON did not parse ({}); failing closed.", e.getMessage());
            return null;
        }
    }

    private SpecificationReview parseSpecificationReview(@Nullable SpecificationReviewResponse parsed, SpecificationReviewEvidence evidence) {
        if (parsed == null) {
            return incompleteSpecificationReview("The response was empty or was not valid JSON in the required object shape.");
        }
        if (parsed.omissions() == null || parsed.conflicts() == null || parsed.internalConflicts() == null || parsed.ambiguities() == null
                || parsed.unsupportedConstraints() == null) {
            return incompleteSpecificationReview("One or more mandatory finding arrays were missing.");
        }
        String learningFitValidationError = specificationLearningFitValidationError(parsed.learningFit(), evidence);
        if (learningFitValidationError != null) {
            return incompleteSpecificationReview("learningFit validation failed: " + learningFitValidationError);
        }
        if (!validConceptAlignment(parsed.conceptAlignment(), evidence)) {
            return incompleteSpecificationReview("conceptAlignment was missing a disposition or reason for the supplied concept.");
        }
        // Worked-example replay is a quality signal, not a terminal contract: an inconsistent check still becomes a finding, but a mismatched or missing example-ID set must
        // not discard an otherwise coherent verdict.
        SpecificationConceptDisposition conceptDisposition = evidence.hasConcept() ? parsed.conceptAlignment().disposition() : SpecificationConceptDisposition.ALIGNED;
        List<String> findings = new ArrayList<>();
        SpecificationLearningFitItem learningFit = parsed.learningFit();
        if (!learningFit.sufficient()) {
            findings.add(learningFitFinding(learningFit, evidence));
        }
        if (conceptDisposition == SpecificationConceptDisposition.SPEC_REPAIR) {
            SpecificationConceptAlignmentItem alignment = parsed.conceptAlignment();
            findings.add("Concept continuity — selected concept says \"" + truncate(evidence.concept().resolve(alignment.conceptEvidenceIds())) + "\"; SPEC evidence says \""
                    + evidence.specification().resolve(alignment.specEvidenceIds()) + "\": " + truncateLearningEvidence(alignment.reason().strip())
                    + " Repair: rewrite the specification around the selected concept's central situation, constraint, and student-owned behavior; do not reopen theme selection.");
        }
        if (conceptDisposition == SpecificationConceptDisposition.CONCEPT_RESELECTION) {
            SpecificationConceptAlignmentItem alignment = parsed.conceptAlignment();
            findings.add("Concept viability — brief says \"" + truncate(evidence.brief().resolve(alignment.briefEvidenceIds())) + "\"; selected concept says \""
                    + truncate(evidence.concept().resolve(alignment.conceptEvidenceIds())) + "\": " + truncateLearningEvidence(alignment.reason().strip())
                    + " Repair: return to reviewed concept selection; do not try to rescue an unviable central interaction by adding unrelated types, validations, or edge cases.");
        }
        for (SpecificationExampleCheckItem item : parsed.exampleChecks() == null ? List.<SpecificationExampleCheckItem>of() : parsed.exampleChecks()) {
            if (item == null || !Boolean.FALSE.equals(item.consistent()) || item.replayedOutcome() == null || item.replayedOutcome().isBlank() || item.reason() == null
                    || item.reason().isBlank()) {
                continue;
            }
            String exampleQuote = item.exampleEvidenceId() == null ? "" : truncate(evidence.specification().resolve(List.of(item.exampleEvidenceId())));
            findings.add("Incorrect worked example — SPEC says \"" + exampleQuote + "\": replay gives \"" + truncateLearningEvidence(item.replayedOutcome().strip()) + "\" because "
                    + truncateLearningEvidence(item.reason().strip()) + " Repair: correct the erroneous outcome or rule and every dependent example.");
        }
        for (SpecificationReviewItem item : parsed.omissions()) {
            if (!validSpecificationReviewItem(item)) {
                continue;
            }
            findings.add("Omission — brief says \"" + truncate(evidence.brief().resolve(item.briefEvidenceIds())) + "\": " + truncate(item.reason().strip())
                    + " Repair: satisfy this cited brief property with the smallest coherent change; choose the content yourself and preserve unaffected choices.");
        }
        for (SpecificationReviewItem item : parsed.conflicts()) {
            if (!validSpecificationReviewItem(item)) {
                continue;
            }
            findings.add("Conflict — brief says \"" + truncate(evidence.brief().resolve(item.briefEvidenceIds())) + "\" but SPEC says \""
                    + truncate(evidence.specification().resolve(item.specEvidenceIds())) + "\": " + truncate(item.reason().strip())
                    + " Repair: reconcile the cited specification claim with the brief, updating all directly affected vocabulary and examples coherently; choose the replacement yourself.");
        }
        for (SpecificationInternalConflictItem item : parsed.internalConflicts()) {
            if (item == null || item.reason() == null || item.reason().isBlank()) {
                continue;
            }
            findings.add("Internal conflict — SPEC says both \"" + truncate(evidence.specification().resolve(item.firstSpecEvidenceIds())) + "\" and \""
                    + truncate(evidence.specification().resolve(item.secondSpecEvidenceIds())) + "\": " + truncate(item.reason().strip())
                    + " Repair: choose one coherent interpretation grounded in the brief and update every affected section consistently.");
        }
        for (SpecificationReviewItem item : parsed.ambiguities()) {
            if (!validSpecificationReviewItem(item)) {
                continue;
            }
            findings.add("Ambiguous contract — SPEC says \"" + truncate(evidence.specification().resolve(item.specEvidenceIds())) + "\": " + truncate(item.reason().strip())
                    + " Repair: define one coherent, finite, and testable behavior for the cited permitted input or transition, updating dependent examples and seams; choose the behavior yourself.");
        }
        for (SpecificationReviewItem item : parsed.unsupportedConstraints()) {
            if (!validSpecificationReviewItem(item)) {
                continue;
            }
            findings.add("Unsupported constraint — SPEC says \"" + truncate(evidence.specification().resolve(item.specEvidenceIds())) + "\": " + truncate(item.reason().strip())
                    + " Repair: remove or relax only the cited unsupported obligation while preserving requested behavior.");
        }
        boolean coherentRewriteRequired = !learningFit.sufficient() || conceptDisposition == SpecificationConceptDisposition.SPEC_REPAIR;
        return new SpecificationReview(true, conceptDisposition == SpecificationConceptDisposition.CONCEPT_RESELECTION, coherentRewriteRequired,
                findings.stream().limit(SPECIFICATION_REVIEW_MAX_FINDINGS).toList(), specificationReviewAuditSummary(learningFit, conceptDisposition, parsed.exampleChecks()),
                learningFit.direction().name());
    }

    private static SpecificationReview incompleteSpecificationReview(String detail) {
        return new SpecificationReview(false, false, false, List.of(), truncateLearningEvidence(detail));
    }

    private static String safeFailureDetail(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String specificationReviewAuditSummary(SpecificationLearningFitItem learningFit, SpecificationConceptDisposition conceptDisposition,
            @Nullable List<SpecificationExampleCheckItem> exampleChecks) {
        List<SpecificationExampleCheckItem> checkedExamples = exampleChecks == null ? List.of() : exampleChecks;
        long consistentExamples = checkedExamples.stream().filter(item -> Boolean.TRUE.equals(item.consistent())).count();
        return "Learning fit: " + learningFit.direction() + ". Learner owns objective mechanism: " + learningFit.learnerOwnsObjectiveMechanism()
                + ". Objective observable end to end: " + learningFit.objectiveObservable() + ". Objective mechanism: "
                + truncateLearningEvidence(learningFit.objectiveMechanism().strip()) + "\nRemaining student reasoning: "
                + truncateLearningEvidence(learningFit.remainingStudentReasoning().strip()) + "\nDomain grounding: "
                + truncateLearningEvidence(learningFit.domainGrounding().strip()) + "\nConcept disposition: " + conceptDisposition + "\nWorked examples replayed consistently: "
                + consistentExamples + "/" + checkedExamples.size();
    }

    private static String learningFitFinding(SpecificationLearningFitItem learningFit, SpecificationReviewEvidence evidence) {
        String diagnosis = "Learning fit — brief says \"" + truncate(evidence.brief().resolve(learningFit.briefEvidenceIds())) + "\"; SPEC evidence says \""
                + evidence.specification().resolve(learningFit.specEvidenceIds()) + "\"; objective evidence says \""
                + evidence.specification().resolve(learningFit.objectiveEvidenceIds()) + "\": Objective mechanism: "
                + truncateLearningEvidence(learningFit.objectiveMechanism().strip()) + " After routine work is removed: "
                + truncateLearningEvidence(learningFit.remainingStudentReasoning().strip()) + " Domain grounding: "
                + truncateLearningEvidence(learningFit.domainGrounding().strip()) + " Repair: ";
        return diagnosis + switch (learningFit.direction()) {
            case TOO_SHALLOW ->
                "restore or deepen the selected concept's central learner-owned decision and update all affected rules, examples, ownership, and testing seams together. Deepen the requested concept's interaction before adding any domain algorithm; incidental mathematics or collection work cannot rescue learning fit. Do not manufacture difficulty with extra types, validation, exceptions, or arbitrary edge cases.";
            case TOO_COMPLEX ->
                "preserve the selected concept's central learner-owned reasoning while simplifying only supporting representation or plumbing and factoring genuinely shared work once. Do not give the core behavior to supplied scaffolding or collapse the strategies to constants or scalar formulas.";
            case MISALIGNED ->
                "align the student-owned work with the requested objective throughout the specification. If a selected concept exists and that requires replacing its central interaction, conceptAlignment must request CONCEPT_RESELECTION instead of asking this SPEC repair to invent a new concept.";
            case SUFFICIENT -> throw new IllegalStateException("A sufficient learning-fit verdict cannot produce a finding.");
        };
    }

    private static @Nullable String specificationLearningFitValidationError(@Nullable SpecificationLearningFitItem item, SpecificationReviewEvidence evidence) {
        if (item == null) {
            return "the mandatory learningFit object is missing.";
        }
        if (item.objectiveMechanism() == null || item.objectiveMechanism().isBlank()) {
            return "objectiveMechanism is mandatory.";
        }
        if (item.remainingStudentReasoning() == null || item.remainingStudentReasoning().isBlank()) {
            return "remainingStudentReasoning is mandatory.";
        }
        if (item.domainGrounding() == null || item.domainGrounding().isBlank()) {
            return "domainGrounding is mandatory.";
        }
        if (item.learnerOwnsObjectiveMechanism() == null || item.objectiveObservable() == null || item.difficultySufficient() == null || item.domainGrounded() == null
                || item.sufficient() == null) {
            return "all five learning-fit booleans are mandatory.";
        }
        boolean derivedSufficient = item.learnerOwnsObjectiveMechanism() && item.objectiveObservable() && item.difficultySufficient() && item.domainGrounded();
        if (item.sufficient() != derivedSufficient) {
            return "sufficient must equal learnerOwnsObjectiveMechanism && objectiveObservable && difficultySufficient && domainGrounded.";
        }
        if (item.direction() == null) {
            return "direction is mandatory.";
        }
        if (derivedSufficient != (item.direction() == SpecificationLearningFitDirection.SUFFICIENT)) {
            return "direction must be SUFFICIENT exactly when sufficient is true.";
        }
        if (!evidence.brief().containsSubstantive(item.briefEvidenceIds())) {
            return "briefEvidenceIds must cite known, substantive B evidence from this review.";
        }
        if (!evidence.specification().containsSubstantive(item.specEvidenceIds())) {
            return "specEvidenceIds must cite known, substantive E evidence from this review.";
        }
        if (!evidence.specification().containsSubstantive(item.objectiveEvidenceIds())) {
            return "objectiveEvidenceIds must cite known, substantive E evidence from this review.";
        }
        if (!evidence.specification().containsSubstantive(item.studentOwnershipEvidenceIds())) {
            return "studentOwnershipEvidenceIds must cite known, substantive E evidence from this review.";
        }
        if (!evidence.specification().containsSubstantive(item.assessmentEvidenceIds())) {
            return "assessmentEvidenceIds must cite known, substantive E evidence from this review.";
        }
        return null;
    }

    private static boolean validConceptAlignment(@Nullable SpecificationConceptAlignmentItem item, SpecificationReviewEvidence evidence) {
        if (!evidence.hasConcept()) {
            return item == null;
        }
        return item != null && item.disposition() != null && item.reason() != null && !item.reason().isBlank() && evidence.brief().containsSubstantive(item.briefEvidenceIds())
                && evidence.concept().containsSubstantive(item.conceptEvidenceIds()) && evidence.specification().containsSubstantive(item.specEvidenceIds());
    }

    private static boolean validSpecificationReviewItem(@Nullable SpecificationReviewItem item) {
        return item != null && item.reason() != null && !item.reason().isBlank();
    }

}
