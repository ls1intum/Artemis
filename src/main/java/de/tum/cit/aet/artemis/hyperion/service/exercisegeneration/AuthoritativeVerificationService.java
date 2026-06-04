package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;

/**
 * Decides whether a generated exercise is correct, independently of what the agent reports, by running the {@code verify.sh} recipe in the sandbox once against the solution and
 * once
 * against the template and applying the differential oracle: the solution must compile and pass all tests; the template must compile, run the same tests, and fail them; at least
 * one
 * test must exist. The verdict is read from the machine-readable {@code HYPERION_RESULT} line {@code verify.sh} aggregates from the JUnit XML (not the build log), making it
 * build-tool-agnostic and able to distinguish <em>compiled-but-failed</em> (a valid template), <em>did-not-compile</em>, and <em>fewer-tests-ran</em> (a tampered set) on a
 * non-zero
 * exit.
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

    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    private final SandboxBuildCommandService buildCommandFactory;

    /**
     * Persisted SCA categories read the SAME way production grading does ({@code findByExerciseId}), so the SCA-parity gate decides from the authoritative category state rather
     * than
     * the in-memory exercise's detached, lazily-loaded collection. Optional because SCA categories live in the core profile; absent on a node that cannot grade anyway, where the
     * gate then fails open.
     */
    private final Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository;

    /** Supplies the per-run anti-forgery nonce; production uses a fresh unguessable token, tests a fixed value matching their scripted markers. */
    private final Supplier<String> nonceSupplier;

    // @Autowired disambiguates this from the package-private test constructor; with two constructors and no annotation Spring fails to instantiate the bean.
    @Autowired
    public AuthoritativeVerificationService(SandboxBuildCommandService buildCommandFactory, Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this(buildCommandFactory, AuthoritativeVerificationService::randomNonce, staticCodeAnalysisCategoryRepository);
    }

    AuthoritativeVerificationService(SandboxBuildCommandService buildCommandFactory, Supplier<String> nonceSupplier) {
        this(buildCommandFactory, nonceSupplier, Optional.empty());
    }

    AuthoritativeVerificationService(SandboxBuildCommandService buildCommandFactory, Supplier<String> nonceSupplier,
            Optional<StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this.buildCommandFactory = buildCommandFactory;
        this.nonceSupplier = nonceSupplier;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
    }

    /** A fresh 128-bit hex nonce, unguessable so the agent's pre-written test code cannot embed it to forge a marker. */
    private static String randomNonce() {
        byte[] bytes = new byte[16];
        NONCE_RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return "HN" + hex;
    }

    private String readProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        return result.isSuccess() ? result.stdout() : "";
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
        List<String> reasons = new ArrayList<>();

        // Re-seed and invoke a PRISTINE verify.sh the agent could never have written, so any edit to its own /workspace/verify.sh is irrelevant to the verdict.
        seedPristineVerifyScript(sandbox, sessionId, exercise);
        // Anti-forgery: the pristine script stamps this fresh per-run nonce onto every HYPERION_* marker, and the parser honors only nonce-bearing lines, so a marker the agent's
        // test code printed to stdout cannot forge a verdict.
        String nonce = nonceSupplier.get();
        SandboxExecResult solutionRun = sandbox.exec(sessionId, VERIFY_TIMEOUT, "sh", "-c", buildCommandFactory.pristineSolutionBuildCommand(nonce));
        BuildSummary solution = BuildSummary.parse(solutionRun, nonce);
        SandboxExecResult templateRun = sandbox.exec(sessionId, VERIFY_TIMEOUT, "sh", "-c", buildCommandFactory.pristineTemplateBuildCommand(nonce));
        BuildSummary template = BuildSummary.parse(templateRun, nonce);

        // The solution must compile, run at least one test, and have every test pass.
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

        // The template must compile and run the SAME tests as the solution but fail at least half of them (a near-complete template that fails only one of many is not a real
        // starting point, and tests()==0 means it did not compile).
        int testCount = solution.tests();
        int templateFailing = template.failures() + template.errors();
        int requiredTemplateFailures = Math.max(1, testCount / 2);
        boolean templateCompiledAndRan = !template.timedOut() && template.tests() > 0;
        boolean templateFailed = false;
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
            templateFailed = true;
        }

        // Emitter soundness, fail-CLOSED: the pristine verify.sh always emits a TESTNAME per <testcase> and a TESTFAIL per failing one, so a missing/short set is evidence of a
        // broken or forged emitter that would silently disable the binding-resolution and per-test gates below. We REJECT rather than skip.
        boolean solutionNamesComplete = solution.tests() > 0 && solution.testNames().size() >= solution.tests();
        if (solution.tests() > 0 && !solutionNamesComplete) {
            reasons.add("The solution ran " + solution.tests() + " tests but the verifier only recorded " + solution.testNames().size()
                    + " test name(s). The per-test name list must be complete for the grading checks to run; an incomplete list means the test reports could not be parsed "
                    + "reliably (or the build emitted no per-test results), so the exercise cannot be verified. Ensure every test writes a JUnit <testcase> entry.");
        }
        boolean templateFailNamesSound = !(templateCompiledAndRan && templateFailing > 0 && template.testFailedNames().isEmpty());
        if (!templateFailNamesSound) {
            reasons.add("The template reported " + templateFailing
                    + " failing/erroring test(s) but the verifier could not record WHICH tests failed (no per-test failure entries). "
                    + "Without the failing-test names the verifier cannot confirm that every graded test fails on the template, so the exercise cannot be verified. Ensure the test "
                    + "reports record each failing <testcase> with its <failure>/<error>.");
        }

        // The exercise must bind its tests to the problem statement via [task][title](testNames); without this the student sees no task checklist (a Maven project, not an Artemis
        // exercise).
        String problemStatement = readProblemStatement(sandbox, sessionId);
        boolean problemStatementHasTasks = TASK_BINDING.matcher(problemStatement).find();
        if (!problemStatementHasTasks) {
            reasons.add("The problem statement has no Artemis task bindings. Add at least one line of the form [task][Title](testName) binding the graded tests to tasks so they "
                    + "appear as a checklist for students.");
        }

        // A [task]'s names must be the real runner test names, not a @DisplayName or prose title; a binding that resolves to nothing silently shows no progress in Artemis, which
        // the
        // differential build cannot detect. Checked against the names verify.sh extracted; fails open when no trustworthy set was emitted.
        List<String> unresolvedTaskBindings = unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        boolean taskBindingsResolve = unresolvedTaskBindings.isEmpty();
        if (problemStatementHasTasks && !taskBindingsResolve) {
            reasons.add("These [task] bindings reference names that match no actual test: " + unresolvedTaskBindings + ". A [task]'s parenthesised names must be the exact test "
                    + "method/function names (e.g. testSortsAscending), not a @DisplayName or a prose title — otherwise the task shows no result in Artemis. The actual test names are: "
                    + solution.testNames() + ". Fix the [task] lines (or rename the tests) so every binding references a real test name.");
        }

        // Strict per-test differential: every [task]-bound test the SOLUTION passes must FAIL on the TEMPLATE. A graded test the template already satisfies (a `return 0` stub
        // passing
        // `fibonacci(0)==0`) hands the student a free point even if the count gate passed. Fails open when the name/fail sets are untrustworthy.
        List<String> taskTestsPassingOnTemplate = taskBoundTestsThatPassOnTemplate(problemStatement, solution, template);
        boolean noTaskTestPassesTemplate = taskTestsPassingOnTemplate.isEmpty();
        if (problemStatementHasTasks && taskBindingsResolve && !noTaskTestPassesTemplate) {
            reasons.add("These [task]-bound tests PASS on the template, which gives the student free points before doing any work: " + taskTestsPassingOnTemplate
                    + ". Every graded ([task]-bound) test must FAIL on the template — the template's placeholder bodies must not accidentally satisfy a graded assertion (e.g. a "
                    + "`return 0` stub must not pass an `expected == 0` test). Strip the template so every graded test fails, or make the test assert behaviour the placeholder cannot meet.");
        }

        // Production grades EVERY discovered test, not only the [task]-bound subset, so an unbound test that passes on the template still gives a bare-template student >0%
        // (observed:
        // a Python template at 22.2% from one unbound test). We require every solution-passing test to FAIL on the template, except the build/compile/configure gates
        // (isBuildGateTest)
        // that legitimately pass on both because the same-signature template compiles by design. Fails open under the same guards as the [task] gate.
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

        // SCA-parity gate: SCA reports carry no <testcase>, so the differential oracle is blind to them while production folds a penalty into the score. verify.sh emits a
        // HYPERION_SCA line per finding and ScaPenaltyParity flags those production would actually penalise (graded, positively-penalised category, matched to the persisted
        // categories); silent and verdict-unchanged when none would dock.
        List<String> penalisingScaFindings = penalisingScaFindings(exercise, solution);
        boolean solutionScaClean = penalisingScaFindings.isEmpty();
        if (!solutionScaClean) {
            reasons.add("The reference solution produces static-code-analysis findings that production would penalise (graded SCA categories): " + penalisingScaFindings
                    + ". With static code analysis enabled and a graded penalty, a student's score is docked for these — so the reference solution, which must grade 100%, would not. "
                    + "Make the reference solution clean of these graded SCA findings (fix the flagged code, or it must not trip the graded categories) before the exercise can ship.");
        }

        // Sandbox-free integrity gates the build cannot see: a tampered graded-verbatim harness, or a solution leaked into the student-facing template. Both fail OPEN on genuinely
        // empty inputs.
        List<String> harnessTamperingReasons = ExerciseIntegrityGate.harnessTamperingReasons(seedTestsFiles, producedTestsFiles);
        boolean harnessIntact = harnessTamperingReasons.isEmpty();
        reasons.addAll(harnessTamperingReasons);
        List<String> solutionLeakReasons = ExerciseIntegrityGate.solutionLeakReasons(producedTemplateFiles, producedSolutionFiles);
        boolean noSolutionLeak = solutionLeakReasons.isEmpty();
        reasons.addAll(solutionLeakReasons);

        // Fail-CLOSED on a read-back gap: a repo seeded non-empty but extracted empty silently disables the harness/leak gates, so we reject rather than accept on that doubt. (A
        // genuinely empty repo is reported as NOT failed and stays fail-open.)
        boolean extractionSound = extractionFailedRepositories == null || extractionFailedRepositories.isEmpty();
        if (!extractionSound) {
            reasons.add("The generated files for these repositories could not be read back for verification: " + extractionFailedRepositories
                    + ". The integrity checks (harness immutability, solution leak) cannot run on missing files, so the exercise cannot be verified. This is usually a transient "
                    + "read-back error; retry the generation.");
        }

        boolean accepted = solutionPassed && templateFailed && testCount > 0 && solutionNamesComplete && templateFailNamesSound && problemStatementHasTasks && taskBindingsResolve
                && noTaskTestPassesTemplate && noGradableTestPassesTemplate && solutionScaClean && harnessIntact && noSolutionLeak && extractionSound;
        if (!accepted) {
            log.info(
                    "Authoritative verification failed: solution[{}], template[{}], namesComplete={}, failNamesSound={}, tasks={}, bindingsResolve={}, noTaskTestPassesTemplate={}, "
                            + "noGradableTestPassesTemplate={}, solutionScaClean={}, harnessIntact={}, noSolutionLeak={}, extractionSound={}",
                    solution, template, solutionNamesComplete, templateFailNamesSound, problemStatementHasTasks, taskBindingsResolve, noTaskTestPassesTemplate,
                    noGradableTestPassesTemplate, solutionScaClean, harnessIntact, noSolutionLeak, extractionSound);
        }
        return new VerificationResult(accepted, solutionPassed, templateFailed, testCount, reasons);
    }

    /**
     * Renders a fresh pristine {@code verify.sh} into the verifier-owned directory OUTSIDE {@code /workspace} ({@link SandboxBuildCommandService#PRISTINE_VERIFY_PATH}), which the
     * agent tools (which only resolve under {@code /workspace}) could never have reached — so an agent edit to its own copy is irrelevant.
     */
    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        String script = buildCommandFactory.verifyScriptContent(exercise);
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
     * The solution-build SCA findings production WOULD penalise (a graded, positively-penalised category), empty when SCA cannot dock the solution. Reads the persisted categories
     * the same way production grading does ({@code findByExerciseId}), so the decision matches {@code calculateTotalPenalty}; fails open when the repository is absent.
     */
    private List<String> penalisingScaFindings(ProgrammingExercise exercise, BuildSummary solution) {
        // Short-circuit before the DB read on the common non-SCA path.
        if (solution.scaFindings().isEmpty() || !Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) || exercise.getId() == null
                || staticCodeAnalysisCategoryRepository.isEmpty()) {
            return List.of();
        }
        var categories = staticCodeAnalysisCategoryRepository.get().findByExerciseId(exercise.getId());
        return ScaPenaltyParity.penalisingFindings(exercise, categories, solution.scaFindings());
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
     * The aggregated test outcome of one {@code verify.sh} run, read from its nonce-stamped {@code HYPERION_*} marker lines.
     *
     * @param tests           tests that ran (zero when the build did not reach the runner, e.g. a compile error)
     * @param testNames       distinct test-case names extracted from the JUnit XML (validate [task] bindings); empty if none emitted
     * @param testFailedNames distinct names of cases that FAILED/ERRORED ({@code HYPERION_TESTFAIL}); used by the strict per-test gate; empty if none emitted
     * @param scaFindings     SCA findings ({@code HYPERION_SCA}) as {@code <TOOL>|<rawCategory>}; populated only when SCA is enabled; empty if none emitted
     */
    record BuildSummary(int tests, int failures, int errors, int exitCode, boolean timedOut, List<String> testNames, List<String> testFailedNames, List<String> scaFindings) {

        static BuildSummary parse(SandboxExecResult result, String nonce) {
            if (result.timedOut()) {
                return new BuildSummary(0, 0, 0, result.exitCode(), true, List.of(), List.of(), List.of());
            }
            // Each marker is anchored to the per-run nonce, so a HYPERION_* line the agent's test code printed to stdout (which cannot carry the freshly-generated token) is
            // ignored.
            String quotedNonce = Pattern.quote(nonce);
            Pattern marker = Pattern.compile(
                    SandboxBuildCommandService.RESULT_MARKER + "\\s+" + quotedNonce + "\\s+tests=(\\d+)\\s+failures=(\\d+)\\s+errors=(\\d+)\\s+skipped=(\\d+)\\s+exit=(-?\\d+)");
            Pattern testName = Pattern.compile(SandboxBuildCommandService.TESTNAME_MARKER + "\\s+" + quotedNonce + " (.+)");
            Pattern testFail = Pattern.compile(SandboxBuildCommandService.TESTFAIL_MARKER + "\\s+" + quotedNonce + " (.+)");
            Pattern scaFinding = Pattern.compile(SandboxBuildCommandService.SCA_MARKER + "\\s+" + quotedNonce + " (.+)");
            Matcher matcher = marker.matcher(result.combinedOutput());
            // The summary is printed once at the very end; the last line wins if it somehow ran more than once.
            int tests = 0;
            int failures = 0;
            int errors = 0;
            int exitCode = result.exitCode();
            boolean found = false;
            while (matcher.find()) {
                found = true;
                tests = Integer.parseInt(matcher.group(1));
                failures = Integer.parseInt(matcher.group(2));
                errors = Integer.parseInt(matcher.group(3));
                exitCode = Integer.parseInt(matcher.group(5));
            }
            if (!found) {
                // No nonce-stamped summary: killed, mis-seeded, or every result line was an unsigned forgery. Treat as a failed build with no tests so the oracle rejects.
                return new BuildSummary(0, 0, 0, result.exitCode() == 0 ? 1 : result.exitCode(), false, List.of(), List.of(), List.of());
            }
            return new BuildSummary(tests, failures, errors, exitCode, false, parseNames(result.combinedOutput(), testName), parseNames(result.combinedOutput(), testFail),
                    parseNames(result.combinedOutput(), scaFinding));
        }

        private static List<String> parseNames(String output, Pattern marker) {
            Set<String> names = new LinkedHashSet<>();
            Matcher matcher = marker.matcher(output);
            while (matcher.find()) {
                names.add(matcher.group(1).trim());
            }
            return List.copyOf(names);
        }
    }
}
