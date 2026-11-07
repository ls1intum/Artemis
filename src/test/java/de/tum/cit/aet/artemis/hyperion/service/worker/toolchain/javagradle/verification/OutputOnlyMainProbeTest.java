package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
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
import java.util.concurrent.atomic.AtomicReference;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

    /** A graded test that captures what main prints and compares it, the shape a print-only main can pass. */
    private static final Map<String, String> TESTS = Map.of("test/example/LibraryTest.java", """
            package example;
            class LibraryTest {
                private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                @BeforeEach void capture() { System.setOut(new java.io.PrintStream(out)); }
                @Test void printsTheSize() {
                    Library.main(new String[0]);
                    assertEquals("Books: 1\\n", out.toString(), "main prints the size");
                }
            }
            """);

    private static final String PLAN = """
            {"tests":[{"name":"printsTheSize","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"}]}
            """;

    @Test
    void probesOnlyStudentOwnedMainsTheGradedTestsCall() {
        Set<String> graded = OutputOnlyMainProbe.gradedTestMethods(PLAN);
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), graded))
                .containsExactly(new OutputOnlyMainProbe.Target(PATH, "Library", "example.Library"));
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, SOLUTION), Map.of(PATH, SOLUTION), graded)).as("unchanged between template and solution").isEmpty();
        assertThat(OutputOnlyMainProbe.targets(Map.of("test/example/LibraryTest.java", "class LibraryTest { @Test void size() { new Library().size(); } }"), Map.of(PATH, TEMPLATE),
                Map.of(PATH, SOLUTION), graded)).as("no graded test calls main").isEmpty();
        assertThat(
                OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION.replace("library.add(\"Dune\");", "new java.util.Scanner(System.in);")), graded))
                .as("reads standard input").isEmpty();
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(), Map.of("test/example/Library.java", SOLUTION), graded)).as("outside the student source tree").isEmpty();
    }

    @Test
    void mainIsFoundAfterAnyArrangementOfItsModifiersButOnlyWhenItIsPublicAndStatic() {
        String signature = "public static void main(String[] args)";

        assertThat(OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION.replace(signature, "static\n    public final void main(final String... args)"), "x")).isPresent();
        assertThat(OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION.replace(signature, "public static void main(String args [ ])"), "x")).isPresent();
        assertThat(OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION.replace(signature, "public void main(String[] args)"), "x")).as("an instance main").isEmpty();
        assertThat(OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION.replace(signature, "Library.public static void main(String[] args)"), "x"))
                .as("a keyword that continues a qualified name is no modifier").isEmpty();
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void longModifierRunsPackageNamesBlankLinesAndHeadersAreScannedInLinearTime() {
        String manyModifiers = SOLUTION.replace("public static void main", "public ".repeat(20_000) + "static void main");
        String modifiersWithoutMain = SOLUTION.replace("public static void main", "public ".repeat(20_000) + "static void notMain");
        String longPackage = SOLUTION.replace("package example;", "\n".repeat(100_000) + "package example" + ".a".repeat(50_000) + ";");
        Map<String, String> unterminatedHeader = Map.of("test/example/LibraryTest.java", TESTS.get("test/example/LibraryTest.java") + "void helper() throws" + " ".repeat(100_000));
        Set<String> graded = OutputOnlyMainProbe.gradedTestMethods(PLAN);

        assertThat(OutputOnlyMainProbe.transcriptOnlyVariant(manyModifiers, "x")).isPresent();
        assertThat(OutputOnlyMainProbe.transcriptOnlyVariant(modifiersWithoutMain, "x")).isEmpty();
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, longPackage), graded)).singleElement()
                .isEqualTo(new OutputOnlyMainProbe.Target(PATH, "Library", "example" + ".a".repeat(50_000) + ".Library"));
        assertThat(OutputOnlyMainProbe.targets(unterminatedHeader, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), graded))
                .containsExactly(new OutputOnlyMainProbe.Target(PATH, "Library", "example.Library"));
    }

    @Test
    void onlyAGradedTestThatComparesThePrintedOutputMakesMainAProbeTarget() {
        Set<String> graded = OutputOnlyMainProbe.gradedTestMethods(PLAN);
        Map<String, String> smokeTest = Map.of("test/example/LibraryTest.java", """
                class LibraryTest {
                    private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    @BeforeEach void capture() { System.setOut(new java.io.PrintStream(out)); }
                    @Test void printsTheSize() { assertDoesNotThrow(() -> Library.main(new String[0])); }
                }
                """);
        Map<String, String> noCapture = Map.of("test/example/LibraryTest.java", """
                class LibraryTest {
                    @Test void printsTheSize() { Library.main(new String[0]); assertEquals(1, new Library().size()); }
                }
                """);
        Map<String, String> throughHelper = Map.of("test/example/LibraryTest.java", """
                class LibraryTest {
                    private String run() {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        System.setOut(new java.io.PrintStream(out));
                        Library.main(new String[0]);
                        return out.toString();
                    }
                    @Test void printsTheSize() { assertEquals("Books: 1\\n", run()); }
                }
                """);
        String ungradedPlan = """
                {"tests":[{"name":"printsTheSize","purpose":"PRESERVATION","seamWeightTier":0,"visibility":"ALWAYS"}]}
                """;

        assertThat(OutputOnlyMainProbe.targets(smokeTest, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), graded)).as("a smoke test only calls main").isEmpty();
        assertThat(OutputOnlyMainProbe.targets(noCapture, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), graded)).as("nothing captures the output").isEmpty();
        assertThat(OutputOnlyMainProbe.targets(throughHelper, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), graded)).as("compared through a helper").hasSize(1);
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), OutputOnlyMainProbe.gradedTestMethods(ungradedPlan)))
                .as("the transcript check carries no weight").isEmpty();
        assertThat(OutputOnlyMainProbe.targets(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), OutputOnlyMainProbe.gradedTestMethods(null)))
                .as("without a plan every test counts as graded").hasSize(1);
    }

    @Test
    void aProbeNoLongerAppliesOnceNoGradedTestComparesTheTranscript() {
        SemanticMutant probe = SemanticMutant.printedTranscript(PATH, SOLUTION, OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION, "Books: 1\n").orElseThrow(), "Library");
        String ungradedPlan = """
                {"tests":[{"name":"printsTheSize","purpose":"PRESERVATION","seamWeightTier":0,"visibility":"ALWAYS"},
                 {"name":"countsAddedBooks","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"}]}
                """;
        Map<String, String> behaviorGraded = Map.of("test/example/LibraryTest.java", """
                class LibraryTest {
                    @Test void countsAddedBooks() { Library library = new Library(); library.add("Dune"); assertEquals(1, library.size()); }
                }
                """);

        assertThat(OutputOnlyMainProbe.stillApplies(probe, TESTS, PLAN)).isTrue();
        assertThat(OutputOnlyMainProbe.stillApplies(probe, TESTS, ungradedPlan)).as("the transcript check was made zero-weight").isFalse();
        assertThat(OutputOnlyMainProbe.stillApplies(probe, behaviorGraded, PLAN)).as("the transcript check was removed").isFalse();
    }

    @Test
    void aTranscriptComparedThroughAChainOfHelpersMakesMainAProbeTarget() {
        Map<String, String> helperChain = Map.of("test/example/LibraryTest.java", """
                class LibraryTest {
                    private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    private void runMain() { Library.main(new String[0]); }
                    private String capture() { System.setOut(new java.io.PrintStream(out)); runMain(); return out.toString(); }
                    @Test void printsTheSize() { assertEquals("Books: 1\\n", capture()); }
                }
                """);

        assertThat(OutputOnlyMainProbe.targets(helperChain, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), OutputOnlyMainProbe.gradedTestMethods(PLAN))).hasSize(1);
    }

    @Test
    void onlyAFailingWeightedTestKillsThePrintOnlyVariant() {
        String planWithPreservation = """
                {"tests":[{"name":"printsTheSize","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                 {"name":"keepsTheSuppliedSetup","purpose":"PRESERVATION","seamWeightTier":0,"visibility":"ALWAYS"}]}
                """;
        // Replacing all of main also drops supplied setup, which only the zero-weight check observes; the weighted transcript test still passes.
        SemanticMutantExecution.ProbeRunner preservationFails = (mutant, probe) -> {
            boolean prints = mutant.mutantSource().contains("Books: 1");
            return new BuildSummary(2, prints ? 1 : 2, prints ? 1 : 2, false, List.of("LibraryTest.printsTheSize()", "LibraryTest.keepsTheSuppliedSetup()"),
                    prints ? List.of("LibraryTest.keepsTheSuppliedSetup()") : List.of("LibraryTest.printsTheSize()", "LibraryTest.keepsTheSuppliedSetup()"), List.of(), "");
        };

        assertThat(OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), planWithPreservation, Set.of(), target -> Optional.of("Books: 1\n"),
                preservationFails, _ -> {
                })).extracting(SemanticMutantOutcome::disposition).containsExactly(Disposition.SURVIVED_GRADED_SUITE);
        SemanticMutant probe = SemanticMutant.printedTranscript(PATH, SOLUTION, OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION, "Books: 1\n").orElseThrow(), "Library");
        assertThat(OutputOnlyMainProbe.recheck(TESTS, Map.of(PATH, SOLUTION), planWithPreservation, List.of(probe), preservationFails))
                .extracting(SemanticMutantOutcome::disposition).as("a later recheck is judged the same way").containsExactly(Disposition.SURVIVED_GRADED_SUITE);
    }

    @Test
    void aTranscriptCheckTheSourceCheckNoLongerSeesIsResolvedOnlyWhenAMainThatPrintsNothingPassesEveryWeightedTest() {
        SemanticMutant probe = SemanticMutant.printedTranscript(PATH, SOLUTION, OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION, "Books: 1\n").orElseThrow(), "Library");
        // Still weighted, but main is passed as a method reference, a call shape the source check does not follow.
        Map<String, String> unrecognizedShape = Map.of("test/example/LibraryTest.java", """
                class LibraryTest {
                    private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    private String run(java.util.function.Consumer<String[]> program) {
                        System.setOut(new java.io.PrintStream(out));
                        program.accept(new String[0]);
                        return out.toString();
                    }
                    @Test void printsTheSize() { assertEquals("Books: 1\\n", run(Library::main)); }
                }
                """);
        assertThat(OutputOnlyMainProbe.stillApplies(probe, unrecognizedShape, PLAN)).isFalse();
        List<String> built = new ArrayList<>();
        SemanticMutantExecution.ProbeRunner transcriptGraded = (mutant, ignored) -> {
            boolean prints = mutant.mutantSource().contains("System.out.print(");
            built.add(prints ? "print-only" : "silent");
            return prints ? new BuildSummary(1, 0, 0, false, List.of("printsTheSize()"), List.of(), List.of(), "")
                    : new BuildSummary(1, 1, 1, false, List.of("printsTheSize()"), List.of("printsTheSize()"), List.of(), "");
        };

        assertThat(OutputOnlyMainProbe.recheck(unrecognizedShape, Map.of(PATH, SOLUTION), PLAN, List.of(probe), transcriptGraded)).extracting(SemanticMutantOutcome::disposition)
                .as("the weighted test still reads what main prints").containsExactly(Disposition.SURVIVED_GRADED_SUITE);
        assertThat(built).containsExactly("silent", "print-only");

        String transcriptUngraded = """
                {"tests":[{"name":"printsTheSize","purpose":"PRESERVATION","seamWeightTier":0,"visibility":"ALWAYS"},
                 {"name":"countsAddedBooks","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"}]}
                """;
        built.clear();
        assertThat(OutputOnlyMainProbe.recheck(TESTS, Map.of(PATH, SOLUTION), transcriptUngraded, List.of(probe), transcriptGraded)).extracting(SemanticMutantOutcome::disposition)
                .as("only the zero-weight transcript check reads main").containsExactly(Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(built).containsExactly("silent");
    }

    @Test
    void aProbeProvenAgainstAnEarlierClassIsResolvedOnlyWhenNoWeightedTestReadsWhatMainPrints() {
        String earlier = SOLUTION.replace("library.add(\"Dune\");", "library.add(\"Emma\");");
        SemanticMutant stale = SemanticMutant.printedTranscript(PATH, earlier, OutputOnlyMainProbe.transcriptOnlyVariant(earlier, "Books: 1\n").orElseThrow(), "Library");
        List<String> built = new ArrayList<>();
        // Without main's output the transcript test fails; the plan decides whether that failure costs credit.
        SemanticMutantExecution.ProbeRunner silentFailsTranscript = (mutant, ignored) -> {
            built.add(mutant.mutantSource());
            return new BuildSummary(1, 1, 1, false, List.of("printsTheSize()"), List.of("printsTheSize()"), List.of(), "");
        };
        String transcriptUngraded = """
                {"tests":[{"name":"printsTheSize","purpose":"PRESERVATION","seamWeightTier":0,"visibility":"ALWAYS"},
                 {"name":"countsAddedBooks","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"}]}
                """;

        assertThat(OutputOnlyMainProbe.resolveStale(Map.of(PATH, SOLUTION), transcriptUngraded, List.of(stale), silentFailsTranscript))
                .extracting(SemanticMutantOutcome::disposition).containsExactly(Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(built).singleElement().asString().contains("public int size()").doesNotContain("System.out.print(");

        built.clear();
        assertThat(OutputOnlyMainProbe.resolveStale(Map.of(PATH, SOLUTION), PLAN, List.of(stale), silentFailsTranscript)).as("the transcript is still weighted")
                .extracting(SemanticMutantOutcome::disposition).containsExactly(Disposition.INCONCLUSIVE);
        assertThat(built).hasSize(1);
        built.clear();
        assertThat(OutputOnlyMainProbe.resolveStale(Map.of(), transcriptUngraded, List.of(stale), silentFailsTranscript)).as("the class was deleted")
                .extracting(SemanticMutantOutcome::disposition).containsExactly(Disposition.INCONCLUSIVE);
        assertThat(built).isEmpty();
    }

    /** The source check also matches a test that calls main and asserts something unrelated; execution, not that check, decides whether a weighted test reads the output. */
    @Test
    void aStaleProbeIsResolvedByExecutionEvenWhenAnUnrelatedAssertionLooksLikeATranscriptCheck() {
        String earlier = SOLUTION.replace("library.add(\"Dune\");", "library.add(\"Emma\");");
        SemanticMutant stale = SemanticMutant.printedTranscript(PATH, earlier, OutputOnlyMainProbe.transcriptOnlyVariant(earlier, "Books: 1\n").orElseThrow(), "Library");
        Map<String, String> unrelatedAssertion = Map.of("test/example/LibraryTest.java", """
                class LibraryTest {
                    private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    @BeforeEach void capture() { System.setOut(new java.io.PrintStream(out)); }
                    @Test void printsTheSize() { Library.main(new String[0]); assertEquals(1, new Library().size() + 1); }
                }
                """);
        assertThat(OutputOnlyMainProbe.stillApplies(stale, unrelatedAssertion, PLAN)).isTrue();
        SemanticMutantExecution.ProbeRunner passing = (mutant, ignored) -> new BuildSummary(1, 0, 0, false, List.of("printsTheSize()"), List.of(), List.of(), "");

        assertThat(OutputOnlyMainProbe.resolveStale(Map.of(PATH, SOLUTION), PLAN, List.of(stale), passing)).extracting(SemanticMutantOutcome::disposition)
                .containsExactly(Disposition.KILLED_BY_GRADED_SUITE);
    }

    /** Only main's body is replaced, so code that static initialization runs could still print the transcript; a passing silent run then proves nothing. */
    @Test
    void aClassWhoseStaticInitializationRunsCodeGivesNoSilentVerdict() {
        String printingOnLoad = SOLUTION.replace("public class Library {", "public class Library {\n    static { System.out.println(\"Books: 1\"); }");
        String callingOnLoad = SOLUTION.replace("public class Library {", "public class Library {\n    private static final String BANNER = Banner.render();");
        List<String> built = new ArrayList<>();
        SemanticMutantExecution.ProbeRunner passing = (mutant, ignored) -> {
            built.add(mutant.mutantSource());
            return new BuildSummary(1, 0, 0, false, List.of("printsTheSize()"), List.of(), List.of(), "");
        };
        String transcriptUngraded = """
                {"tests":[{"name":"printsTheSize","purpose":"PRESERVATION","seamWeightTier":0,"visibility":"ALWAYS"}]}
                """;

        for (String source : List.of(printingOnLoad, callingOnLoad)) {
            SemanticMutant probe = SemanticMutant.printedTranscript(PATH, source, OutputOnlyMainProbe.transcriptOnlyVariant(source, "Books: 1\n").orElseThrow(), "Library");
            SemanticMutant stale = SemanticMutant.printedTranscript(PATH, SOLUTION, probe.mutantSource(), "Library");
            assertThat(OutputOnlyMainProbe.resolveStale(Map.of(PATH, source), transcriptUngraded, List.of(stale), passing)).extracting(SemanticMutantOutcome::disposition)
                    .containsExactly(Disposition.INCONCLUSIVE);
            assertThat(OutputOnlyMainProbe.recheck(TESTS, Map.of(PATH, source), transcriptUngraded, List.of(probe), passing)).extracting(SemanticMutantOutcome::disposition)
                    .containsExactly(Disposition.INCONCLUSIVE);
        }
        assertThat(built).isEmpty();
    }

    @Test
    void theVariantCompilesKeepsEveryOtherMemberAndPrintsExactlyTheTranscript(@TempDir Path directory) throws Exception {
        String transcript = "Books: 1 { \"quoted\" }\n\ttab \\ backslash \u001b[0m é世 😀\r\n" + "x".repeat(1_500) + "\n";

        String variant = OutputOnlyMainProbe.transcriptOnlyVariant(SOLUTION, transcript).orElseThrow();

        assertThat(variant).contains("public void add(String title) {\n        titles.add(title);", "public int size() {").doesNotContain("library.add(\"Dune\")");
        compile(directory, variant);
        // A separate JVM, like the sandbox capture: replacing the shared standard output would race with other tests.
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Dstdout.encoding=UTF-8", "-cp", directory.toString(),
                "example.Library").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        byte[] printed = process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).isZero();
        assertThat(new String(printed, StandardCharsets.UTF_8)).isEqualTo(transcript);
    }

    @Test
    void decodesOnlyASuccessfulBoundedCapture() {
        String encoded = Base64.getMimeEncoder().encodeToString("Books: 1\n".getBytes(StandardCharsets.UTF_8));

        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(0, encoded + "\n", "", false))).contains("Books: 1\n");
        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(92, encoded, "", false))).isEmpty();
        byte[] oversized = new byte[OutputOnlyMainProbe.MAX_TRANSCRIPT_BYTES + 1];
        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(0, Base64.getEncoder().encodeToString(oversized), "", false))).isEmpty();
        // The sandbox keeps the tail of a long output behind a marker; the MIME decoder would skip the marker and decode the rest into garbage.
        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(0, "[... output truncated, showing last 50000 characters ...]\n" + encoded, "", false))).isEmpty();
    }

    @Test
    void theLargestCapturableTranscriptStillFitsTheSandboxOutputLimit() {
        byte[] largest = new byte[OutputOnlyMainProbe.MAX_TRANSCRIPT_BYTES];
        assertThat(Base64.getMimeEncoder(76, new byte[] { '\n' }).encodeToString(largest).length() + 1).isLessThan(50_000);
    }

    /** Runs the generated capture command against a real solution tree, so a broken pipeline, quoting, or source selection fails here rather than silently in the sandbox. */
    @Test
    void theCaptureCommandCompilesOnlyTheStudentSourcesAndPrintsTheExactTranscript(@TempDir Path solution) throws Exception {
        for (String tool : List.of("sh", "javac", "java", "timeout", "base64", "head", "mktemp")) {
            Assumptions.assumeTrue(onPath(tool), tool + " is not installed");
        }
        Files.createDirectories(solution.resolve("src/example"));
        FileUtils.writeStringToFile(solution.resolve(PATH).toFile(), SOLUTION.replace("\"Books: \"", "\"Bücher: \""), StandardCharsets.UTF_8);
        // A student test needs JUnit, which the probe does not resolve; compiling it would make the whole capture fail.
        Files.createDirectories(solution.resolve("test/example"));
        FileUtils.writeStringToFile(solution.resolve("test/example/LibraryTest.java").toFile(),
                "package example; import org.junit.jupiter.api.Test; class LibraryTest { @Test void t() {} }", StandardCharsets.UTF_8);
        String command = OutputOnlyMainProbe.captureCommand(new OutputOnlyMainProbe.Target(PATH, "Library", "example.Library")).replace("'/workspace/solution'",
                "'" + solution + "'");

        Process process = new ProcessBuilder("sh", "-c", command).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();

        assertThat(exit).as(stderr).isZero();
        assertThat(OutputOnlyMainProbe.transcript(new SandboxExecResultDTO(exit, stdout, stderr, false))).contains("Bücher: 1 { \"quoted\" }\n");
    }

    private static boolean onPath(String tool) {
        return Arrays.stream(System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)).anyMatch(directory -> Files.isExecutable(Path.of(directory, tool)));
    }

    @Test
    void reflectiveMainDispatchUsesExecutedGradingScopeRatherThanTheSourceCallShape() {
        Map<String, String> reflective = Map.of("test/LibraryTest.java",
                "class LibraryTest { @Test void printsTheSize() throws Exception { Class.forName(\"example.Library\").getMethod(\"main\", String[].class).invoke(null, (Object)new String[0]); } }");
        List<SemanticMutant> executed = new ArrayList<>();
        var outcomes = OutputOnlyMainProbe.evaluate(reflective, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), PLAN, Set.of(), target -> Optional.of("Books: 1\n"),
                (mutant, overlay) -> {
                    executed.add(mutant);
                    boolean silent = !mutant.mutantSource().contains("Books: 1");
                    return new BuildSummary(1, silent ? 1 : 0, silent ? 1 : 0, false, List.of("printsTheSize"), silent ? List.of("printsTheSize") : List.of(), List.of(), "");
                }, _ -> {
                });
        assertThat(executed).hasSize(2);
        assertThat(outcomes).singleElement().satisfies(outcome -> assertThat(outcome.disposition()).isEqualTo(Disposition.SURVIVED_GRADED_SUITE));
    }

    @Test
    void anUnobservedDemoMainIsNotFlaggedWhenItsEmptyBodyPassesWeightedGrading() {
        Map<String, String> tests = Map.of("test/LibraryTest.java",
                TESTS.values().iterator().next().replace("Library.main(new String[0]);", "if (false) Library.main(new String[0]);"));
        var outcomes = OutputOnlyMainProbe.evaluate(tests, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), PLAN, Set.of(), target -> Optional.of("Books: 1\n"),
                (mutant, overlay) -> new BuildSummary(1, 0, 0, false, List.of("printsTheSize"), List.of(), List.of(), ""), _ -> {
                });
        assertThat(outcomes).isEmpty();
    }

    @Test
    void aPassingSuiteLetsThePrintOnlyVariantSurviveAndAFailingTestKillsIt() {
        List<SemanticMutant> built = new ArrayList<>();
        SemanticMutantExecution.ProbeRunner passing = (mutant, probe) -> {
            built.add(mutant);
            boolean silent = !mutant.mutantSource().contains("Books: 1");
            return new BuildSummary(1, silent ? 1 : 0, silent ? 1 : 0, false, List.of("printsTheSize"), silent ? List.of("printsTheSize") : List.of(), List.of(), "");
        };

        List<SemanticMutantOutcome> survived = OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), PLAN, Set.of(),
                target -> Optional.of("Books: 1\n"), passing, _ -> {
                });

        assertThat(survived).singleElement().satisfies(outcome -> {
            assertThat(outcome.disposition()).isEqualTo(Disposition.SURVIVED_GRADED_SUITE);
            assertThat(outcome.mutant().printsTranscriptOnly()).isTrue();
            assertThat(outcome.mutant().ruleId()).isEqualTo("Library.main");
            assertThat(outcome.mutant().originalSolutionSource()).isEqualTo(SOLUTION);
        });
        assertThat(built).hasSize(2).last().satisfies(mutant -> assertThat(mutant.mutantSource()).contains("System.out.print(\"Books: 1\\n\");"));

        SemanticMutantExecution.ProbeRunner failing = (mutant, probe) -> new BuildSummary(1, 1, 1, false, List.of("printsTheSize"), List.of("printsTheSize"), List.of(), "");
        assertThat(OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), PLAN, Set.of(), target -> Optional.of("Books: 1\n"), failing, _ -> {
        })).extracting(SemanticMutantOutcome::disposition).containsExactly(Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), PLAN, Set.of(PATH), target -> Optional.of("Books: 1\n"), failing, _ -> {
        })).as("a pending probe is rechecked elsewhere").isEmpty();
        assertThat(OutputOnlyMainProbe.evaluate(TESTS, Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), PLAN, Set.of(), target -> Optional.empty(), failing, _ -> {
        })).as("a reference run that is not deterministic and bounded proves nothing").isEmpty();
    }

    @Test
    void theVerifierCapturesTheReferenceMainAndRunsTheGradedSuiteAgainstThePrintOnlyVariant() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        String encoded = Base64.getMimeEncoder().encodeToString("Books: 1\n".getBytes(StandardCharsets.UTF_8));
        when(sandbox.exec(anyString(), any(), any(String[].class))).thenAnswer(invocation -> {
            boolean capture = Arrays.stream(invocation.getArguments()).anyMatch(argument -> String.valueOf(argument).contains("hyperion-main"));
            return new SandboxExecResultDTO(0, capture ? encoded : "", "", false);
        });
        AtomicReference<String> activeSource = new AtomicReference<>(SOLUTION);
        when(sandbox.copyOut(anyString(), anyString())).thenAnswer(invocation -> ReportTarFixtures.junitAndScaReports("solution", List.of("printsTheSize"),
                activeSource.get().contains("Books: 1") ? List.of() : List.of("printsTheSize"), Map.of()));
        List<Map<String, String>> installed = new ArrayList<>();
        doAnswer(invocation -> {
            try (var archive = new TarArchiveInputStream(invocation.getArgument(2, InputStream.class))) {
                Map<String, String> files = WorkspaceArchive.readTar(archive, "");
                installed.add(files);
                if (files.containsKey("solution/" + PATH)) {
                    activeSource.set(files.get("solution/" + PATH));
                }
            }
            return null;
        }).when(sandbox).copyIn(anyString(), anyString(), any());
        SandboxBuildCommands commands = mock(SandboxBuildCommands.class);
        when(commands.verifyScriptContent(any())).thenReturn("#!/bin/sh\n");
        when(commands.behavioralSolutionBuildCommand()).thenReturn("sh /opt/aiworker/verify.sh solution behavior-isolated");
        GenerationInput exercise = new GenerationInput(42, "example", "", false, Set.of(), Set.of(), GenerationBuildTool.GRADLE);

        List<SemanticMutantOutcome> outcomes = new DifferentialVerifier(commands).probeOutputOnlyMains(sandbox, "session", exercise, TESTS, Map.of(PATH, TEMPLATE),
                Map.of(PATH, SOLUTION), PLAN, SeededStructuralTests.EMPTY, Set.of(), () -> {
                }, _ -> {
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
        FileUtils.writeStringToFile(file.toFile(), source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int status = compiler.run(null, null, null, "-encoding", "UTF-8", "-d", directory.toString(), file.toString());
        assertThat(status).as("the variant compiles").isZero();
    }
}
