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
 * Independently adjudicates a witness that executed and failed against the reference solution. Execution proves the failure, but not that the model-authored assertion correctly
 * interprets the specification; this focused pass supplies that missing decision before a reference change can be scheduled.
 */
class ReferenceWitnessCritic {

    private static final Logger log = LoggerFactory.getLogger(ReferenceWitnessCritic.class);

    private static final String PROMPT_TEMPLATE = "/prompts/hyperion/critic/reference_witness_review_system.st";

    private static final int MAX_OUTPUT_TOKENS = 4_096;

    private enum Verdict {
        SUPPORTED_REFERENCE_DEFECT, INVALID_WITNESS, INCONCLUSIVE
    }

    private record Response(@Nullable List<Item> outcomes) {
    }

    private record Item(@Nullable String testName, @Nullable Verdict verdict, @Nullable String sourceQuote, @Nullable String reason) {
    }

    private final ReviewerClient reviewer;

    private final ObjectMapper objectMapper;

    ReferenceWitnessCritic(ReviewerClient reviewer, ObjectMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    SpecFidelityCriticService.ReferenceWitnessReview adjudicate(String specification, String solutionSources, List<ContractWitnessOutcome> outcomes,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        List<ContractWitnessOutcome> failures = outcomes.stream().filter(outcome -> outcome.disposition() == Disposition.REFERENCE_TEST_FAILED).toList();
        if (failures.isEmpty()) {
            return SpecFidelityCriticService.ReferenceWitnessReview.empty();
        }
        if (cancelled.getAsBoolean()) {
            return unavailable("Reference-witness adjudication was cancelled.", failures);
        }
        requireReviewTextSafe("reference-witness/specification", specification);
        requireReviewTextSafe("reference-witness/solution", solutionSources);
        if (!reviewer.configured()) {
            return unavailable("The independent reference-witness adjudicator is not configured.", failures);
        }
        String boundedSolution = solutionSources.strip();
        if (boundedSolution.length() > MAX_ARTIFACT_EVIDENCE_CHARS) {
            boundedSolution = boundedSolution.substring(0, MAX_ARTIFACT_EVIDENCE_CHARS);
        }
        StringBuilder prompt = new StringBuilder("FROZEN SPECIFICATION (sole contract authority):\n").append(specification.strip())
                .append("\n\nREFERENCE SOLUTION SOURCES (bounded implementation evidence, never contract authority):\n").append(boundedSolution)
                .append("\n\nENVIRONMENT-CONFIRMED REFERENCE FAILURES:\n");
        for (ContractWitnessOutcome failure : failures) {
            ContractWitness witness = failure.witness();
            prompt.append("\nTEST NAME: ").append(witness.testName()).append("\nRULE ID: ").append(witness.ruleId()).append("\nTEST METHOD:\n").append(witness.code())
                    .append("\nSANITIZED BUILD EVIDENCE:\n").append(failure.diagnostic()).append('\n');
        }
        try {
            String response = reviewer.call(PROMPT_TEMPLATE, prompt.toString(), usageSink, MAX_OUTPUT_TOKENS);
            return parse(response, specification, failures);
        }
        catch (RuntimeException exception) {
            log.warn("Reference-witness adjudication failed: {}", exception.getMessage());
            return unavailable("The independent reference-witness adjudicator failed.", failures);
        }
    }

    private SpecFidelityCriticService.ReferenceWitnessReview parse(@Nullable String text, String specification, List<ContractWitnessOutcome> failures) {
        Response response;
        try {
            response = text == null ? null : objectMapper.readValue(extractJsonPayload(text), Response.class);
        }
        catch (Exception exception) {
            return unavailable("The independent reference-witness verdict was malformed.", failures);
        }
        if (response == null || response.outcomes() == null) {
            return unavailable("The independent reference-witness verdict was incomplete.", failures);
        }
        Map<String, ContractWitnessOutcome> failuresByName = new HashMap<>();
        for (ContractWitnessOutcome failure : failures) {
            if (failuresByName.putIfAbsent(failure.witness().testName(), failure) != null) {
                return unavailable("The environment reported duplicate reference-witness test names.", failures);
            }
        }
        Set<String> seen = new HashSet<>();
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        List<ContractWitness> supported = new ArrayList<>();
        List<ContractWitness> invalid = new ArrayList<>();
        List<ContractWitness> unresolved = new ArrayList<>();
        for (Item item : response.outcomes()) {
            if (item == null || item.testName() == null || item.verdict() == null || !seen.add(item.testName())) {
                return unavailable("The independent reference-witness verdict did not identify every failed test exactly once.", failures);
            }
            ContractWitnessOutcome failure = failuresByName.get(item.testName());
            if (failure == null) {
                return unavailable("The independent reference-witness verdict named a test the environment did not report failing.", failures);
            }
            if (item.reason() == null || item.reason().isBlank()) {
                return unavailable("The independent reference-witness verdict omitted its rationale.", failures);
            }
            if (item.verdict() == Verdict.INVALID_WITNESS) {
                invalid.add(failure.witness());
                continue;
            }
            if (item.verdict() == Verdict.INCONCLUSIVE) {
                findings.addAll(
                        unavailable("The independent reviewer could not determine whether " + item.testName() + " exposes a reference defect.", List.of(failure)).findings());
                unresolved.add(failure.witness());
                continue;
            }
            if (item.sourceQuote() == null || item.sourceQuote().isBlank() || !specification.contains(item.sourceQuote().strip())) {
                return unavailable("A claimed reference defect was not grounded in an exact specification quote.", failures);
            }
            ContractWitness witness = failure.witness();
            findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                    "Reference solution violates " + witness.ruleId() + " in executable witness " + witness.testName(),
                    "An independent reviewer grounded the failed test in the frozen specification quote \"" + truncate(item.sourceQuote().strip()) + "\". The environment "
                            + "executed the named test against the reference solution and observed a failure or error (not a compilation or discovery failure). Repair the "
                            + "reference behavior without weakening the frozen rule, then make this exact witness pass. Reviewer rationale: " + truncate(item.reason().strip())
                            + "\nWitness:\n" + witness.code() + "\nEnvironment evidence:\n" + truncate(failure.diagnostic())));
            supported.add(witness);
        }
        if (!seen.equals(failuresByName.keySet())) {
            return unavailable("The independent reference-witness verdict omitted an environment-confirmed failed test.", failures);
        }
        return new SpecFidelityCriticService.ReferenceWitnessReview(findings, supported, invalid, unresolved);
    }

    private static SpecFidelityCriticService.ReferenceWitnessReview unavailable(String detail, List<ContractWitnessOutcome> failures) {
        return new SpecFidelityCriticService.ReferenceWitnessReview(SpecFidelityReport.qualityReviewUnavailable(detail).findings(), List.of(), List.of(),
                failures.stream().map(ContractWitnessOutcome::witness).distinct().toList());
    }
}
