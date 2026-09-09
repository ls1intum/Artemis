package de.tum.cit.aet.artemis.hyperionworker.generation.verification;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief.Mode;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperionworker.config.DockerConfiguration;
import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.CollectedReports;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.DockerSandbox;

/** Actual offline canonical Gradle builds; a shell-only image cannot satisfy this contract. */
@EnabledIfEnvironmentVariable(named = "HYPERION_GRADLE_TEST_IMAGE", matches = "sha256:[a-f0-9]{64}")
class DockerGradleBuildTest {

    private final GenerationResources resources = new GenerationResources();

    private final SandboxBuildCommandService commands = new SandboxBuildCommandService(resources);

    private final GenerationWorkspaceService workspace = new GenerationWorkspaceService(commands, resources);

    private final GenerationInput exercise = new GenerationInput(1, "de.tum.cit.aet.reference", "Count passing scores.", false, Set.of());

    private DockerClient docker;

    private DockerSandbox sandbox;

    @BeforeEach
    void setup() {
        docker = new DockerConfiguration().dockerClient();
        var settings = new WorkerSettings("gradle-test-" + UUID.randomUUID(), System.getenv("HYPERION_GRADLE_TEST_IMAGE"), "runc", 2L * 1024 * 1024 * 1024, 200_000, 256,
                Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofMinutes(2));
        sandbox = new DockerSandbox(docker, settings);
    }

    @AfterEach
    void cleanup() throws IOException {
        sandbox.destroyActiveSessions();
        docker.close();
    }

    @Test
    void specificationDerivedGenericGraderRejectsErasedApiAndAcceptsRenamedTypeParameter() throws IOException {
        String session = sandbox.createSession();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommandService.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));
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
        SeededStructuralTests seeded = new StructuralOracleSeedingService(workspace, specifications).seedIfStructuralDiff(sandbox, session, exercise);
        assertThat(seeded.testNames()).contains("testGenericApi[Box]");
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
        assertThat(correct.testNames()).contains("testGenericApi[Box]");

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
        assertThat(erased.testFailedNames()).contains("testGenericApi[Box]");
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
        assertThat(shadowed.testFailedNames()).contains("testGenericApi[Box]");
        assertThat(shadowed.testFailedNames()).doesNotContain("testMethods[Box]", "testConstructors[Box]");
    }

    @Test
    void verificationDoesNotLeaveADaemonHoldingTheDisposableGradleCache() throws IOException {
        String session = sandbox.createSession();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, snapshot(), true);
        sandbox.copyIn(session, SandboxBuildCommandService.PRISTINE_VERIFY_DIR,
                WorkspaceArchive.buildWorkspaceTarStream(Map.of("verify.sh", commands.verifyScriptContent(exercise)), Map.of()));

        assertThat(build(session, "solution").exitCode()).isZero();

        // A daemon can keep deleted JARs mapped when the next lane replaces the cache, exhausting bounded tmpfs.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var processes = sandbox.exec(session, Duration.ofSeconds(5), "sh", "-c", "ps -eo args | grep '[o]rg.gradle.launcher.daemon.bootstrap.GradleDaemon' || true");
            assertThat(processes.timedOut()).isFalse();
            assertThat(processes.combinedOutput()).isBlank();
        });
    }

    @Test
    void pristineSolutionPassesAndTemplateFailsWithFreshParsedReports() throws IOException {
        String session = sandbox.createSession();
        var seed = snapshot();
        workspace.seedWorkspace(sandbox, session, exercise, Mode.ADAPT, seed, true);
        sandbox.copyIn(session, SandboxBuildCommandService.PRISTINE_VERIFY_DIR,
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
            for (var phase : new ObjectMapper(new YAMLFactory()).readTree(input)) {
                phases.add(phase.required("script").asText());
            }
        }
        var result = sandbox.exec(session, Duration.ofMinutes(3), "sh", "-c",
                "set -e; cd /tmp/ordinary-ci && export GRADLE_USER_HOME=/tmp/hyperion-gradle-home && " + String.join("\n", phases));
        assertThat(result.timedOut()).as(result.combinedOutput()).isFalse();
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
        var result = sandbox.exec(session, Duration.ofMinutes(3), "sh", SandboxBuildCommandService.PRISTINE_VERIFY_PATH, lane, mode);
        assertThat(result.timedOut()).as(result.combinedOutput()).isFalse();
        try (var tar = sandbox.copyOut(session, SandboxBuildCommandService.reportsDirectoryFor(lane))) {
            return BuildSummary.fromReports(CollectedReports.read(tar, lane), result.exitCode(), result.combinedOutput());
        }
    }

    private WorkspaceSnapshot snapshot() throws IOException {
        List<WorkspaceFile> files = new ArrayList<>();
        for (String path : List.of("build.gradle", "settings.gradle", "gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")) {
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
