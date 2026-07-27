package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.requireReviewTextSafe;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.truncateLearningEvidence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService.ConceptSelectionReview;

/**
 * The concept-selection review pass: one bounded, tool-free review of exactly three generator-authored concepts against the instructor brief, run before any specification work
 * starts.
 * <p>
 * It is the cheapest place to reject a weak exercise idea — a concept that cannot carry the requested learning objective consumes the entire specification and repository
 * authoring budget before any later gate can see the problem. The reviewer is a selector and diagnostician only: it never proposes replacement design content, and its verdict is
 * accepted only when every judgment is grounded in the candidate lines it was shown.
 */
class ConceptSelectionCritic {

    private static final Logger log = LoggerFactory.getLogger(ConceptSelectionCritic.class);

    private static final String CONCEPT_REVIEW_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/concept_review_system.st";

    /**
     * Sized for the declared response shape: the selected candidate and its reason, plus one evaluation per candidate carrying its evidence IDs, seven short prose analyses and
     * nine booleans — so three candidates' worth of structured judgment, each field a phrase or a sentence rather than an essay. The cap covers hidden reasoning too, and a
     * response cut off by it is unparseable and costs the correction pass below.
     */
    private static final int CONCEPT_REVIEW_MAX_OUTPUT_TOKENS = 4_096;

    private static final String CONCEPT_REVIEW_CORRECTION = """

            The previous response was malformed, incomplete, or internally inconsistent. Re-evaluate the same three candidates and return the complete JSON object. Preserve sound
            judgments, but do not add replacement design ideas.
            """;

    private record ConceptReviewResponse(@Nullable Integer selectedCandidate, @Nullable String selectionReason, @Nullable List<ConceptCandidateReviewItem> evaluations) {
    }

    private record ConceptCandidateReviewItem(@Nullable Integer candidate, @Nullable List<String> candidateEvidenceIds, @Nullable String briefCoverage,
            @Nullable String objectiveCounterfactual, @Nullable String difficultyFit, @Nullable String smallestStudentImplementation, @Nullable String reasoningAfterRoutineWork,
            @Nullable String domainGrounding, @Nullable String feasibility, @Nullable Boolean briefCovered, @Nullable Boolean objectiveEssential,
            @Nullable Boolean learningFitSufficient, @Nullable Boolean learnerOwnsObjectiveMechanism, @Nullable Boolean objectiveObservable,
            @Nullable Boolean prematureContractClosure, @Nullable Boolean difficultySufficient, @Nullable Boolean domainGrounded, @Nullable Boolean feasibleAndProportionate) {
    }

    private final ReviewerClient reviewer;

    private final ObjectMapper objectMapper;

    ConceptSelectionCritic(ReviewerClient reviewer, ObjectMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    /**
     * Selects one generator-authored concept without contributing design content. This early semantic check prevents a weak concept from consuming the full SPEC and repository
     * authoring budget.
     *
     * @param brief      the instructor brief
     * @param candidates exactly three generator-authored concept candidates
     * @param usageSink  optional token-usage sink
     * @param cancelled  cooperative cancellation signal
     * @return the grounded selection verdict
     */
    ConceptSelectionReview reviewConceptCandidates(String brief, Map<Integer, String> candidates, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        Map<Integer, EvidenceSource> candidateEvidence = candidates.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> EvidenceSource.from("C" + entry.getKey() + ".", entry.getValue())));
        String candidateText = candidateEvidence.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getValue().promptText())
                .collect(Collectors.joining("\n\n"));
        requireReviewTextSafe("concept-review/brief", brief);
        requireReviewTextSafe("concept-review/candidates", candidateText);
        if (cancelled.getAsBoolean() || !reviewer.configured() || brief.isBlank() || candidates.size() != 3) {
            return new ConceptSelectionReview(false, null, List.of(), "");
        }
        String userPrompt = "INSTRUCTOR BRIEF (sole authority):\n" + brief.strip() + "\n\nGENERATOR-AUTHORED CONCEPT CANDIDATES:\n" + candidateText;
        try {
            String response = reviewer.call(CONCEPT_REVIEW_SYSTEM_PROMPT_TEMPLATE, userPrompt, usageSink, CONCEPT_REVIEW_MAX_OUTPUT_TOKENS);
            ConceptSelectionReview review = parseConceptReview(readConceptReviewResponse(response), candidates, candidateEvidence);
            if (review.complete() || cancelled.getAsBoolean()) {
                return review;
            }
            String correction = reviewer.call(CONCEPT_REVIEW_SYSTEM_PROMPT_TEMPLATE,
                    userPrompt + CONCEPT_REVIEW_CORRECTION + "\n\nSERVER VALIDATION FAILURE TO CORRECT:\n" + review.auditSummary(), usageSink, CONCEPT_REVIEW_MAX_OUTPUT_TOKENS);
            return parseConceptReview(readConceptReviewResponse(correction), candidates, candidateEvidence);
        }
        catch (RuntimeException e) {
            log.warn("Concept review failed: {}", e.getMessage());
            return new ConceptSelectionReview(false, null, List.of(), "");
        }
    }

    private @Nullable ConceptReviewResponse readConceptReviewResponse(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(extractJsonPayload(text), ConceptReviewResponse.class);
        }
        catch (Exception e) {
            log.debug("Concept review JSON did not parse ({}); failing closed.", e.getMessage());
            return null;
        }
    }

    private static ConceptSelectionReview parseConceptReview(@Nullable ConceptReviewResponse response, Map<Integer, String> candidates,
            Map<Integer, EvidenceSource> candidateEvidence) {
        if (response == null) {
            return incompleteConceptReview("The response was empty or was not valid JSON in the required object shape.");
        }
        if (!hasConceptAnalysis(response.selectionReason())) {
            return incompleteConceptReview("selectionReason is mandatory and must contain a substantive comparison.");
        }
        if (response.evaluations() == null || response.evaluations().size() != 3) {
            return incompleteConceptReview("evaluations must contain exactly three items.");
        }
        Map<Integer, ConceptCandidateReviewItem> evaluations = new HashMap<>();
        for (ConceptCandidateReviewItem item : response.evaluations()) {
            String validationError = conceptEvaluationValidationError(item, candidates, candidateEvidence);
            if (validationError != null) {
                return incompleteConceptReview(validationError);
            }
            if (evaluations.putIfAbsent(item.candidate(), item) != null) {
                return incompleteConceptReview("each candidate number must appear exactly once.");
            }
        }
        if (!evaluations.keySet().equals(candidates.keySet())) {
            return incompleteConceptReview("evaluations must cover candidates 1, 2, and 3 exactly once.");
        }
        if (response.selectedCandidate() != null) {
            ConceptCandidateReviewItem selected = evaluations.get(response.selectedCandidate());
            if (selected == null || !conceptPasses(selected)) {
                return incompleteConceptReview("selectedCandidate must name an evaluation that passes every required axis.");
            }
            return new ConceptSelectionReview(true, response.selectedCandidate(), List.of(), truncateLearningEvidence(response.selectionReason().strip()),
                    conceptReviewAudit(response, evaluations));
        }
        if (evaluations.values().stream().anyMatch(ConceptSelectionCritic::conceptPasses)) {
            return incompleteConceptReview("selectedCandidate cannot be null while at least one evaluation passes every required axis.");
        }
        List<String> findings = evaluations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "Candidate " + entry.getKey() + ": " + conceptFailureSummary(entry.getValue())).toList();
        return new ConceptSelectionReview(true, null, findings, failedConceptAxes(evaluations.values()), conceptReviewAudit(response, evaluations));
    }

    private static ConceptSelectionReview incompleteConceptReview(String detail) {
        return new ConceptSelectionReview(false, null, List.of(), truncateLearningEvidence(detail));
    }

    private static String conceptReviewAudit(ConceptReviewResponse response, Map<Integer, ConceptCandidateReviewItem> evaluations) {
        StringBuilder audit = new StringBuilder("Selected candidate: ").append(response.selectedCandidate() == null ? "none" : response.selectedCandidate())
                .append("\nSelection reason: ").append(truncateLearningEvidence(response.selectionReason().strip()));
        evaluations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ConceptCandidateReviewItem item = entry.getValue();
            audit.append("\n\n## Candidate ").append(entry.getKey()).append(conceptPasses(item) ? " — accepted" : " — rejected");
            appendConceptAxis(audit, "Brief coverage", item.briefCovered(), item.briefCoverage());
            appendConceptAxis(audit, "Objective is essential", item.objectiveEssential(), item.objectiveCounterfactual());
            audit.append("\n- Learner owns objective mechanism: ").append(item.learnerOwnsObjectiveMechanism() ? "pass" : "fail");
            audit.append("\n- Objective observable end to end: ").append(item.objectiveObservable() ? "pass" : "fail");
            audit.append("\n- Premature contract closure: ").append(item.prematureContractClosure() ? "fail" : "pass");
            appendConceptAxis(audit, "Difficulty", item.difficultySufficient(), item.difficultyFit());
            audit.append("\n- Smallest student implementation: ").append(truncateLearningEvidence(item.smallestStudentImplementation().strip()));
            audit.append("\n- Reasoning after routine work: ").append(truncateLearningEvidence(item.reasoningAfterRoutineWork().strip()));
            appendConceptAxis(audit, "Domain grounding", item.domainGrounded(), item.domainGrounding());
            appendConceptAxis(audit, "Feasibility and proportionality", item.feasibleAndProportionate(), item.feasibility());
        });
        return audit.toString();
    }

    private static void appendConceptAxis(StringBuilder audit, String label, boolean passed, String analysis) {
        audit.append("\n- ").append(label).append(passed ? " (pass): " : " (fail): ").append(truncateLearningEvidence(analysis.strip()));
    }

    private static boolean hasConceptAnalysis(@Nullable String analysis) {
        return analysis != null && analysis.strip().length() >= 12;
    }

    private static @Nullable String conceptEvaluationValidationError(@Nullable ConceptCandidateReviewItem item, Map<Integer, String> candidates,
            Map<Integer, EvidenceSource> candidateEvidence) {
        if (item == null || item.candidate() == null || !candidates.containsKey(item.candidate())) {
            return "each evaluation must name candidate 1, 2, or 3.";
        }
        if (!hasConceptAnalysis(item.briefCoverage()) || !hasConceptAnalysis(item.objectiveCounterfactual()) || !hasConceptAnalysis(item.difficultyFit())
                || !hasConceptAnalysis(item.domainGrounding()) || !hasConceptAnalysis(item.feasibility()) || !hasConceptAnalysis(item.smallestStudentImplementation())
                || !hasConceptAnalysis(item.reasoningAfterRoutineWork())) {
            return "candidate " + item.candidate() + " is missing one or more mandatory substantive analysis fields.";
        }
        EvidenceSource evidence = candidateEvidence.get(item.candidate());
        if (evidence == null || !evidence.containsSubstantive(item.candidateEvidenceIds())) {
            return "candidate " + item.candidate() + " candidateEvidenceIds must cite a substantive line from that same candidate.";
        }
        if (item.briefCovered() == null || item.objectiveEssential() == null || item.learningFitSufficient() == null || item.learnerOwnsObjectiveMechanism() == null
                || item.objectiveObservable() == null || item.prematureContractClosure() == null || item.difficultySufficient() == null || item.domainGrounded() == null
                || item.feasibleAndProportionate() == null) {
            return "candidate " + item.candidate() + " is missing one or more mandatory boolean judgments.";
        }
        if (item.learningFitSufficient()
                && (!item.objectiveEssential() || !item.learnerOwnsObjectiveMechanism() || !item.objectiveObservable() || item.prematureContractClosure())) {
            return "candidate " + item.candidate()
                    + " cannot set learningFitSufficient true unless objectiveEssential, learnerOwnsObjectiveMechanism, and objectiveObservable are true and prematureContractClosure is false.";
        }
        return null;
    }

    private static boolean conceptPasses(ConceptCandidateReviewItem item) {
        return item.briefCovered() && item.objectiveEssential() && item.learningFitSufficient() && item.learnerOwnsObjectiveMechanism() && item.objectiveObservable()
                && !item.prematureContractClosure() && item.difficultySufficient() && item.domainGrounded() && item.feasibleAndProportionate();
    }

    private static String conceptFailureSummary(ConceptCandidateReviewItem item) {
        List<String> failures = new ArrayList<>();
        if (!item.briefCovered()) {
            failures.add("brief fit — " + item.briefCoverage().strip());
        }
        if (!item.objectiveEssential() || !item.learningFitSufficient()) {
            failures.add("learning objective — " + item.objectiveCounterfactual().strip());
        }
        if (!item.learnerOwnsObjectiveMechanism()) {
            failures.add("learner ownership — the requested objective mechanism remains in supplied scaffolding");
        }
        if (!item.objectiveObservable()) {
            failures.add("assessment path — the requested objective is not observable end to end");
        }
        if (item.prematureContractClosure()) {
            failures.add("concept exploration — the candidate prematurely fixes contract details and then counts their transcription as reasoning");
        }
        if (!item.difficultySufficient()) {
            failures.add("difficulty — " + item.difficultyFit().strip());
        }
        if (!item.domainGrounded()) {
            failures.add("grounding — " + item.domainGrounding().strip());
        }
        if (!item.feasibleAndProportionate()) {
            failures.add("feasibility — " + item.feasibility().strip());
        }
        return truncateLearningEvidence(String.join("; ", failures));
    }

    private static String failedConceptAxes(Collection<ConceptCandidateReviewItem> evaluations) {
        List<String> axes = new ArrayList<>();
        if (evaluations.stream().anyMatch(item -> !item.briefCovered())) {
            axes.add("brief coverage");
        }
        if (evaluations.stream().anyMatch(item -> !item.objectiveEssential() || !item.learningFitSufficient())) {
            axes.add("learner-owned learning fit");
        }
        if (evaluations.stream().anyMatch(item -> !item.difficultySufficient())) {
            axes.add("requested difficulty after routine work is removed");
        }
        if (evaluations.stream().anyMatch(item -> !item.domainGrounded())) {
            axes.add("domain grounding");
        }
        if (evaluations.stream().anyMatch(item -> !item.feasibleAndProportionate())) {
            axes.add("feasibility and proportionality");
        }
        return "The previous batch failed these review axes: " + String.join(", ", axes) + ".";
    }
}
