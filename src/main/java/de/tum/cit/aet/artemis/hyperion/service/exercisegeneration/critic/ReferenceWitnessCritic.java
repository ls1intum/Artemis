package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.MAX_ARTIFACT_EVIDENCE_CHARS;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.requireReviewTextSafe;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.truncate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome.Disposition;

/**
 * Independently adjudicates executed contract witnesses. Execution proves how a witness behaves against the generated reference and starter, but not that the model-authored
 * assertion follows from the specification; this focused pass supplies that missing decision before a reference repair or test adoption can be scheduled.
 */
class ReferenceWitnessCritic {

    private static final Logger log = LoggerFactory.getLogger(ReferenceWitnessCritic.class);

    private static final String PROMPT_TEMPLATE = "/prompts/hyperion/critic/reference_witness_review_system.st";

    private static final int MAX_OUTPUT_TOKENS = 4_096;

    private enum Verdict {
        SUPPORTED_REFERENCE_DEFECT, SUPPORTED_GRADING_WITNESS, INVALID_WITNESS, INCONCLUSIVE
    }

    private record Response(@Nullable List<Item> outcomes) {
    }

    private record Item(@Nullable String testName, @Nullable Verdict verdict, @Nullable String sourceQuote, @Nullable String ownerType, @Nullable String reason) {
    }

    private final ReviewerClient reviewer;

    private final ObjectMapper objectMapper;

    ReferenceWitnessCritic(ReviewerClient reviewer, ObjectMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    SpecFidelityCriticService.ReferenceWitnessReview adjudicate(String specification, String solutionSources, Map<String, String> templateStatuses,
            List<ContractWitnessOutcome> outcomes, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        List<ContractWitnessOutcome> candidates = outcomes.stream()
                .filter(outcome -> outcome.disposition() == Disposition.REFERENCE_TEST_FAILED || outcome.disposition() == Disposition.REFERENCE_PASSED_STARTER_FAILED).toList();
        if (candidates.isEmpty()) {
            return SpecFidelityCriticService.ReferenceWitnessReview.empty();
        }
        if (cancelled.getAsBoolean()) {
            return unavailable("Contract-witness adjudication was cancelled.", candidates);
        }
        requireReviewTextSafe("reference-witness/specification", specification);
        requireReviewTextSafe("reference-witness/solution", solutionSources);
        if (!reviewer.configured()) {
            return unavailable("The independent contract-witness adjudicator is not configured.", candidates);
        }
        String boundedSolution = solutionSources.strip();
        if (boundedSolution.length() > MAX_ARTIFACT_EVIDENCE_CHARS) {
            boundedSolution = boundedSolution.substring(0, MAX_ARTIFACT_EVIDENCE_CHARS);
        }
        StringBuilder prompt = new StringBuilder("FROZEN SPECIFICATION (sole contract authority):\n").append(specification.strip())
                .append("\n\nREFERENCE SOLUTION SOURCES (bounded implementation evidence, never contract authority):\n").append(boundedSolution)
                .append("\n\nAUTHORITATIVE TEMPLATE OWNERSHIP:\n").append(ContractWitnessAuthor.renderTemplateOwnership(templateStatuses))
                .append("\n\nENVIRONMENT-CONFIRMED WITNESS OUTCOMES:\n");
        for (ContractWitnessOutcome candidate : candidates) {
            ContractWitness witness = candidate.witness();
            prompt.append("\nTEST NAME: ").append(witness.testName()).append("\nENVIRONMENT OUTCOME: ").append(candidate.disposition()).append("\nRULE ID: ")
                    .append(witness.ruleId()).append("\nTEST METHOD:\n").append(witness.code()).append("\nSANITIZED BUILD EVIDENCE:\n").append(candidate.diagnostic()).append('\n');
        }
        try {
            String response = reviewer.call(PROMPT_TEMPLATE, prompt.toString(), usageSink, MAX_OUTPUT_TOKENS);
            return parse(response, specification, templateStatuses, candidates);
        }
        catch (RuntimeException exception) {
            log.warn("Contract-witness adjudication failed: {}", exception.getMessage());
            return unavailable("The independent contract-witness adjudicator failed.", candidates);
        }
    }

    private SpecFidelityCriticService.ReferenceWitnessReview parse(@Nullable String text, String specification, Map<String, String> templateStatuses,
            List<ContractWitnessOutcome> candidates) {
        Response response;
        try {
            response = text == null ? null : objectMapper.readValue(extractJsonPayload(text), Response.class);
        }
        catch (Exception exception) {
            return unavailable("The independent contract-witness verdict was malformed.", candidates);
        }
        if (response == null || response.outcomes() == null) {
            return unavailable("The independent contract-witness verdict was incomplete.", candidates);
        }
        Map<String, ContractWitnessOutcome> candidatesByName = new HashMap<>();
        for (ContractWitnessOutcome candidate : candidates) {
            if (candidatesByName.putIfAbsent(candidate.witness().testName(), candidate) != null) {
                return unavailable("The environment reported duplicate contract-witness test names.", candidates);
            }
        }
        Set<String> seen = new HashSet<>();
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        List<ContractWitness> supported = new ArrayList<>();
        List<ContractWitness> adoptable = new ArrayList<>();
        List<ContractWitness> invalid = new ArrayList<>();
        List<ContractWitness> unresolvedReference = new ArrayList<>();
        List<ContractWitness> unresolvedAdoption = new ArrayList<>();
        for (Item item : response.outcomes()) {
            if (item == null || item.testName() == null || item.verdict() == null || !seen.add(item.testName())) {
                return unavailable("The independent contract-witness verdict did not identify every test exactly once.", candidates);
            }
            ContractWitnessOutcome candidate = candidatesByName.get(item.testName());
            if (candidate == null) {
                return unavailable("The independent contract-witness verdict named a test the environment did not report.", candidates);
            }
            if (item.reason() == null || item.reason().isBlank()) {
                return unavailable("The independent contract-witness verdict omitted its rationale.", candidates);
            }
            if (item.verdict() == Verdict.INVALID_WITNESS) {
                invalid.add(candidate.witness());
                continue;
            }
            if (item.verdict() == Verdict.INCONCLUSIVE) {
                addUnresolved(candidate, unresolvedReference, unresolvedAdoption);
                continue;
            }
            if (item.sourceQuote() == null || item.sourceQuote().isBlank() || !specification.contains(item.sourceQuote().strip())) {
                return unavailable("A supported contract witness was not grounded in an exact specification quote.", candidates);
            }
            if (item.verdict() == Verdict.SUPPORTED_REFERENCE_DEFECT && candidate.disposition() == Disposition.REFERENCE_TEST_FAILED) {
                ContractWitness witness = candidate.witness();
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                        "Reference solution violates " + witness.ruleId() + " in executable witness " + witness.testName(),
                        "An independent reviewer grounded the failed test in the frozen specification quote \"" + truncate(item.sourceQuote().strip()) + "\". The environment "
                                + "executed the named test against the reference solution and observed a failure or error (not a compilation or discovery failure). Repair the "
                                + "reference behavior without weakening the frozen rule, then make this exact witness pass. Reviewer rationale: " + truncate(item.reason().strip())
                                + "\nWitness:\n" + witness.code() + "\nEnvironment evidence:\n" + truncate(candidate.diagnostic())));
                supported.add(witness);
                continue;
            }
            if (item.verdict() == Verdict.SUPPORTED_GRADING_WITNESS && candidate.disposition() == Disposition.REFERENCE_PASSED_STARTER_FAILED
                    && studentOwned(item.ownerType(), templateStatuses)) {
                ContractWitness witness = candidate.witness();
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE,
                        "Rule " + witness.ruleId() + " has a source-approved executable witness " + witness.testName(),
                        "An independent reviewer grounded this assertion in the frozen specification quote \"" + truncate(item.sourceQuote().strip())
                                + "\" and attributed it to student-owned type " + item.ownerType().strip() + ". The environment executed it: the reference passes and the starter "
                                + "fails. Add it unless an existing assertion already distinguishes the same behavior. Reviewer rationale: " + truncate(item.reason().strip())
                                + "\nThe author proposed it for this plausible wrong behavior, which the environment did not execute: " + witness.wrongBehavior() + "\nWitness:\n"
                                + witness.code()));
                adoptable.add(witness);
                continue;
            }
            return unavailable("A contract-witness verdict did not match its environment outcome or a student-owned Design type.", candidates);
        }
        if (!seen.equals(candidatesByName.keySet())) {
            return unavailable("The independent contract-witness verdict omitted an environment-confirmed test.", candidates);
        }
        return new SpecFidelityCriticService.ReferenceWitnessReview(findings, supported, adoptable, invalid, unresolvedReference, unresolvedAdoption);
    }

    private static void addUnresolved(ContractWitnessOutcome outcome, List<ContractWitness> unresolvedReference, List<ContractWitness> unresolvedAdoption) {
        (outcome.disposition() == Disposition.REFERENCE_TEST_FAILED ? unresolvedReference : unresolvedAdoption).add(outcome.witness());
    }

    private static boolean studentOwned(@Nullable String ownerType, Map<String, String> templateStatuses) {
        if (ownerType == null || ownerType.isBlank()) {
            return false;
        }
        String status = templateStatuses.get(ownerType.strip());
        return "stubbed".equals(status) || "student-creates".equals(status);
    }

    private static SpecFidelityCriticService.ReferenceWitnessReview unavailable(String detail, List<ContractWitnessOutcome> candidates) {
        List<ContractWitness> unresolvedReference = candidates.stream().filter(outcome -> outcome.disposition() == Disposition.REFERENCE_TEST_FAILED)
                .map(ContractWitnessOutcome::witness).distinct().toList();
        List<ContractWitness> unresolvedAdoption = candidates.stream().filter(outcome -> outcome.disposition() == Disposition.REFERENCE_PASSED_STARTER_FAILED)
                .map(ContractWitnessOutcome::witness).distinct().toList();
        List<SpecFidelityReport.Finding> findings = unresolvedReference.isEmpty() ? List.of() : SpecFidelityReport.qualityReviewUnavailable(detail).findings();
        return new SpecFidelityCriticService.ReferenceWitnessReview(findings, List.of(), List.of(), List.of(), unresolvedReference, unresolvedAdoption);
    }
}
