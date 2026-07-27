package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.MAX_ARTIFACT_EVIDENCE_CHARS;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.MAX_REQUIREMENT_CHARS;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.MAX_REVIEW_FINDINGS;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.requireReviewTextSafe;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.truncate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Semantic exercise-quality reviewer for contract risks the differential oracle ({@link DifferentialVerificationService}) is structurally blind to.
 * <p>
 * The oracle proves an exercise is internally <em>consistent</em> (the solution passes its own tests, the template fails them, every [task] binds) but never whether the produced
 * tests cover the requirements the <em>instructor's brief</em> actually names. Real defect classes slip straight through it, for example:
 * <ul>
 * <li><strong>Spec-narrowing:</strong> a "count user-perceived characters incl. emoji and CJK" brief shipped with tests for only precomposed {@code café} and one emoji, with no
 * CJK and no ZWJ/flag-emoji test — internally consistent, but wrong for the real spec.</li>
 * <li><strong>Untested promises:</strong> a stated contract ("must not modify the input", "throw {@code invalid_argument} on zero capacity") with no test asserting it.</li>
 * <li><strong>Grader-mechanics leakage:</strong> grader-internal phrasing ("All functions should raise NotImplementedError in the template file to make the tests fail") leaking
 * into the student-facing problem statement.</li>
 * </ul>
 * It combines deterministic checks with two bounded, tool-free reviews of the complete generated artifact set: one for the student contract and one for the executable test
 * oracle. Contract-risk findings feed the bounded repair loop and, if unresolved, require instructor review after the mechanically valid exercise is saved. Subjective presentation
 * findings remain advisory.
 * <p>
 * For adaptations, the contract pass also receives a compact baseline-to-candidate diff. Unresolved blocking findings are attached to a mechanically valid saved candidate for
 * instructor review.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class SpecFidelityCriticService {

    private static final Logger log = LoggerFactory.getLogger(SpecFidelityCriticService.class);

    private static final int MAX_TECHNIQUE_RULE_FINDINGS = 4;

    /** Bounds all input sent to the reviewer, including the instructor brief, statement, test names, artifacts, and adaptation diff. */
    private static final int MAX_REVIEW_INPUT_CHARS = 120_000;

    private static final String ORACLE_REVIEW_CORRECTION = """

            Your previous verdict cited at least one unknown PRIMARY SOURCE EVIDENCE ID. Adjudicate those claims against the same complete evidence and return a corrected JSON
            verdict. Keep any grounded issue you can cite by its exact P ID; omit every unsupported claim. An empty mutantChecks/uncovered/weakOracle
            verdict is valid when no grounded issue remains—the earlier response already established that the test suite itself was reviewed.
            """;

    private static final String CONTRACT_REVIEW_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/contract_review_system.st";

    private static final String ORACLE_REVIEW_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/oracle_review_system.st";

    /** A complete, evidence-grounded brief-to-spec verdict. Incomplete means the provider returned no trustworthy verdict, so the runner must not freeze the contract. */
    public record SpecificationReview(boolean complete, boolean conceptualReworkRequired, boolean coherentRewriteRequired, List<String> findings, String auditSummary,
            @Nullable String learningFitDirection) {

        public SpecificationReview {
            findings = List.copyOf(findings);
            auditSummary = auditSummary == null ? "" : auditSummary.strip();
            learningFitDirection = learningFitDirection == null ? null : learningFitDirection.strip();
        }

        public SpecificationReview(boolean complete, boolean conceptualReworkRequired, boolean coherentRewriteRequired, List<String> findings, String auditSummary) {
            this(complete, conceptualReworkRequired, coherentRewriteRequired, findings, auditSummary, null);
        }

        public SpecificationReview(boolean complete, List<String> findings) {
            this(complete, false, !findings.isEmpty(), findings, "", null);
        }

        public SpecificationReview(boolean complete, boolean conceptualReworkRequired, List<String> findings) {
            this(complete, conceptualReworkRequired, !findings.isEmpty(), findings, "", null);
        }

        public boolean accepted() {
            return complete && findings.isEmpty();
        }

        public String feedback() {
            if (!complete) {
                return "The specification could not be reviewed against the instructor brief; final review is still required.";
            }
            return "The specification does not yet preserve the instructor brief:\n- " + String.join("\n- ", findings);
        }
    }

    /** A grounded, property-only verdict over three generator-authored concepts. */
    public record ConceptSelectionReview(boolean complete, @Nullable Integer selectedCandidate, List<String> findings, String decisionSummary, String auditSummary) {

        public ConceptSelectionReview {
            findings = List.copyOf(findings);
        }

        public ConceptSelectionReview(boolean complete, @Nullable Integer selectedCandidate, List<String> findings, String decisionSummary) {
            this(complete, selectedCandidate, findings, decisionSummary, decisionSummary);
        }

        public ConceptSelectionReview(boolean complete, @Nullable Integer selectedCandidate, List<String> findings) {
            this(complete, selectedCandidate, findings, "", "");
        }

        public boolean accepted() {
            return complete && selectedCandidate != null && findings.isEmpty();
        }

        public String feedback() {
            if (!complete) {
                return "The concept candidates could not be reviewed reliably.";
            }
            return findings.isEmpty() ? decisionSummary : String.join("\n", findings);
        }
    }

    /**
     * Grader-mechanics phrases that must never appear in the student-facing problem statement. These describe how the grader/template is rigged, not the task; their presence means
     * grader internals leaked into student-facing text. Matched case-insensitively as substrings, so they catch the common phrasings without a brittle full-sentence match.
     */
    private static final List<Pattern> MECHANICS_LEAK_PATTERNS = List.of(compile("notimplementederror"), compile("todo!\\(\\)"), compile("make (?:all )?(?:the )?tests? fail"),
            compile("the template must fail"), compile("exact test name"), compile("reported by the test runner"), compile("generated by the test (?:suite|runner)"),
            compile("in the template file"));

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private final ReviewerClient reviewer;

    private final CriticVerdictParser verdictParser;

    private final ConceptSelectionCritic conceptCritic;

    private final SpecificationReviewCritic specificationCritic;

    private final ContractWitnessAuthor witnessAuthor;

    @Autowired
    public SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper, HyperionPromptTemplateService templateService,
            @Value("${spring.ai.openai.chat.model:}") String configuredModel,
            @Value("${artemis.hyperion.agent.provider-hard-failure-cooldown:PT5M}") Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown,
            @Value("${artemis.hyperion.agent.context-window-tokens:128000}") int contextWindowTokens, Collection<ChatModel> chatModels) {
        this(chatClient, objectMapper, templateService, configuredModel, providerHardFailureCooldown, providerFailureCooldown, contextWindowTokens, configuredOptions(chatModels));
    }

    /** Full-control constructor for tests: the provider options a running server reads from its {@link ChatModel} bean are passed in directly. */
    SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper, HyperionPromptTemplateService templateService, String configuredModel,
            Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown, int contextWindowTokens, @Nullable ChatOptions configuredOptions) {
        this.reviewer = new ReviewerClient(chatClient, templateService, configuredModel, providerHardFailureCooldown, providerFailureCooldown, contextWindowTokens,
                configuredOptions);
        this.verdictParser = new CriticVerdictParser(objectMapper);
        this.conceptCritic = new ConceptSelectionCritic(reviewer, objectMapper);
        this.specificationCritic = new SpecificationReviewCritic(reviewer, objectMapper);
        this.witnessAuthor = new ContractWitnessAuthor(reviewer, objectMapper);
    }

    /**
     * Minimal constructor for callers outside this package that need a critic without a Spring context, such as a test delegating {@link #renderForRetryPrompt} to real behaviour.
     * The prompt template service is stateless apart from its classpath cache, so constructing one here is equivalent to injecting the bean.
     *
     * @param chatClient   the shared chat client, or {@code null} when no provider is configured and every review fails closed
     * @param objectMapper the shared JSON mapper
     */
    public SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper) {
        this(chatClient, objectMapper, new HyperionPromptTemplateService(), "", Duration.ZERO, ProviderFailureCooldown.disabled(), 128_000, (ChatOptions) null);
    }

    private static @Nullable ChatOptions configuredOptions(Collection<ChatModel> chatModels) {
        return chatModels.isEmpty() ? null : chatModels.iterator().next().getOptions();
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
    public SpecificationReview reviewSpecification(String brief, String specification, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return specificationCritic.reviewSpecification(brief, null, specification, usageSink, cancelled);
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
    public SpecificationReview reviewSpecification(String brief, @Nullable String selectedConcept, String specification, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        return specificationCritic.reviewSpecification(brief, selectedConcept, specification, usageSink, cancelled);
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
    public ConceptSelectionReview reviewConceptCandidates(String brief, Map<Integer, String> candidates, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return conceptCritic.reviewConceptCandidates(brief, candidates, usageSink, cancelled);
    }

    /**
     * Authors executable witnesses for rules of the approved specification, so coverage becomes something the server can run rather than something a model asserts.
     *
     * @param specificationContract the approved specification whose {@code ## Rules} rows are the only admissible source of a witness
     * @param testSources           the graded test sources as produced, so the pass targets rules the suite does not already pin
     * @param solutionSources       the reference solution, which fixes the exact API a witness must call
     * @param usageSink             optional token-usage sink
     * @param cancelled             cooperative cancellation signal
     * @return candidate witnesses, still unvalidated; empty whenever the pass is unavailable, cancelled, or does not parse
     */
    public List<ContractWitness> authorContractWitnesses(String specificationContract, String testSources, String solutionSources, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        return witnessAuthor.authorContractWitnesses(specificationContract, testSources, solutionSources, usageSink, cancelled);
    }

    /**
     * Reviews the complete generated artifacts.
     * <p>
     * The gate-frozen SPEC.md snapshot extends the AUTHORITATIVE source for requirement-coverage findings: the spec was written before any code, approved by a mechanical gate,
     * and is instructor-visible — so "no test covers this spec rule" is reportable even when the instructor brief was one line (such findings previously had to abstain, which is
     * why hollow exercises could ship). It also lets the reviewer report contradictions between the stated plan (state ownership, type structure) and the implemented artifacts.
     * The produced statement stays excluded from authority: the final artifact must never authorize its own additions.
     *
     * @param brief            the instructor's source requirements
     * @param problemStatement the produced problem statement
     * @param testNames        the produced test identifiers
     * @param artifacts        the generated repository files grouped by repository type
     * @param usageSink        receives the critic's {@code ChatResponse} from every provider call, so reviewer tokens are counted against the generation run instead of going
     *                             unrecorded; {@code null} skips accounting
     * @param cancelled        polled between provider calls so a running generation can stop mid-review
     * @param previousReport   the immediately preceding attempt's report, for review continuity
     * @param specDocument     the gate-frozen SPEC.md snapshot, or {@code null} when the stage was skipped or never passed its gate
     * @param repairDelta      the bounded artifact changes since the previously reviewed candidate, used to adjudicate whether prior blockers were repaired; {@code null} on the
     *                             first review
     * @param testPlanJson     the exact grading plan consumed by verification and persistence, or {@code null}
     * @return the review report; contract-risk findings request repair and require instructor review if they remain
     */
    public SpecFidelityReport critique(@Nullable String brief, @Nullable String problemStatement, List<String> testNames, Map<RepositoryType, Map<String, String>> artifacts,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled, @Nullable SpecFidelityReport previousReport, @Nullable String specDocument,
            @Nullable String repairDelta, @Nullable String testPlanJson) {
        requireReviewInputsSafe(brief, problemStatement, testNames, artifacts, null);
        List<SpecFidelityReport.Finding> findings = new ArrayList<>(detectMechanicsLeaks(problemStatement));
        if (!hasCompleteArtifactSet(artifacts)) {
            findings.addAll(reviewUnavailable(null, "The generated solution, template, or tests snapshot was missing."));
            return new SpecFidelityReport(List.copyOf(findings));
        }
        findings.addAll(reviewArtifacts(brief, problemStatement, testNames, artifacts, null, usageSink, cancelled, previousReport, specDocument, repairDelta, testPlanJson));
        return new SpecFidelityReport(List.copyOf(findings));
    }

    /**
     * Reviews a mechanically verified adaptation against both its requested scope and the complete generated artifacts.
     *
     * @param brief             the primary source requirements, or {@code null}
     * @param problemStatement  the produced problem statement, or {@code null}
     * @param testNames         the reported gradable test names
     * @param adaptationChanges the rendered summary of what the adaptation changed, reviewed as the requested scope
     * @param artifacts         the produced repository files by repository type
     * @param usageSink         the provider usage sink, or {@code null}
     * @param cancelled         polled between provider calls so a running generation can stop mid-review
     * @param previousReport    the immediately preceding attempt's report threaded into the reviewer prompt for continuity, or {@code null} when there is none
     * @return the full-artifact and adaptation-scope report
     */
    public SpecFidelityReport critiqueAdaptation(@Nullable String brief, @Nullable String problemStatement, List<String> testNames, String adaptationChanges,
            Map<RepositoryType, Map<String, String>> artifacts, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled,
            @Nullable SpecFidelityReport previousReport) {
        requireReviewInputsSafe(brief, problemStatement, testNames, artifacts, adaptationChanges);
        List<SpecFidelityReport.Finding> findings = new ArrayList<>(detectMechanicsLeaks(problemStatement));
        if (!hasCompleteArtifactSet(artifacts)) {
            findings.addAll(reviewUnavailable(adaptationChanges, "The generated solution, template, or tests snapshot was missing."));
            return new SpecFidelityReport(List.copyOf(findings));
        }
        findings.addAll(reviewArtifacts(brief, problemStatement, testNames, artifacts, adaptationChanges, usageSink, cancelled, previousReport, null, null, null));
        return new SpecFidelityReport(List.copyOf(findings));
    }

    private static void requireReviewInputsSafe(@Nullable String brief, @Nullable String problemStatement, List<String> testNames,
            @Nullable Map<RepositoryType, Map<String, String>> artifacts, @Nullable String adaptationChanges) {
        requireReviewTextSafe("critic/brief", brief);
        requireReviewTextSafe("critic/problem-statement.md", problemStatement);
        requireReviewTextSafe("critic/adaptation-changes", adaptationChanges);
        for (int index = 0; index < testNames.size(); index++) {
            requireReviewTextSafe("critic/test-name-" + index, testNames.get(index));
        }
        if (artifacts == null) {
            return;
        }
        for (Map.Entry<RepositoryType, Map<String, String>> repository : artifacts.entrySet()) {
            if (repository.getValue() == null) {
                continue;
            }
            for (Map.Entry<String, String> file : repository.getValue().entrySet()) {
                requireReviewTextSafe("critic/" + repository.getKey().name().toLowerCase(Locale.ROOT) + "/" + file.getKey(), file.getValue());
            }
        }
    }

    private static boolean hasCompleteArtifactSet(Map<RepositoryType, Map<String, String>> artifacts) {
        return artifacts != null && List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS).stream()
                .allMatch(type -> artifacts.get(type) != null && artifacts.get(type).values().stream().anyMatch(content -> content != null && !content.isBlank()));
    }

    private List<SpecFidelityReport.Finding> detectMechanicsLeaks(@Nullable String problemStatement) {
        if (problemStatement == null || problemStatement.isBlank()) {
            return List.of();
        }
        List<SpecFidelityReport.Finding> leaks = new ArrayList<>();
        for (Pattern pattern : MECHANICS_LEAK_PATTERNS) {
            var matcher = pattern.matcher(problemStatement);
            if (matcher.find()) {
                String matched = problemStatement.substring(matcher.start(), Math.min(problemStatement.length(), matcher.start() + MAX_REQUIREMENT_CHARS)).strip();
                leaks.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MECHANICS_LEAK, matched,
                        "This grader/template-mechanics phrasing should not appear in the student-facing problem statement — it describes how the exercise is rigged for grading, "
                                + "not the task. Remove it so students see only the task and its requirements."));
            }
        }
        return leaks;
    }

    /** Runs two bounded, specialized full-artifact review passes and fails closed when either verdict is incomplete. */
    private List<SpecFidelityReport.Finding> reviewArtifacts(@Nullable String brief, @Nullable String problemStatement, List<String> testNames,
            Map<RepositoryType, Map<String, String>> artifacts, @Nullable String adaptationChanges, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled,
            @Nullable SpecFidelityReport previousReport, @Nullable String specDocument, @Nullable String repairDelta, @Nullable String testPlanJson) {
        String effectiveBrief = brief == null ? "" : brief.strip();
        if (adaptationChanges != null && adaptationChanges.isBlank()) {
            String requestedChange = effectiveBrief.isBlank() ? "the requested adaptation" : truncate(effectiveBrief);
            return List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING, requestedChange,
                    "The candidate is unchanged, so it cannot implement the requested adaptation."));
        }
        if (!reviewer.configured()) {
            return reviewUnavailable(adaptationChanges, "No AI reviewer is configured.");
        }
        ArtifactEvidence evidence = renderArtifactEvidence(artifacts);
        if (evidence.truncated()) {
            return reviewUnavailable(adaptationChanges, "The generated artifact set exceeded the bounded review input.");
        }
        // The gate-frozen spec is authoritative for downstream implementation and coverage, not for expanding the instructor's scope. Keeping the two sources visibly separate
        // prevents a model-authored SPEC from laundering its own invented requirement into "primary source" authority.
        String specificationContract = specDocument == null || specDocument.isBlank() ? "" : specDocument.strip();
        String authoritativeSource = specificationContract.isBlank() ? effectiveBrief : effectiveBrief + "\n\n" + specificationContract;
        String userPrompt = renderUserPrompt(effectiveBrief, specificationContract, problemStatement, testNames, evidence.text(), adaptationChanges, previousReport, repairDelta,
                testPlanJson) + "\n\nPRIMARY SOURCE EVIDENCE IDS FOR ORACLE ONLY:\n" + EvidenceSource.from("P", authoritativeSource).promptText();
        // Contradiction and hidden-requirement findings may quote the frozen contract, while invented-requirement findings must quote an artifact the repair loop can still edit.
        // Keeping these grounding sources separate prevents a frozen specification defect from becoming an impossible downstream repair while still catching unsupported promises
        // introduced by the statement, solution, template, or tests.
        String planEvidence = testPlanJson == null || testPlanJson.isBlank() ? "" : "\n\n" + testPlanJson.strip();
        String contractGroundingSource = (problemStatement == null || problemStatement.isBlank() ? authoritativeSource : authoritativeSource + "\n\n" + problemStatement.strip())
                + planEvidence + "\n\n" + evidence.text();
        String repairableDownstreamSource = (problemStatement == null || problemStatement.isBlank() ? "" : problemStatement.strip() + "\n\n") + evidence.text() + planEvidence;
        if (userPrompt.length() > MAX_REVIEW_INPUT_CHARS) {
            return reviewUnavailable(adaptationChanges, "The complete review input exceeded its bounded size.");
        }
        boolean expectExampleChecks = problemStatement != null && problemStatement.toLowerCase(Locale.ROOT).contains("example");
        boolean expectApiChecks = artifacts.getOrDefault(RepositoryType.SOLUTION, Map.of()).values().stream().filter(Objects::nonNull)
                .anyMatch(content -> content.contains("public "));
        boolean expectTestChecks = !testNames.isEmpty();
        Map<String, String> templateStatuses = designTemplateStatuses(specificationContract);
        if (cancelled.getAsBoolean()) {
            return reviewUnavailable(adaptationChanges, "The full-artifact review was cancelled before both review passes completed.");
        }
        List<SpecFidelityReport.Finding> contractFindings = callReviewerSafely(CriticVerdictParser.ReviewPass.CONTRACT, CONTRACT_REVIEW_SYSTEM_PROMPT_TEMPLATE, userPrompt,
                adaptationChanges != null, contractGroundingSource, repairableDownstreamSource, expectExampleChecks, expectApiChecks, expectTestChecks, false, templateStatuses,
                usageSink);
        if (cancelled.getAsBoolean()) {
            return reviewUnavailable(adaptationChanges, "The full-artifact review was cancelled before both review passes completed.");
        }
        List<SpecFidelityReport.Finding> oracleFindings = callReviewerSafely(CriticVerdictParser.ReviewPass.ORACLE, ORACLE_REVIEW_SYSTEM_PROMPT_TEMPLATE, userPrompt, false,
                authoritativeSource, authoritativeSource, false, false, false, expectTestChecks, Map.of(), usageSink);
        if (cancelled.getAsBoolean()) {
            return reviewUnavailable(adaptationChanges, "The full-artifact review was cancelled before both review passes completed.");
        }
        if (!cancelled.getAsBoolean() && oracleFindings != null && CriticVerdictParser.hasUngroundedOracleReview(oracleFindings)
                && userPrompt.length() + ORACLE_REVIEW_CORRECTION.length() <= MAX_REVIEW_INPUT_CHARS) {
            List<SpecFidelityReport.Finding> correctedOracleFindings = callReviewerSafely(CriticVerdictParser.ReviewPass.ORACLE, ORACLE_REVIEW_SYSTEM_PROMPT_TEMPLATE,
                    userPrompt + ORACLE_REVIEW_CORRECTION, false, authoritativeSource, authoritativeSource, false, false, false, false, Map.of(), usageSink);
            if (correctedOracleFindings != null && !CriticVerdictParser.hasUngroundedOracleReview(correctedOracleFindings)) {
                // The correction is a complete verdict, not an addendum. Substring grounding proves provenance only; retaining initially grounded claims would make a corrected
                // response unable to retract a semantically false claim.
                oracleFindings = correctedOracleFindings;
            }
        }
        contractFindings = contractFindings == null ? reviewUnavailable(adaptationChanges, "The contract reviewer returned no verdict.") : contractFindings;
        oracleFindings = oracleFindings == null ? reviewUnavailable(adaptationChanges, "The test-oracle reviewer returned no verdict.") : oracleFindings;
        Map<String, SpecFidelityReport.Finding> unique = new LinkedHashMap<>();
        // Preserve blockers from both specialized passes before advisories, while alternating each pass so neither can consume the shared cap alone.
        addInterleaved(unique, contractFindings.stream().filter(SpecFidelityReport.Finding::isBlocking).toList(),
                oracleFindings.stream().filter(SpecFidelityReport.Finding::isBlocking).toList());
        addInterleaved(unique, contractFindings.stream().filter(finding -> !finding.isBlocking()).toList(),
                oracleFindings.stream().filter(finding -> !finding.isBlocking()).toList());
        return List.copyOf(unique.values());
    }

    private static void addInterleaved(Map<String, SpecFidelityReport.Finding> unique, List<SpecFidelityReport.Finding> first, List<SpecFidelityReport.Finding> second) {
        for (int index = 0; unique.size() < MAX_REVIEW_FINDINGS && (index < first.size() || index < second.size()); index++) {
            if (index < first.size()) {
                addUniqueFinding(unique, first.get(index));
            }
            if (index < second.size() && unique.size() < MAX_REVIEW_FINDINGS) {
                addUniqueFinding(unique, second.get(index));
            }
        }
    }

    private static void addUniqueFinding(Map<String, SpecFidelityReport.Finding> unique, SpecFidelityReport.Finding finding) {
        if (unique.size() >= MAX_REVIEW_FINDINGS) {
            return;
        }
        String key = finding.kind() + "\n" + finding.requirement();
        if (finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE || finding.kind() == SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE) {
            key += "\n" + finding.detail();
        }
        unique.putIfAbsent(key, finding);
    }

    private @Nullable List<SpecFidelityReport.Finding> callReviewerSafely(CriticVerdictParser.ReviewPass pass, String systemPromptTemplate, String userPrompt,
            boolean requireScopeVerdict, String authoritativeSource, String repairableDownstreamSource, boolean expectExampleChecks, boolean expectApiChecks,
            boolean expectTemplateChecks, boolean expectMutantChecks, Map<String, String> templateStatuses, @Nullable Consumer<ChatResponse> usageSink) {
        try {
            return callReviewer(pass, systemPromptTemplate, userPrompt, requireScopeVerdict, authoritativeSource, repairableDownstreamSource, expectExampleChecks, expectApiChecks,
                    expectTemplateChecks, expectMutantChecks, templateStatuses, usageSink);
        }
        catch (RuntimeException e) {
            log.warn("{} exercise review failed: {}", pass, e.getMessage());
            return null;
        }
    }

    private @Nullable List<SpecFidelityReport.Finding> callReviewer(CriticVerdictParser.ReviewPass pass, String systemPromptTemplate, String userPrompt,
            boolean requireScopeVerdict, String authoritativeSource, String repairableDownstreamSource, boolean expectExampleChecks, boolean expectApiChecks,
            boolean expectTemplateChecks, boolean expectMutantChecks, Map<String, String> templateStatuses, @Nullable Consumer<ChatResponse> usageSink) {
        String text = reviewer.call(systemPromptTemplate, userPrompt, usageSink);
        return text == null || text.isBlank() ? null
                : verdictParser.parseCritique(text, pass, requireScopeVerdict, authoritativeSource, repairableDownstreamSource, expectExampleChecks, expectApiChecks,
                        expectTemplateChecks, expectMutantChecks, templateStatuses);
    }

    private static List<SpecFidelityReport.Finding> reviewUnavailable(@Nullable String adaptationChanges, String detail) {
        return (adaptationChanges == null ? SpecFidelityReport.qualityReviewUnavailable(detail) : SpecFidelityReport.adaptationScopeUnavailable(detail)).findings();
    }

    private static String renderUserPrompt(String brief, String specificationContract, @Nullable String problemStatement, List<String> testNames, String artifactEvidence,
            @Nullable String adaptationChanges, @Nullable SpecFidelityReport previousReport, @Nullable String repairDelta, @Nullable String testPlanJson) {
        String tests = testNames.isEmpty() ? "(no tests were produced)" : String.join("\n", testNames);
        String changes = adaptationChanges == null ? "" : "\n\nADAPTATION CHANGES (baseline to candidate):\n" + (adaptationChanges.isBlank() ? "(no changes)" : adaptationChanges);
        String repairChanges = repairDelta == null ? ""
                : "\n\nREPAIR DELTA (previous mechanically verified candidate to current candidate):\n" + (repairDelta.isBlank() ? "(no artifact changes)" : repairDelta);
        return "INSTRUCTOR BRIEF (authoritative for requested scope and explicit boundaries):\n" + brief
                + "\n\nAPPROVED SPECIFICATION CONTRACT (binding authority for coherent operational choices within that scope):\n"
                + (specificationContract.isBlank() ? "(none)" : specificationContract) + "\n\nPRODUCED PROBLEM STATEMENT:\n"
                + (problemStatement == null || problemStatement.isBlank() ? "(empty)" : problemStatement.strip()) + "\n\nTEST NAMES (navigation aid only; not coverage evidence) ("
                + testNames.size() + "):\n" + tests
                + "\n\nGENERATED TEST PLAN (mapping evidence only; repeated weights are seam tiers divided evenly across persisted cases; assertions remain authoritative):\n"
                + (testPlanJson == null || testPlanJson.isBlank() ? "(none)" : testPlanJson.strip()) + "\n\nMECHANICALLY VERIFIED CANDIDATE ARTIFACTS:\n" + artifactEvidence
                + changes + repairChanges + renderPreviousReviewSection(previousReport)
                + "\n\nDo not treat test names or comments as proof. Return the complete JSON verdict specified by the system prompt.";
    }

    /**
     * Renders the continuity section so a repair attempt is reviewed against its history instead of re-rolling a fresh critique: every finding from the immediately preceding
     * attempt is listed verbatim, and the reviewer is told to re-verify each before reporting anything new. Empty when there is no prior report to carry forward.
     */
    private static String renderPreviousReviewSection(@Nullable SpecFidelityReport previousReport) {
        if (previousReport == null || previousReport.findings().isEmpty()) {
            return "";
        }
        StringBuilder section = new StringBuilder("\n\nPREVIOUS REVIEW HYPOTHESES (verdict on the previous candidate; not facts about the current artifacts):");
        for (SpecFidelityReport.Finding finding : previousReport.findings()) {
            section.append("\n- [").append(finding.kind()).append("] ").append(finding.requirement()).append(": ").append(finding.detail());
        }
        section.append(
                "\nFirst adjudicate each item against the current artifacts and, when present, the REPAIR DELTA: omit it if resolved, or repeat it with fresh current evidence if still open. For a "
                        + "previously named mutant, explicitly decide whether the added/changed assertion now kills that same mutant before searching for another. Do not "
                        + "invent alternate versions of resolved findings. Then perform the complete review of the current candidate. Report any fresh high-confidence blocker with current evidence, "
                        + "including a defect overlooked previously; do not suppress a real defect merely because its artifact was unchanged.");
        return section.toString();
    }

    private static Map<String, String> designTemplateStatuses(String specification) {
        Map<String, String> statuses = new LinkedHashMap<>();
        boolean inDesign = false;
        for (String rawLine : specification.lines().toList()) {
            String line = rawLine.strip();
            if (line.equals("## Design")) {
                inDesign = true;
                continue;
            }
            if (inDesign && line.startsWith("## ")) {
                break;
            }
            if (!inDesign || !line.startsWith("|")) {
                continue;
            }
            String[] cells = line.substring(1, line.length() - (line.endsWith("|") ? 1 : 0)).split("\\|", -1);
            if (cells.length < 3) {
                continue;
            }
            String status = cells[cells.length - 1].strip().toLowerCase(Locale.ROOT);
            if (!status.equals("given") && !status.equals("stubbed") && !status.equals("student-creates")) {
                continue;
            }
            String type = cells[0].strip().replace("`", "");
            if (!type.isBlank()) {
                statuses.put(type, status);
            }
        }
        return Map.copyOf(statuses);
    }

    private record ArtifactEvidence(String text, boolean truncated) {
    }

    private static ArtifactEvidence renderArtifactEvidence(Map<RepositoryType, Map<String, String>> artifacts) {
        StringBuilder evidence = new StringBuilder();
        for (RepositoryType type : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            for (Map.Entry<String, String> file : new TreeMap<>(artifacts.getOrDefault(type, Map.of())).entrySet()) {
                String content = file.getValue();
                if (content == null) {
                    continue;
                }
                String header = "\n--- " + type.name() + ": " + file.getKey() + " ---\n";
                if (evidence.length() + header.length() + content.length() > MAX_ARTIFACT_EVIDENCE_CHARS) {
                    return new ArtifactEvidence(evidence.toString(), true);
                }
                evidence.append(header).append(content).append('\n');
            }
        }
        return new ArtifactEvidence(evidence.toString(), false);
    }

    /**
     * Languages whose default assertion failure does not name the behaviour — a bare {@code assertEquals(600L, actual)} reports only "expected 600 but was 500", so a human failure
     * message is the failing student's only diagnostic. Other frameworks self-describe (Go {@code t.Errorf} format strings, Jest's auto-diff plus descriptive {@code it()} names,
     * Catch2/gtest expression expansion), so they are deliberately out of scope and fail open.
     */
    private static final Set<ProgrammingLanguage> MESSAGE_SENSITIVE_LANGUAGES = Set.of(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN);

    /** A JVM assertion or {@code fail()} call. */
    private static final Pattern JVM_ASSERTION_CALL = Pattern.compile("\\b(?:assert\\w*|fail)\\s*\\(");

    /**
     * A JVM assertion/{@code fail} call carrying a string-literal argument somewhere in the same statement. Used only at the file level to decide a file is not wholly
     * message-less:
     * a matched string may be a real failure message ({@code fail("...")}) or a string expected-value ({@code assertEquals("olleh", ...)}); both make the file "not wholly bare",
     * so
     * the check conservatively under-fires rather than risk a false advisory. The {@code [^;{}]} bound keeps the scan inside one statement and out of a braced lambda body.
     */
    private static final Pattern JVM_ASSERTION_WITH_STRING = Pattern.compile("\\b(?:assert\\w*|fail)\\s*\\([^;{}]*\"");

    /**
     * Flags rules that mandate an implementation technique, which behavioural tests cannot observe.
     * <p>
     * A rule such as "the implementation must be recursive" or "must use a Stream pipeline" reads like a graded requirement and is not one: no assertion over the public API can
     * tell a recursive implementation from an iterative one that returns the same values (see {@link SpecFidelityReport.Kind#UNENFORCEABLE_TECHNIQUE_RULE}).
     * <p>
     * Deterministic and deliberately narrow: only mandates naming a control-flow or API technique match. Across every specification generated so far this fires on exactly the
     * three whose brief asked for a technique and on nothing else, and it must stay that tight — a rule like "must delegate to the injected collaborator" IS observable through a
     * recording fake and must not be flagged.
     *
     * @param specificationContract the approved specification
     * @return one advisory finding per distinct technique mandate, or empty when the contract states none
     */
    public List<SpecFidelityReport.Finding> detectUnenforceableTechniqueRules(@Nullable String specificationContract) {
        if (specificationContract == null || specificationContract.isBlank()) {
            return List.of();
        }
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // One definition, shared with the specification gate that rejects these outright: this pass is the backstop for a mandate that reaches a later stage anyway.
        for (String mandate : ExerciseIntegrityGate.techniqueMandatesInRules(specificationContract)) {
            if (findings.size() >= MAX_TECHNIQUE_RULE_FINDINGS || !seen.add(mandate.toLowerCase(Locale.ROOT))) {
                continue;
            }
            findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, mandate,
                    "The specification requires this technique, but the graded tests observe behaviour through the public API and cannot see how a result was produced: a "
                            + "student who ignores it and returns the same values scores full marks. Nothing downstream can repair that. Either accept it and review the "
                            + "technique by hand, or design the task so the objective shows up in the observable result."));
        }
        return List.copyOf(findings);
    }

    /**
     * Deterministic, model-free advisory check: flags a graded JVM test file whose assertions carry no human-readable failure message, so a failing student sees only the raw value
     * mismatch with no hint at which behaviour broke. File-level by design — it flags only a wholly message-less file, which sidesteps per-assertion argument parsing and keeps
     * false
     * positives near zero (a mixed file, or any framework that self-describes, is left alone). Advisory only; it never affects acceptance.
     *
     * @param language           the exercise programming language
     * @param producedTestsFiles the read-back tests repository (repository-relative path -> content)
     * @return one finding per wholly-message-less test file, capped at {@link #MAX_REVIEW_FINDINGS}; empty for non-JVM languages or when every test file already messages
     */
    public List<SpecFidelityReport.Finding> detectMessagelessAssertions(@Nullable ProgrammingLanguage language, @Nullable Map<String, String> producedTestsFiles) {
        if (language == null || !MESSAGE_SENSITIVE_LANGUAGES.contains(language) || producedTestsFiles == null || producedTestsFiles.isEmpty()) {
            return List.of();
        }
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        for (Map.Entry<String, String> entry : producedTestsFiles.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            if (content == null || !(path.endsWith(".java") || path.endsWith(".kt"))) {
                continue;
            }
            String code = stripCommentsForAssertionScan(content);
            if (!JVM_ASSERTION_CALL.matcher(code).find()) {
                continue; // not a file with assertions (a helper/fixture) -> nothing to flag
            }
            if (!JVM_ASSERTION_WITH_STRING.matcher(code).find()) {
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_FAILURE_MESSAGE, path,
                        "Every assertion in this graded test file is bare (no message argument), so a failing student sees only the raw value mismatch with no hint at which behaviour "
                                + "broke."));
                if (findings.size() >= MAX_REVIEW_FINDINGS) {
                    break;
                }
            }
        }
        return findings;
    }

    /** Strips {@code //} line and {@code /* *}{@code /} block comments so a commented-out assertion or message cannot skew the message-coverage scan. */
    private static String stripCommentsForAssertionScan(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", "");
    }

    /**
     * Renders blocking contract-risk findings and optional presentation improvements for the next bounded repair attempt.
     *
     * @param report the spec-fidelity report (its findings drive the rendered guidance)
     * @return a retry-prompt fragment, or an empty string when there are no findings
     */
    public String renderForRetryPrompt(SpecFidelityReport report) {
        if (!report.hasFindings()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (report.hasBlockingFindings()) {
            builder.append("\n\nExercise-quality issues that you must fix before saving, in priority order — requirements-level findings first; only then scaffold polish:");
            // Requirements-level findings (contradictions, invented/hidden requirements, weak oracles) decide whether the exercise is RIGHT; TEMPLATE_QUALITY_GAP findings only
            // polish the scaffold. Rendering them in that order stops a repair attempt from spending its turns on placement nits while a design defect survives.
            report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking)
                    .sorted(Comparator.comparing(finding -> finding.kind() == SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP ? 1 : 0))
                    .forEach(finding -> appendRetryFinding(builder, finding));
        }
        if (report.findings().stream().anyMatch(finding -> !finding.isBlocking())) {
            builder.append(report.hasBlockingFindings() ? "\n\nOptional quality improvements (do not expand the requested adaptation to address these):"
                    : "\n\nAdditionally, a spec-fidelity review found these optional quality improvements against the instructor's brief:");
            report.findings().stream().filter(finding -> !finding.isBlocking()).forEach(finding -> appendRetryFinding(builder, finding));
        }
        return builder.toString();
    }

    private static void appendRetryFinding(StringBuilder builder, SpecFidelityReport.Finding finding) {
        switch (finding.kind()) {
            case MECHANICS_LEAK -> builder.append("\n- The problem statement contains grader-mechanics phrasing that students should not see (\"").append(finding.requirement())
                    .append("\"). Remove it from the student-facing problem statement. ").append(finding.detail());
            case MISSING_WORKED_EXAMPLE -> builder.append("\n- This important behaviour may benefit from a concrete worked example: \"").append(finding.requirement())
                    .append("\". Consider one representative input->outcome example in a code block, table, or precise prose. ").append(finding.detail());
            case INVENTED_REQUIREMENT -> builder.append("\n- The problem statement adds a graded requirement the brief did not ask for: \"").append(finding.requirement())
                    .append("\". Remove it (and any test enforcing it) unless the brief implies it, so the exercise matches the brief. ").append(finding.detail());
            case MISSING_FAILURE_MESSAGE -> builder.append("\n- The graded test file ").append(finding.requirement())
                    .append(" asserts without a human-readable failure message, so a failing student sees only \"expected X but was Y\". Add a short message to each assertion "
                            + "naming the behaviour that broke, e.g. assertEquals(expected, actual, \"calculateSize must sum every file regardless of nesting depth\").");
            case UNCOVERED_REQUIREMENT -> builder.append("\n- No test covers this student-owned requirement from the approved grading seams: \"").append(finding.requirement())
                    .append("\". Confirm its Design owner is stubbed or student-creates before adding a test. A given support type is already provided: inspect and repair both supplied copies "
                            + "directly if they violate the contract, but never add a gradable task or damage the starter merely to make a given-code test fail. Otherwise add the smallest "
                            + "discriminating assertion and reconcile its existing student-work seam. The new assertion must FAIL on the template and PASS on the solution — that is what makes it "
                            + "gradable. If the template stub happens to satisfy it (for example a stub that already returns the expected value such as 0, an empty collection, or null), the "
                            + "differential will reject the whole candidate; change that stub to a clearly-unimplemented placeholder (e.g. throw the not-implemented sentinel) so the new test is "
                            + "genuinely discriminating instead of removing the test. ")
                    .append(finding.detail());
            case UNREQUESTED_ADAPTATION_CHANGE -> builder.append("\n- Unrequested adaptation change: \"").append(finding.requirement()).append("\". ").append(finding.detail());
            case REQUESTED_ADAPTATION_CHANGE_MISSING ->
                builder.append("\n- Requested adaptation change missing or incomplete: \"").append(finding.requirement()).append("\". ").append(finding.detail());
            case ADAPTATION_SCOPE_REVIEW_UNAVAILABLE -> builder.append("\n- The adaptation-scope review was unavailable. Re-check every changed file against the feedback "
                    + "and preserve all unrelated content before submitting again.");
            case CONTRACT_CONTRADICTION ->
                builder.append("\n- Resolve this cross-artifact contract contradiction: \"").append(finding.requirement()).append("\". ").append(finding.detail());
            case HIDDEN_GRADED_REQUIREMENT -> builder.append("\n- Make this graded requirement discoverable in the statement and template, or remove the assertion: \"")
                    .append(finding.requirement()).append("\". ").append(finding.detail());
            case WEAK_TEST_ORACLE -> builder.append("\n- Strengthen the tests so this specific wrong implementation fails: \"").append(finding.requirement()).append("\". ")
                    .append(finding.detail())
                    .append(" Change the test setup and assertion first; keep the verified solution unchanged unless the new witness proves it wrong. For delegation, inject a "
                            + "test-controlled fake or recording collaborator that returns one unique sentinel and records the exact input, then assert return propagation and forwarding. "
                            + "Never compare results from two independent production calls or add production caching/state to satisfy such a comparison.");
            case TEMPLATE_QUALITY_GAP ->
                builder.append("\n- Align the student task and starter scaffold for: \"").append(finding.requirement()).append("\". ").append(finding.detail());
            case QUALITY_REVIEW_UNAVAILABLE -> builder.append("\n- The full-artifact quality review was unavailable; do not claim semantic quality without a complete review.");
        }
    }
}
