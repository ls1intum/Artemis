package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Per-language (and, where it matters, per-project-type) generation guidance injected into the agent system prompt. This is the single place that encodes the conventions an LLM
 * cannot infer from a cleared scaffold for each Artemis-supported language: the source/test layout, the test framework, <em>how the test runner names a test in its result
 * report</em>
 * (which is exactly what an Artemis {@code [task]} binding must reference — and it differs sharply between frameworks), how the template is made to compile-but-fail, how the tests
 * reference the code under test, and the handful of gotchas that otherwise make a generated exercise fail to build.
 * <p>
 * Each profile is grounded in the language's shipped sample exercise under {@code src/main/resources/templates/<language>/}. Only the profile for the exercise's own language is
 * ever
 * added to a prompt, so coverage of all languages does not bloat any single run.
 */
final class LanguageGenerationProfile {

    private LanguageGenerationProfile() {
    }

    /**
     * @param exercise the exercise being generated
     * @return the language-specific guidance block for the system prompt (may be empty for languages with no special conventions)
     */
    static String guidanceFor(ProgrammingExercise exercise) {
        ProgrammingLanguage language = exercise.getProgrammingLanguage();
        if (language == null) {
            return "";
        }
        return switch (language) {
            // KOTLIN never uses MAVEN_BLACKBOX; only Java has the DejaGnu black-box project type.
            case JAVA -> exercise.getProjectType() == ProjectType.MAVEN_BLACKBOX ? JAVA_BLACKBOX : jvm(language);
            case KOTLIN -> jvm(language);
            case PYTHON -> PYTHON;
            case JAVASCRIPT -> JAVASCRIPT;
            case TYPESCRIPT -> TYPESCRIPT;
            case RUBY -> RUBY;
            case R -> R;
            case GO -> GO;
            case RUST -> RUST;
            case C_PLUS_PLUS -> C_PLUS_PLUS;
            case C -> exercise.getProjectType() == ProjectType.FACT ? C_FACT : C_GCC;
            case C_SHARP -> C_SHARP;
            case DART -> DART;
            case SWIFT -> SWIFT;
            case HASKELL -> HASKELL;
            case OCAML -> OCAML;
            case BASH -> BASH;
            case ASSEMBLER -> ASSEMBLER;
            case MATLAB -> MATLAB;
            case VHDL -> VHDL;
            // Languages with no Artemis exercise templates / not registered as creatable in this deployment get no profile.
            case EMPTY, SQL, POWERSHELL, ADA, PHP -> "";
        };
    }

    /** The universal rule, stated once, that every profile relies on: a {@code [task]} binds to the test's NAME AS THE RUNNER REPORTS IT — which varies by framework. */
    private static final String TASK_NAME_RULE = "Remember: a [task]'s parenthesised names must be the test identifiers EXACTLY as this framework's runner writes them into its result "
            + "report (see below) — never a @DisplayName or a prose title.";

    private static String jvm(ProgrammingLanguage language) {
        String base = """


                For this Maven/Gradle JVM exercise the conventional Artemis layout is:
                - solution/src/<package path>/*   (e.g. package `sorting` -> solution/src/sorting/Foo)
                - template/src/<package path>/*   (identical signatures, placeholder bodies)
                - tests/test/<package path>/*     (the test sources directory is `test`, NOT `src/test/java`)
                The test project depends on Ares (de.tum.in.ase:artemis-java-test-sandbox). Ares REFUSES to run any test class that is not annotated, so every test class MUST be \
                annotated @Public and, for parity with Artemis's security conventions, @WhitelistPath("target") and @BlacklistPath("target/test-classes"); and every @Test MUST carry \
                @StrictTimeout(seconds) so a student's infinite loop cannot hang grading. The [task] binding uses the test METHOD name, so give each @Test a clear, descriptive method \
                name and do NOT add a @DisplayName to a graded test: Artemis reports and matches the method name, so a @DisplayName would make that [task] bind to nothing. For example:
                    package sorting;
                    import org.junit.jupiter.api.*;
                    import static org.junit.jupiter.api.Assertions.*;
                    import de.tum.in.test.api.jupiter.Public;
                    import de.tum.in.test.api.StrictTimeout;
                    import de.tum.in.test.api.WhitelistPath;
                    import de.tum.in.test.api.BlacklistPath;
                    @Public
                    @WhitelistPath("target")
                    @BlacklistPath("target/test-classes")
                    class SortTest {
                        @Test
                        @StrictTimeout(5)
                        void sortsAscendingArray() { assertArrayEquals(new int[]{1,2,3}, new Sorter().sort(new int[]{3,1,2})); }
                    }
                The matching task line would be: [task][Sort an unsorted array](sortsAscendingArray). Use plain JUnit 5 assertions inside @Public test classes. Do not modify \
                tests/pom.xml or the build configuration.

                Optionally, you can make the exercise richer: instead of only stubbing method bodies, have the template OMIT a whole class, method, or field that the student must add. \
                If you do this, the platform AUTOMATICALLY generates structural tests (class/method/attribute existence) from the difference between your solution and template — do NOT \
                write a structure oracle (test.json) or structural test classes yourself. But then your behaviour tests must still COMPILE against the incomplete template, so call the \
                omitted members through Ares reflection (de.tum.in.test.api.util.ReflectionTestUtils: newInstance, getMethod, invokeMethod) rather than referencing them directly. If \
                you are unsure, keep identical class/method signatures in solution and template with stubbed bodies and direct calls — that is fully acceptable and produces a valid \
                exercise.""";
        if (language == ProgrammingLanguage.KOTLIN) {
            return base
                    + """


                            This is a KOTLIN exercise: write the reference solution, the template, AND the tests ALL as Kotlin (.kt) files using Kotlin syntax — do NOT write any .java file. The \
                            layout is identical (solution/src, template/src, tests/test) and the Ares rules are identical (the tests are JUnit 5 and still carry @Public, @StrictTimeout, \
                            @WhitelistPath, and @BlacklistPath; Kotlin interoperates with them) — only the language differs: a test method is `@Test fun sortsAscendingArray() { … }` and its \
                            [task] binding uses that function name. CRITICAL: each class must have EXACTLY ONE source file — the Kotlin .kt file. A package must never contain both a .java and a \
                            .kt for the same class, or compilation fails with a conflicting/duplicate class and NO tests run. If a .java file ever exists in solution/ or template/, delete it \
                            immediately with bash (`rm …`). Run `ls -R solution template tests` to confirm no stray .java remain before you submit.""";
        }
        return base;
    }

    private static final String JAVA_BLACKBOX = """


            For this Java MAVEN_BLACKBOX exercise grading is BLACK-BOX via DejaGnu (expect) scripts, NOT JUnit — there are no JUnit tests at all, so do NOT use Ares, @Public, \
            @StrictTimeout, @WhitelistPath/@BlacklistPath, or any test annotation. The student writes a normal console PROGRAM with a `public static void main` that reads commands \
            on STDIN and prints results to STDOUT.
            - Source layout: the student's .java files go under solution/src/<package path>/ and template/src/<package path>/ (e.g. package `sorting` -> solution/src/sorting/Client.java) \
            — note this is `src/<package>/`, NOT `src/main/java`. Keep identical class/method signatures across solution and template; the template's main is a stub (e.g. a TODO body \
            that prints nothing or wrong output) so it still COMPILES but produces output the .exp scripts reject.
            - How the program is driven (from testsuite/config/default.exp): the harness spawns `java <MainClass>`, waits for a fixed PROMPT (the sample uses `set prompt "sort> "`), \
            then for each step sends a command line and matches the program's echo + STDOUT against an expected string. The program must print the prompt before every command and \
            exit on the configured exit command (the sample uses `quit`). A test STEP is a `PROGRAM_test {<input line>} {<expected output>}` (or `PROGRAM_enter {<input>}` for input \
            with no expected output) in an .exp file.
            - The instructor authors three DejaGnu test files — testsuite/<package>.tests/public.exp, advanced.exp, and secret.exp — plus any input fixtures under \
            testsuite/testfiles/{public,secret}/, and must list the black-box test names in BOTH solution/Tests.txt and template/Tests.txt (a `file-exists` checker requires \
            assignment/Tests.txt to be present and non-empty). Do NOT edit testsuite/config/default.exp or testsuite/lib/<package>.exp or pom.xml.
            - CRUCIAL [task] NAMING: the grader runs each .exp file as one DejaGnu step and aggregates all of that file's PROGRAM_test PASS/FAIL lines into a SINGLE feedback named \
            `dejagnu[<step>]`, where <step> is the .exp filename without extension. So there are exactly three bindable test names — `dejagnu[public]`, `dejagnu[advanced]`, \
            `dejagnu[secret]` — and the [task] lines are e.g. [task][Public Tests](dejagnu[public]). The individual PROGRAM_test lines are NOT separately addressable. (The platform \
            also emits non-DejaGnu checkers like MainMethodChecker/LineLengthChecker/FileExistsChecker; you may bind tasks to those names too, but you do not author them.) %s
            - Differential: the solution's main emits exactly the expected prompt/output so every .exp step PASSes (dejagnu[*] green); the template's stub main compiles but emits \
            wrong/missing output, so the SAME .exp steps FAIL (dejagnu[*] red).
            - CAVEAT: the sandbox differential oracle (`verify.sh`) grades via JUnit-XML reports (mvn test / surefire) and does NOT run the DejaGnu pipeline (runtest/expect/\
            pipeline-helper, which exists only in the Jenkins build), so it cannot self-verify a black-box exercise the way it does a JUnit one. Author the structure faithfully to \
            the shipped sample (solution + template + the three .exp files + Tests.txt), but treat black-box generation as best-effort: only attempt it when the instructions clearly \
            describe a deterministic stdin/stdout console dialogue you can encode as expected-output lines."""
            .formatted(TASK_NAME_RULE);

    private static final String PYTHON = """


            For this Python exercise:
            - The reference solution and template are flat .py modules at the solution/ and template/ roots (e.g. solution/sorting.py and template/sorting.py with identical function/\
            class signatures but stub bodies). Put the tests under tests/ (e.g. tests/test_sorting.py); if you split tests into sub-packages, each test directory needs an __init__.py.
            - Tests are unittest classes run by pytest. The [task] binding uses the bare test METHOD name, e.g. a method `def test_sorts_ascending(self):` binds as \
            [task][Sorts ascending](test_sorts_ascending). %s
            - The verifier mounts your solution/template under a package named `assignment`, so import the code under test FROM THERE: `from assignment.sorting import bubble_sort` \
            (use the literal `assignment` package — do NOT import it as a top-level module).
            - The template must still IMPORT-COMPILE (pytest only collects modules that import), so use `pass`/empty bodies or `raise NotImplementedError()` for stubs — never syntax \
            errors. There is no Ares here; do not add Java-style annotations."""
            .formatted(TASK_NAME_RULE);

    private static final String JAVASCRIPT = """


            For this JavaScript exercise:
            - ES-module sources go in solution/src/*.js and template/src/*.js (e.g. `export default class BubbleSort { … }`). The student package.json must have "name": "artemis-exercise", \
            "type": "module", and an "exports" map exposing the source files; the tests reference the code as `import BubbleSort from 'artemis-exercise/bubblesort.js';` (the test project \
            lists the student dir as an npm workspace named `assignment`, which resolves the `artemis-exercise` alias).
            - Tests are Jest under tests/src/*.test.js using describe()/it(). CRUCIAL [task] NAMING: jest-junit reports each test as `<describe>_…_<it>` joined by underscores, so a test \
            `describe('behavior', () => describe('BubbleSort', () => it('sorts_correctly', …)))` is reported as `behavior_BubbleSort_sorts_correctly` — that exact string is the [task] \
            binding name. %s
            - Stub bodies are empty classes whose `// TODO` reads as an instruction to the STUDENT ("// TODO: return the top element without removing it"), not a note about the \
            placeholder. JS has no auto-generated structural tests, so to match the shipped sample's depth write BOTH a behaviour suite AND a structural suite: a \
            `describe('structural', …)` that asserts each method exists (`expect(Stack.prototype).toHaveProperty('pop', expect.any(Function))`) — for that to fail on the template, leave \
            the method OUT of the template class — plus at least one multi-element ordering test (push three, pop three, assert full LIFO order), not just depth-two. Do NOT modify \
            tests/package.json (its `workspaces` field, the `test:ci` script, or the jest-junit reporter) — leave the test harness exactly as seeded; import the code under test by the \
            package name `artemis-exercise/<module>.js`, NEVER by a relative path into ../assignment. Count your describe nesting depth when writing [task] names \
            (one `describe('Stack')` -> `Stack_<it>`). If the seeded solution/template package.json keeps a `start` script pointing at a file the exercise does not contain \
            (e.g. `node ./src/client.js`), delete that script so the shipped exercise has no dead entry point."""
            .formatted(TASK_NAME_RULE);

    private static final String TYPESCRIPT = """


            For this TypeScript exercise. CRITICAL: the seeded build harness is a two-stage npm-workspace + `tsc -b` project-reference build that wires the tests to your COMPILED \
            solution. Do NOT re-author it — leave solution/tsconfig.json, template/tsconfig.json, tests/tsconfig.json, every package.json, and tests/jest.config.js EXACTLY as seeded; \
            change ONLY the src/*.ts files and the test bodies in tests/src/*.test.ts. Rewriting any of these breaks the build and the exercise is rejected with testCount=0.
            - Sources go in solution/src/*.ts and template/src/*.ts and are compiled by `tsc -b` to dist/; the solution/template tsconfig.json keeps `"composite": true` and \
            `extends @tsconfig/node20` (do not change them). The template must still type-check (empty method bodies / `return undefined as any`, NOT type errors), or the whole build fails. \
            Import the code under test ONLY by the package name, e.g. `import Stack from 'artemis-exercise/Stack';` — NEVER by a relative path like `../assignment/src/Stack`. Keep the \
            solution package.json `exports` map exactly `"./*": { "types": "./dist/*.d.ts", "default": "./dist/*.js" }` (NEVER write two `"./*"` keys — JSON keeps only the last).
            - Tests are Jest + ts-jest under tests/src/*.test.ts using describe()/it(). [task] NAMING is identical to JavaScript: the reported name is `<describe>_…_<it>` joined by \
            underscores (e.g. `behavior_BubbleSort_sorts_correctly`). %s
            - To let a structural-existence test FAIL on the template (instead of breaking the compile, and so the bare template scores a clean 0): leave the missing member OUT of the \
            template class (an empty `// TODO` body), and in the test cast the import to `any` before use (`const _Stack = Stack as any;`) so the behaviour tests still type-check \
            against the incomplete template while an existence check like `expect(_Stack.prototype).toHaveProperty('pop', expect.any(Function))` fails on the template and passes on the \
            solution. This is the sample's idiom; a stub that merely `return undefined as any` can ACCIDENTALLY pass a test asserting `toBeUndefined()`, which the verifier rejects."""
            .formatted(TASK_NAME_RULE);

    private static final String RUBY = """


            For this Ruby exercise:
            - Sources go in solution/src/*.rb and template/src/*.rb with snake_case filenames (e.g. bubble_sort.rb defining `class BubbleSort`). Tests are Minitest under tests/test/\
            test_*.rb, run via a Rakefile that emits report.xml (minitest-junit). A tests/test/test_helper.rb prepends the assignment sources to the load path \
            (`$LOAD_PATH.unshift File.join('../assignment', 'src')`) and each test does `require "bubble_sort"` (bare filename).
            - The [task] binding uses the bare Minitest test METHOD name, e.g. `def test_sorts_ascending` binds as [task][Sorts ascending](test_sorts_ascending). %s
            - Stub bodies `raise NotImplementedError` accompanied by a `# TODO:` line phrased as an instruction to the student. A structural-style task can require a whole class the \
            template omits. In the problem statement give every [task] a one-line description of the expected behaviour, not just a title."""
            .formatted(TASK_NAME_RULE);

    private static final String R = """


            For this R exercise:
            - The solution and template are each an R PACKAGE named `assignment`: a DESCRIPTION file with `Package: assignment`, a NAMESPACE that exports the public functions, and the \
            functions under R/ (e.g. R/convert.R). The tests are a separate package under tests/ whose DESCRIPTION has `Imports: assignment` and `Remotes: local::./assignment`, with \
            testthat tests under tests/tests/testthat/test-*.R that call the code as `assignment::matrix_to_column_list(...)`.
            - Tests use testthat (edition 3). CRUCIAL [task] NAMING: the reported name is the `test_that("…")` DESCRIPTION STRING, so write it as a stable identifier, e.g. \
            `test_that("converts_3x3_matrix", { … })` binds as [task][Converts a 3x3 matrix](converts_3x3_matrix). %s
            - The template's function bodies must `stop("not implemented")` (with a # TODO) — NOT `return NULL`: a NULL return can accidentally satisfy a lenient testthat assertion, but \
            `stop()` raises an error so every test fails on the template, as the differential requires. The package must still install (only the bodies error, at call time)."""
            .formatted(TASK_NAME_RULE);

    private static final String GO = """


            For this Go exercise:
            - Go keeps sources at the REPOSITORY ROOT (NOT under src/): put the solution/template .go files directly in solution/ and template/ (package `${packageName}`), with a go.mod \
            declaring `module artemis/<package>`. The tests live under tests/ as packages (e.g. tests/behavior/behavior_test.go) with their own tests/go.mod that has \
            `replace artemis/<package> => ../assignment` and `require artemis/<package>`; test files `import assignment "artemis/<package>"`.
            - Tests are the standard `testing` package; the JUnit report is produced by go-junit-report. The [task] binding uses the Go `func TestXxx(t *testing.T)` name, e.g. \
            `func TestSortsAscending` binds as [task][Sorts ascending](TestSortsAscending). %s
            - Stub constructors `panic("not implemented")`, and a structural task can leave an interface/struct empty so a runtime type-assertion in the test fails.
            - CRITICAL for the differential: the template must COMPILE against EVERY test package and run the SAME number of tests as the solution. Every function, type, and method your \
            tests reference must ALSO exist in the template (with a wrong stub body, an empty interface{}, or an empty struct{}); if a test package references a symbol the template \
            lacks, that package fails to compile, runs FEWER tests than the solution, and the exercise is rejected for a test-count mismatch. Run `sh verify.sh template` and confirm its \
            collected report runs the same number of tests as the solution's before submitting."""
            .formatted(TASK_NAME_RULE);

    private static final String RUST = """


            For this Rust exercise:
            - The solution and template are each a Cargo crate: Cargo.toml plus src/lib.rs that declares every `pub mod`, with the modules in src/*.rs. Keep the crate name stable (the \
            test crate under tests/ depends on it by `path = "../assignment"`). The tests are a separate crate with #[test] functions under tests/tests/*.rs, run by `cargo nextest` \
            (it writes JUnit to target/nextest/ci/junit.xml).
            - The [task] binding uses the `#[test] fn` name, e.g. `fn test_sorts_ascending()` binds as [task][Sorts ascending](test_sorts_ascending). %s
            - Stub bodies `todo!()`. NOTE: the sample ships an advanced syn/proc-macro structural harness; unless you need it (the common case), keep the solution and template signatures \
            identical with `todo!()` bodies and direct calls — AND delete the now-dead structural machinery so the shipped exercise carries no unused weight: remove `tests/build.rs`, the whole \
            `tests/rust_template_test_macros/` crate, and the `syn`, `chrono`, `rust_template_test_macros` and `[build-dependencies]` entries from `tests/Cargo.toml`; also drop any dependency \
            (e.g. `rand`, `chrono`) the solution/template do not `use` from their Cargo.toml. Before submitting run `grep -rn 'syn\\|chrono\\|rand\\|rust_template_test_macros' solution template tests` \
            — a crate your code never references must not appear in any Cargo.toml."""
            .formatted(TASK_NAME_RULE);

    private static final String C_PLUS_PLUS = """


            For this C++ exercise:
            - Use a src/ + include/ layout: the public header (e.g. include/sort.hpp) is the shared CONTRACT and must be byte-identical across solution/, template/, and what the tests \
            include; the student implements src/sort.cpp. A CMakeLists.txt builds a library target named `assignment`; the test project does `add_subdirectory(assignment)` and \
            `target_link_libraries(... assignment Catch2::Catch2WithMain)`.
            - Tests are Catch2 v3, driven by the seeded Python harness (tests/Tests.py runs TestConfigure, a compile step, then TestCatch2). [task] NAMING: a flat `TEST_CASE("name")` with \
            NO SECTION is reported by that exact name — e.g. `TEST_CASE("sorts_ascending")` binds as [task][Sorts ascending](sorts_ascending) — so PREFER flat TEST_CASEs, one per behaviour, \
            so each binds by its own string. If you nest a `SECTION("s")` inside `TEST_CASE("t")`, the reported name becomes the SLASH-joined path `t/s` (e.g. \
            sorting_algorithms/selection_sort) and the [task] must use that exact path, not the bare section name. %s
            - CRITICAL for the differential: the template's header (include/*.hpp) must declare the COMPLETE interface — EVERY class, method, and signature the tests call — IDENTICAL \
            to the solution's header; only the .cpp method BODIES differ, stubbed with `throw std::logic_error("not implemented");`. If the template header omits a member a test uses, \
            that TEST_CASE fails to COMPILE and the template runs FEWER tests than the solution (rejected). Run `sh verify.sh template` and confirm it runs the same number of tests as the solution. \
            The headers are the shared CONTRACT, so before submitting run `diff -r solution/include template/include` — it MUST print NOTHING: the headers must be byte-identical \
            (comments included), since the problem statement presents them as the same contract. Cover each operation across happy, empty, single-element, and a larger/stress input, \
            and keep flat `TEST_CASE`s (no SECTION) so each binds by its own name."""
            .formatted(TASK_NAME_RULE);

    private static final String C_GCC = """


            For this C exercise (gcc project type):
            - C keeps sources at the REPOSITORY ROOT (e.g. solution/calculator.c, template/calculator.c) — NOT under src/. This project type uses an output/black-box test harness \
            (tests/Tests.py) that compiles the assignment, runs the produced program, and compares its STDOUT, plus AddressSanitizer/UBSan rebuilds.
            - CRUCIAL [task] NAMING: the tests are defined in tests/Tests.py and each is reported under the `name=` you give it (e.g. TestCompile, TestOutput); the [task] binding uses \
            those exact names, NOT the C function names. %s
            - This harness is intricate (a student Makefile with load-bearing INCLUDEDIRS=/SOURCE= variables, stdout fixtures). For a small algorithmic C exercise prefer the `fact` \
            project type instead, which is declarative and far simpler."""
            .formatted(TASK_NAME_RULE);

    private static final String C_FACT = """


            For this C exercise (fact project type):
            - C keeps the source at the REPOSITORY ROOT (e.g. solution/exercise.c, template/exercise.c). Grading is DECLARATIVE via the FACT framework: you write tests/exercise.yml \
            (listing tests by `name:`, e.g. Compile, CodeStructure, InputOutput, each with a type like compile/structural/io) and tests/exercise_io.txt (the input/output session DSL: \
            `s>` sends a line, `o>` expects an output line). `translation_unit:` in the yml must match the root .c filename.
            - The [task] binding uses the `name:` of each entry in exercise.yml, e.g. [task][Correct output](InputOutput). %s
            - The template's exercise.c has a TODO / deliberately wrong body so the InputOutput/structural checks fail while the solution passes."""
            .formatted(TASK_NAME_RULE);

    private static final String C_SHARP = """


            For this C# exercise:
            - The solution and template are flat .cs files directly in solution/ and template/ with `namespace assignment;` (the namespace MUST be `assignment`) and an assignment.csproj \
            named exactly `assignment`. ALL THREE csproj (solution, template, tests) MUST stay on net8.0 — never change the TargetFramework: the CI image ships only the net8.0 SDK, so a \
            net6.0/net7.0 target fails to restore in production even if it builds in the sandbox. The tests live flat in tests/ — keep the seeded test.csproj EXACTLY as is (its ProjectReference \
            already points at the assignment; do not hardcode or rewrite the path).
            - Tests are NUnit 3; the JUnit report comes from JunitXml.TestLogger (`dotnet test --logger=junit`). The [task] binding uses the `[Test]` METHOD name (PascalCase), e.g. \
            `[Test] public void TestSortsAscending()` binds as [task][Sorts ascending](TestSortsAscending); give each test `[Timeout(1000)]`. %s
            - Stub method bodies must `throw new NotImplementedException();` with a `// TODO:` telling the STUDENT what to implement — an EMPTY body does NOT compile for a value-returning \
            method (CS0161). Reflection-based tests resolve types via `Assembly.Load("assignment")`, so keep the assembly/namespace name `assignment`."""
            .formatted(TASK_NAME_RULE);

    private static final String DART = """


            For this Dart exercise:
            - Library sources go under solution/lib/*.dart and template/lib/*.dart with snake_case filenames (e.g. lib/bubble_sort.dart); the pubspec.yaml `name:` is the package name. \
            The tests are under tests/test/*_test.dart with a test pubspec that has a path dependency on `../assignment`; tests import as `import 'package:<package>/bubble_sort.dart';`.
            - Tests use package:test under tests/test/*_test.dart. CRUCIAL [task] NAMING — do NOT hand-derive it, COPY the exact `<testcase name="...">` strings from the collected report (grep /opt/hyperion/reports/solution/* after running verify.sh). \
            The rule: the package:test full name is every enclosing `group('…')` name then the `test('…')` description joined by SINGLE SPACES (e.g. `group('reverseString'){ test('reverse_non_empty') }` \
            -> `reverseString reverse_non_empty`). Artemis PREPENDS the dot-joined test-FILE suite name (path minus `_test.dart`, separators -> dots, e.g. `test.palindrome`) ONLY when the run has \
            TWO OR MORE test files; with a SINGLE test file (the common case — one top-level suite) that file prefix is DROPPED and the reported name is the BARE `reverseString reverse_non_empty`. The \
            shipped sample has multiple test files, which is why its readme shows `test.behavior.X`/`test.structural.X` — do not copy that prefix into a single-file exercise. %s
            - Stub bodies `throw UnimplementedError();`."""
            .formatted(TASK_NAME_RULE);

    private static final String SWIFT = """


            For this Swift exercise (Swift Package Manager):
            - The algorithm/domain types go in Sources/<Package>Lib/*.swift; the executable's main.swift in Sources/<Package>App/main.swift must stay minimal (anything else there \
            breaks test linking). The tests are under tests/Tests/<Package>Tests/*.swift and `@testable import <Package>Lib`.
            - Tests are XCTest; the report comes from SwiftTestReporter. The [task] binding uses the XCTest METHOD name, e.g. `func testSortsAscending()` binds as \
            [task][Sorts ascending](testSortsAscending). CRUCIAL: every test method must ALSO be registered in the class's `static var allTests = [("testSortsAscending", testSortsAscending)]` \
            and in XCTestManifests.swift, or it silently will not run on Linux. %s
            - Stub bodies return a wrong value (e.g. `return input` unsorted) with a `// TODO:` telling the STUDENT what to implement. COVERAGE: do NOT ship only a couple of tests for a \
            multi-operation type — cover each operation across happy, empty, single-element, and a >=3-element ordering case, and assert every invariant the statement promises (e.g. peek \
            does not mutate the stack). A promise with no test is a hole that lets a broken solution score full marks. Every test method must be registered in `allTests` AND XCTestManifests.swift."""
            .formatted(TASK_NAME_RULE);

    private static final String HASKELL = """


            For this Haskell exercise:
            - The solution and template are each `src/Exercise.hs` exposing `module Exercise (…)` with identical type signatures; the template's function bodies are `= undefined`. The \
            tests (tests/test/Test.hs) use tasty (HUnit/QuickCheck/SmallCheck) and an Interface.hs that re-exports the student's functions — all three must agree on the exact signature.
            - Tests emit JUnit via tasty-ant-xml. CRUCIAL [task] NAMING: the reported name is the DOT-JOINED tasty testGroup path, e.g. a `testCase "sorts ascending"` nested under \
            `testGroup "Unit Tests"` is reported as `Unit Tests.sorts ascending` — that whole string is the [task] binding name. %s
            - Safe-Haskell restrictions apply (no unsafe/CPP). Keep all three cabal files' resolver/deps consistent."""
            .formatted(TASK_NAME_RULE);

    private static final String OCAML = """


            For this OCaml exercise:
            - The solution and template are each src/assignment.ml plus a matching assignment.mli interface and a dune file (library `assignment`); the template bodies are \
            `failwith "TODO"`. The tests (tests/test/test.ml) use OUnit2 + QCheck, and the build mocks the stdlib via an overrides.ml whitelist and an AST checker that forbids \
            imperative constructs.
            - CRUCIAL [task] NAMING: this framework binds tasks by POSITIONAL INDEX, not by name: `<suite-index>:<test-label>:<case-index>`, e.g. [task][add](0:add:0,0:add:1). Mirror \
            the sample's readme exactly and do not reorder the OUnit suite. %s
            - This is an advanced, intricate harness; keep the surrounding files (mli, dune, overrides, checker) internally consistent with the function signatures."""
            .formatted(TASK_NAME_RULE);

    private static final String BASH = """


            For this Bash exercise:
            - The solution and template are a single script (e.g. solution/script.bash, template/script.bash). The tests (tests/test.bats) use bats; the script is put on PATH and run \
            in a temp dir seeded with fixtures you must also ship under tests/test_data/ (inputs and matching *_expected.txt).
            - The [task] binding uses the bats `@test "name"` STRING verbatim, e.g. `@test "lists files"` binds as [task][Lists files](lists files). %s
            - The template is a TODO stub that ends with `exit 1` so even a status-code test fails; the solution must produce exactly the fixture outputs (stdout / files)."""
            .formatted(TASK_NAME_RULE);

    private static final String ASSEMBLER = """


            For this Assembler (x86 NASM) exercise:
            - The shipped sample grades COMPILATION ONLY: a single test named `Compile` checks that `make` (nasm) assembles the student's .asm. There is no behavioural test harness, so a \
            template that already assembles cannot fail the Compile test — which the differential verifier (solution passes, template compiles-but-fails) cannot accept.
            - To make a verifiable exercise you would have to author a runnable test rig (a driver that calls the routine and checks I/O) that the sample does not model. %s
            - This is a niche, ABI-specific target; only attempt it if the instructions clearly describe a runnable, checkable behaviour."""
            .formatted(TASK_NAME_RULE);

    private static final String MATLAB = """


            For this MATLAB exercise:
            - One function per .m file with the function name equal to the filename (e.g. solution/finalGrade.m defining `function g = finalGrade(...)`). The tests \
            (tests/tests/*.m) are a `matlab.unittest.TestCase` class; tests/testRunner.m addpaths the assignment dir and runs them, emitting JUnit.
            - The [task] binding uses the test METHOD name, e.g. `function testFinalGrade(testCase)` binds as [task][Final grade](testFinalGrade). %s
            - The template's function bodies are empty (the output variable is never assigned) so the function errors. You must compute the EXACT expected numeric values (mind vector \
            shape and rounding) for the assertions. Note: this needs a licensed MATLAB runner."""
            .formatted(TASK_NAME_RULE);

    private static final String VHDL = """


            For this VHDL exercise:
            - The shipped sample grades ANALYSIS/COMPILATION ONLY (GHDL `-a`): a single `Compile` test checks the entity plus a provided testbench analyse. There is no simulation, so a \
            template whose entity analyses cannot fail — which the differential verifier (solution passes, template compiles-but-fails) cannot accept as-is.
            - A real exercise needs a self-checking testbench (assert statements + a `ghdl -r` run step) that the sample does not provide. The student entity's port names/types must \
            match the testbench component declaration. %s
            - This is a niche, simulator-bound hardware target; only attempt it if the instructions describe a checkable behaviour and you provide a self-checking testbench."""
            .formatted(TASK_NAME_RULE);
}
