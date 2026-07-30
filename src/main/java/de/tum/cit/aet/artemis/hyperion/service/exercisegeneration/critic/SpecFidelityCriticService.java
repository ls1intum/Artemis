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
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentCheckpointManager;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Semantic exercise-quality reviewer for the contract risks the differential oracle ({@link DifferentialVerificationService}) is structurally blind to.
 * <p>
 * The oracle proves an exercise is internally <em>consistent</em> — the solution passes its own tests, the template fails them, every [task] binds — but never whether the
 * produced tests cover what the <em>instructor's brief</em> asked for. Three defect classes pass it unchallenged:
 * <ul>
 * <li><strong>Spec-narrowing:</strong> the tests cover a strictly smaller input space than the brief names, so the exercise is consistent but grades the wrong contract.</li>
 * <li><strong>Untested promises:</strong> the statement states a contract ("must not modify the input", "throws on zero capacity") that no assertion checks.</li>
 * <li><strong>Grader-mechanics leakage:</strong> phrasing describing how the template is rigged for grading reaches the student-facing statement.</li>
 * </ul>
 * Deterministic checks are combined with two bounded, tool-free reviews of the complete artifact set: one for the student contract, one for the executable test oracle. For an
 * adaptation the contract pass also receives a baseline-to-candidate diff. Contract-risk findings drive the bounded repair loop and, if unresolved, are attached to the saved
 * candidate for instructor review; presentation findings stay advisory.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class SpecFidelityCriticService {

    private static final Logger log = LoggerFactory.getLogger(SpecFidelityCriticService.class);

    private static final int MAX_TECHNIQUE_RULE_FINDINGS = 4;

    /** Bounds all input sent to the reviewer, including the instructor brief, statement, test names, artifacts, and adaptation diff. */
    private static final int MAX_REVIEW_INPUT_CHARS = 120_000;

    private static final int MAX_TEMPLATE_FAILURE_EVIDENCE = 8;

    private static final String ORACLE_REVIEW_CORRECTION = """

            Your previous verdict was incomplete, malformed, or cited an unknown PRIMARY SOURCE EVIDENCE ID. Re-evaluate the same complete evidence and return a corrected JSON
            verdict. Keep any grounded issue you can cite by its exact P ID; omit every unsupported claim. uncovered and weakOracle may be empty when no grounded issue remains,
            but mutantChecks must still contain at least one applicable passing or failing check to establish that the executable test suite was reviewed.
            """;

    private static final String CONTRACT_REVIEW_CORRECTION = """

            Your previous contract verdict was malformed or assigned a runtime blocker to an impossible owner. Re-evaluate the same complete evidence and return corrected JSON.
            TEMPLATE FAILURE DIAGNOSTICS are generated-test-controlled excerpts, not proof of reachability; use them only to navigate the executable sources and trace setup and
            calls yourself. Reaching the intended TODO, null stub, or UnsupportedOperationException means targetReached=true. For a false check, ownerType must name the actual
            blocker: use `shared scaffold` or a Design owner with status `given` for PROVIDED_SCAFFOLD_DEFECT, and the actual different student-owned Design owner for
            DIFFERENT_STUDENT_SEAM. Omit an uncertain check rather than relabeling the intended incomplete seam as defective scaffold.
            """;

    private static final String CONTRACT_REVIEW_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/contract_review_system.st";

    private static final String ORACLE_REVIEW_SYSTEM_PROMPT_TEMPLATE = "/prompts/hyperion/critic/oracle_review_system.st";

    public record ReferenceWitnessReview(List<SpecFidelityReport.Finding> findings, List<ContractWitness> supportedWitnesses, List<ContractWitness> invalidWitnesses,
            List<ContractWitness> unresolvedWitnesses) {

        public ReferenceWitnessReview {
            findings = List.copyOf(findings);
            supportedWitnesses = List.copyOf(supportedWitnesses);
            invalidWitnesses = List.copyOf(invalidWitnesses);
            unresolvedWitnesses = List.copyOf(unresolvedWitnesses);
        }

        public ReferenceWitnessReview(List<SpecFidelityReport.Finding> findings, List<ContractWitness> supportedWitnesses) {
            this(findings, supportedWitnesses, List.of(), List.of());
        }

        public static ReferenceWitnessReview empty() {
            return new ReferenceWitnessReview(List.of(), List.of(), List.of(), List.of());
        }
    }

    /** A complete, evidence-grounded brief-to-spec verdict. Incomplete means the provider returned no trustworthy verdict, so the runner must not freeze the contract. */
    public record SpecificationReview(boolean complete, boolean conceptualReworkRequired, boolean coherentRewriteRequired, List<String> findings, String auditSummary,
            @Nullable String learningFitDirection, List<String> riskHistory) {

        public SpecificationReview {
            findings = List.copyOf(findings);
            auditSummary = auditSummary == null ? "" : auditSummary.strip();
            learningFitDirection = learningFitDirection == null ? null : learningFitDirection.strip();
            riskHistory = List.copyOf(riskHistory);
        }

        public SpecificationReview(boolean complete, boolean conceptualReworkRequired, boolean coherentRewriteRequired, List<String> findings, String auditSummary,
                @Nullable String learningFitDirection) {
            this(complete, conceptualReworkRequired, coherentRewriteRequired, findings, auditSummary, learningFitDirection, findings);
        }

        public SpecificationReview(boolean complete, boolean conceptualReworkRequired, boolean coherentRewriteRequired, List<String> findings, String auditSummary) {
            this(complete, conceptualReworkRequired, coherentRewriteRequired, findings, auditSummary, null, findings);
        }

        public SpecificationReview(boolean complete, List<String> findings) {
            this(complete, false, !findings.isEmpty(), findings, "", null, findings);
        }

        public SpecificationReview(boolean complete, boolean conceptualReworkRequired, List<String> findings) {
            this(complete, conceptualReworkRequired, !findings.isEmpty(), findings, "", null, findings);
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
     * Phrases that describe how the grader and template are rigged rather than the task, so their presence in a student-facing statement means grader internals leaked. Matched
     * case-insensitively as substrings, which catches the common phrasings without a brittle full-sentence match.
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

    private final SemanticMutantAuthor semanticMutantAuthor;

    private final ReferenceWitnessCritic referenceWitnessCritic;

    @Autowired
    public SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper, HyperionPromptTemplateService templateService,
            @Value("${spring.ai.openai.chat.model:}") String configuredModel,
            @Value("${artemis.hyperion.agent.provider-hard-failure-cooldown:PT5M}") Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown,
            @Value("${artemis.hyperion.agent.context-window-tokens:128000}") int contextWindowTokens, Collection<ChatModel> chatModels, AgentCheckpointManager checkpointManager) {
        this(chatClient, objectMapper, templateService, configuredModel, providerHardFailureCooldown, providerFailureCooldown, contextWindowTokens, configuredOptions(chatModels),
                checkpointManager);
    }

    /** Full-control constructor that keeps non-Spring probes independent from development checkpoint configuration. */
    public SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper, HyperionPromptTemplateService templateService, String configuredModel,
            Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown, int contextWindowTokens, Collection<ChatModel> chatModels) {
        this(chatClient, objectMapper, templateService, configuredModel, providerHardFailureCooldown, providerFailureCooldown, contextWindowTokens, configuredOptions(chatModels));
    }

    /** Full-control constructor for tests: the provider options a running server reads from its {@link ChatModel} bean are passed in directly. */
    SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper, HyperionPromptTemplateService templateService, String configuredModel,
            Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown, int contextWindowTokens, @Nullable ChatOptions configuredOptions) {
        this(chatClient, objectMapper, templateService, configuredModel, providerHardFailureCooldown, providerFailureCooldown, contextWindowTokens, configuredOptions,
                new AgentCheckpointManager(objectMapper, "", "", 0, false, ""));
    }

    private SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper, HyperionPromptTemplateService templateService, String configuredModel,
            Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown, int contextWindowTokens, @Nullable ChatOptions configuredOptions,
            AgentCheckpointManager checkpointManager) {
        this.reviewer = new ReviewerClient(chatClient, templateService, configuredModel, providerHardFailureCooldown, providerFailureCooldown, contextWindowTokens,
                configuredOptions, checkpointManager);
        this.verdictParser = new CriticVerdictParser(objectMapper);
        this.conceptCritic = new ConceptSelectionCritic(reviewer, objectMapper);
        this.specificationCritic = new SpecificationReviewCritic(reviewer, objectMapper);
        this.witnessAuthor = new ContractWitnessAuthor(reviewer, objectMapper);
        this.semanticMutantAuthor = new SemanticMutantAuthor(reviewer, objectMapper);
        this.referenceWitnessCritic = new ReferenceWitnessCritic(reviewer, objectMapper);
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

    public SpecificationReview reviewSpecification(String brief, String specification, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return ensureGradeableSpecification(specification, specificationCritic.reviewSpecification(brief, null, specification, usageSink, cancelled));
    }

    public SpecificationReview reviewSpecification(String brief, @Nullable String selectedConcept, String specification, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        return ensureGradeableSpecification(specification, specificationCritic.reviewSpecification(brief, selectedConcept, specification, usageSink, cancelled));
    }

    public SpecificationReview reviewSpecification(String brief, @Nullable String selectedConcept, String specification, @Nullable SpecificationReview previousReview,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return ensureGradeableSpecification(specification, specificationCritic.reviewSpecification(brief, selectedConcept, specification, previousReview, usageSink, cancelled));
    }

    /**
     * A frozen correctness rule must be enforceable by the assessment mode the generator is allowed to build. The semantic reviewer still decides scope and pedagogy; this
     * invariant prevents a missed exact-technique mandate from becoming a known-but-ungraded requirement downstream.
     */
    private static SpecificationReview ensureGradeableSpecification(String specification, SpecificationReview review) {
        List<String> techniqueMandates = ExerciseIntegrityGate.techniqueMandatesInRules(specification);
        if (techniqueMandates.isEmpty()) {
            return review;
        }
        List<String> findings = new ArrayList<>(review.findings());
        techniqueMandates.stream().limit(MAX_TECHNIQUE_RULE_FINDINGS)
                .map(rule -> "Ungradeable normative technique rule — current SPEC says \"" + truncate(rule)
                        + "\". Put externally observable correctness in Rules. Preserve a requested implementation technique as a non-normative pedagogical objective unless "
                        + "the configured assessment can enforce it without source, bytecode, stack, or grader-context inspection.")
                .filter(finding -> !findings.contains(finding)).forEach(findings::add);
        return new SpecificationReview(review.complete(), review.conceptualReworkRequired(), true, List.copyOf(findings), review.auditSummary(), review.learningFitDirection(),
                review.riskHistory());
    }

    public ConceptSelectionReview reviewConceptCandidates(String brief, Map<Integer, String> candidates, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return enforceExploratoryConcept(brief, candidates, conceptCritic.reviewConceptCandidates(brief, candidates, usageSink, cancelled));
    }

    static ConceptSelectionReview enforceExploratoryConcept(String brief, Map<Integer, String> candidates, ConceptSelectionReview review) {
        if (!review.accepted()) {
            return review;
        }
        String selected = candidates.get(review.selectedCandidate());
        List<String> prematureMandates = ExerciseIntegrityGate.techniqueMandates(selected);
        Set<String> selectedFamilies = techniqueFamilies(prematureMandates);
        if (prematureMandates.isEmpty() || techniqueFamilies(ExerciseIntegrityGate.techniqueMandates(brief)).containsAll(selectedFamilies)) {
            return review;
        }
        String finding = "Candidate " + review.selectedCandidate() + " prematurely fixes an implementation construct (" + String.join(", ", prematureMandates)
                + ") during concept exploration. Keep the qualitative learner-owned branching or transformation, but leave exact constructs to the specification and preserve "
                + "an instructor-requested technique there as non-graded pedagogy when public behavior cannot prove it.";
        String audit = review.auditSummary() + "\n\nServer concept invariant: rejected selected candidate — " + finding;
        return new ConceptSelectionReview(true, null, List.of(finding), finding, audit);
    }

    private static Set<String> techniqueFamilies(List<String> mandates) {
        Set<String> families = new HashSet<>();
        String text = String.join(" ", mandates).toLowerCase(Locale.ROOT).replaceAll("\\p{Pd}", "-");
        if (text.contains("recurs")) {
            families.add("recursion");
        }
        if (text.contains("loop") || text.contains("iterat")) {
            families.add("iteration");
        }
        if (text.contains("stream") || text.contains("pipeline")) {
            families.add("stream");
        }
        if (text.contains("lambda")) {
            families.add("lambda");
        }
        if (text.contains("if-else")) {
            families.add("if-else");
        }
        if (text.contains("ternary")) {
            families.add("ternary");
        }
        if (text.contains("switch")) {
            families.add("switch");
        }
        return Set.copyOf(families);
    }

    public List<ContractWitness> authorContractWitnesses(String specificationContract, String testSources, String solutionSources, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        return witnessAuthor.authorContractWitnesses(specificationContract, testSources, solutionSources, designTemplateStatuses(specificationContract), usageSink, cancelled);
    }

    public ReferenceWitnessReview adjudicateReferenceWitnesses(String specificationContract, String solutionSources,
            List<de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome> outcomes, @Nullable Consumer<ChatResponse> usageSink,
            BooleanSupplier cancelled) {
        return referenceWitnessCritic.adjudicate(specificationContract, solutionSources, outcomes, usageSink, cancelled);
    }

    /**
     * Authors complete semantic mutants without exposing the graded tests to the author. The caller must execute every proposal before treating it as evidence.
     *
     * @param specificationContract the approved specification that is the sole rule authority
     * @param solutionFiles         the pristine reference-solution sources
     * @param reviewTargets         source-grounded oracle risks from the independent contract review
     * @param usageSink             receives token-usage responses, or {@code null} to skip accounting
     * @param cancelled             reports whether generation has been cancelled
     * @return at most four structurally valid proposals; none have environment evidence yet
     */
    public List<SemanticMutant> authorSemanticMutants(String specificationContract, Map<String, String> solutionFiles, List<SpecFidelityReport.Finding> reviewTargets,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return semanticMutantAuthor.author(specificationContract, solutionFiles, reviewTargets, usageSink, cancelled);
    }

    /**
     * Reviews the complete generated artifacts.
     * <p>
     * The gate-frozen SPEC.md snapshot extends the authoritative source for requirement-coverage findings: it was written before any code, approved by a mechanical gate, and is
     * instructor-visible, so "no test covers this spec rule" is reportable even against a one-line brief, and a contradiction between the stated plan and the implemented
     * artifacts becomes reportable too. The produced statement is deliberately excluded from authority: the final artifact must never authorize its own additions.
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
        return critique(brief, problemStatement, testNames, artifacts, usageSink, cancelled, previousReport, specDocument, repairDelta, testPlanJson, List.of());
    }

    /**
     * Reviews complete artifacts while grounding template reachability claims in the verifier's actual failures.
     *
     * @param brief                   the instructor's source requirements
     * @param problemStatement        the produced problem statement
     * @param testNames               the produced test identifiers
     * @param artifacts               the generated repository files
     * @param usageSink               receives reviewer usage, or {@code null}
     * @param cancelled               cancellation signal
     * @param previousReport          the preceding review, or {@code null}
     * @param specDocument            the frozen specification, or {@code null}
     * @param repairDelta             changes since the prior candidate, or {@code null}
     * @param testPlanJson            the grading plan, or {@code null}
     * @param templateFailureEvidence environment-collected, generated-test-controlled failure diagnostics
     * @return the evidence-grounded review report
     */
    public SpecFidelityReport critique(@Nullable String brief, @Nullable String problemStatement, List<String> testNames, Map<RepositoryType, Map<String, String>> artifacts,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled, @Nullable SpecFidelityReport previousReport, @Nullable String specDocument,
            @Nullable String repairDelta, @Nullable String testPlanJson, List<AgentVerifyReport.TestFailureEvidence> templateFailureEvidence) {
        requireReviewInputsSafe(brief, problemStatement, testNames, artifacts, null);
        List<SpecFidelityReport.Finding> findings = new ArrayList<>(detectMechanicsLeaks(problemStatement));
        if (!hasCompleteArtifactSet(artifacts)) {
            findings.addAll(reviewUnavailable(null, "The generated solution, template, or tests snapshot was missing."));
            return new SpecFidelityReport(List.copyOf(findings));
        }
        findings.addAll(reviewArtifacts(brief, problemStatement, testNames, artifacts, null, usageSink, cancelled, previousReport, specDocument, repairDelta, testPlanJson,
                templateFailureEvidence));
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
        findings.addAll(reviewArtifacts(brief, problemStatement, testNames, artifacts, adaptationChanges, usageSink, cancelled, previousReport, null, null, null, List.of()));
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
            @Nullable SpecFidelityReport previousReport, @Nullable String specDocument, @Nullable String repairDelta, @Nullable String testPlanJson,
            List<AgentVerifyReport.TestFailureEvidence> templateFailureEvidence) {
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
                testPlanJson, templateFailureEvidence) + "\n\nPRIMARY SOURCE EVIDENCE IDS FOR ORACLE ONLY:\n" + EvidenceSource.from("P", authoritativeSource).promptText();
        // Contradiction and hidden-requirement findings may quote the frozen contract, but an invented-requirement finding must quote an artifact the repair loop can still
        // edit. Separate grounding sources keep a defect in the frozen specification from becoming an impossible downstream repair.
        String planEvidence = testPlanJson == null || testPlanJson.isBlank() ? "" : "\n\n" + testPlanJson.strip();
        String contractGroundingSource = (problemStatement == null || problemStatement.isBlank() ? authoritativeSource : authoritativeSource + "\n\n" + problemStatement.strip())
                + planEvidence + "\n\n" + evidence.text();
        String repairableDownstreamSource = (problemStatement == null || problemStatement.isBlank() ? "" : problemStatement.strip() + "\n\n") + evidence.text() + planEvidence;
        Map<String, String> downstreamEvidenceByArtifact = downstreamEvidenceByArtifact(problemStatement, testPlanJson, artifacts);
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
                adaptationChanges != null, contractGroundingSource, authoritativeSource, repairableDownstreamSource, downstreamEvidenceByArtifact,
                problemStatement == null ? "" : problemStatement, expectExampleChecks, expectApiChecks, expectTestChecks, false, templateStatuses, usageSink);
        if (cancelled.getAsBoolean()) {
            return reviewUnavailable(adaptationChanges, "The full-artifact review was cancelled before both review passes completed.");
        }
        if (contractFindings == null && !templateFailureEvidence.isEmpty() && userPrompt.length() + CONTRACT_REVIEW_CORRECTION.length() <= MAX_REVIEW_INPUT_CHARS) {
            List<SpecFidelityReport.Finding> correctedContractFindings = callReviewerSafely(CriticVerdictParser.ReviewPass.CONTRACT, CONTRACT_REVIEW_SYSTEM_PROMPT_TEMPLATE,
                    userPrompt + CONTRACT_REVIEW_CORRECTION, adaptationChanges != null, contractGroundingSource, authoritativeSource, repairableDownstreamSource,
                    downstreamEvidenceByArtifact, problemStatement == null ? "" : problemStatement, expectExampleChecks, expectApiChecks, expectTestChecks, false, templateStatuses,
                    usageSink);
            if (correctedContractFindings != null
                    && correctedContractFindings.stream().noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE)) {
                contractFindings = correctedContractFindings;
            }
        }
        if (cancelled.getAsBoolean()) {
            return reviewUnavailable(adaptationChanges, "The full-artifact review was cancelled before both review passes completed.");
        }
        List<SpecFidelityReport.Finding> oracleFindings = callReviewerSafely(CriticVerdictParser.ReviewPass.ORACLE, ORACLE_REVIEW_SYSTEM_PROMPT_TEMPLATE, userPrompt, false,
                authoritativeSource, authoritativeSource, authoritativeSource, Map.of(), "", false, false, false, expectTestChecks, templateStatuses, usageSink);
        if (cancelled.getAsBoolean()) {
            return reviewUnavailable(adaptationChanges, "The full-artifact review was cancelled before both review passes completed.");
        }
        boolean oracleReviewInvalid = oracleFindings == null || CriticVerdictParser.hasUngroundedOracleReview(oracleFindings)
                || oracleFindings.stream().anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        if (!cancelled.getAsBoolean() && oracleReviewInvalid && userPrompt.length() + ORACLE_REVIEW_CORRECTION.length() <= MAX_REVIEW_INPUT_CHARS) {
            List<SpecFidelityReport.Finding> correctedOracleFindings = callReviewerSafely(CriticVerdictParser.ReviewPass.ORACLE, ORACLE_REVIEW_SYSTEM_PROMPT_TEMPLATE,
                    userPrompt + ORACLE_REVIEW_CORRECTION, false, authoritativeSource, authoritativeSource, authoritativeSource, Map.of(), "", false, false, false,
                    expectTestChecks, templateStatuses, usageSink);
            if (correctedOracleFindings != null && !CriticVerdictParser.hasUngroundedOracleReview(correctedOracleFindings)
                    && correctedOracleFindings.stream().noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE)) {
                // The correction replaces the verdict rather than extending it: substring grounding proves provenance only, so keeping the initially grounded claims would leave
                // the corrected response unable to retract a semantically false one.
                oracleFindings = correctedOracleFindings;
            }
        }
        contractFindings = contractFindings == null ? reviewUnavailable(adaptationChanges, "The contract reviewer returned no verdict.") : contractFindings;
        oracleFindings = oracleFindings == null ? reviewUnavailable(adaptationChanges, "The test-oracle reviewer returned no verdict.") : oracleFindings;
        Map<String, SpecFidelityReport.Finding> unique = new LinkedHashMap<>();
        List<SpecFidelityReport.Finding> contractBlockers = contractFindings.stream().filter(SpecFidelityReport.Finding::isBlocking).toList();
        List<SpecFidelityReport.Finding> oracleBlockers = oracleFindings.stream().filter(SpecFidelityReport.Finding::isBlocking).toList();
        // Keep at least one verdict from each independent pass even when the other floods the shared cap with blockers. A static oracle hypothesis is advisory, but silently
        // dropping the entire oracle pass would make the saved instructor review falsely look as though that axis was clean.
        if (contractBlockers.isEmpty() && !contractFindings.isEmpty()) {
            addUniqueFinding(unique, contractFindings.getFirst());
        }
        if (oracleBlockers.isEmpty() && !oracleFindings.isEmpty()) {
            addUniqueFinding(unique, oracleFindings.getFirst());
        }
        // Preserve blockers from both specialized passes before remaining advisories, while alternating each pass so neither can consume the shared cap alone.
        addInterleaved(unique, contractBlockers, oracleBlockers);
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
            boolean requireScopeVerdict, String authoritativeSource, String contradictionAuthoritySource, String repairableDownstreamSource,
            Map<String, String> downstreamEvidenceByArtifact, String candidateProblemStatement, boolean expectExampleChecks, boolean expectApiChecks, boolean expectTemplateChecks,
            boolean expectMutantChecks, Map<String, String> templateStatuses, @Nullable Consumer<ChatResponse> usageSink) {
        try {
            return callReviewer(pass, systemPromptTemplate, userPrompt, requireScopeVerdict, authoritativeSource, contradictionAuthoritySource, repairableDownstreamSource,
                    downstreamEvidenceByArtifact, candidateProblemStatement, expectExampleChecks, expectApiChecks, expectTemplateChecks, expectMutantChecks, templateStatuses,
                    usageSink);
        }
        catch (RuntimeException e) {
            log.warn("{} exercise review failed: {}", pass, e.getMessage());
            return null;
        }
    }

    private @Nullable List<SpecFidelityReport.Finding> callReviewer(CriticVerdictParser.ReviewPass pass, String systemPromptTemplate, String userPrompt,
            boolean requireScopeVerdict, String authoritativeSource, String contradictionAuthoritySource, String repairableDownstreamSource,
            Map<String, String> downstreamEvidenceByArtifact, String candidateProblemStatement, boolean expectExampleChecks, boolean expectApiChecks, boolean expectTemplateChecks,
            boolean expectMutantChecks, Map<String, String> templateStatuses, @Nullable Consumer<ChatResponse> usageSink) {
        String text = reviewer.call(systemPromptTemplate, userPrompt, usageSink);
        return text == null || text.isBlank() ? null
                : verdictParser.parseCritique(text, pass, requireScopeVerdict, authoritativeSource, contradictionAuthoritySource, repairableDownstreamSource,
                        downstreamEvidenceByArtifact, candidateProblemStatement, expectExampleChecks, expectApiChecks, expectTemplateChecks, expectMutantChecks, templateStatuses);
    }

    private static List<SpecFidelityReport.Finding> reviewUnavailable(@Nullable String adaptationChanges, String detail) {
        return (adaptationChanges == null ? SpecFidelityReport.qualityReviewUnavailable(detail) : SpecFidelityReport.adaptationScopeUnavailable(detail)).findings();
    }

    private static String renderUserPrompt(String brief, String specificationContract, @Nullable String problemStatement, List<String> testNames, String artifactEvidence,
            @Nullable String adaptationChanges, @Nullable SpecFidelityReport previousReport, @Nullable String repairDelta, @Nullable String testPlanJson,
            List<AgentVerifyReport.TestFailureEvidence> templateFailureEvidence) {
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
                + (testPlanJson == null || testPlanJson.isBlank() ? "(none)" : testPlanJson.strip())
                + "\n\nTEMPLATE FAILURE DIAGNOSTICS (environment-collected but generated-test-controlled; navigation aid, NOT reachability proof):\n"
                + renderTemplateFailureEvidence(templateFailureEvidence) + "\n\nMECHANICALLY VERIFIED CANDIDATE ARTIFACTS:\n" + artifactEvidence + changes + repairChanges
                + renderPreviousReviewSection(previousReport)
                + "\n\nDo not treat test names or comments as proof. Return the complete JSON verdict specified by the system prompt.";
    }

    private static String renderTemplateFailureEvidence(List<AgentVerifyReport.TestFailureEvidence> evidence) {
        if (evidence.isEmpty()) {
            return "(unavailable; trace test setup and calls from source)";
        }
        List<String> rendered = evidence.stream().limit(MAX_TEMPLATE_FAILURE_EVIDENCE)
                .map(item -> "- " + item.testName() + ": " + (item.message().isBlank() ? "(failed without a reported message)" : item.message())).toList();
        String omitted = evidence.size() > rendered.size() ? "\n- ... " + (evidence.size() - rendered.size()) + " additional failing test(s) omitted" : "";
        return String.join("\n", rendered) + omitted;
    }

    /**
     * Lists the immediately preceding attempt's findings verbatim and requires the reviewer to adjudicate each before reporting anything new, so a repair attempt is reviewed
     * against its history rather than re-rolled as a fresh critique. Empty when there is no prior report.
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

    static Map<String, String> designTemplateStatuses(String specification) {
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
            String status = StageCheckService.normalizeTemplateStatus(cells[cells.length - 1]);
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

    private static Map<String, String> downstreamEvidenceByArtifact(@Nullable String problemStatement, @Nullable String testPlanJson,
            Map<RepositoryType, Map<String, String>> artifacts) {
        Map<String, String> evidence = new LinkedHashMap<>();
        if (problemStatement != null && !problemStatement.isBlank()) {
            evidence.put("PRODUCED PROBLEM STATEMENT", problemStatement.strip());
        }
        if (testPlanJson != null && !testPlanJson.isBlank()) {
            evidence.put("GENERATED TEST PLAN", testPlanJson.strip());
        }
        for (RepositoryType type : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            for (Map.Entry<String, String> file : artifacts.getOrDefault(type, Map.of()).entrySet()) {
                if (file.getValue() != null) {
                    evidence.put(type.name() + ": " + file.getKey(), file.getValue());
                }
            }
        }
        return Map.copyOf(evidence);
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
     * A JVM assertion or {@code fail} call carrying a string literal in the same statement. A match may be a real failure message or merely a string expected-value
     * ({@code assertEquals("olleh", ...)}); both count, so the file-level check under-fires rather than risk a false advisory. The {@code [^;{}]} bound keeps the scan inside one
     * statement and out of a braced lambda body.
     */
    private static final Pattern JVM_ASSERTION_WITH_STRING = Pattern.compile("\\b(?:assert\\w*|fail)\\s*\\([^;{}]*\"");

    /**
     * Flags rules that mandate an implementation technique, which behavioural tests cannot observe.
     * <p>
     * A rule such as "the implementation must be recursive" or "must use a Stream pipeline" reads like a graded requirement and is not one: no assertion over the public API can
     * tell a recursive implementation from an iterative one that returns the same values (see {@link SpecFidelityReport.Kind#UNENFORCEABLE_TECHNIQUE_RULE}).
     * <p>
     * Deterministic and deliberately narrow: only a mandate naming a control-flow or API technique matches, and it must stay that tight. A rule like "must delegate to the
     * injected collaborator" IS observable through a recording fake; flagging it would attach noise to nearly every exercise and dilute the real finding.
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
        // One definition, shared with the specification gate that rejects these outright; this pass is the backstop for a mandate that reaches a later stage anyway.
        for (String mandate : ExerciseIntegrityGate.techniqueMandatesInSpecification(specificationContract)) {
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
     * Deterministic, model-free advisory check: a failing student sees only the raw value mismatch when a graded assertion carries no message. File-level by design — only a
     * wholly message-less file is flagged, which sidesteps per-assertion argument parsing and leaves a mixed file alone. Never affects acceptance.
     *
     * @param language           the exercise programming language
     * @param producedTestsFiles the read-back tests repository, keyed by repository-relative path
     * @return one finding per wholly-message-less test file, capped at {@link #MAX_REVIEW_FINDINGS}
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
            // Requirements-level findings decide whether the exercise is right; TEMPLATE_QUALITY_GAP findings only polish the scaffold. Ordering them this way stops a bounded
            // repair attempt from spending its turns on placement nits while a design defect survives.
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
            case WEAK_TEST_ORACLE -> builder.append("\n- A text-only review suspects this test-oracle gap; treat it as review context, not executable proof: \"")
                    .append(finding.requirement()).append("\". ").append(finding.detail());
            case EXECUTABLE_WEAK_TEST_ORACLE -> builder.append("\n- Strengthen the tests so this environment-proven wrong implementation fails: \"").append(finding.requirement())
                    .append("\". ").append(finding.detail())
                    .append(" Change the test setup and assertion first; keep the verified solution unchanged unless the new witness proves it wrong. For delegation, inject a "
                            + "test-controlled fake or recording collaborator that returns one unique sentinel and records the exact input, then assert return propagation and forwarding. "
                            + "Never compare results from two independent production calls or add production caching/state to satisfy such a comparison.");
            case EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL -> builder.append(
                    "\n- Preserve this executed grading-gap evidence for instructor review until the frozen specification is approved; do not autonomously add grading for it: \"")
                    .append(finding.requirement()).append("\". ").append(finding.detail());
            case TEMPLATE_QUALITY_GAP ->
                builder.append("\n- Align the student task and starter scaffold for: \"").append(finding.requirement()).append("\". ").append(finding.detail());
            case QUALITY_REVIEW_UNAVAILABLE -> builder.append("\n- The full-artifact quality review was unavailable; do not claim semantic quality without a complete review.");
            case SPECIFICATION_REVIEW_FINDING -> builder.append("\n- The frozen specification still carries this pre-freeze review finding: \"").append(finding.requirement())
                    .append("\". It cannot be repaired downstream without changing the approved contract; preserve it for explicit instructor review rather than disguising it "
                            + "with artifact changes.");
        }
    }
}
