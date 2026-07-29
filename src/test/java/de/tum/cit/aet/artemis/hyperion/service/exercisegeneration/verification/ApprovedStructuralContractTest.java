package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ApprovedStructuralContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesFencedJavaByAstOwnerWithoutInventingAttributes() throws Exception {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API

                ### NearestStrategy
                ```java
                public class NearestStrategy implements DispatchStrategy {
                    public NearestStrategy();
                    @Override
                    public Elevator selectElevator(List<Elevator> elevators, int callFloor);
                }
                ```

                ### ElevatorDispatcher
                ```java
                public class ElevatorDispatcher {
                    public ElevatorDispatcher(List<Elevator> elevators, DispatchStrategy initialStrategy) { ... }
                    public void setStrategy(DispatchStrategy strategy);
                    public Elevator dispatch(int callFloor);
                }
                ```

                ## Testing Strategy
                """, Set.of("NearestStrategy", "ElevatorDispatcher"));

        assertThat(result.errors()).isEmpty();
        JsonNode oracle = MAPPER.readTree(result.contract().toOracle("seededexercise", MAPPER));
        assertThat(oracle.toString()).contains("\"name\":\"NearestStrategy\"", "\"interfaces\":[\"DispatchStrategy\"]", "\"name\":\"selectElevator\"",
                "\"parameters\":[\"List\",\"int\"]", "\"name\":\"ElevatorDispatcher\"").doesNotContain("attributes", "classNearestStrategyimplements");
    }

    @Test
    void keepsCrossOwnerTypesWithTheDeclaringAstType() throws Exception {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ### Alpha
                ```java
                public class Alpha {
                    public Alpha();
                    public Beta combine(Beta input, Gamma fallback);
                }
                ```
                ### Beta
                ```java
                public class Beta { public Beta(); }
                ```
                ### Gamma
                ```java
                public class Gamma { public Gamma(); }
                ```
                ## Testing Strategy
                """, Set.of("Alpha", "Beta", "Gamma"));

        assertThat(result.errors()).isEmpty();
        JsonNode oracle = MAPPER.readTree(result.contract().toOracle("example", MAPPER));
        JsonNode alpha = oracle.get(0);
        assertThat(alpha.path("methods").toString()).contains("combine", "Beta", "Gamma");
    }

    @Test
    void rejectsMissingAndUnparseableStudentCreatedApis() {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ### Alpha
                Alpha implements `work` somehow.
                """, Set.of("Alpha", "Beta"));

        assertThat(result.errors()).anyMatch(error -> error.contains("Alpha needs one exact Java declaration"))
                .anyMatch(error -> error.contains("Beta needs one exact Java declaration"));
    }

    @Test
    void rendersNegativeTypeKindChecksAndTheImplicitPublicConstructor() throws Exception {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                public class Worker {
                    public static int work(int input);
                }
                ```
                """, Set.of("Worker"));

        assertThat(result.errors()).isEmpty();
        JsonNode oracle = MAPPER.readTree(result.contract().toOracle("example", MAPPER));
        assertThat(oracle.at("/0/class/isInterface").asBoolean()).isFalse();
        assertThat(oracle.at("/0/class/isEnum").asBoolean()).isFalse();
        assertThat(oracle.at("/0/class/isAbstract").asBoolean()).isFalse();
        assertThat(oracle.at("/0/constructors/0/modifiers/0").asText()).isEqualTo("public");
    }

    @Test
    void rejectsAuthorityTheStructuralGraderCannotRepresentExactly() {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                public record RecordType(int value) {}
                ```
                ```java
                public class VarargsType {
                    public void add(String... values);
                }
                ```
                """, Set.of("RecordType", "VarargsType"));

        assertThat(result.errors()).anyMatch(error -> error.contains("RecordType uses a record")).anyMatch(error -> error.contains("VarargsType uses a varargs"));
    }

    @Test
    void rejectsPackagePrivateApiAndTypesNotDeclaredByTheDesign() {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                class Worker {
                    void work();
                }
                public class Invented {
                    public void escape();
                }
                ```
                """, Set.of("Worker"), Set.of("Worker"));

        assertThat(result.errors()).anyMatch(error -> error.contains("Worker must be declared public")).anyMatch(error -> error.contains("package-private signatures"))
                .anyMatch(error -> error.contains("Invented") && error.contains("no row in ## Design"));
    }

    @Test
    void retainsGenericSourceTypesForExactSurfaceComparison() {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                public class Box {
                    public java.util.List<String> values();
                }
                ```
                """, Set.of("Box"), Set.of());

        assertThat(result.errors()).isEmpty();
        assertThat(result.contract()
                .solutionSurfaceReasons(java.util.Map.of("src/Box.java", "public class Box { public java.util.List<Integer> values() { return java.util.List.of(); } }")))
                .singleElement().satisfies(reason -> assertThat(reason).contains("List<String>", "List<Integer>"));
    }

    @Test
    void treatsImportedAndFullyQualifiedGenericTypesAsTheSameApiWhileRenderingAresSimpleNames() throws Exception {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                public interface DispatchStrategy {
                    Elevator select(java.util.List<Elevator> elevators, int callFloor);
                }
                ```
                ```java
                public class Elevator {}
                ```
                """, Set.of("DispatchStrategy", "Elevator"), Set.of("DispatchStrategy"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.contract().solutionSurfaceReasons(java.util.Map.of("src/DispatchStrategy.java", """
                package seededexercise;
                import java.util.List;
                public interface DispatchStrategy {
                    Elevator select(List<Elevator> elevators, int callFloor);
                }
                """, "src/Elevator.java", "package seededexercise; public class Elevator {}"))).isEmpty();

        JsonNode oracle = MAPPER.readTree(result.contract().toOracle("seededexercise", MAPPER, Set.of("DispatchStrategy")));
        assertThat(oracle.at("/0/methods/0/parameters/0").asText()).isEqualTo("List");
    }

    @Test
    void treatsAnUnqualifiedJdkCollectionInTheSpecAsTheImportedRepositoryType() {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                public interface DispatchStrategy {
                    Elevator select(List<Elevator> elevators, int callFloor);
                }
                ```
                ```java
                public class Elevator {}
                ```
                """, Set.of("DispatchStrategy", "Elevator"), Set.of("DispatchStrategy"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.contract().solutionSurfaceReasons(java.util.Map.of("src/DispatchStrategy.java", """
                package seededexercise;
                import java.util.List;
                public interface DispatchStrategy {
                    Elevator select(List<Elevator> elevators, int callFloor);
                }
                """, "src/Elevator.java", "package seededexercise; public class Elevator {}"))).isEmpty();
    }

    @Test
    void doesNotConfuseAnExerciseTypeWithAnEquallyNamedLibraryType() {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                public class List {}
                ```
                ```java
                public class Box {
                    public java.util.List<List> values();
                }
                ```
                """, Set.of("Box", "List"), Set.of());

        assertThat(result.errors()).isEmpty();
        assertThat(result.contract().solutionSurfaceReasons(java.util.Map.of("src/List.java", "package example; public class List {}", "src/Box.java", """
                package example;
                public class Box {
                    public java.util.List<List> values() { return java.util.List.of(); }
                }
                """))).isEmpty();
    }

    @Test
    void projectsOnlyStudentCreatedTypesWhileRetainingTheWholeDesignContract() throws Exception {
        ApprovedStructuralContract.ParseResult result = ApprovedStructuralContract.parse("""
                ## Public API
                ```java
                public class GivenType { public int value(); }
                ```
                ```java
                public interface StudentType { int work(); }
                ```
                """, Set.of("GivenType", "StudentType"), Set.of("StudentType"));

        assertThat(result.errors()).isEmpty();
        assertThat(MAPPER.readTree(result.contract().toOracle("example", MAPPER, Set.of("StudentType"))).toString()).contains("StudentType").doesNotContain("GivenType");
    }
}
