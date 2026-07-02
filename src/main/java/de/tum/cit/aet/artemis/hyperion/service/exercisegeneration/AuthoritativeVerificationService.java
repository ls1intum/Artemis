package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localci.service.scaparser.ReportParser;
import de.tum.cit.aet.artemis.localci.service.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;

/**
 * Decides whether a generated exercise is correct, independently of what the agent reports, by running the {@code verify.sh} recipe in the sandbox once against the solution and
 * once
 * against the template and applying the differential oracle: the solution must compile and pass all tests; the template must compile, run the same tests, and fail them; at least
 * one
 * test must exist.
 * <p>
 * The verdict is parsed with PRODUCTION code, not in the shell: the pristine {@code verify.sh} collects build-fresh reports into a fixed, verifier-owned directory that this
 * service
 * {@code copyOut}s, validates via the hardened {@link CollectedReports} reader, and parses with the same parsers LocalCI uses ({@link TestResultXmlParser} for JUnit,
 * {@link ReportParser} for SCA) — parity-by-construction. Authenticity needs no nonce: the agent cannot reach the re-seeded pristine script or write the verifier-owned reports
 * dir,
 * and a forged always-pass report would also make the template pass, tripping {@code checkTemplateFails}.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class AuthoritativeVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AuthoritativeVerificationService.class);

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Matches a problem-statement task binding and captures its parenthesised, comma-separated test names: {@code [task][Some title](testA,testB)}. The capture is greedy (up to
     * the
     * LAST {@code )} on the line) because a test identifier can itself contain parentheses (a JVM/Ares name is reported as {@code testFoo()}); {@link #normalizeTestName} strips a
     * trailing {@code ()} so paren and no-paren binding forms resolve to the same test.
     */
    private static final Pattern TASK_BINDING = Pattern.compile("\\[task\\]\\[[^\\]]*\\]\\((.*)\\)");

    /**
     * A line shaped exactly like a task binding {@code [keyword][title](names)} (captures the keyword) so a near-miss such as {@code [tasks]} or {@code [Task]} is caught: only the
     * literal lowercase {@code [task]} binds tests; any other keyword renders as plain text and binds nothing, silently dropping a graded requirement and leaking the raw test
     * name.
     */
    private static final Pattern TASK_LIKE_BINDING = Pattern.compile("\\[(\\w+)\\]\\[[^\\]]*\\]\\([^)]*\\)");

    /**
     * The shape of an Ares auto-generated structural test-case name ({@code testClass[X]}, {@code testMethods[X]}, …). Relaxes binding RESOLUTION only (the seeder injects these
     * AFTER the agent submits), never the differential — a real behaviour test named {@code testClass[Evil]} still must fail on the template.
     */
    private static final Pattern STRUCTURAL_TEST_NAME = Pattern.compile("test(?:Class|Methods|Attributes|Constructors)\\[[^\\]]+\\]");

    /** A bare {@code [task]}/{@code [tasks]} marker NOT in binding position (negative lookahead excludes a real {@code [task][Title](test)} binding) — a prose leak. */
    private static final Pattern BARE_TASK_KEYWORD = Pattern.compile("\\[tasks?\\](?!\\s*\\[[^\\]]*\\]\\s*\\()", Pattern.CASE_INSENSITIVE);

    /** Grader-mechanics phrasings that must never reach a student; each is specific enough to be a leak rather than legitimate prose. Matched case-insensitively as substrings. */
    private static final List<Pattern> PROSE_MECHANICS_LEAKS = List.of(Pattern.compile("notimplementederror", Pattern.CASE_INSENSITIVE), Pattern.compile("todo!\\(\\)"),
            Pattern.compile("make (?:all )?(?:the )?tests? fail", Pattern.CASE_INSENSITIVE), Pattern.compile("exact test name", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reported by the test runner", Pattern.CASE_INSENSITIVE), Pattern.compile("generated by the test (?:suite|runner)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("in the template file", Pattern.CASE_INSENSITIVE));

    private static final int PRISTINE_SCRIPT_MODE = 0755;

    private final SandboxBuildCommandService sandboxBuildCommandService;

    /**
     * Persisted SCA categories read the SAME way production grading does ({@code findByExerciseId}), so the SCA-parity gate decides from authoritative state, not the detached
     * in-memory collection. Optional because SCA categories live in the core profile; absent on a node that cannot grade anyway, where the gate fails open.
     */
    private final Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository;

    // @Autowired disambiguates from the package-private test constructor; with two constructors and no annotation Spring fails to instantiate the bean.
    @Autowired
    public AuthoritativeVerificationService(SandboxBuildCommandService sandboxBuildCommandService,
            Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
    }

    AuthoritativeVerificationService(SandboxBuildCommandService sandboxBuildCommandService) {
        this(sandboxBuildCommandService, Optional.empty());
    }

    private String readProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        return result.isSuccess() ? result.stdout() : "";
    }

    /** Cap on the dead-file probe output, so a runaway workspace listing cannot blow the agent's context. */
    private static final int MAX_POSSIBLY_DEAD_FILES = 20;

    /**
     * Best-effort, language-agnostic dead-file probe for the agent's self-check (advisory only — never a gate): a source file present in exactly ONE of {@code solution/} and
     * {@code template/}, a likely abandoned orphan since the two repos must differ only in method bodies. Ignores build manifests and hidden files and never throws (fail-open).
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
        Set<String> files = new java.util.LinkedHashSet<>();
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

    // Package-private convenience overloads used only by the unit test; production calls the full verify(...) below.
    VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        return verify(sandbox, sessionId, exercise, Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }

    VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles,
            Set<String> extractionFailedRepositories) {
        return verify(sandbox, sessionId, exercise, seedTestsFiles, producedTestsFiles, producedTemplateFiles, producedSolutionFiles, extractionFailedRepositories, Set.of());
    }

    /**
     * Runs the differential verification AND the sandbox-free integrity gates (harness immutability and solution-leak); the exercise is accepted only when both pass.
     * <p>
     * Non-forgeable: the verifier re-seeds a pristine {@code verify.sh} to a verifier-owned path outside {@code /workspace} the agent cannot reach, and that copy deletes any
     * pre-existing report XML and counts only reports written during the build. The integrity gates fail OPEN on genuinely-empty inputs but fail CLOSED when a repo seeded
     * non-empty
     * extracts empty at verify time (via {@code extractionFailedRepositories}), so a flaky read-back cannot silently disable a gate.
     *
     * @param sandbox                      the open sandbox session the pristine builds run in
     * @param sessionId                    the sandbox session id
     * @param exercise                     the exercise being verified (drives the per-language build recipe)
     * @param seedTestsFiles               tests-repo files snapshotted at seed time; enables the harness-immutability gate
     * @param producedTestsFiles           tests-repo files read back after generation; compared against the seed snapshot for the harness-immutability gate
     * @param producedTemplateFiles        template-repo files read back after generation; enables the solution-leak gate
     * @param producedSolutionFiles        solution-repo files read back after generation; the leak gate flags solution bodies that surfaced in the template
     * @param extractionFailedRepositories repository directory names whose read-back FAILED (seeded non-empty but extracted empty); fail-closed signal distinct from a genuinely
     *                                         empty repo
     * @param seededStructuralTestNames    the AUTHORITATIVE structural test names the seeder injected this run (never agent-supplied); a {@code [task]} bound to one is exempt from
     *                                         binding RESOLUTION but still participates in the differential. Empty for callers without it (the from-scratch path falls back to the
     *                                         name-shape exemption)
     * @return the verdict (accepted, solution-passed, template-failed, test count, and the rejection reasons)
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories,
            Set<String> seededStructuralTestNames) {
        // The sandbox-dependent differential is computed by the SAME method the in-loop self-check uses, so the agent's `verify` tool and this acceptance decision can never
        // diverge.
        // This call layers the sandbox-FREE integrity gates and the final verdict on top of that shared analysis.
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, seededStructuralTestNames);
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();
        List<String> reasons = new ArrayList<>(analysis.actionableReasons());

        // Integrity gates the build cannot see. Post-loop only (the self-check skips them): they need the seed snapshot and read-back files the agent loop lacks mid-session.
        List<String> harnessTamperingReasons = ExerciseIntegrityGate.harnessTamperingReasons(seedTestsFiles, producedTestsFiles);
        boolean harnessIntact = harnessTamperingReasons.isEmpty();
        reasons.addAll(harnessTamperingReasons);
        List<String> solutionLeakReasons = ExerciseIntegrityGate.solutionLeakReasons(producedTemplateFiles, producedSolutionFiles);
        boolean noSolutionLeak = solutionLeakReasons.isEmpty();
        reasons.addAll(solutionLeakReasons);
        // A self-comparison harness passes the differential invariant (template still errors) yet grades any submission 100%, so the oracle is blind to it; gated here.
        List<String> selfComparisonReasons = ExerciseIntegrityGate.selfComparisonHarnessReasons(producedTestsFiles);
        boolean noSelfComparison = selfComparisonReasons.isEmpty();
        reasons.addAll(selfComparisonReasons);

        boolean extractionSound = checkExtractionSound(extractionFailedRepositories, reasons);

        boolean accepted = analysis.actionableGatesPass() && harnessIntact && noSolutionLeak && noSelfComparison && extractionSound;
        if (!accepted) {
            log.info("Authoritative verification failed: solution[{}], template[{}], actionableGatesPass={}, harnessIntact={}, noSolutionLeak={}, noSelfComparison={}, "
                    + "extractionSound={}", solution, template, analysis.actionableGatesPass(), harnessIntact, noSolutionLeak, noSelfComparison, extractionSound);
        }
        return new VerificationResult(accepted, analysis.solutionPassed(), analysis.templateFailed(), solution.tests(), reasons);
    }

    /**
     * The in-loop self-check the agent's {@code verify} tool calls: runs the SAME two pristine builds + production parse + actionable gates as the post-loop {@link #verify} and
     * returns an agent-readable {@link AgentVerifyReport}, so the agent sees exactly what the acceptance verdict will conclude.
     * <p>
     * It deliberately SKIPS the sandbox-free integrity gates (they need the seed snapshot and read-back the agent loop lacks), so {@code wouldBeAccepted} reflects the differential
     * +
     * actionable gates only; the post-loop {@link #verify} remains the sole acceptance truth. Each call re-runs the two builds (no stale cache).
     *
     * @param sandbox   the open sandbox session the pristine builds run in
     * @param sessionId the sandbox session id
     * @param exercise  the exercise being checked (drives the per-language build recipe)
     * @return the agent-readable differential report (per-test pass/fail on solution and template, parser-form names, wrongly-passing template tests, unresolved bindings)
     */
    public AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        // No authoritative seeded set: the agent cannot bind to structural tests seeded AFTER it submits. The name-shape exemption still applies.
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, Set.of());
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();

        boolean solutionPassed = analysis.solutionPassed();
        boolean templateCompiled = !template.timedOut() && template.tests() > 0;
        // Reuse the production-parity gate's computation so the agent sees exactly the tests that would block acceptance (the Go/no-exception zero-value-stub trap).
        List<String> templateWronglyPassing = templateCompiled ? gradableTestsThatPassOnTemplate(solution, template) : List.of();

        return new AgentVerifyReport(solution.tests(), solutionPassed, List.copyOf(solution.testFailedNames()), template.tests(), templateCompiled, analysis.templateFailed(),
                templateWronglyPassing, List.copyOf(solution.testNames()), analysis.unresolvedTaskBindings(), analysis.possiblyDeadFiles(), analysis.actionableGatesPass(),
                analysis.actionableReasons());
    }

    /**
     * Runs the shared, sandbox-DEPENDENT half of verification ONCE: re-seeds and runs the two pristine builds, parses them with the production parsers, reads the problem
     * statement,
     * and applies every actionable gate (solution passes, template fails, emitter soundness, task-binding presence/resolution, the two no-test-passes-template gates, and SCA
     * parity). Both the post-loop {@link #verify} and the in-loop {@link #selfCheck} consume this, so the agent's feedback and the verdict are computed by identical code.
     *
     * @param seededStructuralTestNames the authoritative seeded structural test names exempt from binding resolution (empty for the self-check)
     */
    private DifferentialAnalysis runDifferential(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Set<String> seededStructuralTestNames) {
        List<String> reasons = new ArrayList<>();

        // Re-seed and invoke a PRISTINE verify.sh outside /workspace, so any edit to the agent's own copy is irrelevant. It collects build-fresh reports into a verifier-owned dir
        // we copyOut and parse with the production parsers (no marker scraping).
        seedPristineVerifyScript(sandbox, sessionId, exercise);
        BuildSummary solution = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineSolutionBuildCommand(),
                GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
        BuildSummary template = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineTemplateBuildCommand(),
                GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));

        int testCount = solution.tests();
        boolean solutionPassed = checkSolutionPasses(solution, reasons);
        boolean templateFailed = checkTemplateFails(solution, template, reasons);
        EmitterSoundness emitterSoundness = checkEmitterSoundness(solution, template, reasons);

        // The exercise must bind its tests to the problem statement via [task][title](testNames), else the student sees no task checklist.
        String problemStatement = readProblemStatement(sandbox, sessionId);
        boolean problemStatementHasTasks = TASK_BINDING.matcher(problemStatement).find();
        if (!problemStatementHasTasks) {
            reasons.add("The problem statement has no Artemis task bindings. Add at least one line of the form [task][Title](testName) binding the graded tests to tasks so they "
                    + "appear as a checklist for students.");
        }

        List<String> malformedTaskKeywords = malformedTaskKeywords(problemStatement);
        boolean taskKeywordsWellFormed = malformedTaskKeywords.isEmpty();
        if (!taskKeywordsWellFormed) {
            reasons.add(
                    "These lines look like task bindings but use the wrong keyword " + malformedTaskKeywords + " instead of the exact lowercase singular [task]; they render as "
                            + "plain text and bind NO test (and leak the raw test names). Rewrite each as [task][Title](testName).");
        }

        // Compute once and let the gate decide; surfaced to the agent verbatim (the C++/Catch2 bare-name trap).
        List<String> unresolvedTaskBindings = unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        boolean taskBindingsResolve = checkTaskBindingsResolve(unresolvedTaskBindings, solution, problemStatementHasTasks, reasons);
        boolean noTaskTestPassesTemplate = checkNoTaskBoundTestPassesTemplate(problemStatement, solution, template, problemStatementHasTasks, taskBindingsResolve, reasons);
        boolean noGradableTestPassesTemplate = checkNoGradableTestPassesTemplate(solution, template, reasons);
        boolean solutionScaClean = checkSolutionScaClean(exercise, solution, reasons);

        // Prose hygiene: the oracle is blind to what the student-facing statement exposes, so this gate blocks leaks of grader internals or bare task markers (with exact phrases).
        List<String> proseHygieneLeaks = proseHygieneLeaks(problemStatement);
        boolean proseHygienic = proseHygieneLeaks.isEmpty();
        if (!proseHygienic) {
            reasons.add("The problem statement leaks grader internals or stray task markers into student-facing prose: " + proseHygieneLeaks
                    + ". Rewrite it as a student would read it — describe the required behaviour and edge cases, never how the exercise is built, tested or graded, and bind tasks "
                    + "only via [task][Title](testName) lines.");
        }

        boolean actionableGatesPass = solutionPassed && templateFailed && testCount > 0 && emitterSoundness.solutionNamesComplete() && emitterSoundness.templateFailNamesSound()
                && problemStatementHasTasks && taskKeywordsWellFormed && taskBindingsResolve && noTaskTestPassesTemplate && noGradableTestPassesTemplate && solutionScaClean
                && proseHygienic;

        List<String> possiblyDeadFiles = possiblyDeadWorkspaceFiles(sandbox, sessionId);
        return new DifferentialAnalysis(solution, template, solutionPassed, templateFailed, actionableGatesPass, reasons, unresolvedTaskBindings, possiblyDeadFiles);
    }

    /**
     * The shared, sandbox-dependent half of verification: the two parsed build summaries plus the actionable gate outcome. Consumed by both the post-loop {@link #verify} (which
     * adds
     * the integrity gates and verdict) and the in-loop {@link #selfCheck} (which renders the agent observation).
     *
     * @param actionableGatesPass    whether EVERY sandbox-dependent gate held (the integrity gates are layered on top by {@link #verify})
     * @param actionableReasons      the reasons any sandbox-dependent gate failed (empty when all hold); the SAME wording {@link #verify} surfaces
     * @param unresolvedTaskBindings the {@code [task]} bindings referencing no real test (surfaced to the agent verbatim)
     * @param possiblyDeadFiles      best-effort workspace files no build phase appears to read (advisory; empty when unavailable)
     */
    private record DifferentialAnalysis(BuildSummary solution, BuildSummary template, boolean solutionPassed, boolean templateFailed, boolean actionableGatesPass,
            List<String> actionableReasons, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles) {
    }

    /**
     * Runs one PRISTINE build for the given assignment, then copies out the build-fresh reports from the verifier-owned reports dir (a constant path, never derived from agent
     * output) and parses them with the production parsers into a {@link BuildSummary}. The tar is validated by {@link CollectedReports} before any byte is parsed.
     *
     * @param assignmentName the assignment directory name ({@code solution}/{@code template}); also the reports subdir name and the copyOut prefix
     */
    private BuildSummary runPristineBuild(InteractiveSandbox sandbox, String sessionId, String buildCommand, String assignmentName) {
        SandboxExecResult run = sandbox.exec(sessionId, VERIFY_TIMEOUT, "sh", "-c", buildCommand);
        if (run.timedOut()) {
            return BuildSummary.timedOut(run.exitCode());
        }
        Map<String, byte[]> reports;
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, SandboxBuildCommandService.reportsDirectoryFor(assignmentName))) {
            // Docker prefixes copied-out entries with the source directory name; strip it to get the flat collected names.
            reports = tar == null ? Map.of() : CollectedReports.read(tar, assignmentName);
        }
        catch (CollectedReports.RejectedReportException e) {
            // A linked/escaping/oversize entry is a hard integrity failure: treat as a non-compiling build rather than parsing partial input.
            log.warn("Rejected the verifier reports archive for the {} build: {}", assignmentName, e.getMessage());
            return BuildSummary.unparseable(run.exitCode());
        }
        catch (IOException e) {
            log.warn("Could not read the verifier reports archive for the {} build: {}", assignmentName, e.getMessage());
            return BuildSummary.unparseable(run.exitCode());
        }
        return BuildSummary.fromReports(reports, run.exitCode());
    }

    /** The solution gate: the solution must compile, run at least one test, and pass every test. Appends a rejection reason to {@code reasons} otherwise. */
    private static boolean checkSolutionPasses(BuildSummary solution, List<String> reasons) {
        boolean solutionPassed = !solution.timedOut() && solution.exitCode() == 0 && solution.tests() > 0 && solution.failures() == 0 && solution.errors() == 0;
        if (solution.timedOut()) {
            reasons.add("The solution build timed out. The solution must compile and pass every test within the time limit.");
        }
        else if (solution.tests() == 0) {
            reasons.add("No tests were detected when building against the solution. The exercise must contain at least one meaningful test, and the solution must compile so the "
                    + "tests can run.");
        }
        else if (!solutionPassed) {
            reasons.add("The solution does not pass its own tests (" + solution.failures() + " failing, " + solution.errors() + " erroring of " + solution.tests()
                    + "). The solution must compile and pass every test.");
        }
        return solutionPassed;
    }

    /**
     * The template gate: the template must compile and run the SAME tests as the solution but fail at least half of them (a near-complete template is not a real starting point;
     * {@code tests()==0} means it did not compile). Appends a rejection reason to {@code reasons} otherwise.
     *
     * @param solution the solution build summary (its test count is the reference)
     */
    private static boolean checkTemplateFails(BuildSummary solution, BuildSummary template, List<String> reasons) {
        int testCount = solution.tests();
        int templateFailing = template.failures() + template.errors();
        int requiredTemplateFailures = Math.max(1, testCount / 2);
        boolean templateCompiledAndRan = !template.timedOut() && template.tests() > 0;
        if (template.timedOut()) {
            reasons.add("The template build timed out; it must compile and fail the tests quickly.");
        }
        else if (template.tests() == 0) {
            reasons.add("The template does not compile (the tests never ran). The template must compile and only fail because the student's work is missing — use placeholder "
                    + "method bodies (returning null, 0, false) with the same signatures as the solution.");
        }
        else if (templateCompiledAndRan && solution.tests() > 0 && template.tests() != solution.tests()) {
            // A differing count means the template silently dropped tests, letting a vacuous template "fail" without the tests discriminating.
            reasons.add("The template runs a different number of tests (" + template.tests() + ") than the solution (" + solution.tests()
                    + "). Both must run the same tests; the template must differ only in its (unimplemented) method bodies, not in which tests compile and run.");
        }
        else if (templateFailing == 0) {
            reasons.add("The template passes the tests, but it must fail them (a student starting from the template has not implemented the solution yet). Make the tests assert "
                    + "behaviour the template's placeholder implementations cannot satisfy.");
        }
        else if (templateFailing < requiredTemplateFailures) {
            reasons.add("The template fails only " + templateFailing + " of " + testCount + " tests, so it is nearly complete. A meaningful starting template should fail at least "
                    + requiredTemplateFailures + " — strip its implementations to placeholders and make sure the tests check the behaviour the student must implement.");
        }
        else {
            // Correctly-failing template. Trust the JUnit failure/error counts, NOT the exit code: some report converters (Go's go-junit-report, Dart's tojunit) exit 0 even on
            // failure.
            return true;
        }
        return false;
    }

    /**
     * Whether the per-test emission is sound enough to trust the binding-resolution and per-test gates.
     *
     * @param solutionNamesComplete  the solution recorded a test name for every test it ran
     * @param templateFailNamesSound the template recorded WHICH tests failed for its failing/erroring run
     */
    private record EmitterSoundness(boolean solutionNamesComplete, boolean templateFailNamesSound) {
    }

    /**
     * Emitter soundness, fail-CLOSED: a missing/short per-test name or fail set is evidence of a broken or forged emitter that would silently disable the binding-resolution and
     * per-test gates, so we REJECT rather than skip, appending a reason for each gap.
     */
    private static EmitterSoundness checkEmitterSoundness(BuildSummary solution, BuildSummary template, List<String> reasons) {
        boolean solutionNamesComplete = solution.tests() > 0 && solution.testNames().size() >= solution.tests();
        if (solution.tests() > 0 && !solutionNamesComplete) {
            reasons.add("The solution ran " + solution.tests() + " tests but the verifier only recorded " + solution.testNames().size()
                    + " test name(s). The per-test name list must be complete for the grading checks to run; an incomplete list means the test reports could not be parsed "
                    + "reliably (or the build emitted no per-test results), so the exercise cannot be verified. Ensure every test writes a JUnit <testcase> entry.");
        }
        int templateFailing = template.failures() + template.errors();
        boolean templateCompiledAndRan = !template.timedOut() && template.tests() > 0;
        boolean templateFailNamesSound = !(templateCompiledAndRan && templateFailing > 0 && template.testFailedNames().isEmpty());
        if (!templateFailNamesSound) {
            reasons.add("The template reported " + templateFailing
                    + " failing/erroring test(s) but the verifier could not record WHICH tests failed (no per-test failure entries). "
                    + "Without the failing-test names the verifier cannot confirm that every graded test fails on the template, so the exercise cannot be verified. Ensure the test "
                    + "reports record each failing <testcase> with its <failure>/<error>.");
        }
        return new EmitterSoundness(solutionNamesComplete, templateFailNamesSound);
    }

    /**
     * The binding-resolution gate: a {@code [task]}'s names must be real runner test names, not a {@code @DisplayName} or prose title; a binding that resolves to nothing silently
     * shows no progress in Artemis, which the differential build cannot detect. Decides from the precomputed unresolved list and fails open when no trustworthy set was emitted.
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

    /** The distinct non-{@code [task]} keywords used on task-binding-shaped lines; empty when every such line uses {@code [task]}. See {@link #TASK_LIKE_BINDING}. */
    private static List<String> malformedTaskKeywords(String problemStatement) {
        Matcher matcher = TASK_LIKE_BINDING.matcher(problemStatement);
        List<String> wrongKeywords = new ArrayList<>();
        while (matcher.find()) {
            String keyword = matcher.group(1);
            if (!"task".equals(keyword) && !wrongKeywords.contains(keyword)) {
                wrongKeywords.add(keyword);
            }
        }
        return wrongKeywords;
    }

    /**
     * The distinct student-facing prose-hygiene leaks in the problem statement: bare {@code [task]}/{@code [tasks]} markers not in binding position, plus high-precision
     * grader-mechanics phrasings. Empty when the prose is clean. Surfaced verbatim to the agent so a retry can excise the exact offending text. See {@link #BARE_TASK_KEYWORD} and
     * {@link #PROSE_MECHANICS_LEAKS}.
     */
    private static List<String> proseHygieneLeaks(String problemStatement) {
        List<String> leaks = new ArrayList<>();
        if (BARE_TASK_KEYWORD.matcher(problemStatement).find()) {
            leaks.add("a bare [task]/[tasks] marker outside a [task][Title](testName) binding");
        }
        for (Pattern pattern : PROSE_MECHANICS_LEAKS) {
            Matcher matcher = pattern.matcher(problemStatement);
            if (matcher.find()) {
                String matched = '"' + matcher.group() + '"';
                if (!leaks.contains(matched)) {
                    leaks.add(matched);
                }
            }
        }
        return leaks;
    }

    /**
     * The strict per-test differential gate: every {@code [task]}-bound test the SOLUTION passes must FAIL on the TEMPLATE. A graded test the template already satisfies (a
     * {@code return 0} stub passing {@code fibonacci(0)==0}) hands the student a free point even if the count gate passed. Fails open when the name/fail sets are untrustworthy.
     *
     * @param taskBindingsResolve whether the bindings resolve (the gate reports only when they do)
     */
    private static boolean checkNoTaskBoundTestPassesTemplate(String problemStatement, BuildSummary solution, BuildSummary template, boolean problemStatementHasTasks,
            boolean taskBindingsResolve, List<String> reasons) {
        List<String> taskTestsPassingOnTemplate = taskBoundTestsThatPassOnTemplate(problemStatement, solution, template);
        boolean noTaskTestPassesTemplate = taskTestsPassingOnTemplate.isEmpty();
        if (problemStatementHasTasks && taskBindingsResolve && !noTaskTestPassesTemplate) {
            reasons.add("These [task]-bound tests PASS on the template, which gives the student free points before doing any work: " + taskTestsPassingOnTemplate
                    + ". Every graded ([task]-bound) test must FAIL on the template — the template's placeholder bodies must not accidentally satisfy a graded assertion (e.g. a "
                    + "`return 0` stub must not pass an `expected == 0` test). Strip the template so every graded test fails, or make the test assert behaviour the placeholder cannot meet.");
        }
        return noTaskTestPassesTemplate;
    }

    /**
     * The production-parity gate: production grades EVERY discovered test, not only the {@code [task]}-bound subset, so an unbound test passing on the template still gives a
     * bare-template student {@code >0%}. We require every solution-passing test to FAIL on the template, except the build/compile/configure gates ({@link #isBuildGateTest}) that
     * legitimately pass on both. Fails open under the same guards as the {@code [task]} gate.
     */
    private static boolean checkNoGradableTestPassesTemplate(BuildSummary solution, BuildSummary template, List<String> reasons) {
        List<String> gradableTestsPassingOnTemplate = gradableTestsThatPassOnTemplate(solution, template);
        boolean noGradableTestPassesTemplate = gradableTestsPassingOnTemplate.isEmpty();
        if (!noGradableTestPassesTemplate) {
            reasons.add("These tests PASS on the template, but production grades EVERY discovered test (not only the [task]-bound ones), so a student submitting the bare template "
                    + "would score above 0% on them: " + gradableTestsPassingOnTemplate
                    + ". Every gradable test the solution passes must FAIL on the template — a student starting "
                    + "from the template has implemented nothing, so their score must be exactly 0%. Make every test assert behaviour the template's placeholder bodies cannot "
                    + "satisfy (do NOT leave an unbound test, or a too-lucky placeholder that accidentally returns the expected value). Build/compile/configure gate tests "
                    + "(e.g. C++ TestConfigure/CompileSort, C Compile) are exempt because they only check that the code compiles, which the template does by design.");
        }
        return noGradableTestPassesTemplate;
    }

    /**
     * The SCA-parity gate: SCA reports carry no {@code <testcase>}, so the differential oracle is blind to them while production folds a penalty into the score. The solution's SCA
     * findings are handed to {@link ScaPenaltyParity}, which flags those production would actually penalise; silent and verdict-unchanged when none would dock.
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
     * Fail-CLOSED on a read-back gap: a repo seeded non-empty but extracted empty silently disables the harness/leak gates, so we reject rather than accept on that doubt. (A
     * genuinely empty repo is reported as NOT failed and stays fail-open.)
     */
    private static boolean checkExtractionSound(Set<String> extractionFailedRepositories, List<String> reasons) {
        boolean extractionSound = extractionFailedRepositories == null || extractionFailedRepositories.isEmpty();
        if (!extractionSound) {
            reasons.add("The generated files for these repositories could not be read back for verification: " + extractionFailedRepositories
                    + ". The integrity checks (harness immutability, solution leak) cannot run on missing files, so the exercise cannot be verified. This is usually a transient "
                    + "read-back error; retry the generation.");
        }
        return extractionSound;
    }

    /**
     * Renders a fresh pristine {@code verify.sh} into the verifier-owned directory OUTSIDE {@code /workspace} the agent tools cannot reach, so an agent edit to its own copy is
     * irrelevant.
     */
    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        String script = sandboxBuildCommandService.verifyScriptContent(exercise);
        // Docker's copy-to-container requires the destination directory to already exist.
        sandbox.exec(sessionId, READ_TIMEOUT, "mkdir", "-p", SandboxBuildCommandService.PRISTINE_VERIFY_DIR);
        sandbox.copyIn(sessionId, SandboxBuildCommandService.PRISTINE_VERIFY_DIR, singleFileTar(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, script));
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
     * The {@code [task]}-bound names that resolve to no real test, so the verifier can reject a checklist that binds to nothing. Forward-only (not every test must be bound) and
     * fails
     * open when the extracted name set is missing or shorter than the test count.
     * <p>
     * Structural-test exemption: a structural-shaped binding need not resolve, since the seeder injects {@code testClass[X]} tests AFTER the agent submits and only for a class
     * absent
     * from the template. Sound because the differential is keyed to REAL test names regardless of binding shape. A binding in the authoritative seeded set is also treated as
     * resolved.
     */
    private static List<String> unresolvedTaskBindings(String problemStatement, List<String> actualTestNames, int testCount, Set<String> seededStructuralTestNames) {
        if (actualTestNames.isEmpty() || actualTestNames.size() < testCount) {
            return List.of();
        }
        Set<String> actual = actualTestNames.stream().map(AuthoritativeVerificationService::normalizeTestName).collect(Collectors.toSet());
        Set<String> seededStructural = seededStructuralTestNames == null ? Set.of()
                : seededStructuralTestNames.stream().map(AuthoritativeVerificationService::normalizeTestName).collect(Collectors.toSet());
        List<String> unresolved = new ArrayList<>();
        Matcher matcher = TASK_BINDING.matcher(problemStatement);
        while (matcher.find()) {
            for (String rawName : matcher.group(1).split(",")) {
                String name = rawName.trim();
                if (name.isEmpty()) {
                    continue;
                }
                String normalized = normalizeTestName(name);
                if (actual.contains(normalized) || seededStructural.contains(normalized)) {
                    continue;
                }
                if (!isStructuralTestName(name) && !unresolved.contains(name)) {
                    unresolved.add(name);
                }
            }
        }
        return unresolved;
    }

    /** Whether a name has the Ares structural-test shape ({@code testClass[X]} / {@code testMethods[X]} / …). */
    private static boolean isStructuralTestName(String name) {
        return STRUCTURAL_TEST_NAME.matcher(name.trim()).matches();
    }

    /**
     * The {@code [task]}-bound test names that PASS on the template (resolve to a real solution test but are NOT in the template's failed/errored set) — the accidental free points
     * the strict differential rejects. Fails open (empty) when the template emitted no fail lines or the solution name set is missing/short.
     */
    private static List<String> taskBoundTestsThatPassOnTemplate(String problemStatement, BuildSummary solution, BuildSummary template) {
        List<String> solutionNames = solution.testNames();
        if (solutionNames.isEmpty() || solutionNames.size() < solution.tests()) {
            return List.of();
        }
        if (template.testFailedNames().isEmpty()) {
            return List.of();
        }
        Set<String> solutionPassing = solutionNames.stream().map(AuthoritativeVerificationService::normalizeTestName).collect(Collectors.toSet());
        Set<String> templateFailed = template.testFailedNames().stream().map(AuthoritativeVerificationService::normalizeTestName).collect(Collectors.toSet());
        List<String> offending = new ArrayList<>();
        Matcher matcher = TASK_BINDING.matcher(problemStatement);
        while (matcher.find()) {
            for (String rawName : matcher.group(1).split(",")) {
                String name = rawName.trim();
                if (name.isEmpty()) {
                    continue;
                }
                String normalized = normalizeTestName(name);
                if (solutionPassing.contains(normalized) && !templateFailed.contains(normalized) && !offending.contains(name)) {
                    offending.add(name);
                }
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
     * Every solution-passing test that is NOT in the template's failed/errored set, EXCLUDING the build gates ({@link #isBuildGateTest}). Mirrors how production grades (every
     * discovered test, not only the {@code [task]}-bound subset). Fails open under the same guards as {@link #taskBoundTestsThatPassOnTemplate}.
     */
    private static List<String> gradableTestsThatPassOnTemplate(BuildSummary solution, BuildSummary template) {
        List<String> solutionNames = solution.testNames();
        if (solutionNames.isEmpty() || solutionNames.size() < solution.tests()) {
            return List.of();
        }
        if (template.testFailedNames().isEmpty()) {
            return List.of();
        }
        Set<String> templateFailed = template.testFailedNames().stream().map(AuthoritativeVerificationService::normalizeTestName).collect(Collectors.toSet());
        List<String> offending = new ArrayList<>();
        for (String rawName : solutionNames) {
            String normalized = normalizeTestName(rawName);
            if (isBuildGateTest(normalized)) {
                continue;
            }
            if (!templateFailed.contains(normalized) && !offending.contains(normalized)) {
                offending.add(normalized);
            }
        }
        return offending;
    }

    /**
     * The solution-build SCA findings production WOULD penalise, rendered as {@code <TOOL>|<category>}; empty when SCA cannot dock the solution. Reads the persisted categories the
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

    /** Normalises a test name for matching: trims whitespace and drops a trailing {@code ()}, so {@code testFoo} and {@code testFoo()} compare equal (as Artemis treats them). */
    private static String normalizeTestName(String name) {
        String normalized = name.trim();
        if (normalized.endsWith("()")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        return normalized;
    }

    /**
     * The aggregated test outcome of one {@code verify.sh} run, built by parsing the collected report files with the SAME production parsers LocalCI uses
     * ({@link TestResultXmlParser}
     * for JUnit, {@link ReportParser} for SCA), so the oracle's view is parity-by-construction with grading.
     *
     * @param tests           tests that ran (zero when the build did not reach the runner, e.g. a compile error); excludes {@code <skipped>} cases, exactly as production grades
     * @param testNames       distinct test-case names from the JUnit XML, composed exactly as production does; empty if none collected
     * @param testFailedNames distinct names of cases that FAILED/ERRORED; used by the strict per-test gate; empty if none collected
     * @param scaFindings     SCA findings (tool + real derived category from {@link ReportParser}); populated only when the SCA reports were collected; empty otherwise
     */
    record BuildSummary(int tests, int failures, int errors, int exitCode, boolean timedOut, List<String> testNames, List<String> testFailedNames,
            List<ScaPenaltyParity.ScaFinding> scaFindings) {

        /** Build killed for exceeding its timeout; treated as a failed build with no tests. */
        static BuildSummary timedOut(int exitCode) {
            return new BuildSummary(0, 0, 0, exitCode, true, List.of(), List.of(), List.of());
        }

        /** Reports archive unreadable or rejected (linked/escaping/oversize entry): treat as a non-compiling build (no tests) so the oracle rejects, fail-closed. */
        static BuildSummary unparseable(int exitCode) {
            return new BuildSummary(0, 0, 0, exitCode == 0 ? 1 : exitCode, false, List.of(), List.of(), List.of());
        }

        /**
         * Builds the summary from the collected report files (flat name -> bytes): JUnit-token files are parsed by {@link TestResultXmlParser}, recognised SCA reports by
         * {@link ReportParser}. A report that fails to parse is skipped (fail-open per file), so one malformed report cannot crash the verdict.
         */
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
                        log.warn("Skipping an unparseable JUnit report ({}): {}", report.getKey(), e.getMessage());
                    }
                }
                else {
                    parseScaReport(content, canonical, scaFindings);
                }
            }
            List<String> testNames = new ArrayList<>();
            List<String> failedNames = new ArrayList<>();
            failed.forEach(job -> {
                testNames.add(job.name());
                failedNames.add(job.name());
            });
            successful.forEach(job -> testNames.add(job.name()));
            int tests = failed.size() + successful.size();
            return new BuildSummary(tests, failed.size(), 0, exitCode, false, List.copyOf(testNames), List.copyOf(failedNames), List.copyOf(scaFindings));
        }

        /**
         * Parses one collected SCA report with the production {@link ReportParser} (routed by its canonical report file name) and appends each issue's {@code (tool, derived
         * category)} to {@code scaFindings}. An unsupported tool or a malformed report is skipped (fail-open), so a stray non-SCA file cannot break the verdict.
         */
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
                log.warn("Skipping an unparseable SCA report ({}): {}", canonicalFileName, e.getMessage());
            }
        }

        /** The canonical routing token a collected file name carries (the segment after the {@code <seq>__} prefix): the JUnit token or an SCA tool's canonical report name. */
        private static String canonicalToken(String collectedName) {
            int sep = collectedName.indexOf(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR);
            return sep < 0 ? collectedName : collectedName.substring(sep + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR.length());
        }
    }
}
