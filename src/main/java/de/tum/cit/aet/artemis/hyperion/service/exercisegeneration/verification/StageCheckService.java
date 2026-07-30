package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * The mechanical per-stage gates enforced by the staged generation workflow
 * ({@code de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.StagedGenerationRunner}),
 * one method per {@link GenerationStage}: SPEC.md's required sections and evidence gates, the differential self-check for TESTS, and the problem-statement task-binding resolution
 * for STATEMENT. The runner owns stage sequencing, turn budgets, and re-entry; this service owns only "does this stage's artifact pass".
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class StageCheckService {

    private static final Logger log = LoggerFactory.getLogger(StageCheckService.class);

    private static final List<String> REQUIRED_SPEC_HEADINGS = List.of("## Rules", "## Worked Examples", "## Design", "## Public API", "## Testing Strategy",
            "## Contract Risk Inventory", "## Diagram");

    /** The only template-status tokens a SPEC.md '## Design' data row may carry; 'student-creates' additionally arms the template omit-gate. */
    private static final Set<String> TEMPLATE_STATUS_TOKENS = Set.of("given", "stubbed", "student-creates");

    private final DifferentialVerificationService verifier;

    private final ApprovedSpecRegistry approvedSpecs;

    private final boolean workspaceSpecFallback;

    // Required: with several constructors and no annotation, Spring cannot pick one.
    @Autowired
    public StageCheckService(DifferentialVerificationService verifier, ApprovedSpecRegistry approvedSpecs) {
        this(verifier, approvedSpecs, false);
    }

    StageCheckService(DifferentialVerificationService verifier, ApprovedSpecRegistry approvedSpecs, boolean workspaceSpecFallback) {
        this.verifier = verifier;
        this.approvedSpecs = approvedSpecs;
        this.workspaceSpecFallback = workspaceSpecFallback;
    }

    /**
     * Rejects a workspace mutation that would undo an approved ownership decision, at the write boundary rather than at the next build: once SPEC.md passes its reviews it is the
     * downstream contract, and a type assigned to the student cannot be restored to the template to make direct-reference tests compile. Executable artifacts stay writable so
     * later stages can still repair real implementation defects.
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
                + ". Do not restore or pre-create their declarations in the template, and do not put their seam IDs on unrelated collaborators. Test the student-created types "
                + "through Class.forName/reflection (and a dynamic proxy where the context needs an interface instance), as shown by the seeded test utilities.");
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
        SandboxExecResultDTO restore = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c",
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

    /** Without a shared registry every check reads the live workspace specification. */
    StageCheckService(DifferentialVerificationService verifier) {
        this(verifier, new ApprovedSpecRegistry(), true);
    }

    /**
     * Checks one stage's artifact against its mechanical gate.
     *
     * @param stage                 the stage whose artifact is being checked
     * @param sandbox               the open sandbox session
     * @param sessionId             the sandbox session id
     * @param exercise              the exercise being generated (drives the per-language build recipe)
     * @param seedTestsFiles        the tests-repository snapshot taken before generation, forwarded to the TESTS stage's differential self-check
     * @param lastTestsReport       the TESTS stage's {@link AgentVerifyReport}, consumed by the STATEMENT stage to resolve {@code [task]} bindings against exact test names;
     *                                  {@code null} before TESTS has run (or when TESTS never returned a report)
     * @param seededStructuralTests server-authored structural test names and exact trusted files currently materialized in the workspace
     * @return the gate's pass/fail verdict, an actionable observation, and — for TESTS only — the full {@link AgentVerifyReport}
     */
    public StageCheckResult check(GenerationStage stage, InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            @Nullable AgentVerifyReport lastTestsReport, SeededStructuralTests seededStructuralTests) {
        Set<String> seededStructuralTestNames = seededStructuralTests.testNames();
        // A downstream or unstaged run may use only a specification frozen by the SPEC gate. Treating a candidate-authored workspace file as authority would let ADAPT define
        // its own ownership and grading exemptions.
        if (stage != GenerationStage.SPEC && approvedSpecs.approved(sessionId).isEmpty() && !readSpec(sandbox, sessionId).isBlank()) {
            if (workspaceSpecFallback) {
                StageCheckResult specStillValid = checkSpec(sandbox, sessionId, exercise);
                if (!specStillValid.passed()) {
                    return StageCheckResult.failed("SPEC.md is no longer a valid specification: " + specStillValid.observation()
                            + " The approved specification is read-only; do not mutate files through bash. Task bindings and PlantUML belong in problem-statement.md, never in "
                            + "SPEC.md.");
                }
            }
            else {
                return StageCheckResult
                        .failed("An unapproved SPEC.md appeared after authoring began. Candidate-authored files cannot define grading authority. Delete it and preserve "
                                + "the instructor's existing exercise contract; only the dedicated SPEC stage may freeze a new specification.");
            }
        }
        return switch (stage) {
            case SPEC -> checkSpec(sandbox, sessionId, exercise);
            case TESTS -> checkTests(sandbox, sessionId, exercise, seedTestsFiles, seededStructuralTests);
            case STATEMENT -> checkStatement(sandbox, sessionId, lastTestsReport, seededStructuralTestNames);
        };
    }

    /** The SPEC stage's mechanical floor. Everything semantic (learning fit and example quality) stays with the critic; a deterministic gate must never hold an opinion. */
    private StageCheckResult checkSpec(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        String spec = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
        if (spec.isBlank()) {
            return StageCheckResult.failed("SPEC.md is missing or empty. Write /workspace/SPEC.md with '## Rules' (numbered R1..Rn), a '## Worked Examples' "
                    + "table, a '## Design' table, a compact '## Public API' contract, a '## Testing Strategy', a '## Contract Risk Inventory', and a '## Diagram' decision "
                    + "before continuing.");
        }
        Set<String> exactSpecLines = spec.lines().map(String::strip).collect(Collectors.toSet());
        List<String> missingSpecSections = REQUIRED_SPEC_HEADINGS.stream().filter(heading -> !exactSpecLines.contains(heading)).toList();
        if (!missingSpecSections.isEmpty()) {
            return StageCheckResult.failed("SPEC.md is missing required section(s): " + missingSpecSections + ". Add them before continuing.");
        }
        if (ProblemStatementBindingChecker.hasTaskBindings(spec) || spec.contains("@startuml")) {
            return StageCheckResult.failed("SPEC.md must not contain [task] bindings or PlantUML diagrams — those belong to the final statement, written once tests exist. "
                    + "Remove them; keep the spec to rules and worked examples.");
        }
        List<String> workedExampleRows = workedExampleDataRows(spec);
        if (workedExampleRows.size() < 2) {
            return StageCheckResult.failed("The '## Worked Examples' table needs at least two data rows (| Rules | Input | Expected |) with replayable observable outcomes.");
        }
        List<DesignRow> designRows = designTableRows(spec);
        if (designRows.isEmpty()) {
            return StageCheckResult.failed("The '## Design' section has no data rows. It must be a table (| Type | Role | Template status |) listing every type with its "
                    + "Template status: exactly one of 'given', 'stubbed', 'student-creates'.");
        }
        List<String> rowsWithoutStatus = designRows.stream().filter(row -> row.status() == null).map(DesignRow::type).toList();
        if (!rowsWithoutStatus.isEmpty()) {
            return StageCheckResult.failed("These '## Design' rows have no valid final Template status cell: " + rowsWithoutStatus
                    + ". Replace each row's LAST cell with exactly one bare token from " + TEMPLATE_STATUS_TOKENS
                    + " — no 'absent', parentheses, or explanation. Do not move the token into the Role cell; the later template gate reads the final cell only.");
        }
        List<String> designTypes = designRows.stream().map(DesignRow::type).toList();
        List<String> duplicateDesignTypes = designTypes.stream().filter(type -> Collections.frequency(designTypes, type) > 1).distinct().toList();
        if (!duplicateDesignTypes.isEmpty()) {
            return StageCheckResult.failed("The Design table declares the same type more than once: " + duplicateDesignTypes
                    + ". Keep one authoritative row per type so template ownership cannot contradict itself.");
        }
        List<String> unenforceableCreatedTypes = designRows.stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type)
                .filter(type -> !isEnforceableTypeName(type)).toList();
        if (!unenforceableCreatedTypes.isEmpty()) {
            return StageCheckResult.failed("These '## Design' rows are marked 'student-creates' but their Type cell is not a bare type name the later gates can look for: "
                    + unenforceableCreatedTypes + ". Write one bare type name per row (no generics, package prefix, emphasis, parenthetical, or second type in the same cell) — "
                    + "otherwise nothing can enforce that the template omits it.");
        }
        if (exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            Set<String> studentCreatedTypes = designRows.stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type)
                    .filter(StageCheckService::isEnforceableTypeName).collect(Collectors.toSet());
            ApprovedStructuralContract.ParseResult structuralContract = ApprovedStructuralContract.parse(spec, Set.copyOf(designTypes), studentCreatedTypes);
            if (!structuralContract.valid()) {
                return StageCheckResult.failed("The Java Public API is not a complete machine-checkable contract for student-created types: " + structuralContract.errors()
                        + ". Put each exact type declaration and all of its public/protected constructors, methods, and deliberately exposed fields in fenced ```java blocks. "
                        + "Use signatures only; do not replace them with prose or include private implementation details.");
            }
        }
        // An all-student-creates design makes an empty starter repository inevitable, and the oracle's "the template must fail" then holds vacuously (nothing compiles, so
        // everything fails), scoring the degenerate candidate like a well-scaffolded one.
        if (designRows.stream().noneMatch(row -> "given".equals(row.status()) || "stubbed".equals(row.status()))) {
            return StageCheckResult.failed("Every '## Design' row is marked 'student-creates', so the template repository would ship empty and students would start from a "
                    + "blank project. Give the exercise a starting point: mark at least one type 'given' (supplied complete) or 'stubbed' (signatures with TODO bodies) — "
                    + "typically the collaborator, context, or data type the student's own work plugs into — and keep the genuinely design-bearing types 'student-creates'.");
        }
        if (exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            List<String> impossibleGivenDependencies = givenTypesDependingOnStudentCreatedTypes(spec, designRows);
            if (!impossibleGivenDependencies.isEmpty()) {
                return StageCheckResult.failed("These given Java types have Public API signatures that reference a student-created type absent from the template: "
                        + impossibleGivenDependencies
                        + ". A given type must ship complete and compile. Choose a coherent ownership graph before approval: make the dependent collaborator stubbed or "
                        + "student-created with its own work seam, or ship the referenced abstraction as stubbed/given. Do not erase the typed API or replace it with Object/reflection "
                        + "just to bypass this contradiction.");
            }
        }
        List<TestingStrategyRow> testingRows = testingStrategyRows(spec);
        List<String> seamIds = testingRows.stream().map(TestingStrategyRow::seamId).toList();
        if (seamIds.isEmpty()) {
            return StageCheckResult.failed("The '## Testing Strategy' section needs a table with one data row per independently actionable unit of student work. Give each row a "
                    + "stable seam ID in its first cell (S1, S2, ...); tests and statement tasks use these IDs to preserve the plan without creating one task per test.");
        }
        List<String> testingHeaders = testingStrategyHeaders(spec);
        String ownerHeader = testingHeaders.size() > 1 ? testingHeaders.get(1) : "";
        if (!"Owner type".equalsIgnoreCase(ownerHeader)) {
            return StageCheckResult.failed("The second Testing Strategy column must be named 'Owner type'; found '" + ownerHeader
                    + "'. Put one exact bare Design type in that column so ownership is explicit rather than inferred from prose.");
        }
        String responsibilityHeader = testingHeaders.size() > 2 ? testingHeaders.get(2) : "";
        if (!"Observable responsibility".equalsIgnoreCase(responsibilityHeader)) {
            return StageCheckResult.failed("The third Testing Strategy column must be named 'Observable responsibility'; found '" + responsibilityHeader
                    + "'. State the behavior, collaboration, or state transition this seam grades so later tests do not infer the contract from a type name.");
        }
        List<String> malformedSeamIds = seamIds.stream().filter(id -> !id.matches("S[1-9][0-9]*")).toList();
        if (!malformedSeamIds.isEmpty()) {
            return StageCheckResult.failed("These Testing Strategy rows have no stable seam ID: " + malformedSeamIds
                    + ". Use S1, S2, ... in the first column. Describe the work in the remaining cells; do not use a prose label as the identifier.");
        }
        List<String> duplicateSeamIds = seamIds.stream().filter(id -> Collections.frequency(seamIds, id) > 1).distinct().toList();
        if (!duplicateSeamIds.isEmpty()) {
            return StageCheckResult.failed("The Testing Strategy contains duplicate seam IDs: " + duplicateSeamIds
                    + ". One seam is one independently actionable unit of student work; merge its partitions into one row or assign genuinely different work a new ID.");
        }
        List<String> malformedOwners = testingRows.stream().filter(row -> !isEnforceableTypeName(row.ownerType())).map(TestingStrategyRow::seamId).toList();
        if (!malformedOwners.isEmpty()) {
            return StageCheckResult.failed("These Testing Strategy rows have no enforceable Owner type: " + malformedOwners
                    + ". Add an Owner type column and put one bare type name from the Design table in every row. This exact link determines whether and where the template "
                    + "may carry that seam's TODO breadcrumb.");
        }
        List<String> missingResponsibilities = testingRows.stream().filter(row -> row.observableResponsibility().isBlank()).map(TestingStrategyRow::seamId).toList();
        if (!missingResponsibilities.isEmpty()) {
            return StageCheckResult.failed("These Testing Strategy seams do not state an Observable responsibility: " + missingResponsibilities
                    + ". Describe the student-owned behavior each seam grades; an owner type alone is not a testing strategy.");
        }
        List<String> invalidWeights = testingRows.stream().filter(row -> !row.weightTier().matches("[123]")).map(TestingStrategyRow::seamId).toList();
        if (!invalidWeights.isEmpty()) {
            return StageCheckResult.failed("These Testing Strategy seams do not use a weight tier of exactly 1, 2, or 3: " + invalidWeights
                    + ". Put the seam's Artemis test-case weight in the fourth column.");
        }
        Map<String, String> designStatusByType = designRows.stream().collect(Collectors.toMap(DesignRow::type, DesignRow::status, (first, ignored) -> first, LinkedHashMap::new));
        List<String> unknownOwners = testingRows.stream().filter(row -> !designStatusByType.containsKey(row.ownerType())).map(row -> row.seamId() + "->" + row.ownerType())
                .toList();
        if (!unknownOwners.isEmpty()) {
            return StageCheckResult.failed("These Testing Strategy owner links do not name a type in the Design table: " + unknownOwners
                    + ". Use the exact bare Design type that owns the student work; do not encode ownership in prose.");
        }
        List<String> givenOwners = testingRows.stream().filter(row -> "given".equals(designStatusByType.get(row.ownerType()))).map(row -> row.seamId() + "->" + row.ownerType())
                .toList();
        if (!givenOwners.isEmpty()) {
            return StageCheckResult.failed("These Testing Strategy seams assign student work to a Design type marked given: " + givenOwners
                    + ". A graded seam must be owned by a stubbed or student-creates type; either correct the ownership or remove work that students do not perform.");
        }
        List<String> hiddenCells = hiddenVariantCells(spec);
        if (hiddenCells.size() != seamIds.size() || hiddenCells.stream().anyMatch(cell -> !cell.equals("yes") && !cell.equals("no"))) {
            return StageCheckResult.failed("Every Testing Strategy row must end with exactly 'yes' or 'no' in the Hidden variant column. This structured decision controls "
                    + "which seams need an AFTER_DUE_DATE overfit-resistance test; prose or a missing cell cannot be enforced.");
        }
        if (exercise.getDueDate() == null && hiddenCells.stream().anyMatch("yes"::equals)) {
            return StageCheckResult.failed("The Testing Strategy requests hidden after-due-date coverage, but this exercise has no due date. Mark every Hidden variant cell 'no'; "
                    + "otherwise AFTER_DUE_DATE tests would remain hidden indefinitely.");
        }
        List<RiskInventoryRow> riskRows = riskInventoryRows(spec);
        List<String> riskSeamIds = riskRows.stream().map(RiskInventoryRow::seamId).toList();
        List<String> duplicateRiskSeams = riskSeamIds.stream().filter(id -> Collections.frequency(riskSeamIds, id) > 1).distinct().toList();
        if (!duplicateRiskSeams.isEmpty()) {
            return StageCheckResult.failed("The Contract Risk Inventory contains duplicate seam IDs: " + duplicateRiskSeams
                    + ". Keep one row per Testing Strategy seam and combine that seam's admitted partitions in its row.");
        }
        List<String> missingRiskSeams = seamIds.stream().filter(id -> !riskSeamIds.contains(id)).toList();
        List<String> unknownRiskSeams = riskSeamIds.stream().filter(id -> !seamIds.contains(id)).toList();
        if (!missingRiskSeams.isEmpty() || !unknownRiskSeams.isEmpty()) {
            return StageCheckResult.failed("The Contract Risk Inventory must cover every Testing Strategy seam exactly once. Missing seams: " + missingRiskSeams
                    + "; unknown seams: " + unknownRiskSeams + ".");
        }
        String rulesSection = section(spec, "## Rules");
        Set<String> declaredRuleIds = Pattern.compile("(?<![A-Za-z0-9_])R[1-9][0-9]*(?![A-Za-z0-9_])").matcher(rulesSection).results().map(java.util.regex.MatchResult::group)
                .collect(Collectors.toSet());
        List<String> invalidRiskRows = riskRows.stream().filter(row -> {
            List<String> citedRules = Pattern.compile("R[1-9][0-9]*").matcher(row.ruleIds()).results().map(java.util.regex.MatchResult::group).toList();
            return riskPartitionIds(row).isEmpty() || citedRules.isEmpty() || citedRules.stream().anyMatch(rule -> !declaredRuleIds.contains(rule));
        }).map(RiskInventoryRow::seamId).toList();
        if (!invalidRiskRows.isEmpty()) {
            return StageCheckResult.failed("These Contract Risk Inventory rows do not cite a declared rule and enumerate stable admitted-partition IDs: " + invalidRiskRows
                    + ". Cite exact R IDs and write every semicolon-delimited partition as <seam>.P<n>: <concrete legal distinction>, for example S1.P1: ordinary values; "
                    + "S1.P2: integer extrema.");
        }
        List<String> allRiskPartitionIds = riskRows.stream().flatMap(row -> riskPartitionIds(row).stream()).toList();
        List<String> duplicateRiskPartitionIds = allRiskPartitionIds.stream().filter(id -> Collections.frequency(allRiskPartitionIds, id) > 1).distinct().toList();
        if (!duplicateRiskPartitionIds.isEmpty()) {
            return StageCheckResult.failed("The Contract Risk Inventory reuses partition IDs " + duplicateRiskPartitionIds
                    + ". Give every admitted partition one unique stable ID so executable tests can trace to it.");
        }
        // Echo the parsed plan back so the agent sees exactly what the later gates will hold it to, rather than only the absence of errors.
        String echo = designRows.stream().map(row -> row.type() + "=" + row.status()).collect(Collectors.joining(", "));
        String seamEcho = testingRows.stream().map(row -> row.seamId() + "->" + row.ownerType() + "(" + designStatusByType.get(row.ownerType()) + ")")
                .collect(Collectors.joining(", "));
        // The ownership decision is frozen here, so state its testing consequence here too: graded tests compile against the template as well as the solution, so a
        // student-created type does not exist at test-compile time. Left unsaid, that constraint is discovered as an unexplained "cannot find symbol" and costs repair budgets.
        List<String> createdTypes = designRows.stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type).toList();
        String reflectionConsequence = createdTypes.isEmpty() ? ""
                : " The graded tests compile against the template too, so nothing may import or name " + createdTypes + " directly: reach them through Class.forName and the "
                        + "seeded reflection utilities (a dynamic proxy when supplied code must receive a student-created interface). Adding them to the template to make the "
                        + "tests compile is the one repair the ownership gate will always reject; choose 'stubbed' instead at specification time if the tests need to name a "
                        + "type directly.";
        // Advice on a pass, never a rejection: the mandate detector is not precise enough to justify discarding a sound contract with no recourse, and the harm comes from the
        // Testing Strategy seam the agent would write for such a rule rather than from the rule itself. Delivered here because this is the last point at which the spec is still
        // editable. The advice must also forbid instrumenting the API (call counters, invocation flags) — that grades nothing and burdens the student's contract.
        List<String> techniqueMandates = ExerciseIntegrityGate.techniqueMandatesInSpecification(spec);
        String techniqueAdvice = techniqueMandates.isEmpty() ? ""
                : " One or more rules state an implementation technique (" + techniqueMandates + "). No assertion through the public API can separate a recursive "
                        + "implementation from an iterative one returning identical values, so keep this as guidance in the student-facing statement and do NOT give it a "
                        + "Testing Strategy seam. Do NOT try to make it observable either: adding call counters, invocation flags, depth trackers, or any other member that "
                        + "exists so a test can watch how the work was done is worse than leaving it ungraded. It does not grade the technique — an iterative implementation "
                        + "that updates the same counter passes — and it forces machinery into the student's contract that the exercise does not need. Reading the student's "
                        + "source file is rejected outright. State the rule in terms of the result the student must produce, and put the technique in the prose.";
        return StageCheckResult.passed("Specification accepted. Parsed template plan the later gates will enforce: " + echo + ". Parsed work ownership: " + seamEcho
                + ". A student-creates type is absent from the template and therefore has no template TODO; a stubbed owner carries its own seam TODO." + reflectionConsequence
                + techniqueAdvice);
    }

    /** One parsed data row of SPEC.md's '## Design' table: the type name (first cell, backticks stripped) and its template-status token ({@code null} when the row has none). */
    record DesignRow(String type, @Nullable String status) {
    }

    /** One Testing Strategy row. Owner type links to the Design table; observable responsibility states the student-owned behavior the seam grades. */
    record TestingStrategyRow(String seamId, String ownerType, String observableResponsibility, String weightTier, String hiddenDecision) {
    }

    /** One contract-closure row linking a graded seam to exact rules and the admitted partitions its tests must distinguish. */
    record RiskInventoryRow(String seamId, String ruleIds, String admittedPartitions) {
    }

    /** The first cell is the type and the FINAL cell is the closed-set template status, whatever columns a specification puts in between. */
    static List<DesignRow> designTableRows(String spec) {
        int start = spec.indexOf("## Design");
        if (start < 0) {
            return List.of();
        }
        List<DesignRow> rows = new ArrayList<>();
        boolean pastHeader = false;
        for (String line : spec.substring(start).lines().map(String::strip).toList()) {
            if (line.startsWith("## ") && !line.startsWith("## Design")) {
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
            String row = line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length());
            List<String> cells = Arrays.stream(row.split("\\|", -1)).map(String::strip).toList();
            if (cells.isEmpty()) {
                continue;
            }
            String type = cells.getFirst().replace("`", "").strip();
            String normalizedStatus = normalizeTemplateStatus(cells.getLast());
            String status = TEMPLATE_STATUS_TOKENS.contains(normalizedStatus) ? normalizedStatus : null;
            if (!type.isBlank()) {
                rows.add(new DesignRow(type, status));
            }
        }
        return rows;
    }

    /**
     * Canonicalizes punctuation only for a Design table's closed-set status cell: a Unicode dash typeset for an ASCII hyphen would otherwise make {@code student‑creates} an
     * unrelated value and silently disarm the ownership gate. The vocabulary stays closed, so prose or extended tokens still fail the specification gate.
     *
     * @param cell raw Design-table status cell
     * @return canonical status token, or the normalized unrecognized value for fail-closed validation
     */
    public static String normalizeTemplateStatus(String cell) {
        String normalized = cell.replace("`", "").strip().toLowerCase(Locale.ROOT).replaceAll("[\u2010-\u2015\u2212]", "-");
        for (String emphasis : List.of("**", "__", "*", "_")) {
            if (normalized.startsWith(emphasis) && normalized.endsWith(emphasis) && normalized.length() > emphasis.length() * 2) {
                return normalized.substring(emphasis.length(), normalized.length() - emphasis.length()).strip();
            }
        }
        return normalized;
    }

    /** The type names SPEC.md's '## Design' table marks {@code student-creates} — the ones the template omit-gate and the solution presence-gate enforce. */
    static List<String> specStudentCreatedTypes(String spec) {
        return designTableRows(spec).stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type).filter(StageCheckService::isEnforceableTypeName).toList();
    }

    /**
     * The type names SPEC.md's '## Design' table marks {@code stubbed} — the ones the template must declare, because a stubbed type is the student's starting point. Only
     * enforceable bare names count. The specification gate demands a bare name of {@code student-creates} rows only, so a stubbed row may legitimately read {@code Stack<T>}; the
     * declaration probe matches the bare {@code Stack} a Java source actually declares, and searching for the generic cell verbatim would find nothing and reject a sound
     * exercise. The {@code given} arm of this contract already fails open on the same input.
     */
    static List<String> specStubbedTypes(String spec) {
        return designTableRows(spec).stream().filter(row -> "stubbed".equals(row.status())).map(DesignRow::type).filter(StageCheckService::isEnforceableTypeName).toList();
    }

    private static List<String> givenTypesDependingOnStudentCreatedTypes(String spec, List<DesignRow> designRows) {
        Set<String> givenTypes = designRows.stream().filter(row -> "given".equals(row.status())).map(DesignRow::type).collect(Collectors.toSet());
        Set<String> studentCreatedTypes = designRows.stream().filter(row -> "student-creates".equals(row.status())).map(DesignRow::type).collect(Collectors.toSet());
        List<String> conflicts = new ArrayList<>();
        boolean inPublicApi = false;
        @Nullable
        String currentOwner = null;
        for (String line : spec.lines().map(String::strip).toList()) {
            if (line.equals("## Public API")) {
                inPublicApi = true;
                continue;
            }
            if (inPublicApi && line.startsWith("## ")) {
                break;
            }
            if (!inPublicApi) {
                continue;
            }
            if (line.startsWith("### ")) {
                String heading = line.substring(4).replace("`", "").strip();
                currentOwner = givenTypes.stream().filter(type -> containsTypeName(heading, type)).findFirst().orElse(null);
                continue;
            }
            if (currentOwner == null) {
                continue;
            }
            for (String studentCreatedType : studentCreatedTypes) {
                if (containsTypeName(line, studentCreatedType)) {
                    conflicts.add(currentOwner + "->" + studentCreatedType);
                }
            }
        }
        return conflicts.stream().distinct().toList();
    }

    private static boolean containsTypeName(String text, String type) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(type) + "(?![A-Za-z0-9_])").matcher(text).find();
    }

    /**
     * Whether a '## Design' row's type name is a bare identifier the later gates can look for. Anything else ({@code Stack<T>}, a qualified name, {@code **bold**}, two types in
     * one cell) is unenforceable, and silently dropping it would make the spec gate's own pass observation ("the later gates will enforce...") a lie.
     */
    private static boolean isEnforceableTypeName(String type) {
        return type.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    static List<String> workedExampleDataRows(String spec) {
        List<String> rows = new ArrayList<>();
        boolean inSection = false;
        boolean pastHeader = false;
        for (String line : spec.lines().map(String::strip).toList()) {
            if (line.equals("## Worked Examples")) {
                inSection = true;
                continue;
            }
            if (inSection && line.startsWith("## ")) {
                break;
            }
            if (!inSection || !line.startsWith("|")) {
                continue;
            }
            if (!pastHeader) {
                if (line.contains("-") && line.matches("[|:\\-\\s]+")) {
                    pastHeader = true;
                }
                continue;
            }
            if (!line.matches("[|:\\-\\s]+")) {
                rows.add(line);
            }
        }
        return List.copyOf(rows);
    }

    /**
     * Whether SPEC.md's {@code ## Testing Strategy} declares at least one hidden after-due-date variant. Read from the structured last-column cell and never from prose, which
     * cannot be parsed without holding an opinion: "no hidden after-due-date variant" contains every keyword, and "released at the deadline" contains none.
     */
    static boolean specDeclaresHiddenVariants(String spec) {
        return !hiddenVariantSeamIds(spec).isEmpty();
    }

    static Set<String> hiddenVariantSeamIds(String spec) {
        return testingStrategyRows(spec).stream().filter(row -> row.hiddenDecision().equals("yes")).map(TestingStrategyRow::seamId).collect(Collectors.toUnmodifiableSet());
    }

    /** Invalid seam IDs are retained rather than dropped, so the SPEC gate can name them in its rejection. */
    static List<TestingStrategyRow> testingStrategyRows(String spec) {
        int start = spec.indexOf("## Testing Strategy");
        if (start < 0) {
            return List.of();
        }
        List<TestingStrategyRow> rows = new ArrayList<>();
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
            String[] columns = line.split("\\|", -1);
            String seam = columns.length > 1 ? normalizeTestingCell(columns[1]) : "";
            String owner = columns.length > 2 ? normalizeTestingCell(columns[2]) : "";
            String responsibility = columns.length > 3 ? normalizeTestingCell(columns[3]) : "";
            String weight = columns.length > 4 ? normalizeTestingCell(columns[4]) : "";
            int lastContentColumn = line.endsWith("|") ? columns.length - 2 : columns.length - 1;
            String hidden = columns.length > 2 ? normalizeTestingCell(columns[lastContentColumn]).toLowerCase(Locale.ROOT) : "";
            if (!seam.isBlank()) {
                rows.add(new TestingStrategyRow(seam, owner, responsibility, weight, hidden));
            }
        }
        return List.copyOf(rows);
    }

    private static String normalizeTestingCell(String cell) {
        String normalized = cell.strip();
        if (normalized.length() >= 2 && normalized.startsWith("`") && normalized.endsWith("`")) {
            normalized = normalized.substring(1, normalized.length() - 1).strip();
        }
        while (normalized.length() >= 2 && normalized.startsWith("*") && normalized.endsWith("*")) {
            normalized = normalized.substring(1, normalized.length() - 1).strip();
        }
        return normalized;
    }

    private static List<String> testingStrategyHeaders(String spec) {
        int start = spec.indexOf("## Testing Strategy");
        if (start < 0) {
            return List.of();
        }
        return spec.substring(start).lines().map(String::strip).filter(line -> line.startsWith("|")).findFirst().map(line -> line.split("\\|", -1)).map(columns -> {
            List<String> headers = new ArrayList<>();
            int lastContentColumn = columns.length - 1;
            if (columns[lastContentColumn].isBlank()) {
                lastContentColumn--;
            }
            for (int index = 1; index <= lastContentColumn; index++) {
                headers.add(normalizeTestingCell(columns[index]));
            }
            return List.copyOf(headers);
        }).orElseGet(List::of);
    }

    /** Empty when the section is not a table, which the SPEC gate reports as a missing decision. */
    private static List<String> hiddenVariantCells(String spec) {
        return testingStrategyRows(spec).stream().map(TestingStrategyRow::hiddenDecision).filter(cell -> !cell.isBlank()).toList();
    }

    /** Invalid and incomplete rows are retained so the SPEC gate can reject them with their exact seam ID. */
    static List<RiskInventoryRow> riskInventoryRows(String spec) {
        int start = spec.indexOf("## Contract Risk Inventory");
        if (start < 0) {
            return List.of();
        }
        List<RiskInventoryRow> rows = new ArrayList<>();
        boolean pastHeader = false;
        for (String line : spec.substring(start).lines().map(String::strip).toList()) {
            if (line.startsWith("## ") && !line.startsWith("## Contract Risk Inventory")) {
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
            String[] columns = line.split("\\|", -1);
            String seam = columns.length > 1 ? normalizeTestingCell(columns[1]) : "";
            String rules = columns.length > 2 ? normalizeTestingCell(columns[2]) : "";
            String partitions = columns.length > 3 ? normalizeTestingCell(columns[3]) : "";
            if (!seam.isBlank()) {
                rows.add(new RiskInventoryRow(seam, rules, partitions));
            }
        }
        return List.copyOf(rows);
    }

    static List<String> riskPartitionIds(String spec) {
        return riskInventoryRows(spec).stream().flatMap(row -> riskPartitionIds(row).stream()).toList();
    }

    private static List<String> riskPartitionIds(RiskInventoryRow row) {
        if (row.admittedPartitions().isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String partition : row.admittedPartitions().split(";")) {
            java.util.regex.Matcher matcher = Pattern.compile("(S[1-9][0-9]*\\.P[1-9][0-9]*):\\s*\\S.*").matcher(partition.strip());
            if (!matcher.matches() || !matcher.group(1).startsWith(row.seamId() + ".")) {
                return List.of();
            }
            ids.add(matcher.group(1));
        }
        return List.copyOf(ids);
    }

    private static String section(String document, String heading) {
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int next = document.indexOf("\n## ", start + heading.length());
        return next < 0 ? document.substring(start) : document.substring(start, next);
    }

    /** The {@code student-creates} types from the frozen specification. */
    private List<String> enforcedStudentCreatedTypes(InteractiveSandbox sandbox, String sessionId) {
        return specStudentCreatedTypes(authoritativeSpec(sandbox, sessionId));
    }

    /** The scaffold the template must ship to the student; only enforceable bare names count. */
    static List<String> specScaffoldTypes(String spec) {
        return designTableRows(spec).stream().filter(row -> "given".equals(row.status()) || "stubbed".equals(row.status())).map(DesignRow::type)
                .filter(StageCheckService::isEnforceableTypeName).toList();
    }

    private String readSpec(InteractiveSandbox sandbox, String sessionId) {
        return execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/SPEC.md");
    }

    private String authoritativeSpec(InteractiveSandbox sandbox, String sessionId) {
        return approvedSpecs.approved(sessionId).orElseGet(() -> workspaceSpecFallback ? readSpec(sandbox, sessionId) : "");
    }

    /**
     * Where the type is declared under the given repository: a file named after it OR any source file declaring it. Both probes are needed — a filename alone misses a nested or
     * secondary declaration hidden in another file, and a declaration alone misses a stray {@code Type.md} or {@code Type.java.orig}. Type names are validated as bare
     * identifiers and {@code exec} spawns without a shell, so neither argument can inject. Fails open on a tooling error.
     */
    private String findTypeDeclarations(InteractiveSandbox sandbox, String sessionId, String repo, String type) {
        String root = GenerationWorkspaceService.WORKSPACE + "/" + repo;
        String byName = execRead(sandbox, sessionId, "find", root, "-type", "f", "-name", type + ".*", "-not", "-path", "*/target/*", "-not", "-path", "*/build/*", "-not", "-name",
                "*.md", "-not", "-name", "*.txt", "-not", "-name", "*.orig", "-not", "-name", "*.class");
        String declarationPattern = "(^|[;{}])[[:space:]]*((public|protected|private|static|abstract|final|sealed|non-sealed)[[:space:]]+)*"
                + "(class|interface|enum|record|trait|struct|protocol)[[:space:]]+" + type + "\\b";
        String byDeclaration = execRead(sandbox, sessionId, "grep", "-rlE", declarationPattern, root, "--exclude-dir=target", "--exclude-dir=build", "--exclude=*.md",
                "--exclude=*.txt");
        return Stream.of(byName, byDeclaration).flatMap(String::lines).map(String::strip).filter(line -> !line.isBlank()).distinct().collect(Collectors.joining("\n"));
    }

    private StageCheckResult checkTests(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> seedTestsFiles,
            SeededStructuralTests seededStructuralTests) {
        Set<String> seededStructuralTestNames = seededStructuralTests.testNames();
        AgentVerifyReport report;
        try {
            report = verifier.selfCheckTestsStage(sandbox, sessionId, exercise, seedTestsFiles, seededStructuralTests);
        }
        catch (RuntimeException e) {
            return new StageCheckResult(false, "Could not run the differential self-check: " + e.getMessage(), null);
        }
        String observation = report.toTestsStageObservation();
        if (!report.wouldBeAccepted()) {
            List<String> studentCreatedTypes = enforcedStudentCreatedTypes(sandbox, sessionId);
            String ownershipRepair = studentCreatedTypes.isEmpty() ? ""
                    : "\nThe approved student-created types are " + studentCreatedTypes
                            + ". Do not add their declarations to the template or edit SPEC.md. If direct references make the template test compilation fail, rewrite those tests "
                            + "using the seeded reflection utilities/Class.forName; use a dynamic proxy when the context must receive the omitted interface.";
            return new StageCheckResult(false, "The executable test artifacts do not yet satisfy the TESTS-stage checks:\n" + observation + ownershipRepair, report);
        }
        // Checked only once the differential is green, so a missing plan never drowns out failing tests in the feedback.
        String planJson = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
        if (planJson.isBlank()) {
            List<String> behavioralTestNames = report.exactTestNames().stream().filter(name -> !seededStructuralTestNames.contains(name) && !BuildGateTestNames.isBuildGate(name))
                    .toList();
            return new StageCheckResult(false, "The differential passed, but /workspace/test-plan.json is missing. Write it now, implementing the specification's Testing "
                    + "Strategy: {\"tests\":[{\"name\":\"<exact test name>\",\"seamWeightTier\":1..3,\"visibility\":\"ALWAYS\"|\"AFTER_DUE_DATE\"}]}. Use exactly these behavioral test "
                    + "names verify reported: " + behavioralTestNames + ". Do not include server-seeded structural checks; Artemis keeps those visible and zero-weight: "
                    + seededStructuralTestNames + ".", report);
        }
        GeneratedTestPlan plan;
        try {
            plan = GeneratedTestPlan.parse(planJson);
        }
        catch (IllegalArgumentException e) {
            return new StageCheckResult(false, "The differential passed, but test-plan.json is invalid: " + e.getMessage(), report);
        }
        String specification = authoritativeSpec(sandbox, sessionId);
        List<String> planReasons = ExerciseIntegrityGate.approvedTestPlanReasons(specification, planJson, report.exactTestNames(), exercise.getDueDate() != null,
                seededStructuralTestNames);
        if (!planReasons.isEmpty()) {
            return new StageCheckResult(false, "The differential passed, but the grading plan does not implement the approved Testing Strategy:\n" + String.join("\n", planReasons),
                    report);
        }
        String planSummary = "Grading plan accepted: " + plan.tests().size() + " test(s), " + plan.hiddenEntries().size() + " hidden until the due date.";
        return new StageCheckResult(true, observation + "\n" + planSummary, report);
    }

    private StageCheckResult checkStatement(InteractiveSandbox sandbox, String sessionId, @Nullable AgentVerifyReport lastTestsReport, Set<String> seededStructuralTestNames) {
        String statement = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/problem-statement.md");
        if (statement.isBlank()) {
            return StageCheckResult.failed("problem-statement.md is missing or empty. Write the student-facing problem statement before submitting.");
        }
        if (ProblemStatementBindingChecker.hasTaskBindingInsideMarkdownCode(statement)) {
            return StageCheckResult.failed("A [task][Title](testName) marker is hidden inside Markdown code, so Artemis renders it as code instead of a task checklist item. "
                    + "Remove the surrounding inline backticks or fenced code block and put each task marker on its own plain Markdown line.");
        }
        List<String> proseLeaks = ProblemStatementBindingChecker.proseHygieneLeaks(statement);
        if (!proseLeaks.isEmpty()) {
            return StageCheckResult.failed("The student-facing statement contains internal authoring or grading vocabulary: " + proseLeaks
                    + ". Rewrite it self-contained: describe the required behavior directly and never name internal contracts, workspace paths, reference artifacts, or test machinery.");
        }
        GeneratedTestPlan plan = null;
        Set<String> hiddenNames = Set.of();
        if (lastTestsReport != null) {
            String planJson = execRead(sandbox, sessionId, "cat", GenerationWorkspaceService.WORKSPACE + "/test-plan.json");
            if (planJson.isBlank()) {
                return StageCheckResult.failed("The accepted test-plan.json handoff from the TESTS stage is missing or unreadable. Stop statement authoring rather than guessing "
                        + "task bindings; restore the accepted grading plan and retry this stage.");
            }
            try {
                plan = GeneratedTestPlan.parse(planJson);
                hiddenNames = plan.hiddenEntries().stream().map(GeneratedTestPlan.Entry::name).map(ProblemStatementBindingChecker::normalizeTestName)
                        .collect(Collectors.toUnmodifiableSet());
            }
            catch (IllegalArgumentException e) {
                return StageCheckResult.failed("The accepted test-plan.json handoff from the TESTS stage is no longer valid: " + e.getMessage()
                        + " Restore the accepted grading plan before authoring the statement.");
            }
        }
        if (lastTestsReport != null) {
            List<String> exactTestNames = lastTestsReport.exactTestNames();
            List<String> unresolved = ProblemStatementBindingChecker.unresolvedTaskBindings(statement, exactTestNames, exactTestNames.size(), seededStructuralTestNames);
            if (!unresolved.isEmpty()) {
                List<String> bindableNames = Stream
                        .concat(ProblemStatementBindingChecker.bindableTestNames(exactTestNames, hiddenNames).stream(), seededStructuralTestNames.stream()).distinct().sorted()
                        .toList();
                return StageCheckResult.failed("These [task] bindings reference names that match no actual test: " + unresolved
                        + ". A [task]'s parenthesised names must be exact, visible test names from the TESTS stage, copied verbatim: " + bindableNames + ".");
            }
            // Artemis renders testsColor links interactively (pass/fail per diagram element), so a name matching no test is a dead link the student can never satisfy and is held
            // to the same resolution standard as a [task] binding.
            List<String> deadDiagramLinks = ProblemStatementBindingChecker.unresolvedTestsColorNames(statement, exactTestNames, seededStructuralTestNames);
            if (!deadDiagramLinks.isEmpty()) {
                return StageCheckResult.failed("These diagram testsColor(...) names match no actual test: " + deadDiagramLinks
                        + ". Use the exact test names from the TESTS stage (behavioural or seeded structural), or remove the link: " + exactTestNames + ".");
            }
        }
        if (!seededStructuralTestNames.isEmpty()) {
            Set<String> boundNames = ProblemStatementBindingChecker.boundTestNames(statement).stream().map(ProblemStatementBindingChecker::normalizeTestName)
                    .collect(Collectors.toSet());
            List<String> missingStructural = seededStructuralTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).filter(name -> !boundNames.contains(name))
                    .sorted().toList();
            Set<String> structuralNames = seededStructuralTestNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
            List<String> duplicateStructural = ProblemStatementBindingChecker.duplicateTaskBindings(statement).stream().filter(structuralNames::contains).sorted().toList();
            if (!missingStructural.isEmpty() || !duplicateStructural.isEmpty()) {
                return StageCheckResult.failed("Every visible server-seeded structural check must be bound exactly once on the [task] that creates or declares its owner type/API."
                        + (missingStructural.isEmpty() ? "" : " These structural checks are not bound: " + missingStructural + ".")
                        + (duplicateStructural.isEmpty() ? "" : " These structural checks are bound more than once: " + duplicateStructural + "."));
            }
        }
        if (plan != null) {
            List<String> groupingReasons = ProblemStatementBindingChecker.seamTaskGroupingReasons(statement, plan);
            if (!groupingReasons.isEmpty()) {
                return StageCheckResult.failed(
                        "The statement must have one task per student-work seam, with all visible tests for that seam bound to that task: " + String.join(" ", groupingReasons));
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
        String authoritativeSpec = authoritativeSpec(sandbox, sessionId);
        boolean diagramPromised = ProblemStatementBindingChecker.specPromisesDiagram(authoritativeSpec);
        if (diagramPromised && !statement.contains("@startuml")) {
            return StageCheckResult.failed("SPEC.md's '## Diagram' section says yes, but the statement contains no @startuml diagram. Add the PlantUML class diagram after "
                    + "the tasks it illustrates (with testsColor links). The accepted diagram decision cannot be revoked after the specification gate.");
        }
        List<String> duplicateHeadings = ProblemStatementBindingChecker.duplicateHeadings(statement);
        if (!duplicateHeadings.isEmpty()) {
            return StageCheckResult.failed("The statement repeats these headings verbatim: " + duplicateHeadings + ". Merge or remove the duplicate sections.");
        }
        // The same defect one level down: a repeated instruction sentence reads to a student as several different requirements.
        List<String> duplicateInstructions = ProblemStatementBindingChecker.duplicateInstructionLines(statement);
        if (!duplicateInstructions.isEmpty()) {
            return StageCheckResult.failed(
                    "The statement repeats these instruction lines verbatim: " + duplicateInstructions + ". Keep one statement of each requirement and delete the repeats.");
        }
        List<String> bareTasks = ProblemStatementBindingChecker.tasksWithoutInstruction(statement);
        if (!bareTasks.isEmpty()) {
            return StageCheckResult.failed("These [task] bindings have no student-facing instruction before the next task or heading: " + bareTasks
                    + ". Follow each task with concise imperative prose naming the types or members the student must implement; a checkbox alone is not an exercise instruction.");
        }
        return StageCheckResult.passed("");
    }

    /**
     * The body of one markdown section, up to the next heading at any level. Stopping at a nested subsection matters: a {@code ### Worked examples} table inside {@code ## Rules}
     * would otherwise have its rows counted as rules, and an inflated count can only ever cause a false rejection.
     */
    private static String sectionBody(String document, String heading) {
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int bodyStart = start + heading.length();
        int nextSection = document.indexOf("\n## ", bodyStart);
        int nextSubsection = document.indexOf("\n### ", bodyStart);
        int next = nextSection < 0 ? nextSubsection : nextSubsection < 0 ? nextSection : Math.min(nextSection, nextSubsection);
        return next < 0 ? document.substring(bodyStart) : document.substring(bodyStart, next);
    }

    private String execRead(InteractiveSandbox sandbox, String sessionId, String... command) {
        try {
            SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, command);
            return result.isSuccess() && result.stdout() != null ? result.stdout() : "";
        }
        catch (RuntimeException e) {
            log.debug("Staged generation read failed ({}): {}", String.join(" ", command), e.getMessage());
            return "";
        }
    }
}
