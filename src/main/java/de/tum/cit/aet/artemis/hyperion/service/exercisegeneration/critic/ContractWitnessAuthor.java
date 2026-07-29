package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.MAX_ARTIFACT_EVIDENCE_CHARS;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.requireReviewTextSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The contract-witness authoring pass: it proposes a few executable test methods that pin rules of the approved specification, so rule coverage becomes something the server can
 * run instead of something a model asserts.
 * <p>
 * Every candidate is validated against the reference solution by the caller and discarded unless it passes, which is why an over-confident witness costs nothing.
 */
class ContractWitnessAuthor {

    private static final Logger log = LoggerFactory.getLogger(ContractWitnessAuthor.class);

    private static final String CONTRACT_WITNESS_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/contract_witness_system.st";

    private static final int CONTRACT_WITNESS_MAX_OUTPUT_TOKENS = 8_192;

    /** Each witness costs a validating build, so the pass stays small enough to sit inside a generation without dominating its wall clock. */
    private static final int MAX_CONTRACT_WITNESSES = 3;

    /** Assertion calls a witness may use; a witness without one passes against every implementation and therefore pins nothing. */
    private static final Pattern ASSERTION_CALL = Pattern.compile("\\b(assert\\w*|verify|expect(That)?)\\s*\\(");

    private record ContractWitnessResponse(@Nullable List<ContractWitnessItem> witnesses) {
    }

    private record ContractWitnessItem(@Nullable String rule, @Nullable String testName, @Nullable String code, @Nullable String wrongBehavior) {
    }

    private final ReviewerClient reviewer;

    private final ObjectMapper objectMapper;

    ContractWitnessAuthor(ReviewerClient reviewer, ObjectMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    /**
     * Authors executable witnesses for rules of the approved specification, so rule coverage becomes something the server can run rather than something a model asserts.
     * <p>
     * The oracle review already proposes plausible wrong implementations and reports whether the graded suite kills them, but its {@code killed} flag is the reviewing model's own
     * claim and is never executed. A witness is executed, so the caller can validate each one against the reference solution and discard any that does not pass.
     * <p>
     * Kept separate from the oracle review on purpose: a rule is usually untested because the authoring agent did not think of it, so asking that same context to attack its own
     * work reproduces the blind spot.
     *
     * @param specificationContract the approved specification whose {@code ## Rules} rows are the only admissible source of a witness
     * @param testSources           the graded test sources as produced, so the pass targets rules the suite does not already pin
     * @param solutionSources       the reference solution, which fixes the exact API a witness must call
     * @param usageSink             optional token-usage sink
     * @param cancelled             cooperative cancellation signal
     * @return at most {@link #MAX_CONTRACT_WITNESSES} unvalidated candidates; empty whenever the pass is unavailable, cancelled, or does not parse
     */
    List<ContractWitness> authorContractWitnesses(String specificationContract, String testSources, String solutionSources, Map<String, String> templateStatuses,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        requireReviewTextSafe("contract-witness/specification", specificationContract);
        requireReviewTextSafe("contract-witness/tests", testSources);
        // The reference solution goes to the provider like every other artifact, so it passes the same secret-material policy; an adapted exercise can carry a hard-coded key.
        requireReviewTextSafe("contract-witness/solution", solutionSources);
        if (cancelled.getAsBoolean() || !reviewer.configured() || specificationContract.isBlank() || testSources.isBlank()) {
            return List.of();
        }
        String userPrompt = "APPROVED SPECIFICATION CONTRACT (sole authority for a rule):\n" + specificationContract.strip() + "\n\nAUTHORITATIVE TEMPLATE OWNERSHIP:\n"
                + renderTemplateOwnership(templateStatuses) + "\n\nREFERENCE SOLUTION (fixes the exact API a witness may call):\n" + boundedEvidence(solutionSources)
                + "\n\nGRADED TEST SOURCES AS PRODUCED:\n" + boundedEvidence(testSources);
        try {
            String response = reviewer.call(CONTRACT_WITNESS_SYSTEM_PROMPT_TEMPLATE, userPrompt, usageSink, CONTRACT_WITNESS_MAX_OUTPUT_TOKENS);
            return parseContractWitnesses(response, specificationContract);
        }
        catch (RuntimeException e) {
            // Advisory by construction: the caller proceeds with the suite it already has.
            log.warn("Contract-witness authoring failed: {}", e.getMessage());
            return List.of();
        }
    }

    static String renderTemplateOwnership(Map<String, String> statuses) {
        if (statuses.isEmpty()) {
            return "No Design ownership table was parsed. Infer no starter availability; follow the graded tests' compilation style and rely on environment validation.";
        }
        StringBuilder rendered = new StringBuilder();
        statuses.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            rendered.append("- ").append(entry.getKey()).append(": ").append(entry.getValue());
            if ("student-creates".equals(entry.getValue())) {
                rendered.append(" — ABSENT from the starter. Never name this type in a declaration, constructor call, class literal, cast, or generic signature; use the shown "
                        + "reflection/dynamic-proxy idiom so the starter compiles and the witness executes.");
            }
            else {
                rendered.append(" — present in the starter and may be named directly.");
            }
            rendered.append('\n');
        });
        return rendered.toString().stripTrailing();
    }

    private static String boundedEvidence(String text) {
        String stripped = text.strip();
        return stripped.length() <= MAX_ARTIFACT_EVIDENCE_CHARS ? stripped : stripped.substring(0, MAX_ARTIFACT_EVIDENCE_CHARS);
    }

    private List<ContractWitness> parseContractWitnesses(@Nullable String text, String specificationContract) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        ContractWitnessResponse parsed;
        try {
            parsed = objectMapper.readValue(extractJsonPayload(text), ContractWitnessResponse.class);
        }
        catch (Exception e) {
            log.debug("Contract-witness JSON did not parse ({}); authoring nothing.", e.getMessage());
            return List.of();
        }
        if (parsed == null || parsed.witnesses() == null) {
            return List.of();
        }
        List<ContractWitness> witnesses = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (ContractWitnessItem item : parsed.witnesses()) {
            if (item == null || isBlank(item.rule()) || isBlank(item.testName()) || isBlank(item.code()) || isBlank(item.wrongBehavior())) {
                continue;
            }
            String testName = item.testName().strip();
            String code = item.code().strip();
            String ruleId = item.rule().strip();
            // Each check below drops a witness that would otherwise be validated on no evidence: the name must be the method the code DECLARES, or a build result could never be
            // attributed to it; a witness with no assertion passes against every implementation; and a rule the specification does not contain is an invented requirement.
            if (!declaresMethod(code, testName) || !containsAssertion(code) || !specificationDeclaresRule(specificationContract, ruleId) || !seenNames.add(testName)) {
                continue;
            }
            witnesses.add(new ContractWitness(ruleId, testName, code, item.wrongBehavior().strip()));
            if (witnesses.size() == MAX_CONTRACT_WITNESSES) {
                break;
            }
        }
        return List.copyOf(witnesses);
    }

    private static boolean declaresMethod(String code, String testName) {
        return Pattern.compile("\\b" + Pattern.quote(testName) + "\\s*\\(").matcher(code).find()
                && Pattern.compile("\\bvoid\\s+" + Pattern.quote(testName) + "\\s*\\(").matcher(code).find();
    }

    private static boolean containsAssertion(String code) {
        return ASSERTION_CALL.matcher(code).find();
    }

    /** Whether the approved specification actually declares this rule ID, so a witness can never pin a requirement the contract does not state. */
    private static boolean specificationDeclaresRule(String specificationContract, String ruleId) {
        return Pattern.compile("(?<![\\w])" + Pattern.quote(ruleId) + "(?![\\w])").matcher(specificationContract).find();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
