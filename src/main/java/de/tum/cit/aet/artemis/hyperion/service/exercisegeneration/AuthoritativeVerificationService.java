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
 * <strong>The verdict is parsed with PRODUCTION code, not in the shell.</strong> The pristine {@code verify.sh} runs the real per-language build phases and COLLECTS the
 * build-fresh
 * report files into a fixed, verifier-owned directory ({@link SandboxBuildCommandService#REPORTS_DIR}). This service then {@code copyOut}s that directory (a constant path the
 * verifier knows a priori, never derived from agent output), validates the tar with the hardened {@link CollectedReports} reader (regular files only, no symlink/hardlink, no path
 * escape, bounded size), and parses the surviving files with the SAME production parsers the real LocalCI pipeline uses: {@link TestResultXmlParser} for JUnit (which skips
 * {@code <skipped>} testcases exactly as production grading does, so skipped-test parity is guaranteed) and {@link ReportParser} for SCA (whose findings carry the real derived
 * category, including SARIF/GCC). This gives parity-by-construction, a real XML parser, and production SCA category derivation; the differential and integrity gates are unchanged
 * —
 * only the SOURCE of the {@link BuildSummary} they consume changed.
 * <p>
 * <strong>Non-forgeability without a nonce.</strong> Authenticity rests on the verifier running a re-seeded pristine script the agent cannot reach AND reading the reports from a
 * constant verifier-owned directory the agent cannot write — not on a per-run marker token. So the old anti-forgery nonce is gone: there are no stdout markers for the agent's test
 * code to forge in the first place. The residual "the agent's test writes a fake passing report at build time" is not widened by this change and is already neutralized by the
 * differential (a forged always-pass report also makes the TEMPLATE pass, tripping {@code checkTemplateFails}).
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class AuthoritativeVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AuthoritativeVerificationService.class);

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Production grading truncates feedback messages to this length; the verifier sets the same bound so its parse matches production exactly (it never reads the messages). */
    private static final int MAX_FEEDBACK_LENGTH = 20_000;

    /**
     * Matches a problem-statement task binding and captures its parenthesised, comma-separated test names: {@code [task][Some title](testA,testB)}. The capture is greedy (up to
     * the
     * LAST {@code )} on the line) because a test identifier can itself contain parentheses (a JVM/Ares name is reported as {@code testFoo()}); {@link #normalizeTestName} strips a
     * trailing {@code ()} so the paren and no-paren binding forms resolve to the same test.
     */
    private static final Pattern TASK_BINDING = Pattern.compile("\\[task\\]\\[[^\\]]*\\]\\((.*)\\)");

    /**
     * The shape of an Ares auto-generated structural test-case name ({@code testClass[X]}, {@code testMethods[X]}, …). Used ONLY to relax binding RESOLUTION (a structural-shaped
     * binding need not resolve, since the seeder injects these AFTER the agent submits), never the differential — a real behaviour test named {@code testClass[Evil]} still must
     * fail
     * on the template via the gradable/per-test gates.
     */
    private static final Pattern STRUCTURAL_TEST_NAME = Pattern.compile("test(?:Class|Methods|Attributes|Constructors)\\[[^\\]]+\\]");

    private static final int PRISTINE_SCRIPT_MODE = 0755;

    private final SandboxBuildCommandService sandboxBuildCommandService;

    /**
     * Persisted SCA categories read the SAME way production grading does ({@code findByExerciseId}), so the SCA-parity gate decides from the authoritative category state rather
     * than
     * the in-memory exercise's detached, lazily-loaded collection. Optional because SCA categories live in the core profile; absent on a node that cannot grade anyway, where the
     * gate then fails open.
     */
    private final Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository;

    // @Autowired disambiguates this from the package-private test constructor; with two constructors and no annotation Spring fails to instantiate the bean.
    @Autowired
    public AuthoritativeVerificationService(SandboxBuildCommandService sandboxBuildCommandService,
            Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        // The production grading parser is configured statically; set the feedback bound once so the verifier's parse matches production.
        TestResultXmlParser.setMaxFeedbackLength(MAX_FEEDBACK_LENGTH);
    }

    AuthoritativeVerificationService(SandboxBuildCommandService sandboxBuildCommandService) {
        this(sandboxBuildCommandService, Optional.empty());
    }

    private String readProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        return result.isSuccess() ? result.stdout() : "";
    }

    /** Upper bound on the dead-file probe output kept inline, so a huge or runaway workspace listing can never blow the agent's context. */
    private static final int MAX_POSSIBLY_DEAD_FILES = 20;

    /**
     * A best-effort, LANGUAGE-AGNOSTIC dead-file probe surfaced to the agent's self-check (advisory only — never a gate): a source file present in exactly ONE of {@code solution/}
     * and {@code template/}. Since the two repos must differ only in their (unimplemented) method bodies, a source file in one but not the other is a likely abandoned orphan from
     * a
     * replaced approach (the from-scratch prompt warns about exactly this). It deliberately ignores build manifests and hidden files (legitimately repo-specific) and never throws
     * —
     * any probe error yields an empty list, so the self-check degrades to "no dead-file hint" rather than failing. It is purely informational; the differential verdict ignores it.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
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

    /** The build-manifest filenames that are legitimately repo-specific (a template may carry a manifest the solution does not), so the dead-file probe must not flag them. */
    private static final Set<String> BUILD_MANIFEST_NAMES = Set.of("go.mod", "go.sum", "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "cargo.toml", "cargo.lock", "makefile", "package.json", "package-lock.json", "tsconfig.json", "build.sbt", "pubspec.yaml", "pubspec.lock");

    /**
     * Lists the assignment repo's SOURCE files (repo-relative) for the dead-file probe: regular files under the repo, excluding hidden files/dirs and the known build manifests
     * (which are legitimately repo-specific). Returns an empty set on any non-success, so the probe fails open.
     */
    private static Set<String> listSourceFiles(InteractiveSandbox sandbox, String sessionId, String repoDirectory) {
        String repoRoot = GenerationWorkspaceService.WORKSPACE + "/" + repoDirectory;
        // -type f lists regular files only (skips dirs/symlinks); the sed strips the repo prefix to repo-relative paths; grep -v drops dotfiles/dotdirs.
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

    // Package-private convenience overloads used only by the unit test (no integrity/structural inputs); production calls the full verify(...) below.
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
     * <strong>Non-forgeable verdict.</strong> The agent's {@code /workspace/verify.sh} is agent-writable, so the verifier instead RE-SEEDS a freshly-rendered pristine
     * {@code verify.sh} to a verifier-owned path OUTSIDE {@code /workspace} ({@link SandboxBuildCommandService#PRISTINE_VERIFY_PATH}) the agent tools cannot reach, and runs THAT
     * copy; that copy deletes any pre-existing report XML and counts only reports written during the build, so a planted report cannot be summed either.
     * <p>
     * The integrity gates fail OPEN when their inputs are GENUINELY empty (a legitimately empty repo) but fail CLOSED when a repo seeded non-empty extracts empty at verify time
     * (signalled via {@code extractionFailedRepositories}), so a flaky read-back cannot silently disable a gate.
     *
     * @param sandbox                      the sandbox session the differential builds run in
     * @param sessionId                    the sandbox session handle
     * @param exercise                     the exercise whose per-language {@code verify.sh} is rendered and run
     * @param seedTestsFiles               tests-repo files snapshotted at seed time (repository-relative); enables the harness-immutability gate
     * @param producedTestsFiles           tests-repo files read back after generation (repository-relative)
     * @param producedTemplateFiles        template-repo files read back after generation (repository-relative); enables the solution-leak gate
     * @param producedSolutionFiles        solution-repo files read back after generation (repository-relative)
     * @param extractionFailedRepositories repository directory names whose read-back FAILED (seeded non-empty but extracted empty); fail-closed signal distinct from a genuinely
     *                                         empty repo
     * @param seededStructuralTestNames    the AUTHORITATIVE structural test names the seeder injected this run (never agent-supplied); a {@code [task]} bound to one is exempt from
     *                                         binding RESOLUTION but still participates in the differential. Empty for callers without it (the from-scratch path falls back to the
     *                                         name-shape exemption)
     * @return the verdict and, on rejection, the reasons
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories,
            Set<String> seededStructuralTestNames) {
        // The sandbox-dependent differential (two pristine builds + the actionable gates) is computed by the SAME method the in-loop self-check uses, so the agent's `verify` tool
        // and this acceptance decision can never diverge. This call layers the sandbox-FREE integrity gates and the final verdict on top of that shared analysis.
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, seededStructuralTestNames);
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();
        List<String> reasons = new ArrayList<>(analysis.actionableReasons());

        // Sandbox-free integrity gates the build cannot see: a tampered graded-verbatim harness, or a solution leaked into the student-facing template. Both fail OPEN on genuinely
        // empty inputs. These run ONLY post-loop (the self-check skips them): they need the seed snapshot and read-back files the agent loop does not have mid-session.
        List<String> harnessTamperingReasons = ExerciseIntegrityGate.harnessTamperingReasons(seedTestsFiles, producedTestsFiles);
        boolean harnessIntact = harnessTamperingReasons.isEmpty();
        reasons.addAll(harnessTamperingReasons);
        List<String> solutionLeakReasons = ExerciseIntegrityGate.solutionLeakReasons(producedTemplateFiles, producedSolutionFiles);
        boolean noSolutionLeak = solutionLeakReasons.isEmpty();
        reasons.addAll(solutionLeakReasons);

        boolean extractionSound = checkExtractionSound(extractionFailedRepositories, reasons);

        boolean accepted = analysis.actionableGatesPass() && harnessIntact && noSolutionLeak && extractionSound;
        if (!accepted) {
            log.info("Authoritative verification failed: solution[{}], template[{}], actionableGatesPass={}, harnessIntact={}, noSolutionLeak={}, extractionSound={}", solution,
                    template, analysis.actionableGatesPass(), harnessIntact, noSolutionLeak, extractionSound);
        }
        return new VerificationResult(accepted, analysis.solutionPassed(), analysis.templateFailed(), solution.tests(), reasons);
    }

    /**
     * The IN-LOOP self-check the agent's {@code verify} tool calls: runs the SAME two pristine builds + production parse + the actionable acceptance gates as the post-loop
     * {@link #verify}, and returns a rich, agent-readable {@link AgentVerifyReport} (which tests pass/fail on the solution and template, the exact parser-form names to bind
     * {@code [task]}s to, the wrongly-passing template tests, and the unresolved bindings) — so the agent sees exactly what the acceptance verdict will conclude every time it
     * asks.
     * <p>
     * It deliberately SKIPS the sandbox-free integrity gates (harness immutability, solution leak): those need the seed snapshot and post-generation read-back the agent loop does
     * not have, and they stay post-loop-only. The {@code wouldBeAccepted} flag therefore reflects the differential + actionable gates only; the post-loop {@link #verify} remains
     * the
     * sole acceptance truth. Each call re-runs the two builds (no stale cache), so the report always reflects the current workspace.
     *
     * @param sandbox   the sandbox session the differential builds run in
     * @param sessionId the sandbox session handle
     * @param exercise  the exercise whose per-language {@code verify.sh} is rendered and run (its SCA configuration governs the SCA gate)
     * @return the structured in-loop report
     */
    public AgentVerifyReport selfCheck(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        // The agent could not bind to structural tests seeded AFTER it submits, so the self-check passes no authoritative seeded set; the name-shape exemption still applies.
        DifferentialAnalysis analysis = runDifferential(sandbox, sessionId, exercise, Set.of());
        BuildSummary solution = analysis.solution();
        BuildSummary template = analysis.template();

        boolean solutionPassed = analysis.solutionPassed();
        boolean templateCompiled = !template.timedOut() && template.tests() > 0;
        // The template tests the solution passes but the template does NOT fail — the Go/no-exception zero-value-stub trap. Reuse the production-parity gate's exact computation so
        // the names the agent sees are precisely the ones that would block acceptance, minus the legitimately-exempt build gates.
        List<String> templateWronglyPassing = templateCompiled ? gradableTestsThatPassOnTemplate(solution, template) : List.of();

        return new AgentVerifyReport(solution.tests(), solutionPassed, List.copyOf(solution.testFailedNames()), template.tests(), templateCompiled, analysis.templateFailed(),
                templateWronglyPassing, List.copyOf(solution.testNames()), analysis.unresolvedTaskBindings(), analysis.possiblyDeadFiles(), analysis.actionableGatesPass(),
                analysis.actionableReasons());
    }

    /**
     * Runs the shared, sandbox-DEPENDENT half of verification ONCE: re-seeds and runs the two pristine builds, parses them with the production parsers, reads the problem
     * statement,
     * and applies every actionable gate (solution passes, template fails, emitter soundness, task-binding presence/resolution, the two no-test-passes-template gates, and SCA
     * parity). Both the post-loop {@link #verify} and the in-loop {@link #selfCheck} consume this, so the agent's feedback and the acceptance verdict are computed by identical
     * code.
     *
     * @param sandbox                   the sandbox session
     * @param sessionId                 the session handle
     * @param exercise                  the exercise (its {@code verify.sh} and SCA configuration)
     * @param seededStructuralTestNames the authoritative seeded structural test names exempt from binding resolution (empty for the self-check)
     * @return the parsed build summaries plus the actionable gate outcome (reasons, pass flag, and the derived agent-facing lists)
     */
    private DifferentialAnalysis runDifferential(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Set<String> seededStructuralTestNames) {
        List<String> reasons = new ArrayList<>();

        // Re-seed and invoke a PRISTINE verify.sh the agent could never have written, so any edit to its own /workspace/verify.sh is irrelevant to the verdict.
        seedPristineVerifyScript(sandbox, sessionId, exercise);
        // The pristine script collects the build-fresh reports into a fixed verifier-owned dir; we copy that dir out and parse it with the production parsers (no marker scraping).
        BuildSummary solution = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineSolutionBuildCommand(),
                GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
        BuildSummary template = runPristineBuild(sandbox, sessionId, sandboxBuildCommandService.pristineTemplateBuildCommand(),
                GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));

        int testCount = solution.tests();
        boolean solutionPassed = checkSolutionPasses(solution, reasons);
        boolean templateFailed = checkTemplateFails(solution, template, reasons);
        EmitterSoundness emitterSoundness = checkEmitterSoundness(solution, template, reasons);

        // The exercise must bind its tests to the problem statement via [task][title](testNames); without this the student sees no task checklist (a Maven project, not an Artemis
        // exercise).
        String problemStatement = readProblemStatement(sandbox, sessionId);
        boolean problemStatementHasTasks = TASK_BINDING.matcher(problemStatement).find();
        if (!problemStatementHasTasks) {
            reasons.add("The problem statement has no Artemis task bindings. Add at least one line of the form [task][Title](testName) binding the graded tests to tasks so they "
                    + "appear as a checklist for students.");
        }

        // The unresolved bindings are surfaced to the agent verbatim (the C++/Catch2 bare-name trap), so compute the list once and let the gate decide from it.
        List<String> unresolvedTaskBindings = unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        boolean taskBindingsResolve = checkTaskBindingsResolve(unresolvedTaskBindings, solution, problemStatementHasTasks, reasons);
        boolean noTaskTestPassesTemplate = checkNoTaskBoundTestPassesTemplate(problemStatement, solution, template, problemStatementHasTasks, taskBindingsResolve, reasons);
        boolean noGradableTestPassesTemplate = checkNoGradableTestPassesTemplate(solution, template, reasons);
        boolean solutionScaClean = checkSolutionScaClean(exercise, solution, reasons);

        boolean actionableGatesPass = solutionPassed && templateFailed && testCount > 0 && emitterSoundness.solutionNamesComplete() && emitterSoundness.templateFailNamesSound()
                && problemStatementHasTasks && taskBindingsResolve && noTaskTestPassesTemplate && noGradableTestPassesTemplate && solutionScaClean;

        List<String> possiblyDeadFiles = possiblyDeadWorkspaceFiles(sandbox, sessionId);
        return new DifferentialAnalysis(solution, template, solutionPassed, templateFailed, actionableGatesPass, reasons, unresolvedTaskBindings, possiblyDeadFiles);
    }

    /**
     * The shared, sandbox-dependent half of verification: the two parsed build summaries plus the actionable gate outcome. Consumed by both the post-loop {@link #verify} (which
     * adds
     * the integrity gates and verdict) and the in-loop {@link #selfCheck} (which renders the agent observation).
     *
     * @param solution               the parsed solution build summary
     * @param template               the parsed template build summary
     * @param solutionPassed         whether the solution gate held
     * @param templateFailed         whether the template gate held
     * @param actionableGatesPass    whether EVERY sandbox-dependent gate held (the integrity gates are layered on top by {@link #verify})
     * @param actionableReasons      the human-readable reasons any sandbox-dependent gate failed (empty when all hold); the SAME wording {@link #verify} surfaces
     * @param unresolvedTaskBindings the {@code [task]} bindings referencing no real test (surfaced to the agent verbatim)
     * @param possiblyDeadFiles      best-effort workspace files no build phase appears to read (advisory; empty when unavailable)
     */
    private record DifferentialAnalysis(BuildSummary solution, BuildSummary template, boolean solutionPassed, boolean templateFailed, boolean actionableGatesPass,
            List<String> actionableReasons, List<String> unresolvedTaskBindings, List<String> possiblyDeadFiles) {
    }

    /**
     * Runs one PRISTINE (verifier-controlled) build for the given assignment, then copies the build-fresh reports the script collected into the verifier-owned reports dir out of
     * the container and parses them with the production parsers into a {@link BuildSummary}. The reports path is a CONSTANT the verifier knows a priori (never derived from agent
     * output), and the tar is validated by {@link CollectedReports} before any byte is parsed.
     *
     * @param sandbox        the sandbox session
     * @param sessionId      the session handle
     * @param buildCommand   the pristine verify.sh invocation for this assignment
     * @param assignmentName the assignment directory name ({@code solution}/{@code template}); also the reports subdir name and the copyOut prefix
     * @return the parsed build summary
     */
    private BuildSummary runPristineBuild(InteractiveSandbox sandbox, String sessionId, String buildCommand, String assignmentName) {
        SandboxExecResult run = sandbox.exec(sessionId, VERIFY_TIMEOUT, "sh", "-c", buildCommand);
        if (run.timedOut()) {
            return BuildSummary.timedOut(run.exitCode());
        }
        Map<String, byte[]> reports;
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, SandboxBuildCommandService.reportsDirectoryFor(assignmentName))) {
            // Docker prefixes copied-out entries with the source directory name (the reports subdir, e.g. "solution"); strip it to get the flat collected names.
            reports = tar == null ? Map.of() : CollectedReports.read(tar, assignmentName);
        }
        catch (CollectedReports.RejectedReportException e) {
            // A linked/escaping/oversize report entry is a hard integrity failure: refuse to trust the build rather than parsing partial input. Treat as a non-compiling build.
            log.warn("Rejected the verifier reports archive for the {} build: {}", assignmentName, e.getMessage());
            return BuildSummary.unparseable(run.exitCode());
        }
        catch (IOException e) {
            log.warn("Could not read the verifier reports archive for the {} build: {}", assignmentName, e.getMessage());
            return BuildSummary.unparseable(run.exitCode());
        }
        return BuildSummary.fromReports(reports, run.exitCode());
    }

    /**
     * The solution gate: the solution must compile, run at least one test, and have every test pass. Appends a rejection reason to {@code reasons} otherwise.
     *
     * @param solution the solution build summary
     * @param reasons  the running list of rejection reasons (appended to on failure)
     * @return {@code true} when the solution compiled, ran tests, and passed them all
     */
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
     * The template gate: the template must compile and run the SAME tests as the solution but fail at least half of them (a near-complete template that fails only one of many is
     * not
     * a real starting point, and {@code tests()==0} means it did not compile). Appends a rejection reason to {@code reasons} otherwise.
     *
     * @param solution the solution build summary (its test count is the reference)
     * @param template the template build summary
     * @param reasons  the running list of rejection reasons (appended to on failure)
     * @return {@code true} when the template compiled, ran the same tests, and (correctly) failed enough of them
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
            // A differing count means the template silently dropped tests, letting a vacuous template "fail" without the tests actually discriminating.
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
            // A correctly-failing template. Trust the JUnit failure/error counts, NOT the exit code: some languages pipe the test run through a report converter (Go's
            // go-junit-report, Dart's tojunit) that exits 0 even when tests failed.
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
     * Emitter soundness, fail-CLOSED: the pristine {@code verify.sh} always emits a TESTNAME per {@code <testcase>} and a TESTFAIL per failing one, so a missing/short set is
     * evidence of a broken or forged emitter that would silently disable the binding-resolution and per-test gates. We REJECT rather than skip, appending a reason for each gap.
     *
     * @param solution the solution build summary
     * @param template the template build summary
     * @param reasons  the running list of rejection reasons (appended to on failure)
     * @return whether the solution name list and the template fail-name list are complete enough to trust
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
     * The binding-resolution gate: a {@code [task]}'s names must be the real runner test names, not a {@code @DisplayName} or prose title; a binding that resolves to nothing
     * silently shows no progress in Artemis, which the differential build cannot detect. Decides from the precomputed unresolved list (so the agent self-check can surface the same
     * names verbatim) and fails open when no trustworthy set was emitted.
     *
     * @param unresolvedTaskBindings   the precomputed {@code [task]} bindings that resolve to no real test (the C++/Catch2 bare-name trap)
     * @param solution                 the solution build summary (its test names are the resolution target, included in the message)
     * @param problemStatementHasTasks whether the problem statement contains any {@code [task]} binding at all
     * @param reasons                  the running list of rejection reasons (appended to on failure)
     * @return whether every {@code [task]} binding resolves to a real (or seeded-structural) test name
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

    /**
     * The strict per-test differential gate: every {@code [task]}-bound test the SOLUTION passes must FAIL on the TEMPLATE. A graded test the template already satisfies (a
     * {@code return 0} stub passing {@code fibonacci(0)==0}) hands the student a free point even if the count gate passed. Fails open when the name/fail sets are untrustworthy.
     *
     * @param problemStatement         the produced problem statement
     * @param solution                 the solution build summary
     * @param template                 the template build summary
     * @param problemStatementHasTasks whether the problem statement contains any {@code [task]} binding
     * @param taskBindingsResolve      whether the bindings resolve (the gate only reports when they do)
     * @param reasons                  the running list of rejection reasons (appended to on failure)
     * @return whether no {@code [task]}-bound test passes on the template
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
     * The production-parity gate: production grades EVERY discovered test, not only the {@code [task]}-bound subset, so an unbound test that passes on the template still gives a
     * bare-template student {@code >0%} (observed: a Python template at 22.2% from one unbound test). We require every solution-passing test to FAIL on the template, except the
     * build/compile/configure gates ({@link #isBuildGateTest}) that legitimately pass on both because the same-signature template compiles by design. Fails open under the same
     * guards as the {@code [task]} gate.
     *
     * @param solution the solution build summary
     * @param template the template build summary
     * @param reasons  the running list of rejection reasons (appended to on failure)
     * @return whether no gradable test passes on the template
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
     * The SCA-parity gate: SCA reports carry no {@code <testcase>}, so the differential oracle is blind to them while production folds a penalty into the score. {@code verify.sh}
     * emits a {@code HYPERION_SCA} line per finding and {@link ScaPenaltyParity} flags those production would actually penalise (graded, positively-penalised category, matched to
     * the persisted categories); silent and verdict-unchanged when none would dock.
     *
     * @param exercise the exercise whose SCA configuration governs grading
     * @param solution the solution build summary (its SCA findings are checked)
     * @param reasons  the running list of rejection reasons (appended to on failure)
     * @return whether the solution produces no SCA finding production would penalise
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
     *
     * @param extractionFailedRepositories the repository directory names whose read-back failed (seeded non-empty but extracted empty)
     * @param reasons                      the running list of rejection reasons (appended to on failure)
     * @return whether every repository was read back successfully
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
     * Renders a fresh pristine {@code verify.sh} into the verifier-owned directory OUTSIDE {@code /workspace} ({@link SandboxBuildCommandService#PRISTINE_VERIFY_PATH}), which the
     * agent tools (which only resolve under {@code /workspace}) could never have reached — so an agent edit to its own copy is irrelevant.
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
     * <strong>Structural-test exemption (W1).</strong> A STRUCTURAL-SHAPED binding need not resolve: the seeder injects {@code testClass[X]} tests AFTER the agent submits and only
     * for a class absent from the template, so a binding the agent wrote up front for a class the seeder declined to enforce would otherwise force a wasted retry. This is sound —
     * such
     * a binding backs only machinery, and the differential is keyed to REAL test names regardless of binding shape (a real test named {@code testClass[Evil]} still must fail on
     * the
     * template). When the authoritative seeded set is supplied, a binding in it is also treated as resolved.
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
                // Unresolved and not structural-shaped machinery: a genuinely dangling binding the agent must fix.
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
                // A name that resolves to a real solution test but is not in the template's failed set passed on the template.
                if (solutionPassing.contains(normalized) && !templateFailed.contains(normalized) && !offending.contains(name)) {
                    offending.add(name);
                }
            }
        }
        return offending;
    }

    /**
     * The non-behavioural build/compile/configure gate test-case names that legitimately PASS on both the solution and the template (the C++ Catch2/CMake {@code TestConfigure} /
     * {@code Compile<Target>}, the C {@code Compile}/{@code TestCompile}, a generic {@code Configure}/{@code Build}). They assert only "does it compile/configure", which the
     * same-signature placeholder template satisfies by design, so the production-parity gate exempts them while every behaviour test must still fail on the template.
     */
    private static final Set<String> BUILD_GATE_EXACT_NAMES = Set.of("testconfigure", "configure", "compile", "testcompile", "build", "testbuild", "cmake");

    /** Prefixes of a per-target build gate ({@code CompileSort}, {@code ConfigureDebug}, {@code BuildTests}). Case-insensitive. */
    private static final List<String> BUILD_GATE_PREFIXES = List.of("compile", "configure", "build");

    /** Whether a name is a build/compile/configure gate exempt from the production-parity gate (see {@link #BUILD_GATE_EXACT_NAMES}). */
    private static boolean isBuildGateTest(String normalizedName) {
        // Also check the last dot-segment: the real C++ harness reports build gates with the framework suite prefix (GBS-Tester-1.36.TestConfigure), so the gate word is the final
        // segment, not the start of the whole name.
        if (matchesBuildGateToken(normalizedName)) {
            return true;
        }
        int lastDot = normalizedName.lastIndexOf('.');
        return lastDot >= 0 && lastDot < normalizedName.length() - 1 && matchesBuildGateToken(normalizedName.substring(lastDot + 1));
    }

    /** Whether a token is exactly a build-gate word, or a {@code GateWord<UpperCaseTarget>} form (e.g. {@code CompileSort}). */
    private static boolean matchesBuildGateToken(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (BUILD_GATE_EXACT_NAMES.contains(lower)) {
            return true;
        }
        for (String prefix : BUILD_GATE_PREFIXES) {
            // Require an uppercase target after the gate word so a behaviour test like "compiles_an_empty_program" is never a false positive.
            if (lower.startsWith(prefix) && token.length() > prefix.length() && Character.isUpperCase(token.charAt(prefix.length()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every solution-passing test (the whole suite) that is NOT in the template's failed/errored set, EXCLUDING the build gates ({@link #isBuildGateTest}). This mirrors how
     * production grades (every discovered test, not only the {@code [task]}-bound subset), catching an unbound test or too-lucky placeholder the {@code [task]}-only gate misses.
     * Fails
     * open under the same guards as {@link #taskBoundTestsThatPassOnTemplate}.
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
     * The solution-build SCA findings production WOULD penalise (a graded, positively-penalised category), rendered as {@code <TOOL>|<category>} for the rejection message; empty
     * when SCA cannot dock the solution. Reads the persisted categories the same way production grading does ({@code findByExerciseId}), so the decision matches
     * {@code calculateTotalPenalty}; fails open when the repository is absent.
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
     * The aggregated test outcome of one {@code verify.sh} run, built by parsing the collected report files with the SAME production parsers the real LocalCI pipeline uses
     * ({@link TestResultXmlParser} for JUnit, {@link ReportParser} for SCA), so the oracle's view is parity-by-construction with grading.
     *
     * @param tests           tests that ran (zero when the build did not reach the runner, e.g. a compile error); excludes {@code <skipped>} cases, exactly as production grades
     * @param testNames       distinct test-case names from the JUnit XML, composed exactly as production does (validate [task] bindings); empty if none collected
     * @param testFailedNames distinct names of cases that FAILED/ERRORED; used by the strict per-test gate; empty if none collected
     * @param scaFindings     SCA findings (tool + real derived category from {@link ReportParser}); populated only when the SCA reports were collected; empty otherwise
     */
    record BuildSummary(int tests, int failures, int errors, int exitCode, boolean timedOut, List<String> testNames, List<String> testFailedNames,
            List<ScaPenaltyParity.ScaFinding> scaFindings) {

        /** The build was killed for exceeding its timeout; no reports could be collected, so the oracle treats it as a failed build with no tests. */
        static BuildSummary timedOut(int exitCode) {
            return new BuildSummary(0, 0, 0, exitCode, true, List.of(), List.of(), List.of());
        }

        /**
         * The reports archive could not be read or was rejected (a linked/escaping/oversize entry): treat as a non-compiling build (no tests) so the oracle rejects, fail-closed.
         */
        static BuildSummary unparseable(int exitCode) {
            return new BuildSummary(0, 0, 0, exitCode == 0 ? 1 : exitCode, false, List.of(), List.of(), List.of());
        }

        /**
         * Builds the summary from the collected report files (flat name -> bytes): every file whose canonical token is the JUnit token is parsed by {@link TestResultXmlParser}
         * (failed + successful test jobs), every file whose name is a recognised SCA report is parsed by {@link ReportParser} (real derived categories). A report that fails to
         * parse is skipped (fail-open per file), so a single malformed report cannot crash the verdict; the differential and integrity gates handle the resulting summary.
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
         * Parses one collected SCA report with the production {@link ReportParser}, routed by its canonical report file name so {@code ParserPolicy} selects the right parser, and
         * appends each issue's {@code (tool, real derived category)} to {@code scaFindings}. An unsupported tool ({@link UnsupportedToolException}) or a malformed report is
         * skipped
         * (fail-open), so a non-SCA file that happened to be collected, or a tool the parser cannot handle, cannot break the verdict.
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
                // Fail-open: a tool the production parser does not support yields no SCA signal rather than a crash.
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
