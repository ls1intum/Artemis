package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * one method per {@link GenerationStage}: SPEC.md's required sections and evidence gates, one pristine build each for SOLUTION and TEMPLATE, the differential self-check for TESTS,
 * and the
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

    private static final List<String> REQUIRED_SPEC_HEADINGS = List.of("## Rules", "## Worked Examples", "## Design", "## Testing Strategy", "## Diagram");

    /** The only template-status tokens a SPEC.md '## Design' data row may carry; 'student-creates' additionally arms the template omit-gate. */
    private static final Set<String> TEMPLATE_STATUS_TOKENS = Set.of("given", "stubbed", "student-creates");

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
            case SPEC -> checkSpec(sandbox, sessionId);
            case SOLUTION -> checkSolution(sandbox, sessionId, exercise);
            case TEMPLATE -> checkTemplate(sandbox, sessionId, exercise);
            case TESTS -> checkTests(sandbox, sessionId, exercise, seedTestsFiles);
            case STATEMENT -> checkStatement(sandbox, sessionId, lastTestsReport);
        };
    }

    /**
     * The SPEC stage's mechanical floor. Depth is enforced by EVIDENCE, not judgment: the worked-examples table must contain at least two rows whose expected results differ,
     * so the spec proves branching instead of asserting constants — the one mechanically checkable core of the "no seam whose answer is copying a literal" principle. Everything
     * semantic (archetype fit, rule quality) stays advisory with the critic; a deterministic gate must never hold an opinion.
     */
    private StageCheckResult checkSpec(InteractiveSandbox sandbox, String sessionId) {
        String spec = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
        if (spec.isBlank()) {
            return StageCheckResult.failed("SPEC.md is missing or empty. Write /workspace/SPEC.md with the archetype, '## Rules' (numbered R1..Rn), a '## Worked Examples' "
                    + "table, a '## Design' table, a '## Testing Strategy', and a '## Diagram' decision before continuing.");
        }
        List<String> missingSpecSections = REQUIRED_SPEC_HEADINGS.stream().filter(heading -> !spec.contains(heading)).toList();
        if (!missingSpecSections.isEmpty()) {
            return StageCheckResult.failed("SPEC.md is missing required section(s): " + missingSpecSections + ". Add them before continuing.");
        }
        if (ProblemStatementBindingChecker.hasTaskBindings(spec) || spec.contains("@startuml")) {
            return StageCheckResult.failed("SPEC.md must not contain [task] bindings or PlantUML diagrams — those belong to the final statement, written once tests exist. "
                    + "Remove them; keep the spec to rules and worked examples.");
        }
        List<String> expectedResults = workedExampleExpectedValues(spec);
        if (expectedResults.size() < 2) {
            return StageCheckResult.failed("The '## Worked Examples' table needs at least two data rows (| Rules | Input | Expected |). The table is the spec's evidence of "
                    + "real computation; the solution stage replays it.");
        }
        if (Set.copyOf(expectedResults).size() < 2) {
            return StageCheckResult.failed("Every row of the '## Worked Examples' table has the SAME expected result ('" + expectedResults.getFirst() + "'). The table must "
                    + "prove branching: different inputs must lead to different computed results. If the exercise cannot produce two different results, its rules grade a "
                    + "constant — deepen the rules.");
        }
        List<DesignRow> designRows = designTableRows(spec);
        if (designRows.isEmpty()) {
            return StageCheckResult.failed("The '## Design' section has no data rows. It must be a table (| Type | Role | Template status |) listing every type with its "
                    + "Template status: exactly one of 'given', 'stubbed', 'student-creates'.");
        }
        List<String> rowsWithoutStatus = designRows.stream().filter(row -> row.status() == null).map(DesignRow::type).toList();
        if (!rowsWithoutStatus.isEmpty()) {
            return StageCheckResult.failed("These '## Design' rows carry no template-status token: " + rowsWithoutStatus + ". Each row must contain exactly one of "
                    + TEMPLATE_STATUS_TOKENS + " — the token is what the later template gate enforces, so an ambiguous row cannot be checked.");
        }
        // Echo the parsed plan back so the agent SEES what the later gates will hold it to (feedback quality: confirm understanding, not just absence of errors).
        String echo = designRows.stream().map(row -> row.type() + "=" + row.status()).collect(java.util.stream.Collectors.joining(", "));
        return StageCheckResult.passed("Specification accepted. Parsed template plan the later gates will enforce: " + echo + ". A 'student-creates' type must exist in the "
                + "solution and must be ABSENT from the template.");
    }

    /** One parsed data row of SPEC.md's '## Design' table: the type name (first cell, backticks stripped) and its template-status token ({@code null} when the row has none). */
    record DesignRow(String type, @Nullable String status) {
    }

    /** Parses the '## Design' table's data rows (skipping the header and separator), tolerant of column order — the status is found by token, not position. */
    static List<DesignRow> designTableRows(String spec) {
        int start = spec.indexOf("## Design");
        if (start < 0) {
            return List.of();
        }
        List<DesignRow> rows = new java.util.ArrayList<>();
        boolean pastHeader = false;
        for (String line : spec.substring(start).lines().map(String::strip).toList()) {
            if (line.startsWith("## ") && !line.startsWith("## Design")) {
                break;
            }
            if (!line.startsWith("|")) {
                continue;
            }
            if (!pastHeader) {
                // The first two pipe rows are the header and the |---| separator.
                if (line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ')) {
                    pastHeader = true;
                }
                continue;
            }
            List<String> cells = java.util.Arrays.stream(line.split("\\|")).map(String::strip).filter(cell -> !cell.isBlank()).toList();
            if (cells.isEmpty()) {
                continue;
            }
            String type = cells.getFirst().replace("`", "").strip();
            String status = cells.stream().map(cell -> cell.replace("`", "").strip().toLowerCase(java.util.Locale.ROOT)).filter(TEMPLATE_STATUS_TOKENS::contains).findFirst()
                    .orElse(null);
            if (!type.isBlank()) {
                rows.add(new DesignRow(type, status));
            }
        }
        return rows;
    }

    /** The type names SPEC.md's '## Design' table marks {@code student-creates} — the ones the template omit-gate and the solution presence-gate enforce. */
    static List<String> specStudentCreatedTypes(String spec) {
        return designTableRows(spec).stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type).filter(type -> type.matches("[A-Za-z_][A-Za-z0-9_]*"))
                .toList();
    }

    /**
     * The expected-result cells (last column) of every data row in the {@code ## Worked Examples} table: rows starting with '|' after that heading, skipping the header and the
     * separator row.
     */
    static List<String> workedExampleExpectedValues(String spec) {
        int start = spec.indexOf("## Worked Examples");
        if (start < 0) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        boolean pastHeader = false;
        for (String line : spec.substring(start).lines().map(String::strip).toList()) {
            if (line.startsWith("## ") && !line.startsWith("## Worked Examples")) {
                break;
            }
            if (!line.startsWith("|")) {
                continue;
            }
            String[] cells = line.split("\\|");
            String last = cells.length == 0 ? "" : cells[cells.length - 1].strip();
            if (!pastHeader) {
                // The first two pipe rows are the header and the |---| separator.
                if (last.chars().allMatch(c -> c == '-' || c == ':' || c == ' ')) {
                    pastHeader = true;
                }
                continue;
            }
            if (!last.isBlank()) {
                values.add(last);
            }
        }
        return values;
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
        List<String> createdTypes = specStudentCreatedTypes(readSpec(sandbox, sessionId));
        List<String> missingCreatedTypes = createdTypes.stream().filter(type -> findTypeFiles(sandbox, sessionId, "solution", type).isBlank()).toList();
        if (!missingCreatedTypes.isEmpty()) {
            return StageCheckResult.failed("SPEC.md's '## Design' table marks these types 'student-creates', but the solution contains no file for them: " + missingCreatedTypes
                    + ". The reference solution must fully implement every student-created type (it is what the tests grade); add the missing file(s), or correct SPEC.md if "
                    + "the design genuinely changed.");
        }
        return StageCheckResult.passed(createdTypes.isEmpty() ? "" : "Solution compiles and contains every student-created type from the specification: " + createdTypes + ".");
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
                return StageCheckResult.failed("The template is byte-identical to the solution (a degenerate copy). Remove the student work the specification marks 'stubbed' or "
                        + "'student-creates' from the template so it still compiles but no longer matches the solution.");
            }
        }
        catch (RuntimeException e) {
            // Advisory only: a tooling failure here must not block an otherwise sound template.
            log.debug("Degenerate-copy check could not run (fail-open): {}", e.getMessage());
        }
        List<String> createdTypes = specStudentCreatedTypes(readSpec(sandbox, sessionId));
        List<String> leakedFiles = createdTypes.stream().map(type -> findTypeFiles(sandbox, sessionId, "template", type)).filter(found -> !found.isBlank())
                .flatMap(found -> found.lines().map(String::strip)).toList();
        if (!leakedFiles.isEmpty()) {
            return StageCheckResult.failed("SPEC.md's '## Design' table marks type(s) 'student-creates', so the template must NOT contain their files — students create them "
                    + "from scratch (they are graded through the seeded structural checks and reflection-based tests). Delete these template files (leave TODO breadcrumbs in "
                    + "the collaborating classes instead): " + leakedFiles + ". If the type should ship as a stub after all, change its status in SPEC.md to 'stubbed'.");
        }
        if (!createdTypes.isEmpty()) {
            observation = (observation.isBlank() ? "" : observation + " ") + "Confirmed absent from the template, as the specification requires students to create them: "
                    + createdTypes + ".";
        }
        return StageCheckResult.passed(observation);
    }

    private String readSpec(InteractiveSandbox sandbox, String sessionId) {
        return execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
    }

    /** The files under the given repo whose basename is {@code <type>.<ext>}, excluding build output. Type names were validated as identifiers, and exec spawns without a shell. */
    private String findTypeFiles(InteractiveSandbox sandbox, String sessionId, String repo, String type) {
        return execRead(sandbox, sessionId, "find", GenerationWorkspaceService.WORKSPACE + "/" + repo, "-type", "f", "-name", type + ".*", "-not", "-path", "*/target/*", "-not",
                "-path", "*/build/*");
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
        if (!report.solutionPassed() || !report.templateFailed()) {
            return new StageCheckResult(false, "The tests do not yet satisfy the differential requirement (the solution must pass every test, the template must fail every "
                    + "task-bound behavioural test):\n" + observation, report);
        }
        // Only once the differential is green does the grading plan matter — a missing plan must never drown out failing tests in the feedback.
        String planJson = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
        if (planJson.isBlank()) {
            return new StageCheckResult(false,
                    "The differential passed, but /workspace/test-plan.json is missing. Write it now, implementing the specification's Testing "
                            + "Strategy: {\"tests\":[{\"name\":\"<exact test name>\",\"weight\":1..3,\"visibility\":\"ALWAYS\"|\"AFTER_DUE_DATE\"}]} — use the exact names verify "
                            + "reported: " + report.exactTestNames() + ".",
                    report);
        }
        GeneratedTestPlan plan;
        try {
            plan = GeneratedTestPlan.parse(planJson);
        }
        catch (IllegalArgumentException e) {
            return new StageCheckResult(false, "The differential passed, but test-plan.json is invalid: " + e.getMessage(), report);
        }
        Set<String> knownNames = Set.copyOf(report.exactTestNames());
        List<String> unknownPlanNames = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(name -> !knownNames.contains(name)).toList();
        if (!unknownPlanNames.isEmpty()) {
            return new StageCheckResult(false, "The differential passed, but test-plan.json names tests that do not exist: " + unknownPlanNames
                    + ". Copy the exact names verify reported: " + report.exactTestNames() + ".", report);
        }
        List<String> unplannedNames = report.exactTestNames().stream().filter(name -> plan.tests().stream().noneMatch(entry -> entry.name().equals(name))).toList();
        String planSummary = "Grading plan accepted: " + plan.tests().size() + " test(s), " + plan.hiddenEntries().size() + " hidden until the due date."
                + (unplannedNames.isEmpty() ? "" : " Not in the plan (they keep Artemis defaults, weight 1 and visible): " + unplannedNames + ".");
        return new StageCheckResult(true, observation + "\n" + planSummary, report);
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
            List<String> deadDiagramLinks = ProblemStatementBindingChecker.unresolvedTestsColorNames(statement, exactTestNames, Set.of());
            if (!deadDiagramLinks.isEmpty()) {
                return StageCheckResult.failed("These diagram testsColor(...) names match no actual test: " + deadDiagramLinks
                        + ". Use the exact test names from the TESTS stage (behavioural or seeded structural), or remove the link: " + exactTestNames + ".");
            }
        }
        if (ProblemStatementBindingChecker.hasStrayPlantUmlDirectives(statement)) {
            return StageCheckResult.failed("PlantUML directives ('hide empty fields', 'hide empty methods', 'skinparam ...') sit OUTSIDE the @startuml...@enduml block, where "
                    + "Artemis renders them as stray text. Move them inside the block, directly before @enduml.");
        }
        List<String> duplicateTaskTitles = ProblemStatementBindingChecker.duplicateTaskTitles(statement);
        if (!duplicateTaskTitles.isEmpty()) {
            return StageCheckResult.failed("Multiple [task] lines share the same title: " + duplicateTaskTitles
                    + ". A title identifies ONE student work seam — merge each duplicated group into a single [task] line binding all of its tests, "
                    + "followed by one or two imperative sentences describing the work.");
        }
        if (ProblemStatementBindingChecker.writesAboutStudentsInThirdPerson(statement)) {
            return StageCheckResult.failed("The statement writes ABOUT students in the third person ('Students must/will/should ...'). Address the reader directly instead: "
                    + "frame the goal as \"we\" and the work as \"you\" with imperative tasks ('Define ...', 'Implement ...').");
        }
        String specDocument = readSpec(sandbox, sessionId);
        if (ProblemStatementBindingChecker.designSaysDiagramYes(specDocument) && !statement.contains("@startuml")) {
            return StageCheckResult.failed("SPEC.md's '## Diagram' section says yes, but the statement contains no @startuml diagram. Add the PlantUML class diagram after "
                    + "the tasks it illustrates (with testsColor links), or update SPEC.md's Diagram decision if the design genuinely changed.");
        }
        // Exact duplicate headings are a mechanical statement defect (observed shipping live: the same '### 1. ...' section twice); catching it here costs nothing.
        List<String> duplicateHeadings = ProblemStatementBindingChecker.duplicateHeadings(statement);
        if (!duplicateHeadings.isEmpty()) {
            return StageCheckResult.failed("The statement repeats these headings verbatim: " + duplicateHeadings + ". Merge or remove the duplicate sections.");
        }
        return StageCheckResult.passed("");
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
