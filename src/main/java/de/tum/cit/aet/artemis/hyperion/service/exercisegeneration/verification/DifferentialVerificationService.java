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
 * Independently verifies generated exercises by building solution and template from a pristine script and parsing verifier-owned reports with the normal LocalCI parsers. The
 * solution must pass; the template must compile, run the same tests, and fail every gradable test.
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

    // @Autowired disambiguates from the package-private test constructor; with two constructors and no annotation Spring cannot instantiate the bean.
    @Autowired
    public DifferentialVerificationService(SandboxBuildCommandService sandboxBuildCommandService,
            Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
    }

    DifferentialVerificationService(SandboxBuildCommandService sandboxBuildCommandService) {
        this(sandboxBuildCommandService, Optional.empty());
    }

    private String readProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
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

    /** Build-manifest filenames that are legitimately repo-specific, so the dead-file probe must not flag them. */
    private static final Set<String> BUILD_MANIFEST_NAMES = Set.of("go.mod", "go.sum", "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "cargo.toml", "cargo.lock", "makefile", "package.json", "package-lock.json", "tsconfig.json", "build.sbt", "pubspec.yaml", "pubspec.lock");

    /** Lists the assignment repo's source files (repo-relative) for the dead-file probe, excluding hidden files and build manifests. Empty on any non-success (fail-open). */
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
     * @param restoreCandidate resets the same sandbox container and materializes the exact captured candidate
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
        List<String> solutionLeakReasons = ExerciseIntegrityGate.solutionLeakReasons(request.producedTemplateFiles(), request.producedSolutionFiles());
        boolean noSolutionLeak = solutionLeakReasons.isEmpty();
        reasons.addAll(solutionLeakReasons);
        // A self-comparison harness passes the differential invariant (template still errors) yet grades any submission 100%, so the oracle is blind to it; gated here.
        List<String> selfComparisonReasons = ExerciseIntegrityGate.selfComparisonHarnessReasons(request.producedTestsFiles());
        boolean noSelfComparison = selfComparisonReasons.isEmpty();
        reasons.addAll(selfComparisonReasons);
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

        boolean extractionSound = checkExtractionSound(request.extractionFailedRepositories(), reasons);

        // Adapt total-wipe gate: an adapt that retains none of the pre-adapt graded test names is a from-scratch regeneration mislabeled as an adapt (a destructive rewrite the
        // internally-consistent differential cannot see). Inert for generate (empty baseline) and for any partial edit that keeps at least one graded name; only adds a reject.
        List<String> adaptWipeReasons = ExerciseIntegrityGate.adaptWipedGradedTestsReasons(request.baselineGradedTestNames(), solution.testNames());
        boolean noAdaptWipe = adaptWipeReasons.isEmpty();
        reasons.addAll(adaptWipeReasons);

        boolean mechanicallyVerified = analysis.actionableGatesPass() && harnessIntact && noSolutionLeak && noSelfComparison && javaAresConventionsHold && javaSourceLayoutIntact
                && extractionSound && noAdaptWipe;
        if (!mechanicallyVerified) {
            log.info(
                    "Differential verification failed: solution[{}], template[{}], actionableGatesPass={}, harnessIntact={}, noSolutionLeak={}, noSelfComparison={}, "
                            + "javaAresConventionsHold={}, javaSourceLayoutIntact={}, extractionSound={}, noAdaptWipe={}",
                    solution, template, analysis.actionableGatesPass(), harnessIntact, noSolutionLeak, noSelfComparison, javaAresConventionsHold, javaSourceLayoutIntact,
                    extractionSound, noAdaptWipe);
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
        // No authoritative seeded set: the agent cannot bind to structural tests seeded after it submits. The name-shape exemption still applies.
        String problemStatement = readProblemStatement(sandbox, sessionId);
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, Set.of(), problemStatement, () -> {
        });
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();

        boolean solutionPassed = analysis.solutionPassed();
        boolean templateCompiled = !template.timedOut() && template.tests() > 0;
        List<String> templateWronglyPassing = templateCompiled ? testsInFullyPassingTasks(problemStatement, solution, template) : List.of();
        List<String> reasons = new ArrayList<>(analysis.actionableReasons());
        boolean javaAresConventionsHold = true;
        if (exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            Map<String, String> testsRepositoryFiles = readTestsRepositoryFiles(sandbox, sessionId);
            List<String> javaAresConventionReasons = testsRepositoryFiles.isEmpty()
                    ? List.of("Could not inspect the tests repository for Java/Ares conventions; run verify again after ensuring /workspace/tests is readable.")
                    : ExerciseIntegrityGate.javaAresConventionReasons(seedTestsFiles, testsRepositoryFiles, adaptation);
            javaAresConventionsHold = javaAresConventionReasons.isEmpty();
            reasons.addAll(javaAresConventionReasons);
        }

        return new AgentVerifyReport(solution.tests(), solutionPassed, List.copyOf(solution.testFailedNames()), solution.failureEvidence(), template.tests(), templateCompiled,
                analysis.templateFailed(), template.failureEvidence(), templateWronglyPassing, List.copyOf(solution.testNames()), analysis.unresolvedTaskBindings(),
                analysis.possiblyDeadFiles(), analysis.actionableGatesPass() && javaAresConventionsHold, reasons);
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
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, GenerationWorkspaceService.WORKSPACE + "/" + GenerationWorkspaceService.directoryFor(RepositoryType.TESTS))) {
            return tar == null ? Map.of() : WorkspaceArchive.readTar(tar, GenerationWorkspaceService.directoryFor(RepositoryType.TESTS));
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
        boolean templateFailed = checkTemplateFails(solution, template, reasons);

        // The exercise must bind its tests to the problem statement via [task][title](testNames), else the student sees no task checklist.
        String problemStatement = producedProblemStatement != null ? producedProblemStatement : readProblemStatement(sandbox, sessionId);
        boolean problemStatementHasTasks = ProblemStatementBindingChecker.hasTaskBindings(problemStatement);
        if (!problemStatementHasTasks) {
            reasons.add("The problem statement has no Artemis task bindings. Add at least one line of the form [task][Title](testName) binding the graded tests to tasks so they "
                    + "appear as a checklist for students.");
        }

        List<String> malformedTaskKeywords = ProblemStatementBindingChecker.malformedTaskKeywords(problemStatement);
        boolean taskKeywordsWellFormed = malformedTaskKeywords.isEmpty();
        if (!taskKeywordsWellFormed) {
            reasons.add(
                    "These lines look like task bindings but use the wrong keyword " + malformedTaskKeywords + " instead of the exact lowercase singular [task]; they render as "
                            + "plain text and bind NO test (and leak the raw test names). Rewrite each as [task][Title](testName).");
        }

        // Compute once and let the gate decide; surfaced to the agent verbatim (guards the C++/Catch2 bare-name trap).
        List<String> unresolvedTaskBindings = ProblemStatementBindingChecker.unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        boolean taskBindingsResolve = checkTaskBindingsResolve(unresolvedTaskBindings, solution, problemStatementHasTasks, reasons);
        List<String> duplicateTaskBindings = ProblemStatementBindingChecker.duplicateTaskBindings(problemStatement);
        boolean noDuplicateTaskBindings = checkNoDuplicateTaskBindings(duplicateTaskBindings, problemStatementHasTasks, reasons);
        List<String> unboundGradableTests = ProblemStatementBindingChecker.unboundGradableTestNames(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        boolean allGradableTestsBound = checkAllGradableTestsBound(unboundGradableTests, problemStatementHasTasks, taskBindingsResolve, reasons);
        boolean noTaskPassesTemplate = checkNoTaskPassesTemplate(problemStatement, solution, template, problemStatementHasTasks, taskBindingsResolve, reasons);
        boolean solutionScaClean = checkSolutionScaClean(exercise, solution, reasons);

        // Prose hygiene: the oracle is blind to what the student-facing statement exposes, so this gate blocks leaks of grader internals or bare task markers (with exact phrases).
        List<String> proseHygieneLeaks = ProblemStatementBindingChecker.proseHygieneLeaks(problemStatement);
        boolean proseHygienic = proseHygieneLeaks.isEmpty();
        if (!proseHygienic) {
            reasons.add("The problem statement leaks grader internals or stray task markers into student-facing prose: " + proseHygieneLeaks
                    + ". Rewrite it as a student would read it — describe the required behaviour and edge cases, never how the exercise is built, tested or graded, and bind tasks "
                    + "only via [task][Title](testName) lines.");
        }

        boolean actionableGatesPass = solutionPassed && noDuplicateTestNames && templateFailed && testCount > 0 && problemStatementHasTasks && taskKeywordsWellFormed
                && taskBindingsResolve && noDuplicateTaskBindings && allGradableTestsBound && noTaskPassesTemplate && solutionScaClean && proseHygienic;

        List<String> possiblyDeadFiles = possiblyDeadWorkspaceFiles(sandbox, sessionId);
        return new DifferentialAnalysis(solution, template, solutionPassed, templateFailed, actionableGatesPass, reasons, unresolvedTaskBindings, possiblyDeadFiles);
    }

    /**
     * The shared, sandbox-dependent half of verification: the two parsed build summaries plus the actionable gate outcome. Consumed by both the post-loop {@link #verify} (which
     * adds
     * the integrity gates and verdict) and the in-loop {@link #selfCheck} (which renders the agent observation).
     *
     * @param actionableGatesPass    whether every sandbox-dependent gate held (the integrity gates are layered on top by {@link #verify})
     * @param actionableReasons      the reasons any sandbox-dependent gate failed (empty when all hold); the same wording {@link #verify} surfaces
     * @param unresolvedTaskBindings the {@code [task]} bindings referencing no real test (surfaced to the agent verbatim)
     * @param possiblyDeadFiles      best-effort workspace files no build phase appears to read (advisory; empty when unavailable)
     */
    private record DifferentialAnalysis(BuildSummary solution, BuildSummary template, boolean solutionPassed, boolean templateFailed, boolean actionableGatesPass,
            List<String> actionableReasons, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles) {
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
     * The template gate: the template must compile and run the same tests as the solution but fail at least half of the gradable ones (a near-complete template is not a real
     * starting point; {@code tests()==0} means it did not compile). Both the threshold and the failure count exclude the build/compile/configure gate tests
     * ({@link BuildGateTestNames}) — they legitimately pass on both repos, so counting them would false-reject a compile-heavy exercise (e.g. 4 gate tests + 2 behaviour tests:
     * half
     * of 6 is 3, but only the 2 behaviour tests can fail). A subset of {@link #checkNoGradableTestPassesTemplate} (which requires every gradable test to fail) — a cheaper early
     * signal that can never contradict it. Appends a rejection reason otherwise.
     *
     * @param solution the solution build summary (its test count is the reference)
     */
    private static boolean checkTemplateFails(BuildSummary solution, BuildSummary template, List<String> reasons) {
        int testCount = solution.tests();
        int templateFailing = template.failures();
        // Build/compile/configure gate tests pass on both builds by design, so the template can never fail them; count the "must fail at least half" threshold and the actual
        // template failures over the gradable (non-gate) tests only — the same population checkNoGradableTestPassesTemplate requires to fail in full.
        int gradableTestCount = testCount - (int) solution.testNames().stream().filter(name -> isBuildGateTest(ProblemStatementBindingChecker.normalizeTestName(name))).count();
        int gradableTemplateFailures = (int) template.testFailedNames().stream().filter(name -> !isBuildGateTest(ProblemStatementBindingChecker.normalizeTestName(name))).count();
        int requiredTemplateFailures = Math.max(1, gradableTestCount / 2);
        if (template.timedOut()) {
            reasons.add("The template build timed out; it must compile and fail the tests quickly.");
        }
        else if (template.tests() == 0) {
            reasons.add("The template does not compile (the tests never ran). The template must compile and only fail because the student's work is missing — use placeholder "
                    + "method bodies (returning null, 0, false) with the same signatures as the solution.");
        }
        else if (solution.tests() > 0 && template.tests() != solution.tests()) {
            // A differing count means the template silently dropped tests, letting a vacuous template "fail" without the tests discriminating.
            reasons.add("The template runs a different number of tests (" + template.tests() + ") than the solution (" + solution.tests()
                    + "). Both must run the same tests; the template must differ only in its (unimplemented) method bodies, not in which tests compile and run.");
        }
        else if (templateFailing == 0) {
            reasons.add("The template passes the tests, but it must fail them (a student starting from the template has not implemented the solution yet). Make the tests assert "
                    + "behaviour the template's placeholder implementations cannot satisfy.");
        }
        else if (gradableTemplateFailures < requiredTemplateFailures) {
            reasons.add("The template fails only " + gradableTemplateFailures + " of " + gradableTestCount
                    + " gradable tests, so it is nearly complete. A meaningful starting template should fail at least " + requiredTemplateFailures
                    + " of them — strip its implementations to placeholders and make sure the tests check the behaviour the student must implement. (Build/compile/configure gate "
                    + "tests are ignored here: they only check that the code compiles, which the template does by design, so they pass on both builds.)");
        }
        else {
            // Correctly-failing template. Trust the JUnit failure/error counts, not the exit code: some report converters (Go's go-junit-report, Dart's tojunit) exit 0 even on
            // failure.
            return true;
        }
        return false;
    }

    /**
     * The binding-resolution gate: a {@code [task]}'s names must be real runner test names, not a {@code @DisplayName} or prose title; a binding that resolves to nothing shows no
     * progress in Artemis, which the differential build cannot detect. Decides from the precomputed unresolved list and fails open when no trustworthy set was emitted.
     *
     * @param unresolvedTaskBindings the precomputed {@code [task]} bindings that resolve to no real test (the C++/Catch2 bare-name trap)
     */
    private static boolean checkTaskBindingsResolve(List<String> unresolvedTaskBindings, BuildSummary solution, boolean problemStatementHasTasks, List<String> reasons) {
        boolean taskBindingsResolve = unresolvedTaskBindings.isEmpty();
        if (problemStatementHasTasks && !taskBindingsResolve) {
            reasons.add("These [task] bindings reference names that match no actual test: " + unresolvedTaskBindings + ". A [task]'s parenthesised names must be the exact test "
                    + "method/function names (e.g. testSortsAscending), not a @DisplayName or a prose title — otherwise the task shows no result in Artemis. The actual test names are: "
                    + solution.testNames() + ". Fix the [task] lines (or rename the tests) so every binding references a real test name.");
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
                    + ". Add each non-build-gate test to exactly one [task][Title](testName) binding, or remove/rename tests that should not be graded.");
        }
        return allGradableTestsBound;
    }

    private static boolean checkNoTaskPassesTemplate(String problemStatement, BuildSummary solution, BuildSummary template, boolean problemStatementHasTasks,
            boolean taskBindingsResolve, List<String> reasons) {
        List<String> testsInFullyPassingTasks = testsInFullyPassingTasks(problemStatement, solution, template);
        boolean noTaskPassesTemplate = testsInFullyPassingTasks.isEmpty();
        if (problemStatementHasTasks && taskBindingsResolve && !noTaskPassesTemplate) {
            reasons.add("These [task] groups are already fully satisfied by the template: " + testsInFullyPassingTasks
                    + ". A starter may pass structural or scaffolding checks, but every task must retain at least one failing behavioural test so students still have work to do.");
        }
        return noTaskPassesTemplate;
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
     * The {@code [task]}-bound test names that pass on the template (resolve to a real solution test but are not in the template's failed/errored set) — the accidental free points
     * the strict differential rejects. Fails open (empty) when the template emitted no fail lines or the solution name set is missing/short.
     */
    private static List<String> testsInFullyPassingTasks(String problemStatement, BuildSummary solution, BuildSummary template) {
        List<String> solutionNames = solution.testNames();
        if (solutionNames.isEmpty() || solutionNames.size() < solution.tests()) {
            return List.of();
        }
        if (template.testFailedNames().isEmpty()) {
            return List.of();
        }
        Set<String> solutionPassing = solutionNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        Set<String> templateFailed = template.testFailedNames().stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        List<String> offending = new ArrayList<>();
        for (List<String> group : ProblemStatementBindingChecker.boundTestGroups(problemStatement)) {
            boolean allResolveAndPass = group.stream().map(ProblemStatementBindingChecker::normalizeTestName)
                    .allMatch(name -> solutionPassing.contains(name) && !templateFailed.contains(name));
            if (allResolveAndPass) {
                offending.add(String.join(",", group));
            }
        }
        return offending;
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
