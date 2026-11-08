package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Deterministic unit tests for the approved-ownership gates: student-created, stubbed and given types against the approved Design table and Public API, given-type
 * dependencies on student-created types, the template's TODO anchors, and the diagram requirement.
 */
class ApprovedOwnershipGateTest {

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    // --- Approved specification contract ---

    @Test
    void approvedSpecification_acceptsAContractThatRequiresStudentsToCreateEveryType() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `BaseShape` | abstract base | student-creates |
                | `Rectangle` | concrete shape | student-creates |
                """;
        Map<String, String> solution = map("src/BaseShape.java", "public abstract class BaseShape {}", "src/Rectangle.java", "public class Rectangle {}");

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, Map.of(), solution)).isEmpty();
    }

    /**
     * The template scaffold-presence matrix. A template legitimately ships stubs rather than implementations, so presence cannot be inferred from file counts or from the
     * template merely differing from the solution: the frozen '## Design' table is the authority on which types must be there ({@code given}, {@code stubbed}) and which must not
     * ({@code student-creates}). A rejected candidate is discarded without an instructor ever seeing it, so every doubt-on-read-back row must stay inert.
     */
    private static Stream<Arguments> scaffoldPresenceCases() {
        String stubbedPlayer = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Player` | context students complete | stubbed |
                """;
        String genericStack = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Stack<T>` | generic container students complete | stubbed |
                """;
        String givenAndCreated = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> playerSolution = map("src/Player.java", "public class Player { int score() { return 1; } }");
        return Stream.of(
                Arguments.of("stubbed type present as an unimplemented stub", stubbedPlayer,
                        map("src/Player.java", "public class Player { int score() { throw new UnsupportedOperationException(); } }"), playerSolution, null),
                Arguments.of("stubbed type absent from the template", stubbedPlayer, map("src/Other.java", "class Other {}"), playerSolution, "Player"),
                // A generic cell reaches this gate verbatim: searching for "Stack<T>" in a source declaring "class Stack<T>" finds nothing and rejected a sound exercise.
                Arguments.of("generic stubbed cell whose template declaration is sound", genericStack, map("src/Stack.java", "public class Stack<T> { void push(T item) {} }"),
                        map("src/Stack.java", "public class Stack<T> { void push(T item) { store.add(item); } }"), null),
                // A student-created type is REQUIRED to be absent from the template, so its absence must never be read as a missing scaffold.
                Arguments.of("student-creates type absent from the template", givenAndCreated, map("src/Track.java", track),
                        map("src/Track.java", track, "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}"), null),
                Arguments.of("given type absent from the template", givenAndCreated, map("src/Unrelated.java", "class Unrelated {}"),
                        map("src/Track.java", track, "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}"), "Track"),
                // Fail-open rows: no readable contract means no evidence to reject on.
                Arguments.of("no approved specification", "", map("src/Anything.java", "class Anything {}"), map("src/Anything.java", "class Anything {}"), null),
                Arguments.of("specification without a Design section", "## Rules\n1. Sort ascending.\n", Map.of(), Map.of(), null),
                Arguments.of("Design section whose table has no parseable data rows", "## Design\n\nSee the table above.\n", Map.of(), Map.of(), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scaffoldPresenceCases")
    void approvedSpecification_enforcesTheTemplateScaffoldOnlyWhereTheContractSaysSo(String description, String spec, Map<String, String> template, Map<String, String> solution,
            String expectedRejectedType) {
        List<String> reasons = ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution);

        if (expectedRejectedType == null) {
            assertThat(reasons).isEmpty();
        }
        else {
            assertThat(reasons).singleElement().satisfies(reason -> assertThat(reason).contains(expectedRejectedType).contains("template"));
        }
    }

    @Test
    void approvedSpecification_rejectsStudentCreatedTypesThatLeakIntoTheTemplate() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | data | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                | `Player` | context students wire | student-creates |
                """;
        Map<String, String> solution = map("src/Track.java", "public record Track(String title) {}", "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}",
                "src/Player.java", "public class Player {}");
        Map<String, String> template = map("src/Track.java", "public record Track(String title) {}", "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}",
                "src/Player.java", "public class Player { /* TODO */ }");

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("template already declares them", "PlaybackStrategy", "Player", "changing SPEC.md after approval cannot"));
    }

    @Test
    void approvedSpecification_acceptsStudentCreatedTypesOnlyInTheSolutionIncludingSecondaryDeclarations() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                | `Player` | context students wire | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> solution = map("src/ExerciseTypes.java", "interface PlaybackStrategy {}\nclass Player {}", "src/Track.java", track);

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, map("src/Track.java", track), solution)).isEmpty();
    }

    @Test
    void approvedSpecification_rejectsAGivenTypeWhoseCodeConstructsAStudentCreatedType() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `Demo` | supplied entry point | given |
                | `Player` | context students create | student-creates |
                """;
        String track = "public record Track(String title) {}";
        // The signature names no student-created type, so the specification gate cannot see the dependency; only the body does.
        String demo = "public class Demo { public static void main(String[] args) { new Player().play(new Track(\"a\")); } }";
        Map<String, String> solution = map("src/Track.java", track, "src/Demo.java", demo, "src/Player.java", "public class Player { public void play(Track t) {} }");
        Map<String, String> template = map("src/Track.java", track, "src/Demo.java", demo);

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("[Demo->Player]", "cannot compile", "'stubbed'").doesNotContain("Track->"));
        // The solution copy alone already shows the dependency, before the template copy exists.
        assertThat(ApprovedOwnershipGate.givenTypeStudentCreatedDependencies(StageChecks.designTableRows(spec), List.of("Player"), Map.of(), solution))
                .containsExactly("Demo->Player");
    }

    @Test
    void approvedSpecification_ignoresStudentCreatedTypeNamesInGivenCommentsAndLiterals() {
        String spec = "## Design\n| Type | Role | Template status |\n|---|---|---|\n| Demo | entry point | given |\n| Player | context | student-creates |\n";
        String demo = "/** Students write Player. */ public class Demo { String hint() { return \"implement Player\"; } // Player\n }";
        Map<String, String> solution = map("src/Demo.java", demo, "src/Player.java", "public class Player {}");

        assertThat(ApprovedOwnershipGate.givenTypeStudentCreatedDependencies(StageChecks.designTableRows(spec), List.of("Player"), map("src/Demo.java", demo), solution)).isEmpty();
    }

    @Test
    void givenDependencies_resolveNamesAgainstTheStudentTypesDeclarationAndImports() {
        String spec = "## Design\n| Type | Role | Template status |\n|---|---|---|\n| Ledger | supplied store | given |\n| Entry | record students create | student-creates |\n";
        List<StageChecks.DesignRow> rows = StageChecks.designTableRows(spec);
        String entry = "package de.test;\npublic record Entry(String key) {}";
        String qualified = "package de.test;\nimport java.util.Map;\npublic class Ledger { int size(Map<String, Integer> m) { for (Map.Entry<String, Integer> e : m.entrySet()) {} return 0; } }";
        String importedElsewhere = "package de.test;\nimport java.util.Map.Entry;\npublic class Ledger { Entry<String, Integer> first; }";
        String otherPackage = "package de.other;\npublic class Ledger { Entry first; }";
        String importedStudentType = "package de.other;\nimport de.test.Entry;\npublic class Ledger { Entry first; }";
        String samePackage = "package de.test;\npublic class Ledger { Entry first; }";

        for (String ledger : List.of(qualified, importedElsewhere, otherPackage)) {
            assertThat(ApprovedOwnershipGate.givenTypeStudentCreatedDependencies(rows, List.of("Entry"), Map.of(),
                    map("src/de/test/Entry.java", entry, "src/de/test/Ledger.java", ledger))).as(ledger).isEmpty();
        }
        for (String ledger : List.of(importedStudentType, samePackage)) {
            assertThat(ApprovedOwnershipGate.givenTypeStudentCreatedDependencies(rows, List.of("Entry"), Map.of(),
                    map("src/de/test/Entry.java", entry, "src/de/test/Ledger.java", ledger))).as(ledger).containsExactly("Ledger->Entry");
        }
        // A fully qualified name needs no import and names the student type wherever the given type lives.
        String fullyQualified = "package de.other;\npublic class Ledger { Object first() { return new de.test.Entry(\"k\"); } }";
        assertThat(ApprovedOwnershipGate.givenTypeStudentCreatedDependencies(rows, List.of("Entry"), Map.of(),
                map("src/de/test/Entry.java", entry, "src/de/other/Ledger.java", fullyQualified))).containsExactly("Ledger->Entry");
        // Without the student type's declaration nothing can be resolved, so nothing is reported.
        assertThat(ApprovedOwnershipGate.givenTypeStudentCreatedDependencies(rows, List.of("Entry"), Map.of(), map("src/de/test/Ledger.java", samePackage))).isEmpty();
    }

    private static Stream<Arguments> annotatedTypeOwnershipCases() {
        return Stream
                .of("@FunctionalInterface public interface Strategy { int apply(int x); }", "@java.lang.FunctionalInterface public interface Strategy { int apply(int x); }",
                        "@Deprecated @FunctionalInterface public interface Strategy { int apply(int x); }",
                        "@Deprecated(since = \"1.0\", forRemoval = false) public interface Strategy { int apply(int x); }",
                        "@Metadata(values = {@Nested(value = \"}) class Phantom {}\")}) public interface Strategy { int apply(int x); }",
                        "class Host { @FunctionalInterface public interface Strategy { int apply(int x); } }",
                        "@Deprecated public @FunctionalInterface interface Strategy { int apply(int x); }")
                .flatMap(source -> Stream.of(Arguments.of("student-creates", source), Arguments.of("given", source)));
    }

    @ParameterizedTest
    @MethodSource("annotatedTypeOwnershipCases")
    void approvedSpecification_acceptsAnnotatedStudentCreatedAndIdenticalGivenTypes(String status, String source) {
        String spec = "## Design\n| Type | Role | Template status |\n|---|---|---|\n| Strategy | abstraction | " + status + " |\n";
        Map<String, String> solution = map("src/Strategy.java", source);
        Map<String, String> template = status.equals("given") ? solution : Map.of();

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).isEmpty();
    }

    @Test
    void approvedSpecification_preservesAnnotatedOwnershipAndGivenByteEquality() {
        String given = "## Design\n| Type | Role | Template status |\n|---|---|---|\n| Strategy | abstraction | given |\n";
        String source = "@FunctionalInterface public interface Strategy { int apply(int x); }";
        Map<String, String> solution = map("src/Strategy.java", source);

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(given, map("src/Strategy.java", source + "\n"), solution)).singleElement().asString()
                .contains("byte-for-byte identical");
        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(given.replace("given", "student-creates"), solution, solution)).singleElement().asString()
                .contains("template already declares them");
    }

    @ParameterizedTest
    @ValueSource(strings = { "// @FunctionalInterface public interface Strategy { int apply(int x); }",
            "class Host { String text = \"@FunctionalInterface public interface Strategy {}\"; }",
            "class Host { String text = \"\"\"\n@FunctionalInterface public interface Strategy {}\n\"\"\"; }",
            "@Metadata(values = {@Nested(value = \"public interface Strategy {}\")}) public class Other {}", "@Broken( public interface Strategy { int apply(int x); }" })
    void approvedSpecification_doesNotAcceptAnnotationTriviaOrMalformedArgumentsAsTypes(String source) {
        String spec = "## Design\n| Type | Role | Template status |\n|---|---|---|\n| Strategy | abstraction | student-creates |\n";

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, Map.of(), map("src/Example.java", source))).singleElement().asString()
                .contains("reference solution does not declare them");
    }

    @Test
    void approvedSpecification_doesNotMistakeATodoBreadcrumbForATypeDeclaration() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> solution = map("src/PlaybackStrategy.java", "public interface PlaybackStrategy {}", "src/Track.java", track);
        Map<String, String> template = map("src/Player.java", "public class Player { // TODO: create interface PlaybackStrategy\n}", "src/Track.java", track);

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).isEmpty();
    }

    @Test
    void approvedSpecification_doesNotMistakeATextBlockExampleForAStudentCreatedType() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """;
        String help = String.join("\n", "class Help {", "    String example = \"\"\"", "        interface PlaybackStrategy {}", "        \"\"\";", "}");
        Map<String, String> solution = map("src/PlaybackStrategy.java", "public interface PlaybackStrategy {}", "src/Help.java", help);
        Map<String, String> template = map("src/Help.java", help);

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).isEmpty();
    }

    @Test
    void approvedSpecification_rejectsANamedTypeArtifactAndASameLineNestedDeclaration() {
        String spec = "## Design\n| Type | Role | Template status |\n|---|---|---|\n| `Track` | supplied data type | given |\n"
                + "| `PlaybackStrategy` | students create it | student-creates |\n";
        String track = "public record Track(String title) {}";
        Map<String, String> solution = map("src/PlaybackStrategy.java", "public interface PlaybackStrategy {}", "src/Track.java", track);

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, map("src/PlaybackStrategy.java", "", "src/Track.java", track), solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("template already declares", "PlaybackStrategy"));
        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, map("src/Player.java", "class Player { interface PlaybackStrategy {} }", "src/Track.java", track),
                solution)).singleElement().satisfies(reason -> assertThat(reason).contains("template already declares", "PlaybackStrategy"));
    }

    @Test
    void approvedSpecification_rejectsAStubbedTypeMissingOnlyFromTheReferenceSolution() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Input` | supplied value | given |
                | `Processor` | behavior students complete | stubbed |
                ## Public API
                ```java
                public record Input(int value) {}
                ```
                ```java
                public class Processor {
                    public int process();
                }
                ```
                """;
        String input = "public record Input(int value) {}";
        Map<String, String> template = map("src/Input.java", input, "src/Processor.java",
                "public class Processor { public int process() { throw new UnsupportedOperationException(); } }");
        Map<String, String> solution = map("src/Input.java", input, "src/Processor.java", "public class Processor { public int process() { return 1; } }");

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).isEmpty();

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, map("src/Input.java", input))).singleElement()
                .satisfies(reason -> assertThat(reason).contains("solution", "Processor", "does not declare"));
    }

    @Test
    void approvedSpecification_rejectsAnIncompleteReferenceSolution() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> supplied = map("src/Track.java", track);

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, supplied, supplied)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("reference solution does not declare", "PlaybackStrategy"));
    }

    @Test
    void approvedSpecification_acceptsIdenticalGivenTypesInSolutionAndTemplate() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Ingredient` | supplied immutable value | given |
                """;
        String ingredient = "public record Ingredient(String name, int potency) {}\n";

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, map("src/Ingredient.java", ingredient), map("src/Ingredient.java", ingredient))).isEmpty();
    }

    @Test
    void approvedSpecification_rejectsDivergentGivenTypes() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Ingredient` | supplied immutable value | given |
                """;
        Map<String, String> solution = map("src/Ingredient.java", "public record Ingredient(String name, int potency) {}\n");
        Map<String, String> template = map("src/Ingredient.java", "public class Ingredient { public void setPotency(int potency) {} }\n");

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("given type", "Ingredient", "byte-for-byte identical", "solution and template", "First differences", "line 1",
                        "solution is the canonical complete implementation", "Never delete solution documentation"));
    }

    @Test
    void approvedSpecification_enforcesExactApisForGivenStubbedAndStudentCreatedTypes() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Input | value | given |
                | Processor | student implementation | stubbed |
                | Policy | student-created policy | student-creates |

                ## Public API
                ```java
                public record Input(int value) {}
                ```
                ```java
                public class Processor {
                    public Processor(Input input);
                    public int process();
                }
                ```
                ```java
                public interface Policy {
                    int apply(int input);
                }
                ```
                """;
        String input = "public record Input(int value) {}";
        Map<String, String> solution = map("src/Input.java", input, "src/Processor.java",
                "public class Processor { public Processor(Input input) {} public int process() { return 1; } }", "src/Policy.java",
                "public interface Policy { int apply(int input); }");
        Map<String, String> template = map("src/Input.java", input, "src/Processor.java",
                "public class Processor { public Processor(Input input) {} public int process() { throw new UnsupportedOperationException(); } }");

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, template, solution)).isEmpty();

        Map<String, String> driftedTemplate = map("src/Input.java", input, "src/Processor.java",
                "public class Processor { public Processor(Input input) {} public int process() { return 0; } public int process(int mode) { return 0; } }");
        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, driftedTemplate, solution))
                .anySatisfy(reason -> assertThat(reason).contains("template public API for Processor", "extra", "process"));
    }

    @Test
    void approvedSpecification_failsClosedWhenTheFrozenPublicApiIsInvalid() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Policy | student-created policy | student-creates |

                ## Public API
                Policy has some method.
                """;

        assertThat(ApprovedOwnershipGate.approvedSpecificationReasons(spec, Map.of(), map("src/Policy.java", "public interface Policy { int apply(int input); }")))
                .anySatisfy(reason -> assertThat(reason).contains("structural contract is invalid", "cannot be replaced by candidate code"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"TODO S1\"", "\"\"\"\nTODO S1\n\"\"\"" })
    void seamLabelsInsideLiteralsDoNotInvalidateTaskComments(String literal) {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | calculates a value | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | calculate a value | 3 | no |
                """;
        String source = "class Player { String label = " + literal + ";\n // TODO: calculate a value\n int calculate() { return 0; }\n}";

        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, Map.of("src/Player.java", source), Map.of())).isEmpty();
    }

    @Test
    void templateTodoSeams_rejectSeamIdLabelsAndStubbedOwnersWithoutTodo() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | player | stubbed |
                | Context | context | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | create strategy | 3 | no |
                | S2 | Context | wire strategy | 2 | no |
                """;
        Map<String, String> template = Map.of("src/Player.java", "class Player { // TODO S1: create strategy\n}", "src/Context.java",
                "class Context {\n  /** Wires the strategy. */\n  void wire() {}\n}");

        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, template, Map.of())).hasSize(2)
                .anySatisfy(reason -> assertThat(reason).contains("internal seam IDs", "src/Player.java", "// TODO: <task wording>"))
                .anySatisfy(reason -> assertThat(reason).contains("Context", "S2", "declaring source"));
        assertThat(
                ApprovedOwnershipGate.templateTodoSeamReasons(spec,
                        Map.of("src/Player.java", "class Player { /* TODO (S1) create strategy */ }", "src/Context.java",
                                "class Context {\n  /**\n   * TODO: wire strategy\n   */\n  void wire() {}\n}"),
                        Map.of()))
                .singleElement().asString().contains("internal seam IDs");
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void templateTodoSeams_scanALongBlankRunAfterTheMarkerInLinearTime() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | player | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | create strategy | 3 | no |
                """;
        String blanks = " ".repeat(100_000);
        Map<String, String> unlabelled = Map.of("src/Player.java", "class Player { // TODO" + blanks + "create strategy\n}");
        Map<String, String> labelled = Map.of("src/Player.java", "class Player { // TODO -" + blanks + "S1 create strategy\n}");

        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, unlabelled, Map.of())).isEmpty();
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, labelled, Map.of())).singleElement().asString().contains("internal seam IDs");
    }

    @Test
    void templateTodoSeams_acceptExactCoverageAcrossFiles() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | player | stubbed |
                | Context | context | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | create strategy | 3 | no |
                | S2 | Context | wire strategy | 2 | no |
                """;
        Map<String, String> template = Map.of("src/Player.java", "class Player { // TODO: create strategy\n}", "src/Context.java", "class Context { // TODO: wire strategy\n}");

        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, template, Map.of())).isEmpty();
    }

    @Test
    void templateTodoSeams_ignoreMarkersOutsideJavaSources() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | player | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | create strategy | 3 | no |
                """;
        Map<String, String> template = Map.of("src/Player.java", "class Player { // TODO: create strategy\n}", "README.md", "TODO S9: author note", "target/Stale.java",
                "// TODO S8: generated output");

        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, template, Map.of())).isEmpty();
    }

    @Test
    void templateTodoSeams_rejectTodosInGivenTypesAndGradedTestsButNotInOtherStudentFiles() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | FireSpell | strategy | student-creates |
                | IceSpell | strategy | student-creates |
                | Spell | strategy interface | given |
                | Mage | context | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | FireSpell | formula | 2 | yes |
                | S2 | IceSpell | formula | 2 | yes |
                | S3 | Mage | delegation and switching | 3 | yes |
                """;
        Map<String, String> honest = Map.of("src/Mage.java", "class Mage { // TODO: delegate and switch\n}", "src/Spell.java", "interface Spell { int cast(); }");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, honest, Map.of())).isEmpty();

        // Student-facing files outside the Design table, such as a student test skeleton or a helper, may anchor their own work.
        Map<String, String> studentSkeletons = Map.of("src/Mage.java", "class Mage { // TODO: delegate\n}", "src/Helper.java", "class Helper { // TODO: format the log\n}",
                "test/MageTest.java", "class MageTest { // TODO: test switching\n}");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, studentSkeletons, Map.of())).isEmpty();

        Map<String, String> givenTodo = Map.of("src/Mage.java", "class Mage { // TODO: delegate\n}", "src/Spell.java",
                "interface Spell { // TODO: create FireSpell\n int cast(); }");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, givenTodo, Map.of())).singleElement().asString().contains("TODOs in given types", "src/Spell.java");

        Map<String, String> unfinishedTests = Map.of("test/MageTest.java", "class MageTest { @Test void casts() { /* TODO assert the damage */ } }", "SecurityPolicy.yaml",
                "# TODO is not Java");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, honest, unfinishedTests)).singleElement().asString().contains("graded tests contain TODOs",
                "test/MageTest.java");

        // The TODO's enclosing declaration decides, not the file: a given type beside a stubbed one in the same file still ships complete.
        Map<String, String> givenTodoBesideStubbedOwner = Map.of("src/Mage.java",
                "class Mage {\n  int level;\n}\ninterface Spell {\n  // TODO: create FireSpell\n  int cast();\n}");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, givenTodoBesideStubbedOwner, Map.of())).hasSize(2)
                .anySatisfy(reason -> assertThat(reason).contains("TODOs in given types", "src/Mage.java"))
                .anySatisfy(reason -> assertThat(reason).contains("stubbed owner Mage", "declaring source"));

        // A TODO outside the owner's body anchors nothing: above the class it could stand for any type of the file.
        Map<String, String> todoAboveTheOwner = Map.of("src/Mage.java", "// TODO: delegate and switch\nclass Mage {\n}", "src/Spell.java", "interface Spell { int cast(); }");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, todoAboveTheOwner, Map.of())).singleElement().asString().contains("stubbed owner Mage",
                "inside its declaration");

        // Text in a string literal is not a TODO comment.
        Map<String, String> todoOnlyInALiteral = Map.of("src/Mage.java", "class Mage { String hint = \"// TODO: delegate\"; }", "src/Spell.java",
                "interface Spell { int cast(); }");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, todoOnlyInALiteral, Map.of())).singleElement().asString().contains("stubbed owner Mage");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, honest,
                Map.of("test/MageTest.java", "class MageTest { @Test void hints() { assertEquals(\"// TODO: delegate\", new Mage().hint); } }"))).isEmpty();

        Map<String, String> ownerMentionedOnlyInAComment = Map.of("src/Helper.java", "// class Mage would own this\nclass Helper { // TODO: delegate\n}");
        assertThat(ApprovedOwnershipGate.templateTodoSeamReasons(spec, ownerMentionedOnlyInAComment, Map.of()))
                .anySatisfy(reason -> assertThat(reason).contains("declaring source", "Mage"));
    }

    private static String designAndApi(String designRows, String... declarations) {
        StringBuilder api = new StringBuilder();
        for (String declaration : declarations) {
            String type = declaration.replaceFirst("^.*?(?:class|interface|enum|record)\\s+(\\w+).*$", "$1");
            api.append("### ").append(type).append("\n```java\n").append(declaration).append("\n```\n");
        }
        return "## Design\n| Type | Role | Template status |\n|---|---|---|\n" + designRows + "\n## Public API\n" + api;
    }

    @Test
    void diagramRequired_derivesFromRelationsAmongExerciseTypesAndStudentOwnedTypes() {
        String hierarchy = designAndApi("| Shape | base | given |\n| Circle | shape | stubbed |\n", "public abstract class Shape { public abstract double area(); }",
                "public class Circle extends Shape { public double area(); }");
        String implementation = designAndApi("| Strategy | policy | given |\n| Doubling | policy | stubbed |\n", "public interface Strategy { int apply(int value); }",
                "public class Doubling implements Strategy { public int apply(int value); }");
        String twoStudentOwned = designAndApi("| Account | balance | stubbed |\n| Bank | accounts | student-creates |\n", "public class Account { public int balance(); }",
                "public class Bank { public int total(); }");
        String singleClass = designAndApi("| Calculator | computes | stubbed |\n| Printer | prints | given |\n",
                "public class Calculator implements Comparable<Calculator> { public int compareTo(Calculator other); }", "public class Printer { public void print(int value); }");

        assertThat(ApprovedOwnershipGate.diagramRequired(hierarchy)).as("a class hierarchy among exercise types").isTrue();
        assertThat(ApprovedOwnershipGate.diagramRequired(implementation)).as("an exercise type implementing another").isTrue();
        assertThat(ApprovedOwnershipGate.diagramRequired(twoStudentOwned)).as("two student-owned types").isTrue();
        assertThat(ApprovedOwnershipGate.diagramRequired(singleClass)).as("one student-owned class whose only supertype is a library type").isFalse();
        assertThat(ApprovedOwnershipGate.diagramRequired(singleClass + "\n## Diagram\nyes — the author asked for one\n")).as("the author's answer has no say").isFalse();
        assertThat(ApprovedOwnershipGate.diagramRequired(null)).isFalse();
    }
}
