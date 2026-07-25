package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.CollectedReports;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.localci.service.scaparser.ReportParser;
import de.tum.cit.aet.artemis.localci.service.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;

/**
 * Independently verifies generated exercises by building solution and template from a pristine script and parsing verifier-owned reports with the normal LocalCI parsers.
 * <p>
 * The enforced starter-credit policy: the solution must pass every test. The template must compile and run the same tests; every non-structural gradable test must fail on it
 * (build/compile/configure gate tests are exempt because they only gate compilation, and structural tests seeded by {@link StructuralOracleSeedingService} may legitimately pass
 * — Artemis's own reference exercises, such as BubbleSort, give early "structural" credit for a correctly-shaped stub while the behavioural tests still fail). There is no
 * "at least half" or "at least one per task" leniency: every individual non-structural gradable test that passes on the template is an actionable rejection naming that test.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class DifferentialVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DifferentialVerificationService.class);

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(10);

    private static final Set<String> READINESS_TEST_NAMES = Set.of("testPublicApi", "testRepresentativeScores", "testBoundaryScores", "testEmptyInput");

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private static final int PRISTINE_SCRIPT_MODE = 0755;

    private static final int MAX_READINESS_DIAGNOSTIC_CHARS = 4_000;

    private static final Pattern URI_CREDENTIALS = Pattern.compile("(?i)(https?://)[^\\s/@]+@");

    private static final Pattern AUTHORIZATION_CREDENTIALS = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(?:basic|bearer)?\\s*[^\\s,;]+");

    private static final Pattern BEARER_CREDENTIALS = Pattern.compile("(?i)\\bbearer\\s+[^\\s,;]+");

    private static final Pattern NAMED_CREDENTIALS = Pattern.compile("(?i)\\b(password|passwd|token|secret|api[-_]?key)\\s*[:=]\\s*[^\\s,;]+");

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

    private final SandboxBuildCommandService sandboxBuildCommandService;

    /**
     * Persisted SCA categories read the same way production grading does ({@code findByExerciseId}), so the SCA-parity gate decides from authoritative state, not the detached
     * in-memory collection. Optional because SCA categories live in the core profile; absent on a node that cannot grade anyway, where the gate fails open.
     */
    private final Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository;

    private final ApprovedSpecRegistry approvedSpecs;

    // @Autowired disambiguates from the package-private test constructor; with two constructors and no annotation Spring cannot instantiate the bean.
    @Autowired
    public DifferentialVerificationService(SandboxBuildCommandService sandboxBuildCommandService,
            Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository, ApprovedSpecRegistry approvedSpecs) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.approvedSpecs = approvedSpecs;
    }

    DifferentialVerificationService(SandboxBuildCommandService sandboxBuildCommandService, Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this(sandboxBuildCommandService, staticCodeAnalysisCategoryRepository, new ApprovedSpecRegistry());
    }

    DifferentialVerificationService(SandboxBuildCommandService sandboxBuildCommandService) {
        this(sandboxBuildCommandService, Optional.empty());
    }

    private String readProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        return result.isSuccess() ? result.stdout() : "";
    }

    /**
     * The normalized test names the workspace's grading plan hides until the due date. Fail-open by construction: a missing, unreadable, or malformed {@code test-plan.json}
     * yields an empty set, which restores the pre-plan behaviour (every gradable test must be bound) rather than silently relaxing the binding gate.
     */
    private Set<String> readHiddenTestNames(InteractiveSandbox sandbox, String sessionId) {
        try {
            SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
            if (!result.isSuccess() || result.stdout() == null || result.stdout().isBlank()) {
                return Set.of();
            }
            return GeneratedTestPlan.parse(result.stdout()).hiddenEntries().stream().map(GeneratedTestPlan.Entry::name).map(ProblemStatementBindingChecker::normalizeTestName)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        catch (RuntimeException e) {
            return Set.of();
        }
    }

    private String readSpecDocument(InteractiveSandbox sandbox, String sessionId) {
        return readWorkspaceRootFile(sandbox, sessionId, "SPEC.md");
    }

    private static String readWorkspaceRootFile(InteractiveSandbox sandbox, String sessionId, String filename) {
        SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/" + filename);
        return result.isSuccess() ? result.stdout() : "";
    }

    /** Cap on the dead-file probe output, so a runaway workspace listing cannot blow the agent's context. */
    private static final int MAX_POSSIBLY_DEAD_FILES = 20;

    /**
     * Best-effort, language-agnostic dead-file probe for the agent's self-check (advisory only, not a gate): a source file present in exactly one of {@code solution/} and
     * {@code template/}, a likely abandoned orphan since the two repos should differ only in method bodies. Ignores build manifests and hidden files and never throws (fail-open).
     *
     * @return the repo-qualified paths present in exactly one of the two assignment repos, capped; empty when the probe is unavailable or finds nothing
     */
    private static List<String> possiblyDeadWorkspaceFiles(InteractiveSandbox sandbox, String sessionId) {
        try {
            Set<String> solution = listSourceFiles(sandbox, sessionId, GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
            Set<String> template = listSourceFiles(sandbox, sessionId, GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));
            if (solution.isEmpty() && template.isEmpty()) {
                return List.of();
            }
            List<String> dead = new ArrayList<>();
            solution.stream().filter(path -> !template.contains(path)).sorted().forEach(path -> dead.add("solution/" + path));
            template.stream().filter(path -> !solution.contains(path)).sorted().forEach(path -> dead.add("template/" + path));
            return dead.size() <= MAX_POSSIBLY_DEAD_FILES ? List.copyOf(dead) : List.copyOf(dead.subList(0, MAX_POSSIBLY_DEAD_FILES));
        }
        catch (RuntimeException e) {
            log.debug("Dead-file probe failed; omitting the hint from the self-check: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Build-manifest filenames that are legitimately repo-specific, so the dead-file probe must not flag them. Generation only ever runs for Java/Maven exercises
     * ({@link de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.LanguageGenerationProfile}), so only the Maven manifest is listed.
     */
    private static final Set<String> BUILD_MANIFEST_NAMES = Set.of("pom.xml");

    /** Lists assignment-repository source files for the asymmetric-file advisory, excluding hidden files and build manifests. Empty on any non-success (fail-open). */
    private static Set<String> listSourceFiles(InteractiveSandbox sandbox, String sessionId, String repoDirectory) {
        String repoRoot = GenerationWorkspaceService.WORKSPACE + "/" + repoDirectory;
        SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "sh", "-c",
                "cd '" + repoRoot + "' 2>/dev/null && find . -type f | sed 's|^\\./||' | grep -v '/\\.' | grep -v '^\\.' || true");
        if (!result.isSuccess()) {
            return Set.of();
        }
        Set<String> files = new LinkedHashSet<>();
        for (String line : result.stdout().split("\n")) {
            String path = line.strip();
            if (path.isEmpty()) {
                continue;
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!BUILD_MANIFEST_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                files.add(path);
            }
        }
        return files;
    }

    /**
     * Runs the differential verification and the sandbox-free integrity gates (harness immutability and solution-leak); mechanical verification passes only when both pass.
     * <p>
     * The authoritative pass wipes and re-seeds the verifier control directory, deletes pre-existing report XML, builds in fresh temporary directories, and counts only reports
     * written during the build. The integrity gates fail open on genuinely-empty inputs but fail closed when a repo seeded non-empty
     * extracts empty at verify time (via {@code extractionFailedRepositories}), so a flaky read-back cannot silently disable a gate.
     *
     * @param sandbox   the open sandbox session the pristine builds run in
     * @param sessionId the sandbox session id
     * @param exercise  the exercise being verified (drives the per-language build recipe)
     * @param request   the produced artifacts and integrity-gate inputs to decide on (see {@link VerificationRequest})
     * @return the mechanical verdict (verified, solution-passed, template-failed, test count, and rejection reasons)
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, VerificationRequest request) {
        return verify(sandbox, sessionId, exercise, request, () -> {
        });
    }

    /**
     * Runs authoritative verification after restoring the captured candidate independently before the solution and template builds. This prevents generated tests or detached
     * processes from changing the second build's input and making the verifier approve a tree different from the one persistence receives.
     *
     * @param sandbox          the open sandbox session the pristine builds run in
     * @param sessionId        the sandbox session id
     * @param exercise         the exercise being verified (drives the per-language build recipe)
     * @param request          the produced artifacts and integrity-gate inputs to decide on (see {@link VerificationRequest})
     * @param restoreCandidate resets the same sandbox container and materializes the exact captured candidate before each of the two builds
     * @return the mechanical verdict (verified, solution-passed, template-failed, test count, and rejection reasons)
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, VerificationRequest request, Runnable restoreCandidate) {
        // The sandbox-dependent differential is computed by the same method the in-loop self-check uses, so the agent's `verify` tool and this mechanical decision cannot diverge.
        // This call layers the sandbox-free integrity gates and the final verdict on top of that shared analysis.
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, request.seededStructuralTestNames(), request.producedProblemStatement(), restoreCandidate);
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();
        List<String> reasons = new ArrayList<>(analysis.actionableReasons());

        // Integrity gates the build cannot see. Post-loop only (the self-check skips them): they need the seed snapshot and read-back files the agent loop lacks mid-session.
        // Adaptation may change test source files, but never the seeded build harness/manifest layout that production grading trusts verbatim.
        boolean harnessSnapshotRequired = exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA;
        List<String> harnessTamperingReasons = new ArrayList<>();
        harnessTamperingReasons.addAll(ExerciseIntegrityGate.harnessTamperingReasons("tests", request.seedTestsFiles(), request.producedTestsFiles(), harnessSnapshotRequired));
        harnessTamperingReasons.addAll(ExerciseIntegrityGate.harnessTamperingReasons("template", request.seedTemplateFiles(), request.producedTemplateFiles(), false));
        harnessTamperingReasons.addAll(ExerciseIntegrityGate.harnessTamperingReasons("solution", request.seedSolutionFiles(), request.producedSolutionFiles(), false));
        boolean harnessIntact = harnessTamperingReasons.isEmpty();
        reasons.addAll(harnessTamperingReasons);
        List<String> nondeterministicTestReasons = ExerciseIntegrityGate.nondeterministicGradedTestReasons(request.producedTestsFiles());
        boolean gradedTestsDeterministic = nondeterministicTestReasons.isEmpty();
        reasons.addAll(nondeterministicTestReasons);
        List<String> solutionLeakReasons = ExerciseIntegrityGate.solutionLeakReasons(request.producedTemplateFiles(), request.producedSolutionFiles());
        boolean noSolutionLeak = solutionLeakReasons.isEmpty();
        reasons.addAll(solutionLeakReasons);
        List<String> gradingContextSniffingReasons = ExerciseIntegrityGate.gradingContextSniffingReasons(request.producedTemplateFiles(), request.producedSolutionFiles());
        boolean noGradingContextSniffing = gradingContextSniffingReasons.isEmpty();
        reasons.addAll(gradingContextSniffingReasons);
        List<String> javaAresConventionReasons = exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA
                ? ExerciseIntegrityGate.javaAresConventionReasons(request.seedTestsFiles(), request.producedTestsFiles(), request.adaptation())
                : List.of();
        boolean javaAresConventionsHold = javaAresConventionReasons.isEmpty();
        reasons.addAll(javaAresConventionReasons);
        List<String> javaSourceLayoutReasons = exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA
                ? ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(exercise.getPackageName(), request.seedTestsFiles(), request.seedTemplateFiles(),
                        request.seedSolutionFiles(), request.producedTestsFiles(), request.producedTemplateFiles(), request.producedSolutionFiles())
                : List.of();
        boolean javaSourceLayoutIntact = javaSourceLayoutReasons.isEmpty();
        reasons.addAll(javaSourceLayoutReasons);

        List<String> contractSpecifications = contractSpecifications(sandbox, sessionId);
        List<String> approvedSpecificationReasons = contractSpecifications.stream()
                .flatMap(spec -> ExerciseIntegrityGate.approvedSpecificationReasons(spec, request.producedTemplateFiles(), request.producedSolutionFiles()).stream()).distinct()
                .toList();
        boolean approvedSpecificationHolds = approvedSpecificationReasons.isEmpty();
        reasons.addAll(approvedSpecificationReasons);
        List<String> approvedTestPlanReasons = contractSpecifications.stream()
                .flatMap(spec -> ExerciseIntegrityGate
                        .approvedTestPlanReasons(spec, request.producedTestPlan(), solution.testNames(), exercise.getDueDate() != null, request.seededStructuralTestNames())
                        .stream())
                .distinct().toList();
        boolean approvedTestPlanHolds = approvedTestPlanReasons.isEmpty();
        reasons.addAll(approvedTestPlanReasons);
        List<String> statementTraceabilityReasons = ExerciseIntegrityGate.statementTraceabilityReasons(request.producedTestPlan(), request.producedProblemStatement());
        boolean statementTraceabilityHolds = statementTraceabilityReasons.isEmpty();
        reasons.addAll(statementTraceabilityReasons);
        List<String> statementTaskInstructionReasons = ExerciseIntegrityGate.statementTaskInstructionReasons(request.producedProblemStatement());
        boolean statementTasksHaveInstructions = statementTaskInstructionReasons.isEmpty();
        reasons.addAll(statementTaskInstructionReasons);
        List<String> templateTodoSeamReasons = exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA
                ? contractSpecifications.stream().flatMap(spec -> ExerciseIntegrityGate.templateTodoSeamReasons(spec, request.producedTemplateFiles()).stream()).distinct().toList()
                : List.of();
        boolean templateTodoSeamsHold = templateTodoSeamReasons.isEmpty();
        reasons.addAll(templateTodoSeamReasons);

        boolean extractionSound = checkExtractionSound(request.extractionFailedRepositories(), reasons);

        // Adapt total-wipe gate: an adapt that retains none of the pre-adapt graded test names is a from-scratch regeneration mislabeled as an adapt (a destructive rewrite the
        // internally-consistent differential cannot see). Inert for generate (empty baseline) and for any partial edit that keeps at least one graded name; only adds a reject.
        List<String> adaptWipeReasons = ExerciseIntegrityGate.adaptWipedGradedTestsReasons(request.baselineGradedTestNames(), solution.testNames());
        boolean noAdaptWipe = adaptWipeReasons.isEmpty();
        reasons.addAll(adaptWipeReasons);

        boolean mechanicallyVerified = analysis.actionableGatesPass() && harnessIntact && noSolutionLeak && noGradingContextSniffing && javaAresConventionsHold
                && javaSourceLayoutIntact && approvedSpecificationHolds && approvedTestPlanHolds && statementTraceabilityHolds && templateTodoSeamsHold && extractionSound
                && statementTasksHaveInstructions && noAdaptWipe && gradedTestsDeterministic;
        if (!mechanicallyVerified) {
            log.info(
                    "Differential verification failed: solution[{}], template[{}], actionableGatesPass={}, harnessIntact={}, noSolutionLeak={}, "
                            + "javaAresConventionsHold={}, javaSourceLayoutIntact={}, approvedSpecificationHolds={}, approvedTestPlanHolds={}, statementTraceabilityHolds={}, "
                            + "statementTasksHaveInstructions={}, templateTodoSeamsHold={}, extractionSound={}, noAdaptWipe={}, gradedTestsDeterministic={}",
                    solution, template, analysis.actionableGatesPass(), harnessIntact, noSolutionLeak, javaAresConventionsHold, javaSourceLayoutIntact, approvedSpecificationHolds,
                    approvedTestPlanHolds, statementTraceabilityHolds, statementTasksHaveInstructions, templateTodoSeamsHold, extractionSound, noAdaptWipe,
                    gradedTestsDeterministic);
        }
        return new VerificationResult(mechanicallyVerified, analysis.solutionPassed(), analysis.templateFailed(), solution.tests(), reasons);
    }

    /**
     * The in-loop self-check the agent's {@code verify} tool calls: runs the same two pristine builds + production parse + actionable gates as the post-loop {@link #verify} and
     * returns an agent-readable {@link AgentVerifyReport} as a mechanical precheck before final post-loop integrity and semantic review.
     * <p>
     * It skips the sandbox-free integrity gates (they need the seed snapshot and read-back the agent loop lacks), so {@code wouldBeAccepted} reflects the differential + actionable
     * gates only; it neither checks nor proves semantic relevance. Final post-loop integrity checks determine mechanical validity; semantic review informs the instructor. Each
     * call
     * re-runs the two builds
     * (no stale cache).
     *
     * @param sandbox   the open sandbox session the pristine builds run in
     * @param sessionId the sandbox session id
     * @param exercise  the exercise being checked (drives the per-language build recipe)
     * @return the agent-readable differential report (per-test pass/fail on solution and template, parser-form names, wrongly-passing template tests, unresolved bindings)
     */
    public AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        return selfCheck(sandbox, sessionId, exercise, Map.of(), false);
    }

    /**
     * Runs the agent-visible verification while preserving untouched legacy tests during adaptation.
     *
     * @param sandbox        the open sandbox session
     * @param sessionId      the sandbox session id
     * @param exercise       the exercise being checked
     * @param seedTestsFiles the tests repository snapshot taken before generation
     * @param adaptation     whether the current job adapts an existing exercise
     * @return the agent-readable differential report
     */
    public AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles, boolean adaptation) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, adaptation, true);
    }

    /**
     * Runs a full agent-visible check with the server-authored structural names currently materialized in the workspace.
     *
     * @param sandbox                   the open sandbox session
     * @param sessionId                 the sandbox session id
     * @param exercise                  the exercise being checked
     * @param seedTestsFiles            the tests repository snapshot taken before generation
     * @param adaptation                whether the current job adapts an existing exercise
     * @param seededStructuralTestNames server-authored structural test names currently materialized in the workspace
     * @return the agent-readable differential report
     */
    public AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles, boolean adaptation,
            Set<String> seededStructuralTestNames) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, adaptation, true, seededStructuralTestNames);
    }

    /**
     * Runs the executable-build check with generated structural checks refreshed from the approved ownership contract. Threading their authoritative names here keeps the grading
     * plan and
     * statement contract identical to final verification instead of first revealing those gradable names in the outer repair loop.
     *
     * @param sandbox                   the open sandbox session
     * @param sessionId                 the sandbox session id
     * @param exercise                  the exercise whose tests are checked
     * @param seedTestsFiles            the immutable seeded tests snapshot
     * @param seededStructuralTestNames structural names produced by the server-side seeder for this session
     * @return a report limited to test-stage artifacts
     */
    public AgentVerifyReport selfCheckTestsStage(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            Set<String> seededStructuralTestNames) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, false, false, seededStructuralTestNames);
    }

    private AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles, boolean adaptation,
            boolean includeStatementChecks) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, adaptation, includeStatementChecks, Set.of());
    }

    private AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles, boolean adaptation,
            boolean includeStatementChecks, Set<String> seededStructuralTestNames) {
        String problemStatement = readProblemStatement(sandbox, sessionId);
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, seededStructuralTestNames, problemStatement, () -> {
        });
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();

        boolean solutionPassed = analysis.solutionPassed();
        boolean templateCompiled = !template.timedOut() && template.tests() > 0;
        List<String> templateWronglyPassing = analysis.gradableTestsPassingOnTemplate();
        List<String> reasons = new ArrayList<>(includeStatementChecks ? analysis.actionableReasons() : analysis.testArtifactReasons());
        boolean javaAresConventionsHold = true;
        if (exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            Map<String, String> testsRepositoryFiles = readTestsRepositoryFiles(sandbox, sessionId);
            List<String> javaAresConventionReasons = testsRepositoryFiles.isEmpty()
                    ? List.of("Could not inspect the tests repository for Java/Ares conventions; run verify again after ensuring /workspace/tests is readable.")
                    : ExerciseIntegrityGate.javaAresConventionReasons(seedTestsFiles, testsRepositoryFiles, adaptation);
            javaAresConventionsHold = javaAresConventionReasons.isEmpty();
            reasons.addAll(javaAresConventionReasons);
        }
        List<String> contractSpecifications = contractSpecifications(sandbox, sessionId);
        Map<String, String> templateFiles = readRepositoryFiles(sandbox, sessionId, RepositoryType.TEMPLATE);
        Map<String, String> solutionFiles = readRepositoryFiles(sandbox, sessionId, RepositoryType.SOLUTION);
        List<String> approvedSpecificationReasons = contractSpecifications.stream()
                .flatMap(spec -> ExerciseIntegrityGate.approvedSpecificationReasons(spec, templateFiles, solutionFiles).stream()).distinct().toList();
        boolean approvedSpecificationHolds = approvedSpecificationReasons.isEmpty();
        reasons.addAll(approvedSpecificationReasons);
        String testPlanJson = readWorkspaceRootFile(sandbox, sessionId, "test-plan.json");
        List<String> approvedTestPlanReasons = includeStatementChecks
                ? contractSpecifications.stream()
                        .flatMap(spec -> ExerciseIntegrityGate
                                .approvedTestPlanReasons(spec, testPlanJson, solution.testNames(), exercise.getDueDate() != null, seededStructuralTestNames).stream())
                        .distinct().toList()
                : List.of();
        boolean approvedTestPlanHolds = approvedTestPlanReasons.isEmpty();
        reasons.addAll(approvedTestPlanReasons);
        List<String> statementTraceabilityReasons = includeStatementChecks ? ExerciseIntegrityGate.statementTraceabilityReasons(testPlanJson, problemStatement) : List.of();
        boolean statementTraceabilityHolds = statementTraceabilityReasons.isEmpty();
        reasons.addAll(statementTraceabilityReasons);
        List<String> templateTodoSeamReasons = exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA
                ? contractSpecifications.stream().flatMap(spec -> ExerciseIntegrityGate.templateTodoSeamReasons(spec, templateFiles).stream()).distinct().toList()
                : List.of();
        boolean templateTodoSeamsHold = templateTodoSeamReasons.isEmpty();
        reasons.addAll(templateTodoSeamReasons);

        return new AgentVerifyReport(solution.tests(), solutionPassed, List.copyOf(solution.testFailedNames()), solution.failureEvidence(), template.tests(), templateCompiled,
                analysis.templateFailed(), template.failureEvidence(), templateWronglyPassing, List.copyOf(solution.testNames()),
                includeStatementChecks ? analysis.unresolvedTaskBindings() : List.of(), analysis.possiblyDeadFiles(),
                (includeStatementChecks ? analysis.actionableGatesPass() : analysis.testArtifactGatesPass()) && javaAresConventionsHold && approvedSpecificationHolds
                        && approvedTestPlanHolds && statementTraceabilityHolds && templateTodoSeamsHold,
                reasons, List.copyOf(readHiddenTestNames(sandbox, sessionId)));
    }

    /**
     * Validates the immutable build harness and offline sandbox image before any provider call. This runs the same pristine script and production build phases used by final
     * verification, preventing source-generation retries from spending tokens on an environment defect they cannot repair.
     *
     * @param sandbox   the sandbox that will host the generation job
     * @param sessionId its active session id
     * @param exercise  the exercise whose immutable build harness and phases are checked
     * @return an operator-actionable failure, or empty when the build emitted a JUnit report successfully
     */
    public Optional<String> checkBuildEnvironment(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        try {
            seedPristineVerifyScript(sandbox, sessionId, sandboxBuildCommandService.readinessVerifyScriptContent(exercise));
            PristineBuildExecution execution = runPristineBuildWithExecution(sandbox, sessionId, sandboxBuildCommandService.buildEnvironmentPreflightCommand(),
                    GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
            BuildSummary result = execution.summary();
            boolean trustedTestsRan = READINESS_TEST_NAMES.stream().allMatch(expected -> result.testNames().stream().anyMatch(name -> readinessNameMatches(name, expected)));
            if (!result.timedOut() && result.exitCode() == 0 && result.failures() == 0 && trustedTestsRan) {
                return Optional.empty();
            }
            log.warn("Sandbox build-environment readiness probe failed for project type {} (exit {}, parsed tests {}, failures {}). Build output: {}", exercise.getProjectType(),
                    result.exitCode(), result.tests(), result.failures(), boundedReadinessDiagnostic(execution.process().combinedOutput()));
            if (result.timedOut()) {
                return Optional.of("The sandbox build-environment readiness probe timed out before authoring began. Fix the build image or exercise scaffold; the authoring agent "
                        + "was not started.");
            }
            return Optional.of("The configured sandbox image and immutable exercise build harness failed the readiness probe before authoring began (exit " + result.exitCode()
                    + ", " + result.tests() + " parsed tests, " + result.failures() + " failures). Fix the build image or exercise scaffold; the authoring agent was not started.");
        }
        catch (RuntimeException exception) {
            log.warn("Could not run the sandbox build-environment readiness probe for project type {} ({}): {}", exercise.getProjectType(), exception.getClass().getSimpleName(),
                    boundedReadinessDiagnostic(exception.getMessage()));
            return Optional.of("The sandbox build environment could not be prepared before authoring began. Fix the build image or sandbox runtime; the authoring agent was not "
                    + "started.");
        }
    }

    /**
     * Redacts common credential forms and caps a readiness diagnostic before it reaches protected server logs. User-facing errors must remain generic and must not use this text.
     *
     * @param diagnostic raw build or exception output
     * @return bounded diagnostic text safe for protected logs
     */
    public static String boundedReadinessDiagnostic(@Nullable String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank()) {
            return "[no diagnostic output]";
        }
        String redacted = URI_CREDENTIALS.matcher(diagnostic).replaceAll("$1[REDACTED]@");
        redacted = AUTHORIZATION_CREDENTIALS.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = BEARER_CREDENTIALS.matcher(redacted).replaceAll("Bearer [REDACTED]");
        redacted = NAMED_CREDENTIALS.matcher(redacted).replaceAll("$1=[REDACTED]");
        redacted = ANSI_ESCAPE.matcher(redacted).replaceAll("").replace('\r', ' ');
        if (redacted.length() <= MAX_READINESS_DIAGNOSTIC_CHARS) {
            return redacted;
        }
        return redacted.substring(0, MAX_READINESS_DIAGNOSTIC_CHARS) + " ... [truncated]";
    }

    private static boolean readinessNameMatches(String actual, String expected) {
        String normalized = actual.endsWith("()") ? actual.substring(0, actual.length() - 2) : actual;
        return normalized.equals(expected) || normalized.endsWith("." + expected);
    }

    private static Map<String, String> readTestsRepositoryFiles(InteractiveSandbox sandbox, String sessionId) {
        return readRepositoryFiles(sandbox, sessionId, RepositoryType.TESTS);
    }

    /** The frozen approved specification, falling back to the live workspace only for a legacy/unapproved flow. */
    private List<String> contractSpecifications(InteractiveSandbox sandbox, String sessionId) {
        Optional<String> approved = approvedSpecs.approved(sessionId).filter(spec -> !spec.isBlank());
        if (approved.isPresent()) {
            return List.of(approved.get());
        }
        String liveSpec = readSpecDocument(sandbox, sessionId);
        return liveSpec.contains("## Design") && liveSpec.contains("## Testing Strategy") ? List.of(liveSpec) : List.of();
    }

    static Map<String, String> readRepositoryFiles(InteractiveSandbox sandbox, String sessionId, RepositoryType repositoryType) {
        String directory = GenerationWorkspaceService.directoryFor(repositoryType);
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, GenerationWorkspaceService.WORKSPACE + "/" + directory)) {
            return tar == null ? Map.of() : WorkspaceArchive.readTar(tar, directory);
        }
        catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    /**
     * Runs the shared, sandbox-dependent half of verification once: re-seeds and runs the two pristine builds, parses them with the production parsers, reads the problem
     * statement,
     * and applies every actionable gate (solution passes, template fails, task-binding presence/resolution, the two no-test-passes-template gates, and SCA parity). Both the
     * post-loop {@link #verify} and the in-loop {@link #selfCheck} consume this, so the agent's feedback and the verdict are computed by identical code.
     *
     * @param seededStructuralTestNames the authoritative seeded structural test names exempt from binding resolution (empty for the self-check)
     */
    private DifferentialAnalysis runDifferential(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Set<String> seededStructuralTestNames,
            @Nullable String producedProblemStatement, Runnable restoreCandidate) {
        List<String> reasons = new ArrayList<>();
        List<String> statementReasons = new ArrayList<>();

        restoreCandidate.run();
        seedPristineVerifyScript(sandbox, sessionId, exercise);
        BuildSummary solution = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineSolutionBuildCommand(),
                GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
        restoreCandidate.run();
        seedPristineVerifyScript(sandbox, sessionId, exercise);
        BuildSummary template = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineTemplateBuildCommand(),
                GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));

        int testCount = solution.tests();
        boolean solutionPassed = checkSolutionPasses(solution, reasons);
        boolean noDuplicateTestNames = checkNoDuplicateTestNames(solution, reasons);
        boolean templateBuildSound = checkTemplateBuildSound(solution, template, reasons);
        List<String> behaviouralTestNames = behaviouralTestNames(solution, seededStructuralTestNames);
        boolean hasBehaviouralTests = checkHasBehaviouralTests(behaviouralTestNames, reasons);
        List<String> gradableTestsPassingOnTemplate = templateBuildSound && hasBehaviouralTests ? gradableTestsPassingOnTemplate(behaviouralTestNames, template) : List.of();
        boolean templateFailed = templateBuildSound && hasBehaviouralTests && checkNoGradableTestPassesTemplate(gradableTestsPassingOnTemplate, reasons);

        // The exercise must bind its tests to the problem statement via [task][title](testNames), else the student sees no task checklist.
        String problemStatement = producedProblemStatement != null ? producedProblemStatement : readProblemStatement(sandbox, sessionId);
        boolean problemStatementHasTasks = ProblemStatementBindingChecker.hasTaskBindings(problemStatement);
        if (!problemStatementHasTasks) {
            statementReasons
                    .add("The problem statement has no Artemis task bindings. Add at least one line of the form [task][Title](testName) binding the graded tests to tasks so they "
                            + "appear as a checklist for students.");
        }

        List<String> malformedTaskKeywords = ProblemStatementBindingChecker.malformedTaskKeywords(problemStatement);
        boolean taskKeywordsWellFormed = malformedTaskKeywords.isEmpty();
        if (!taskKeywordsWellFormed) {
            statementReasons.add(
                    "These lines look like task bindings but use the wrong keyword " + malformedTaskKeywords + " instead of the exact lowercase singular [task]; they render as "
                            + "plain text and bind NO test (and leak the raw test names). Rewrite each as [task][Title](testName).");
        }

        // Compute once and let the gate decide; surfaced to the agent verbatim (guards the C++/Catch2 bare-name trap).
        Set<String> hiddenTestNames = readHiddenTestNames(sandbox, sessionId);
        List<String> unresolvedTaskBindings = ProblemStatementBindingChecker.unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        List<String> bindableTestNames = ProblemStatementBindingChecker.bindableTestNames(solution.testNames(), hiddenTestNames);
        boolean taskBindingsResolve = checkTaskBindingsResolve(unresolvedTaskBindings, bindableTestNames, problemStatementHasTasks, statementReasons);
        List<String> duplicateTaskBindings = ProblemStatementBindingChecker.duplicateTaskBindings(problemStatement);
        boolean noDuplicateTaskBindings = checkNoDuplicateTaskBindings(duplicateTaskBindings, problemStatementHasTasks, statementReasons);
        // Visibility is part of the binding contract: hidden tests are DELIBERATELY unbound, so they are exempt here and forbidden below. Both halves must move together —
        // exempting alone would let a bound hidden test through, forbidding alone would make the two gates unsatisfiable at once.
        List<String> unboundGradableTests = ProblemStatementBindingChecker.unboundGradableTestNames(problemStatement, solution.testNames(), testCount, hiddenTestNames);
        boolean allGradableTestsBound = checkAllGradableTestsBound(unboundGradableTests, problemStatementHasTasks, taskBindingsResolve, statementReasons);
        List<String> hiddenTestMentions = ProblemStatementBindingChecker.hiddenTestMentions(problemStatement, hiddenTestNames);
        boolean noHiddenTestsExposed = hiddenTestMentions.isEmpty();
        if (!noHiddenTestsExposed) {
            statementReasons.add(ProblemStatementBindingChecker.hiddenTestMentionsRejection(hiddenTestMentions));
        }
        boolean solutionScaClean = checkSolutionScaClean(exercise, solution, reasons);

        // Prose hygiene: the oracle is blind to what the student-facing statement exposes, so this gate blocks leaks of grader internals or bare task markers (with exact phrases).
        List<String> proseHygieneLeaks = ProblemStatementBindingChecker.proseHygieneLeaks(problemStatement);
        boolean proseHygienic = proseHygieneLeaks.isEmpty();
        if (!proseHygienic) {
            statementReasons.add("The problem statement leaks grader internals or stray task markers into student-facing prose: " + proseHygieneLeaks
                    + ". Rewrite it as a student would read it — describe the required behaviour and edge cases, never how the exercise is built, tested or graded, and bind tasks "
                    + "only via [task][Title](testName) lines.");
        }

        // Statement-shape defects the staged gate also enforces. Repair attempts run only this verifier — without the same checks here, a repair attempt can ship exactly the
        // statement the staged gate rejected (observed live: the spec said diagram yes, the gate failed the statement, and the repair attempt saved it diagram-less anyway).
        List<String> duplicateTaskTitles = ProblemStatementBindingChecker.duplicateTaskTitles(problemStatement);
        boolean taskTitlesUnique = duplicateTaskTitles.isEmpty();
        if (!taskTitlesUnique) {
            statementReasons.add("Multiple [task] lines share the same title: " + duplicateTaskTitles
                    + ". A title identifies ONE student work seam — merge each duplicated group into a single [task] line binding all of its tests.");
        }
        boolean statementVoiceOk = !ProblemStatementBindingChecker.writesAboutStudentsInThirdPerson(problemStatement);
        if (!statementVoiceOk) {
            statementReasons
                    .add("The problem statement writes ABOUT students in the third person ('Students must/will/should ...'). Address the reader directly: frame the goal as "
                            + "\"we\" and the work as \"you\" with imperative tasks.");
        }
        List<String> deadDiagramLinks = ProblemStatementBindingChecker.unresolvedTestsColorNames(problemStatement, solution.testNames(), seededStructuralTestNames);
        boolean diagramLinksResolve = deadDiagramLinks.isEmpty();
        if (!diagramLinksResolve) {
            statementReasons.add("These diagram testsColor(...) names match no actual test: " + deadDiagramLinks
                    + ". Use exact behavioural or seeded structural check names, or remove the link.");
        }
        boolean noStrayUmlDirectives = !ProblemStatementBindingChecker.hasStrayPlantUmlDirectives(problemStatement);
        if (!noStrayUmlDirectives) {
            statementReasons
                    .add("PlantUML directives ('hide empty fields', 'hide empty methods', 'skinparam ...') sit OUTSIDE the @startuml...@enduml block and render as stray text. "
                            + "Move them inside the block, directly before @enduml.");
        }
        List<String> duplicateHeadings = ProblemStatementBindingChecker.duplicateHeadings(problemStatement);
        boolean headingsUnique = duplicateHeadings.isEmpty();
        if (!headingsUnique) {
            statementReasons.add("The statement repeats these headings verbatim: " + duplicateHeadings + ". Merge or remove the duplicate sections.");
        }
        // The approved specification's diagram decision outranks the live file: a run under statement-gate pressure rewrote '## Diagram' from yes to no and the gate then
        // passed vacuously. A later edit may promise a diagram, never un-promise one.
        boolean diagramPromised = ProblemStatementBindingChecker.specPromisesDiagram(readSpecDocument(sandbox, sessionId))
                || approvedSpecs.approved(sessionId).filter(ProblemStatementBindingChecker::specPromisesDiagram).isPresent();
        boolean statementHonoursDiagramPromise = !(diagramPromised && !problemStatement.contains("@startuml"));
        if (!statementHonoursDiagramPromise) {
            statementReasons
                    .add("SPEC.md's '## Diagram' section says yes, but the statement contains no @startuml diagram. Add the PlantUML class diagram (with testsColor links) after "
                            + "the tasks it illustrates. The accepted diagram decision cannot be revoked after the specification gate.");
        }

        boolean testArtifactGatesPass = solutionPassed && noDuplicateTestNames && templateFailed && testCount > 0 && solutionScaClean;
        boolean actionableGatesPass = testArtifactGatesPass && problemStatementHasTasks && taskKeywordsWellFormed && taskBindingsResolve && noDuplicateTaskBindings
                && allGradableTestsBound && proseHygienic && taskTitlesUnique && statementVoiceOk && diagramLinksResolve && noStrayUmlDirectives && headingsUnique
                && statementHonoursDiagramPromise && noHiddenTestsExposed;

        List<String> possiblyDeadFiles = possiblyDeadWorkspaceFiles(sandbox, sessionId);
        List<String> actionableReasons = new ArrayList<>(reasons);
        actionableReasons.addAll(statementReasons);
        return new DifferentialAnalysis(solution, template, solutionPassed, templateFailed, testArtifactGatesPass, actionableGatesPass, List.copyOf(reasons),
                List.copyOf(actionableReasons), unresolvedTaskBindings, possiblyDeadFiles, gradableTestsPassingOnTemplate);
    }

    /**
     * The shared, sandbox-dependent half of verification: the two parsed build summaries plus the actionable gate outcome. Consumed by both the post-loop {@link #verify} (which
     * adds
     * the integrity gates and verdict) and the in-loop {@link #selfCheck} (which renders the agent observation).
     *
     * @param actionableGatesPass            whether every sandbox-dependent gate held (the integrity gates are layered on top by {@link #verify})
     * @param actionableReasons              the reasons any sandbox-dependent gate failed (empty when all hold); the same wording {@link #verify} surfaces
     * @param unresolvedTaskBindings         the {@code [task]} bindings referencing no real test (surfaced to the agent verbatim)
     * @param possiblyDeadFiles              best-effort files present in only one assignment repository (advisory; empty when unavailable)
     * @param gradableTestsPassingOnTemplate the exact, non-structural, non-build-gate solution test names that pass on the template (empty when the template did not build
     *                                           soundly, or none pass); consumed both by the actionable gate and by {@link #selfCheck}'s agent-facing report so the two never
     *                                           diverge
     */
    private record DifferentialAnalysis(BuildSummary solution, BuildSummary template, boolean solutionPassed, boolean templateFailed, boolean testArtifactGatesPass,
            boolean actionableGatesPass, List<String> testArtifactReasons, List<String> actionableReasons, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles,
            List<String> gradableTestsPassingOnTemplate) {
    }

    /**
     * Runs one pristine build for the given assignment, then copies out the build-fresh reports from the verifier-owned reports dir (a constant path, never derived from agent
     * output) and parses them with the production parsers into a {@link BuildSummary}. The tar is validated by {@link CollectedReports} before any byte is parsed.
     *
     * @param assignmentName the assignment directory name ({@code solution}/{@code template}); also the reports subdir name and the copyOut prefix
     */
    private BuildSummary runPristineBuild(InteractiveSandbox sandbox, String sessionId, String buildCommand, String assignmentName) {
        return runPristineBuildWithExecution(sandbox, sessionId, buildCommand, assignmentName).summary();
    }

    private PristineBuildExecution runPristineBuildWithExecution(InteractiveSandbox sandbox, String sessionId, String buildCommand, String assignmentName) {
        SandboxExecResult run;
        try {
            run = sandbox.exec(sessionId, VERIFY_TIMEOUT, "sh", "-c", buildCommand);
        }
        catch (RuntimeException exception) {
            throw new VerificationInfrastructureException("The verifier could not execute the " + assignmentName + " build", exception);
        }
        if (run.timedOut()) {
            // InteractiveSandbox destroys a session whose command exceeds its deadline. Continuing or retrying in that session would only spend another provider/verifier call.
            throw VerificationInfrastructureException.sessionLost("The " + assignmentName + " build exceeded its sandbox deadline");
        }
        Map<String, byte[]> reports;
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, SandboxBuildCommandService.reportsDirectoryFor(assignmentName))) {
            // Docker prefixes copied-out entries with the source directory name; strip it for the flat collected names.
            reports = tar == null ? Map.of() : CollectedReports.read(tar, assignmentName);
        }
        catch (CollectedReports.RejectedReportException e) {
            throw VerificationInfrastructureException.reportRejected("The verifier rejected the " + assignmentName + " reports archive", e);
        }
        catch (IOException e) {
            throw new VerificationInfrastructureException("The verifier could not read the " + assignmentName + " reports archive", e);
        }
        catch (RuntimeException e) {
            throw new VerificationInfrastructureException(
                    "The verifier could not retrieve the " + assignmentName + " reports archive; build process: " + boundedReadinessDiagnostic(run.combinedOutput()), e);
        }
        return new PristineBuildExecution(BuildSummary.fromReports(reports, run.exitCode()), run);
    }

    private record PristineBuildExecution(BuildSummary summary, SandboxExecResult process) {
    }

    /** The solution gate: the solution must compile, run at least one test, and pass every test. Appends a rejection reason to {@code reasons} otherwise. */
    private static boolean checkSolutionPasses(BuildSummary solution, List<String> reasons) {
        boolean solutionPassed = !solution.timedOut() && solution.exitCode() == 0 && solution.tests() > 0 && solution.failures() == 0;
        if (solution.timedOut()) {
            reasons.add("The solution build timed out. The solution must compile and pass every test within the time limit.");
        }
        else if (solution.tests() == 0) {
            reasons.add("No tests were detected when building against the solution. The exercise must contain at least one meaningful test, and the solution must compile so the "
                    + "tests can run.");
        }
        else if (!solutionPassed) {
            reasons.add(
                    "The solution does not pass its own tests (" + solution.failures() + " failing of " + solution.tests() + "). The solution must compile and pass every test.");
        }
        return solutionPassed;
    }

    private static boolean checkNoDuplicateTestNames(BuildSummary solution, List<String> reasons) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String name : solution.testNames()) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                duplicates.add(name);
            }
        }
        if (!duplicates.isEmpty()) {
            reasons.add("Duplicate test names are not production-gradeable: " + duplicates
                    + ". Rename the generated tests so every test case name is unique (case-insensitive), otherwise Artemis production grading marks duplicates and keeps the "
                    + "result at zero.");
            return false;
        }
        return true;
    }

    /**
     * The template's build-level sanity: it must compile (a non-empty test count) and run exactly the same tests as the solution, without timing out. This is a prerequisite for
     * {@link #gradableTestsPassingOnTemplate}, which decides the per-test starter-credit policy; a template that fails this cannot be meaningfully checked test-by-test. Appends a
     * rejection reason otherwise.
     *
     * @param solution the solution build summary (its test count is the reference)
     */
    private static boolean checkTemplateBuildSound(BuildSummary solution, BuildSummary template, List<String> reasons) {
        if (template.timedOut()) {
            reasons.add("The template build timed out; it must compile and fail the tests quickly.");
            return false;
        }
        if (template.tests() == 0) {
            reasons.add("The template does not compile (the tests never ran). The template must compile and only fail because the student's work is missing — use placeholder "
                    + "method bodies (returning null, 0, false) with the same signatures as the solution.");
            return false;
        }
        if (solution.tests() > 0 && template.tests() != solution.tests()) {
            // A differing count means the template silently dropped tests, letting a vacuous template "fail" without the tests discriminating.
            reasons.add("The template runs a different number of tests (" + template.tests() + ") than the solution (" + solution.tests()
                    + "). Both must run the same tests; the template must differ only in its (unimplemented) method bodies, not in which tests compile and run.");
            return false;
        }
        return true;
    }

    /** Returns the real behavioural tests after excluding build gates and the structural checks seeded by Artemis. */
    private static List<String> behaviouralTestNames(BuildSummary solution, Set<String> seededStructuralTestNames) {
        Set<String> structural = seededStructuralTestNames == null ? Set.of()
                : seededStructuralTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        List<String> behavioural = new ArrayList<>();
        for (String rawName : solution.testNames()) {
            String normalized = ProblemStatementBindingChecker.normalizeTestName(rawName);
            if (isBuildGateTest(normalized) || structural.contains(normalized)) {
                continue;
            }
            behavioural.add(rawName);
        }
        return behavioural;
    }

    private static boolean checkHasBehaviouralTests(List<String> behaviouralTestNames, List<String> reasons) {
        if (!behaviouralTestNames.isEmpty()) {
            return true;
        }
        reasons.add("The exercise has no behavioural tests. Add at least one behavioural test that passes on the solution and fails on the template; build gates and structural "
                + "checks alone do not verify the required student behaviour.");
        return false;
    }

    private static List<String> gradableTestsPassingOnTemplate(List<String> behaviouralTestNames, BuildSummary template) {
        Set<String> templateFailed = template.testFailedNames().stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        List<String> passing = new ArrayList<>();
        for (String rawName : behaviouralTestNames) {
            String normalized = ProblemStatementBindingChecker.normalizeTestName(rawName);
            if (!templateFailed.contains(normalized)) {
                passing.add(rawName);
            }
        }
        return passing;
    }

    /** Appends a rejection reason naming every offending test when {@code gradableTestsPassingOnTemplate} is non-empty; returns whether the gate holds. */
    private static boolean checkNoGradableTestPassesTemplate(List<String> gradableTestsPassingOnTemplate, List<String> reasons) {
        if (gradableTestsPassingOnTemplate.isEmpty()) {
            return true;
        }
        reasons.add("These non-structural gradable tests pass on the template but must fail: " + gradableTestsPassingOnTemplate
                + ". Starter credit is allowed only for structural tests seeded by the exercise scaffold (checking a class/method/attribute/constructor shape exists) — every other "
                + "test is behavioural and a student who has not started must not pass it. Strip the template's implementation to a wrong placeholder for each of these until it "
                + "fails.");
        return false;
    }

    /**
     * The binding-resolution gate: a {@code [task]}'s names must be real runner test names, not a {@code @DisplayName} or prose title; a binding that resolves to nothing shows no
     * progress in Artemis, which the differential build cannot detect. Decides from the precomputed unresolved list and fails open when no trustworthy set was emitted.
     *
     * @param unresolvedTaskBindings the precomputed {@code [task]} bindings that resolve to no real test (the C++/Catch2 bare-name trap)
     */
    private static boolean checkTaskBindingsResolve(List<String> unresolvedTaskBindings, List<String> bindableTestNames, boolean problemStatementHasTasks, List<String> reasons) {
        boolean taskBindingsResolve = unresolvedTaskBindings.isEmpty();
        if (problemStatementHasTasks && !taskBindingsResolve) {
            reasons.add("These [task] bindings reference names that match no actual test: " + unresolvedTaskBindings + ". A [task]'s parenthesised names must be the exact test "
                    + "method/function names (e.g. testSortsAscending), not a @DisplayName or a prose title — otherwise the task shows no result in Artemis. The actual test names are: "
                    + bindableTestNames + ". Fix the [task] lines (or rename the tests) so every binding references a real, visible test name.");
        }
        return taskBindingsResolve;
    }

    private static boolean checkNoDuplicateTaskBindings(List<String> duplicateTaskBindings, boolean problemStatementHasTasks, List<String> reasons) {
        boolean noDuplicateTaskBindings = duplicateTaskBindings.isEmpty();
        if (problemStatementHasTasks && !noDuplicateTaskBindings) {
            reasons.add("These tests are bound more than once by [task] entries: " + duplicateTaskBindings
                    + ". Bind each graded test exactly once so the student-facing checklist is unambiguous and matches production grading.");
        }
        return noDuplicateTaskBindings;
    }

    private static boolean checkAllGradableTestsBound(List<String> unboundGradableTests, boolean problemStatementHasTasks, boolean taskBindingsResolve, List<String> reasons) {
        boolean allGradableTestsBound = unboundGradableTests.isEmpty();
        if (problemStatementHasTasks && taskBindingsResolve && !allGradableTestsBound) {
            reasons.add("These real gradable tests are not bound by any [task] entry even though production will grade them: " + unboundGradableTests
                    + ". Add each to the [task] line of the seam it belongs to — one line takes a comma-separated list, [task][Title](testA,testB,testC) — never a new [task] "
                    + "line per test. Tests the grading plan marks AFTER_DUE_DATE are exempt and must stay unbound.");
        }
        return allGradableTestsBound;
    }

    /**
     * The SCA-parity gate: SCA reports carry no {@code <testcase>}, so the differential oracle is blind to them while production folds a penalty into the score. The solution's SCA
     * findings are handed to {@link ScaPenaltyParity}, which flags those production would penalise; silent and verdict-unchanged when none would dock.
     */
    private boolean checkSolutionScaClean(ProgrammingExercise exercise, BuildSummary solution, List<String> reasons) {
        List<String> penalisingScaFindings = penalisingScaFindings(exercise, solution);
        boolean solutionScaClean = penalisingScaFindings.isEmpty();
        if (!solutionScaClean) {
            reasons.add("The reference solution produces static-code-analysis findings that production would penalise (graded SCA categories): " + penalisingScaFindings
                    + ". With static code analysis enabled and a graded penalty, a student's score is docked for these — so the reference solution, which must grade 100%, would not. "
                    + "Make the reference solution clean of these graded SCA findings (fix the flagged code, or it must not trip the graded categories) before the exercise can ship.");
        }
        return solutionScaClean;
    }

    /**
     * Fail-closed on a read-back gap: a repo seeded non-empty but extracted empty silently disables the harness/leak gates, so we reject rather than accept on that doubt. (A
     * genuinely empty repo is reported as not failed and stays fail-open.)
     */
    private static boolean checkExtractionSound(Set<String> extractionFailedRepositories, List<String> reasons) {
        boolean extractionSound = extractionFailedRepositories == null || extractionFailedRepositories.isEmpty();
        if (!extractionSound) {
            reasons.add("The generated files for these repositories could not be safely extracted for integrity verification: " + extractionFailedRepositories
                    + ". The harness-immutability and solution-leak checks cannot run on an incomplete or unrepresentable tree, so the exercise cannot be verified. This can be "
                    + "caused by a temporary copy-out failure, generated binary or mode changes, or repository-layout residue. Retry once; if it persists, inspect the generated "
                    + "workspace and build outputs.");
        }
        return extractionSound;
    }

    /**
     * Runs one pristine build for a single assignment ({@code solution} or {@code template}) without a paired differential, for callers that only need "did it build, and which
     * tests failed" before authoring the next stage — currently {@link StageCheckService}'s per-stage compile gates. Re-seeds the pristine verify script first (idempotent), the
     * same as the two-build {@link #runDifferential}, so this is also the reseed point for a fresh session's very first build (see {@link #ensurePristineVerifyScript}).
     * <p>
     * Reuses the same build-and-parse machinery ({@link #runPristineBuildWithExecution}) that {@link #runDifferential} calls twice, so a single-assignment caller and the full
     * differential can never observe a different build for the same assignment.
     *
     * @param sandbox    the open sandbox session the pristine build runs in
     * @param sessionId  the sandbox session id
     * @param exercise   the exercise being built (drives the per-language build recipe)
     * @param assignment {@code solution} or {@code template}
     * @return the bounded, caller-facing projection of the build
     */
    /**
     * Runs candidate contract witnesses against the reference solution and returns the ones that demonstrably ran and passed.
     * <p>
     * Costs one pristine solution build regardless of how many witnesses are offered. The witnesses are carried by a single throwaway probe class written beside the graded suite;
     * it is removed again immediately, and because it never replaces an existing file, a crash before the removal leaves the graded suite itself untouched.
     * <p>
     * Every failure path returns no witnesses rather than propagating: this is an advisory signal layered on top of an already-passing candidate, so a broken probe must cost the
     * exercise nothing.
     *
     * @param sandbox            the open sandbox session
     * @param sessionId          the sandbox session id
     * @param exercise           the exercise being built (drives the build recipe)
     * @param producedTestsFiles the tests repository as produced, providing both the collision check and the source of the probe's package and imports
     * @param candidates         the unvalidated witnesses
     * @return the witnesses the reference solution actually satisfied
     */
    public List<ContractWitness> validateContractWitnesses(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> producedTestsFiles,
            List<ContractWitness> candidates) {
        if (candidates.isEmpty() || producedTestsFiles.isEmpty() || exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return List.of();
        }
        Optional<Map.Entry<String, String>> host = producedTestsFiles.entrySet().stream().filter(entry -> entry.getKey().endsWith(".java")
                && !ExerciseIntegrityGate.isHarnessFile(entry.getKey()) && entry.getValue() != null && entry.getValue().contains("package ")).min(Map.Entry.comparingByKey());
        if (host.isEmpty()) {
            return List.of();
        }
        String probePath = ContractWitnessProbe.probePath(host.get().getKey(), producedTestsFiles.keySet());
        String probeSource = ContractWitnessProbe.buildProbeSource(host.get().getValue(), candidates);
        if (probePath == null || probeSource.isBlank()) {
            return List.of();
        }
        String workspacePath = GenerationWorkspaceService.directoryFor(RepositoryType.TESTS) + "/" + probePath;
        try {
            sandbox.copyIn(sessionId, GenerationWorkspaceService.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(Map.of(workspacePath, probeSource), Map.of()));
            seedPristineVerifyScript(sandbox, sessionId, exercise);
            BuildSummary solution = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineSolutionBuildCommand(),
                    GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
            List<ContractWitness> validated = ContractWitnessProbe.validated(solution.testNames(), solution.testFailedNames(), candidates);
            log.info("Contract-witness probe for exercise {}: {} of {} candidate witnesses were satisfied by the reference solution", exercise.getId(), validated.size(),
                    candidates.size());
            return validated;
        }
        catch (RuntimeException e) {
            log.warn("The contract-witness probe could not run for exercise {}: {}", exercise.getId(), e.getMessage());
            return List.of();
        }
        finally {
            removeContractWitnessProbe(sandbox, sessionId, workspacePath);
        }
    }

    /**
     * Deletes the throwaway probe. The graded suite is never at risk here: the path was rejected earlier if any produced file already used it, so this can only remove the file
     * this probe wrote.
     */
    private void removeContractWitnessProbe(InteractiveSandbox sandbox, String sessionId, String workspacePath) {
        try {
            SandboxExecResult removal = sandbox.exec(sessionId, READ_TIMEOUT, "rm", "-f", GenerationWorkspaceService.WORKSPACE + "/" + workspacePath);
            if (!removal.isSuccess()) {
                log.warn("The contract-witness probe {} could not be removed; the read-back residue strip is the remaining guard", workspacePath);
            }
        }
        catch (RuntimeException e) {
            log.warn("The contract-witness probe {} could not be removed: {}", workspacePath, e.getMessage());
        }
    }

    public SingleBuildResult singleBuild(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, String assignment) {
        ensurePristineVerifyScript(sandbox, sessionId, exercise);
        String buildCommand = "solution".equals(assignment) ? sandboxBuildCommandService.pristineSolutionBuildCommand() : sandboxBuildCommandService.pristineTemplateBuildCommand();
        PristineBuildExecution execution = runPristineBuildWithExecution(sandbox, sessionId, buildCommand, assignment);
        BuildSummary summary = execution.summary();
        return new SingleBuildResult(summary.exitCode(), summary.tests(), summary.failures(), summary.testFailedNames(),
                boundedReadinessDiagnostic(execution.process().combinedOutput()));
    }

    /**
     * Public seam for the staged runner's compile gates: until the first verification (or in-loop self-check) runs, {@code /opt/hyperion/verify.sh} still holds the
     * readiness-probe variant from session bootstrap, whose fixture is already consumed — invoking it fails with "build-readiness fixture is unavailable" (exit 66).
     * Idempotent: re-renders and overwrites the pristine script.
     *
     * @param sandbox   the sandbox hosting the workspace
     * @param sessionId the sandbox session
     * @param exercise  the exercise whose build recipe the script encodes
     */
    public void ensurePristineVerifyScript(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        seedPristineVerifyScript(sandbox, sessionId, exercise);
    }

    /**
     * Recreates the verifier control directory and renders a fresh {@code verify.sh}. This discards any files left by the agent or an earlier in-loop verification before the
     * authoritative pass.
     */
    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        seedPristineVerifyScript(sandbox, sessionId, sandboxBuildCommandService.verifyScriptContent(exercise));
    }

    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, String script) {
        try {
            // Docker's copy-to-container requires the destination directory to already exist.
            SandboxExecResult preparation = sandbox.exec(sessionId, READ_TIMEOUT, "sh", "-c", "find " + SandboxBuildCommandService.PRISTINE_VERIFY_DIR + " -mindepth 1 -delete");
            if (!preparation.isSuccess()) {
                throw new VerificationInfrastructureException("The verifier could not prepare its script directory", null);
            }
            sandbox.copyIn(sessionId, SandboxBuildCommandService.PRISTINE_VERIFY_DIR, singleFileTar(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, script));
        }
        catch (VerificationInfrastructureException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new VerificationInfrastructureException("The verifier could not install its pristine script", exception);
        }
    }

    /** Builds a one-entry tar carrying {@code name} with the given UTF-8 content and an executable mode, for {@link InteractiveSandbox#copyIn}. */
    private static InputStream singleFileTar(String name, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(bytes.length);
            entry.setMode(PRISTINE_SCRIPT_MODE);
            tar.putArchiveEntry(entry);
            tar.write(bytes);
            tar.closeArchiveEntry();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * Whether a name is a build/compile/configure gate exempt from the production-parity gate; single source of truth shared with the persistence step
     * ({@link BuildGateTestNames}).
     */
    private static boolean isBuildGateTest(String normalizedName) {
        return BuildGateTestNames.isBuildGate(normalizedName);
    }

    /**
     * The solution-build SCA findings production would penalise, rendered as {@code <TOOL>|<category>}; empty when SCA cannot dock the solution. Reads the persisted categories the
     * same way production grading does ({@code findByExerciseId}), so the decision matches {@code calculateTotalPenalty}; fails open when the repository is absent.
     */
    private List<String> penalisingScaFindings(ProgrammingExercise exercise, BuildSummary solution) {
        // Short-circuit before the DB read on the common non-SCA path.
        if (solution.scaFindings().isEmpty() || !Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) || exercise.getId() == null
                || staticCodeAnalysisCategoryRepository.isEmpty()) {
            return List.of();
        }
        var categories = staticCodeAnalysisCategoryRepository.get().findByExerciseId(exercise.getId());
        return ScaPenaltyParity.penalisingFindings(exercise, categories, solution.scaFindings()).stream().map(f -> f.tool() + "|" + f.category()).toList();
    }

    /**
     * The aggregated test outcome of one {@code verify.sh} run, built by parsing the collected report files with the same production parsers LocalCI uses
     * ({@link TestResultXmlParser}
     * for JUnit, {@link ReportParser} for SCA), so the oracle's view is parity-by-construction with grading.
     *
     * @param tests           tests that ran (zero when the build did not reach the runner, e.g. a compile error); excludes {@code <skipped>} cases, as production grades
     * @param testNames       distinct test-case names from the JUnit XML, composed as production does; empty if none collected
     * @param testFailedNames distinct names of cases that failed/errored; used by the strict per-test gate; empty if none collected
     * @param failureEvidence bounded, sanitized names and first useful failure messages for agent feedback
     * @param scaFindings     SCA findings (tool + real derived category from {@link ReportParser}); populated only when the SCA reports were collected; empty otherwise
     */
    record BuildSummary(int tests, int failures, int exitCode, boolean timedOut, List<String> testNames, List<String> testFailedNames,
            List<AgentVerifyReport.TestFailureEvidence> failureEvidence, List<ScaPenaltyParity.ScaFinding> scaFindings) {

        /** Build killed for exceeding its timeout; treated as a failed build with no tests. */
        static BuildSummary timedOut(int exitCode) {
            return new BuildSummary(0, 0, exitCode, true, List.of(), List.of(), List.of(), List.of());
        }

        static BuildSummary fromReports(Map<String, byte[]> reports, int exitCode) {
            List<LocalCITestJobDTO> failed = new ArrayList<>();
            List<LocalCITestJobDTO> successful = new ArrayList<>();
            List<ScaPenaltyParity.ScaFinding> scaFindings = new ArrayList<>();
            for (Map.Entry<String, byte[]> report : reports.entrySet()) {
                String canonical = canonicalToken(report.getKey());
                String content = CollectedReports.asString(report.getValue());
                if (SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN.equals(canonical)) {
                    try {
                        TestResultXmlParser.processTestResultFile(content, failed, successful);
                    }
                    catch (IOException | RuntimeException e) {
                        throw VerificationInfrastructureException.reportRejected("The verifier could not parse JUnit report " + report.getKey(), e);
                    }
                }
                else {
                    parseScaReport(content, canonical, scaFindings);
                }
            }
            List<String> testNames = new ArrayList<>();
            List<String> failedNames = new ArrayList<>();
            List<AgentVerifyReport.TestFailureEvidence> failureEvidence = new ArrayList<>();
            failed.forEach(job -> {
                testNames.add(job.name());
                failedNames.add(job.name());
                failureEvidence.add(AgentVerifyReport.TestFailureEvidence.from(job.name(), job.testMessages()));
            });
            successful.forEach(job -> testNames.add(job.name()));
            int tests = failed.size() + successful.size();
            return new BuildSummary(tests, failed.size(), exitCode, false, List.copyOf(testNames), List.copyOf(failedNames), List.copyOf(failureEvidence),
                    List.copyOf(scaFindings));
        }

        private static void parseScaReport(String content, String canonicalFileName, List<ScaPenaltyParity.ScaFinding> scaFindings) {
            try {
                StaticCodeAnalysisReportDTO report = ReportParser.getReport(content, canonicalFileName);
                if (report == null || report.issues() == null || report.tool() == null) {
                    return;
                }
                String tool = report.tool().name();
                for (StaticCodeAnalysisIssue issue : report.issues()) {
                    scaFindings.add(new ScaPenaltyParity.ScaFinding(tool, issue.category()));
                }
            }
            catch (UnsupportedToolException e) {
                log.debug("No SCA parser for collected report {}: {}", canonicalFileName, e.getMessage());
            }
            catch (RuntimeException e) {
                throw VerificationInfrastructureException.reportRejected("The verifier could not parse SCA report " + canonicalFileName, e);
            }
        }

        /** The canonical routing token a collected file name carries (the segment after the {@code <seq>__} prefix): the JUnit token or an SCA tool's canonical report name. */
        private static String canonicalToken(String collectedName) {
            int sep = collectedName.indexOf(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR);
            return sep < 0 ? collectedName : collectedName.substring(sep + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR.length());
        }
    }

    /** Signals a verifier transport, archive-integrity, or report-parsing failure that authoring cannot repair reliably. */
    public static final class VerificationInfrastructureException extends RuntimeException {

        private final boolean retryableInSameSession;

        public VerificationInfrastructureException(String message, Throwable cause) {
            this(message, cause, true);
        }

        private VerificationInfrastructureException(String message, Throwable cause, boolean retryableInSameSession) {
            super(message, cause);
            this.retryableInSameSession = retryableInSameSession;
        }

        public static VerificationInfrastructureException sessionLost(String message) {
            return new VerificationInfrastructureException(message, null, false);
        }

        public static VerificationInfrastructureException reportRejected(String message, Throwable cause) {
            return new VerificationInfrastructureException(message, cause, false);
        }

        public boolean isRetryableInSameSession() {
            return retryableInSameSession;
        }
    }
}
