package de.tum.cit.aet.artemis.hyperionworker.generation.workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;

/**
 * Renders the canonical Java Gradle LocalCI phases for the agent and verifier; a missing recipe fails closed.
 * Each invocation creates a fresh build tree and collects reports for the production Java parsers; shell output does not determine the verdict.
 */
public class SandboxBuildCommandService {

    private static final Pattern GRADLE_TEST_TASK = Pattern.compile("(?<![\\w-])(test|structuralTests|behaviorTests)(?![\\w-])");

    public static final String VERIFY_SCRIPT_NAME = "verify.sh";

    /** Verifier-owned location outside the agent workspace. */
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
     * the shared production JUnit parser.
     */
    public static final String COLLECTED_JUNIT_TOKEN = "junit.xml";

    public static final String COLLECTED_NAME_SEPARATOR = "__";

    private final BuildRecipe recipe;

    public SandboxBuildCommandService(GenerationResources resources) {
        try (var input = resources.getResource(Path.of("templates/phases/java/plain_gradle.yaml")).getInputStream()) {
            var phases = new ObjectMapper(new YAMLFactory()).readTree(input);
            List<String> scripts = new ArrayList<>();
            List<String> reports = new ArrayList<>();
            for (var phase : phases) {
                scripts.add(phase.required("script").asText());
                for (var report : phase.path("resultPaths")) {
                    reports.add(report.asText());
                }
            }
            if (scripts.isEmpty() || reports.isEmpty()) {
                throw new IllegalStateException("Canonical Gradle build recipe is incomplete");
            }
            recipe = new BuildRecipe(List.copyOf(scripts), List.copyOf(reports), "assignment", "");
        }
        catch (IOException e) {
            throw new IllegalStateException("Canonical Gradle build recipe is unavailable", e);
        }
    }

    public String pristineSolutionBuildCommand() {
        return pristineVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryRole.SOLUTION));
    }

    public String pristineTemplateBuildCommand() {
        return pristineVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryRole.TEMPLATE));
    }

    public String behavioralSolutionBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryRole.SOLUTION), "behavior-isolated");
    }

    public String behavioralTemplateBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryRole.TEMPLATE), "behavior-isolated");
    }

    public String trustedStructuralSolutionBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryRole.SOLUTION), "trusted-structural");
    }

    public String trustedStructuralTemplateBuildCommand() {
        return laneVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryRole.TEMPLATE), "trusted-structural");
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

    public String verifyScriptContent(GenerationInput exercise) {
        return verifyScriptContent(exercise, false);
    }

    public String readinessVerifyScriptContent(GenerationInput exercise) {
        return verifyScriptContent(exercise, true);
    }

    private String verifyScriptContent(GenerationInput exercise, boolean readinessProbe) {
        String findExpression = buildFindExpression(recipe.reportGlobs());
        String assignmentDestination = "$BUILD_DIR/" + recipe.assignmentDir();
        String testDestination = recipe.testDir().isEmpty() ? "$BUILD_DIR" : "$BUILD_DIR/" + recipe.testDir();
        String phaseSection = buildPhaseSection(recipe.phases());
        String isolatedPhaseSection = buildIsolatedGradlePhaseSection(recipe);
        // Each lane replaces the disposable Gradle cache. A persistent daemon would retain its deleted JARs in bounded tmpfs.
        String javaSecurityManagerAllow = "export JAVA_TOOL_OPTIONS=\"${JAVA_TOOL_OPTIONS:-} -Djava.security.manager=allow\"\n"
                + "export GRADLE_OPTS=\"${GRADLE_OPTS:-} -Djava.security.manager=allow -Dorg.gradle.daemon=false\"";
        // The assignment build checks out no sibling solution/, so the solution placeholder collapses to the assignment directory.
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
        // Plain POSIX sh, because not every language image ships bash. Rendered by @@TOKEN@@ name rather than positional %s, so substitution is order-independent.
        String script = """
                #!/bin/sh
                # Generated by Artemis Hyperion. Assembles the CI build layout, runs the exercise's real build phases for one assignment (solution or template), and collects
                # the build-fresh JUnit reports into a verifier-owned directory. The verdict is NOT decided here: the verifier copies those reports out and parses them.
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
                GRADLE_WRAPPERS=$(find "$BUILD_DIR" -type f -name gradlew -print 2>/dev/null)
                if [ -n "$GRADLE_WRAPPERS" ]; then
                    # The sandbox root filesystem is read-only. Ensure the disposable wrapper is executable and copy
                    # the image's offline Gradle home into private writable storage before any configured phase runs.
                    printf '%s\n' "$GRADLE_WRAPPERS" | while IFS= read -r wrapper; do chmod +x "$wrapper" || exit 74; done
                    export GRADLE_USER_HOME=/tmp/hyperion-gradle-home
                    rm -rf "$GRADLE_USER_HOME"
                    mkdir -p "$GRADLE_USER_HOME"
                    cp -a /root/.gradle/. "$GRADLE_USER_HOME"/ || exit 74
                fi
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
                # Substitute the CI directory placeholders inside the COPIED harness with the exercise's real checkout layout, exactly as production exercise creation does, so a
                # seeded harness resolves against THIS build tree without the agent having to edit an immutable file. The seeded sources themselves stay untouched.
                find "$TEST_DEST" -type f 2>/dev/null | while IFS= read -r f; do
                    sed -e 's#${studentWorkingDirectory}#/@@ASSIGNMENT_DIR@@/src#g' \\
                        -e 's#${studentParentWorkingDirectoryName}#@@ASSIGNMENT_PARENT@@#g' \\
                        -e 's#${solutionWorkingDirectory}#@@SOLUTION_DIR@@#g' \\
                        -e 's#${testWorkingDirectory}#@@TEST_DIR@@#g' "$f" > "$f.hyp" 2>/dev/null && mv "$f.hyp" "$f" 2>/dev/null || rm -f "$f.hyp" 2>/dev/null
                done
                find "$BUILD_DIR" -type f -name gradlew -exec chmod +x {} \\; 2>/dev/null || exit 74
                # Anti-forgery: delete every pre-existing JUnit report before the phases run (the agent can plant one in tests/ and cp -a preserves its mtime), so only reports
                # written this run are collected.
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
                # <seq>__<canonical>, with the fixed JUnit token "@@JUNIT_TOKEN@@".
                rm -rf "$REPORTS_DIR" 2>/dev/null || true
                mkdir -p "$REPORTS_DIR" || exit 70
                collected_tests=0
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
                echo "@@COLLECTED_MARKER@@ tests=$collected_tests exit=$rc"
                exit $rc
                """;
        return script.replace("@@WORKSPACE@@", GenerationWorkspaceService.WORKSPACE).replace("@@REPORTS_DIR@@", REPORTS_DIR).replace("@@ASSIGNMENT_DEST@@", assignmentDestination)
                .replace("@@ASSIGNMENT_DIR@@", recipe.assignmentDir()).replace("@@ASSIGNMENT_PARENT@@", assignmentParentPlaceholderValue).replace("@@TEST_DEST@@", testDestination)
                .replace("@@SOLUTION_COPY@@", solutionCopySection).replace("@@SOLUTION_DIR@@", solutionPlaceholderValue).replace("@@TEST_DIR@@", testPlaceholderValue)
                .replace("@@REPORT_FIND@@", findExpression).replace("@@JAVA_SECURITY_MANAGER_ALLOW@@", javaSecurityManagerAllow).replace("@@PHASES@@", phaseSection)
                .replace("@@ISOLATED_PHASES@@", isolatedPhaseSection).replace("@@NAME_SEP@@", COLLECTED_NAME_SEPARATOR).replace("@@JUNIT_TOKEN@@", COLLECTED_JUNIT_TOKEN)
                .replace("@@COLLECTED_MARKER@@", COLLECTED_MARKER).replace("@@READINESS_OVERLAY@@", readinessOverlay).replace("@@READINESS_FIXTURE@@", READINESS_FIXTURE_DIR)
                .replace("@@TRUSTED_STRUCTURAL_DIR@@", TRUSTED_STRUCTURAL_DIR).replace("@@PRISTINE_VERIFY_DIR@@", PRISTINE_VERIFY_DIR);
    }

    /**
     * Escapes single quotes so the body reaches {@code eval} verbatim. One phase per {@code run_phase} call, each re-rooted at the build dir, mirrors how real CI resets the
     * working directory before every phase.
     */
    private static String buildPhaseSection(List<String> phases) {
        return phases.stream().map(phase -> "run_phase '" + singleQuote(phase) + "'").collect(Collectors.joining("\n"));
    }

    /**
     * Precompiles every Gradle test source set before generated sources are removed, then runs the already compiled tests
     * with their producer tasks excluded. Generated behavioral tests cannot read the authored source files.
     */
    private static String buildIsolatedGradlePhaseSection(BuildRecipe recipe) {
        List<String> setupPhases = new ArrayList<>();
        List<String> compilePhases = new ArrayList<>();
        List<String> executePhases = new ArrayList<>();
        boolean reachedTests = false;
        for (String phase : recipe.phases()) {
            Matcher matcher = GRADLE_TEST_TASK.matcher(phase);
            if (!phase.contains("gradlew") || !matcher.find()) {
                if (reachedTests) {
                    return "run_phase 'echo \"Source-isolated verification requires compile/setup phases before Gradle test phases\" >&2; exit 65'";
                }
                setupPhases.add(phase);
                continue;
            }
            reachedTests = true;
            String task = matcher.group(1);
            String classesTask = task.equals("test") ? "testClasses" : task.substring(0, task.length() - "Tests".length()) + "TestClasses";
            String compile = matcher.replaceFirst(classesTask);
            compilePhases.add(compile);
            String execute = matcher.replaceFirst(task).replaceFirst("(?<![\\w-])clean(?![\\w-])\\s*", "") + gradleProducerExclusions(task);
            executePhases.add(execute.strip());
        }
        if (executePhases.isEmpty()) {
            return "run_phase 'echo \"Source-isolated verification requires a standalone Gradle test phase\" >&2; exit 65'";
        }
        return buildPhaseSection(setupPhases) + "\n" + buildPhaseSection(compilePhases) + """

                if [ "$rc" -eq 0 ]; then
                    find "$BUILD_DIR" "$WORKSPACE" -type f -name '*.java' -delete 2>/dev/null || exit 74
                """ + buildPhaseSection(executePhases) + """

                fi""";
    }

    private static String gradleProducerExclusions(String task) {
        String sourceSet = task.equals("test") ? "Test" : Character.toUpperCase(task.charAt(0)) + task.substring(1, task.length() - "Tests".length()) + "Test";
        return " -x compileJava -x processResources -x compile" + sourceSet + "Java -x process" + sourceSet + "Resources -x " + decapitalize(sourceSet) + "Classes";
    }

    private static String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Every instructor-configurable value interpolated into a single-quoted shell token — a phase body, a report glob, an SCA file name — must go through this, or an embedded
     * quote closes the token and injects shell.
     */
    private static String singleQuote(String value) {
        return value.replace("'", "'\\''");
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

    private record BuildRecipe(List<String> phases, List<String> reportGlobs, String assignmentDir, String testDir) {
    }

    /** Canonical Gradle commands and report paths, also exposed in the agent's build instructions. */
    public record BuildContextSummary(List<String> phaseScripts, List<String> reportGlobs, String testCheckoutDir) {
    }

    public BuildContextSummary describeBuildContext(GenerationInput exercise) {
        return new BuildContextSummary(recipe.phases(), recipe.reportGlobs(), recipe.testDir());
    }
}
