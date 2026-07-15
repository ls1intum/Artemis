package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.LanguageGenerationProfile;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Builds the system prompt for the exercise-generation agent.
 * <p>
 * Encodes the verifier contract, repository layout, self-check workflow, and language-specific conventions that the model cannot infer from an empty scaffold.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class AgentSystemPromptService {

    private final SandboxBuildCommandService sandboxBuildCommandService;

    public AgentSystemPromptService(SandboxBuildCommandService sandboxBuildCommandService) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
    }

    /**
     * @param exercise the exercise being generated or adapted
     * @return the full system prompt in the default {@link GenerationMode#GENERATE} framing
     */
    public String build(ProgrammingExercise exercise) {
        return build(exercise, GenerationMode.GENERATE);
    }

    /**
     * Builds the system prompt, branching only its top framing on the run intent: {@link GenerationMode#GENERATE} authors the exercise from the plan, while
     * {@link GenerationMode#ADAPT} tells the agent to apply requested feedback to the seeded exercise while preserving unaffected content. The remaining guidance is shared.
     *
     * @param exercise the exercise being generated or adapted
     * @param mode     the explicit run intent (generate a fresh exercise vs. adapt the existing one)
     * @return the full system prompt for the given mode
     */
    public String build(ProgrammingExercise exercise, GenerationMode mode) {
        ProgrammingLanguage language = exercise.getProgrammingLanguage();
        String languageName = language != null ? language.toString() : "the exercise language";
        String problemStatementGuidance = isNonTrivialProblemStatement(exercise.getProblemStatement()) ? mode == GenerationMode.ADAPT
                ? "- problem-statement.md: the CURRENT statement. Apply the feedback as a targeted revision and preserve its requirements and prose where the feedback is "
                        + "silent. Align only the impacted statement, solution, template, tests, and task bindings."
                : "- problem-statement.md: the CURRENT statement and starting point. The user brief is authoritative and may refine or replace it; preserve requirements where "
                        + "the brief is silent. Align the resulting statement, solution, template, tests, and task bindings, and remove internal notes."
                : "- problem-statement.md : the task description shown to students (you write it; it may currently be empty or a placeholder)";
        String groundedWorkflow = mode == GenerationMode.ADAPT
                ? """
                        1. Inspect the existing statement, solution, template, tests, and task bindings before editing. Identify the smallest set of artifacts the feedback actually affects.
                        2. Call `verify` early to observe the initial state, exact reported test names, binding problems, and build failures.
                        3. Make surgical edits only to the impacted artifacts. Do not delete or rename existing source files, public APIs, tests, task bindings, or instructor prose unless the
                        feedback requires it. Re-run `verify` after meaningful changes; raw shell exit codes are only debugging aids.
                        4. Before submission, re-read the feedback and every changed file. Confirm each change is required, every explicitly preserved artifact remains, the solution passes,
                        and every task-bound test fails on the template. Run `verify` once more. Submit only after the verdict is ACCEPTED, then stop.
                        """
                : """
                        1. Inspect `solution`, `template`, and `tests`. The exercise source and test roots are clean; preserve the supplied harness and build files.
                        2. Call `verify` early to observe the initial state, exact reported test names, binding problems, and build failures.
                        3. Implement the smallest coherent exercise requested by the brief. Re-run `verify` after meaningful changes. Its structured solution/template results and final verdict are the
                        authoritative evidence; raw shell exit codes are only debugging aids.
                        4. Before submission, compare statement promises with assertions in both directions, confirm the solution passes and every task-bound test fails on the template, and run `verify`
                        once more. Remove abandoned sources. Submit only after the verdict is ACCEPTED, then stop.
                        """;
        String testSourceGuidance = mode == GenerationMode.ADAPT ? "Edit only exercise-specific test sources required by the feedback; preserve all others."
                : "Replace only exercise-specific test source files.";
        String prompt = """
                You author production-quality Java programming exercises for Artemis in the `/workspace` sandbox.

                WORKSPACE
                %s
                - solution/: reference implementation
                - template/: student starting point
                - tests/: instructor tests and immutable build harness
                - verify.sh: grader-equivalent build recipe
                - reference/: optional read-only example of Java/Ares conventions; never copy its topic, design, or code

                Programming language: %s%s

                THE CONTRACT
                1. The solution compiles and passes every behavioural test.
                2. The template compiles, but every task-bound test fails. Normally keep the solution's public API with readable TODO stubs rather than solution logic or validation. Prefer a TODO followed by
                `throw new UnsupportedOperationException("Not implemented")`; a placeholder value is acceptable only when it is wrong for every test. The template is student-facing code, not a
                description of how the grader is defeated.
                3. The same meaningful tests run against solution and template. Cover the central behaviour, representative boundaries, state transitions, and every stated exceptional case.
                Give each behavioural assertion a concise failure message that tells the student what contract failed. Include a non-degenerate witness for broad claims such as arbitrary nesting
                or operation sequences. Do not use @DisplayName because Artemis binds reported method names.
                4. The problem statement, code, and tests describe one coherent exercise. Every promise in the statement has a task-bound assertion, and every behavioural assertion corresponds
                to a stated requirement. If a claim is not graded, narrow or remove it rather than inventing unsupported confidence.
                5. Keep student work focused on the stated learning objective. Provide routine data-holder constructors and accessors in the template unless implementing them is an explicit,
                separately tested objective. Prefer the smallest public API that supports clear assessment.

                STUDENT-FACING STATEMENT
                Write one `#` title, a short motivating objective, a precise public API and input/output contract, and a `## Tasks` section. Pin relevant types, bounds, ordering, tie-breaking,
                tolerance, mutation, and exception semantics only where the implementation enforces them and a test observes them. Avoid unverifiable complexity or allocation claims. Keep internal
                details about the agent, sandbox, verifier, harness, and raw test identifiers out of visible prose.
                Remove drafting notes, unresolved instructor decisions, and other authoring-process sections from the final student-facing statement; resolve them into the contract or omit them.

                Provide representative worked examples only where they clarify important, non-obvious behaviour. Use a code block, table, or precise prose, whichever communicates the contract
                most clearly. Examples must agree with the implementation and tests but must not reproduce a graded test's exact composite input. Use a smaller or materially different input that
                teaches the rule without revealing the oracle. Use a precise API block for a multi-type design; add UML only when it materially clarifies that design. Keep authored prose and source
                text in plain ASCII except when non-ASCII data is intrinsic to the exercise.

                ARTEMIS TASK BINDINGS
                Use one line per student-facing requirement:
                  [task][Short human title](exactTestNameA,exactTestNameB)
                Copy test names verbatim from `verify`; never guess, rename, add parentheses, or remove prefixes. Group related tests into coherent tasks rather than creating one microtask per
                test. Every real behavioural test appears exactly once across the task bindings. Do not bind build gates, aggregates, harness checks, or structural checks already satisfied by the
                template. Titles describe behaviour and never expose raw test names. The exact lowercase `[task]` keyword is required.

                LAYOUT AND HARNESS
                The verifier checks the assignment out under `assignment/` beside the tests. Read the existing Maven/Gradle harness to learn its source layout, package, and expected test filenames,
                then place solution, template, and test sources accordingly. Preserve package names across repositories. Build manifests, wrappers, plugins, reporter configuration, commands,
                placeholders, and report paths in all three repositories are seeded and managed by Artemis; do not edit or replace them. %s%s

                GROUNDED WORKFLOW
                %s

                SAFE TOOL USE
                Your only tools are bash, read_file, write_file, edit_file, delete_file, verify, and submit. Use `delete_file` to remove a generated file that is misplaced or no longer needed. Use `verify` for builds; it handles the network-isolated CI scaffold. Use bash only for inspection,
                safe source-file removal, and `sh verify.sh solution` or `sh verify.sh template` when detailed output helps. Never run repository Gradle/Maven directly or change build infrastructure
                to work around offline dependency resolution. Do not edit file contents through bash; use write_file or edit_file. There is no apply_patch tool, so
                never call it directly or through bash. Re-read only a file that changed or whose exact contents are needed after a failed edit. Never fabricate build or test results, and keep
                routine narration brief.%s
                """
                .formatted(problemStatementGuidance, languageName, buildContextSection(exercise), testSourceGuidance, staticCodeAnalysisGuidance(exercise), groundedWorkflow,
                        LanguageGenerationProfile.guidanceFor(exercise));
        return mode == GenerationMode.ADAPT ? ADAPT_MODE_FRAMING + prompt : prompt;
    }

    /**
     * Prepended in {@link GenerationMode#ADAPT} to require a targeted revision of the seeded exercise while preserving unrelated work.
     */
    private static final String ADAPT_MODE_FRAMING = """
            ADAPT MODE: revise the existing seeded exercise. Apply the user's feedback with the smallest coherent change, preserve requirements and artifacts where the feedback is silent,
            and keep the statement, solution, template, tests, and task bindings consistent. Do not rewrite unrelated work. The contract below still applies.

            """;

    /**
     * A tight, exercise-specific build-context block: the resolved project type, package/module name, checkout layout, the build phase commands the grader runs, and the report
     * locations it parses. Derived from the same recipe behind {@code verify.sh} so it cannot drift from what the grader runs, closing the "verify.sh passed but real CI scored
     * zero" class. Returns the empty string if the build context cannot be resolved, so prompt building never fails on it.
     *
     * @param exercise the exercise being generated or adapted
     * @return the build-context section (prefixed with a blank line), or {@code ""} when it cannot be resolved
     */
    private String buildContextSection(ProgrammingExercise exercise) {
        SandboxBuildCommandService.BuildContextSummary context;
        try {
            context = sandboxBuildCommandService.describeBuildContext(exercise);
        }
        catch (RuntimeException e) {
            return "";
        }
        StringBuilder section = new StringBuilder(
                "\n\nTHIS EXERCISE'S BUILD CONTEXT (resolved by Artemis — the grader runs exactly this; do NOT change how it builds or where reports are written):");
        if (exercise.getProjectType() != null) {
            section.append("\n- Project type: ").append(exercise.getProjectType());
        }
        String packageName = exercise.getPackageName();
        if (packageName != null && !packageName.isBlank()) {
            section.append("\n- Module / package name: ").append(packageName).append("  (use this EXACT name across solution, template, and tests so the shared tests resolve)");
        }
        String testLocation = context.testCheckoutDir().isBlank() ? "the build root, next to assignment/" : context.testCheckoutDir() + "/";
        section.append("\n- Layout: your assignment is checked out into assignment/; the tests into ").append(testLocation);
        if (context.materializesSolution()) {
            section.append("; a sibling solution/ is also checked out because this harness references it");
        }
        if (!context.phaseScripts().isEmpty()) {
            section.append("\n- Build phases (run in order from the build root, verbatim):");
            int index = 1;
            for (String phase : context.phaseScripts()) {
                section.append("\n    ").append(index++).append(". ").append(capCommand(phase));
            }
        }
        String reports = context.reportGlobs().stream().distinct().collect(Collectors.joining(", "));
        if (!reports.isBlank()) {
            section.append("\n- Test reports the grader reads (keep the reporter writing here, unchanged): ").append(reports);
        }
        if (!context.scaReportFiles().isEmpty()) {
            section.append("\n- Static code analysis is ON; the grader parses these report files: ").append(String.join(", ", context.scaReportFiles()));
        }
        return section.toString();
    }

    /** Max chars of a build-phase command previewed in the prompt before eliding, so a long script is listed as a hint rather than dumped in full. */
    private static final int MAX_COMMAND_PREVIEW_CHARS = 200;

    /** Collapses a (possibly multi-line) build-phase command to a single trimmed line and caps its length, so the prompt lists the command without dumping a long script. */
    private static String capCommand(String command) {
        String oneLine = command.replaceAll("\\s+", " ").trim();
        return oneLine.length() > MAX_COMMAND_PREVIEW_CHARS ? oneLine.substring(0, MAX_COMMAND_PREVIEW_CHARS) + " …" : oneLine;
    }

    /**
     * Extra contract clause when static code analysis is enabled: the reference solution must be clean of findings in the graded categories, because production folds an SCA
     * penalty
     * into the score and the verifier rejects a solution whose build trips a graded SCA category (it would otherwise grade below 100%). Empty when SCA is disabled.
     */
    private static String staticCodeAnalysisGuidance(ProgrammingExercise exercise) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled())) {
            return "";
        }
        return "\n\nSTATIC CODE ANALYSIS IS ENABLED and graded. Keep the Java reference solution free of graded SpotBugs and Checkstyle findings, or it cannot receive full credit. The "
                + "template need not be lint-clean; only the solution must be clean.";
    }

    /**
     * Minimum stripped length for a problem statement to count as a real instructor spec (vs. an empty field or a short placeholder) that the agent must build the exercise to
     * match.
     */
    private static final int NON_TRIVIAL_PROBLEM_STATEMENT_MIN_CHARS = 40;

    /**
     * Whether the exercise already carries a real, instructor-provided problem statement to build against, rather than authoring one from scratch (a present brief may still refine
     * or change it). Used by both the system prompt (spec vs from-scratch framing) and the resource (mode-aware default instruction), so the two always agree.
     *
     * @param problemStatement the exercise's current problem statement (may be {@code null})
     * @return {@code true} if it is non-trivial enough to be treated as the spec
     */
    public boolean isNonTrivialProblemStatement(@Nullable String problemStatement) {
        return problemStatement != null && problemStatement.strip().length() >= NON_TRIVIAL_PROBLEM_STATEMENT_MIN_CHARS;
    }

    /**
     * Resolves the instruction for a generation run. A generation brief may replace the current task; adaptation feedback is always a targeted revision that preserves artifacts
     * where it is silent. With no brief the default matches an existing statement or authors a fresh exercise from scratch.
     *
     * @param request  the generation request holding the optional prompt
     * @param exercise the exercise being generated or adapted
     * @return the resolved instruction for the agent
     */
    public String resolvePrompt(ExerciseGenerationRequestDTO request, ProgrammingExercise exercise) {
        String brief = request.prompt() == null ? "" : request.prompt().strip();
        // A present brief is the authoritative instruction for this run and may change the task entirely, so it can override a statement on a different topic; the statement is the
        // starting point, preserved where the brief is silent. With no brief, the statement alone binds.
        boolean hasSpec = isNonTrivialProblemStatement(exercise.getProblemStatement());
        if (!brief.isBlank()) {
            if (request.effectiveMode() == GenerationMode.ADAPT) {
                return "Apply this feedback as a targeted revision of the existing exercise. Preserve every statement requirement and artifact where the feedback is silent, and "
                        + "change only the statement, solution, template, tests, and task bindings that the feedback requires: " + brief;
            }
            if (hasSpec) {
                return "problem-statement.md holds the exercise's current problem statement. Apply this instruction, authoritative for this run, which may refine that statement or "
                        + "change the task (topic, named types, requirements); where it is silent, keep the statement's intent and stated requirements, then build the solution, "
                        + "template, and tests to match the resulting statement and add the [task] bindings for the tests you write: " + brief;
            }
            return brief;
        }
        if (hasSpec) {
            return "An initial problem statement is already in problem-statement.md. Treat it as the authoritative specification and build the solution, template, and tests to match "
                    + "it, keeping its intent and every stated requirement; refine its wording and add the [task] bindings for the tests you write.";
        }
        return "Generate a complete, correct programming exercise: a reference solution that passes all tests, a template that compiles but fails the tests, and meaningful tests.";
    }

    /**
     * The oracle-verifiable languages Hyperion offers for one-click whole-exercise generation, defined on {@link LanguageGenerationProfile#supportedLanguages()}. Exposed so the
     * resource can both guard a run and serve the set to clients rather than have them hardcode it.
     *
     * @return the immutable set of generation-supported languages
     */
    public Set<ProgrammingLanguage> supportedGenerationLanguages() {
        return LanguageGenerationProfile.supportedLanguages();
    }

    /**
     * @param exercise the exercise configuration to check, or {@code null}
     * @return whether Hyperion can verify generation for its language and project type
     */
    public boolean isGenerationSupported(@Nullable ProgrammingExercise exercise) {
        return LanguageGenerationProfile.isSupported(exercise);
    }
}
