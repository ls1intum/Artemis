package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.dockerjava.api.DockerClient;

import tools.jackson.dataformat.yaml.YAMLMapper;

import de.tum.cit.aet.artemis.aiworker.config.DockerConfiguration;
import de.tum.cit.aet.artemis.aiworker.config.WorkerSandboxConfiguration;
import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.service.sandbox.DockerSandboxService;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief.Mode;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationResources;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.CollectedReports;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.SandboxBuildCommands;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.WorkspaceArchive;

/** Actual offline canonical Gradle and Maven builds; a shell-only image cannot satisfy this contract. */
@EnabledIfEnvironmentVariable(named = "HYPERION_GRADLE_TEST_IMAGE", matches = "sha256:[a-f0-9]{64}")
class DockerGradleBuildTest {

    private final GenerationResources resources = new GenerationResources();

    private final SandboxBuildCommands commands = new SandboxBuildCommands(resources);

    private final GenerationWorkspace workspace = new GenerationWorkspace(resources);

    private final GenerationInput exercise = new GenerationInput(1, "de.tum.cit.aet.reference", "Count passing scores.", false, Set.of(), Set.of(), GenerationBuildTool.GRADLE);

    private static final ContractWitness HIGH_SCORE_WITNESS = new ContractWitness("R1", "testScoreAboveOneHundred", """
            @org.junit.jupiter.api.Test
            void testScoreAboveOneHundred() throws java.io.IOException {
                int score = Integer.parseInt(java.nio.file.Files.readString(java.nio.file.Path.of("fixtures/witness-scores.txt")).strip());
                org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class, UnregisteredFixtureReader::readFixture);
                org.junit.jupiter.api.Assertions.assertEquals(1, ScoreCalculator.countPassing(new int[] {score}), "R1: every score at least 50 passes");
            }
            """, "incorrectly rejects passing scores above 100");

    private DockerClient docker;

    private DockerSandboxService sandbox;

    @BeforeEach
    void setup() {
        docker = new DockerConfiguration().dockerClient();
        var settings = new WorkerSettings("gradle-test-" + UUID.randomUUID(), System.getenv("HYPERION_GRADLE_TEST_IMAGE"), "runc", 2L * 1024 * 1024 * 1024, 200_000, 256,
                Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofMinutes(2), 1, "hyperion-generation", "java-gradle");
        sandbox = new DockerSandboxService(docker, new WorkerSandboxConfiguration().sandboxPolicy(settings));
    }

    @AfterEach
    void cleanup() throws IOException {
        sandbox.destroyActiveSessions();
        docker.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void emptyRepositoryRootsRemainReadableAfterSeedingAndReset(boolean reset) {
        String session = sandbox.createSession();
        WorkspaceSnapshot sourceOnlyTemplate = new WorkspaceSnapshot(List.of(new WorkspaceFile("template/src/Old.java", "class Old {}".getBytes(StandardCharsets.UTF_8), false)));
        var seed = workspace.seedWorkspace(sandbox, session, exercise, Mode.GENERATE, sourceOnlyTemplate, true);
        if (reset) {
            sandbox.resetSession(session);
            workspace.materializeRepositoryFiles(sandbox, session, exercise, Mode.GENERATE, seed.repositoryTextFiles(), seed.repositoryMetadata(), seed.repositoryBinaryFiles(),
                    exercise.problemStatement(), null, null);
        }

        JavaSourceSnapshot sources = JavaSourceSnapshot.capture(sandbox, session);
        assertThat(sources.templateFiles()).isEmpty();
        assertThat(sources.solutionFiles()).isEmpty();
        assertThat(sources.testsFiles()).isEmpty();
        for (RepositoryRole role : List.of(RepositoryRole.TEMPLATE, RepositoryRole.SOLUTION, RepositoryRole.TESTS)) {
            var extracted = workspace.extractRepository(sandbox, session, role, seed.repositoryMetadata().get(role));
            assertThat(extracted.extractionFailed()).as("%s extraction after reset=%s", role, reset).isFalse();
            assertThat(extracted.files()).isEmpty();
        }
    }

    @Test
    void largeFileWritesAndEditsUseArchiveTransfer() throws IOException {
        String session = sandbox.createSession();
        var tools = new SandboxAgentTools(sandbox, session, null, null);
        String prefix = "preserve this prefix\n".repeat(8_000);
        String path = "solution/nested/large.md";
        assertThat(tools.writeFile(path, prefix + "replace this suffix")).startsWith("Wrote ");
        assertThat(tools.editFile(path, "replace this suffix", "updated suffix")).startsWith("Replaced 1");
        try (var archive = sandbox.copyOut(session, "/workspace/" + path)) {
            assertThat(WorkspaceArchive.readTar(archive, "")).containsExactlyEntriesOf(Map.of("large.md", prefix + "updated suffix"));
        }
    }

    @Test
    void contractWitnessRunsUnderAresAndRestoresTheCandidatePolicy() throws IOException {
        String session = sandbox.createSession();
        var seed = workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, probeSnapshot(), true);

        var outcomes = new DifferentialVerifier(commands).evaluateContractWitnesses(sandbox, session, exercise, seed.testsSeedSnapshot(), SeededStructuralTests.EMPTY,
                List.of(HIGH_SCORE_WITNESS), candidateRestorer(session, seed));

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.disposition()).as(outcome.diagnostic()).isEqualTo(ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED);
        });
        assertCandidateRestored(session, seed);
    }

    @Test
    void retainedWitnessStillRunsAfterTheRepairedSolutionAdoptsItsExactMethod() throws IOException {
        var original = probeSnapshot();
        Map<String, String> tests = new LinkedHashMap<>();
        original.files().stream().filter(file -> file.path().startsWith("tests/"))
                .forEach(file -> tests.put(file.path().substring("tests/".length()), new String(file.content(), StandardCharsets.UTF_8)));
        String host = tests.get("test/de/tum/cit/aet/reference/ScoreCalculatorTest.java");
        String adopted = ContractWitnessProbe.buildProbeSource(host, List.of(HIGH_SCORE_WITNESS)).replace(ContractWitnessProbe.PROBE_CLASS_NAME, "AdoptedWitnessTest");
        tests.put("test/de/tum/cit/aet/reference/AdoptedWitnessTest.java", adopted);
        String policy = AresSecurityPolicy.registerTestClasses(tests);
        List<WorkspaceFile> files = new ArrayList<>(original.files().stream().filter(file -> !file.path().equals("tests/SecurityPolicy.yaml")).toList());
        files.add(new WorkspaceFile("tests/SecurityPolicy.yaml", policy.getBytes(StandardCharsets.UTF_8), false));
        files.add(new WorkspaceFile("tests/test/de/tum/cit/aet/reference/AdoptedWitnessTest.java", adopted.getBytes(StandardCharsets.UTF_8), false));
        String session = sandbox.createSession();
        var seed = workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, new WorkspaceSnapshot(files), true);

        var outcomes = new DifferentialVerifier(commands).evaluateContractWitnesses(sandbox, session, exercise, seed.testsSeedSnapshot(), SeededStructuralTests.EMPTY,
                List.of(HIGH_SCORE_WITNESS), candidateRestorer(session, seed));

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.witness()).isEqualTo(HIGH_SCORE_WITNESS);
            assertThat(outcome.disposition()).as(outcome.diagnostic()).isEqualTo(ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED);
        });
        assertCandidateRestored(session, seed);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
        BuildSummary graded = build(session, "solution");
        assertThat(graded.exitCode()).as(graded.buildDiagnostic()).isZero();
        assertThat(graded.testNames()).contains(HIGH_SCORE_WITNESS.testName()).noneMatch(name -> name.contains("HyperionProbe"));
    }

    @Test
    void semanticMutantCounterexampleRunsUnderAresAndRestoresTheCandidatePolicy() throws IOException {
        String session = sandbox.createSession();
        var seed = workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, probeSnapshot(), true);
        String path = "src/de/tum/cit/aet/reference/ScoreCalculator.java";
        String original = seed.repositoryTextFiles().get(RepositoryRole.SOLUTION).get(path);
        var mutant = new SemanticMutant("R1", path, original, original.replace("score >= 50", "score >= 50 && score <= 100"), HIGH_SCORE_WITNESS);

        var outcomes = new DifferentialVerifier(commands).evaluateSemanticMutants(sandbox, session, exercise, seed.testsSeedSnapshot(),
                seed.repositoryTextFiles().get(RepositoryRole.SOLUTION), SeededStructuralTests.EMPTY, List.of(mutant), candidateRestorer(session, seed));

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.disposition()).as(outcome.diagnostic()).isEqualTo(SemanticMutantOutcome.Disposition.SURVIVED_GRADED_SUITE);
        });
        assertCandidateRestored(session, seed);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void probeBuildFailureRestoresTheExactPolicyAndRemovesTemporarySources(boolean semanticMutant) throws IOException {
        String session = sandbox.createSession();
        var seed = workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        Runnable restore = candidateRestorer(session, seed);
        if (semanticMutant) {
            var host = ContractWitnessProbe.host(seed.testsSeedSnapshot()).orElseThrow();
            String path = ContractWitnessProbe.probePath(host.getKey(), seed.testsSeedSnapshot().keySet());
            var overlay = ContractWitnessProbe.workspaceOverlay(seed.testsSeedSnapshot(), path,
                    ContractWitnessProbe.buildProbeSource(host.getValue(), List.of(HIGH_SCORE_WITNESS)));
            assertThatThrownBy(() -> SemanticMutantWorkspaceProbe.run(sandbox, session, null, overlay, restore, () -> {
            }, () -> {
                throw new IllegalStateException("injected build failure");
            })).isInstanceOf(IllegalStateException.class).hasMessage("injected build failure");
        }
        else {
            assertThatThrownBy(() -> ContractWitnessEvaluator.evaluate(sandbox, session, exercise.id(), seed.testsSeedSnapshot(), List.of(HIGH_SCORE_WITNESS), restore, () -> {
            }, () -> {
                throw new IllegalStateException("injected build failure");
            }, () -> {
                throw new AssertionError("starter build must not run after a failed reference build");
            })).isInstanceOf(DifferentialVerifier.VerificationInfrastructureException.class);
        }
        assertCandidateRestored(session, seed);
    }

    private Runnable candidateRestorer(String session, GenerationWorkspace.WorkspaceSeed seed) {
        return () -> {
            sandbox.resetSession(session);
            workspace.materializeRepositoryFiles(sandbox, session, exercise, Mode.ADAPT, seed.repositoryTextFiles(), seed.repositoryMetadata(), seed.repositoryBinaryFiles(),
                    exercise.problemStatement(), null, null);
        };
    }

    private void assertCandidateRestored(String session, GenerationWorkspace.WorkspaceSeed seed) {
        JavaSourceSnapshot restored = JavaSourceSnapshot.capture(sandbox, session);
        assertThat(restored.testsFiles()).containsExactlyInAnyOrderEntriesOf(seed.testsSeedSnapshot());
        assertThat(restored.solutionFiles()).containsExactlyInAnyOrderEntriesOf(seed.repositoryTextFiles().get(RepositoryRole.SOLUTION));
        assertThat(restored.templateFiles()).containsExactlyInAnyOrderEntriesOf(seed.repositoryTextFiles().get(RepositoryRole.TEMPLATE));
        assertThat(restored.testsFiles().keySet()).noneMatch(path -> path.contains(ContractWitnessProbe.PROBE_CLASS_NAME));
    }

    @Test
    void specificationDerivedEnumGraderAcceptsImplicitGenericSuperclassButRejectsErasedMember() throws IOException {
        String session = sandbox.createSession();
        var workspaceSeed = workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
        ApprovedSpecRegistry specifications = new ApprovedSpecRegistry();
        specifications.approve(session, """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | RoastProfile | Declare the profile and its labels | student-creates |
                ## Public API
                ```java
                public enum RoastProfile { LIGHT; public java.util.List<String> labels(); }
                ```
                """);
        // As the orchestrator does: the seeded policy is the baseline the server writes its test-class list into.
        StructuralOracleSeeder seeder = new StructuralOracleSeeder(workspace, specifications);
        seeder.captureBaseline(session, workspaceSeed.testsSeedSnapshot());
        SeededStructuralTests seeded = seeder.seedIfStructuralDiff(sandbox, session, exercise);
        assertThat(seeded.testNames()).contains("testClass[GenericApi:RoastProfile]");
        String path = "solution/src/de/tum/cit/aet/reference/RoastProfile.java";
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, """
                package de.tum.cit.aet.reference;
                public enum RoastProfile {
                    LIGHT;
                    public java.util.List<String> labels() { return java.util.List.of("light"); }
                }
                """), Map.of()));
        var correct = build(session, "solution");
        assertThat(correct.exitCode()).as(correct.buildDiagnostic()).isZero();
        assertThat(correct.testNames()).contains("testClass[GenericApi:RoastProfile]");

        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, """
                package de.tum.cit.aet.reference;
                public enum RoastProfile {
                    LIGHT;
                    public java.util.List labels() { return java.util.List.of("light"); }
                }
                """), Map.of()));
        var erased = build(session, "solution");
        assertThat(erased.exitCode()).isNotZero();
        assertThat(erased.testFailedNames()).contains("testClass[GenericApi:RoastProfile]");
        assertThat(erased.testFailedNames()).doesNotContain("testMethods[RoastProfile]");
    }

    /** Student-owned JUnit classes compile in the assignment source set, and Ares enforces their approved annotations. */
    @Test
    void specificationDerivedMethodGraderAcceptsTheApprovedAnnotationsAndRejectsTheirAbsence() throws IOException {
        String session = sandbox.createSession();
        var workspaceSeed = workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
        ApprovedSpecRegistry specifications = new ApprovedSpecRegistry();
        specifications.approve(session, """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | ScoreChecks | Checks students write | student-creates |
                ## Public API
                ```java
                public class ScoreChecks {
                    @java.lang.annotation.Native public static final int LIMIT = 3;
                    @org.junit.jupiter.api.Test public void checkNullInput();
                    @Override public String toString();
                }
                ```
                """);
        StructuralOracleSeeder seeder = new StructuralOracleSeeder(workspace, specifications);
        seeder.captureBaseline(session, workspaceSeed.testsSeedSnapshot());
        SeededStructuralTests seeded = seeder.seedIfStructuralDiff(sandbox, session, exercise);
        assertThat(seeded.testNames()).contains("testMethods[ScoreChecks]", "testAttributes[ScoreChecks]");
        String path = "solution/src/de/tum/cit/aet/reference/ScoreChecks.java";
        // @Native has source retention, so reflection never sees it and the oracle must not expect it.
        String annotated = """
                package de.tum.cit.aet.reference;
                public class ScoreChecks {
                    @java.lang.annotation.Native public static final int LIMIT = 3;
                    @org.junit.jupiter.api.Test public void checkNullInput() { org.junit.jupiter.api.Assertions.assertTrue(true); }
                    @Override public String toString() { return "checks"; }
                }
                """;
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, annotated), Map.of()));
        seeder.seedIfStructuralDiff(sandbox, session, exercise);
        var correct = build(session, "solution");
        assertThat(correct.exitCode()).as(correct.buildDiagnostic()).isZero();
        assertThat(correct.testNames()).contains("testMethods[ScoreChecks]", "testAttributes[ScoreChecks]");

        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, annotated.replace("@org.junit.jupiter.api.Test ", "")), Map.of()));
        var unannotated = build(session, "solution");
        assertThat(unannotated.exitCode()).isNotZero();
        assertThat(unannotated.testFailedNames()).contains("testMethods[ScoreChecks]");
    }

    // Both build tools must report the seeded dynamic check by its display name; surefire names flat dynamic tests genericApi()[n].
    @ParameterizedTest
    @EnumSource(GenerationBuildTool.class)
    void specificationDerivedGenericGraderRejectsErasedApiAndAcceptsRenamedTypeParameter(GenerationBuildTool buildTool) throws IOException {
        boolean maven = buildTool == GenerationBuildTool.MAVEN;
        var input = maven ? new GenerationInput(1, "de.tum.cit.aet.reference", "Count passing scores.", false, Set.of(), Set.of(), buildTool) : exercise;
        String session = sandbox.createSession();
        var workspaceSeed = workspace.seedWorkspace(sandbox, session, input, Mode.ADAPT, maven ? mavenSnapshot("java/test/maven/projectTemplate/pom.xml") : snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(input)), Map.of()));
        ApprovedSpecRegistry specifications = new ApprovedSpecRegistry();
        specifications.approve(session, """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Box | Own generic type | student-creates |
                ## Public API
                ```java
                public class Box<T> {
                    public Box(T value) { ... }
                    public T get();
                    public java.util.List<T> values();
                }
                ```
                """);
        StructuralOracleSeeder seeder = new StructuralOracleSeeder(workspace, specifications);
        seeder.captureBaseline(session, workspaceSeed.testsSeedSnapshot());
        SeededStructuralTests seeded = seeder.seedIfStructuralDiff(sandbox, session, input);
        assertThat(seeded.testNames()).contains("testClass[GenericApi:Box]");
        String path = "solution/src/de/tum/cit/aet/reference/Box.java";
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, """
                package de.tum.cit.aet.reference;
                public class Box<E> {
                    private final E value;
                    public Box(E value) { this.value = value; }
                    public E get() { return value; }
                    public java.util.List<E> values() { return java.util.List.of(value); }
                }
                """), Map.of()));
        var correct = build(session, "solution");
        assertThat(correct.exitCode()).as(correct.buildDiagnostic()).isZero();
        assertThat(correct.testNames()).contains("testClass[GenericApi:Box]");

        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, """
                package de.tum.cit.aet.reference;
                public class Box<T> {
                    private final Object value;
                    public Box(Object value) { this.value = value; }
                    public Object get() { return value; }
                    public java.util.List<Object> values() { return java.util.List.of(value); }
                }
                """), Map.of()));
        var erased = build(session, "solution");
        assertThat(erased.exitCode()).isNotZero();
        assertThat(erased.testFailedNames()).contains("testClass[GenericApi:Box]");
        assertThat(erased.testFailedNames()).doesNotContain("testMethods[Box]", "testConstructors[Box]");

        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, """
                package de.tum.cit.aet.reference;
                public class Box<Object> {
                    private final java.lang.Object value;
                    public Box(java.lang.Object value) { this.value = value; }
                    public java.lang.Object get() { return value; }
                    public java.util.List<java.lang.Object> values() { return java.util.List.of(value); }
                }
                """), Map.of()));
        var shadowed = build(session, "solution");
        assertThat(shadowed.exitCode()).isNotZero();
        assertThat(shadowed.testFailedNames()).contains("testClass[GenericApi:Box]");
        assertThat(shadowed.testFailedNames()).doesNotContain("testMethods[Box]", "testConstructors[Box]");
    }

    @Test
    void installedReflectionApiPreservesResultsAndStudentExceptions() throws IOException {
        String session = sandbox.createSession();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
        String source = """
                package de.tum.cit.aet.reference;
                import static org.junit.jupiter.api.Assertions.*;
                import static de.tum.cit.ase.ares.api.util.ReflectionTestUtils.*;
                import org.junit.jupiter.api.*;
                import de.tum.cit.ase.ares.api.jupiter.Public;
                import de.tum.cit.ase.ares.api.Policy;
                import de.tum.cit.ase.ares.api.StrictTimeout;
                @Public
                @Policy(value = "SecurityPolicy.yaml")
                @DisplayNameGeneration(DisplayNameGenerator.Simple.class)
                class ScoreCalculatorTest {
                    @Test
                    @StrictTimeout(1)
                    void reflectionUsesDeclaredTypesAndPreservesExceptions() {
                        Class<?> owner = getClazz("de.tum.cit.aet.reference.ScoreCalculator");
                        var method = getMethod(owner, "countPassing", int[].class);
                        assertEquals(2, (int) invokeMethod(null, method, (Object) new int[] { 10, 60, 100 }));
                        assertThrows(NullPointerException.class, () -> invokeMethodRethrowing(null, method, (Object) null));
                    }
                }
                """;
        String path = "tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java";
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, source.replace("ares.api.util", "ares.util")), Map.of()));
        var wrongImport = build(session, "solution");
        assertThat(wrongImport.exitCode()).isNotZero();
        assertThat(wrongImport.buildDiagnostic()).contains("ares.util does not exist");
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, source), Map.of()));
        var correct = build(session, "solution");
        assertThat(correct.exitCode()).as(correct.buildDiagnostic()).isZero();
        assertThat(correct.testNames()).contains("reflectionUsesDeclaredTypesAndPreservesExceptions");
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, source.replace("invokeMethodRethrowing", "invokeMethod")), Map.of()));
        var wrappedException = build(session, "solution");
        assertThat(wrappedException.exitCode()).isNotZero();
        assertThat(wrappedException.testFailedNames()).contains("reflectionUsesDeclaredTypesAndPreservesExceptions");
    }

    @Test
    void instructorFixturesResetSharedStatePerTestWithoutChangingTheExerciseApi() throws IOException {
        String session = sandbox.createSession();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
        String calculator = new String(read("hyperion/readiness/java/solution/src/de/tum/cit/aet/reference/ScoreCalculator.java"), StandardCharsets.UTF_8);
        calculator = calculator.substring(0, calculator.lastIndexOf('}')) + "private static int nextId; public static int nextId() { return nextId++; }\n}";
        String source = """
                package de.tum.cit.aet.reference;
                import static org.junit.jupiter.api.Assertions.*;
                import org.junit.jupiter.api.*;
                import de.tum.cit.ase.ares.api.jupiter.Public;
                import de.tum.cit.ase.ares.api.Policy;
                import de.tum.cit.ase.ares.api.StrictTimeout;
                @Public
                @Policy(value = "SecurityPolicy.yaml")
                @DisplayNameGeneration(DisplayNameGenerator.Simple.class)
                @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
                class ScoreCalculatorTest {
                    @BeforeAll
                    static void resetState() throws Exception {
                        var counter = ScoreCalculator.class.getDeclaredField("nextId");
                        counter.setAccessible(true);
                        counter.setInt(null, 0);
                    }
                    @Test @Order(1) @StrictTimeout(1)
                    void firstScenario() { assertEquals(0, ScoreCalculator.nextId()); assertEquals(1, ScoreCalculator.nextId()); }
                    @Test @Order(2) @StrictTimeout(1)
                    void secondScenario() { assertEquals(0, ScoreCalculator.nextId()); assertEquals(1, ScoreCalculator.nextId()); }
                }
                """;
        String path = "tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java";
        sandbox.copyIn(session, "/workspace",
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("solution/src/de/tum/cit/aet/reference/ScoreCalculator.java", calculator, path, source), Map.of()));
        var sharedFixture = build(session, "solution");
        assertThat(sharedFixture.testFailedNames()).as(sharedFixture.buildDiagnostic()).containsExactly("secondScenario");
        sandbox.copyIn(session, "/workspace",
                WorkspaceArchive.buildWorkspaceTarStream(Map.of(path, source.replace("@BeforeAll", "@BeforeEach").replace("static void resetState", "void resetState")), Map.of()));
        var isolatedFixture = build(session, "solution");
        assertThat(isolatedFixture.exitCode()).as(isolatedFixture.buildDiagnostic()).isZero();
        assertThat(isolatedFixture.testNames()).contains("firstScenario", "secondScenario");
        assertThat(isolatedFixture.failures()).isZero();
    }

    @Test
    void verificationDoesNotLeaveADaemonHoldingTheDisposableGradleCache() throws IOException {
        String session = sandbox.createSession();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));

        assertThat(build(session, "solution").exitCode()).isZero();

        // A daemon can keep deleted JARs mapped when the next lane replaces the cache, exhausting bounded tmpfs.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var processes = sandbox.exec(session, Duration.ofSeconds(5), "sh", "-c", "ps -eo args | grep '[o]rg.gradle.launcher.daemon.bootstrap.GradleDaemon' || true");
            assertThat(processes.timedOut()).isFalse();
            assertThat(processes.combinedOutput()).isBlank();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2 })
    void studentWrittenTestsMustRejectFaultyBehaviorRatherThanOnlyRunOnTheCorrectLibrary(int assessment) throws IOException {
        boolean gradeFaults = assessment == 1;
        String learner = """
                package de.tum.cit.aet.reference;
                public class LearnerCheck {
                    @org.junit.jupiter.api.Test
                    public void verifiesSum() {
                        Toolbox.observations++;
                        org.junit.jupiter.api.Assertions.assertEquals(5, Toolbox.sum(2, 3));
                    }
                }
                """;
        String support = """
                package de.tum.cit.aet.reference;
                public class Toolbox {
                    private static boolean faulty;
                    public static int observations;
                    public static int sum(int a, int b) { return faulty ? 0 : a + b; }
                }
                """;
        String instructor = """
                package de.tum.cit.aet.reference;
                import static org.junit.jupiter.api.Assertions.*;
                import org.junit.jupiter.api.Test;
                import de.tum.cit.ase.ares.api.Policy;
                import de.tum.cit.ase.ares.api.StrictTimeout;
                import de.tum.cit.ase.ares.api.jupiter.Public;
                @Public @Policy("SecurityPolicy.yaml")
                @org.junit.jupiter.api.DisplayNameGeneration(org.junit.jupiter.api.DisplayNameGenerator.Simple.class)
                class ScoreCalculatorTest {
                    @Test @StrictTimeout(5) void testLearnerAssertions() throws Exception {
                        var faulty = Toolbox.class.getDeclaredField("faulty");
                        faulty.setAccessible(true);
                        faulty.setBoolean(null, false);
                        Toolbox.observations = 0;
                        assertDoesNotThrow(() -> new LearnerCheck().verifiesSum());
                        %s
                    }
                }
                """.formatted(gradeFaults
                ? "faulty.setBoolean(null, true); try { assertThrows(AssertionError.class, () -> new LearnerCheck().verifiesSum()); } finally { faulty.setBoolean(null, false); }"
                : assessment == 2 ? "assertEquals(1, Toolbox.observations);" : "");
        List<WorkspaceFile> files = new ArrayList<>(
                snapshot().files().stream().filter(file -> !file.path().equals("tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java")).toList());
        files.add(new WorkspaceFile("tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java", instructor.getBytes(StandardCharsets.UTF_8), false));
        String path = "src/de/tum/cit/aet/reference/LearnerCheck.java";
        files.add(new WorkspaceFile("solution/" + path, learner.getBytes(StandardCharsets.UTF_8), false));
        files.add(new WorkspaceFile("template/" + path, learner
                .replace("org.junit.jupiter.api.Assertions.assertEquals(5, Toolbox.sum(2, 3));", "throw new UnsupportedOperationException();").getBytes(StandardCharsets.UTF_8),
                false));
        for (String lane : List.of("template", "solution")) {
            files.add(new WorkspaceFile(lane + "/src/de/tum/cit/aet/reference/Toolbox.java", support.getBytes(StandardCharsets.UTF_8), false));
        }
        String session = sandbox.createSession();
        var seed = workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, new WorkspaceSnapshot(files), true);
        var specifications = new ApprovedSpecRegistry();
        specifications.approve(session, """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | LearnerCheck | Tests the learner writes | stubbed |
                ## Public API
                ```java
                public class LearnerCheck {
                    @org.junit.jupiter.api.Test public void verifiesSum();
                }
                ```
                """);
        var seeder = new StructuralOracleSeeder(workspace, specifications);
        seeder.captureBaseline(session, seed.testsSeedSnapshot());
        var structural = seeder.seedIfStructuralDiff(sandbox, session, exercise);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
        assertThat(build(session, "solution").exitCode()).isZero();
        assertThat(build(session, "template").testFailedNames()).contains("testLearnerAssertions");
        String plan = "{\"tests\":[{\"name\":\"testLearnerAssertions\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of("test-plan.json", plan), Map.of()));

        if (assessment == 2) {
            var sources = JavaSourceSnapshot.capture(sandbox, session);
            var outcomes = new DifferentialVerifier(commands).probeStudentTestAssertions(sandbox, session, exercise, sources.templateFiles(), sources.solutionFiles(), plan,
                    structural, () -> sources.restore(sandbox, session));
            assertThat(outcomes).anySatisfy(outcome -> {
                assertThat(outcome.mutant().evidence()).isEqualTo(SemanticMutant.Evidence.EMPTY_STUDENT_TESTS);
                assertThat(outcome.disposition()).isEqualTo(SemanticMutantOutcome.Disposition.KILLED_BY_GRADED_SUITE);
            }).anySatisfy(outcome -> {
                assertThat(outcome.mutant().evidence()).isEqualTo(SemanticMutant.Evidence.NON_FAILING_STUDENT_TESTS);
                assertThat(outcome.disposition()).isEqualTo(SemanticMutantOutcome.Disposition.SURVIVED_GRADED_SUITE);
            });
        }
        assertThat(sandbox.exec(session, java.time.Duration.ofSeconds(5), "sh", "-c", "test ! -e /workspace/verify.sh").isSuccess())
                .as("source-destructive verification is available only through server-managed tools").isTrue();
        var failure = StageAssessmentCheck.failure(new DifferentialVerifier(commands), sandbox, session, exercise, structural, org.mockito.Mockito.mock(AgentVerifyReport.class));

        if (gradeFaults) {
            assertThat(failure).isEmpty();
        }
        else {
            assertThat(failure).hasValueSatisfying(result -> {
                assertThat(result.contractRedesignRequired()).isTrue();
                assertThat(result.observation()).contains("empty learner-written JUnit", "LearnerCheck.java");
            });
        }
        assertThat(JavaSourceSnapshot.capture(sandbox, session).solutionFiles()).containsEntry(path, learner);
        if (gradeFaults) {
            var before = JavaSourceSnapshot.capture(sandbox, session);
            var tools = new SandboxAgentTools(sandbox, session, new DifferentialVerifier(commands), exercise);
            tools.configureStructuralOracleRefresh(() -> structural);
            tools.verify();
            var after = JavaSourceSnapshot.capture(sandbox, session);
            assertThat(after.solutionFiles()).isEqualTo(before.solutionFiles());
            assertThat(after.templateFiles()).isEqualTo(before.templateFiles());
            assertThat(after.testsFiles()).isEqualTo(before.testsFiles());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2 })
    void assessmentGateDistinguishesTranscriptGradingFromSuppliedRuntimeObservation(int assessment) throws IOException {
        boolean observeInteraction = assessment == 1;
        String sourcePath = "src/de/tum/cit/aet/reference/ScoreCalculator.java";
        String reference = """
                package de.tum.cit.aet.reference;
                public class ScoreCalculator {
                    public static void main(String[] args) {
                        Counter counter = new Counter();
                        counter.increment();
                        counter.reset();
                        System.out.println(counter.value());
                    }
                }
                """;
        if (assessment == 2) {
            reference = reference.replace("public static void main",
                    "public static Counter runScenario() { Counter c = new Counter(); c.increment(); c.reset(); return c; } public static void main");
        }
        String supplied = """
                package de.tum.cit.aet.reference;
                public class Counter {
                    private static Counter observed;
                    private int value;
                    private final int[] observedValues = new int[2];
                    private int observedChanges;
                    public Counter() { observed = this; }
                    public void increment() { value++; observedValues[observedChanges++] = value; }
                    public void reset() { value = 0; observedValues[observedChanges++] = value; }
                    public int observedChanges() { return observedChanges; }
                    public int valueAfterChange(int index) { return observedValues[index]; }
                    public int value() { return value; }
                    public static Counter lastCreated() { return observed; }
                }
                """;
        String testSource = """
                package de.tum.cit.aet.reference;
                import static org.junit.jupiter.api.Assertions.*;
                import java.io.ByteArrayOutputStream;
                import java.io.PrintStream;
                import org.junit.jupiter.api.Test;
                import de.tum.cit.ase.ares.api.Policy;
                import de.tum.cit.ase.ares.api.StrictTimeout;
                import de.tum.cit.ase.ares.api.jupiter.Public;
                @Public @Policy("SecurityPolicy.yaml")
                @org.junit.jupiter.api.DisplayNameGeneration(org.junit.jupiter.api.DisplayNameGenerator.Simple.class)
                class ScoreCalculatorTest {
                    @Test @StrictTimeout(5)
                    void testDemo() {
                        PrintStream previous = System.out;
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try {
                            System.setOut(new PrintStream(output));
                            ScoreCalculator.main(new String[0]);
                        } finally { System.setOut(previous); }
                        assertEquals("0", output.toString().strip());
                        %s
                    }
                }
                """.formatted(
                observeInteraction
                        ? "assertNotNull(Counter.lastCreated()); assertEquals(0, Counter.lastCreated().value()); "
                                + "assertEquals(2, Counter.lastCreated().observedChanges()); assertEquals(1, Counter.lastCreated().valueAfterChange(0)); "
                                + "assertEquals(0, Counter.lastCreated().valueAfterChange(1));"
                        : "");
        if (assessment == 2) {
            testSource = testSource.replace("assertEquals(\"0\", output.toString().strip());",
                    "assertEquals(\"0\", output.toString().strip()); assertEquals(0, ScoreCalculator.runScenario().value());");
        }
        List<WorkspaceFile> files = new ArrayList<>();
        snapshot().files().stream().filter(file -> file.path().startsWith("tests/") && !file.path().endsWith(".java")).forEach(files::add);
        files.add(new WorkspaceFile("solution/" + sourcePath, reference.getBytes(StandardCharsets.UTF_8), false));
        files.add(new WorkspaceFile("template/" + sourcePath, reference.replace("Counter counter = new Counter();", "").replace("counter.increment();", "")
                .replace("counter.reset();", "").replace("System.out.println(counter.value());", "").getBytes(StandardCharsets.UTF_8), false));
        for (String lane : List.of("solution", "template")) {
            files.add(new WorkspaceFile(lane + "/src/de/tum/cit/aet/reference/Counter.java", supplied.getBytes(StandardCharsets.UTF_8), false));
        }
        files.add(new WorkspaceFile("tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java", testSource.getBytes(StandardCharsets.UTF_8), false));
        String plan = "{\"tests\":[{\"name\":\"testDemo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";
        files.add(new WorkspaceFile("test-plan.json", plan.getBytes(StandardCharsets.UTF_8), false));
        String session = sandbox.createSession();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, new WorkspaceSnapshot(files), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
        assertThat(build(session, "solution").failures()).isZero();
        assertThat(build(session, "template").testFailedNames()).contains("testDemo");

        // Reset leaves the final value unchanged even when the earlier increment is missing.
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of("solution/" + sourcePath, reference.replace("counter.increment();", "")), Map.of()));
        var missingEarlierOperation = build(session, "solution");
        if (observeInteraction) {
            assertThat(missingEarlierOperation.testFailedNames()).contains("testDemo");
        }
        else {
            assertThat(missingEarlierOperation.failures()).isZero();
        }
        sandbox.copyIn(session, "/workspace", WorkspaceArchive.buildWorkspaceTarStream(Map.of("solution/" + sourcePath, reference), Map.of()));

        var failure = StageAssessmentCheck.failure(new DifferentialVerifier(commands), sandbox, session, exercise, SeededStructuralTests.EMPTY,
                org.mockito.Mockito.mock(AgentVerifyReport.class));

        if (observeInteraction) {
            assertThat(failure).isEmpty();
        }
        else {
            assertThat(failure).hasValueSatisfying(result -> assertThat(result.contractRedesignRequired()).isTrue());
        }
        assertThat(sandbox.exec(session, Duration.ofSeconds(10), "cat", "/workspace/solution/" + sourcePath).stdout()).isEqualTo(reference);
    }

    @Test
    void pristineSolutionPassesAndTemplateFailsWithFreshParsedReports() throws IOException {
        String session = sandbox.createSession();
        var seed = snapshot();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, seed, true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));

        var solution = build(session, "solution");
        assertThat(solution.exitCode()).as(solution.buildDiagnostic()).isZero();
        assertThat(solution.tests()).isEqualTo(4);
        assertThat(solution.failures()).isZero();
        var ordinarySolution = ordinaryBuild(session, seed, "solution");
        assertThat(ordinarySolution.exitCode()).as(ordinarySolution.buildDiagnostic()).isEqualTo(solution.exitCode());
        assertThat(ordinarySolution.testNames()).containsExactlyInAnyOrderElementsOf(solution.testNames());
        assertThat(ordinarySolution.failures()).isEqualTo(solution.failures());
        assertThat(solution.testNames()).containsExactlyInAnyOrder("testPublicApi", "testRepresentativeScores", "testBoundaryScores", "testEmptyInput");

        var template = build(session, "template");
        assertThat(template.tests()).as(template.buildDiagnostic()).isEqualTo(4);
        assertThat(template.exitCode()).isNotZero();
        var ordinaryTemplate = ordinaryBuild(session, seed, "template");
        assertThat(ordinaryTemplate.exitCode()).isEqualTo(template.exitCode());
        assertThat(ordinaryTemplate.testNames()).containsExactlyInAnyOrderElementsOf(template.testNames());
        assertThat(ordinaryTemplate.testFailedNames()).containsExactlyInAnyOrderElementsOf(template.testFailedNames());
        assertThat(template.testFailedNames()).containsExactlyInAnyOrder("testRepresentativeScores", "testBoundaryScores", "testEmptyInput");

        // A second successful lane must not pick up the failing template's reports or its compiled classes.
        var repeated = build(session, "solution");
        assertThat(repeated.exitCode()).as(repeated.buildDiagnostic()).isZero();
        assertThat(repeated.tests()).isEqualTo(4);
        assertThat(repeated.failures()).isZero();

        var isolated = build(session, "solution", "behavior-isolated");
        assertThat(isolated.exitCode()).as(isolated.buildDiagnostic()).isZero();
        assertThat(isolated.testNames()).containsExactlyInAnyOrderElementsOf(solution.testNames());
        assertThat(isolated.failures()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "java/test/maven/projectTemplate/pom.xml", "java/maven_maven/test/projectTemplate/pom.xml" })
    void mavenSolutionPassesAndTemplateFailsOfflineWithTheMavenHarness(String pomTemplate) throws IOException {
        var mavenExercise = new GenerationInput(1, "de.tum.cit.aet.reference", "Count passing scores.", false, Set.of(), Set.of(), GenerationBuildTool.MAVEN);
        String session = sandbox.createSession();
        workspace.seedWorkspace(sandbox, session, mavenExercise, Mode.ADAPT, mavenSnapshot(pomTemplate), true);
        sandbox.copyIn(session, SandboxBuildCommands.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(mavenExercise)), Map.of()));

        var solution = build(session, "solution");
        assertThat(solution.exitCode()).as(solution.buildDiagnostic()).isZero();
        assertThat(solution.testNames()).containsExactlyInAnyOrder("testPublicApi", "testRepresentativeScores", "testBoundaryScores", "testEmptyInput");
        assertThat(solution.failures()).isZero();

        var template = build(session, "template");
        assertThat(template.tests()).as(template.buildDiagnostic()).isEqualTo(4);
        assertThat(template.exitCode()).isNotZero();
        assertThat(template.testFailedNames()).containsExactlyInAnyOrder("testRepresentativeScores", "testBoundaryScores", "testEmptyInput");

        var isolated = build(session, "solution", "behavior-isolated");
        assertThat(isolated.exitCode()).as(isolated.buildDiagnostic()).isZero();
        assertThat(isolated.testNames()).containsExactlyInAnyOrderElementsOf(solution.testNames());
        assertThat(isolated.failures()).isZero();
    }

    /** The Gradle seed with the rendered Maven test harness in place of the Gradle one, as exercise creation produces it. */
    private WorkspaceSnapshot mavenSnapshot(String pomTemplate) throws IOException {
        List<WorkspaceFile> files = new ArrayList<>();
        for (WorkspaceFile file : snapshot().files()) {
            if (file.path().equals("tests/SecurityPolicy.yaml")) {
                String policy = new String(file.content(), StandardCharsets.UTF_8).replace("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ", "JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ");
                files.add(new WorkspaceFile(file.path(), policy.getBytes(StandardCharsets.UTF_8), false));
            }
            else if (!file.path().startsWith("tests/") || file.path().startsWith("tests/test/")) {
                files.add(file);
            }
        }
        String pom = new String(read(pomTemplate), StandardCharsets.UTF_8);
        for (String block : List.of("static-code-analysis", "maven-central-mirror")) {
            pom = pom.replaceAll("(?s)<!--%" + block + "-start%-->.*?<!--%" + block + "-stop%-->", "");
        }
        pom = pom.replace("${studentWorkingDirectory}", "/assignment/src").replace("${exerciseNamePomXml}", "hyperion-readiness").replace("${exerciseName}", "hyperion-readiness")
                .replace("${packageName}", "de.tum.cit.aet.reference").replace("${packaging}", "jar");
        files.add(new WorkspaceFile("tests/pom.xml", pom.getBytes(StandardCharsets.UTF_8), false));
        return new WorkspaceSnapshot(files);
    }

    private BuildSummary ordinaryBuild(String session, WorkspaceSnapshot seed, String lane) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<String> executable = new LinkedHashSet<>();
        for (var file : seed.files()) {
            String path;
            if (file.path().startsWith("tests/")) {
                path = file.path().substring("tests/".length());
            }
            else if (file.path().startsWith(lane + "/")) {
                path = "assignment/" + file.path().substring(lane.length() + 1);
            }
            else {
                continue;
            }
            files.put(path, file.content());
            if (file.executable()) {
                executable.add(path);
            }
        }
        assertThat(sandbox.exec(session, Duration.ofSeconds(10), "sh", "-c", "rm -rf /tmp/ordinary-ci; mkdir /tmp/ordinary-ci").isSuccess()).isTrue();
        sandbox.copyIn(session, "/tmp/ordinary-ci", WorkspaceArchive.buildFilesTarStream(Map.of(), files, executable));
        List<String> phases = new ArrayList<>();
        try (var input = resources.getResource(Path.of("templates/phases/java/plain_gradle.yaml")).getInputStream()) {
            for (var phase : YAMLMapper.builder().build().readTree(input)) {
                phases.add(phase.required("script").asText());
            }
        }
        var result = sandbox.exec(session, Duration.ofMinutes(3), "sh", "-c",
                "set -e; if [ ! -d /tmp/hyperion-gradle-home ]; then mkdir /tmp/hyperion-gradle-home; cp -a /root/.gradle/. /tmp/hyperion-gradle-home/; fi; "
                        + "cd /tmp/ordinary-ci && export GRADLE_USER_HOME=/tmp/hyperion-gradle-home GRADLE_OPTS=-Dorg.gradle.daemon=false && " + String.join("\n", phases));
        assertThat(result.timedOut()).as(result.combinedOutput()).isFalse();
        if (lane.equals("solution")) {
            assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
        }
        Map<String, byte[]> reports = new LinkedHashMap<>();
        try (var tar = sandbox.copyOut(session, "/tmp/ordinary-ci/build/test-results/test")) {
            WorkspaceArchive.readTar(tar, "test").forEach((name, content) -> {
                if (name.endsWith(".xml")) {
                    reports.put(reports.size() + "__junit.xml", content.getBytes(StandardCharsets.UTF_8));
                }
            });
        }
        return BuildSummary.fromReports(reports, result.exitCode(), result.combinedOutput());
    }

    private BuildSummary build(String session, String lane) throws IOException {
        return build(session, lane, "");
    }

    private BuildSummary build(String session, String lane, String mode) throws IOException {
        var result = sandbox.exec(session, Duration.ofMinutes(3), "sh", SandboxBuildCommands.PRISTINE_VERIFY_PATH, lane, mode);
        assertThat(result.timedOut()).as(result.combinedOutput()).isFalse();
        try (var tar = sandbox.copyOut(session, SandboxBuildCommands.reportsDirectoryFor(lane))) {
            return BuildSummary.fromReports(CollectedReports.read(tar, lane), result.exitCode(), result.combinedOutput());
        }
    }

    private WorkspaceSnapshot probeSnapshot() throws IOException {
        List<WorkspaceFile> files = new ArrayList<>();
        for (WorkspaceFile file : snapshot().files()) {
            String text = new String(file.content(), StandardCharsets.UTF_8);
            if (file.path().equals("tests/SecurityPolicy.yaml")) {
                text = text.replace("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ", "JAVA_USING_GRADLE_ARCHUNIT_AND_INSTRUMENTATION");
            }
            else if (file.path().equals("tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java")) {
                // A separate top-level class is not exempted by the host test class's policy entry.
                text += """

                        final class UnregisteredFixtureReader {
                            static String readFixture() throws java.io.IOException {
                                return java.nio.file.Files.readString(java.nio.file.Path.of("fixtures/witness-scores.txt"));
                            }
                        }
                        """;
            }
            else {
                files.add(file);
                continue;
            }
            files.add(new WorkspaceFile(file.path(), text.getBytes(StandardCharsets.UTF_8), file.executable()));
        }
        files.add(new WorkspaceFile("tests/fixtures/witness-scores.txt", "150\n".getBytes(StandardCharsets.UTF_8), false));
        return new WorkspaceSnapshot(files);
    }

    private WorkspaceSnapshot snapshot() throws IOException {
        List<WorkspaceFile> files = new ArrayList<>();
        for (String path : List.of("build.gradle", "settings.gradle", "gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
                "gradle/AresReservedPackages.gradle")) {
            byte[] bytes = read("java/test/gradle/projectTemplate/" + path);
            if (path.endsWith(".gradle")) {
                String text = new String(bytes, StandardCharsets.UTF_8);
                for (String block : List.of("static-code-analysis", "sequential", "maven-central-mirror")) {
                    text = text.replaceAll("(?ms)^[^\\n]*// %" + block + "-start%[^\\n]*\\n.*?^[^\\n]*// %" + block + "-stop%[^\\n]*\\n?", "");
                }
                bytes = text.replace("${exerciseNamePomXml}", "hyperion-readiness").replace("${studentWorkingDirectoryNoSlash}", "assignment/src").getBytes(StandardCharsets.UTF_8);
            }
            files.add(new WorkspaceFile("tests/" + path, bytes, path.equals("gradlew")));
        }
        files.add(new WorkspaceFile("tests/SecurityPolicy.yaml", read("hyperion/readiness/java/tests/SecurityPolicy.yaml"), false));
        String sourcePath = "src/de/tum/cit/aet/reference/ScoreCalculator.java";
        files.add(new WorkspaceFile("solution/" + sourcePath, read("hyperion/readiness/java/solution/" + sourcePath), false));
        files.add(new WorkspaceFile("template/" + sourcePath, """
                package de.tum.cit.aet.reference;
                public final class ScoreCalculator {
                    private ScoreCalculator() {}
                    public static int countPassing(int[] scores) {
                        throw new UnsupportedOperationException("Not implemented");
                    }
                }
                """.getBytes(StandardCharsets.UTF_8), false));
        for (String area : List.of("behavior", "structural")) {
            String test = area.equals("behavior") ? "ScoreCalculatorTest.java" : "ScoreCalculatorStructureTest.java";
            String path = "de/tum/cit/aet/reference/" + test;
            files.add(new WorkspaceFile("tests/test/" + path, read("hyperion/readiness/java/tests/" + area + "/" + path), false));
        }
        return new WorkspaceSnapshot(files);
    }

    private byte[] read(String path) throws IOException {
        try (var input = resources.getResource(Path.of("templates", path)).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            return path.endsWith(".java") ? GenerationResources.javaGradleFixture(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8) : bytes;
        }
    }
}
