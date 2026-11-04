package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.aiworker.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.SandboxBuildCommands;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.WorkspaceArchive;

class OutputOnlyMainProbeTest {

    private static final String PATH = "src/example/Library.java";

    private static final String SOLUTION = """
            package example;

            import java.util.ArrayList;
            import java.util.List;

            public class Library {

                private final List<String> titles = new ArrayList<>();

                public void add(String title) {
                    titles.add(title);
                }

                public int size() {
                    return titles.size();
                }

                public static void main(String[] args) {
                    Library library = new Library();
                    library.add("Dune");
                    System.out.println("Books: " + library.size() + " { \\"quoted\\" }");
                }
            }
            """;

    private static final String TEMPLATE = """
            package example;

            public class Library {

                public static void main(String[] args) {
                    // TODO: create a library and print its size
                }
            }
            """;

    private static final Map<String, String> TESTS = Map.of("test/example/LibraryTest.java", """
            package example;
            class LibraryTest {
                @Test void printsTheSize() { Library.main(new String[0]); }
            }
            """);

    @Test
    void probesOnlyStudentOwnedMainsTheGradedTestsCall() {
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION)))
                .containsExactly(new OutputOnlyMainProbe.Target(PATH, "Library", "example.Library"));
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, SOLUTION), Map.of(PATH, SOLUTION))).as("unchanged between template and solution").isEmpty();
        assertThat(OutputOnlyMainProbe.targets(Map.of("test/example/LibraryTest.java", "class LibraryTest { @Test void size() { new Library().size(); } }"), Map.of(PATH, TEMPLATE),
                Map.of(PATH, SOLUTION))).as("no graded test calls main").isEmpty();
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION.replace("library.add(\"Dune\");", "new java.util.Scanner(System.in);"))))
                .as("reads standard input").isEmpty();
    }

    @Test
    void theVariantCompilesKeepsEveryOtherMemberAndPrintsExactlyTheTranscript(@TempDir Path directory) throws Exception {
        String transcript = "Books: 1 { \"quoted\" }\n\ttab \\ backslash \u001b[0m é世 😀\r\n" + "x".repeat(1_500) + "\n";

        String variant = OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION, transcript).orElseThrow();

        assertThat(variant).contains("public void add(String title) {\n        titles.add(title);", "public int size() {").doesNotContain("library.add(\"Dune\")");
        compile(directory, variant);
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(new URL[] { directory.toUri().toURL() }, OutputOnlyMainProbeTest.class.getClassLoader())) {
            Method main = loader.loadClass("example.Library").getMethod("main", String[].class);
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            main.invoke(null, (Object) new String[0]);
        }
        finally {
            System.setOut(original);
        }
        assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo(transcript);
    }

    @Test
    void decodesOnlyASuccessfulBoundedCapture() {
        String encoded = Base64.getMimeEncoder().encodeToString("Books: 1\n".getBytes(StandardCharsets.UTF_8));

        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(0, encoded + "\n", "", false))).contains("Books: 1\n");
        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(92, encoded, "", false))).isEmpty();
        byte[] oversized = new byte[OutputOnlyMainProbe.MAX_TRANSCRIPT_BYTES + 1];
        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(0, Base64.getEncoder().encodeToString(oversized), "", false))).isEmpty();
        assertThat(OutputOnlyMainProbe.captureCommand(new OutputOnlyMainProbe.Target(PATH, "Library", "example.Library"))).contains("'example.Library'", "< /dev/null",
                "timeout 20", "head -c " + (OutputOnlyMainProbe.MAX_TRANSCRIPT_BYTES + 1));
    }

    @Test
    void aPassingSuiteLetsThePrintOnlyVariantSurviveAndAFailingTestKillsIt() {
        List<SemanticMutant> built = new ArrayList<>();
        SemanticMutantExecution.ProbeRunner passing = (mutant, probe) -> {
            built.add(mutant);
            return new BuildSummary(1, 0, 0, false, List.of("printsTheSize"), List.of(), List.of(), "");
        };

        List<SemanticMutantOutcome> survived = OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), Set.of(), target -> Optional.of("Books: 1\n"),
                passing);

        assertThat(survived).singleElement().satisfies(outcome -> {
            assertThat(outcome.disposition()).isEqualTo(Disposition.SURVIVED_GRADED_SUITE);
            assertThat(outcome.mutant().printsTranscriptOnly()).isTrue();
            assertThat(outcome.mutant().ruleId()).isEqualTo("Library.main");
            assertThat(outcome.mutant().originalSolutionSource()).isEqualTo(SOLUTION);
        });
        assertThat(built).singleElement().satisfies(mutant -> assertThat(mutant.mutantSource()).contains("System.out.print(\"Books: 1\\n\");"));

        SemanticMutantExecution.ProbeRunner failing = (mutant, probe) -> new BuildSummary(1, 1, 1, false, List.of("printsTheSize"), List.of("printsTheSize"), List.of(), "");
        assertThat(OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), Set.of(), target -> Optional.of("Books: 1\n"), failing))
                .extracting(SemanticMutantOutcome::disposition).containsExactly(Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), Set.of(PATH), target -> Optional.of("Books: 1\n"), failing))
                .as("a pending probe is rechecked elsewhere").isEmpty();
        assertThat(OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), Set.of(), target -> Optional.empty(), failing))
                .as("a reference run that is not deterministic and bounded proves nothing").isEmpty();
    }

    @Test
    void theVerifierCapturesTheReferenceMainAndRunsTheGradedSuiteAgainstThePrintOnlyVariant() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        String encoded = Base64.getMimeEncoder().encodeToString("Books: 1\n".getBytes(StandardCharsets.UTF_8));
        when(sandbox.exec(anyString(), any(), any(String[].class))).thenAnswer(invocation -> {
            boolean capture = Arrays.stream(invocation.getArguments()).anyMatch(argument -> String.valueOf(argument).contains("hyperion-main"));
            return new SandboxExecResultDTO(0, capture ? encoded : "", "", false);
        });
        when(sandbox.copyOut(anyString(), anyString())).thenAnswer(invocation -> ReportTarFixtures.junitAndScaReports("solution", List.of("printsTheSize"), List.of(), Map.of()));
        List<Map<String, String>> installed = new ArrayList<>();
        doAnswer(invocation -> {
            try (var archive = new TarArchiveInputStream(invocation.getArgument(2, InputStream.class))) {
                installed.add(WorkspaceArchive.readTar(archive, ""));
            }
            return null;
        }).when(sandbox).copyIn(anyString(), anyString(), any());
        SandboxBuildCommands commands = mock(SandboxBuildCommands.class);
        when(commands.verifyScriptContent(any())).thenReturn("#!/bin/sh\n");
        when(commands.behavioralSolutionBuildCommand()).thenReturn("sh /opt/aiworker/verify.sh solution behavior-isolated");
        GenerationInput exercise = new GenerationInput(42, "example", "", false, Set.of(), Set.of(), GenerationBuildTool.GRADLE);

        List<SemanticMutantOutcome> outcomes = new DifferentialVerifier(commands).probeOutputOnlyMains(sandbox, "session", exercise, TESTS, Map.of(PATH, TEMPLATE),
                Map.of(PATH, SOLUTION), SeededStructuralTests.EMPTY, Set.of(), () -> {
                });

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.disposition()).isEqualTo(Disposition.SURVIVED_GRADED_SUITE);
            assertThat(outcome.mutant().printsTranscriptOnly()).isTrue();
        });
        assertThat(installed).anySatisfy(files -> assertThat(files.get("solution/" + PATH)).contains("System.out.print(\"Books: 1\\n\");", "public int size()"));
    }

    private static void compile(Path directory, String source) throws Exception {
        Path file = directory.resolve("example/Library.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int status = compiler.run(null, null, null, "-encoding", "UTF-8", "-d", directory.toString(), file.toString());
        assertThat(status).as("the variant compiles").isZero();
    }
}
