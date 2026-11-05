package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.aiworker.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.runtime.verification.GeneratedTestPlan;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentSystemPrompt;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.CollectedReports;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.SandboxBuildCommands;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.WorkspaceArchive;

/**
 * Independently verifies generated exercises by building solution and template from a pristine script and parsing verifier-owned reports with the production LocalCI parsers, so
 * the oracle's view of a build is the same one grading will take (parity by construction).
 * <p>
 * The solution must pass every test. The template must run the same tests, fail every assessed student-work test, and pass every declared preservation check.
 * Build/compile/configure gates are exempt because they only gate compilation, and structural tests seeded by {@link StructuralOracleSeeder} may legitimately pass, as
 * Artemis's own reference exercises include correctly-shaped stubs. Preservation checks carry zero weight and never replace assessed evidence. Every assessed test that passes
 * on the starter remains an actionable rejection; there is no percentage-based leniency.
 */
public class DifferentialVerifier {

    private static final Logger log = LoggerFactory.getLogger(DifferentialVerifier.class);

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(10);

    private static final Set<String> READINESS_TEST_NAMES = Set.of("testPublicApi", "testRepresentativeScores", "testBoundaryScores", "testEmptyInput");

    private static final int MAX_READINESS_DIAGNOSTIC_CHARS = 4_000;

    private static final int READINESS_DIAGNOSTIC_HEAD_CHARS = 700;

    private static final Pattern URI_CREDENTIALS = Pattern.compile("(?i)(https?://)[^\\s/@]+@");

    private static final Pattern AUTHORIZATION_CREDENTIALS = Pattern.compile("(?i)(authorization\\s*+[:=]\\s*+)(?:(?:basic|bearer)\\s++)?[^\\s,;]++");

    private static final Pattern BEARER_CREDENTIALS = Pattern.compile("(?i)\\bbearer\\s+[^\\s,;]+");

    private static final Pattern NAMED_CREDENTIALS = Pattern.compile("(?i)\\b(password|passwd|token|secret|api[-_]?key)\\s*[:=]\\s*[^\\s,;]+");

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

    private final SandboxBuildCommands sandboxBuildCommandService;

    private final ApprovedSpecRegistry approvedSpecs;

    public DifferentialVerifier(SandboxBuildCommands sandboxBuildCommandService, ApprovedSpecRegistry approvedSpecs) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.approvedSpecs = approvedSpecs;
    }

    DifferentialVerifier(SandboxBuildCommands sandboxBuildCommandService) {
        this(sandboxBuildCommandService, new ApprovedSpecRegistry());
    }

    private String readProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        return Objects.requireNonNullElse(GenerationWorkspace.readRootArtifact(sandbox, sessionId, "problem-statement.md"), "");
    }

    /**
     * Preserves core-captured hidden visibility when no new plan is available. An explicit plan entry replaces that test's saved visibility; unknown tests gain no exemption.
     */
    private Set<String> readHiddenTestNames(InteractiveSandbox sandbox, String sessionId, Set<String> baselineHiddenTestNames) {
        Set<String> hidden = baselineHiddenTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            String plan = GenerationWorkspace.readRootArtifact(sandbox, sessionId, "test-plan.json");
            if (plan == null || plan.isBlank()) {
                return Set.copyOf(hidden);
            }
            for (GeneratedTestPlan.Entry entry : GeneratedTestPlan.parse(plan).tests()) {
                String name = ProblemStatementBindingChecker.normalizeTestName(entry.name());
                if (entry.visibility().equals("AFTER_DUE_DATE")) {
                    hidden.add(name);
                }
                else {
                    hidden.remove(name);
                }
            }
            return Set.copyOf(hidden);
        }
        catch (RuntimeException e) {
            return Set.copyOf(hidden);
        }
    }

    private static String readWorkspaceRootFile(InteractiveSandbox sandbox, String sessionId, String filename) {
        return Objects.requireNonNullElse(GenerationWorkspace.readRootArtifact(sandbox, sessionId, filename), "");
    }

    private static final int MAX_POSSIBLY_DEAD_FILES = 20;

    /**
     * Advisory (never a gate) probe for the agent's self-check: a source file present in exactly one of {@code solution/} and {@code template/} is a likely abandoned orphan,
     * because the two repositories should differ only in method bodies. Never throws.
     */
    private static List<String> possiblyDeadWorkspaceFiles(InteractiveSandbox sandbox, String sessionId) {
        try {
            Set<String> solution = listSourceFiles(sandbox, sessionId, GenerationWorkspace.directoryFor(RepositoryRole.SOLUTION));
            Set<String> template = listSourceFiles(sandbox, sessionId, GenerationWorkspace.directoryFor(RepositoryRole.TEMPLATE));
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

    /** Build manifests and wrapper launchers are repository-specific and must not be reported as dead source files. */
    private static final Set<String> BUILD_MANIFEST_NAMES = Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties",
            "gradlew", "gradlew.bat");

    /** Empty on any non-success, so the advisory disappears rather than misreporting. */
    private static Set<String> listSourceFiles(InteractiveSandbox sandbox, String sessionId, String repoDirectory) {
        String repoRoot = GenerationWorkspace.WORKSPACE + "/" + repoDirectory;
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspace.SANDBOX_READ_TIMEOUT, "sh", "-c",
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
     * Runs the differential verification and the sandbox-free integrity gates; mechanical verification passes only when both do. Each build wipes and re-seeds the verifier
     * control directory and counts only reports written during that build. The integrity gates fail open on genuinely-empty inputs but fail closed on
     * {@code extractionFailedRepositories}, so a flaky read-back cannot silently disable a gate.
     *
     * @param sandbox          the open sandbox session the pristine builds run in
     * @param sessionId        the sandbox session id
     * @param exercise         the exercise being verified (drives the per-language build recipe)
     * @param request          the produced artifacts and integrity-gate inputs to decide on
     * @param restoreCandidate re-materializes the exact captured candidate before each build, so generated tests or detached processes cannot change a later build's input and make
     *                             the verifier approve a tree different from the one persistence receives
     * @return the mechanical verdict (verified, solution-passed, template-failed, test count, and rejection reasons)
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, VerificationRequest request, Runnable restoreCandidate) {
        Set<String> baselineHiddenTestNames = request.adaptation() ? exercise.baselineHiddenTestNames() : Set.of();
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, request.seededStructuralTests(), request.producedProblemStatement(),
                request.producedTestPlan(), baselineHiddenTestNames, restoreCandidate);
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();
        List<String> reasons = new ArrayList<>(analysis.actionableReasons());

        // Integrity gates the build cannot see. Post-loop only: they need the seed snapshot and read-back files the agent loop lacks mid-session.
        boolean harnessSnapshotRequired = true;
        List<String> harnessTamperingReasons = new ArrayList<>();
        harnessTamperingReasons.addAll(ExerciseIntegrityGate.harnessTamperingReasons("tests", request.seedTestsFiles(), request.producedTestsFiles(), harnessSnapshotRequired));
        harnessTamperingReasons.addAll(ExerciseIntegrityGate.harnessTamperingReasons("template", request.seedTemplateFiles(), request.producedTemplateFiles(), false));
        harnessTamperingReasons.addAll(ExerciseIntegrityGate.harnessTamperingReasons("solution", request.seedSolutionFiles(), request.producedSolutionFiles(), false));
        boolean harnessIntact = harnessTamperingReasons.isEmpty();
        reasons.addAll(harnessTamperingReasons);
        List<String> nondeterministicTestReasons = ExerciseIntegrityGate.nondeterministicGradedTestReasons(request.producedTestsFiles());
        boolean gradedTestsDeterministic = nondeterministicTestReasons.isEmpty();
        reasons.addAll(nondeterministicTestReasons);
        List<String> sourceReadingTestReasons = ExerciseIntegrityGate.sourceReadingTestReasons(request.producedTestsFiles());
        boolean testsReadNoSources = sourceReadingTestReasons.isEmpty();
        reasons.addAll(sourceReadingTestReasons);
        List<String> solutionLeakReasons = ExerciseIntegrityGate.solutionLeakReasons(request.producedTemplateFiles(), request.producedSolutionFiles());
        boolean noSolutionLeak = solutionLeakReasons.isEmpty();
        reasons.addAll(solutionLeakReasons);
        List<String> gradingContextSniffingReasons = ExerciseIntegrityGate.gradingContextSniffingReasons(request.producedTemplateFiles(), request.producedSolutionFiles());
        boolean noGradingContextSniffing = gradingContextSniffingReasons.isEmpty();
        reasons.addAll(gradingContextSniffingReasons);
        List<String> javaAresConventionReasons = new ArrayList<>(
                ExerciseIntegrityGate.javaAresConventionReasons(request.seedTestsFiles(), request.producedTestsFiles(), request.adaptation()));
        javaAresConventionReasons.addAll(ExerciseIntegrityGate.securityPolicyReasons(request.seedTestsFiles(), request.producedTestsFiles(), request.producedSolutionFiles(),
                approvedSpecs.approved(sessionId).orElse(null)));
        boolean javaAresConventionsHold = javaAresConventionReasons.isEmpty();
        reasons.addAll(javaAresConventionReasons);
        List<String> javaSourceLayoutReasons = ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(exercise.packageName(), request.seedTestsFiles(), request.seedTemplateFiles(),
                request.seedSolutionFiles(), request.producedTestsFiles(), request.producedTemplateFiles(), request.producedSolutionFiles());
        boolean javaSourceLayoutIntact = javaSourceLayoutReasons.isEmpty();
        reasons.addAll(javaSourceLayoutReasons);

        List<String> contractSpecifications = contractSpecifications(sandbox, sessionId);
        List<String> approvedSpecificationReasons = contractSpecifications.stream()
                .flatMap(spec -> ApprovedOwnershipGate.approvedSpecificationReasons(spec, request.producedTemplateFiles(), request.producedSolutionFiles()).stream()).distinct()
                .toList();
        boolean approvedSpecificationHolds = approvedSpecificationReasons.isEmpty();
        reasons.addAll(approvedSpecificationReasons);
        String approvedSpec = String.join("\n\n", contractSpecifications);
        List<String> approvedTestPlanReasons = ExerciseIntegrityGate.approvedTestPlanReasons(approvedSpec, request.producedTestPlan(), solution.testNames(), exercise.hasDueDate(),
                request.seededStructuralTestNames(), baselineHiddenTestNames);
        boolean approvedTestPlanHolds = approvedTestPlanReasons.isEmpty();
        reasons.addAll(approvedTestPlanReasons);
        List<String> statementTraceabilityReasons = ExerciseIntegrityGate.statementTraceabilityReasons(request.producedTestPlan(), request.producedProblemStatement());
        boolean statementTraceabilityHolds = statementTraceabilityReasons.isEmpty();
        reasons.addAll(statementTraceabilityReasons);
        List<String> statementTaskInstructionReasons = ExerciseIntegrityGate.statementTaskInstructionReasons(request.producedProblemStatement());
        boolean statementTasksHaveInstructions = statementTaskInstructionReasons.isEmpty();
        reasons.addAll(statementTaskInstructionReasons);
        List<String> templateTodoSeamReasons = contractSpecifications.stream()
                .flatMap(spec -> ApprovedOwnershipGate.templateTodoSeamReasons(spec, request.producedTemplateFiles(), request.producedTestsFiles()).stream()).distinct().toList();
        boolean templateTodoSeamsHold = templateTodoSeamReasons.isEmpty();
        reasons.addAll(templateTodoSeamReasons);

        boolean extractionSound = DifferentialBuildChecks.checkExtractionSound(request.extractionFailedRepositories(), reasons);

        // An adapt retaining none of the pre-adapt graded names is a from-scratch regeneration mislabelled as an adapt, which the internally-consistent differential cannot see.
        List<String> adaptWipeReasons = ExerciseIntegrityGate.adaptWipedGradedTestsReasons(request.baselineGradedTestNames(), solution.testNames());
        boolean noAdaptWipe = adaptWipeReasons.isEmpty();
        reasons.addAll(adaptWipeReasons);

        boolean gatesApartFromSolutionHold = harnessIntact && noSolutionLeak && noGradingContextSniffing && javaAresConventionsHold && javaSourceLayoutIntact
                && approvedSpecificationHolds && approvedTestPlanHolds && statementTraceabilityHolds && templateTodoSeamsHold && extractionSound && statementTasksHaveInstructions
                && noAdaptWipe && gradedTestsDeterministic && testsReadNoSources;
        boolean mechanicallyVerified = analysis.actionableGatesPass() && gatesApartFromSolutionHold;
        reasons.addAll(DifferentialBuildChecks.structuralSeedingDefectReasons(solution, request.seededStructuralTestNames(), contractSpecifications,
                request.producedSolutionFiles(), exercise.packageName(), analysis.actionableGatesPassApartFromSolution() && gatesApartFromSolutionHold));
        if (!mechanicallyVerified) {
            log.info(
                    "Differential verification failed: solution[{}], template[{}], actionableGatesPass={}, harnessIntact={}, noSolutionLeak={}, "
                            + "javaAresConventionsHold={}, javaSourceLayoutIntact={}, approvedSpecificationHolds={}, approvedTestPlanHolds={}, statementTraceabilityHolds={}, "
                            + "statementTasksHaveInstructions={}, templateTodoSeamsHold={}, extractionSound={}, noAdaptWipe={}, gradedTestsDeterministic={}, testsReadNoSources={}",
                    solution, template, analysis.actionableGatesPass(), harnessIntact, noSolutionLeak, javaAresConventionsHold, javaSourceLayoutIntact, approvedSpecificationHolds,
                    approvedTestPlanHolds, statementTraceabilityHolds, statementTasksHaveInstructions, templateTodoSeamsHold, extractionSound, noAdaptWipe,
                    gradedTestsDeterministic, testsReadNoSources);
        }
        return new VerificationResult(mechanicallyVerified, analysis.solutionPassed(), analysis.templateFailed(), solution.tests(), reasons,
                template.failureEvidence().stream().map(evidence -> new VerificationResult.TestFailureEvidence(evidence.testName(), evidence.message())).toList());
    }

    public AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> seedTestsFiles, boolean adaptation,
            SeededStructuralTests seededStructuralTests) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, adaptation, true, seededStructuralTests);
    }

    public AgentVerifyReport selfCheckTestsStage(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> seedTestsFiles,
            SeededStructuralTests seededStructuralTests) {
        return selfCheck(sandbox, sessionId, exercise, seedTestsFiles, false, false, seededStructuralTests);
    }

    /**
     * The in-loop self-check the agent's {@code verify} tool calls: the same pristine builds, production parse and actionable gates as {@link #verify}, rendered as an
     * agent-readable {@link AgentVerifyReport}. It skips the sandbox-free integrity gates, so {@code wouldBeAccepted} covers the differential and actionable gates only and proves
     * nothing about semantic quality. {@code includeStatementChecks} is false for the tests-stage entry point, which reports on test artifacts only.
     */
    private AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> seedTestsFiles, boolean adaptation,
            boolean includeStatementChecks, SeededStructuralTests seededStructuralTests) {
        Set<String> seededStructuralTestNames = seededStructuralTests.testNames();
        Set<String> baselineHiddenTestNames = adaptation ? exercise.baselineHiddenTestNames() : Set.of();
        JavaSourceSnapshot sourceSnapshot = JavaSourceSnapshot.capture(sandbox, sessionId);
        Map<String, String> testsRepositoryFiles = sourceSnapshot.testsFiles();
        Map<String, String> templateFiles = sourceSnapshot.templateFiles();
        Map<String, String> solutionFiles = sourceSnapshot.solutionFiles();
        String problemStatement = readProblemStatement(sandbox, sessionId);
        String testPlanJson = readWorkspaceRootFile(sandbox, sessionId, "test-plan.json");
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, seededStructuralTests, problemStatement, testPlanJson, baselineHiddenTestNames,
                () -> sourceSnapshot.restore(sandbox, sessionId));
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();

        boolean solutionPassed = analysis.solutionPassed();
        boolean templateCompiled = !template.timedOut() && template.tests() > 0;
        List<String> templateWronglyPassing = analysis.gradableTestsPassingOnTemplate();
        List<String> reasons = new ArrayList<>(includeStatementChecks ? analysis.actionableReasons() : analysis.testArtifactReasons());
        List<String> javaAresConventionReasons = new ArrayList<>();
        if (testsRepositoryFiles.isEmpty()) {
            javaAresConventionReasons.add("Could not inspect the tests repository for Java/Ares conventions; run verify again after ensuring /workspace/tests is readable.");
        }
        else {
            javaAresConventionReasons.addAll(ExerciseIntegrityGate.javaAresConventionReasons(seedTestsFiles, testsRepositoryFiles, adaptation));
            javaAresConventionReasons
                    .addAll(ExerciseIntegrityGate.securityPolicyReasons(seedTestsFiles, testsRepositoryFiles, solutionFiles, approvedSpecs.approved(sessionId).orElse(null)));
        }
        boolean javaAresConventionsHold = javaAresConventionReasons.isEmpty();
        reasons.addAll(javaAresConventionReasons);
        // Checked in the self-check too, so the tests stage rejects source-reading tests before any repair attempt is spent on them.
        List<String> sourceReadingTestReasons = ExerciseIntegrityGate.sourceReadingTestReasons(testsRepositoryFiles);
        boolean testsReadNoSources = sourceReadingTestReasons.isEmpty();
        reasons.addAll(sourceReadingTestReasons);

        List<String> contractSpecifications = contractSpecifications(sandbox, sessionId);
        List<String> approvedSpecificationReasons = contractSpecifications.stream()
                .flatMap(spec -> ApprovedOwnershipGate.approvedSpecificationReasons(spec, templateFiles, solutionFiles).stream()).distinct().toList();
        boolean approvedSpecificationHolds = approvedSpecificationReasons.isEmpty();
        reasons.addAll(approvedSpecificationReasons);
        List<String> approvedTestPlanReasons = includeStatementChecks
                ? ExerciseIntegrityGate.approvedTestPlanReasons(String.join("\n\n", contractSpecifications), testPlanJson, solution.testNames(), exercise.hasDueDate(),
                        seededStructuralTestNames, baselineHiddenTestNames)
                : List.of();
        boolean approvedTestPlanHolds = approvedTestPlanReasons.isEmpty();
        reasons.addAll(approvedTestPlanReasons);
        List<String> statementTraceabilityReasons = includeStatementChecks ? ExerciseIntegrityGate.statementTraceabilityReasons(testPlanJson, problemStatement) : List.of();
        boolean statementTraceabilityHolds = statementTraceabilityReasons.isEmpty();
        reasons.addAll(statementTraceabilityReasons);
        List<String> templateTodoSeamReasons = contractSpecifications.stream()
                .flatMap(spec -> ApprovedOwnershipGate.templateTodoSeamReasons(spec, templateFiles, testsRepositoryFiles).stream()).distinct().toList();
        boolean templateTodoSeamsHold = templateTodoSeamReasons.isEmpty();
        reasons.addAll(templateTodoSeamReasons);
        boolean gatesApartFromSolutionHold = javaAresConventionsHold && testsReadNoSources && approvedSpecificationHolds && approvedTestPlanHolds && statementTraceabilityHolds
                && templateTodoSeamsHold;
        reasons.addAll(DifferentialBuildChecks.structuralSeedingDefectReasons(solution, seededStructuralTestNames, contractSpecifications, solutionFiles, exercise.packageName(),
                (includeStatementChecks ? analysis.actionableGatesPassApartFromSolution() : analysis.testArtifactGatesPassApartFromSolution()) && gatesApartFromSolutionHold));

        return new AgentVerifyReport(solution.tests(), solutionPassed, List.copyOf(solution.testFailedNames()), solution.failureEvidence(), template.tests(), templateCompiled,
                analysis.templateFailed(), template.failureEvidence(), templateWronglyPassing, List.copyOf(solution.testNames()),
                includeStatementChecks ? analysis.unresolvedTaskBindings() : List.of(), analysis.possiblyDeadFiles(),
                (includeStatementChecks ? analysis.actionableGatesPass() : analysis.testArtifactGatesPass()) && javaAresConventionsHold && testsReadNoSources
                        && approvedSpecificationHolds && approvedTestPlanHolds && statementTraceabilityHolds && templateTodoSeamsHold,
                reasons, List.copyOf(readHiddenTestNames(sandbox, sessionId, baselineHiddenTestNames)), solution.buildDiagnostic(), template.buildDiagnostic(),
                analysis.preservationTestNames());
    }

    /**
     * Validates the immutable build harness and offline sandbox image before any provider call, using the same pristine script and build phases as final verification, so an
     * environment defect that authoring cannot repair is caught before tokens are spent on it.
     *
     * @param sandbox   the sandbox that will host the generation job
     * @param sessionId its active session id
     * @param exercise  the exercise whose immutable build harness and phases are checked
     * @return an operator-actionable failure, or empty when the build emitted a JUnit report successfully
     */
    public Optional<String> checkBuildEnvironment(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise) {
        try {
            PristineBuildExecution execution = runPristineBuildWithExecution(sandbox, sessionId, seedReadinessProbe(sandbox, sessionId, exercise),
                    GenerationWorkspace.directoryFor(RepositoryRole.SOLUTION));
            BuildSummary result = execution.summary();
            boolean trustedTestsRan = READINESS_TEST_NAMES.stream().allMatch(expected -> result.testNames().stream().anyMatch(name -> readinessNameMatches(name, expected)));
            if (!result.timedOut() && result.exitCode() == 0 && result.failures() == 0 && trustedTestsRan) {
                return Optional.empty();
            }
            log.warn("Sandbox build-environment readiness probe failed for the Java Gradle harness (exit {}, parsed tests {}, failures {}). Build output: {}", result.exitCode(),
                    result.tests(), result.failures(), boundedReadinessDiagnostic(execution.process().combinedOutput()));
            if (result.timedOut()) {
                return Optional.of("The sandbox build-environment readiness probe timed out before authoring began. Fix the build image or exercise scaffold; the authoring agent "
                        + "was not started.");
            }
            return Optional.of("The configured sandbox image and immutable exercise build harness failed the readiness probe before authoring began (exit " + result.exitCode()
                    + ", " + result.tests() + " parsed tests, " + result.failures() + " failures). Fix the build image or exercise scaffold; the authoring agent was not started.");
        }
        catch (RuntimeException exception) {
            log.warn("Could not run the sandbox build-environment readiness probe for the Java Gradle harness ({}): {}", exception.getClass().getSimpleName(),
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

    /**
     * The sandbox-dependent half of verification: source-isolated behavioral builds plus, when present, exact server-owned structural builds for solution and template. Both the
     * post-loop {@link #verify} and the in-loop {@link #selfCheck} consume this, so the agent's feedback and the verdict are computed by identical code.
     */
    private DifferentialAnalysis runDifferential(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, SeededStructuralTests seededStructuralTests,
            @Nullable String producedProblemStatement, @Nullable String producedTestPlan, Set<String> baselineHiddenTestNames, Runnable restoreCandidate) {
        List<String> reasons = new ArrayList<>();
        List<String> statementReasons = new ArrayList<>();
        Set<String> seededStructuralTestNames = seededStructuralTests.testNames();
        GeneratedTestPlan plan = null;
        boolean planValid = true;
        if (producedTestPlan != null && !producedTestPlan.isBlank()) {
            try {
                plan = GeneratedTestPlan.parse(producedTestPlan);
            }
            catch (IllegalArgumentException exception) {
                reasons.add("Invalid test-plan.json: " + exception.getMessage());
                planValid = false;
            }
        }
        Set<String> preservationTestNames = plan == null ? Set.of()
                : plan.preservationEntries().stream().map(GeneratedTestPlan.Entry::name).collect(Collectors.toUnmodifiableSet());

        BuildSummary behavioralSolution;
        BuildSummary behavioralTemplate;
        BuildSummary structuralSolution = null;
        BuildSummary structuralTemplate = null;
        try {
            restoreCandidate.run();
            seedPristineVerifyScript(sandbox, sessionId, exercise, seededStructuralTests);
            behavioralSolution = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.behavioralSolutionBuildCommand(),
                    GenerationWorkspace.directoryFor(RepositoryRole.SOLUTION));
            restoreCandidate.run();
            seedPristineVerifyScript(sandbox, sessionId, exercise, seededStructuralTests);
            behavioralTemplate = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.behavioralTemplateBuildCommand(),
                    GenerationWorkspace.directoryFor(RepositoryRole.TEMPLATE));
            if (!seededStructuralTestNames.isEmpty()) {
                restoreCandidate.run();
                seedPristineVerifyScript(sandbox, sessionId, exercise, seededStructuralTests);
                structuralSolution = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.trustedStructuralSolutionBuildCommand(),
                        GenerationWorkspace.directoryFor(RepositoryRole.SOLUTION));
                restoreCandidate.run();
                seedPristineVerifyScript(sandbox, sessionId, exercise, seededStructuralTests);
                structuralTemplate = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.trustedStructuralTemplateBuildCommand(),
                        GenerationWorkspace.directoryFor(RepositoryRole.TEMPLATE));
            }
        }
        finally {
            restoreCandidate.run();
        }
        BuildSummary solution = structuralSolution == null ? behavioralSolution : ProvenanceSeparatedBuilds.merge(behavioralSolution, structuralSolution);
        BuildSummary template = structuralTemplate == null ? behavioralTemplate : ProvenanceSeparatedBuilds.merge(behavioralTemplate, structuralTemplate);

        int testCount = solution.tests();
        boolean laneResultsSound = ProvenanceSeparatedBuilds.validate(behavioralSolution, behavioralTemplate, structuralSolution, structuralTemplate, seededStructuralTestNames,
                reasons);
        boolean solutionPassed = DifferentialBuildChecks.checkSolutionPasses(solution, reasons);
        boolean noDuplicateTestNames = DifferentialBuildChecks.checkNoDuplicateTestNames(solution, reasons);
        boolean templateBuildSound = DifferentialBuildChecks.checkTemplateBuildSound(solution, template, reasons);
        List<String> behaviouralTestNames = DifferentialBuildChecks.behaviouralTestNames(solution, seededStructuralTestNames);
        List<String> assessmentTestNames = behaviouralTestNames.stream().filter(name -> !preservationTestNames.contains(name)).toList();
        boolean hasBehaviouralTests = DifferentialBuildChecks.checkHasBehaviouralTests(assessmentTestNames, reasons);
        List<String> invalidPreservation = preservationTestNames.stream()
                .filter(name -> !behaviouralTestNames.contains(name) || !template.testNames().contains(name) || template.testFailedNames().contains(name)).sorted().toList();
        if (!invalidPreservation.isEmpty()) {
            reasons.add("These preservation checks do not pass on the template: " + invalidPreservation
                    + ". They must run as behavioral tests and pass on both repositories. Restore the supplied behavior; do not erase it to manufacture a student gap.");
        }
        List<String> gradableTestsPassingOnTemplate = templateBuildSound && hasBehaviouralTests
                ? DifferentialBuildChecks.gradableTestsPassingOnTemplate(assessmentTestNames, template)
                : List.of();
        boolean templateFailed = planValid && invalidPreservation.isEmpty() && templateBuildSound && hasBehaviouralTests
                && DifferentialBuildChecks.checkNoGradableTestPassesTemplate(gradableTestsPassingOnTemplate, reasons);

        String problemStatement = producedProblemStatement != null ? producedProblemStatement : readProblemStatement(sandbox, sessionId);
        boolean problemStatementHasTasks = ProblemStatementBindingChecker.hasTaskBindings(problemStatement);
        boolean taskBindingHiddenInMarkdownCode = ProblemStatementBindingChecker.hasTaskBindingInsideMarkdownCode(problemStatement);
        if (taskBindingHiddenInMarkdownCode) {
            statementReasons
                    .add("A [task][Title](testName) marker is hidden inside Markdown code, so Artemis renders it as code instead of a task checklist item. Remove the surrounding "
                            + "inline backticks or fenced code block and put each task marker on its own plain Markdown line.");
        }
        else if (!problemStatementHasTasks) {
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

        Set<String> hiddenTestNames = readHiddenTestNames(sandbox, sessionId, baselineHiddenTestNames);
        Set<String> unboundTestNames = new LinkedHashSet<>(hiddenTestNames);
        preservationTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).forEach(unboundTestNames::add);
        List<String> unresolvedTaskBindings = ProblemStatementBindingChecker.unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        List<String> bindableTestNames = ProblemStatementBindingChecker.bindableTestNames(solution.testNames(), unboundTestNames);
        boolean taskBindingsResolve = DifferentialBuildChecks.checkTaskBindingsResolve(unresolvedTaskBindings, bindableTestNames, problemStatementHasTasks, statementReasons);
        List<String> duplicateTaskBindings = ProblemStatementBindingChecker.duplicateTaskBindings(problemStatement);
        boolean noDuplicateTaskBindings = DifferentialBuildChecks.checkNoDuplicateTaskBindings(duplicateTaskBindings, problemStatementHasTasks, statementReasons);
        // Hidden tests must stay unbound, so they are exempt here and forbidden below. Both halves must move together: exempting alone lets a bound hidden test through,
        // forbidding alone makes the two gates jointly unsatisfiable.
        List<String> unboundGradableTests = ProblemStatementBindingChecker.unboundGradableTestNames(problemStatement, solution.testNames(), testCount, unboundTestNames);
        boolean allGradableTestsBound = DifferentialBuildChecks.checkAllGradableTestsBound(unboundGradableTests, problemStatementHasTasks, taskBindingsResolve, statementReasons);
        List<String> preservationBindingReasons = plan == null ? List.of() : ProblemStatementBindingChecker.preservationTaskBindingReasons(problemStatement, plan);
        statementReasons.addAll(preservationBindingReasons);
        List<String> hiddenTestMentions = ProblemStatementBindingChecker.hiddenTestMentions(problemStatement, hiddenTestNames);
        boolean noHiddenTestsExposed = hiddenTestMentions.isEmpty();
        if (!noHiddenTestsExposed) {
            statementReasons.add(ProblemStatementBindingChecker.hiddenTestMentionsRejection(hiddenTestMentions));
        }

        List<String> proseHygieneLeaks = ProblemStatementBindingChecker.proseHygieneLeaks(problemStatement);
        boolean proseHygienic = proseHygieneLeaks.isEmpty();
        if (!proseHygienic) {
            statementReasons.add(ProblemStatementBindingChecker.proseHygieneRejection(proseHygieneLeaks));
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
        boolean diagramSyntaxValid = !ProblemStatementBindingChecker.hasInvalidDiagramSyntax(problemStatement);
        if (!diagramSyntaxValid) {
            statementReasons.add("The PlantUML diagram has invalid syntax. Quote styled class/enum labels and give them plain aliases for relationships.");
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
        // Derived from the approved specification, never the live file, so editing the design under gate pressure cannot make this pass vacuously.
        boolean diagramRequired = approvedSpecs.approved(sessionId).filter(ApprovedOwnershipGate::diagramRequired).isPresent();
        boolean statementHonoursDiagramPromise = !(diagramRequired && !problemStatement.contains("@startuml"));
        if (!statementHonoursDiagramPromise) {
            statementReasons.add("The statement contains no @startuml class diagram, but this exercise requires one. " + AgentSystemPrompt.DIAGRAM_REQUIREMENT_RULE);
        }

        // Every test-artifact gate except the solution passing its tests, so a server-owned structural failure can be told apart from other failures.
        boolean testArtifactGatesPassApartFromSolution = laneResultsSound && noDuplicateTestNames && templateFailed && testCount > 0;
        boolean testArtifactGatesPass = testArtifactGatesPassApartFromSolution && solutionPassed;
        boolean statementGatesPass = problemStatementHasTasks && !taskBindingHiddenInMarkdownCode && taskKeywordsWellFormed && taskBindingsResolve && noDuplicateTaskBindings
                && allGradableTestsBound && preservationBindingReasons.isEmpty() && proseHygienic && taskTitlesUnique && statementVoiceOk && diagramLinksResolve
                && noStrayUmlDirectives && diagramSyntaxValid && headingsUnique && statementHonoursDiagramPromise && noHiddenTestsExposed;
        boolean actionableGatesPass = testArtifactGatesPass && statementGatesPass;

        List<String> possiblyDeadFiles = possiblyDeadWorkspaceFiles(sandbox, sessionId);
        List<String> actionableReasons = new ArrayList<>(reasons);
        actionableReasons.addAll(statementReasons);
        return new DifferentialAnalysis(solution, template, solutionPassed, templateFailed, testArtifactGatesPass, actionableGatesPass, testArtifactGatesPassApartFromSolution,
                testArtifactGatesPassApartFromSolution && statementGatesPass, List.copyOf(reasons), List.copyOf(actionableReasons), unresolvedTaskBindings, possiblyDeadFiles,
                gradableTestsPassingOnTemplate, preservationTestNames.stream().sorted().toList());
    }

    /**
     * The two parsed build summaries, the actionable gate outcome and its reasons, plus the exact name lists the agent is shown verbatim.
     * {@code gradableTestsPassingOnTemplate} (non-structural, non-build-gate solution tests that also pass on the template; empty when the template did not build soundly) both
     * drives the gate and is reported to the agent, so gate and feedback can never disagree.
     */
    private record DifferentialAnalysis(BuildSummary solution, BuildSummary template, boolean solutionPassed, boolean templateFailed, boolean testArtifactGatesPass,
            boolean actionableGatesPass, boolean testArtifactGatesPassApartFromSolution, boolean actionableGatesPassApartFromSolution, List<String> testArtifactReasons,
            List<String> actionableReasons, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles, List<String> gradableTestsPassingOnTemplate,
            List<String> preservationTestNames) {
    }

    /**
     * Runs one pristine build, then copies out its reports from the verifier-owned reports directory — a constant path, never derived from agent output — and parses them with
     * Hyperion's JUnit and SCA parsers. The tar is validated by {@link CollectedReports} before any byte is parsed.
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
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, SandboxBuildCommands.reportsDirectoryFor(assignmentName))) {
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
        return new PristineBuildExecution(BuildSummary.fromReports(reports, run.exitCode(), boundedReadinessDiagnostic(run.combinedOutput())), run);
    }

    private record PristineBuildExecution(BuildSummary summary, SandboxExecResultDTO process) {
    }

    /**
     * Runs every candidate witness against the reference solution and, when it passes there, the template. It preserves a named test failure in the reference solution as
     * evidence for later independent adjudication instead of silently discarding it.
     *
     * @param sandbox               the open sandbox session
     * @param sessionId             the sandbox session id
     * @param exercise              the exercise being built
     * @param producedTestsFiles    the tests repository used to host the throwaway probe
     * @param seededStructuralTests the exact server-owned structural authority
     * @param candidates            the unvalidated witnesses
     * @param restoreCandidate      restores the mechanically verified candidate and removes probe residue
     * @return one environment outcome per candidate, or an empty list when there are no candidates
     */
    public List<ContractWitnessOutcome> evaluateContractWitnesses(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> producedTestsFiles,
            SeededStructuralTests seededStructuralTests, List<ContractWitness> candidates, Runnable restoreCandidate) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (producedTestsFiles.isEmpty()) {
            return ContractWitnessEvaluator.inconclusive(candidates, "No executable Java test host was available.");
        }
        return ContractWitnessEvaluator.evaluate(sandbox, sessionId, exercise.id(), producedTestsFiles, candidates, restoreCandidate,
                () -> seedPristineVerifyScript(sandbox, sessionId, exercise, seededStructuralTests),
                () -> runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.behavioralSolutionBuildCommand(), GenerationWorkspace.directoryFor(RepositoryRole.SOLUTION)),
                () -> runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.behavioralTemplateBuildCommand(), GenerationWorkspace.directoryFor(RepositoryRole.TEMPLATE)));
    }

    /**
     * Executes semantic-mutant proposals and preserves whether the existing suite killed them or execution was inconclusive, so orchestration can reconcile textual review
     * hypotheses with stronger environment evidence.
     *
     * @param sandbox               active generation sandbox
     * @param sessionId             active sandbox session
     * @param exercise              exercise whose build recipe is used
     * @param producedTestsFiles    mechanically verified graded tests
     * @param producedSolutionFiles pristine reference-solution sources
     * @param seededStructuralTests the exact server-owned structural authority
     * @param candidates            independently authored mutant proposals
     * @param restoreCandidate      restores the verified candidate between probes
     * @return one evidence disposition per examined proposal
     */
    public List<SemanticMutantOutcome> evaluateSemanticMutants(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> producedTestsFiles,
            Map<String, String> producedSolutionFiles, SeededStructuralTests seededStructuralTests, List<SemanticMutant> candidates, Runnable restoreCandidate) {
        if (candidates.isEmpty() || producedTestsFiles.isEmpty() || producedSolutionFiles.isEmpty()) {
            return List.of();
        }
        try {
            List<SemanticMutantOutcome> outcomes = SemanticMutantExecution.evaluate(producedTestsFiles, producedSolutionFiles, candidates,
                    (mutant, probe) -> runSemanticMutantProbe(sandbox, sessionId, exercise, seededStructuralTests, mutant, probe, restoreCandidate));
            Map<SemanticMutantOutcome.Disposition, Long> counts = outcomes.stream().collect(Collectors.groupingBy(SemanticMutantOutcome::disposition, Collectors.counting()));
            log.info("Semantic-mutant probe for exercise {}: {} proposal outcomes {}", exercise.id(), outcomes.size(), counts);
            return outcomes;
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
     * @param seededStructuralTests the exact server-owned structural authority
     * @param provenMutants         mutants previously proven by the three-probe validation
     * @param restoreCandidate      restores the mechanically verified workspace
     * @return one executed survivor, kill, or inconclusive disposition per proven mutant
     */
    public List<SemanticMutantOutcome> checkSemanticMutants(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> producedSolutionFiles,
            SeededStructuralTests seededStructuralTests, List<SemanticMutant> provenMutants, Runnable restoreCandidate) {
        if (provenMutants.isEmpty()) {
            return List.of();
        }
        return SemanticMutantExecution.recheck(producedSolutionFiles, provenMutants,
                (mutant, probe) -> runSemanticMutantProbe(sandbox, sessionId, exercise, seededStructuralTests, mutant, probe, restoreCandidate));
    }

    /**
     * Probes whether the graded tests observe a student-owned {@code main} only through its printed transcript: the reference {@code main} runs once without arguments or input,
     * and the graded suite runs against a variant whose {@code main} only prints that transcript. A passing suite yields a survivor with the same recheck, kill, and regression
     * accounting as an executed semantic mutant; a reference run that is not deterministic and bounded yields nothing.
     *
     * @param sandbox               active generation sandbox
     * @param sessionId             active sandbox session
     * @param exercise              exercise whose build recipe is used
     * @param producedTestsFiles    mechanically verified graded tests
     * @param producedTemplateFiles the student starter, which decides which classes are student-owned
     * @param producedSolutionFiles pristine reference-solution sources
     * @param testPlanJson          the grading plan, which decides which tests carry weight
     * @param seededStructuralTests the exact server-owned structural authority
     * @param pendingSolutionPaths  classes whose probe is already pending and is rechecked by {@link #checkSemanticMutants}
     * @param restoreCandidate      restores the verified candidate between probes
     * @return one executed outcome per probed class
     */
    public List<SemanticMutantOutcome> probeOutputOnlyMains(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> producedTestsFiles,
            Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles, @Nullable String testPlanJson, SeededStructuralTests seededStructuralTests,
            Set<String> pendingSolutionPaths, Runnable restoreCandidate) {
        List<SemanticMutantOutcome> outcomes = OutputOnlyMainProbe.evaluate(producedTestsFiles, producedTemplateFiles, producedSolutionFiles, testPlanJson, pendingSolutionPaths,
                target -> {
                    restoreCandidate.run();
                    SandboxExecResultDTO capture;
                    try {
                        capture = sandbox.exec(sessionId, OutputOnlyMainProbe.CAPTURE_TIMEOUT, "sh", "-c", OutputOnlyMainProbe.captureCommand(target));
                    }
                    catch (RuntimeException exception) {
                        throw new VerificationInfrastructureException("The output-only probe could not run the reference main of " + target.simpleName(), exception);
                    }
                    if (capture.timedOut()) {
                        throw VerificationInfrastructureException.sessionLost("The output-only probe of " + target.simpleName() + " exceeded its sandbox deadline");
                    }
                    Optional<String> transcript = OutputOnlyMainProbe.transcript(capture);
                    if (transcript.isEmpty()) {
                        log.warn("Output-only probe for exercise {} skipped {}: {}", exercise.id(), target.simpleName(), OutputOnlyMainProbe.skipReason(capture));
                    }
                    return transcript;
                }, (mutant, probe) -> runSemanticMutantProbe(sandbox, sessionId, exercise, seededStructuralTests, mutant, probe, restoreCandidate));
        if (!outcomes.isEmpty() && log.isInfoEnabled()) {
            log.info("Output-only probe for exercise {}: {}", exercise.id(),
                    outcomes.stream().map(outcome -> outcome.mutant().ruleId() + "=" + outcome.disposition()).collect(Collectors.joining(", ")));
        }
        return outcomes;
    }

    /**
     * Executes an empty-body counterexample for simple learner-owned JUnit classes after a passing reference build. Unchanged supplied classes and complex test lifecycles are
     * excluded. A compilation failure is inconclusive; only the executed weighted grading tests decide whether the empty test bodies are accepted.
     *
     * @param sandbox               isolated workspace
     * @param sessionId             active sandbox session
     * @param exercise              exercise whose build recipe is used
     * @param producedTemplateFiles starter sources used to exclude unchanged supplied classes
     * @param producedSolutionFiles reference sources that passed verification
     * @param testPlanJson          grading plan identifying tests that carry credit
     * @param seededStructuralTests trusted server-owned structural bundle
     * @param restoreCandidate      restores the pristine candidate around every probe
     * @return executed survivor, kill, or inconclusive outcomes for the applicable learner test classes
     */
    public List<SemanticMutantOutcome> probeEmptyStudentTests(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> producedTemplateFiles,
            Map<String, String> producedSolutionFiles, @Nullable String testPlanJson, SeededStructuralTests seededStructuralTests, Runnable restoreCandidate) {
        List<SemanticMutant> probes = new ArrayList<>();
        producedSolutionFiles.forEach((path, source) -> {
            if (path.startsWith("src/") && path.endsWith(".java") && !source.equals(producedTemplateFiles.get(path))) {
                JavaSourceInspector.emptyJUnitTestBodies(source).ifPresent(variant -> probes.add(SemanticMutant.emptyStudentTests(path, source, variant)));
            }
        });
        return SemanticMutantExecution.recheck(producedSolutionFiles, probes, OutputOnlyMainProbe.gradedTest(testPlanJson),
                (mutant, overlay) -> runSemanticMutantProbe(sandbox, sessionId, exercise, seededStructuralTests, mutant, overlay, restoreCandidate));
    }

    /**
     * Rechecks printed-transcript probes after a repair. Only a failing test that carries weight kills one; a probe whose transcript comparison the source no longer shows is
     * resolved only once a {@code main} that prints nothing passes every weighted test. A failed probe preserves the prior evidence by propagating.
     *
     * @param sandbox               the active generation sandbox
     * @param sessionId             the active sandbox session
     * @param exercise              the exercise whose build commands should run
     * @param producedTestsFiles    the current graded tests
     * @param producedSolutionFiles the current reference-solution sources
     * @param testPlanJson          the current grading plan
     * @param seededStructuralTests the exact server-owned structural authority
     * @param probes                printed-transcript probes proven against the solution
     * @param restoreCandidate      restores the verified candidate between probes
     * @return one outcome per probe
     */
    public List<SemanticMutantOutcome> recheckOutputOnlyMains(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Map<String, String> producedTestsFiles,
            Map<String, String> producedSolutionFiles, @Nullable String testPlanJson, SeededStructuralTests seededStructuralTests, List<SemanticMutant> probes,
            Runnable restoreCandidate) {
        if (probes.isEmpty()) {
            return List.of();
        }
        return OutputOnlyMainProbe.recheck(producedTestsFiles, producedSolutionFiles, testPlanJson, probes,
                (mutant, probe) -> runSemanticMutantProbe(sandbox, sessionId, exercise, seededStructuralTests, mutant, probe, restoreCandidate));
    }

    /**
     * Resolves printed-transcript probes proven against an earlier revision of their class that a probe of the current class could not replace: one is resolved only when the
     * source shows no graded transcript comparison and a {@code main} that prints nothing passes every weighted test.
     *
     * @param sandbox               the active generation sandbox
     * @param sessionId             the active sandbox session
     * @param exercise              the exercise whose build commands should run
     * @param producedSolutionFiles the current reference-solution sources
     * @param testPlanJson          the current grading plan
     * @param seededStructuralTests the exact server-owned structural authority
     * @param staleProbes           printed-transcript probes proven against an earlier source of their class
     * @param restoreCandidate      restores the verified candidate between probes
     * @return one outcome per probe, killed when resolved and inconclusive otherwise
     */
    public List<SemanticMutantOutcome> resolveStaleOutputOnlyMains(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise,
            Map<String, String> producedSolutionFiles, @Nullable String testPlanJson, SeededStructuralTests seededStructuralTests, List<SemanticMutant> staleProbes,
            Runnable restoreCandidate) {
        if (staleProbes.isEmpty()) {
            return List.of();
        }
        return OutputOnlyMainProbe.resolveStale(producedSolutionFiles, testPlanJson, staleProbes,
                (mutant, probe) -> runSemanticMutantProbe(sandbox, sessionId, exercise, seededStructuralTests, mutant, probe, restoreCandidate));
    }

    private BuildSummary runSemanticMutantProbe(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, SeededStructuralTests seededStructuralTests,
            @Nullable SemanticMutant mutant, @Nullable Map<String, String> counterexampleProbe, Runnable restoreCandidate) {
        return SemanticMutantWorkspaceProbe.run(sandbox, sessionId, mutant, counterexampleProbe, restoreCandidate,
                () -> seedPristineVerifyScript(sandbox, sessionId, exercise, seededStructuralTests),
                () -> runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.behavioralSolutionBuildCommand(), GenerationWorkspace.directoryFor(RepositoryRole.SOLUTION)));
    }

    /**
     * Installs the readiness variant of the pristine script and returns the command that runs it. The two must stay one step: the readiness script replaces the exercise's own
     * test and assignment sources with the trusted fixture inside its disposable build tree, so running the ordinary solution command without installing it first would silently
     * probe the exercise instead of the environment.
     */
    private String seedReadinessProbe(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise) {
        seedPristineVerifyScript(sandbox, sessionId, sandboxBuildCommandService.readinessVerifyScriptContent(exercise));
        return sandboxBuildCommandService.pristineSolutionBuildCommand();
    }

    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, SeededStructuralTests seededStructuralTests) {
        seedPristineVerifyScript(sandbox, sessionId, sandboxBuildCommandService.verifyScriptContent(exercise), seededStructuralTests);
    }

    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, String script) {
        seedPristineVerifyScript(sandbox, sessionId, script, SeededStructuralTests.EMPTY);
    }

    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, String script, SeededStructuralTests seededStructuralTests) {
        try {
            // Empty the directory rather than recreating it: copy-to-container requires the destination to already exist.
            SandboxExecResultDTO preparation = sandbox.exec(sessionId, GenerationWorkspace.SANDBOX_READ_TIMEOUT, "sh", "-c",
                    "find " + SandboxBuildCommands.PRISTINE_VERIFY_DIR + " -mindepth 1 -delete");
            if (!preparation.isSuccess()) {
                throw new VerificationInfrastructureException("The verifier could not prepare its script directory", null);
            }
            sandbox.copyIn(sessionId, SandboxBuildCommands.PRISTINE_VERIFY_DIR, verifierFilesTar(script, seededStructuralTests));
        }
        catch (VerificationInfrastructureException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new VerificationInfrastructureException("The verifier could not install its pristine script", exception);
        }
    }

    private static InputStream verifierFilesTar(String script, SeededStructuralTests seededStructuralTests) {
        java.util.LinkedHashMap<String, String> files = new java.util.LinkedHashMap<>();
        files.put(SandboxBuildCommands.VERIFY_SCRIPT_NAME, script);
        seededStructuralTests.repositoryFiles().forEach((path, content) -> files.put("trusted-structural/" + path, content));
        return WorkspaceArchive.buildFilesTarStream(files, Map.of(), Set.of(SandboxBuildCommands.VERIFY_SCRIPT_NAME));
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
