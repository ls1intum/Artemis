package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Decides whether a generated exercise is correct, independently of what the agent reports, by running the {@code verify.sh} build recipe in the sandbox once with the solution
 * as the assignment and once with the template, and applying the differential oracle: the solution must compile and pass all tests; the template must compile and run the same
 * tests but fail them; and at least one test must exist.
 * <p>
 * The verdict is read from the machine-readable {@code HYPERION_RESULT} line that {@code verify.sh} aggregates from the JUnit XML reports, not from scraping the build log. This
 * makes the verdict build-tool-agnostic and lets the oracle distinguish three states that look alike on a non-zero exit: <em>compiled, tests failed</em> (a valid template),
 * <em>did not compile</em> (no reports, rejected), and <em>fewer tests ran than for the solution</em> (a divergent or tampered test set, rejected).
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class AuthoritativeVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AuthoritativeVerificationService.class);

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Matches an Artemis problem-statement task binding and captures its parenthesised, comma-separated test names: {@code [task][Some title](testA,testB)}.
     * <p>
     * The captured group is greedy ({@code (.*)} up to the LAST {@code )} on the line, since {@code .} does not cross newlines) rather than {@code [^)]*}, because a test
     * identifier
     * can itself contain parentheses — a JVM/Ares method name is reported as {@code testSortsAscending()}, a Rust test as {@code test_sorts()}. With the old {@code [^)]*} capture
     * a
     * correctly-written {@code (testSortsAscending())} binding was truncated to {@code testSortsAscending(} at the first {@code )}, which then resolved against no real test and
     * the
     * verifier wrongly rejected the exercise — even though the agent had copied the verbatim {@code HYPERION_TESTNAME} name exactly as instructed. {@link #normalizeTestName}
     * strips a
     * trailing {@code ()} so both the paren and no-paren binding forms resolve to the same test.
     */
    private static final Pattern TASK_BINDING = Pattern.compile("\\[task\\]\\[[^\\]]*\\]\\((.*)\\)");

    /**
     * Matches the shape of an Ares auto-generated structural test-case name: {@code testClass[X]}, {@code testMethods[X]}, {@code testAttributes[X]}, {@code testConstructors[X]}
     * (case-sensitive; this is the exact form {@code *TestProvider} reports its {@link org.junit.jupiter.api.DynamicTest}s as). Used ONLY to relax the binding-RESOLUTION gate: a
     * {@code [task]} bound to a structural-shaped name is not required to resolve to a real test, because the structural tests are MACHINERY the structural-oracle seeder injects
     * AFTER the agent submits (so the agent cannot know which classes the conservative seeder will ultimately enforce, and a structural binding that resolves to nothing is a
     * harmless cosmetic dead task — no real test, no grade). It is NEVER used to weaken the differential: every real test (whatever its name shape) must still pass on the solution
     * and fail on the template via the gradable/per-test gates, so an agent that names a real behaviour test {@code testClass[Evil]} gains no evasion (the test stays in the
     * differential and must fail on the template).
     */
    private static final Pattern STRUCTURAL_TEST_NAME = Pattern.compile("test(?:Class|Methods|Attributes|Constructors)\\[[^\\]]+\\]");

    /** Default permissions for the pristine {@code verify.sh}: world-readable, owner-executable (it is invoked with {@code sh}, but mark it executable for hygiene). */
    private static final int PRISTINE_SCRIPT_MODE = 0755;

    /** Cryptographically-strong source for the per-run anti-forgery marker nonce. */
    private static final java.security.SecureRandom NONCE_RANDOM = new java.security.SecureRandom();

    private final SandboxBuildCommandService buildCommandFactory;

    /**
     * Reads the exercise's persisted SCA categories the SAME way production grading does ({@code findByExerciseId}), so the SCA-parity gate decides "would production penalise this
     * solution?" from the authoritative category state — not from the in-memory exercise's lazily-loaded (and at verify time unloaded / detached)
     * {@code staticCodeAnalysisCategories}
     * collection. Optional because SCA categories live in the core profile; on a node without it (which cannot run grading anyway) the gate fails open (no SCA rejection).
     */
    private final java.util.Optional<de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository;

    /**
     * Supplies the per-run anti-forgery nonce stamped onto the pristine script's {@code HYPERION_*} markers. Production uses a fresh, unguessable token per run; tests override it
     * with a fixed value so the scripted sandbox can emit markers bearing the same nonce the verifier expects.
     */
    private final java.util.function.Supplier<String> nonceSupplier;

    // @Autowired disambiguates which constructor Spring uses: this class has a second, package-private constructor that takes a fixed nonce supplier for tests, and with two
    // constructors and no annotation Spring fails to instantiate the bean ("No default constructor found"). The production constructor uses a fresh, unguessable per-run nonce.
    @org.springframework.beans.factory.annotation.Autowired
    public AuthoritativeVerificationService(SandboxBuildCommandService buildCommandFactory,
            java.util.Optional<de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this(buildCommandFactory, AuthoritativeVerificationService::randomNonce, staticCodeAnalysisCategoryRepository);
    }

    AuthoritativeVerificationService(SandboxBuildCommandService buildCommandFactory, java.util.function.Supplier<String> nonceSupplier) {
        this(buildCommandFactory, nonceSupplier, java.util.Optional.empty());
    }

    AuthoritativeVerificationService(SandboxBuildCommandService buildCommandFactory, java.util.function.Supplier<String> nonceSupplier,
            java.util.Optional<de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository> staticCodeAnalysisCategoryRepository) {
        this.buildCommandFactory = buildCommandFactory;
        this.nonceSupplier = nonceSupplier;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
    }

    /** A fresh 128-bit hex nonce; unguessable, so the agent's pre-written test code cannot embed it to forge a marker line. */
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

    /**
     * Runs the differential verification by re-seeding a pristine {@code verify.sh} and executing it in the sandbox.
     *
     * @param sandbox   the sandbox session to run the builds in
     * @param sessionId the session handle
     * @param exercise  the exercise whose per-language {@code verify.sh} is freshly rendered and run (the script the agent never touched)
     * @return the verdict and, on rejection, the reasons
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        return verify(sandbox, sessionId, exercise, Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }

    /**
     * Backwards-compatible overload without the auto-seeded structural test names: behaves exactly as the 9-arg form with an empty seeded set (so a structural-shaped binding is
     * still exempted from RESOLUTION by its name shape, just not additionally cross-checked against an authoritative seeded set).
     *
     * @param sandbox                      the sandbox session to run the builds in
     * @param sessionId                    the session handle
     * @param exercise                     the exercise whose per-language {@code verify.sh} is freshly rendered and run
     * @param seedTestsFiles               the tests-repo files snapshotted at seed time (repository-relative)
     * @param producedTestsFiles           the tests-repo files read back after generation (repository-relative)
     * @param producedTemplateFiles        the template-repo files read back after generation (repository-relative)
     * @param producedSolutionFiles        the solution-repo files read back after generation (repository-relative)
     * @param extractionFailedRepositories the repository directory names whose read-back FAILED (seeded non-empty but extracted empty)
     * @return the verdict and, on rejection, the reasons
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles,
            Set<String> extractionFailedRepositories) {
        return verify(sandbox, sessionId, exercise, seedTestsFiles, producedTestsFiles, producedTemplateFiles, producedSolutionFiles, extractionFailedRepositories, Set.of());
    }

    /**
     * Runs the differential verification AND the sandbox-free integrity gates (harness immutability and solution-leak). The build-oracle reasons and the integrity-gate reasons are
     * combined; the exercise is accepted only when both pass.
     * <p>
     * <strong>Non-forgeable verdict.</strong> The agent's {@code /workspace/verify.sh} is agent-writable, and the agent could overwrite it to echo a perfect marker set with no
     * real
     * tests. To make the verdict trustworthy the verifier RE-SEEDS a freshly-rendered, pristine {@code verify.sh} to a verifier-owned path OUTSIDE {@code /workspace}
     * ({@link SandboxBuildCommandService#PRISTINE_VERIFY_PATH}) that the agent tools cannot reach, and runs THAT copy — never the agent's. The pristine script reads the repository
     * sources from {@code /workspace/{tests,solution,template}} (those ARE the agent's work, by design) but additionally deletes any pre-existing report XML and counts only
     * reports
     * written during the build, so a planted report cannot be summed either.
     * <p>
     * The integrity gates fail OPEN when their inputs are GENUINELY empty (a legitimately empty repo), but fail CLOSED when a repo that was seeded non-empty extracts empty at
     * verify
     * time (signalled via {@code extractionFailedRepositories}) — a flaky read-back must not silently disable a correctness gate.
     *
     * @param sandbox                      the sandbox session to run the builds in
     * @param sessionId                    the session handle
     * @param exercise                     the exercise whose per-language {@code verify.sh} is freshly rendered, placed outside {@code /workspace}, and run
     * @param seedTestsFiles               the tests-repo files snapshotted at seed time (repository-relative); enables the harness-immutability gate
     * @param producedTestsFiles           the tests-repo files read back after generation (repository-relative)
     * @param producedTemplateFiles        the template-repo files read back after generation (repository-relative); enables the solution-leak gate
     * @param producedSolutionFiles        the solution-repo files read back after generation (repository-relative)
     * @param extractionFailedRepositories the repository directory names whose read-back FAILED (seeded non-empty but extracted empty); fail-closed signal distinct from a
     *                                         genuinely
     *                                         empty repo
     * @param seededStructuralTestNames    the AUTHORITATIVE structural test-case names the {@link StructuralOracleSeedingService} injected this run (e.g.
     *                                         {@code testClass[Sorter]}),
     *                                         computed from the seeder's own oracle — never from agent-supplied names. A {@code [task]} bound to one of these is exempt from the
     *                                         binding-RESOLUTION gate (the agent could not have known the conservative seeder would enforce exactly these), while every such test
     *                                         still participates in the pass-on-solution / fail-on-template differential. Empty for callers without it (e.g. the from-scratch path
     *                                         falls back to the name-shape exemption, which is itself sound)
     * @return the verdict and, on rejection, the reasons
     */
    public VerificationResult verify(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories,
            Set<String> seededStructuralTestNames) {
        List<String> reasons = new ArrayList<>();

        // Re-seed a PRISTINE verify.sh the verifier controls, at a path the agent could never have written, immediately before this verification run, then invoke THAT copy. Any
        // edit the agent made to its own /workspace/verify.sh is therefore irrelevant to the verdict.
        seedPristineVerifyScript(sandbox, sessionId, exercise);
        // Fresh, unguessable per-run nonce. The pristine verify.sh stamps it onto every HYPERION_* marker; the parser accepts only marker lines bearing this exact nonce, so a
        // HYPERION_TESTFAIL/HYPERION_RESULT line the agent's test code prints to stdout (which is interleaved with the phase output the verifier scrapes, but cannot carry this
        // token) is ignored rather than honored as a forged verdict.
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

        // The template must compile and run the SAME tests as the solution, but fail a meaningful fraction of them. tests()==0 means it did not compile (no reports were written),
        // which is not a valid exercise; a non-zero exit with failures/errors means it compiled and the tests correctly reject the unfinished template. A template that fails only
        // one of many tests is nearly complete and not a real starting point, so we require it to fail at least half — which also rules out a single trivial discriminating test.
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
            // A smaller count means a class present in the solution but missing from the template silently dropped tests (or the suite branches on what it should grade), which
            // would let a vacuous template "fail" without the tests actually discriminating.
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
            // The template compiled, ran the SAME tests as the solution, and failed at least the required fraction of them — that IS a correctly-failing template. Trust the JUnit
            // failure/error counts, NOT the build's exit code: several languages pipe the test run through a report converter (e.g. Go's `go test … | go-junit-report`, Dart's
            // `dart test | tojunit`) whose exit code is 0 even when tests failed, so an exit-code check would wrongly reject a perfectly valid template.
            templateFailed = true;
        }

        // Fail-CLOSED soundness of the emitter (Defect B). The verifier runs its OWN pristine verify.sh, which always emits a HYPERION_TESTNAME line per <testcase> and a
        // HYPERION_TESTFAIL line per failing one. So a name/fail set that is missing or short is no longer an "older script / extraction gap" we can shrug off — it is evidence the
        // emitter was broken or forged, and the binding-resolution and per-test-soundness gates below would silently NOT run. We therefore REJECT rather than skip:
        // (a) the solution ran N>0 tests but fewer than N TESTNAME lines were emitted, and
        // (b) the template reported failures/errors but emitted ZERO TESTFAIL names (we cannot confirm WHICH tests failed, so we cannot prove the bound tests fail on the
        // template).
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

        // The exercise must integrate with Artemis grading: the problem statement has to present the tests as gradable tasks via [task][title](testNames). Without this the student
        // sees no task checklist and the tests are not bound to the problem statement, which is the difference between a Maven project and an Artemis exercise.
        String problemStatement = readProblemStatement(sandbox, sessionId);
        boolean problemStatementHasTasks = TASK_BINDING.matcher(problemStatement).find();
        if (!problemStatementHasTasks) {
            reasons.add("The problem statement has no Artemis task bindings. Add at least one line of the form [task][Title](testName) binding the graded tests to tasks so they "
                    + "appear as a checklist for students.");
        }

        // A [task]'s parenthesised names must be the actual test names Artemis matches results by — the JUnit method name / pytest function name reported by the runner, NOT a
        // @DisplayName or a prose title. A binding that resolves to no real test silently shows no progress in Artemis, which the differential build cannot detect, so check it
        // here
        // against the real test names verify.sh extracted from the JUnit XML. Fails open (skips the check) when no trustworthy name set was emitted, so it never rejects
        // spuriously.
        List<String> unresolvedTaskBindings = unresolvedTaskBindings(problemStatement, solution.testNames(), testCount, seededStructuralTestNames);
        boolean taskBindingsResolve = unresolvedTaskBindings.isEmpty();
        if (problemStatementHasTasks && !taskBindingsResolve) {
            reasons.add("These [task] bindings reference names that match no actual test: " + unresolvedTaskBindings + ". A [task]'s parenthesised names must be the exact test "
                    + "method/function names (e.g. testSortsAscending), not a @DisplayName or a prose title — otherwise the task shows no result in Artemis. The actual test names are: "
                    + solution.testNames() + ". Fix the [task] lines (or rename the tests) so every binding references a real test name.");
        }

        // Per-test soundness gate (the strict differential): every [task]-bound test that the SOLUTION passes must FAIL (or error) on the TEMPLATE. A graded test the template
        // already satisfies (e.g. a `return 0` fibonacci stub passing `fibonacci(0)==0`, or a `pop()->undefined` stub passing `pop_on_empty_returns_undefined`) hands the student a
        // free point and is a false grading signal, even though the aggregate count gate ("fail at least half") might pass. We match the [task] binding names against the names
        // verify.sh reported the template FAILED (HYPERION_TESTFAIL); a resolved, solution-passing binding that is NOT among them passed on the template and is rejected. Only
        // [task]-bound tests are checked, so non-gradable harness testcases (a C++ compile/configure case, structural probes that are not bound) may legitimately pass on both.
        // Fails OPEN: skipped entirely when the name/fail sets are untrustworthy (no HYPERION_TESTFAIL emitted, or the solution name set is missing/short), so an extraction gap
        // can
        // never cause a spurious rejection of a valid exercise.
        List<String> taskTestsPassingOnTemplate = taskBoundTestsThatPassOnTemplate(problemStatement, solution, template);
        boolean noTaskTestPassesTemplate = taskTestsPassingOnTemplate.isEmpty();
        if (problemStatementHasTasks && taskBindingsResolve && !noTaskTestPassesTemplate) {
            reasons.add("These [task]-bound tests PASS on the template, which gives the student free points before doing any work: " + taskTestsPassingOnTemplate
                    + ". Every graded ([task]-bound) test must FAIL on the template — the template's placeholder bodies must not accidentally satisfy a graded assertion (e.g. a "
                    + "`return 0` stub must not pass an `expected == 0` test). Strip the template so every graded test fails, or make the test assert behaviour the placeholder cannot meet.");
        }

        // Production-parity gate (the strict differential, matching how production actually grades). Production grades EVERY discovered test case at default weight — NOT only the
        // [task]-bound subset — so a bare-template student scores >0% if ANY test the solution passes also passes on the template, even a test the agent wrote but forgot to bind
        // to a
        // [task] (observed: a Python from-scratch template at 22.2% from one unbound behaviour test). The [task]-only gate above cannot see that, so we additionally require EVERY
        // solution-passing test (the whole suite — the solution passes them all, so solution.testNames() IS the solution-passing set) to FAIL on the template, EXCEPT the small
        // allowlist of non-behavioural build/compile/configure gate tests (see isBuildGateTest): the C++ Catch2 harness emits a `TestConfigure` and per-target `Compile<Target>`
        // case, the C harness a `Compile`/`TestCompile` case; these assert only "does it compile/configure", which BOTH the solution and the template satisfy because the template
        // compiles by design (that is the whole point of a same-signature placeholder template). They carry no exercise behaviour, so a student cannot earn a behaviour point from
        // them; they are the framework's build gates, present verbatim in the canonical harness, and legitimately pass on both. This is NOT over-rejection: after the orphan fix a
        // same-signature template seeds only behaviour tests (no structural-existence tests — those are absent when signatures match), and every behaviour test MUST fail on the
        // template. Fails OPEN under the same untrustworthy-emission guards as the [task] gate so an extraction gap never spuriously rejects.
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

        // Static-code-analysis parity gate (Defect D2). When the exercise has SCA enabled, both verify.sh and production run the same *_static.yaml phases, so the SCA tools
        // execute
        // in BOTH — but their reports carry no JUnit <testcase>, so the differential oracle above is blind to them while production folds a penalty into the score
        // (ProgrammingExerciseGradingService.calculateTotalPenalty). A reference SOLUTION with graded SCA violations would thus be ACCEPTED here yet grade below 100% for a
        // student.
        // verify.sh now emits a HYPERION_SCA <TOOL>|<rawCategory> line per solution-build finding; ScaPenaltyParity decides which of those production would ACTUALLY penalise
        // (SCA enabled AND maxPenalty>0 AND the finding maps to a GRADED, positively-penalised category — matched to the persisted categories EXACTLY as production does). When
        // none
        // do (SCA off, maxPenalty==0, or no graded+positive category — including the default Hyperion categories, all FEEDBACK/INACTIVE), this is silent and the verdict is
        // unchanged. We read the categories from the repository (the authoritative grading view), so the gate does not depend on the in-memory exercise's lazily-loaded collection.
        List<String> penalisingScaFindings = penalisingScaFindings(exercise, solution);
        boolean solutionScaClean = penalisingScaFindings.isEmpty();
        if (!solutionScaClean) {
            reasons.add("The reference solution produces static-code-analysis findings that production would penalise (graded SCA categories): " + penalisingScaFindings
                    + ". With static code analysis enabled and a graded penalty, a student's score is docked for these — so the reference solution, which must grade 100%, would not. "
                    + "Make the reference solution clean of these graded SCA findings (fix the flagged code, or it must not trip the graded categories) before the exercise can ship.");
        }

        // Sandbox-free integrity gates the differential build cannot see: the build can pass while production is broken (a tampered, graded-verbatim harness) or the solution is
        // leaked to students (a template that copies a solution source). Both fail OPEN when their inputs are GENUINELY empty (a legitimately empty repo, or no seed snapshot), so
        // a
        // genuinely-empty state never rejects a valid exercise.
        List<String> harnessTamperingReasons = ExerciseIntegrityGate.harnessTamperingReasons(seedTestsFiles, producedTestsFiles);
        boolean harnessIntact = harnessTamperingReasons.isEmpty();
        reasons.addAll(harnessTamperingReasons);
        List<String> solutionLeakReasons = ExerciseIntegrityGate.solutionLeakReasons(producedTemplateFiles, producedSolutionFiles);
        boolean noSolutionLeak = solutionLeakReasons.isEmpty();
        reasons.addAll(solutionLeakReasons);

        // Fail-CLOSED on a read-back gap (Defect B, rule c): a repository that was seeded NON-empty but came back empty at verify time means an extraction error, NOT a genuinely
        // empty repo — and an empty produced map silently disables the harness/leak gates above. We must not accept on that doubt: reject and surface which repo failed, so a flaky
        // copyOut cannot wave an unverified exercise through. (A genuinely empty repo is reported as NOT failed by the caller and stays fail-open.)
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
     * Renders a fresh, pristine {@code verify.sh} for the exercise and writes it to the verifier-owned directory OUTSIDE {@code /workspace}
     * ({@link SandboxBuildCommandService#PRISTINE_VERIFY_PATH}), so the verifier runs a copy of the script the agent could never have reached through its tools (which only resolve
     * paths under {@code /workspace}). Re-rendered (not copied from {@code /workspace}) so an agent edit to its own copy is irrelevant; placed via the same {@code copyIn} tar
     * mechanism the workspace seeding uses, with the executable bit set.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @param exercise  the exercise whose per-language build phases the pristine script runs
     */
    private void seedPristineVerifyScript(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        String script = buildCommandFactory.verifyScriptContent(exercise);
        // Docker's copy-archive-to-container requires the destination directory to already exist; create it (outside /workspace, so the agent never had it) before placing the
        // script.
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
     * Returns the {@code [task]}-bound names that do not resolve to any real test, so the verifier can reject a problem statement whose grading checklist would bind to nothing.
     * <p>
     * Forward-only (a binding must reference a real test; not every test must be bound, since auto-generated structural tests legitimately are not). <strong>Fails open:</strong>
     * returns empty whenever the extracted name set is missing or smaller than the test count (i.e. {@code verify.sh} could not be trusted to have listed every test), so an
     * extraction gap can never cause a spurious rejection of a valid exercise.
     * <p>
     * <strong>Structural-test exemption (W1).</strong> The Java structural-oracle seeder injects Ares structural tests ({@code testClass[X]} …) into the test repo AFTER the agent
     * has submitted — and the scaffold readme teaches the agent the {@code [task][…](testClass[X],testMethods[X])} convention, so the agent writes such bindings up front for the
     * classes it expects the student to create. The conservative seeder only materialises structural tests for a public class entirely absent from the template, so a binding the
     * agent wrote for a class the seeder ultimately declined to enforce resolves to nothing and would force a wasted retry (the W1 thrash, 58–70 turns on Java). We therefore do
     * NOT
     * require a STRUCTURAL-SHAPED binding name to resolve. This is sound: such a binding backs only machinery — if it resolves to a seeded structural test it is real; if it
     * resolves
     * to nothing it is a harmless cosmetic dead task (no real test, no grade, no leak). It can never let the agent evade the differential, which is keyed to REAL test names
     * regardless of binding shape: a real behaviour test the agent named {@code testClass[Evil]} still must fail on the template (see {@link #gradableTestsThatPassOnTemplate}).
     * When
     * the authoritative seeded set is supplied, a binding in it is additionally treated as resolved even if the post-seed name extraction is momentarily incomplete.
     *
     * @param problemStatement          the produced problem statement
     * @param actualTestNames           the test-case names {@code verify.sh} extracted from the solution build's JUnit XML
     * @param testCount                 the number of tests the solution ran
     * @param seededStructuralTestNames the authoritative structural test names the seeder injected (may be empty; only TIGHTENS the shape exemption, never required for soundness)
     * @return the unresolved binding names (empty when all resolve or the check is skipped)
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
                // Resolves to a real test, or to an authoritatively-seeded structural test name: not unresolved.
                if (actual.contains(normalized) || seededStructural.contains(normalized)) {
                    continue;
                }
                // Unresolved AND not structural-shaped machinery -> a genuinely dangling binding to a real-but-missing behaviour test, which the agent must fix.
                if (!isStructuralTestName(name) && !unresolved.contains(name)) {
                    unresolved.add(name);
                }
            }
        }
        return unresolved;
    }

    /**
     * Whether a {@code [task]} binding name has the Ares structural-test shape ({@code testClass[X]} / {@code testMethods[X]} / {@code testAttributes[X]} /
     * {@code testConstructors[X]}).
     */
    private static boolean isStructuralTestName(String name) {
        return STRUCTURAL_TEST_NAME.matcher(name.trim()).matches();
    }

    /**
     * Returns the {@code [task]}-bound test names that PASS on the template (i.e. the solution reports them as real, passing tests, but the template did NOT report them as failed)
     * — the accidental free-points the strict differential rejects.
     * <p>
     * The rule: a name is offending iff it resolves to a real solution test (is in {@code solution.testNames()}, normalised) AND is NOT in the set the template reported
     * failed/errored
     * ({@code template.testFailedNames()}). <strong>Fails open</strong> (returns empty) whenever the verdict cannot be trusted: when the template emitted no fail lines at all, or
     * the
     * solution name set is missing or smaller than its test count (an older {@code verify.sh} or an extraction gap), so the gate can never spuriously reject a valid exercise.
     * Only resolved names are considered, mirroring the forward-only binding-resolution check: unresolved bindings are handled by {@link #unresolvedTaskBindings} and never reach
     * here.
     *
     * @param problemStatement the produced problem statement (source of the {@code [task]} bindings)
     * @param solution         the solution build summary (its passing test names)
     * @param template         the template build summary (its failed/errored test names)
     * @return the offending {@code [task]} names that pass on the template (empty when none, or when the check is skipped)
     */
    private static List<String> taskBoundTestsThatPassOnTemplate(String problemStatement, BuildSummary solution, BuildSummary template) {
        List<String> solutionNames = solution.testNames();
        // Untrustworthy name set: same fail-open guard as the binding-resolution check.
        if (solutionNames.isEmpty() || solutionNames.size() < solution.tests()) {
            return List.of();
        }
        // The template reported no failing testcase names: either an older verify.sh that did not emit them, or an extraction gap. Do not enforce, to avoid a spurious rejection.
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
                // Only consider names that resolve to a real solution test; one that resolves but is not in the template's failed set passed on the template.
                if (solutionPassing.contains(normalized) && !templateFailed.contains(normalized) && !offending.contains(name)) {
                    offending.add(name);
                }
            }
        }
        return offending;
    }

    /**
     * The non-behavioural build/compile/configure gate test-case names that legitimately PASS on both the solution and the template (so the production-parity gate exempts them).
     * These are the framework's "does it compile / does CMake configure" gates, present verbatim in the canonical harness:
     * <ul>
     * <li><b>C++ (Catch2 / CMake harness).</b> {@code TestConfigure} runs CMake to configure the build; {@code Compile<Target>} (e.g. {@code CompileSort}) compiles a target. Both
     * pass on the template because a same-signature placeholder template compiles by design — that is the whole point of the template. They assert nothing about exercise
     * behaviour.</li>
     * <li><b>C (GCC harness).</b> {@code Compile} / {@code TestCompile} (and sanitizer compile variants {@code CompileA}/{@code CompileU}/{@code CompileL}) likewise only check the
     * code compiles.</li>
     * <li><b>Generic.</b> A {@code Configure} / {@code Build} gate any harness may emit before running behaviour tests.</li>
     * </ul>
     * A student cannot earn a behaviour point from these: they pass purely because the code compiles, which the template does on purpose. Everything else (every real behaviour
     * test)
     * MUST fail on the template. The match is by name only (case-insensitive), so a real behaviour test will never be wrongly exempted unless an exercise author deliberately names
     * a
     * behaviour test {@code Compile...}/{@code Configure...}/{@code Build...} — which is itself a naming the harness reserves for build gates.
     */
    private static final Set<String> BUILD_GATE_EXACT_NAMES = Set.of("testconfigure", "configure", "compile", "testcompile", "build", "testbuild", "cmake");

    /**
     * Name prefixes that denote a per-target build gate the harness emits ({@code CompileSort}, {@code CompileA}, {@code ConfigureDebug}, {@code BuildTests}). Case-insensitive.
     */
    private static final List<String> BUILD_GATE_PREFIXES = List.of("compile", "configure", "build");

    /**
     * Whether a test-case name is a non-gradable-behaviour build/compile/configure GATE (see {@link #BUILD_GATE_EXACT_NAMES}) that may legitimately pass on both the solution and
     * the template. Such a gate asserts only that the code compiles/configures — which the same-signature placeholder template satisfies by design — so it carries no exercise
     * behaviour and a student cannot earn a behaviour point from it. Every other (behaviour) test must fail on the template.
     *
     * @param normalizedName the already-normalised (trimmed, {@code ()}-stripped) test-case name
     * @return {@code true} if the name is a build/compile/configure gate exempt from the production-parity gate
     */
    private static boolean isBuildGateTest(String normalizedName) {
        // Check the full name AND the last dot-segment: the production-faithful name composition prepends the framework suite prefix, so the real C++ Catch2/Python harness reports
        // the build gates as `GBS-Tester-1.36.TestConfigure` and `GBS-Tester-1.36.CompileSort` — the gate word is the FINAL segment, not the start of the whole name. Matching only
        // the whole name wrongly REJECTED valid C++ exercises (their build gates legitimately pass on both solution and template). So also test the segment after the last '.'.
        if (matchesBuildGateToken(normalizedName)) {
            return true;
        }
        int lastDot = normalizedName.lastIndexOf('.');
        return lastDot >= 0 && lastDot < normalizedName.length() - 1 && matchesBuildGateToken(normalizedName.substring(lastDot + 1));
    }

    /** Whether a single test-name token is exactly a build-gate word, or a {@code GateWord<UpperCaseTarget>} form (e.g. {@code CompileSort}, {@code ConfigureDebug}). */
    private static boolean matchesBuildGateToken(String token) {
        String lower = token.toLowerCase(java.util.Locale.ROOT);
        if (BUILD_GATE_EXACT_NAMES.contains(lower)) {
            return true;
        }
        for (String prefix : BUILD_GATE_PREFIXES) {
            // Require an uppercase target/qualifier after the gate word (CompileSort, ConfigureDebug) so a behaviour test like "compiles_an_empty_program" (lowercase
            // continuation) is never a false positive; the C++/C harnesses use the PascalCase CompileX form.
            if (lower.startsWith(prefix) && token.length() > prefix.length() && Character.isUpperCase(token.charAt(prefix.length()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the test-case names that PASS on the template but should FAIL — i.e. every test the SOLUTION passes (the solution passes all of them, so {@code solution.testNames()}
     * is the solution-passing set) that is NOT in the template's failed/errored set, EXCLUDING the build/compile/configure gates ({@link #isBuildGateTest}) that legitimately pass
     * on
     * both. This mirrors how PRODUCTION grades (every discovered test at default weight, not only the {@code [task]}-bound subset), so it catches an unbound test or a too-lucky
     * placeholder that the {@code [task]}-only gate misses and that would otherwise let a bare-template student score above 0%.
     * <p>
     * <strong>Fails open</strong> (returns empty) under the SAME untrustworthy-emission guards as {@link #taskBoundTestsThatPassOnTemplate}: when the template emitted no fail
     * names
     * at all, or the solution name set is missing/short — so an extraction gap can never spuriously reject a valid exercise.
     *
     * @param solution the solution build summary (its passing test names)
     * @param template the template build summary (its failed/errored test names)
     * @return the offending names that pass on the template (empty when none, or when the check is skipped)
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
     * The solution-build SCA findings that production WOULD penalise (a graded, positively-penalised category), or empty when SCA cannot dock the solution (SCA disabled,
     * {@code maxPenalty == 0}, no graded+positive category — including the default Hyperion categories — or no SCA findings at all). Reads the exercise's persisted SCA categories
     * from the repository, exactly as production grading does ({@code findByExerciseId}), so the decision matches {@code calculateTotalPenalty} rather than the in-memory
     * exercise's
     * detached/lazily-loaded category collection. Fails OPEN (returns empty) when the category repository is absent (a node that cannot grade anyway).
     */
    private List<String> penalisingScaFindings(ProgrammingExercise exercise, BuildSummary solution) {
        // Short-circuit before the DB read when verify.sh emitted nothing (non-SCA exercise) or SCA is plainly off — the common, hot path.
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
     * The aggregated test outcome of one {@code verify.sh} run, read from its {@code HYPERION_RESULT} summary line and {@code HYPERION_TESTNAME} lines.
     *
     * @param tests           the number of tests that ran (zero when the build did not reach the test runner, e.g. a compile error)
     * @param failures        the number of assertion failures
     * @param errors          the number of erroring tests
     * @param exitCode        the build's exit code
     * @param timedOut        whether the run exceeded its time budget
     * @param testNames       the distinct test-case names extracted from the JUnit XML (used to validate [task] bindings); empty if none were emitted
     * @param testFailedNames the distinct names of the test cases that FAILED or ERRORED in this run ({@code HYPERION_TESTFAIL}); used by the strict per-test soundness gate to
     *                            confirm every graded test fails on the template; empty if none were emitted
     * @param scaFindings     the static-code-analysis findings this run produced ({@code HYPERION_SCA}), each as {@code <TOOL>|<rawCategory>}; populated only when the exercise has
     *                            SCA enabled (otherwise verify.sh emits no SCA section). Used by the SCA-parity gate to reject a solution production would dock for a graded SCA
     *                            penalty; empty if none were emitted
     */
    record BuildSummary(int tests, int failures, int errors, int exitCode, boolean timedOut, List<String> testNames, List<String> testFailedNames, List<String> scaFindings) {

        static BuildSummary parse(SandboxExecResult result, String nonce) {
            if (result.timedOut()) {
                return new BuildSummary(0, 0, 0, result.exitCode(), true, List.of(), List.of(), List.of());
            }
            // Each marker is anchored to the per-run nonce, so only lines the PRISTINE script emitted this run are honored — a HYPERION_* line the agent's test code printed to
            // stdout cannot carry the freshly-generated nonce and is ignored. The nonce is quoted so it is matched literally even though it is hex.
            String quotedNonce = Pattern.quote(nonce);
            Pattern marker = Pattern.compile(
                    SandboxBuildCommandService.RESULT_MARKER + "\\s+" + quotedNonce + "\\s+tests=(\\d+)\\s+failures=(\\d+)\\s+errors=(\\d+)\\s+skipped=(\\d+)\\s+exit=(-?\\d+)");
            Pattern testName = Pattern.compile(SandboxBuildCommandService.TESTNAME_MARKER + "\\s+" + quotedNonce + " (.+)");
            Pattern testFail = Pattern.compile(SandboxBuildCommandService.TESTFAIL_MARKER + "\\s+" + quotedNonce + " (.+)");
            Pattern scaFinding = Pattern.compile(SandboxBuildCommandService.SCA_MARKER + "\\s+" + quotedNonce + " (.+)");
            Matcher matcher = marker.matcher(result.combinedOutput());
            // The script prints the summary once at the very end; if it ran more than once (it should not), the last line is the authoritative one.
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
                // The build helper did not emit a (nonce-stamped) summary: it was killed, the workspace was not seeded correctly, or every result line was an unsigned forgery.
                // Treat as a failed build with no tests so the oracle rejects.
                return new BuildSummary(0, 0, 0, result.exitCode() == 0 ? 1 : result.exitCode(), false, List.of(), List.of(), List.of());
            }
            return new BuildSummary(tests, failures, errors, exitCode, false, parseNames(result.combinedOutput(), testName), parseNames(result.combinedOutput(), testFail),
                    parseScaFindings(result.combinedOutput(), scaFinding));
        }

        /**
         * The {@code HYPERION_SCA} payloads ({@code <TOOL>|<rawCategory>}), kept WITH duplicates (each line is a distinct finding, so two findings in the same category both count
         * toward how many issues that category accrues) — unlike {@link #parseNames} which de-duplicates test-case names. The oracle only checks presence per (tool, category), so
         * the duplicates are harmless, but we keep them so a future count-aware gate is not silently starved.
         */
        private static List<String> parseScaFindings(String output, Pattern marker) {
            List<String> findings = new ArrayList<>();
            Matcher matcher = marker.matcher(output);
            while (matcher.find()) {
                findings.add(matcher.group(1).trim());
            }
            return List.copyOf(findings);
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
