package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.requireReviewTextSafe;
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

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService.ConceptSelectionReview;

/**
 * A small adversarial admission pass over only the selected concept.
 * <p>
 * The broad selector compares learning designs against a general rubric. This pass gets a much smaller context and checks the failure modes that are easy to miss in that
 * comparison: unsupported exact choices, unobservable implementation requirements, and distinctions that disappear from the smallest equivalent behavior.
 */
final class ConceptAdmissionCritic {

    private static final Logger log = LoggerFactory.getLogger(ConceptAdmissionCritic.class);

    private static final String SYSTEM_PROMPT = "/prompts/hyperion/critic/concept_admission_system.st";

    private static final int MAX_OUTPUT_TOKENS = 2_048;

    private static final String CORRECTION = """

            The previous admission response was malformed or internally inconsistent. Re-audit the same selected candidate and return the complete JSON object. Do not propose a
            replacement design.
            """;

    private record AdmissionFinding(@Nullable List<String> candidateEvidenceIds, @Nullable String detail) {
    }

    private record AdmissionResponse(@Nullable List<String> auditedCandidateEvidenceIds, @Nullable String smallestEquivalentImplementation,
            @Nullable String observablePartitionAudit, @Nullable List<AdmissionFinding> unsupportedChoices, @Nullable List<AdmissionFinding> unobservableRequirements,
            @Nullable List<AdmissionFinding> redundantDistinctions, @Nullable Boolean admissible, @Nullable String summary) {
    }

    private record ParsedAdmission(boolean complete, boolean admitted, String finding, String audit) {
    }

    private final ReviewerClient reviewer;

    private final ObjectMapper objectMapper;

    ConceptAdmissionCritic(ReviewerClient reviewer, ObjectMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    ConceptSelectionReview admit(String brief, int candidateNumber, String candidate, ConceptSelectionReview selection, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        if (!selection.accepted() || cancelled.getAsBoolean()) {
            return selection;
        }
        EvidenceSource evidence = EvidenceSource.from("C" + candidateNumber + ".", candidate);
        String userPrompt = "INSTRUCTOR BRIEF (sole authority):\n" + brief.strip() + "\n\nSELECTED CANDIDATE EVIDENCE:\n" + evidence.promptText();
        requireReviewTextSafe("concept-admission/brief", brief);
        requireReviewTextSafe("concept-admission/candidate", candidate);
        try {
            ParsedAdmission parsed = parse(reviewer.call(SYSTEM_PROMPT, userPrompt, usageSink, MAX_OUTPUT_TOKENS), evidence);
            if (!parsed.complete() && !cancelled.getAsBoolean()) {
                parsed = parse(reviewer.call(SYSTEM_PROMPT, userPrompt + CORRECTION + "\n\nSERVER VALIDATION FAILURE:\n" + parsed.audit(), usageSink, MAX_OUTPUT_TOKENS), evidence);
            }
            if (!parsed.complete()) {
                String audit = selection.auditSummary() + "\n\n# Selected-concept admission\n\nReview unavailable: " + parsed.audit();
                return new ConceptSelectionReview(false, null, List.of(), "Selected-concept admission review was unavailable.", audit);
            }
            String audit = selection.auditSummary() + "\n\n# Selected-concept admission\n\n" + parsed.audit();
            if (parsed.admitted()) {
                return new ConceptSelectionReview(true, candidateNumber, List.of(), selection.decisionSummary(), audit);
            }
            // Zero failed selection axes with only this narrower audit objecting makes it the best-evidenced fallback. Recording it does not admit it: the finding travels with it.
            return new ConceptSelectionReview(true, null, List.of(parsed.finding()), parsed.finding(), audit, new SpecFidelityCriticService.ConceptFallback(candidateNumber, 0));
        }
        catch (RuntimeException e) {
            log.warn("Selected-concept admission review failed: {}", e.getMessage());
            String audit = selection.auditSummary() + "\n\n# Selected-concept admission\n\nReview unavailable because the provider call failed.";
            return new ConceptSelectionReview(false, null, List.of(), "Selected-concept admission review was unavailable.", audit);
        }
    }

    private ParsedAdmission parse(@Nullable String text, EvidenceSource evidence) {
        AdmissionResponse response;
        try {
            response = text == null || text.isBlank() ? null : objectMapper.readValue(extractJsonPayload(text), AdmissionResponse.class);
        }
        catch (Exception e) {
            return incomplete("The response was not valid JSON in the required admission shape.");
        }
        if (response == null) {
            return incomplete("The admission response was empty.");
        }
        if (!substantive(response.smallestEquivalentImplementation())) {
            return incomplete("smallestEquivalentImplementation was missing or not substantive.");
        }
        if (!substantive(response.observablePartitionAudit())) {
            return incomplete("observablePartitionAudit was missing or not substantive.");
        }
        if (!substantive(response.summary())) {
            return incomplete("summary was missing or not substantive.");
        }
        if (response.admissible() == null) {
            return incomplete("admissible was missing.");
        }
        if (!evidence.containsSubstantive(response.auditedCandidateEvidenceIds())) {
            return incomplete("auditedCandidateEvidenceIds did not cite known substantive candidate evidence.");
        }
        if (response.unsupportedChoices() == null || response.unobservableRequirements() == null || response.redundantDistinctions() == null) {
            return incomplete("One or more required finding arrays were missing.");
        }
        List<AdmissionFinding> reportedFindings = new ArrayList<>();
        reportedFindings.addAll(response.unsupportedChoices());
        reportedFindings.addAll(response.unobservableRequirements());
        reportedFindings.addAll(response.redundantDistinctions());
        if (reportedFindings.stream().anyMatch(finding -> finding == null || !substantive(finding.detail()) || !evidence.containsSubstantive(finding.candidateEvidenceIds()))) {
            return incomplete("Every admission finding must cite substantive selected-candidate evidence and give a concrete reason.");
        }
        if (response.admissible() != reportedFindings.isEmpty()) {
            return incomplete("admissible must be true exactly when all three finding arrays are empty.");
        }
        List<AdmissionFinding> findings = new ArrayList<>(response.unsupportedChoices());
        findings.addAll(response.unobservableRequirements().stream().filter(finding -> !evidence.citesOnlyNonNormativeStudentReasoning(finding.candidateEvidenceIds())).toList());
        findings.addAll(response.redundantDistinctions());
        int ignoredDescriptiveFindings = reportedFindings.size() - findings.size();
        boolean admitted = findings.isEmpty();
        String audit = "Decision: " + (admitted ? "admitted" : "rejected") + "\nSmallest equivalent implementation: "
                + truncateLearningEvidence(response.smallestEquivalentImplementation().strip()) + "\nObservable partition audit: "
                + truncateLearningEvidence(response.observablePartitionAudit().strip()) + "\nSummary: " + truncateLearningEvidence(response.summary().strip())
                + (ignoredDescriptiveFindings == 0 ? "" : "\nServer normalization: ignored " + ignoredDescriptiveFindings + " non-normative Student-owned reasoning finding(s).");
        if (admitted) {
            return new ParsedAdmission(true, true, "", audit);
        }
        String finding = "Selected concept failed focused admission: "
                + truncateLearningEvidence(findings.stream().map(AdmissionFinding::detail).map(String::strip).distinct().reduce((left, right) -> left + "; " + right).orElse(""));
        return new ParsedAdmission(true, false, finding, audit + "\nFindings: " + finding);
    }

    private static ParsedAdmission incomplete(String detail) {
        return new ParsedAdmission(false, false, "", detail);
    }

    private static boolean substantive(@Nullable String value) {
        return value != null && value.strip().length() >= 12;
    }
}
