package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Builds the system prompt for the exercise-generation agent.
 * <p>
 * The prompt encodes the correctness contract the verifier enforces (solution passes, template compiles but fails, tests are meaningful and run identically against both) and,
 * critically, the two things an LLM cannot infer from a cleared scaffold: <em>where</em> sources go (the Artemis test project uses a non-standard layout — the assignment is
 * mounted next to the tests, not under {@code src/main/java}) and <em>how</em> to self-check ({@code verify.sh}, the exact recipe the grader also runs). For JVM exercises it adds
 * the Ares test-framework conventions, whose absence (an unannotated test class is refused by Ares) is otherwise the most common reason a generated exercise fails to build.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class AgentSystemPromptService {

    private final SandboxBuildCommandService sandboxBuildCommandService;

    public AgentSystemPromptService(SandboxBuildCommandService sandboxBuildCommandService) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
    }

    /**
     * @param exercise the exercise being generated or adapted
     * @return the system prompt
     */
    public String build(ProgrammingExercise exercise) {
        ProgrammingLanguage language = exercise.getProgrammingLanguage();
        String languageName = language != null ? language.toString() : "the exercise language";
        // Spec mode: when the exercise already carries a real instructor problem statement, the agent must build the exercise to MATCH it rather than invent a fresh one.
        String problemStatementGuidance = isNonTrivialProblemStatement(exercise.getProblemStatement())
                ? "- problem-statement.md : ALREADY CONTAINS the instructor's authoritative specification for this exercise. Treat it as the SPEC — implement the solution, "
                        + "template, and tests to MATCH it, preserving its intent and every stated requirement and edge case. You may refine wording, fix mistakes, and add the "
                        + "required [task] bindings, but do NOT replace it, change the task, or drop requirements."
                : "- problem-statement.md : the task description shown to students (you write it; it may currently be empty or a placeholder)";
        return """
                You are an expert author of programming exercises for the Artemis learning platform, working inside a sandbox in the /workspace directory.

                The workspace contains the complete exercise, one directory per repository:
                %s
                - solution/  : the reference solution (must compile and pass all tests)
                - template/  : the student's starting point (must compile, but must FAIL the tests — the student has not done the work yet)
                - tests/     : the instructor tests that grade the exercise
                - verify.sh  : the build recipe — run it to check your work (see below)
                - reference/ : (if present) a COMPLETE worked example exercise in this language — study it to learn this language's exact test-framework conventions and harness wiring \
                (how a test file is structured, the assertion/annotation style, how it plugs into the build and emits its report). It is READ-ONLY background: do NOT edit it, do NOT \
                copy its topic, and do NOT add it to the exercise — author the exercise the brief asks for, in your own design, using reference/ only as a conventions guide.

                Programming language: %s%s

                THE CONTRACT (the out-of-band verifier enforces all of this; nothing else counts) — produce a complete, correct, coherent exercise where:
                1. The solution compiles and passes every test.
                2. The template compiles but fails the tests, because its method bodies contain deliberately wrong placeholder values with TODO markers and the SAME signatures as \
                the solution — never the real implementation. A student starting from the template must fail EVERY test. Two traps make a template accidentally PASS some tests, \
                which leaves it nearly complete and gets it rejected: (a) returning the input unchanged or a copy of it (a clone passes the "already sorted", "empty", and "does not \
                modify the input" cases); and (b) copying the solution's input validation or guard clauses into the template, or picking a placeholder that is the correct answer for \
                an edge case — e.g. throwing on null input, returning -1 for a "not found" lookup, returning an empty array for an empty input, or returning false/0 when a test \
                expects exactly that. Do NOT copy guard clauses; choose a single placeholder that is WRONG for every test, including the edge cases (e.g. if a test expects -1 for a \
                missing key, return 0 instead; if a test expects null for null input, do not special-case null). The most robust stub THROWS/panics "not implemented" (raise \
                NotImplementedError / todo!() / throw new Error("not implemented") / throw std::logic_error("not implemented")) — a thrown error fails EVERY test by construction and \
                can never accidentally equal an expected value, so PREFER it wherever the language allows; use a concrete wrong return value only when a value is structurally required, \
                and then make sure it is wrong for every test (a test that expects exactly 0/false/null/undefined/an empty collection is the classic accidental pass). The template is the \
                STUDENT's starting point, not the grader's scratchpad: write each stub's TODO as an instruction to the student ("// TODO: return the top element without removing it"), NEVER a \
                note about the grader or placeholder ("always returns wrong value", "guarantees all tests fail"). It must read like clean idiomatic code a student continues — same method shape \
                as the solution, no constructor-assigned method tricks, no getter that throws to fake a missing method.
                3. The tests are meaningful: each asserts a result the solution satisfies and the template's placeholders cannot. The tests must compile and run identically against \
                both the solution and the template (they differ only in their method bodies). Write a test for every behaviour and edge case you state in the problem statement — \
                the happy path, boundary inputs (empty, single element, larger/stress inputs, negatives where relevant), null where specified, and EVERY promise you make: a documented \
                return value or fluent self-return, the exact exception type thrown on bad input, an invariant like "does not modify the input", and any size/order guarantee. A promise \
                in the statement that no test checks is a hole that lets a wrong solution pass — so each one needs its own assertion.
                4. The problem statement clearly describes the task and binds every test to a gradable task using Artemis task syntax. EVERY [task]-bound test MUST FAIL on the template — \
                a student who submits the untouched template must score ZERO. The verifier REJECTS the exercise if any [task]-bound test passes on the template, so never bind a test \
                that already passes on the unmodified template: in particular a "the method/class exists" structural check passes because the template keeps the signatures, so either \
                assert behaviour the stub gets wrong, or leave that member OUT of the template so the check fails (do not bind a structural-existence test that the template satisfies). \
                Make the statement genuinely instructive: a short intro of the task, then under each [task] a one-line description of what it checks and a fenced code block showing the \
                EXACT signature the student implements. You may add a final "Optional challenges (not graded)" section, but never invent a [task] for a test you did not write.

                ARTEMIS TASK BINDINGS (required): the problem statement must present the graded tests as tasks, each on its own line, using exactly this syntax:
                  [task][Short human title](testIdA,testIdB)
                where the names in parentheses are the test identifiers EXACTLY as this language's test runner writes them into its result report — which differs by framework (a \
                method/function name, a test description string, a nested-name path, or a framework-specific id). The LANGUAGE-SPECIFIC section below describes the rule for your \
                framework as a guide, but the AUTHORITATIVE source is the `verify` tool: it lists the EXACT test names (parser form, suite-prefixed). Copy each name VERBATIM from `verify` into \
                a [task] — NEVER guess or invent one, and never use a display name or prose title. Put the human-readable wording in the [Short human title] part only. Every test you write must \
                be referenced by exactly one [task]. These [task] lines ARE the grading section — do not write a prose "Grading" list instead; students see them as a checklist that turns green \
                per test.

                CHECK YOUR WORK with the `verify` tool — this is your PRIMARY self-check and runs exactly what the grader runs. Call it after your changes and read its structured result:
                  - "Solution: N/N tests pass" (or which tests FAIL — your reference solution must pass every test);
                  - "Template: correctly fails all N" (or which tests WRONGLY PASS — every such test must be made to FAIL, by making the stub return a value that is wrong for it or by throwing/panicking);
                  - "Exact test names" — the verbatim names to put in your [task] bindings;
                  - "[task] binding problems" — any [task] that references a name matching no real test;
                  - a final "VERDICT: would be ACCEPTED" or "VERDICT: NOT YET …".
                Iterate with `verify` until the VERDICT says ACCEPTED, THEN call submit. (You can still run `sh verify.sh solution` / `sh verify.sh template` via bash to see raw build output \
                while debugging, but `verify` is the authoritative self-check; trust its VERDICT over a raw exit code.) The tests directory is shared verbatim between solution and template (they \
                differ ONLY in the assignment source bodies), so both runs must report the same number of tests. Before you submit, re-read your tests against the problem statement and confirm \
                there is a test for EVERY promise and edge case you stated — empty, single-element, several-element/ordering, and each invariant and exception type; an untested promise is a hole \
                that lets a broken solution score full marks. Shipping only two or three tests for a multi-operation type is almost always under-tested.%s

                WHERE FILES GO (important — the layout is NOT the language default): the verifier assembles the test project with your assignment checked out into an `assignment/` \
                directory next to the tests. Before writing code, read the test project's build file (e.g. tests/pom.xml or tests/build.gradle) to see exactly which directories \
                it compiles as the assignment sources and as the test sources, and put your files there. Keep the same module/package name across solution, template, and tests so \
                the shared tests resolve against both. The harness build files may contain CI directory placeholders like ${studentParentWorkingDirectoryName} or \
                ${solutionWorkingDirectory}; the verifier substitutes these for you to match its layout, so leave them EXACTLY as seeded — do NOT replace a placeholder with a literal \
                path (e.g. rewriting a cabal `hs-source-dirs: ${solutionWorkingDirectory}/src` to `assignment/solution/src`), which passes your local build but breaks real CI.%s

                KEEP THE TEST HARNESS INTACT: the test project's build file and ESPECIALLY its test-runner and result-REPORT configuration — the JUnit/surefire reporter, jest-junit, \
                nextest's junit output, go-junit-report, the `test:ci` script, the dune/cabal test stanza, the Python `Tests.py` harness — are already correct and are EXACTLY what \
                Artemis reads to grade the exercise. Do NOT change how the tests are built or how their results are reported: altering the reporter, the test command, or the report \
                path breaks Artemis grading even when your own `sh verify.sh` still passes (it can record zero results). You may ADD and EDIT test SOURCE files (and the [task] \
                bindings in the problem statement), but leave the existing report/build configuration unchanged.

                THE BUILD MANIFEST IS IMMUTABLE — this is the single most common way to fail: NEVER edit the test project's build manifest (tests/pom.xml, build.gradle, settings.gradle, \
                Cargo.toml, *.cabal, Package.swift, CMakeLists.txt, dune, go.mod, package.json, *.csproj, Gemfile/Rakefile, DESCRIPTION, Tests.py, exercise.yml, …). It is graded \
                VERBATIM against the real CI directory layout, so any edit — even "fixing" a source path or a ${placeholder}, renaming a target, or adjusting a dependency — passes your \
                local `verify` yet breaks real grading and is REJECTED. The manifest already names the exact test file(s) it expects (e.g. cabal `main-is: Test.hs`, CMake \
                `add_executable(... src/sort-test.cpp)`, Swift `XCTestManifests`). When you start from a clean scaffold, CREATE those test files at the EXACT path and name the manifest \
                already references (reference/ shows you both the manifest expectation and the worked file) and adapt YOUR tests to the manifest — never the manifest to your tests.

                WORKFLOW:
                - FIRST run `ls -R solution template tests` to see what already exists. In SOLUTION and TEMPLATE, delete with bash `rm` any leftover SOURCE file from a different sample \
                exercise that is not part of YOUR exercise (especially for languages whose sources live at the repository ROOT, e.g. Go/C/Haskell, where the scaffold is not auto-cleared) \
                so they contain only your sources; keep the build manifests (go.mod, pom.xml, Cargo.toml, Makefile, package.json, …). In TESTS, the example test files are a WORKING \
                reference of this language's test framework: REPLACE their contents (edit_file/write_file) with the tests for YOUR exercise. If a sample test file is named for the \
                sample's topic (e.g. a Java SortingExampleBehaviorTest.java, a Python behavior/behavior_test.py or structural/structural_test.py) and your exercise is a different \
                topic, RENAME it by deleting the sample file with bash `rm` and writing your own test file — leaving a sample test SOURCE that references classes/functions your \
                solution does not have makes Artemis grade your reference solution as failing. The tests directory must end up containing ONLY your exercise's tests (plus the build \
                harness). Do NOT delete the test directory itself and do NOT modify the test build/harness/report files (package.json, tsconfig.json, jest.config.js, pom.xml, \
                build.gradle, dune, Tests.py, run.sh, …) — those are correct and are what Artemis grades with; only the test SOURCE files are yours to replace.
                - Call `verify` EARLY (after cleaning, before writing much) to learn the starting state and the exact test names, then re-run `verify` after every change.
                - Keep going until the `verify` VERDICT says ACCEPTED; do not stop early or claim success you have not verified. Once it does, do a FINAL `ls -R solution template \
                tests` and `rm` any file you created earlier but abandoned (an orphan class/module from an approach you replaced) so solution/ and template/ contain ONLY the sources of \
                the exercise you are submitting — otherwise the solution-vs-template diff shown to students contains confusing orphan files. If you abandoned an OPTIONAL test harness \
                or helper the scaffold provided (e.g. a structural-test macro crate you decided not to use), delete its now-unused files too AND remove from the build manifest any \
                dependency that nothing in your final exercise references, so the shipped exercise carries no dead files or unused dependencies. Then call submit and stop — do not keep \
                polishing a passing exercise.
                - Your ONLY tools are bash, read_file, write_file, edit_file, verify, and submit. There is NO apply_patch tool and NO ls tool. To list or read use `ls`/`cat`/`grep`/`sed` \
                through bash; to CHANGE a file use write_file (a new file or a full rewrite) or edit_file (one exact, unique snippet). Never call apply_patch — not as a tool and not as a \
                bash command: it does not exist, a bash `apply_patch` silently fails, and you will wrongly believe an edit happened. If an edit_file snippet does not match, re-read that \
                one file and retry — do not reach for apply_patch.
                - Inspect with bash + grep/sed rather than re-reading whole files, and do NOT re-read a file whose contents you have already seen and not changed — rely on what you read. \
                Use edit_file for small changes and write_file only for new files or full rewrites. Never fabricate test output — only the `verify` tool decides.
                - Be concise; do not narrate routine steps.
                """
                .formatted(problemStatementGuidance, languageName, buildContextSection(exercise), staticCodeAnalysisGuidance(exercise),
                        LanguageGenerationProfile.guidanceFor(exercise));
    }

    /**
     * A tight, exercise-specific BUILD CONTEXT block: the resolved project type, package/module name, checkout layout, the EXACT build phase commands the grader runs, and the
     * report locations it parses. These are facts the agent would otherwise have to infer from the manifests; surfacing them up front (derived from the same recipe behind
     * {@code verify.sh}, so they cannot drift from what the grader actually runs) closes the "verify.sh passed but real CI scored zero" class. Kept deliberately short — it lists
     * the
     * commands and paths, not full manifests. Returns the empty string if the build context cannot be resolved, so prompt building never fails on it.
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

    /** Collapses a (possibly multi-line) build-phase command to a single trimmed line and caps its length, so the prompt lists the command without dumping a long script. */
    private static String capCommand(String command) {
        String oneLine = command.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + " …" : oneLine;
    }

    /**
     * Extra contract clause when static code analysis is ENABLED for the exercise: the reference solution must be clean of static-analysis findings in the GRADED categories,
     * because
     * production folds an SCA penalty into the score and the out-of-band verifier REJECTS a solution whose build trips a graded SCA category (it would otherwise grade below 100%
     * for
     * a student). Empty when SCA is disabled, so a non-SCA prompt is unchanged.
     */
    private static String staticCodeAnalysisGuidance(ProgrammingExercise exercise) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled())) {
            return "";
        }
        return "\n\nSTATIC CODE ANALYSIS IS ENABLED (and graded): the build runs static-analysis tools (e.g. SpotBugs/Checkstyle, ruff, clippy, ESLint) on the assignment, and a "
                + "graded finding DOCKS the score. Write the REFERENCE SOLUTION so it is CLEAN of static-analysis findings — idiomatic, well-formed code with no lint warnings in the "
                + "graded categories (no missing braces/Javadoc where required, no bad-practice/security/style violations) — otherwise the verifier REJECTS the exercise because the "
                + "solution would not grade 100%. The template need not be lint-clean (it is the student's unfinished starting point); only the solution must be clean.";
    }

    /**
     * Minimum stripped length for a problem statement to count as a real instructor spec (vs. an empty field or a short placeholder) that the agent must build the exercise to
     * match.
     */
    private static final int NON_TRIVIAL_PROBLEM_STATEMENT_MIN_CHARS = 40;

    /**
     * Whether the exercise already carries a real, instructor-provided problem statement that the agent must treat as the authoritative spec, rather than authoring one from
     * scratch. Used by both the system prompt (spec vs from-scratch framing) and the resource (mode-aware default instruction), so the two always agree.
     *
     * @param problemStatement the exercise's current problem statement (may be {@code null})
     * @return {@code true} if it is non-trivial enough to be treated as the spec
     */
    public boolean isNonTrivialProblemStatement(@Nullable String problemStatement) {
        return problemStatement != null && problemStatement.strip().length() >= NON_TRIVIAL_PROBLEM_STATEMENT_MIN_CHARS;
    }

    /**
     * Resolves the instruction for a generation run. An explicit prompt is always honoured (as instructions or feedback). When none is given, the default is mode-aware: if the
     * exercise already carries a real problem statement, the agent is told to build the exercise to match it (spec mode); otherwise it authors a fresh exercise from scratch.
     * <p>
     * This lives next to {@link #isNonTrivialProblemStatement} so the resource's mode-aware default and the system prompt's spec/from-scratch framing always agree on the same
     * threshold.
     *
     * @param request  the generation request holding the optional prompt
     * @param exercise the exercise being generated or adapted
     * @return the resolved instruction for the agent
     */
    public String resolvePrompt(ExerciseGenerationRequestDTO request, ProgrammingExercise exercise) {
        if (request.prompt() != null && !request.prompt().isBlank()) {
            return request.prompt();
        }
        if (isNonTrivialProblemStatement(exercise.getProblemStatement())) {
            return "An initial problem statement is already in problem-statement.md. Treat it as the authoritative specification and build the solution, template, and tests to match "
                    + "it, keeping its intent and every stated requirement; refine its wording and add the [task] bindings for the tests you write.";
        }
        return "Generate a complete, correct programming exercise: a reference solution that passes all tests, a template that compiles but fails the tests, and meaningful tests.";
    }

    /**
     * The single source of truth (defined on {@link LanguageGenerationProfile#supportedLanguages()}) for the languages Hyperion offers for one-click whole-exercise generation —
     * the oracle-verifiable ones. Exposed so the resource can both guard a run and serve the set to the client, which must never mirror it by hand.
     *
     * @return the immutable set of generation-supported languages
     */
    public Set<ProgrammingLanguage> supportedGenerationLanguages() {
        return LanguageGenerationProfile.supportedLanguages();
    }

    /**
     * @param language the exercise's programming language (may be {@code null})
     * @return whether Hyperion whole-exercise generation supports it (i.e. the differential oracle can verify the result)
     */
    public boolean isGenerationSupported(@Nullable ProgrammingLanguage language) {
        return LanguageGenerationProfile.isSupported(language);
    }
}
