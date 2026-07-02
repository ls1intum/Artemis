package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService;

/**
 * Produces the single build recipe — {@code verify.sh} — that both the agent (to self-check) and the {@link AuthoritativeVerificationService} (to decide the verdict) run, so the
 * agent's view of "does it build?" is byte-for-byte the grader's.
 * <p>
 * The script reproduces the real Artemis CI layout: a fresh hermetic build tree with the tests checked out and the chosen assignment ({@code solution/} or {@code template/})
 * copied into {@code assignment/} next to them, then runs the exercise's real per-language build phases ({@link BuildPhasesTemplateService}).
 * <p>
 * The verdict is NOT parsed in the shell: the script only COLLECTS the build-fresh report files into a fixed, verifier-owned directory ({@link #REPORTS_DIR}); the Java verifier
 * copies that directory out and parses it with the SAME production code as the LocalCI pipeline ({@code TestResultXmlParser}, {@code ReportParser}) — parity by construction. The
 * script prints only a single non-authoritative {@code HYPERION_COLLECTED} liveness line.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class SandboxBuildCommandService {

    private static final Logger log = LoggerFactory.getLogger(SandboxBuildCommandService.class);

    static final String VERIFY_SCRIPT_NAME = "verify.sh";

    /**
     * Verifier-owned directory OUTSIDE {@code /workspace} where the verifier re-seeds a pristine {@code verify.sh} per run and that script collects the reports. The agent tools
     * only resolve paths under {@code /workspace}, so this is unreachable to them — the grader runs a script the agent never touched and reads reports it cannot write, which is
     * what makes the verdict non-forgeable.
     */
    static final String PRISTINE_VERIFY_DIR = "/opt/hyperion";

    /** Absolute path of the pristine, verifier-controlled {@code verify.sh} (never the agent's {@code /workspace} copy). */
    static final String PRISTINE_VERIFY_PATH = PRISTINE_VERIFY_DIR + "/" + VERIFY_SCRIPT_NAME;

    /** Verifier-owned, agent-unreachable directory the script collects reports INTO and the verifier {@code copyOut}s FROM. */
    static final String REPORTS_DIR = PRISTINE_VERIFY_DIR + "/reports";

    /** Prefix of the non-authoritative liveness line {@code verify.sh} prints; the verdict is read from the collected files, not this line. */
    static final String COLLECTED_MARKER = "HYPERION_COLLECTED";

    /**
     * Canonical token the collect step appends to every collected JUnit report ({@code <seq>__junit.xml}); the verifier routes a file with this token through
     * {@code TestResultXmlParser}. SCA reports keep their per-tool name ({@code spotbugsXml.xml}, …) as the token instead.
     */
    static final String COLLECTED_JUNIT_TOKEN = "junit.xml";

    /** Separator between the uniquifying sequence and the canonical token in a collected file name ({@code 0001__junit.xml}). */
    static final String COLLECTED_NAME_SEPARATOR = "__";

    /**
     * JUnit-XML report locations covering all shipped languages, independent of phase-declared paths (Maven surefire/failsafe, Gradle test-results, and the test-reports/ dir
     * pytest/C/OCaml write to). The phase's own resultPaths are added on top.
     */
    private static final List<String> DEFAULT_REPORT_GLOBS = List.of("surefire-reports/*.xml", "failsafe-reports/*.xml", "test-results/*.xml", "test-results/*/*.xml",
            "test-reports/*.xml", "test-results.xml");

    // Optional: present only on LocalCI-orchestration nodes (profile localci). Generation requires a co-located build agent anyway, so absence is reported at call time rather than
    // blocking a core-only node from starting.
    private final Optional<BuildPhasesTemplateService> buildPhasesTemplateService;

    private final Optional<BuildScriptProviderService> buildScriptProviderService;

    public SandboxBuildCommandService(Optional<BuildPhasesTemplateService> buildPhasesTemplateService, Optional<BuildScriptProviderService> buildScriptProviderService) {
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.buildScriptProviderService = buildScriptProviderService;
    }

    public String pristineSolutionBuildCommand() {
        return pristineVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
    }

    public String pristineTemplateBuildCommand() {
        return pristineVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));
    }

    /**
     * The verifier-owned directory the pristine script collected the reports for an assignment into, which the verifier copies out and parses.
     *
     * @param assignment the assignment directory name ({@code solution} or {@code template})
     * @return the absolute container path of that assignment's collected-reports directory
     */
    public static String reportsDirectoryFor(String assignment) {
        return REPORTS_DIR + "/" + assignment;
    }

    private static String pristineVerifyInvocation(String assignmentDirectory) {
        return "sh " + PRISTINE_VERIFY_PATH + " " + assignmentDirectory;
    }

    /**
     * Builds the content of the {@code verify.sh} script seeded into the workspace.
     *
     * @param exercise the exercise whose per-language build phases the script runs
     * @return the full shell script (POSIX {@code sh}); it takes one argument, {@code solution} or {@code template}
     */
    public String verifyScriptContent(ProgrammingExercise exercise) {
        BuildRecipe recipe = resolveBuildRecipe(exercise);
        String findExpression = buildFindExpression(recipe.reportGlobs());
        String scaFindExpression = buildScaFindExpression(recipe.scaReportFiles());
        // Tests go at the language's real checkout path (root for Java/Python, a "tests/" subdir for C/Go/OCaml/…) so phase scripts that `cd` into it resolve.
        String testDestination = recipe.testDir().isEmpty() ? "$BUILD_DIR" : "$BUILD_DIR/" + recipe.testDir();
        String phaseSection = buildPhaseSection(recipe.phases());
        // CI placeholder values for the seeded harness, mapped to the real checkout layout. With no solution checkout the solution placeholder never appears, so its fallback is
        // moot.
        String solutionPlaceholderValue = recipe.solutionDir().isEmpty() ? "assignment" : recipe.solutionDir();
        String testPlaceholderValue = recipe.testDir().isEmpty() ? "." : recipe.testDir();
        // Materialize a sibling solution/ EXACTLY when real CI would (language defines a solution checkout path — Haskell/OCaml — AND the exercise checks it out), so the harness
        // reference (e.g. the Haskell cabal's `library solution`) resolves. Other languages get no solution/, keeping their differential unchanged.
        boolean materializeSolution = recipe.materializesSolution();
        String solutionCopySection = materializeSolution
                ? "mkdir -p \"$BUILD_DIR/" + recipe.solutionDir() + "\"\n                cp -a \"$WORKSPACE/solution/.\" \"$BUILD_DIR/" + recipe.solutionDir()
                        + "\"/ 2>/dev/null || true"
                : ": # this language's harness references no sibling solution/";
        // Plain POSIX sh (some of the ~20 language images have no bash). Rendered by @@TOKEN@@ name, not positional %s, so substitution is order-independent and a repeated value
        // (the find-expression, the JUnit token) is written once.
        String script = """
                #!/bin/sh
                # Generated by Artemis Hyperion. Assembles the CI build layout and runs the exercise's real build phases for one assignment (solution or template),
                # then COLLECTS the build-fresh test/SCA report files into a fixed, verifier-owned directory. Used by both the generation agent (to self-check) and the
                # authoritative verifier (which copies the collected reports out and parses them with the production parsers — the verdict is NOT decided in this script).
                ASSIGNMENT="$1"
                if [ "$ASSIGNMENT" != "solution" ] && [ "$ASSIGNMENT" != "template" ]; then
                    echo "usage: verify.sh <solution|template>" >&2
                    exit 64
                fi
                WORKSPACE="@@WORKSPACE@@"
                REPORTS_DIR="@@REPORTS_DIR@@/$ASSIGNMENT"
                BUILD_DIR=$(mktemp -d /tmp/hyperion-verify.XXXXXX) || exit 70
                trap 'rm -rf "$BUILD_DIR"' EXIT
                # Materialize the CI checkout layout: the tests at the language's test checkout path, the chosen assignment in assignment/ (-a preserves exec bits and binaries).
                TEST_DEST="@@TEST_DEST@@"
                mkdir -p "$TEST_DEST"
                cp -a "$WORKSPACE/tests/." "$TEST_DEST"/ 2>/dev/null || true
                mkdir -p "$BUILD_DIR/assignment"
                cp -a "$WORKSPACE/$ASSIGNMENT/." "$BUILD_DIR/assignment"/ 2>/dev/null || true
                @@SOLUTION_COPY@@
                # Substitute the CI directory placeholders inside the COPIED test harness, exactly as production exercise-creation does, so a seeded harness resolves against THIS build
                # tree without the agent editing it. The student parent working directory is assignment/ (the chosen assignment, copied in both runs); the solution and test working
                # directories use the language's real CI checkout layout. Build-tree copy only — the seeded source files are untouched.
                find "$TEST_DEST" -type f 2>/dev/null | while IFS= read -r f; do
                    sed -e 's#${studentWorkingDirectory}#/assignment/src#g' \\
                        -e 's#${studentParentWorkingDirectoryName}#assignment#g' \\
                        -e 's#${solutionWorkingDirectory}#@@SOLUTION_DIR@@#g' \\
                        -e 's#${testWorkingDirectory}#@@TEST_DIR@@#g' "$f" > "$f.hyp" 2>/dev/null && mv "$f.hyp" "$f" 2>/dev/null || rm -f "$f.hyp" 2>/dev/null
                done
                # Anti-forgery: delete every pre-existing report XML before the phases run (the agent can plant one in tests/ and cp -a preserves its mtime), so only reports written
                # this run are collected.
                find "$BUILD_DIR" -type f \\( @@REPORT_FIND@@ \\) -delete 2>/dev/null || true
                # Reference marker; collection takes only reports NEWER than it, so a planted report that escaped the delete still cannot be collected.
                BUILD_START_MARKER="$BUILD_DIR/.hyperion-build-start"
                : > "$BUILD_START_MARKER"
                # Run the exercise's real build phases, each from the build root. A non-zero exit (failing tests or a compile error) is expected for the template.
                rc=0
                run_phase() {
                    ( cd "$BUILD_DIR" || exit 70; set -e; eval "$1" )
                    phase_rc=$?
                    if [ "$phase_rc" -ne 0 ] && [ "$rc" -eq 0 ]; then rc=$phase_rc; fi
                }
                @@PHASES@@
                # Collect the build-fresh report files into the verifier-owned REPORTS_DIR. The verifier copies THIS directory out of the container and parses each file with the
                # production parsers, so the verdict rests on real parsing, not shell text-scraping. We copy ONLY regular files (find -type f already excludes symlinks/dirs/devices)
                # and re-seed the directory empty each run so a previous run's reports cannot leak in. Each file is renamed to <seq>__<canonical> so the verifier can route it:
                # JUnit reports get the fixed canonical token "@@JUNIT_TOKEN@@"; SCA reports keep their per-tool canonical name so ParserPolicy picks the right parser.
                rm -rf "$REPORTS_DIR" 2>/dev/null || true
                mkdir -p "$REPORTS_DIR" || exit 70
                collected_tests=0
                collected_sca=0
                collect_one() {
                    # $1 = sequence, $2 = source file, $3 = canonical token. cp -P never follows a symlink; combined with the -type f find that produced $2, only a regular file is copied.
                    seq=$1; src=$2; canonical=$3
                    cp -P "$src" "$REPORTS_DIR/$(printf '%04d' "$seq")@@NAME_SEP@@$canonical" 2>/dev/null || true
                }
                seq=0
                for report in $(find "$BUILD_DIR" -type f -newer "$BUILD_START_MARKER" \\( @@REPORT_FIND@@ \\) 2>/dev/null); do
                    seq=$((seq + 1)); collect_one "$seq" "$report" "@@JUNIT_TOKEN@@"; collected_tests=$((collected_tests + 1))
                done
                @@SCA_COLLECT@@
                echo "@@COLLECTED_MARKER@@ tests=$collected_tests sca=$collected_sca exit=$rc"
                exit $rc
                """;
        return script.replace("@@WORKSPACE@@", GenerationWorkspaceService.WORKSPACE).replace("@@REPORTS_DIR@@", REPORTS_DIR).replace("@@TEST_DEST@@", testDestination)
                .replace("@@SOLUTION_COPY@@", solutionCopySection).replace("@@SOLUTION_DIR@@", solutionPlaceholderValue).replace("@@TEST_DIR@@", testPlaceholderValue)
                .replace("@@REPORT_FIND@@", findExpression).replace("@@PHASES@@", phaseSection).replace("@@SCA_COLLECT@@", buildScaCollectSection(scaFindExpression))
                .replace("@@NAME_SEP@@", COLLECTED_NAME_SEPARATOR).replace("@@JUNIT_TOKEN@@", COLLECTED_JUNIT_TOKEN).replace("@@COLLECTED_MARKER@@", COLLECTED_MARKER);
    }

    /**
     * Renders each build phase as a {@code run_phase '<script>'} call, escaping single quotes so the body reaches {@code eval} verbatim. One phase per call (each re-rooted at the
     * build dir) mirrors how real CI resets the working directory before every phase.
     */
    private static String buildPhaseSection(List<String> phases) {
        return phases.stream().map(phase -> "run_phase '" + phase.replace("'", "'\\''") + "'").collect(Collectors.joining("\n"));
    }

    /**
     * The collect block for static-code-analysis reports, or empty when SCA is disabled (then only JUnit reports are collected and a non-SCA exercise is unchanged). When enabled
     * it
     * collects each build-fresh SCA report (canonical per-tool names, {@code -newer "$BUILD_START_MARKER"} to exclude planted stale ones), keeping its canonical name as the
     * routing
     * token so the verifier's production {@code ReportParser} picks the right parser.
     */
    private static String buildScaCollectSection(String scaFindExpression) {
        if (scaFindExpression.isEmpty()) {
            return "";
        }
        return """
                for report in $(find "$BUILD_DIR" -type f -newer "$BUILD_START_MARKER" \\( %s \\) 2>/dev/null); do
                    seq=$((seq + 1)); collect_one "$seq" "$report" "$(basename "$report")"; collected_sca=$((collected_sca + 1))
                done""".formatted(scaFindExpression);
    }

    /**
     * Builds the {@code find} predicate locating test-report XMLs: each normalized glob (globstars dropped, leading {@code ./} or {@code /} stripped) becomes an OR-ed
     * {@code -path} clause.
     */
    private static String buildFindExpression(List<String> reportGlobs) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String glob : reportGlobs) {
            String normalized = glob.trim().replace("**/", "").replace("**", "*");
            while (normalized.startsWith("./") || normalized.startsWith("/")) {
                normalized = normalized.startsWith("./") ? normalized.substring(2) : normalized.substring(1);
            }
            if (!normalized.isBlank()) {
                tokens.add("-path '*/" + normalized + "'");
            }
        }
        return String.join(" -o ", tokens);
    }

    /**
     * Builds the {@code find} predicate matching each SCA tool's canonical report file by name ({@code -name 'spotbugsXml.xml' -o -name 'ruff.sarif' …}); empty when SCA is off.
     */
    private static String buildScaFindExpression(List<String> scaReportFiles) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String fileName : scaReportFiles) {
            if (fileName != null && !fileName.isBlank()) {
                tokens.add("-name '" + fileName + "'");
            }
        }
        return String.join(" -o ", tokens);
    }

    /**
     * Sentinel field semantics: empty {@code testDir} = tests at the build root (no {@code tests/} subdir); empty {@code solutionDir} = no sibling solution checkout; empty
     * {@code scaReportFiles} = SCA disabled.
     */
    private record BuildRecipe(List<String> phases, List<String> reportGlobs, String testDir, String solutionDir, boolean checkoutSolution, List<String> scaReportFiles) {

        /** True exactly when the language defines a solution checkout path (Haskell/OCaml) AND the exercise checks it out. */
        boolean materializesSolution() {
            return !solutionDir.isEmpty() && checkoutSolution;
        }
    }

    /**
     * Summary of the resolved build recipe for the agent's system prompt, derived from the SAME {@link #resolveBuildRecipe} that renders {@code verify.sh} so prompt and grader
     * cannot drift.
     *
     * @param phaseScripts         the placeholder-substituted per-phase commands, run in order from the build root
     * @param reportGlobs          the build-root-relative locations the grader collects test-report XML from
     * @param testCheckoutDir      where the tests are checked out ({@code ""} = the build root, not a {@code tests/} subdir)
     * @param materializesSolution whether a sibling {@code solution/} checkout is materialized (Haskell/OCaml only)
     * @param scaReportFiles       the canonical SCA report file names the grader parses ({@code empty} = SCA disabled)
     */
    public record BuildContextSummary(List<String> phaseScripts, List<String> reportGlobs, String testCheckoutDir, boolean materializesSolution, List<String> scaReportFiles) {
    }

    /**
     * Resolves the build context (phase commands, report locations, checkout layout) for the agent's system prompt, reusing the recipe behind {@code verify.sh}.
     *
     * @param exercise the exercise being generated or adapted
     * @return the resolved build-context summary
     */
    public BuildContextSummary describeBuildContext(ProgrammingExercise exercise) {
        BuildRecipe recipe = resolveBuildRecipe(exercise);
        boolean materializeSolution = recipe.materializesSolution();
        return new BuildContextSummary(recipe.phases(), recipe.reportGlobs(), recipe.testDir(), materializeSolution, recipe.scaReportFiles());
    }

    /**
     * Resolves the per-language build recipe from the exact LocalCI build phases (matching real CI), applying the same placeholder substitution mapped to the language's checkout
     * layout. Falls back to conventional Gradle/Maven commands when the phase template cannot be resolved.
     */
    private BuildRecipe resolveBuildRecipe(ProgrammingExercise exercise) {
        String assignmentDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.ASSIGNMENT, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getAssignmentCheckoutPath() : null, "assignment");
        String testDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.TEST, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getTestCheckoutPath() : null, "");
        // Defined only for harnesses referencing a sibling solution/ (Haskell/OCaml); other languages make the enum throw -> "" -> no solution/ materialized.
        String solutionDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.SOLUTION, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getSolutionCheckoutPath() : null, "");
        boolean checkoutSolution = exercise.getBuildConfig() != null && exercise.getBuildConfig().getCheckoutSolutionRepository();

        List<BuildPhaseDTO> phases = List.of();
        if (buildPhasesTemplateService.isPresent()) {
            try {
                // May return null (not only throw) when the phase template cannot be resolved.
                List<BuildPhaseDTO> resolved = buildPhasesTemplateService.get().getDefaultBuildPlanPhasesFor(exercise);
                if (resolved != null) {
                    phases = resolved;
                }
            }
            catch (RuntimeException e) {
                log.warn("Could not resolve build phases for exercise {} ({}); falling back to a generic build.", exercise.getId(), e.getMessage());
            }
        }

        List<String> reportGlobs = new ArrayList<>(DEFAULT_REPORT_GLOBS);
        phases.stream().filter(p -> p.resultPaths() != null).flatMap(p -> p.resultPaths().stream()).map(path -> substitute(path, assignmentDir, testDir))
                .filter(s -> s != null && !s.isBlank()).forEach(reportGlobs::add);

        // Canonical per-tool SCA report names (StaticCodeAnalysisTool.getFileName), scanned independently of resultPaths so the SCA signal does not depend on the report being
        // declared a JUnit glob. Empty when SCA is off.
        List<String> scaReportFiles = scaReportFileNames(exercise);

        List<String> phaseScripts = phases.stream().map(BuildPhaseDTO::script).filter(s -> s != null && !s.isBlank()).map(s -> substitute(s, assignmentDir, testDir)).toList();
        if (!phaseScripts.isEmpty()) {
            return new BuildRecipe(phaseScripts, reportGlobs, testDir, solutionDir, checkoutSolution, scaReportFiles);
        }
        // Generic fallback: prefer a Gradle wrapper, then Maven, then a system Gradle.
        String fallback = """
                if [ -x ./gradlew ]; then ./gradlew clean test --no-daemon;
                elif [ -f pom.xml ]; then mvn clean test;
                elif [ -f build.gradle ]; then gradle clean test --no-daemon;
                else echo 'No recognized build system' >&2; exit 2; fi""";
        return new BuildRecipe(List.of(fallback), reportGlobs, testDir, solutionDir, checkoutSolution, scaReportFiles);
    }

    /** Canonical SCA report file names for the exercise's language, or empty when SCA is disabled or the language has no SCA tools. */
    private static List<String> scaReportFileNames(ProgrammingExercise exercise) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) || exercise.getProgrammingLanguage() == null) {
            return List.of();
        }
        return StaticCodeAnalysisTool.getToolsForProgrammingLanguage(exercise.getProgrammingLanguage()).stream().map(StaticCodeAnalysisTool::getFileName)
                .filter(name -> name != null && !name.isBlank()).distinct().toList();
    }

    /**
     * Resolves a repository checkout subdirectory: the instructor-configured path if set, otherwise the language default, otherwise {@code defaultPath} if the enum does not
     * support
     * the language.
     */
    private String checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath kind, ProgrammingExercise exercise, String configured, String defaultPath) {
        if (configured != null && !configured.isBlank()) {
            return configured.startsWith("/") ? configured.substring(1) : configured;
        }
        if (exercise.getProgrammingLanguage() == null) {
            return defaultPath;
        }
        try {
            return kind.forProgrammingLanguage(exercise.getProgrammingLanguage());
        }
        catch (RuntimeException e) {
            return defaultPath;
        }
    }

    /**
     * Substitutes the CI directory placeholders to the resolved checkout layout via the exact real-CI substitution. An empty test dir maps to {@code .} so {@code cd
     * ${testWorkingDirectory}} stays put rather than {@code cd} with no argument. Returns the input unchanged when the LocalCI service is absent.
     */
    private String substitute(String script, String assignmentDir, String testDir) {
        String testRepo = testDir.isEmpty() ? "." : testDir;
        return buildScriptProviderService.map(service -> service.replacePlaceholders(script, assignmentDir, RepositoryType.SOLUTION.toString(), testRepo)).orElse(script);
    }
}
