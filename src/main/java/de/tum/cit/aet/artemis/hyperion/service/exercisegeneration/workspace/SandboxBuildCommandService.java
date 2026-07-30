package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService;

/**
 * Produces the single build recipe — {@code verify.sh} — that both the agent (to self-check) and the {@link DifferentialVerificationService} (to decide the verdict) run, so the
 * agent's view of "does it build?" uses the same build phases and report parsers as the grader.
 * <p>
 * The script reproduces the real Artemis CI layout: a fresh hermetic build tree with the tests checked out and the chosen assignment ({@code solution/} or {@code template/})
 * copied into {@code assignment/} next to them, then runs the exercise's real per-language build phases ({@link BuildPhasesTemplateService}).
 * <p>
 * The verdict is deliberately not parsed in the shell: the script only collects build-fresh report files into {@link #REPORTS_DIR}, and the Java verifier copies that directory
 * out and parses it with the same production code as LocalCI ({@code TestResultXmlParser}, {@code ReportParser}). Text-scraping the build log in shell would be a second,
 * silently diverging implementation of grading.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class SandboxBuildCommandService {

    private static final Logger log = LoggerFactory.getLogger(SandboxBuildCommandService.class);

    private static final Pattern MAVEN_TEST_PHASE = Pattern
            .compile("(?s)^(\\s*(?:cd\\s+[^\\n;&|]+\\n)?\\s*mvn\\s+)((?:-[^\\s;&|]+\\s+)*(?:clean\\s+)?)test((?:\\s+-[^\\s;&|]+)*)\\s*$");

    private static final Pattern MAVEN_STATIC_ANALYSIS_PHASE = Pattern
            .compile("(?s)^\\s*(?:cd\\s+[^\\n]+\\n)?\\s*mvn\\s+[^\\n;&|]*(?:spotbugs:spotbugs|checkstyle:checkstyle|pmd:pmd|pmd:cpd)[^\\n;&|]*\\s*$");

    public static final String VERIFY_SCRIPT_NAME = "verify.sh";

    /** Lives outside {@code /workspace} so the agent can neither read nor rewrite the script and reports the verdict rests on. */
    public static final String PRISTINE_VERIFY_DIR = "/opt/hyperion";

    public static final String PRISTINE_VERIFY_PATH = PRISTINE_VERIFY_DIR + "/" + VERIFY_SCRIPT_NAME;

    public static final String TRUSTED_STRUCTURAL_DIR = PRISTINE_VERIFY_DIR + "/trusted-structural";

    /** Fixture for the pre-provider readiness build. Outside the agent workspace, and consumed before the agent can run shell commands. */
    public static final String READINESS_FIXTURE_DIR = "/opt/hyperion-readiness-fixture";

    /** Wiped and rebuilt per authoritative run, so a previous run's reports can never be mistaken for this one's. */
    static final String REPORTS_DIR = PRISTINE_VERIFY_DIR + "/reports";

    /** Prefix of the liveness line {@code verify.sh} prints; the verdict is read from the collected files, not from this line. */
    static final String COLLECTED_MARKER = "HYPERION_COLLECTED";

    /**
     * Canonical token the collect step appends to every collected JUnit report ({@code 0001__junit.xml}); the verifier routes a file carrying it through
     * {@code TestResultXmlParser}. An SCA report carries its per-tool name ({@code spotbugsXml.xml}, …) as the token instead, so the same directory can hold both.
     */
    public static final String COLLECTED_JUNIT_TOKEN = "junit.xml";

    public static final String COLLECTED_NAME_SEPARATOR = "__";

    /**
     * Report locations covering all shipped languages, scanned in addition to the phase's own {@code resultPaths} so a language whose phase template declares none is still
     * collected from.
     */
    private static final List<String> DEFAULT_REPORT_GLOBS = List.of("target/surefire-reports/*.xml", "target/failsafe-reports/*.xml", "surefire-reports/*.xml",
            "failsafe-reports/*.xml", "test-results/*.xml", "test-results/*/*.xml", "test-reports/*.xml", "test-results.xml");

    // Present only on LocalCI-orchestration nodes. Generation requires a co-located build agent anyway, so absence is reported at call time rather than blocking a core-only
    // node from starting.
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

    public String isolatedSolutionBuildCommand() {
        return behavioralSolutionBuildCommand();
    }

    public String isolatedTemplateBuildCommand() {
        return behavioralTemplateBuildCommand();
    }

    public String behavioralSolutionBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION), "behavior-isolated");
    }

    public String behavioralTemplateBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE), "behavior-isolated");
    }

    public String trustedStructuralSolutionBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION), "trusted-structural");
    }

    public String trustedStructuralTemplateBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE), "trusted-structural");
    }

    public static String reportsDirectoryFor(String assignment) {
        return REPORTS_DIR + "/" + assignment;
    }

    private static String pristineVerifyInvocation(String assignmentDirectory) {
        return "sh " + PRISTINE_VERIFY_PATH + " " + assignmentDirectory;
    }

    private static String laneVerifyInvocation(String assignmentDirectory, String lane) {
        return pristineVerifyInvocation(assignmentDirectory) + " " + lane;
    }

    /**
     * @param exercise the exercise whose per-language build phases the script runs
     * @return the {@code verify.sh} content; the script takes one argument, {@code solution} or {@code template}
     */
    public String verifyScriptContent(ProgrammingExercise exercise) {
        return verifyScriptContent(exercise, false);
    }

    public String readinessVerifyScriptContent(ProgrammingExercise exercise) {
        return verifyScriptContent(exercise, true);
    }

    private String verifyScriptContent(ProgrammingExercise exercise, boolean readinessProbe) {
        BuildRecipe recipe = resolveBuildRecipe(exercise);
        String findExpression = buildFindExpression(recipe.reportGlobs());
        String scaFindExpression = buildScaFindExpression(recipe.scaReportFiles());
        String assignmentDestination = "$BUILD_DIR/" + recipe.assignmentDir();
        String testDestination = recipe.testDir().isEmpty() ? "$BUILD_DIR" : "$BUILD_DIR/" + recipe.testDir();
        String phaseSection = buildPhaseSection(recipe.phases());
        boolean java = exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA;
        String isolatedPhaseSection = java ? buildIsolatedJavaPhaseSection(recipe, exercise) : phaseSection;
        String javaSecurityManagerAllow = java
                ? "export JAVA_TOOL_OPTIONS=\"${JAVA_TOOL_OPTIONS:-} -Djava.security.manager=allow\"\nexport MAVEN_OPTS=\"${MAVEN_OPTS:-} -Djava.security.manager=allow\"\nexport GRADLE_OPTS=\"${GRADLE_OPTS:-} -Djava.security.manager=allow\""
                : ": # no Java/Ares security-manager compatibility flags needed";
        // CI placeholder values for the seeded harness, mapped to the real checkout layout. The assignment build checks out no sibling solution/, so the solution placeholder
        // collapses to the assignment directory.
        String testPlaceholderValue = recipe.testDir().isEmpty() ? "." : recipe.testDir();
        String solutionPlaceholderValue = "assignment";
        String assignmentParentPlaceholderValue = recipe.assignmentDir();
        String solutionCopySection = ": # this language's harness references no sibling solution/";
        String readinessOverlay = readinessProbe ? """
                # Keep the exercise's immutable build harness, but remove every exercise-owned Java source before installing the trusted readiness fixture.
                rm -rf "$TEST_DEST/test" "$TEST_DEST/structural/test" "$TEST_DEST/behavior/test" "$ASSIGNMENT_DEST"
                mkdir -p "$ASSIGNMENT_DEST"
                if [ ! -d "@@READINESS_FIXTURE@@/tests" ] || [ ! -d "@@READINESS_FIXTURE@@/solution" ]; then
                    echo "build-readiness fixture is unavailable" >&2
                    exit 66
                fi
                cp -a "@@READINESS_FIXTURE@@/tests/." "$TEST_DEST"/ || exit 74
                cp -a "@@READINESS_FIXTURE@@/solution/." "$ASSIGNMENT_DEST"/ || exit 74
                find "@@READINESS_FIXTURE@@" -mindepth 1 -delete
                """ : "";
        // Plain POSIX sh, because not every language image ships bash. Rendered by @@TOKEN@@ name rather than positional %s, so substitution is order-independent and a value
        // used twice (the find expression, the JUnit token) is written once.
        String script = """
                #!/bin/sh
                # Generated by Artemis Hyperion. Assembles the CI build layout, runs the exercise's real build phases for one assignment (solution or template), and collects
                # the build-fresh test/SCA reports into a verifier-owned directory. The verdict is NOT decided here: the verifier copies those reports out and parses them.
                ASSIGNMENT="$1"
                if [ "$ASSIGNMENT" != "solution" ] && [ "$ASSIGNMENT" != "template" ]; then
                    echo "usage: verify.sh <solution|template> [behavior-isolated|trusted-structural]" >&2
                    exit 64
                fi
                LANE="$2"
                if [ -n "$LANE" ] && [ "$LANE" != "behavior-isolated" ] && [ "$LANE" != "trusted-structural" ]; then
                    echo "usage: verify.sh <solution|template> [behavior-isolated|trusted-structural]" >&2
                    exit 64
                fi
                WORKSPACE="@@WORKSPACE@@"
                REPORTS_DIR="@@REPORTS_DIR@@/$ASSIGNMENT"
                BUILD_DIR=$(mktemp -d /tmp/hyperion-verify.XXXXXX) || exit 70
                cleanup() {
                    rm -rf "$BUILD_DIR"
                }
                trap cleanup EXIT
                # Materialize the CI checkout layout (-a preserves exec bits and binaries).
                TEST_DEST="@@TEST_DEST@@"
                mkdir -p "$TEST_DEST"
                cp -a "$WORKSPACE/tests/." "$TEST_DEST"/ 2>/dev/null || true
                ASSIGNMENT_DEST="@@ASSIGNMENT_DEST@@"
                mkdir -p "$ASSIGNMENT_DEST"
                cp -a "$WORKSPACE/$ASSIGNMENT/." "$ASSIGNMENT_DEST"/ 2>/dev/null || true
                if [ "$LANE" = "behavior-isolated" ] && [ -d "@@TRUSTED_STRUCTURAL_DIR@@" ]; then
                    TRUSTED_MANIFEST="$BUILD_DIR/.hyperion-trusted-structural-files"
                    ( cd "@@TRUSTED_STRUCTURAL_DIR@@" && find . -type f -print ) > "$TRUSTED_MANIFEST" || exit 74
                    while IFS= read -r trusted; do
                        rm -f "$TEST_DEST/${trusted#./}" 2>/dev/null || exit 74
                    done < "$TRUSTED_MANIFEST"
                elif [ "$LANE" = "trusted-structural" ]; then
                    if [ ! -d "@@TRUSTED_STRUCTURAL_DIR@@" ] || ! find "@@TRUSTED_STRUCTURAL_DIR@@" -type f -print -quit | grep -q .; then
                        echo "trusted structural fixture is unavailable" >&2
                        exit 66
                    fi
                    rm -rf "$TEST_DEST/test" "$TEST_DEST/structural/test" "$TEST_DEST/behavior/test"
                    rm -f "$TEST_DEST/test.json" 2>/dev/null || exit 74
                    cp -a "@@TRUSTED_STRUCTURAL_DIR@@/." "$TEST_DEST"/ || exit 74
                fi
                @@READINESS_OVERLAY@@
                @@SOLUTION_COPY@@
                # The standard Gradle tests scaffold applies the Teamscale coverage-upload plugin, which LocalCI resolves over the network and a generation sandbox cannot.
                # Strip that one declaration from this disposable build-tree copy only; the seeded tests repository stays byte-identical, and the differential verdict does not
                # depend on coverage upload.
                for build_file in "$TEST_DEST/build.gradle" "$TEST_DEST/build.gradle.kts"; do
                    [ -f "$build_file" ] || continue
                    grep -vF "id 'com.teamscale' version" "$build_file" | grep -vF 'id("com.teamscale") version' > "$build_file.hyp" && mv "$build_file.hyp" "$build_file"
                done
                # Substitute the CI directory placeholders inside the COPIED harness with the exercise's real checkout layout, exactly as production exercise creation does, so a
                # seeded harness resolves against THIS build tree without the agent having to edit an immutable file. The seeded sources themselves stay untouched.
                find "$TEST_DEST" -type f 2>/dev/null | while IFS= read -r f; do
                    sed -e 's#${studentWorkingDirectory}#/@@ASSIGNMENT_DIR@@/src#g' \\
                        -e 's#${studentParentWorkingDirectoryName}#@@ASSIGNMENT_PARENT@@#g' \\
                        -e 's#${solutionWorkingDirectory}#@@SOLUTION_DIR@@#g' \\
                        -e 's#${testWorkingDirectory}#@@TEST_DIR@@#g' "$f" > "$f.hyp" 2>/dev/null && mv "$f.hyp" "$f" 2>/dev/null || rm -f "$f.hyp" 2>/dev/null
                done
                # Anti-forgery: delete every pre-existing JUnit report before the phases run (the agent can plant one in tests/ and cp -a preserves its mtime), so only reports
                # written this run are collected. SCA reports are deliberately not deleted here; see buildScaCollectSection.
                find "$BUILD_DIR" -type f \\( @@REPORT_FIND@@ \\) -delete 2>/dev/null || true
                # Reference marker; collection takes only reports NEWER than it, so a planted report that escaped the delete still cannot be collected.
                BUILD_START_MARKER="$BUILD_DIR/.hyperion-build-start"
                : > "$BUILD_START_MARKER"
                @@JAVA_SECURITY_MANAGER_ALLOW@@
                # Run the exercise's real build phases, each from the build root. A non-zero exit (failing tests or a compile error) is expected for the template.
                rc=0
                run_phase() {
                    ( cd "$BUILD_DIR" || exit 70; set -e; eval "$1" )
                    phase_rc=$?
                    if [ "$phase_rc" -ne 0 ] && [ "$rc" -eq 0 ]; then rc=$phase_rc; fi
                }
                if [ "$LANE" = "behavior-isolated" ]; then
                    @@ISOLATED_PHASES@@
                else
                    @@PHASES@@
                fi
                # Collect the build-fresh reports into the verifier-owned REPORTS_DIR, re-seeded empty so a previous run's reports cannot leak in. Each file is renamed to
                # <seq>__<canonical> so the verifier can route it: JUnit reports get the fixed token "@@JUNIT_TOKEN@@", SCA reports keep their per-tool canonical name.
                rm -rf "$REPORTS_DIR" 2>/dev/null || true
                mkdir -p "$REPORTS_DIR" || exit 70
                collected_tests=0
                collected_sca=0
                collect_one() {
                    # cp -P never follows a symlink; combined with the -type f find that produced $2, only a regular file can be collected.
                    seq=$1; src=$2; canonical=$3
                    cp -P "$src" "$REPORTS_DIR/$(printf '%04d' "$seq")@@NAME_SEP@@$canonical" 2>/dev/null || true
                }
                seq=0
                junit_report_list=$(mktemp /tmp/hyperion-junit-reports.XXXXXX) || exit 70
                find "$BUILD_DIR" -type f -newer "$BUILD_START_MARKER" \\( @@REPORT_FIND@@ \\) > "$junit_report_list" 2>/dev/null || true
                while IFS= read -r report; do
                    seq=$((seq + 1)); collect_one "$seq" "$report" "@@JUNIT_TOKEN@@"; collected_tests=$((collected_tests + 1))
                done < "$junit_report_list"
                rm -f "$junit_report_list"
                @@SCA_COLLECT@@
                echo "@@COLLECTED_MARKER@@ tests=$collected_tests sca=$collected_sca exit=$rc"
                exit $rc
                """;
        return script.replace("@@WORKSPACE@@", GenerationWorkspaceService.WORKSPACE).replace("@@REPORTS_DIR@@", REPORTS_DIR).replace("@@ASSIGNMENT_DEST@@", assignmentDestination)
                .replace("@@ASSIGNMENT_DIR@@", recipe.assignmentDir()).replace("@@ASSIGNMENT_PARENT@@", assignmentParentPlaceholderValue).replace("@@TEST_DEST@@", testDestination)
                .replace("@@SOLUTION_COPY@@", solutionCopySection).replace("@@SOLUTION_DIR@@", solutionPlaceholderValue).replace("@@TEST_DIR@@", testPlaceholderValue)
                .replace("@@REPORT_FIND@@", findExpression).replace("@@JAVA_SECURITY_MANAGER_ALLOW@@", javaSecurityManagerAllow).replace("@@PHASES@@", phaseSection)
                .replace("@@ISOLATED_PHASES@@", isolatedPhaseSection).replace("@@SCA_COLLECT@@", buildScaCollectSection(scaFindExpression))
                .replace("@@NAME_SEP@@", COLLECTED_NAME_SEPARATOR).replace("@@JUNIT_TOKEN@@", COLLECTED_JUNIT_TOKEN).replace("@@COLLECTED_MARKER@@", COLLECTED_MARKER)
                .replace("@@READINESS_OVERLAY@@", readinessOverlay).replace("@@READINESS_FIXTURE@@", READINESS_FIXTURE_DIR)
                .replace("@@TRUSTED_STRUCTURAL_DIR@@", TRUSTED_STRUCTURAL_DIR).replace("@@PRISTINE_VERIFY_DIR@@", PRISTINE_VERIFY_DIR);
    }

    /**
     * Renders each build phase as a {@code run_phase '<script>'} call, escaping single quotes so the body reaches {@code eval} verbatim. One phase per call (each re-rooted at the
     * build dir) mirrors how real CI resets the working directory before every phase.
     */
    private static String buildPhaseSection(List<String> phases) {
        return phases.stream().map(phase -> "run_phase '" + singleQuote(phase) + "'").collect(Collectors.joining("\n"));
    }

    /**
     * Compiles Java tests before removing every generated Java source from both the disposable build and live workspace. Surefire then executes only the compiled classes, so a
     * graded test cannot inspect generated source text. Final verification restores the captured workspace after each run.
     */
    private static String buildIsolatedJavaPhaseSection(BuildRecipe recipe, ProgrammingExercise exercise) {
        List<String> setupPhases = new ArrayList<>();
        List<String> staticAnalysisPhases = new ArrayList<>();
        List<MavenTestPhase> testPhases = new ArrayList<>();
        boolean reachedTests = false;
        for (String phase : recipe.phases()) {
            MavenTestPhase split = splitMavenTestPhase(phase);
            if (split != null) {
                reachedTests = true;
                testPhases.add(split);
            }
            else if (MAVEN_STATIC_ANALYSIS_PHASE.matcher(phase).matches()) {
                staticAnalysisPhases.add(phase);
            }
            else if (reachedTests) {
                return "run_phase 'echo \"Source-isolated verification requires compile/setup phases before Maven test phases\" >&2; exit 65'";
            }
            else {
                setupPhases.add(phase);
            }
        }
        if (testPhases.isEmpty()) {
            return "run_phase 'echo \"Source-isolated verification requires a standalone Maven test phase\" >&2; exit 65'";
        }
        String setup = buildPhaseSection(setupPhases);
        String compileTests = testPhases.stream().map(MavenTestPhase::compileTests).map(SandboxBuildCommandService::singleQuote).map(command -> "run_phase '" + command + "'")
                .collect(Collectors.joining("\n"));
        if (Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) && staticAnalysisPhases.isEmpty()) {
            staticAnalysisPhases.add("mvn -B spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd");
        }
        String staticAnalysis = staticAnalysisPhases.isEmpty() ? "" : "\n" + buildPhaseSection(staticAnalysisPhases);
        String executeTests = testPhases.stream().map(MavenTestPhase::executeTests).map(SandboxBuildCommandService::singleQuote).map(command -> "run_phase '" + command + "'")
                .collect(Collectors.joining("\n"));
        return setup + "\n" + compileTests + staticAnalysis + """

                if [ "$rc" -eq 0 ]; then
                    find "$BUILD_DIR" "$WORKSPACE" -type f -name '*.java' -delete 2>/dev/null || exit 74
                """ + executeTests + """

                fi""";
    }

    private static @Nullable MavenTestPhase splitMavenTestPhase(String phase) {
        Matcher matcher = MAVEN_TEST_PHASE.matcher(phase);
        if (!matcher.matches()) {
            return null;
        }
        String commandPrefix = matcher.group(1);
        String beforeGoal = matcher.group(2);
        String afterGoal = matcher.group(3);
        String compileTests = commandPrefix + beforeGoal + "test-compile -DskipTests" + afterGoal;
        String executionPrefix = beforeGoal.replaceAll("(?<![\\w-])clean(?![\\w-])\\s*", "");
        String executeTests = commandPrefix + executionPrefix + "surefire:test" + afterGoal;
        return new MavenTestPhase(compileTests.strip(), executeTests.strip());
    }

    private record MavenTestPhase(String compileTests, String executeTests) {
    }

    /**
     * Every instructor-configurable value interpolated into a single-quoted shell token — a phase body, a report glob, an SCA file name — must go through this, or an embedded
     * quote closes the token and injects shell.
     */
    private static String singleQuote(String value) {
        return value.replace("'", "'\\''");
    }

    /**
     * Each SCA report keeps its canonical per-tool name as the routing token so the verifier's production {@code ReportParser} picks the right parser for it. Unlike the JUnit
     * reports, pre-existing SCA reports are not deleted before the phases run: the {@code -newer} marker is their only guard, which is enough because a surviving forged SCA
     * report can only add issues to the candidate under review, never hide one.
     */
    private static String buildScaCollectSection(String scaFindExpression) {
        if (scaFindExpression.isEmpty()) {
            return "";
        }
        return """
                sca_report_list=$(mktemp /tmp/hyperion-sca-reports.XXXXXX) || exit 70
                find "$BUILD_DIR" -type f -newer "$BUILD_START_MARKER" \\( %s \\) > "$sca_report_list" 2>/dev/null || true
                while IFS= read -r report; do
                    seq=$((seq + 1)); collect_one "$seq" "$report" "$(basename "$report")"; collected_sca=$((collected_sca + 1))
                done < "$sca_report_list"
                rm -f "$sca_report_list"
                """.formatted(scaFindExpression);
    }

    /** Each glob is anchored with a leading wildcard segment because a phase-declared result path is relative to that phase's working directory, not to the build root. */
    private static String buildFindExpression(List<String> reportGlobs) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String glob : reportGlobs) {
            String normalized = glob.trim().replace("**/", "").replace("**", "*");
            while (normalized.startsWith("./") || normalized.startsWith("/")) {
                normalized = normalized.startsWith("./") ? normalized.substring(2) : normalized.substring(1);
            }
            if (!normalized.isBlank()) {
                tokens.add("-path '*/" + singleQuote(normalized) + "'");
            }
        }
        return String.join(" -o ", tokens);
    }

    private static String buildScaFindExpression(List<String> scaReportFiles) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String fileName : scaReportFiles) {
            if (fileName != null && !fileName.isBlank()) {
                tokens.add("-name '" + singleQuote(fileName) + "'");
            }
        }
        return String.join(" -o ", tokens);
    }

    /** Sentinels: an empty {@code testDir} means the tests sit at the build root rather than in a subdirectory, and empty {@code scaReportFiles} means SCA is disabled. */
    private record BuildRecipe(List<String> phases, List<String> reportGlobs, String assignmentDir, String testDir, List<String> scaReportFiles) {
    }

    /**
     * Summary of the resolved build recipe for the agent's system prompt, derived from the same {@link #resolveBuildRecipe} that renders {@code verify.sh} so prompt and grader
     * cannot drift.
     *
     * @param phaseScripts    the placeholder-substituted per-phase commands, run in order from the build root
     * @param reportGlobs     the build-root-relative locations the grader collects test-report XML from
     * @param testCheckoutDir where the tests are checked out ({@code ""} = the build root, not a {@code tests/} subdir)
     * @param scaReportFiles  the canonical SCA report file names the grader parses ({@code empty} = SCA disabled)
     */
    public record BuildContextSummary(List<String> phaseScripts, List<String> reportGlobs, String testCheckoutDir, List<String> scaReportFiles) {
    }

    public BuildContextSummary describeBuildContext(ProgrammingExercise exercise) {
        BuildRecipe recipe = resolveBuildRecipe(exercise);
        return new BuildContextSummary(recipe.phases(), recipe.reportGlobs(), recipe.testDir(), recipe.scaReportFiles());
    }

    /**
     * Resolves the per-language build recipe from the exact LocalCI build phases (matching real CI), applying the same placeholder substitution mapped to the language's checkout
     * layout. Falls back to a conventional Maven build when the phase template cannot be resolved.
     */
    private BuildRecipe resolveBuildRecipe(ProgrammingExercise exercise) {
        String assignmentDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.ASSIGNMENT, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getAssignmentCheckoutPath() : null, "assignment");
        String testDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.TEST, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getTestCheckoutPath() : null, "");

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
            return new BuildRecipe(phaseScripts, reportGlobs, assignmentDir, testDir, scaReportFiles);
        }
        return new BuildRecipe(fallbackBuildPhases(exercise), reportGlobs, assignmentDir, testDir, scaReportFiles);
    }

    /**
     * Safety net for a degraded {@link BuildPhasesTemplateService}, not a design surface for multi-language support: generation only ever runs for configurations
     * {@link de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.LanguageGenerationProfile} admits, so reaching here means something upstream is already broken.
     */
    private static List<String> fallbackBuildPhases(ProgrammingExercise exercise) {
        boolean sequential = exercise.getBuildConfig() != null && exercise.getBuildConfig().hasSequentialTestRuns();
        if (exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA && ProjectType.isMavenProject(exercise.getProjectType())) {
            if (sequential) {
                return List.of("cd structural\nmvn -B clean compile", "cd behavior\nmvn -B clean compile", "cd structural\nmvn -B test", "cd behavior\nmvn -B test");
            }
            return List.of("mvn -B clean compile", "mvn -B test");
        }
        return List.of("""
                if [ -f pom.xml ]; then mvn clean test;
                elif [ -f ./gradlew ]; then chmod +x ./gradlew && ./gradlew clean test --no-daemon;
                elif [ -f build.gradle ]; then gradle clean test --no-daemon;
                else echo 'No recognized build system' >&2; exit 2; fi""");
    }

    private static List<String> scaReportFileNames(ProgrammingExercise exercise) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) || exercise.getProgrammingLanguage() == null) {
            return List.of();
        }
        return StaticCodeAnalysisTool.getToolsForProgrammingLanguage(exercise.getProgrammingLanguage()).stream().map(StaticCodeAnalysisTool::getFileName)
                .filter(name -> name != null && !name.isBlank()).distinct().toList();
    }

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
     * Runs the real-CI placeholder substitution over a phase script. An empty test dir maps to {@code .} so a {@code cd} into the test working directory stays put instead of
     * running with no argument.
     */
    private String substitute(String script, String assignmentDir, String testDir) {
        String testRepo = testDir.isEmpty() ? "." : testDir;
        return buildScriptProviderService.map(service -> service.replacePlaceholders(script, assignmentDir, RepositoryType.SOLUTION.toString(), testRepo)).orElse(script);
    }
}
