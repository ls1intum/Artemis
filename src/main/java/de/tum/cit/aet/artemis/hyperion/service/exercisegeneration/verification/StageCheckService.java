package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ApprovedSpecRegistry approvedSpecs;

    // @Autowired disambiguates from the package-private test constructor; with two constructors and no annotation Spring cannot instantiate the bean.
    @Autowired
    public StageCheckService(DifferentialVerificationService verifier, ApprovedSpecRegistry approvedSpecs) {
        this.verifier = verifier;
        this.approvedSpecs = approvedSpecs;
    }

    /**
     * Rejects a workspace mutation that would undo an already approved ownership decision. This runs at the write boundary rather than waiting for the next build: once SPEC.md
     * passes its semantic and mechanical reviews it is the downstream contract, and a type assigned to the student cannot be restored to the template to make direct-reference
     * tests compile. Executable artifacts remain writable so later stages can still repair real implementation defects.
     *
     * @param sessionId sandbox session whose approved specification is authoritative
     * @param path      workspace-relative target path
     * @param content   complete prospective file content (empty for deletion)
     * @return an actionable rejection, or empty when the mutation preserves the approved contract
     */
    public Optional<String> validateArtifactWrite(String sessionId, String path, String content) {
        Optional<String> approved = approvedSpecs.approved(sessionId);
        if (approved.isEmpty()) {
            return Optional.empty();
        }
        if ("SPEC.md".equals(path)) {
            return Optional.of("ERROR: SPEC.md is read-only after its specification gate. Repair the solution, template, tests, or statement against the approved contract; "
                    + "do not rewrite the contract to fit a downstream artifact.");
        }
        if (!path.startsWith("template/")) {
            return Optional.empty();
        }
        List<String> restoredTypes = specStudentCreatedTypes(approved.get()).stream().filter(type -> ExerciseIntegrityGate.pathOrContentRepresentsType(path, content, type))
                .toList();
        if (restoredTypes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("ERROR: the approved specification assigns these types to students to create: " + restoredTypes
                + ". Do not restore or pre-create their declarations in the template. Keep TODO seam breadcrumbs in the collaborating scaffold and test the student-created "
                + "types through Class.forName/reflection (and a dynamic proxy where the context needs an interface instance), as shown by the seeded test utilities.");
    }

    /**
     * Restores the frozen specification after a shell command if the command changed it outside the guarded file tools.
     *
     * @param sandbox   active generation sandbox
     * @param sessionId sandbox session whose approved specification is authoritative
     * @return an actionable restoration message, or empty when the specification stayed unchanged
     */
    public Optional<String> restoreApprovedSpecAfterCommand(InteractiveSandbox sandbox, String sessionId) {
        Optional<String> approved = approvedSpecs.approved(sessionId);
        if (approved.isEmpty() || approved.get().equals(readSpec(sandbox, sessionId))) {
            return Optional.empty();
        }
        String encoded = Base64.getEncoder().encodeToString(approved.get().getBytes(StandardCharsets.UTF_8));
        SandboxExecResult restore = sandbox.exec(sessionId, READ_TIMEOUT, "sh", "-c",
                "echo '" + encoded + "' | base64 -d > '" + GenerationWorkspaceService.WORKSPACE + "/SPEC.md'");
        if (!restore.isSuccess()) {
            throw new IllegalStateException("A shell command changed the approved specification and it could not be restored");
        }
        return Optional.of("ERROR: the shell command changed read-only SPEC.md. Artemis restored the approved specification. Use bash only for inspection and temporary "
                + "calculations outside /workspace; edit executable artifacts through write_file/edit_file.");
    }

    /**
     * Detects an out-of-band shell mutation that introduced a type the frozen contract assigns students to create.
     *
     * @param sandbox   active generation sandbox
     * @param sessionId sandbox session whose approved specification is authoritative
     * @return an actionable ownership message, or empty when the template still honours the approved ownership
     */
    public Optional<String> approvedOwnershipViolationAfterCommand(InteractiveSandbox sandbox, String sessionId) {
        List<String> violations = approvedSpecs.approved(sessionId).map(StageCheckService::specStudentCreatedTypes).orElse(List.of()).stream()
                .map(type -> findTypeDeclarations(sandbox, sessionId, "template", type)).filter(found -> !found.isBlank()).flatMap(String::lines).map(String::strip).distinct()
                .toList();
        if (violations.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("ERROR: the shell command introduced template artifacts for types the approved specification assigns students to create: " + violations
                + ". Remove those artifacts with delete_file/edit_file. Test the omitted types through reflection and a dynamic proxy where needed; do not rewrite SPEC.md.");
    }

    /** Test constructor: no approved-spec registry, so every check reads the live specification exactly as it did before the registry existed. */
    StageCheckService(DifferentialVerificationService verifier) {
        this(verifier, new ApprovedSpecRegistry());
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
        // SPEC.md is read-only after approval through supported tools. Re-running its cheap mechanical gate is defense in depth against an out-of-band shell mutation; without
        // it, a later stage could append [task] bindings or empty the Diagram decision and silently disarm checks derived from the workspace copy. Skipped when there is no
        // SPEC.md at all: the SPEC stage does not run when the instructor's own problem statement IS the specification.
        if (stage != GenerationStage.SPEC && approvedSpecs.approved(sessionId).isEmpty() && !readSpec(sandbox, sessionId).isBlank()) {
            StageCheckResult specStillValid = checkSpec(sandbox, sessionId);
            if (!specStillValid.passed()) {
                return StageCheckResult.failed("SPEC.md is no longer a valid specification: " + specStillValid.observation()
                        + " The approved specification is read-only; do not mutate files through bash. Task bindings and PlantUML belong in problem-statement.md, never in SPEC.md.");
            }
        }
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
        List<String> unenforceableCreatedTypes = designRows.stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type)
                .filter(type -> !isEnforceableTypeName(type)).toList();
        if (!unenforceableCreatedTypes.isEmpty()) {
            return StageCheckResult.failed("These '## Design' rows are marked 'student-creates' but their Type cell is not a bare type name the later gates can look for: "
                    + unenforceableCreatedTypes + ". Write one bare type name per row (no generics, package prefix, emphasis, parenthetical, or second type in the same cell) — "
                    + "otherwise nothing can enforce that the template omits it.");
        }
        List<String> seamIds = testingStrategySeamIds(spec);
        if (seamIds.isEmpty()) {
            return StageCheckResult.failed("The '## Testing Strategy' section needs a table with one data row per independently actionable unit of student work. Give each row a "
                    + "stable seam ID in its first cell (S1, S2, ...); tests and statement tasks use these IDs to preserve the plan without creating one task per test.");
        }
        List<String> malformedSeamIds = seamIds.stream().filter(id -> !id.matches("S[1-9][0-9]*")).toList();
        if (!malformedSeamIds.isEmpty()) {
            return StageCheckResult.failed("These Testing Strategy rows have no stable seam ID: " + malformedSeamIds
                    + ". Use S1, S2, ... in the first column. Describe the work in the remaining cells; do not use a prose label as the identifier.");
        }
        List<String> duplicateSeamIds = seamIds.stream().filter(id -> java.util.Collections.frequency(seamIds, id) > 1).distinct().toList();
        if (!duplicateSeamIds.isEmpty()) {
            return StageCheckResult.failed("The Testing Strategy contains duplicate seam IDs: " + duplicateSeamIds
                    + ". One seam is one independently actionable unit of student work; merge its partitions into one row or assign genuinely different work a new ID.");
        }
        List<String> hiddenCells = hiddenVariantCells(spec);
        if (hiddenCells.size() != seamIds.size() || hiddenCells.stream().anyMatch(cell -> !cell.equals("yes") && !cell.equals("no"))) {
            return StageCheckResult.failed("Every Testing Strategy row must end with exactly 'yes' or 'no' in the Hidden variant column. This structured decision controls "
                    + "which seams need an AFTER_DUE_DATE overfit-resistance test; prose or a missing cell cannot be enforced.");
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
            String status = cells.stream().map(StageCheckService::normalizeTemplateStatus).filter(TEMPLATE_STATUS_TOKENS::contains).findFirst().orElse(null);
            if (!type.isBlank()) {
                rows.add(new DesignRow(type, status));
            }
        }
        return rows;
    }

    /**
     * Canonicalizes punctuation only for a Design table's closed-set status cell. Language models commonly typeset an ASCII hyphen as a Unicode dash; treating
     * {@code student‑creates} as an unrelated value silently disarms the ownership gate even though its meaning is unambiguous. The vocabulary remains closed: prose or extended
     * tokens still fail the specification gate.
     */
    private static String normalizeTemplateStatus(String cell) {
        return cell.replace("`", "").strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[\u2010-\u2015\u2212]", "-");
    }

    /** The type names SPEC.md's '## Design' table marks {@code student-creates} — the ones the template omit-gate and the solution presence-gate enforce. */
    static List<String> specStudentCreatedTypes(String spec) {
        return designTableRows(spec).stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type).filter(StageCheckService::isEnforceableTypeName).toList();
    }

    /**
     * Whether a '## Design' row's type name is a bare identifier the later gates can actually look for. Anything else ({@code Stack<T>}, a qualified name, {@code **bold**}, two
     * types in one cell) is UNENFORCEABLE — and silently dropping it would make the spec gate's own pass observation ("the later gates will enforce...") a lie.
     */
    private static boolean isEnforceableTypeName(String type) {
        return type.matches("[A-Za-z_][A-Za-z0-9_]*");
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
        List<String> createdTypes = enforcedStudentCreatedTypes(sandbox, sessionId);
        List<String> missingCreatedTypes = createdTypes.stream().filter(type -> findTypeDeclarations(sandbox, sessionId, "solution", type).isBlank()).toList();
        if (!missingCreatedTypes.isEmpty()) {
            return StageCheckResult.failed("SPEC.md's '## Design' table marks these types 'student-creates', but the solution contains no file for them: " + missingCreatedTypes
                    + ". The reference solution must fully implement every student-created type because that is the accepted learning contract; add the missing file(s).");
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
        List<String> createdTypes = enforcedStudentCreatedTypes(sandbox, sessionId);
        List<String> leakedFiles = createdTypes.stream().map(type -> findTypeDeclarations(sandbox, sessionId, "template", type)).filter(found -> !found.isBlank())
                .flatMap(found -> found.lines().map(String::strip)).toList();
        if (!leakedFiles.isEmpty()) {
            return StageCheckResult.failed("SPEC.md's '## Design' table marks type(s) 'student-creates', so the template must NOT contain their files — students create them "
                    + "from scratch (they are graded through the seeded structural checks and reflection-based tests). Delete these template files (leave TODO breadcrumbs in "
                    + "the collaborating classes instead): " + leakedFiles
                    + ". This ownership decision passed the specification gate; changing SPEC.md now cannot turn the required design work into a stub.");
        }
        if (!createdTypes.isEmpty()) {
            observation = (observation.isBlank() ? "" : observation + " ") + "Confirmed absent from the template, as the specification requires students to create them: "
                    + createdTypes + ".";
        }
        String todoMatches = execRead(sandbox, sessionId, "grep", "-rhoE", "--include=*.java", "--exclude-dir=target", "--exclude-dir=build", "TODO[[:space:]]+S[1-9][0-9]*:",
                GenerationWorkspaceService.WORKSPACE + "/template");
        List<String> todoReasons = ExerciseIntegrityGate.templateTodoSeamReasons(enforcedTestingSeamIds(sandbox, sessionId), Map.of("template-todos.java", todoMatches));
        if (!todoReasons.isEmpty()) {
            return StageCheckResult.failed(String.join(" ", todoReasons));
        }
        return StageCheckResult.passed(observation);
    }

    /**
     * Whether SPEC.md's {@code ## Testing Strategy} declares at least one hidden after-due-date variant, read from a STRUCTURED cell — never from prose. An earlier prose
     * heuristic here both false-triggered (a table that says "no hidden after-due-date variant" contains every keyword) and false-negatived (a paraphrase like "released at the
     * deadline" contains none), which is exactly the opinion-shaped gate this class forbids. The declaration is now a table cell in the section's LAST column whose text starts
     * with {@code yes} or {@code no}, parsed like the Design table's status tokens; anything else reads as "no declaration" and the gate stays silent.
     */
    static boolean specDeclaresHiddenVariants(String spec) {
        return !hiddenVariantSeamIds(spec).isEmpty();
    }

    /** Stable seam IDs whose Testing Strategy row explicitly requires a hidden after-due-date variant. */
    static Set<String> hiddenVariantSeamIds(String spec) {
        int start = spec.indexOf("## Testing Strategy");
        if (start < 0) {
            return Set.of();
        }
        Set<String> seams = new java.util.LinkedHashSet<>();
        boolean pastHeader = false;
        for (String line : spec.substring(start).lines().map(String::strip).toList()) {
            if (line.startsWith("## ") && !line.startsWith("## Testing Strategy")) {
                break;
            }
            if (!line.startsWith("|")) {
                continue;
            }
            if (!pastHeader) {
                if (line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ')) {
                    pastHeader = true;
                }
                continue;
            }
            String[] columns = line.split("\\|");
            if (columns.length < 3) {
                continue;
            }
            String seam = columns[1].replace("`", "").replace("*", "").strip();
            String decision = columns[columns.length - 1].replace("`", "").replace("*", "").strip().toLowerCase(java.util.Locale.ROOT);
            if (decision.equals("yes")) {
                seams.add(seam);
            }
        }
        return Set.copyOf(seams);
    }

    /**
     * Stable IDs from the first cell of each {@code ## Testing Strategy} data row. Invalid IDs are retained so the SPEC gate can report them rather than silently dropping them.
     */
    static List<String> testingStrategySeamIds(String spec) {
        int start = spec.indexOf("## Testing Strategy");
        if (start < 0) {
            return List.of();
        }
        List<String> ids = new java.util.ArrayList<>();
        boolean pastHeader = false;
        for (String line : spec.substring(start).lines().map(String::strip).toList()) {
            if (line.startsWith("## ") && !line.startsWith("## Testing Strategy")) {
                break;
            }
            if (!line.startsWith("|")) {
                continue;
            }
            if (!pastHeader) {
                if (line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ')) {
                    pastHeader = true;
                }
                continue;
            }
            String[] columns = line.split("\\|");
            String first = columns.length > 1 ? columns[1].replace("`", "").replace("*", "").strip() : "";
            if (!first.isBlank()) {
                ids.add(first);
            }
        }
        return List.copyOf(ids);
    }

    /** The last-column cells of the {@code ## Testing Strategy} table's data rows, lower-cased and stripped; empty when the section is not a table. */
    private static List<String> hiddenVariantCells(String spec) {
        int start = spec.indexOf("## Testing Strategy");
        if (start < 0) {
            return List.of();
        }
        List<String> cells = new java.util.ArrayList<>();
        boolean pastHeader = false;
        for (String line : spec.substring(start).lines().map(String::strip).toList()) {
            if (line.startsWith("## ") && !line.startsWith("## Testing Strategy")) {
                break;
            }
            if (!line.startsWith("|")) {
                continue;
            }
            if (!pastHeader) {
                if (line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ')) {
                    pastHeader = true;
                }
                continue;
            }
            String[] columns = line.split("\\|");
            String last = columns.length == 0 ? "" : columns[columns.length - 1].replace("`", "").replace("*", "").strip().toLowerCase(java.util.Locale.ROOT);
            if (!last.isBlank()) {
                cells.add(last);
            }
        }
        return cells;
    }

    /** The normalized names the workspace's grading plan hides until the due date; empty (fail-open) when no readable, parseable plan exists — same contract as the oracle's. */
    private Set<String> hiddenTestNames(InteractiveSandbox sandbox, String sessionId) {
        String planJson = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
        if (planJson.isBlank()) {
            return Set.of();
        }
        try {
            return GeneratedTestPlan.parse(planJson).hiddenEntries().stream().map(GeneratedTestPlan.Entry::name).map(ProblemStatementBindingChecker::normalizeTestName)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        catch (RuntimeException e) {
            return Set.of();
        }
    }

    /** The {@code student-creates} types from the frozen specification, falling back to the live workspace only for legacy/unapproved flows. */
    private List<String> enforcedStudentCreatedTypes(InteractiveSandbox sandbox, String sessionId) {
        String specification = approvedSpecs.approved(sessionId).orElseGet(() -> readSpec(sandbox, sessionId));
        return specStudentCreatedTypes(specification);
    }

    private String readSpec(InteractiveSandbox sandbox, String sessionId) {
        return execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
    }

    /**
     * Where the given type is DECLARED under the given repo: a file named after it, or any source file declaring it. A filename probe alone both missed a nested or secondary
     * declaration (the template ships the answer inside another file and the gate says "confirmed absent") and counted a stray {@code Type.md} or {@code Type.java.orig} as a
     * leak. Type names were validated as bare identifiers, and {@code exec} spawns without a shell, so neither argument can inject. Fails open (empty) on a tooling error.
     */
    private String findTypeDeclarations(InteractiveSandbox sandbox, String sessionId, String repo, String type) {
        String root = GenerationWorkspaceService.WORKSPACE + "/" + repo;
        String byName = execRead(sandbox, sessionId, "find", root, "-type", "f", "-name", type + ".*", "-not", "-path", "*/target/*", "-not", "-path", "*/build/*", "-not", "-name",
                "*.md", "-not", "-name", "*.txt", "-not", "-name", "*.orig", "-not", "-name", "*.class");
        String declarationPattern = "(^|[;{}])[[:space:]]*((public|protected|private|static|abstract|final|sealed|non-sealed)[[:space:]]+)*"
                + "(class|interface|enum|record|trait|struct|protocol)[[:space:]]+" + type + "\\b";
        String byDeclaration = execRead(sandbox, sessionId, "grep", "-rlE", declarationPattern, root, "--exclude-dir=target", "--exclude-dir=build", "--exclude=*.md",
                "--exclude=*.txt");
        return java.util.stream.Stream.of(byName, byDeclaration).flatMap(String::lines).map(String::strip).filter(line -> !line.isBlank()).distinct()
                .collect(java.util.stream.Collectors.joining("\n"));
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
            List<String> studentCreatedTypes = enforcedStudentCreatedTypes(sandbox, sessionId);
            String ownershipRepair = studentCreatedTypes.isEmpty() ? ""
                    : "\nThe approved student-created types are " + studentCreatedTypes
                            + ". Do not add their declarations to the template or edit SPEC.md. If direct references make the template test compilation fail, rewrite those tests "
                            + "using the seeded reflection utilities/Class.forName; use a dynamic proxy when the context must receive the omitted interface.";
            return new StageCheckResult(false, "The tests do not yet satisfy the differential requirement (the solution must pass every test, the template must fail every "
                    + "task-bound behavioural test):\n" + observation + ownershipRepair, report);
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
        List<String> declaredSeams = enforcedTestingSeamIds(sandbox, sessionId);
        List<String> entriesWithoutSeams = plan.tests().stream().filter(entry -> entry.seam().isBlank()).map(GeneratedTestPlan.Entry::name).toList();
        if (!entriesWithoutSeams.isEmpty()) {
            return new StageCheckResult(false, "The differential passed, but these test-plan.json entries have no seam: " + entriesWithoutSeams + ". Set each entry's \"seam\" "
                    + "to one of the specification's stable Testing Strategy IDs: " + declaredSeams + ".", report);
        }
        List<String> undeclaredSeams = plan.tests().stream().map(GeneratedTestPlan.Entry::seam).filter(seam -> !declaredSeams.contains(seam)).distinct().toList();
        if (!undeclaredSeams.isEmpty()) {
            return new StageCheckResult(false, "The differential passed, but test-plan.json uses undeclared seam(s): " + undeclaredSeams
                    + ". Use the approved Testing Strategy IDs " + declaredSeams + "; do not invent a test-only partition.", report);
        }
        Set<String> knownNames = Set.copyOf(report.exactTestNames());
        List<String> unknownPlanNames = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(name -> !knownNames.contains(name)).toList();
        if (!unknownPlanNames.isEmpty()) {
            return new StageCheckResult(false, "The differential passed, but test-plan.json names tests that do not exist: " + unknownPlanNames
                    + ". Copy the exact names verify reported: " + report.exactTestNames() + ".", report);
        }
        List<String> unplannedNames = report.exactTestNames().stream().filter(name -> plan.tests().stream().noneMatch(entry -> entry.name().equals(name))).toList();
        Set<String> hiddenPlanSeams = plan.hiddenEntries().stream().map(GeneratedTestPlan.Entry::seam).collect(java.util.stream.Collectors.toSet());
        List<String> missingHiddenSeams = enforcedHiddenVariantSeamIds(sandbox, sessionId).stream().filter(seam -> !hiddenPlanSeams.contains(seam)).sorted().toList();
        if (!missingHiddenSeams.isEmpty()) {
            return new StageCheckResult(false, "The approved Testing Strategy requires an AFTER_DUE_DATE variant for seam(s) " + missingHiddenSeams
                    + ", but test-plan.json has no hidden test mapped to them. Add fresh witness tests for each listed seam, keep them unbound by [task] lines, and preserve their "
                    + "seam IDs; hiding an unrelated test does not satisfy this contract.", report);
        }
        String planSummary = "Grading plan accepted: " + plan.tests().size() + " test(s), " + plan.hiddenEntries().size() + " hidden until the due date."
                + (unplannedNames.isEmpty() ? "" : " Not in the plan (they keep Artemis defaults, weight 1 and visible): " + unplannedNames + ".");
        return new StageCheckResult(true, observation + "\n" + planSummary, report);
    }

    private StageCheckResult checkStatement(InteractiveSandbox sandbox, String sessionId, @Nullable AgentVerifyReport lastTestsReport) {
        String statement = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        if (statement.isBlank()) {
            return StageCheckResult.failed("problem-statement.md is missing or empty. Write the student-facing problem statement before submitting.");
        }
        Set<String> hiddenNames = hiddenTestNames(sandbox, sessionId);
        if (lastTestsReport != null) {
            List<String> exactTestNames = lastTestsReport.exactTestNames();
            List<String> unresolved = ProblemStatementBindingChecker.unresolvedTaskBindings(statement, exactTestNames, exactTestNames.size(), Set.of());
            if (!unresolved.isEmpty()) {
                List<String> bindableNames = ProblemStatementBindingChecker.bindableTestNames(exactTestNames, hiddenNames);
                return StageCheckResult.failed("These [task] bindings reference names that match no actual test: " + unresolved
                        + ". A [task]'s parenthesised names must be exact, visible test names from the TESTS stage, copied verbatim: " + bindableNames + ".");
            }
            // Diagram testsColor links are interactive in Artemis (they render pass/fail per element); a name that matches no test is a silently dead link the student can
            // never satisfy, so it is held to the same resolution standard as a [task] binding.
            List<String> deadDiagramLinks = ProblemStatementBindingChecker.unresolvedTestsColorNames(statement, exactTestNames, Set.of());
            if (!deadDiagramLinks.isEmpty()) {
                return StageCheckResult.failed("These diagram testsColor(...) names match no actual test: " + deadDiagramLinks
                        + ". Use the exact test names from the TESTS stage (behavioural or seeded structural), or remove the link: " + exactTestNames + ".");
            }
        }
        String planJson = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
        if (!planJson.isBlank()) {
            try {
                List<String> groupingReasons = ProblemStatementBindingChecker.seamTaskGroupingReasons(statement, GeneratedTestPlan.parse(planJson));
                if (!groupingReasons.isEmpty()) {
                    return StageCheckResult.failed("The statement must have one task per student-work seam, with all visible tests for that seam bound to that task: "
                            + String.join(" ", groupingReasons));
                }
            }
            catch (IllegalArgumentException e) {
                return StageCheckResult.failed("The statement cannot be checked against test-plan.json because the plan is invalid: " + e.getMessage());
            }
        }
        List<String> hiddenMentions = ProblemStatementBindingChecker.hiddenTestMentions(statement, hiddenNames);
        if (!hiddenMentions.isEmpty()) {
            return StageCheckResult.failed(ProblemStatementBindingChecker.hiddenTestMentionsRejection(hiddenMentions));
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
        String authoritativeSpec = approvedSpecs.approved(sessionId).orElseGet(() -> readSpec(sandbox, sessionId));
        boolean diagramPromised = ProblemStatementBindingChecker.specPromisesDiagram(authoritativeSpec);
        if (diagramPromised && !statement.contains("@startuml")) {
            return StageCheckResult.failed("SPEC.md's '## Diagram' section says yes, but the statement contains no @startuml diagram. Add the PlantUML class diagram after "
                    + "the tasks it illustrates (with testsColor links). The accepted diagram decision cannot be revoked after the specification gate.");
        }
        // Exact duplicate headings are a mechanical statement defect (observed shipping live: the same '### 1. ...' section twice); catching it here costs nothing.
        List<String> duplicateHeadings = ProblemStatementBindingChecker.duplicateHeadings(statement);
        if (!duplicateHeadings.isEmpty()) {
            return StageCheckResult.failed("The statement repeats these headings verbatim: " + duplicateHeadings + ". Merge or remove the duplicate sections.");
        }
        List<String> bareTasks = ProblemStatementBindingChecker.tasksWithoutInstruction(statement);
        if (!bareTasks.isEmpty()) {
            return StageCheckResult.failed("These [task] bindings have no student-facing instruction before the next task or heading: " + bareTasks
                    + ". Follow each task with concise imperative prose naming the types or members the student must implement; a checkbox alone is not an exercise instruction.");
        }
        return StageCheckResult.passed("");
    }

    /** Seam IDs from the frozen specification, with the live workspace used only for legacy/unapproved flows. */
    private List<String> enforcedTestingSeamIds(InteractiveSandbox sandbox, String sessionId) {
        String specification = approvedSpecs.approved(sessionId).orElseGet(() -> readSpec(sandbox, sessionId));
        return testingStrategySeamIds(specification).stream().filter(id -> id.matches("S[1-9][0-9]*")).distinct().toList();
    }

    private Set<String> enforcedHiddenVariantSeamIds(InteractiveSandbox sandbox, String sessionId) {
        String specification = approvedSpecs.approved(sessionId).orElseGet(() -> readSpec(sandbox, sessionId));
        return hiddenVariantSeamIds(specification);
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
