package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.MAX_ARTIFACT_EVIDENCE_CHARS;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.requireReviewTextSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Independent authoring pass for complete semantic mutants and the counterexamples that should kill them. It deliberately receives no graded test bodies: the environment, not
 * model inspection of the suite, proves whether the current tests miss the proposed defect.
 */
class SemanticMutantAuthor {

    private static final Logger log = LoggerFactory.getLogger(SemanticMutantAuthor.class);

    private static final String SYSTEM_PROMPT = "/prompts/hyperion/critic/semantic_mutant_system.st";

    private static final int MAX_OUTPUT_TOKENS = 16_384;

    private static final int MAX_MUTANTS = 2;

    private static final Pattern ASSERTION_CALL = Pattern.compile("\\b(assert\\w*|verify|expect(That)?)\\s*\\(");

    private static final Pattern FULLY_QUALIFIED_TEST = Pattern.compile("@\\s*org\\.junit\\.jupiter\\.api\\.Test\\b");

    private static final Pattern FULLY_QUALIFIED_ASSERTION = Pattern.compile("\\borg\\.junit\\.jupiter\\.api\\.Assertions\\.assert\\w*\\s*\\(");

    private static final Pattern RULE_ID = Pattern.compile("R[1-9]\\d*");

    private record Response(@Nullable List<Item> mutants) {
    }

    private record Item(@Nullable String rule, @Nullable String solutionPath, @Nullable String mutantSource, @Nullable String testName, @Nullable String testCode,
            @Nullable String misconception, @Nullable String target, @Nullable String targetHypothesis) {
    }

    private final ReviewerClient reviewer;

    private final ObjectMapper objectMapper;

    SemanticMutantAuthor(ReviewerClient reviewer, ObjectMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    List<SemanticMutant> author(String specification, Map<String, String> solutionFiles, List<SpecFidelityReport.Finding> reviewTargets, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        requireReviewTextSafe("semantic-mutant/specification", specification);
        solutionFiles.forEach((path, source) -> {
            requireReviewTextSafe("semantic-mutant/path", path);
            requireReviewTextSafe("semantic-mutant/solution/" + path, source);
        });
        if (cancelled.getAsBoolean() || !reviewer.configured() || specification.isBlank() || solutionFiles.isEmpty()) {
            return List.of();
        }
        Map<String, String> visibleSolutionFiles = boundedSolutionFiles(solutionFiles);
        String renderedSolution = visibleSolutionFiles.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "FILE " + entry.getKey() + "\n" + entry.getValue().strip()).collect(Collectors.joining("\n\n"));
        if (renderedSolution.isBlank()) {
            return List.of();
        }
        List<SpecFidelityReport.Finding> visibleTargets = reviewTargets(reviewTargets);
        String targets = renderReviewTargets(visibleTargets);
        if (!targets.isBlank()) {
            requireReviewTextSafe("semantic-mutant/review-targets", targets);
        }
        String targetPrompt = targets.isBlank() ? ""
                : "\n\nINDEPENDENT REVIEW TARGETS (source-backed risk hypotheses, not new authority):\n" + targets
                        + "\nPrioritize semantically distinct proposals for these risks when the approved specification entails them. Do not repeat an already-covered "
                        + "behavior merely because it is easier to mutate.";
        String prompt = "APPROVED SPECIFICATION (sole rule authority):\n" + specification.strip() + "\n\nPRISTINE REFERENCE SOLUTION:\n" + renderedSolution + targetPrompt;
        try {
            return parse(reviewer.call(SYSTEM_PROMPT, prompt, usageSink, MAX_OUTPUT_TOKENS), specification, visibleSolutionFiles, visibleTargets);
        }
        catch (RuntimeException exception) {
            log.warn("Semantic-mutant authoring failed: {}", exception.getMessage());
            return List.of();
        }
    }

    static String renderReviewTargets(List<SpecFidelityReport.Finding> reviewTargets) {
        List<SpecFidelityReport.Finding> targets = reviewTargets(reviewTargets);
        return java.util.stream.IntStream.range(0, targets.size())
                .mapToObj(index -> "- [T" + (index + 1) + "] " + targets.get(index).requirement() + ": " + targets.get(index).detail()).collect(Collectors.joining("\n"));
    }

    List<SemanticMutant> parse(@Nullable String responseText, String specification, Map<String, String> solutionFiles) {
        return parse(responseText, specification, solutionFiles, List.of());
    }

    List<SemanticMutant> parse(@Nullable String responseText, String specification, Map<String, String> solutionFiles, List<SpecFidelityReport.Finding> reviewTargets) {
        if (responseText == null || responseText.isBlank()) {
            return List.of();
        }
        Response response;
        try {
            response = objectMapper.readValue(extractJsonPayload(responseText), Response.class);
        }
        catch (Exception exception) {
            return List.of();
        }
        if (response == null || response.mutants() == null) {
            return List.of();
        }
        List<SemanticMutant> accepted = new ArrayList<>();
        Set<String> seenTests = new HashSet<>();
        for (Item item : response.mutants()) {
            if (!valid(item, specification, solutionFiles, seenTests)) {
                continue;
            }
            String path = item.solutionPath().strip();
            String original = solutionFiles.get(path);
            ContractWitness counterexample = new ContractWitness(item.rule().strip(), item.testName().strip(), item.testCode().strip(), item.misconception().strip());
            accepted.add(new SemanticMutant(item.rule().strip(), path, original, item.mutantSource().strip(), counterexample, reviewTarget(item, reviewTargets)));
            if (accepted.size() == MAX_MUTANTS) {
                break;
            }
        }
        return List.copyOf(accepted);
    }

    private static List<SpecFidelityReport.Finding> reviewTargets(List<SpecFidelityReport.Finding> findings) {
        return findings.stream().filter(finding -> finding.kind() == SpecFidelityReport.Kind.WEAK_TEST_ORACLE || finding.kind() == SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT)
                .limit(4).toList();
    }

    private static SpecFidelityReport.@Nullable Finding reviewTarget(Item item, List<SpecFidelityReport.Finding> targets) {
        if (item.target() == null || item.targetHypothesis() == null || !item.target().matches("T[1-9]\\d*")) {
            return null;
        }
        int index = Integer.parseInt(item.target().substring(1)) - 1;
        if (index < 0 || index >= targets.size()) {
            return null;
        }
        SpecFidelityReport.Finding target = targets.get(index);
        String exactHypothesis = target.requirement().strip() + ": " + target.detail().strip();
        return item.targetHypothesis().strip().equals(exactHypothesis) ? target : null;
    }

    /** Selects deterministic whole source files only; a truncated Java file is misleading context and cannot produce a trustworthy complete replacement. */
    static Map<String, String> boundedSolutionFiles(Map<String, String> solutionFiles) {
        Map<String, String> visible = new LinkedHashMap<>();
        int used = 0;
        for (Map.Entry<String, String> entry : solutionFiles.entrySet().stream().filter(candidate -> candidate.getValue() != null && !candidate.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey()).toList()) {
            String block = "FILE " + entry.getKey() + "\n" + entry.getValue().strip();
            int separator = visible.isEmpty() ? 0 : 2;
            if (used + separator + block.length() <= MAX_ARTIFACT_EVIDENCE_CHARS) {
                visible.put(entry.getKey(), entry.getValue());
                used += separator + block.length();
            }
        }
        return Map.copyOf(visible);
    }

    private static boolean valid(@Nullable Item item, String specification, Map<String, String> solutionFiles, Set<String> seenTests) {
        if (item == null || blank(item.rule()) || blank(item.solutionPath()) || blank(item.mutantSource()) || blank(item.testName()) || blank(item.testCode())
                || blank(item.misconception())) {
            return false;
        }
        String path = item.solutionPath().strip();
        String rule = item.rule().strip();
        String testName = item.testName().strip();
        String testCode = item.testCode().strip();
        String mutant = item.mutantSource().strip();
        String original = solutionFiles.get(path);
        return safeJavaPath(path) && original != null && !original.isBlank() && !mutant.equals(original.strip()) && completeJavaSource(path, mutant)
                && declaresTestMethod(testCode, testName) && FULLY_QUALIFIED_TEST.matcher(testCode).find() && FULLY_QUALIFIED_ASSERTION.matcher(testCode).find()
                && ASSERTION_CALL.matcher(testCode).find() && specificationDeclaresRule(specification, rule) && seenTests.add(testName);
    }

    private static boolean safeJavaPath(String path) {
        return path.endsWith(".java") && !path.startsWith("/") && !path.contains("\\") && !Pattern.compile("(^|/)\\.\\.?(/|$)").matcher(path).find();
    }

    private static boolean completeJavaSource(String path, String source) {
        String fileName = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
        return !source.contains("```") && Pattern.compile("\\b(class|record|interface|enum)\\s+" + Pattern.quote(fileName) + "\\b").matcher(source).find();
    }

    private static boolean declaresTestMethod(String code, String testName) {
        return Pattern.compile("\\bvoid\\s+" + Pattern.quote(testName) + "\\s*\\(").matcher(code).find();
    }

    private static boolean specificationDeclaresRule(String specification, String rule) {
        return RULE_ID.matcher(rule).matches() && Pattern.compile("(?<![\\w])" + Pattern.quote(rule) + "(?![\\w])").matcher(specification).find();
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
