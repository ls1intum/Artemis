package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ReviewGuardrails.MAX_ARTIFACT_EVIDENCE_CHARS;
import static de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ReviewGuardrails.requireReviewTextSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatResponse;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.hyperion.runtime.verification.JavaSourceText;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessProbe;

/**
 * The contract-witness authoring pass: it proposes a few executable test methods that pin rules of the approved specification, so rule coverage becomes something the server can
 * run instead of something a model asserts.
 * <p>
 * Every candidate is validated against the reference solution by the caller; a proposal is not correctness evidence until execution establishes it.
 */
class ContractWitnessAuthor {

    private static final String CONTRACT_WITNESS_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/contract_witness_system.st";

    private static final int CONTRACT_WITNESS_MAX_OUTPUT_TOKENS = 8_192;

    /** Each witness costs a validating build, so the pass stays small enough to sit inside a generation without dominating its wall clock. */
    private static final int MAX_CONTRACT_WITNESSES = 4;

    /** Assertion calls a witness may use; a witness without one passes against every implementation and therefore pins nothing. */
    private static final Pattern ASSERTION_CALL = Pattern.compile("\\b(assert\\w*|verify|expect(That)?)\\s*\\(");

    private static final Pattern TEST_METHOD_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]*+(?:[\\w<>,?\\[\\].$]++[ \\t]++(?!\\())++(?!(?:if|for|while|switch|catch|synchronized)(?![\\w$]))([A-Za-z_$][A-Za-z0-9_$]*+)[ \\t]*+\\((?:[^;{}()]++|\\((?:[^;{}()]++|\\([^;{}()]*+\\))*+\\))*+\\)\\s*+(?:throws\\s[^{;]*+)?\\{");

    private static final Pattern WITNESS_CONTRACT_SECTION = Pattern.compile("^## (Rules|Worked Examples|Public API)[ \\t]*\\r?$.*?(?=^## |\\z)",
            Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^[ \\t]*package[ \\t]+[\\w.$]+[ \\t]*;");

    private record ContractWitnessResponse(@Nullable List<ContractWitnessItem> witnesses) {
    }

    private record ContractWitnessItem(@Nullable String rule, @Nullable String testName, @Nullable String code, @Nullable String wrongBehavior) {
    }

    private final ReviewerClient reviewer;

    private final JsonMapper objectMapper;

    ContractWitnessAuthor(ReviewerClient reviewer, JsonMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    /**
     * Authors executable witnesses for rules of the approved specification. Kept separate from the oracle review because a rule is usually untested precisely because the
     * authoring agent did not think of it, so asking that same context to attack its own work reproduces the blind spot.
     *
     * @param specificationContract the approved specification whose {@code ## Rules} rows are the only admissible source of a witness
     * @param testSources           the graded sources, used locally to reject fixture-helper dependencies; only package declarations reach the witness designer
     * @param solutionSources       the reference source, screened locally; implementation bodies are withheld from witness design
     * @param usageSink             optional token-usage sink
     * @param cancelled             cooperative cancellation signal
     * @return at most {@link #MAX_CONTRACT_WITNESSES} unvalidated candidates; empty when authoring is disabled, cancelled or lacks its required inputs
     * @throws IllegalStateException if the response is invalid or one bounded correction still yields no admissible witness
     */
    List<ContractWitness> authorContractWitnesses(String specificationContract, String testSources, String solutionSources, Map<String, String> templateStatuses,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        requireReviewTextSafe("contract-witness/specification", specificationContract);
        requireReviewTextSafe("contract-witness/tests", testSources);
        // Keep the review safety boundary even though implementation bodies are withheld from this provider call.
        requireReviewTextSafe("contract-witness/solution", solutionSources);
        if (cancelled.getAsBoolean() || !reviewer.configured() || specificationContract.isBlank() || testSources.isBlank()) {
            return List.of();
        }
        String userPrompt = "APPROVED SPECIFICATION CONTRACT (sole authority for a rule):\n" + witnessContract(specificationContract) + "\n\nAUTHORITATIVE TEMPLATE OWNERSHIP:\n"
                + renderTemplateOwnership(templateStatuses) + "\n\nPROBE PACKAGE DECLARATIONS (names only):\n" + packageDeclarations(testSources);
        String response = Objects.requireNonNullElse(reviewer.call(CONTRACT_WITNESS_SYSTEM_PROMPT_TEMPLATE, userPrompt, usageSink, cancelled, CONTRACT_WITNESS_MAX_OUTPUT_TOKENS),
                "");
        List<ContractWitness> witnesses = parseContractWitnesses(response, specificationContract, testSources);
        if (witnesses.isEmpty() && !cancelled.getAsBoolean()) {
            String correction = userPrompt + "\n\nThe previous response yielded no admissible executable witness. This is not a completed quality check. "
                    + "Return at least one self-contained method for an observable rule of the approved contract. Each item needs an existing rule ID, a testName matching "
                    + "its declared void method, an assertion, and a specific wrongBehavior. Do not use fixture fields or helper methods from the graded suite. "
                    + "Reconsider legal state histories and actual input/collaborator observations rather than copying an existing assertion. "
                    + "If no grounded witness can be authored, return an empty array; the verified draft will require review.\n\nPRIOR RESPONSE (untrusted proposal data):\n"
                    + boundedEvidence(response);
            witnesses = parseContractWitnesses(reviewer.call(CONTRACT_WITNESS_SYSTEM_PROMPT_TEMPLATE, correction, usageSink, cancelled, CONTRACT_WITNESS_MAX_OUTPUT_TOKENS),
                    specificationContract, testSources);
        }
        if (witnesses.isEmpty() && !cancelled.getAsBoolean()) {
            throw new IllegalStateException("Contract-witness authoring produced no admissible executable witness after one bounded correction");
        }
        return witnesses;
    }

    /** Repairs compilation only; the caller must execute every corrected proposal again. No graded test bodies are supplied. */
    List<ContractWitness> repairCompilation(String specification, String solutionSources, List<ContractWitnessOutcome> outcomes, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        List<ContractWitnessOutcome> failures = outcomes.stream()
                .filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.INCONCLUSIVE && ContractWitnessProbe.hasCompilationFailure(outcome.diagnostic()))
                .limit(MAX_CONTRACT_WITNESSES).toList();
        if (failures.isEmpty() || cancelled.getAsBoolean() || !reviewer.configured()) {
            return List.of();
        }
        requireReviewTextSafe("probe-compilation/specification", specification);
        requireReviewTextSafe("probe-compilation/solution", solutionSources);
        String failedSources = objectMapper.writeValueAsString(failures);
        requireReviewTextSafe("probe-compilation/failures", failedSources);
        String prompt = "COMPILATION REPAIR OF TEMPORARY PROBES. Repair the reported compiler errors only. Return the same rule and testName for each corrected method. "
                + "Keep its intended public behavior and assertions. The wrapper contains no fixture fields, setup or helpers: construct all required objects inside the method. "
                + "Include any required leading import declarations, or use fully qualified names. The server retains the original rule and wrongBehavior if omitted. Do not change exercise files, the rule, or expected behavior to make a failing test pass. "
                + "No test executed in these rejected proposals. Each repair will be compiled and executed again; return an empty witnesses array when uncertain.\n\n"
                + "APPROVED SPECIFICATION:\n" + witnessContract(specification) + "\n\nAUTHORITATIVE TEMPLATE OWNERSHIP:\n"
                + renderTemplateOwnership(SpecFidelityCritic.designTemplateStatuses(specification)) + "\n\nPROBE PACKAGE DECLARATIONS (names only):\n"
                + packageDeclarations(solutionSources) + "\n\nUNEXECUTED PROPOSALS AND COMPILER DIAGNOSTICS:\n" + failedSources;
        String response = reviewer.call(CONTRACT_WITNESS_SYSTEM_PROMPT_TEMPLATE, prompt, usageSink, cancelled, CONTRACT_WITNESS_MAX_OUTPUT_TOKENS);
        Map<String, ContractWitness> originals = new java.util.LinkedHashMap<>();
        failures.forEach(outcome -> originals.putIfAbsent(outcome.witness().testName(), outcome.witness()));
        return parseContractWitnesses(response, specification, "", originals).stream().flatMap(repair -> {
            ContractWitness original = originals.get(repair.testName());
            if (original == null || !original.ruleId().equals(repair.ruleId()) || original.code().equals(repair.code())) {
                return java.util.stream.Stream.empty();
            }
            return java.util.stream.Stream.of(new ContractWitness(original.ruleId(), original.testName(), repair.code(), original.wrongBehavior()));
        }).toList();
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

    private static String witnessContract(String specification) {
        // The contract's normative sections define expectations; author-selected risks and implementation rationale would prime an independent reviewer.
        Matcher sections = WITNESS_CONTRACT_SECTION.matcher(specification);
        StringBuilder contract = new StringBuilder();
        boolean hasRules = false;
        while (sections.find()) {
            hasRules |= "Rules".equals(sections.group(1));
            contract.append(sections.group()).append('\n');
        }
        return hasRules ? contract.toString().strip() : specification.strip();
    }

    private static String packageDeclarations(String sources) {
        Matcher packages = PACKAGE_DECLARATION.matcher(JavaSourceText.maskCommentsAndLiterals(sources));
        Set<String> declarations = new java.util.TreeSet<>();
        while (packages.find()) {
            declarations.add(packages.group().strip());
        }
        return String.join("\n", declarations);
    }

    private static String boundedEvidence(String text) {
        String stripped = text.strip();
        return stripped.length() <= MAX_ARTIFACT_EVIDENCE_CHARS ? stripped : stripped.substring(0, MAX_ARTIFACT_EVIDENCE_CHARS);
    }

    private List<ContractWitness> parseContractWitnesses(@Nullable String text, String specificationContract, String testSources) {
        return parseContractWitnesses(text, specificationContract, testSources, Map.of());
    }

    private List<ContractWitness> parseContractWitnesses(@Nullable String text, String specificationContract, String testSources, Map<String, ContractWitness> originals) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Contract-witness authoring returned no response");
        }
        ContractWitnessResponse parsed;
        try {
            parsed = objectMapper.readValue(extractJsonPayload(text), ContractWitnessResponse.class);
        }
        catch (Exception e) {
            throw new IllegalStateException("Contract-witness authoring returned invalid JSON", e);
        }
        if (parsed == null || parsed.witnesses() == null) {
            throw new IllegalStateException("Contract-witness authoring returned no witnesses array");
        }
        List<ContractWitness> witnesses = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (ContractWitnessItem item : parsed.witnesses()) {
            if (item == null || isBlank(item.testName()) || isBlank(item.code())) {
                continue;
            }
            String testName = item.testName().strip();
            String code = item.code().strip();
            ContractWitness original = originals.get(testName);
            if (original == null && (!originals.isEmpty() || isBlank(item.rule()) || isBlank(item.wrongBehavior()))) {
                continue;
            }
            String ruleId = isBlank(item.rule()) ? original.ruleId() : item.rule().strip();
            if (original != null && !original.ruleId().equals(ruleId)) {
                continue;
            }
            String wrongBehavior = original == null ? item.wrongBehavior().strip() : original.wrongBehavior();
            // The name must be the method the code DECLARES, or a build result could never be attributed to it; a witness with no assertion passes against every implementation;
            // and a rule the specification does not contain is an invented requirement.
            if (!declaresMethod(code, testName) || !containsAssertion(code) || !specificationDeclaresRule(specificationContract, ruleId)
                    || usesSuiteLocalHelper(code, testName, testSources) || !seenNames.add(testName)) {
                continue;
            }
            witnesses.add(new ContractWitness(ruleId, testName, code, wrongBehavior));
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

    /** A probe does not copy the suite's class body. An unqualified call to a method declared by that class would therefore make the throwaway probe uncompilable. */
    static boolean usesSuiteLocalHelper(String code, String testName, String testSources) {
        Matcher declarations = TEST_METHOD_DECLARATION.matcher(testSources);
        while (declarations.find()) {
            String methodName = declarations.group(1);
            if (!methodName.equals(testName) && Pattern.compile("(?<![.\\w$])" + Pattern.quote(methodName) + "\\s*\\(").matcher(code).find()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the approved specification actually declares this rule ID, so a witness can never pin a requirement the contract does not state. */
    private static boolean specificationDeclaresRule(String specificationContract, String ruleId) {
        return Pattern.compile("(?<![\\w])" + Pattern.quote(ruleId) + "(?![\\w])").matcher(specificationContract).find();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
