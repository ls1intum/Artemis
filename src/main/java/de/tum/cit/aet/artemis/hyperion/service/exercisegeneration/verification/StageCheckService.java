package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * The mechanical per-stage gates enforced by the staged generation workflow
 * ({@code de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.StagedGenerationRunner}),
 * one method per {@link GenerationStage}: DESIGN.md's required sections, one pristine build each for SOLUTION and TEMPLATE, the differential self-check for TESTS, and the
 * problem-statement task-binding resolution for STATEMENT. The runner owns stage sequencing, turn budgets, and re-entry; this service owns only "does this stage's artifact pass".
 * <p>
 * The SOLUTION/TEMPLATE compile gates run a single build via {@link DifferentialVerificationService#singleBuild}, which shares its build-and-parse machinery with the full
 * differential ({@code runDifferential}) so a stage gate and the eventual TESTS/post-loop differential can never disagree about whether an assignment compiled.
 * <p>
 * <b>The "compiled" definition fix:</b> {@code verify.sh} exits non-zero both for a genuine compile failure and for failing tests once tests exist. A template that correctly fails
 * its behavioural tests — the entire point of a template — must not be misreported as "does not compile" just because its exit code is non-zero. Both compile gates therefore judge
 * "compiled" as {@code testsRun > 0 || exitCode == 0} (see {@link SingleBuildResult#compiled()}), never {@code exitCode == 0} alone: once tests exist, failing tests on the
 * solution
 * is a differential-quality problem to report by name (not a compile error), and failing tests on the template is the expected, healthy outcome.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class StageCheckService {

    private static final Logger log = LoggerFactory.getLogger(StageCheckService.class);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration DIFF_TIMEOUT = Duration.ofMinutes(5);

    private static final List<String> REQUIRED_DESIGN_HEADINGS = List.of("## Classes", "## Public API", "## Tasks", "## Diagram");

    /** Bound on how many extracted build-error lines a compile-failure observation carries, so a noisy build log cannot flood the agent's context. */
    private static final int MAX_ERROR_LINES = 15;

    private final DifferentialVerificationService verifier;

    public StageCheckService(DifferentialVerificationService verifier) {
        this.verifier = verifier;
    }

    /**
     * Checks one stage's artifact against its mechanical gate.
     *
     * @param stage           the stage whose artifact is being checked
     * @param sandbox         the open sandbox session
     * @param sessionId       the sandbox session id
     * @param exercise        the exercise being generated (drives the per-language build recipe)
     * @param seedTestsFiles  the tests-repository snapshot taken before generation, forwarded to the TESTS stage's differential self-check
     * @param lastTestsReport the TESTS stage's {@link AgentVerifyReport}, consumed by the STATEMENT stage to resolve {@code [task]} bindings against exact test names;
     *                            {@code null} before TESTS has run (or when TESTS never returned a report)
     * @return the gate's pass/fail verdict, an actionable observation, and — for TESTS only — the full {@link AgentVerifyReport}
     */
    public StageCheckResult check(GenerationStage stage, InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            @Nullable AgentVerifyReport lastTestsReport) {
        return switch (stage) {
            case DESIGN -> checkDesign(sandbox, sessionId);
            case SOLUTION -> checkSolution(sandbox, sessionId, exercise);
            case TEMPLATE -> checkTemplate(sandbox, sessionId, exercise);
            case TESTS -> checkTests(sandbox, sessionId, exercise, seedTestsFiles);
            case STATEMENT -> checkStatement(sandbox, sessionId, lastTestsReport);
        };
    }

    private StageCheckResult checkDesign(InteractiveSandbox sandbox, String sessionId) {
        String designDocument = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/DESIGN.md");
        if (designDocument.isBlank()) {
            return StageCheckResult.failed(
                    "DESIGN.md is missing or empty. Write /workspace/DESIGN.md with the required '## Classes', '## Public API', and '## Tasks' sections before continuing.");
        }
        List<String> missing = REQUIRED_DESIGN_HEADINGS.stream().filter(heading -> !designDocument.contains(heading)).toList();
        if (!missing.isEmpty()) {
            return StageCheckResult.failed("DESIGN.md is missing required section(s): " + missing + ". Add them before continuing.");
        }
        return StageCheckResult.passed("");
    }

    private StageCheckResult checkSolution(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        SingleBuildResult result;
        try {
            result = verifier.singleBuild(sandbox, sessionId, exercise, "solution");
        }
        catch (RuntimeException e) {
            return StageCheckResult.failed("Could not run the reference solution compile check: " + e.getMessage());
        }
        if (!result.compiled()) {
            return StageCheckResult.failed("The reference solution does not compile:\n" + extractErrorLines(result.boundedLog()));
        }
        if (result.testsRun() > 0 && result.failures() > 0) {
            return StageCheckResult
                    .failed("The solution must pass every test; failing: " + result.failedTestNames() + ". This is not a compile error — fix the solution's behaviour.");
        }
        return StageCheckResult.passed("");
    }

    private StageCheckResult checkTemplate(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        SingleBuildResult result;
        try {
            result = verifier.singleBuild(sandbox, sessionId, exercise, "template");
        }
        catch (RuntimeException e) {
            return StageCheckResult.failed("Could not run the template compile check: " + e.getMessage());
        }
        if (!result.compiled()) {
            return StageCheckResult.failed("The template does not compile:\n" + extractErrorLines(result.boundedLog()));
        }
        String observation = result.testsRun() > 0 && result.failures() > 0 ? "Template compiles. Template correctly failing " + result.failures() + " of " + result.testsRun()
                + " tests (expected — the template must not implement the " + "required behaviour)." : "";
        try {
            SandboxExecResult diff = sandbox.exec(sessionId, DIFF_TIMEOUT, "diff", "-rq", GenerationWorkspaceService.WORKSPACE + "/solution",
                    GenerationWorkspaceService.WORKSPACE + "/template");
            if (!diff.timedOut() && diff.exitCode() == 0) {
                return StageCheckResult.failed("The template is byte-identical to the solution (a degenerate copy). Remove the student work DESIGN.md marks stubbed or absent from "
                        + "the template so it still compiles but no longer matches the solution.");
            }
        }
        catch (RuntimeException e) {
            // Advisory only: a tooling failure here must not block an otherwise sound template.
            log.debug("Degenerate-copy check could not run (fail-open): {}", e.getMessage());
        }
        return StageCheckResult.passed(observation);
    }

    private StageCheckResult checkTests(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles) {
        AgentVerifyReport report;
        try {
            report = verifier.selfCheck(sandbox, sessionId, exercise, seedTestsFiles, false);
        }
        catch (RuntimeException e) {
            return new StageCheckResult(false, "Could not run the differential self-check: " + e.getMessage(), null);
        }
        String observation = report.toObservation();
        if (report.solutionPassed() && report.templateFailed()) {
            return new StageCheckResult(true, observation, report);
        }
        return new StageCheckResult(false, "The tests do not yet satisfy the differential requirement (the solution must pass every test, the template must fail every "
                + "task-bound behavioural test):\n" + observation, report);
    }

    private StageCheckResult checkStatement(InteractiveSandbox sandbox, String sessionId, @Nullable AgentVerifyReport lastTestsReport) {
        String statement = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        if (statement.isBlank()) {
            return StageCheckResult.failed("problem-statement.md is missing or empty. Write the student-facing problem statement before submitting.");
        }
        if (lastTestsReport != null) {
            List<String> exactTestNames = lastTestsReport.exactTestNames();
            List<String> unresolved = ProblemStatementBindingChecker.unresolvedTaskBindings(statement, exactTestNames, exactTestNames.size(), Set.of());
            if (!unresolved.isEmpty()) {
                return StageCheckResult.failed("These [task] bindings reference names that match no actual test: " + unresolved
                        + ". A [task]'s parenthesised names must be the exact test name(s) from the TESTS stage, copied verbatim: " + exactTestNames + ".");
            }
            // Diagram testsColor links are interactive in Artemis (they render pass/fail per element); a name that matches no test is a silently dead link the student can
            // never satisfy, so it is held to the same resolution standard as a [task] binding.
            List<String> deadDiagramLinks = unresolvedTestsColorNames(statement, exactTestNames);
            if (!deadDiagramLinks.isEmpty()) {
                return StageCheckResult.failed("These diagram testsColor(...) names match no actual test: " + deadDiagramLinks
                        + ". Use the exact test names from the TESTS stage (behavioural or seeded structural), or remove the link: " + exactTestNames + ".");
            }
        }
        if (hasStrayPlantUmlDirectives(statement)) {
            return StageCheckResult.failed("PlantUML directives ('hide empty fields', 'hide empty methods', 'skinparam ...') sit OUTSIDE the @startuml...@enduml block, where "
                    + "Artemis renders them as stray text. Move them inside the block, directly before @enduml.");
        }
        // Exact duplicate headings are a mechanical statement defect (observed shipping live: the same '### 1. ...' section twice); catching it here costs nothing.
        List<String> duplicateHeadings = statement.lines().map(String::strip).filter(line -> line.startsWith("#")).collect(Collectors.groupingBy(line -> line)).entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1).map(Map.Entry::getKey).sorted().toList();
        if (!duplicateHeadings.isEmpty()) {
            return StageCheckResult.failed("The statement repeats these headings verbatim: " + duplicateHeadings + ". Merge or remove the duplicate sections.");
        }
        return StageCheckResult.passed("");
    }

    /**
     * Matches every {@code testsColor(NAME)} occurrence in a statement's PlantUML diagram (both the {@code <color:...>} member form and the {@code #testsColor(...)} edge form).
     */
    private static final Pattern TESTS_COLOR_NAME = Pattern.compile("testsColor\\(([^)]+?)(?:\\(\\))?\\)");

    /**
     * Whether a PlantUML rendering directive appears outside every {@code @startuml}...{@code @enduml} block — Artemis renders such a line as stray statement text
     * (observed live: {@code hide empty fields} printed after the diagram).
     */
    static boolean hasStrayPlantUmlDirectives(String statement) {
        boolean insideDiagram = false;
        for (String line : statement.lines().map(String::strip).toList()) {
            if (line.startsWith("@startuml")) {
                insideDiagram = true;
            }
            else if (line.startsWith("@enduml")) {
                insideDiagram = false;
            }
            else if (!insideDiagram && (line.startsWith("hide empty") || line.startsWith("skinparam "))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every distinct {@code testsColor} name in the statement that matches no known test name. A trailing {@code ()} (the classic Artemis statement style for behavioural
     * names) is tolerated on the statement side; the comparison itself is exact.
     */
    static List<String> unresolvedTestsColorNames(String statement, List<String> exactTestNames) {
        Set<String> known = Set.copyOf(exactTestNames);
        return TESTS_COLOR_NAME.matcher(statement).results().map(match -> match.group(1).strip()).distinct().filter(name -> !known.contains(name)).sorted().toList();
    }

    /**
     * Extracts up to {@link #MAX_ERROR_LINES} compiler-error lines from a build log for a bounded, actionable compile-failure observation; falls back to the full (already
     * bounded) log when no such line is found.
     */
    private static String extractErrorLines(String boundedLog) {
        if (boundedLog == null || boundedLog.isBlank()) {
            return "[no build output]";
        }
        List<String> errorLines = boundedLog.lines().filter(line -> line.contains("[ERROR]") || line.contains("error:")).limit(MAX_ERROR_LINES).toList();
        return errorLines.isEmpty() ? boundedLog : String.join("\n", errorLines);
    }

    private String execRead(InteractiveSandbox sandbox, String sessionId, String... command) {
        try {
            SandboxExecResult result = sandbox.exec(sessionId, READ_TIMEOUT, command);
            return result.isSuccess() && result.stdout() != null ? result.stdout() : "";
        }
        catch (RuntimeException e) {
            log.debug("Staged generation read failed ({}): {}", String.join(" ", command), e.getMessage());
            return "";
        }
    }
}
