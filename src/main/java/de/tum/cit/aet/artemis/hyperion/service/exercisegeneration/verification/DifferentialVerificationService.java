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

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.CollectedReports;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;

/**
 * Independently verifies generated exercises by building solution and template from a pristine script and parsing verifier-owned reports with the production LocalCI parsers, so
 * the oracle's view of a build is the same one grading will take (parity by construction).
 * <p>
 * The enforced starter-credit policy: the solution must pass every test, and the template must compile and run the same tests while failing every non-structural gradable one.
 * Build/compile/configure gates are exempt because they only gate compilation, and structural tests seeded by {@link StructuralOracleSeedingService} may legitimately pass, as
 * Artemis's own reference exercises give early structural credit for a correctly-shaped stub. There is no "at least half" or "at least one per task" leniency: each individual
 * non-structural gradable test that passes on the template is an actionable rejection naming that test.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class DifferentialVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DifferentialVerificationService.class);

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(10);

    private static final Set<String> READINESS_TEST_NAMES = Set.of("testPublicApi", "testRepresentativeScores", "testBoundaryScores", "testEmptyInput");

    private static final int PRISTINE_SCRIPT_MODE = 0755;

    private static final int MAX_READINESS_DIAGNOSTIC_CHARS = 4_000;

    private static final int READINESS_DIAGNOSTIC_HEAD_CHARS = 700;

    private static final Pattern URI_CREDENTIALS = Pattern.compile("(?i)(https?://)[^\\s/@]+@");

    private static final Pattern AUTHORIZATION_CREDENTIALS = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(?:basic|bearer)?\\s*[^\\s,;]+");

    private static final Pattern BEARER_CREDENTIALS = Pattern.compile("(?i)\\bbearer\\s+[^\\s,;]+");

    private static final Pattern NAMED_CREDENTIALS = Pattern.compile("(?i)\\b(password|passwd|token|secret|api[-_]?key)\\s*[:=]\\s*[^\\s,;]+");

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

    private final SandboxBuildCommandService sandboxBuildCommandService;

    /**
     * Persisted SCA categories, read the same way production grading does, so the SCA-parity gate decides from authoritative state rather than a detached in-memory collection.
     * Optional because SCA categories live in the core profile; on a node without them the gate fails open, and that node cannot grade anyway.
     */
    private final Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository;

    private final ApprovedSpecRegistry approvedSpecs;

    // Required: with several constructors and no annotation, Spring cannot pick one.
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
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "cat",
                GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        return result.isSuccess() ? result.stdout() : "";
    }

    /**
     * The normalized test names the workspace's grading plan hides until the due date. Fail-open by construction: a missing, unreadable, or malformed {@code test-plan.json}
     * yields an empty set, so the binding gate demands that every gradable test be bound rather than silently relaxing.
     */
    private Set<String> readHiddenTestNames(InteractiveSandbox sandbox, String sessionId) {
        try {
            SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
            if (!result.isSuccess() || result.stdout() == null || result.stdout().isBlank()) {
                return Set.of();
            }
            return GeneratedTestPlan.parse(result.stdout()).hiddenEntries().stream().map(GeneratedTestPlan.Entry::name).map(ProblemStatementBindingChecker::normalizeTestName)
                    .collect(Collectors.toUnmodifiableSet());
        }
        catch (RuntimeException e) {
            return Set.of();
        }
    }

    private static String readWorkspaceRootFile(InteractiveSandbox sandbox, String sessionId, String filename) {
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/" + filename);
        return result.isSuccess() ? result.stdout() : "";
    }

    /** Cap on the dead-file probe output, so a runaway workspace listing cannot blow the agent's context. */
    private static final int MAX_POSSIBLY_DEAD_FILES = 20;

    /**
     * Advisory (never a gate) language-agnostic probe for the agent's self-check: a source file present in exactly one of {@code solution/} and {@code template/} is a likely
     * abandoned orphan, because the two repositories should differ only in method bodies. Ignores build manifests and hidden files and never throws.
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
     * Build manifests are legitimately repository-specific, so the dead-file probe must not flag them. Only Maven is listed because generation runs for Java/Maven exercises
     * alone (see {@link de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.LanguageGenerationProfile}).
     */
    private static final Set<String> BUILD_MANIFEST_NAMES = Set.of("pom.xml");

    /** Empty on any non-success, so the advisory disappears rather than misreporting. */
    private static Set<String> listSourceFiles(InteractiveSandbox sandbox, String sessionId, String repoDirectory) {
        String repoRoot = GenerationWorkspaceService.WORKSPACE + "/" + repoDirectory;
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c",
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
     * Runs the differential verification and the sandbox-free integrity gates; mechanical verification passes only when both do. The authoritative pass wipes and re-seeds the
     * verifier control directory, builds in fresh temporary directories, and counts only reports written during the build. The integrity gates fail open on genuinely-empty
     * inputs but fail closed on {@code extractionFailedRepositories}, so a flaky read-back cannot silently disable a gate.
     *
     * @param sandbox          the open sandbox session the pristine builds run in
     * @param sessionId        the sandbox session id
     * @param exercise         the exercise being verified (drives the per-language build recipe)
     * @param request          the produced artifacts and integrity-gate inputs to decide on (see {@link VerificationRequest})
     * @param restoreCandidate resets the sandbox and re-materializes the exact captured candidate before each of the two builds, so generated tests or detached processes cannot
     *                             change the second build's input and make the verifier approve a tree different from the one persistence receives
     * @return the mechanical verdict (verified, solution-passed, template-failed, test count, and rejection reasons)
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, VerificationRequest request, Runnable restoreCandidate) {
        // Shared with the in-loop self-check so the agent's `verify` tool and this mechanical decision cannot diverge; only the integrity gates and the verdict are layered here.
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, request.seededStructuralTestNames(), request.producedProblemStatement(), restoreCandidate);
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();
        List<String> reasons = new ArrayList<>(analysis.actionableReasons());

        // Integrity gates the build cannot see. Post-loop only (the self-check skips them): they need the seed snapshot and read-back files the agent loop lacks mid-session.
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
        List<String> gradingContextSniffingReasons = new ArrayList<>(
                ExerciseIntegrityGate.gradingContextSniffingReasons(request.producedTemplateFiles(), request.producedSolutionFiles()));
        // The same defect seen from the tests side: a graded test reading the solution/template/assignment tree grades source text rather than behaviour.
        gradingContextSniffingReasons.addAll(ExerciseIntegrityGate.gradedTestsReadingSourceTreeReasons(request.producedTestsFiles()));
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
        String approvedSpec = String.join("\n\n", contractSpecifications);
        List<String> approvedTestPlanReasons = ExerciseIntegrityGate.approvedTestPlanReasons(approvedSpec, request.producedTestPlan(), solution.testNames(),
                exercise.getDueDate() != null, request.seededStructuralTestNames());
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

        // An adapt retaining none of the pre-adapt graded names is a from-scratch regeneration mislabeled as an adapt, which the internally-consistent differential cannot see.
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

    public AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles, boolean adaptation,
            Set<String> seededStructuralTestNames) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, adaptation, true, seededStructuralTestNames);
    }

    public AgentVerifyReport selfCheckTestsStage(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            Set<String> seededStructuralTestNames) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, false, false, seededStructuralTestNames);
    }

    /**
     * The in-loop self-check the agent's {@code verify} tool calls: the same two pristine builds, production parse and actionable gates as {@link #verify}, rendered as an
     * agent-readable {@link AgentVerifyReport}. It skips the sandbox-free integrity gates, so {@code wouldBeAccepted} covers the differential and actionable gates only and proves
     * nothing about semantic quality. Each call re-runs both builds. Threading the server-authored {@code seededStructuralTestNames} through keeps the grading plan and statement
     * contract identical to final verification instead of first revealing those gradable names in the outer repair loop; {@code includeStatementChecks} is false for the
     * tests-stage entry point, which reports on test artifacts only.
     */
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
            Map<String, String> testsRepositoryFiles = readRepositoryFiles(sandbox, sessionId, RepositoryType.TESTS);
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
                ? ExerciseIntegrityGate.approvedTestPlanReasons(String.join("\n\n", contractSpecifications), testPlanJson, solution.testNames(), exercise.getDueDate() != null,
                        seededStructuralTestNames)
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
                reasons, List.copyOf(readHiddenTestNames(sandbox, sessionId)), solution.buildDiagnostic(), template.buildDiagnostic());
    }

    /**
     * Validates the immutable build harness and offline sandbox image before any provider call, using the same pristine script and build phases as final verification, so
     * generation retries cannot spend tokens on an environment defect they are unable to repair.
     *
     * @param sandbox   the sandbox that will host the generation job
     * @param sessionId its active session id
     * @param exercise  the exercise whose immutable build harness and phases are checked
     * @return an operator-actionable failure, or empty when the build emitted a JUnit report successfully
     */
    public Optional<String> checkBuildEnvironment(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        try {
            PristineBuildExecution execution = runPristineBuildWithExecution(sandbox, sessionId, seedReadinessProbe(sandbox, sessionId, exercise),
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
     * Redacts common credential forms and caps a diagnostic for protected server logs or agent tool feedback. Public user-facing errors must stay generic and must not carry this
     * text.
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
        String marker = "\n... [truncated] ...\n";
        int tailChars = MAX_READINESS_DIAGNOSTIC_CHARS - READINESS_DIAGNOSTIC_HEAD_CHARS - marker.length();
        return redacted.substring(0, READINESS_DIAGNOSTIC_HEAD_CHARS) + marker + redacted.substring(redacted.length() - tailChars);
    }

    private static boolean readinessNameMatches(String actual, String expected) {
        String normalized = actual.endsWith("()") ? actual.substring(0, actual.length() - 2) : actual;
        return normalized.equals(expected) || normalized.endsWith("." + expected);
    }

    /** The frozen approved specification. Candidate-authored workspace files never become grading authority. */
    private List<String> contractSpecifications(InteractiveSandbox sandbox, String sessionId) {
        return approvedSpecs.approved(sessionId).filter(spec -> !spec.isBlank()).stream().toList();
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
     * statement, and applies every actionable gate. Both the post-loop {@link #verify} and the in-loop {@link #selfCheck} consume this, so the agent's feedback and the verdict
     * are computed by identical code.
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

        Set<String> hiddenTestNames = readHiddenTestNames(sandbox, sessionId);
        List<String> unresolvedTaskBindings = ProblemStatementBindingChecker.unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        List<String> bindableTestNames = ProblemStatementBindingChecker.bindableTestNames(solution.testNames(), hiddenTestNames);
        boolean taskBindingsResolve = checkTaskBindingsResolve(unresolvedTaskBindings, bindableTestNames, problemStatementHasTasks, statementReasons);
        List<String> duplicateTaskBindings = ProblemStatementBindingChecker.duplicateTaskBindings(problemStatement);
        boolean noDuplicateTaskBindings = checkNoDuplicateTaskBindings(duplicateTaskBindings, problemStatementHasTasks, statementReasons);
        // Hidden tests are deliberately unbound, so they are exempt here and forbidden below. Both halves must move together: exempting alone lets a bound hidden test through,
        // forbidding alone makes the two gates jointly unsatisfiable.
        List<String> unboundGradableTests = ProblemStatementBindingChecker.unboundGradableTestNames(problemStatement, solution.testNames(), testCount, hiddenTestNames);
        boolean allGradableTestsBound = checkAllGradableTestsBound(unboundGradableTests, problemStatementHasTasks, taskBindingsResolve, statementReasons);
        List<String> hiddenTestMentions = ProblemStatementBindingChecker.hiddenTestMentions(problemStatement, hiddenTestNames);
        boolean noHiddenTestsExposed = hiddenTestMentions.isEmpty();
        if (!noHiddenTestsExposed) {
            statementReasons.add(ProblemStatementBindingChecker.hiddenTestMentionsRejection(hiddenTestMentions));
        }
        boolean solutionScaClean = checkSolutionScaClean(exercise, solution, reasons);

        // The build oracle is blind to what the student-facing statement exposes, so grader internals and bare task markers are blocked here.
        List<String> proseHygieneLeaks = ProblemStatementBindingChecker.proseHygieneLeaks(problemStatement);
        boolean proseHygienic = proseHygieneLeaks.isEmpty();
        if (!proseHygienic) {
            statementReasons.add("The problem statement leaks grader internals or stray task markers into student-facing prose: " + proseHygieneLeaks
                    + ". Rewrite it as a student would read it — describe the required behaviour and edge cases, never how the exercise is built, tested or graded, and bind tasks "
                    + "only via [task][Title](testName) lines.");
        }

        // Statement-shape defects the staged gate also enforces, repeated because repair attempts run only this verifier and would otherwise ship the statement it rejected.
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
        // A later edit may promise a diagram but never un-promise one, so both the live and the approved specification are consulted: rewriting '## Diagram' from yes to no under
        // gate pressure must not make this pass vacuously.
        boolean diagramPromised = approvedSpecs.approved(sessionId).filter(ProblemStatementBindingChecker::specPromisesDiagram).isPresent();
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
     * The shared, sandbox-dependent half of verification: the two parsed build summaries, the actionable gate outcome and its reasons, plus the exact name lists the agent is
     * shown verbatim. {@code gradableTestsPassingOnTemplate} (non-structural, non-build-gate solution tests that also pass on the template; empty when the template did not build
     * soundly) both drives the gate and is reported to the agent, so gate and feedback can never disagree.
     */
    private record DifferentialAnalysis(BuildSummary solution, BuildSummary template, boolean solutionPassed, boolean templateFailed, boolean testArtifactGatesPass,
            boolean actionableGatesPass, List<String> testArtifactReasons, List<String> actionableReasons, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles,
            List<String> gradableTestsPassingOnTemplate) {
    }

    /**
     * Runs one pristine build, then copies out its reports from the verifier-owned reports directory — a constant path, never derived from agent output — and parses them with
     * the production parsers. The tar is validated by {@link CollectedReports} before any byte is parsed.
     *
     * @param assignmentName the assignment directory name ({@code solution}/{@code template}); also the reports subdirectory name and the copyOut prefix
     */
    private BuildSummary runPristineBuild(InteractiveSandbox sandbox, String sessionId, String buildCommand, String assignmentName) {
        return runPristineBuildWithExecution(sandbox, sessionId, buildCommand, assignmentName).summary();
    }

    private PristineBuildExecution runPristineBuildWithExecution(InteractiveSandbox sandbox, String sessionId, String buildCommand, String assignmentName) {
        SandboxExecResultDTO run;
        try {
            run = sandbox.exec(sessionId, VERIFY_TIMEOUT, "sh", "-c", buildCommand);
        }
        catch (RuntimeException exception) {
            throw new VerificationInfrastructureException("The verifier could not execute the " + assignmentName + " build", exception);
        }
        if (run.timedOut()) {
            // InteractiveSandbox destroys a session whose command exceeds its deadline, so retrying in it can only waste another verifier call.
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
        return new PristineBuildExecution(BuildSummary.fromReports(reports, run.exitCode(), boundedBuildDiagnostic(run.combinedOutput())), run);
    }

    /** Build scripts put compiler failures at the end; keep that actionable tail while applying the same credential redaction as protected readiness logs. */
    private static String boundedBuildDiagnostic(@Nullable String diagnostic) {
        return boundedReadinessDiagnostic(diagnostic);
    }

    private record PristineBuildExecution(BuildSummary summary, SandboxExecResultDTO process) {
    }

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
     * The template must compile and run exactly the tests the solution ran, without timing out. This is a prerequisite for {@link #gradableTestsPassingOnTemplate}: a template
     * that fails it cannot be checked test-by-test at all.
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
            // A differing count means the template silently dropped tests, so a vacuous template can "fail" without the tests discriminating anything.
            reasons.add("The template runs a different number of tests (" + template.tests() + ") than the solution (" + solution.tests()
                    + "). Both must run the same tests; the template must differ only in its (unimplemented) method bodies, not in which tests compile and run.");
            return false;
        }
        return true;
    }

    private static List<String> behaviouralTestNames(BuildSummary solution, Set<String> seededStructuralTestNames) {
        Set<String> structural = seededStructuralTestNames == null ? Set.of()
                : seededStructuralTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        List<String> behavioural = new ArrayList<>();
        for (String rawName : solution.testNames()) {
            String normalized = ProblemStatementBindingChecker.normalizeTestName(rawName);
            if (BuildGateTestNames.isBuildGate(normalized) || structural.contains(normalized)) {
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
     * A {@code [task]}'s names must be real runner test names, not a {@code @DisplayName} or prose title: a binding that resolves to nothing shows the student no progress in
     * Artemis, and the differential build cannot detect it.
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
     * SCA reports carry no {@code <testcase>}, so the differential is blind to them while production folds their penalty into the score. {@link ScaPenaltyParity} flags only the
     * findings production would actually penalise, leaving the verdict unchanged when none would dock the reference solution.
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
     * Fail-closed on a read-back gap: a repository seeded non-empty but extracted empty would silently disable the harness and leak gates, so the candidate is rejected rather
     * than accepted on that doubt. A genuinely empty repository is reported as not failed and stays fail-open.
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
     * Runs candidate contract witnesses against the reference solution and template and returns the ones that demonstrably pass the solution and fail at the starter seam. The
     * witnesses ride in a single throwaway probe class beside the graded suite. The canonical restore runs before and after the probe so an infrastructure failure can never leave
     * advisory work in the live authoring session.
     *
     * @param sandbox            the open sandbox session
     * @param sessionId          the sandbox session id
     * @param exercise           the exercise being built (drives the build recipe)
     * @param producedTestsFiles the tests repository as produced, providing both the collision check and the source of the probe's package and imports
     * @param candidates         the unvalidated witnesses
     * @param restoreCandidate   restores the mechanically verified candidate and removes all probe residue
     * @return the witnesses the reference solution actually satisfied
     */
    public List<ContractWitness> validateContractWitnesses(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> producedTestsFiles,
            List<ContractWitness> candidates, Runnable restoreCandidate) {
        if (candidates.isEmpty() || producedTestsFiles.isEmpty() || exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return List.of();
        }
        Optional<Map.Entry<String, String>> host = ContractWitnessProbe.host(producedTestsFiles);
        if (host.isEmpty()) {
            return List.of();
        }
        String probePath = ContractWitnessProbe.probePath(host.get().getKey(), producedTestsFiles.keySet());
        String probeSource = ContractWitnessProbe.buildProbeSource(host.get().getValue(), candidates);
        if (probePath == null || probeSource.isBlank()) {
            return List.of();
        }
        try {
            restoreCandidate.run();
            String workspacePath = GenerationWorkspaceService.directoryFor(RepositoryType.TESTS) + "/" + probePath;
            sandbox.copyIn(sessionId, GenerationWorkspaceService.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(Map.of(workspacePath, probeSource), Map.of()));
            seedPristineVerifyScript(sandbox, sessionId, exercise);
            BuildSummary solution = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineSolutionBuildCommand(),
                    GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
            List<ContractWitness> solutionValidated = ContractWitnessProbe.validated(solution.testNames(), solution.testFailedNames(), candidates);
            if (solutionValidated.isEmpty()) {
                return List.of();
            }
            BuildSummary template = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineTemplateBuildCommand(),
                    GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));
            List<ContractWitness> discriminating = ContractWitnessProbe.discriminating(solutionValidated, template.testNames(), template.testFailedNames());
            log.info("Contract-witness probe for exercise {}: {} of {} candidate witnesses passed the reference solution and failed at the starter seam", exercise.getId(),
                    discriminating.size(), candidates.size());
            return discriminating;
        }
        catch (VerificationInfrastructureException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new VerificationInfrastructureException("The contract-witness probe could not restore the verified candidate", exception);
        }
        finally {
            try {
                restoreCandidate.run();
            }
            catch (RuntimeException exception) {
                throw new VerificationInfrastructureException("The contract-witness probe could not restore the verified candidate", exception);
            }
        }
    }

    /**
     * Executes the three restored builds that turn an authored semantic mutant into environment evidence; probe infrastructure failures propagate.
     *
     * @param sandbox               the active generation sandbox
     * @param sessionId             the active sandbox session
     * @param exercise              the exercise whose build commands should run
     * @param producedTestsFiles    the mechanically verified graded tests
     * @param producedSolutionFiles the pristine reference-solution sources
     * @param candidates            independently authored mutant proposals
     * @param restoreCandidate      restores the mechanically verified workspace
     * @return mutants proven to survive the graded suite and fail their own counterexample
     */
    public List<SemanticMutant> validateSemanticMutants(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> producedTestsFiles,
            Map<String, String> producedSolutionFiles, List<SemanticMutant> candidates, Runnable restoreCandidate) {
        if (candidates.isEmpty() || producedTestsFiles.isEmpty() || producedSolutionFiles.isEmpty() || exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return List.of();
        }
        try {
            List<SemanticMutant> validated = SemanticMutantExecution.validate(producedTestsFiles, producedSolutionFiles, candidates,
                    (mutant, probe) -> runSemanticMutantProbe(sandbox, sessionId, exercise, mutant, probe, restoreCandidate));
            log.info("Semantic-mutant probe for exercise {}: {} of {} proposals were proven to survive the graded suite and die on their counterexample", exercise.getId(),
                    validated.size(), Math.min(candidates.size(), 2));
            return validated;
        }
        catch (VerificationInfrastructureException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new VerificationInfrastructureException("The semantic-mutant probe could not restore the verified candidate", exception);
        }
    }

    /**
     * Rechecks proven mutants after repair. Any executed graded test failure is a kill because the same candidate passed ordinary mechanical verification immediately before
     * this mutant-only probe; requiring the author's exact suggested method would reject equivalent renamed or stronger tests. A failed probe preserves the prior evidence by
     * propagating.
     *
     * @param sandbox               the active generation sandbox
     * @param sessionId             the active sandbox session
     * @param exercise              the exercise whose build commands should run
     * @param producedSolutionFiles the current reference-solution sources
     * @param provenMutants         mutants previously proven by the three-probe validation
     * @param restoreCandidate      restores the mechanically verified workspace
     * @return the proven mutants that remain unresolved
     */
    public List<SemanticMutant> checkSemanticMutants(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> producedSolutionFiles,
            List<SemanticMutant> provenMutants, Runnable restoreCandidate) {
        if (provenMutants.isEmpty() || exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return List.of();
        }
        return SemanticMutantExecution.surviving(producedSolutionFiles, provenMutants,
                (mutant, probe) -> runSemanticMutantProbe(sandbox, sessionId, exercise, mutant, probe, restoreCandidate));
    }

    private BuildSummary runSemanticMutantProbe(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, @Nullable SemanticMutant mutant,
            Map.@Nullable Entry<String, String> counterexampleProbe, Runnable restoreCandidate) {
        return SemanticMutantWorkspaceProbe.run(sandbox, sessionId, mutant, counterexampleProbe, restoreCandidate, () -> seedPristineVerifyScript(sandbox, sessionId, exercise),
                () -> runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineSolutionBuildCommand(),
                        GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION)));
    }

    /**
     * Installs the readiness variant of the pristine script and returns the command that runs it. The two are one step on purpose: the readiness script replaces the exercise's
     * own test and assignment sources with the trusted fixture inside its disposable build tree, so running the ordinary solution command without installing it first would
     * silently probe the exercise instead of the environment.
     */
    private String seedReadinessProbe(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        seedPristineVerifyScript(sandbox, sessionId, sandboxBuildCommandService.readinessVerifyScriptContent(exercise));
        return sandboxBuildCommandService.pristineSolutionBuildCommand();
    }

    /** Recreates the verifier control directory, discarding anything the agent or an earlier in-loop verification left in it. */
    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        seedPristineVerifyScript(sandbox, sessionId, sandboxBuildCommandService.verifyScriptContent(exercise));
    }

    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, String script) {
        try {
            // Empty the directory rather than recreating it: copy-to-container requires the destination to already exist.
            SandboxExecResultDTO preparation = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c",
                    "find " + SandboxBuildCommandService.PRISTINE_VERIFY_DIR + " -mindepth 1 -delete");
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
     * The solution-build SCA findings production would penalise, rendered as {@code <TOOL>|<category>}; empty when SCA cannot dock the solution. Reads the persisted categories
     * the same way production grading does, so the decision matches {@code calculateTotalPenalty}; fails open when the repository is absent.
     */
    private List<String> penalisingScaFindings(ProgrammingExercise exercise, BuildSummary solution) {
        if (solution.scaFindings().isEmpty() || !Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) || exercise.getId() == null
                || staticCodeAnalysisCategoryRepository.isEmpty()) {
            return List.of();
        }
        var categories = staticCodeAnalysisCategoryRepository.get().findByExerciseId(exercise.getId());
        return ScaPenaltyParity.penalisingFindings(exercise, categories, solution.scaFindings()).stream().map(f -> f.tool() + "|" + f.category()).toList();
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
