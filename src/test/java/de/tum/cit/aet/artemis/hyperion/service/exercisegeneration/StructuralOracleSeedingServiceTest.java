package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Deterministic test for the structural-oracle seeder (no Docker, no LLM): it runs the real
 * {@link de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.OracleGenerator} over fixture solution/template sources and asserts that structural tests are seeded
 * ONLY for a public class the student must create (entirely absent from the template), and never for incidental member differences within shared classes — the failure mode a
 * generated Roman-numerals exercise exposed, where the template stub merely lacked the solution's private helper.
 */
class StructuralOracleSeedingServiceTest {

    @TempDir
    private Path tempDir;

    private static ProgrammingExercise javaExercise() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        return exercise;
    }

    private StructuralOracleSeedingService seederWith(InteractiveSandbox sandbox, Map<String, String> solution, Map<String, String> template, Map<String, String> tests) {
        GenerationWorkspaceService workspace = mock(GenerationWorkspaceService.class);
        when(workspace.extractRepositoryFiles(sandbox, "s", RepositoryType.SOLUTION)).thenReturn(solution);
        when(workspace.extractRepositoryFiles(sandbox, "s", RepositoryType.TEMPLATE)).thenReturn(template);
        when(workspace.extractRepositoryFiles(sandbox, "s", RepositoryType.TESTS)).thenReturn(tests);
        return new StructuralOracleSeedingService(workspace, new TempFileUtilService(tempDir));
    }

    private static Map<String, String> readTar(InputStream in) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), new String(tar.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }

    @Test
    void seedsStructuralTests_whenTemplateOmitsAWholePublicClass() throws Exception {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        // Solution has Sorter (in both) and MergeSort (a public class the student must create); the template omits MergeSort entirely.
        Map<String, String> solution = Map.of("src/sorting/Sorter.java", "package sorting;\npublic interface Sorter { int[] sort(int[] a); }", "src/sorting/MergeSort.java",
                "package sorting;\npublic class MergeSort implements Sorter {\n    public int[] sort(int[] a){ return a; }\n}");
        Map<String, String> template = Map.of("src/sorting/Sorter.java", "package sorting;\npublic interface Sorter { int[] sort(int[] a); }");
        Map<String, String> tests = Map.of("test/sorting/SortTest.java", "package sorting;\nclass SortTest {}");

        java.util.Set<String> seededNames = seederWith(sandbox, solution, template, tests).seedIfStructuralDiff(sandbox, "s", javaExercise());

        ArgumentCaptor<InputStream> tarCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(sandbox).copyIn(eq("s"), eq("/workspace"), tarCaptor.capture());
        Map<String, String> seeded = readTar(tarCaptor.getValue());
        assertThat(seeded).containsKeys("tests/test/sorting/ClassTest.java", "tests/test/sorting/test.json");
        assertThat(seeded.get("tests/test/sorting/ClassTest.java")).contains("package sorting;").doesNotContain("${packageName}");
        // The oracle enforces the created class, not the already-present interface.
        assertThat(seeded.get("tests/test/sorting/test.json")).contains("MergeSort");
        // The AUTHORITATIVE structural test names returned for the verifier: exactly the four Ares dynamic-test names for the ONE created class (MergeSort), and nothing for the
        // already-present Sorter interface. These are the names the verifier exempts from the [task] binding-resolution gate.
        assertThat(seededNames).containsExactlyInAnyOrder("testClass[MergeSort]", "testMethods[MergeSort]", "testAttributes[MergeSort]", "testConstructors[MergeSort]");
    }

    @Test
    void seedsNothing_whenOnlyAPrivateHelperDiffers() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        // The Roman-numerals failure mode: the public class exists in both; only the solution's PRIVATE helper is absent from the template stub. This must NOT seed structural
        // tests.
        Map<String, String> solution = Map.of("src/roman/RomanNumerals.java",
                "package roman;\npublic class RomanNumerals {\n    public int toInteger(String r){ return 0; }\n    private int valueOf(char c){ return 0; }\n}");
        Map<String, String> template = Map.of("src/roman/RomanNumerals.java", "package roman;\npublic class RomanNumerals {\n    public int toInteger(String r){ return 0; }\n}");
        Map<String, String> tests = Map.of("test/roman/RomanNumeralsTest.java", "package roman;\nclass RomanNumeralsTest {}");

        java.util.Set<String> seededNames = seederWith(sandbox, solution, template, tests).seedIfStructuralDiff(sandbox, "s", javaExercise());

        verify(sandbox, never()).copyIn(any(), any(), any());
        assertThat(seededNames).as("a behaviour-only diff seeds nothing, so there are no exempt structural names").isEmpty();
    }

    @Test
    void seedsNothing_butRemovesStaleStructuralFiles_whenStructuresAreIdentical() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        String identical = "package sorting;\npublic class BubbleSort {\n    public int[] sort(int[] a){ return a; }\n}";
        java.util.Set<String> seededNames = seederWith(sandbox, Map.of("src/sorting/BubbleSort.java", identical), Map.of("src/sorting/BubbleSort.java", identical),
                Map.of("test/sorting/BubbleSortTest.java", "package sorting;\nclass BubbleSortTest {}")).seedIfStructuralDiff(sandbox, "s", javaExercise());

        verify(sandbox, never()).copyIn(any(), any(), any());
        assertThat(seededNames).isEmpty();
        // A previous attempt may have seeded a structure oracle + classes that this (now behaviour-only) attempt no longer requires; they MUST be removed so a stale
        // ClassTest/test.json
        // cannot compile against classes the new solution dropped — a silent acceptance regression. Assert the single rm -f over the oracle + structural classes is issued.
        ArgumentCaptor<String> cleanupCommand = ArgumentCaptor.forClass(String.class);
        verify(sandbox).exec(eq("s"), any(), eq("sh"), eq("-c"), cleanupCommand.capture());
        assertThat(cleanupCommand.getValue()).startsWith("rm -f").contains("/workspace/tests/test/sorting/").contains("test.json").contains("ClassTest.java");
    }

    @Test
    void skipsNonJavaLanguages() {
        GenerationWorkspaceService workspace = mock(GenerationWorkspaceService.class);
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        ProgrammingExercise python = new ProgrammingExercise();
        python.setProgrammingLanguage(ProgrammingLanguage.PYTHON);

        java.util.Set<String> seededNames = new StructuralOracleSeedingService(workspace, new TempFileUtilService(tempDir)).seedIfStructuralDiff(sandbox, "s", python);

        verify(sandbox, never()).copyIn(any(), any(), any());
        verify(workspace, never()).extractRepositoryFiles(any(), any(), any());
        assertThat(seededNames).isEmpty();
    }

    @Test
    void filterOracle_keepsOnlyCreatedPublicClasses_andStripsPrivateMembers() throws Exception {
        // MergeSort is a public class missing from the template (keep, but drop its private member); BubbleSort exists in the template (drop the whole entry).
        String oracle = """
                [ { "class": { "name": "MergeSort", "package": "sorting" },
                    "methods": [ { "name": "sort", "modifiers": ["public"] }, { "name": "merge", "modifiers": ["private"] } ] },
                  { "class": { "name": "BubbleSort", "package": "sorting" },
                    "methods": [ { "name": "helper", "modifiers": ["private"] } ] } ]
                """;
        Map<String, String> templateFiles = Map.of("src/sorting/BubbleSort.java", "package sorting;\npublic class BubbleSort {}");
        Map<String, String> solutionFiles = Map.of("src/sorting/BubbleSort.java", "package sorting;\npublic class BubbleSort {}", "src/sorting/MergeSort.java",
                "package sorting;\npublic class MergeSort {}");

        String filtered = StructuralOracleSeedingService.filterOracleToCreatedPublicApi(oracle, templateFiles, solutionFiles);

        assertThat(filtered).contains("MergeSort").contains("sort").doesNotContain("BubbleSort").doesNotContain("merge").doesNotContain("helper");
    }

    @Test
    void filterOracle_dropsAMissingButNonPublicClass() throws Exception {
        // A package-private helper class missing from the template is an implementation detail, not a contract the student must fulfil — do not enforce it.
        String oracle = "[ { \"class\": { \"name\": \"Helper\", \"package\": \"x\" }, \"methods\": [ { \"name\": \"h\", \"modifiers\": [\"public\"] } ] } ]";
        Map<String, String> templateFiles = Map.of("src/x/Main.java", "package x;\npublic class Main {}");
        Map<String, String> solutionFiles = Map.of("src/x/Helper.java", "package x;\nclass Helper { public void h(){} }");

        assertThat(StructuralOracleSeedingService.filterOracleToCreatedPublicApi(oracle, templateFiles, solutionFiles)).isEqualTo("[]");
    }

    @Test
    void isStructurallyEmpty_recognizesEmptyOracles() {
        assertThat(StructuralOracleSeedingService.isStructurallyEmpty("[]")).isTrue();
        assertThat(StructuralOracleSeedingService.isStructurallyEmpty("[ ]")).isTrue();
        assertThat(StructuralOracleSeedingService.isStructurallyEmpty("  ")).isTrue();
        assertThat(StructuralOracleSeedingService.isStructurallyEmpty(null)).isTrue();
        assertThat(StructuralOracleSeedingService.isStructurallyEmpty("[ { \"class\": {} } ]")).isFalse();
    }
}
