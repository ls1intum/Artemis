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
 * Produces the single build recipe — {@code verify.sh} — that both the agent (to check its work) and the {@link AuthoritativeVerificationService} (to decide the verdict) run.
 * Having one
 * script rather than two divergent command strings guarantees the agent's view of "does it build?" is byte-for-byte the same as the grader's.
 * <p>
 * The script faithfully reproduces the real Artemis CI layout ({@code templates/localci/<lang>/build_and_run_tests.sh}): it assembles a fresh, hermetic build tree, copies the
 * tests into it, copies the chosen assignment ({@code solution/} or {@code template/}) into an {@code assignment/} directory next to the tests (so the test project's
 * {@code sourceDirectory} resolves against the assignment exactly as in CI), and runs the exercise's real per-language build phases ({@link BuildPhasesTemplateService}).
 * <p>
 * <strong>The verdict is no longer parsed inside the shell.</strong> Earlier versions re-implemented JUnit/SCA parsing in POSIX {@code awk} (a byte-lexer scrub, a
 * {@code <testcase>}
 * counter, an SCA category extractor) and printed nonce-stamped {@code HYPERION_*} marker lines the verifier scraped from stdout. That re-implementation could only ever
 * approximate
 * the production parsers and reopened a forgery surface (markup-looking text in a test's captured output). Instead, the script now COLLECTS the build-fresh report files into a
 * fixed,
 * verifier-owned directory ({@link #REPORTS_DIR}) and the trusted Java verifier copies that directory out of the container and parses it with the SAME production code the real
 * LocalCI pipeline uses ({@code TestResultXmlParser}, {@code ReportParser}). This gives parity-by-construction, a real XML parser, and production SARIF/GCC category derivation for
 * free. The script prints only a single non-authoritative {@code HYPERION_COLLECTED} liveness line; the verdict is read entirely from the collected files in Java.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class SandboxBuildCommandService {

    private static final Logger log = LoggerFactory.getLogger(SandboxBuildCommandService.class);

    static final String VERIFY_SCRIPT_NAME = "verify.sh";

    /**
     * Verifier-owned directory OUTSIDE {@code /workspace} where the authoritative verifier re-seeds a pristine {@code verify.sh} before each run AND where that script collects the
     * report files. The agent tools only resolve paths under {@code /workspace}, so a path here is unreachable through them — the grader runs a script the agent never touched and
     * reads reports from a directory the agent cannot write, which is what makes the verdict non-forgeable.
     */
    static final String PRISTINE_VERIFY_DIR = "/opt/hyperion";

    /** Absolute path of the pristine, verifier-controlled {@code verify.sh} the authoritative verifier runs (never the agent's {@code /workspace} copy). */
    static final String PRISTINE_VERIFY_PATH = PRISTINE_VERIFY_DIR + "/" + VERIFY_SCRIPT_NAME;

    /**
     * Verifier-owned, agent-unreachable directory the pristine script collects build-fresh report files INTO and the verifier {@code copyOut}s FROM. A constant path the verifier
     * knows a priori — NEVER derived from agent output — so the bytes the verifier parses cannot be redirected by anything the agent writes under {@code /workspace}.
     */
    static final String REPORTS_DIR = PRISTINE_VERIFY_DIR + "/reports";

    /** The prefix of the single non-authoritative liveness line {@code verify.sh} prints (collected report counts); the verdict is read from the collected files, not this line. */
    static final String COLLECTED_MARKER = "HYPERION_COLLECTED";

    /**
     * Filename component the collect step appends to every collected JUnit report ({@code <seq>__junit.xml}); the verifier routes a collected file whose canonical token equals
     * this
     * through {@code TestResultXmlParser}. SCA reports keep their canonical per-tool name ({@code spotbugsXml.xml}, {@code ruff.sarif}, …) as the token instead.
     */
    static final String COLLECTED_JUNIT_TOKEN = "junit.xml";

    /** The separator between the uniquifying sequence and the canonical token in a collected file name ({@code 0001__junit.xml}, {@code 0007__spotbugsXml.xml}). */
    static final String COLLECTED_NAME_SEPARATOR = "__";

    /**
     * JUnit-XML report locations that cover the languages Artemis ships, independent of any phase-declared paths (surefire/failsafe for Maven, Gradle's test-results, and the
     * test-reports/ directory pytest, the C harness and OCaml write to). The phase's own resultPaths are added on top so any language-specific location is also covered.
     */
    private static final List<String> DEFAULT_REPORT_GLOBS = List.of("surefire-reports/*.xml", "failsafe-reports/*.xml", "test-results/*.xml", "test-results/*/*.xml",
            "test-reports/*.xml", "test-results.xml");

    // Both services are present only on nodes that run the LocalCI orchestration (profile localci). On a core-only node they are absent; generation requires a co-located build
    // agent anyway, so the dependencies are optional and their absence is reported clearly at call time rather than preventing the core node from starting.
    private final Optional<BuildPhasesTemplateService> buildPhasesTemplateService;

    private final Optional<BuildScriptProviderService> buildScriptProviderService;

    public SandboxBuildCommandService(Optional<BuildPhasesTemplateService> buildPhasesTemplateService, Optional<BuildScriptProviderService> buildScriptProviderService) {
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.buildScriptProviderService = buildScriptProviderService;
    }

    /**
     * @return the command that runs the PRISTINE (verifier-controlled, outside {@code /workspace}) verification with the solution as the assignment
     */
    public String pristineSolutionBuildCommand() {
        return pristineVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
    }

    /**
     * @return the command that runs the PRISTINE verification with the template as the assignment
     */
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
        // Materialize the tests at the language's real test checkout path (root for Java/Python, a "tests/" subdir for C/Go/OCaml/…) so phase scripts that `cd` into it resolve.
        String testDestination = recipe.testDir().isEmpty() ? "$BUILD_DIR" : "$BUILD_DIR/" + recipe.testDir();
        String phaseSection = buildPhaseSection(recipe.phases());
        // The script is intentionally plain POSIX sh so it runs in any of the ~20 language images (some have no bash). It must not abort before collecting the reports, so each
        // phase runs in its own subshell (re-rooted at the build dir, like real CI) and a non-zero exit is recorded rather than aborting the script.
        return """
                #!/bin/sh
                # Generated by Artemis Hyperion. Assembles the CI build layout and runs the exercise's real build phases for one assignment (solution or template),
                # then COLLECTS the build-fresh test/SCA report files into a fixed, verifier-owned directory. Used by both the generation agent (to self-check) and the
                # authoritative verifier (which copies the collected reports out and parses them with the production parsers — the verdict is NOT decided in this script).
                ASSIGNMENT="$1"
                if [ "$ASSIGNMENT" != "solution" ] && [ "$ASSIGNMENT" != "template" ]; then
                    echo "usage: verify.sh <solution|template>" >&2
                    exit 64
                fi
                WORKSPACE="%s"
                REPORTS_DIR="%s/$ASSIGNMENT"
                BUILD_DIR=$(mktemp -d /tmp/hyperion-verify.XXXXXX) || exit 70
                trap 'rm -rf "$BUILD_DIR"' EXIT
                # Materialize the CI checkout layout: the tests at the language's test checkout path, the chosen assignment in assignment/ (-a preserves exec bits and binaries).
                TEST_DEST="%s"
                mkdir -p "$TEST_DEST"
                cp -a "$WORKSPACE/tests/." "$TEST_DEST"/ 2>/dev/null || true
                mkdir -p "$BUILD_DIR/assignment"
                cp -a "$WORKSPACE/$ASSIGNMENT/." "$BUILD_DIR/assignment"/ 2>/dev/null || true
                # Substitute the CI directory placeholders inside the COPIED test harness, exactly as production exercise-creation does, so a seeded harness resolves against THIS build
                # tree without the agent editing it. Both the student parent and solution working directory map to assignment/ (where the chosen assignment is copied in both runs); the
                # test working directory is the build root. Build-tree copy only — the seeded source files are untouched.
                find "$TEST_DEST" -type f 2>/dev/null | while IFS= read -r f; do
                    sed -e 's#${studentWorkingDirectory}#/assignment/src#g' \\
                        -e 's#${studentParentWorkingDirectoryName}#assignment#g' \\
                        -e 's#${solutionWorkingDirectory}#assignment#g' \\
                        -e 's#${testWorkingDirectory}#.#g' "$f" > "$f.hyp" 2>/dev/null && mv "$f.hyp" "$f" 2>/dev/null || rm -f "$f.hyp" 2>/dev/null
                done
                # Anti-forgery: delete every pre-existing report XML before the phases run (the agent can plant one in tests/ and cp -a preserves its mtime), so only reports written
                # this run are collected.
                find "$BUILD_DIR" -type f \\( %s \\) -delete 2>/dev/null || true
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
                %s
                # Collect the build-fresh report files into the verifier-owned REPORTS_DIR. The verifier copies THIS directory out of the container and parses each file with the
                # production parsers, so the verdict rests on real parsing, not shell text-scraping. We copy ONLY regular files (find -type f already excludes symlinks/dirs/devices)
                # and re-seed the directory empty each run so a previous run's reports cannot leak in. Each file is renamed to <seq>__<canonical> so the verifier can route it:
                # JUnit reports get the fixed canonical token "%s"; SCA reports keep their per-tool canonical name so ParserPolicy picks the right parser.
                rm -rf "$REPORTS_DIR" 2>/dev/null || true
                mkdir -p "$REPORTS_DIR" || exit 70
                collected_tests=0
                collected_sca=0
                collect_one() {
                    # $1 = source file, $2 = canonical token. cp -P never follows a symlink; combined with the -type f find that produced $1, only a regular file is copied.
                    seq=$1; src=$2; canonical=$3
                    cp -P "$src" "$REPORTS_DIR/$(printf '%%04d' "$seq")%s$canonical" 2>/dev/null || true
                }
                seq=0
                for report in $(find "$BUILD_DIR" -type f -newer "$BUILD_START_MARKER" \\( %s \\) 2>/dev/null); do
                    seq=$((seq + 1)); collect_one "$seq" "$report" "%s"; collected_tests=$((collected_tests + 1))
                done
                %s
                echo "%s tests=$collected_tests sca=$collected_sca exit=$rc"
                exit $rc
                """
                .formatted(GenerationWorkspaceService.WORKSPACE, REPORTS_DIR, testDestination, findExpression, phaseSection, COLLECTED_JUNIT_TOKEN, COLLECTED_NAME_SEPARATOR,
                        findExpression, COLLECTED_JUNIT_TOKEN, buildScaCollectSection(scaFindExpression), COLLECTED_MARKER);
    }

    /**
     * Renders each build phase as a {@code run_phase '<script>'} call. Single quotes inside a phase are escaped for the POSIX single-quoted string so the phase body is passed to
     * {@code eval} verbatim. Running one phase per {@code run_phase} (each re-rooted at the build dir) mirrors how real CI resets the working directory before every phase.
     */
    private static String buildPhaseSection(List<String> phases) {
        return phases.stream().map(phase -> "run_phase '" + phase.replace("'", "'\\''") + "'").collect(Collectors.joining("\n"));
    }

    /**
     * The collect block for static-code-analysis reports, or the empty string when SCA is disabled ({@code scaFindExpression} empty) — in which case the generated
     * {@code verify.sh}
     * collects only JUnit reports and a non-SCA exercise's behaviour is unchanged. When SCA is enabled, the block collects each build-fresh SCA report file (restricted to the
     * canonical per-tool names and to reports written THIS run via {@code -newer "$BUILD_START_MARKER"}, so a planted stale report cannot be collected) into the same reports dir,
     * keeping its canonical name as the routing token so the verifier's {@code ReportParser} (production code, including the SARIF/GCC categorizers) picks the right parser.
     */
    private static String buildScaCollectSection(String scaFindExpression) {
        if (scaFindExpression.isEmpty()) {
            return "";
        }
        // basename gives the canonical per-tool report name (spotbugsXml.xml, ruff.sarif, …) which the verifier routes through ParserPolicy.
        return """
                for report in $(find "$BUILD_DIR" -type f -newer "$BUILD_START_MARKER" \\( %s \\) 2>/dev/null); do
                    seq=$((seq + 1)); collect_one "$seq" "$report" "$(basename "$report")"; collected_sca=$((collected_sca + 1))
                done""".formatted(scaFindExpression);
    }

    /**
     * Builds the {@code find} predicate that locates the test-report XML files: each report glob becomes a {@code -path} clause anchored to match anywhere under the hermetic
     * build tree, OR-ed together. The globs are normalized (globstars dropped, any leading {@code ./} or {@code /} stripped).
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
     * The build phases (each already placeholder-substituted), the JUnit report locations, the test checkout directory ({@code ""} = build root), and — when static code analysis
     * is
     * ENABLED for the exercise — the SCA tool report file names ({@code spotbugsXml.xml}, {@code ruff.sarif}, …) the SCA collection scans. The SCA list is EMPTY when SCA is
     * disabled,
     * so the generated script collects no SCA reports and a non-SCA exercise's behaviour is unchanged.
     */
    private record BuildRecipe(List<String> phases, List<String> reportGlobs, String testDir, List<String> scaReportFiles) {
    }

    /**
     * Resolves the per-language build recipe. Uses the exact LocalCI build phases when available so the verification toolchain matches real CI for every supported language, and
     * applies the same placeholder substitution real CI does ({@code ${testWorkingDirectory}}, {@code ${studentParentWorkingDirectoryName}}, …) mapped to the language's real
     * checkout layout: the assignment is checked out into {@code assignment/} and the tests into the language's test checkout path (the build root for Java/Python, a
     * {@code tests/} subdirectory for C/Go/OCaml/…). Falls back to the conventional Gradle/Maven commands when the phase template cannot be resolved.
     */
    private BuildRecipe resolveBuildRecipe(ProgrammingExercise exercise) {
        String assignmentDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.ASSIGNMENT, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getAssignmentCheckoutPath() : null, "assignment");
        String testDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.TEST, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getTestCheckoutPath() : null, "");

        List<BuildPhaseDTO> phases = List.of();
        if (buildPhasesTemplateService.isPresent()) {
            try {
                // getDefaultBuildPlanPhasesFor may return null (not just throw) when the phase template cannot be resolved, so guard against both.
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

        // SCA report files collected by the SCA collection, ONLY when SCA is enabled (else empty -> no SCA collection -> non-SCA behaviour unchanged). These are the canonical
        // per-tool report file names production reads (StaticCodeAnalysisTool.getFileName); the static build phase writes them (e.g. java/plain_maven_static.yaml emits
        // target/spotbugsXml.xml), so they already appear in the build tree this run. We scan them independently of resultPaths so the SCA signal does not depend on the SCA report
        // being declared a JUnit glob.
        List<String> scaReportFiles = scaReportFileNames(exercise);

        List<String> phaseScripts = phases.stream().map(BuildPhaseDTO::script).filter(s -> s != null && !s.isBlank()).map(s -> substitute(s, assignmentDir, testDir)).toList();
        if (!phaseScripts.isEmpty()) {
            return new BuildRecipe(phaseScripts, reportGlobs, testDir, scaReportFiles);
        }
        // Generic fallback mirroring build_and_run_tests.sh: prefer a Gradle wrapper, then Maven, then a system Gradle.
        String fallback = """
                if [ -x ./gradlew ]; then ./gradlew clean test --no-daemon;
                elif [ -f pom.xml ]; then mvn clean test;
                elif [ -f build.gradle ]; then gradle clean test --no-daemon;
                else echo 'No recognized build system' >&2; exit 2; fi""";
        return new BuildRecipe(List.of(fallback), reportGlobs, testDir, scaReportFiles);
    }

    /**
     * The canonical SCA report file names for the exercise's language ({@code spotbugsXml.xml}, {@code checkstyle-result.xml}, {@code ruff.sarif}, …) — but ONLY when static code
     * analysis is enabled. Returns an empty list when SCA is disabled (or the language has no SCA tools), so {@link #buildScaCollectSection} emits nothing and the script for a
     * non-SCA exercise collects only JUnit reports.
     */
    private static List<String> scaReportFileNames(ProgrammingExercise exercise) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) || exercise.getProgrammingLanguage() == null) {
            return List.of();
        }
        return StaticCodeAnalysisTool.getToolsForProgrammingLanguage(exercise.getProgrammingLanguage()).stream().map(StaticCodeAnalysisTool::getFileName)
                .filter(name -> name != null && !name.isBlank()).distinct().toList();
    }

    /**
     * Resolves a repository checkout subdirectory for the exercise's language: the instructor-configured path if set, otherwise the language default from
     * {@link RepositoryCheckoutService.RepositoryCheckoutPath}. Falls back to {@code defaultPath} if the language is unsupported by the enum.
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
     * Substitutes the CI directory placeholders ({@code ${studentParentWorkingDirectoryName}}, {@code ${testWorkingDirectory}}, …) to the resolved checkout layout, reusing the
     * exact substitution real CI applies. An empty test directory (tests at the build root) is substituted as {@code .} so a phase's {@code cd ${testWorkingDirectory}} stays put
     * rather than {@code cd} with no argument. Returns the input unchanged when the LocalCI service is absent (a core-only node, where generation does not run anyway).
     */
    private String substitute(String script, String assignmentDir, String testDir) {
        String testRepo = testDir.isEmpty() ? "." : testDir;
        return buildScriptProviderService.map(service -> service.replacePlaceholders(script, assignmentDir, RepositoryType.SOLUTION.toString(), testRepo)).orElse(script);
    }
}
