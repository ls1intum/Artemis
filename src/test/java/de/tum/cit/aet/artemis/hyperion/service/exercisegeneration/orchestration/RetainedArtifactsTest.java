package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationArtifactCompleteness;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedFileDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Pure unit tests for {@link RetainedArtifacts}: the bounding, ordering, and screening rules applied to a generated candidate before it is retained. */
class RetainedArtifactsTest {

    @Test
    void of_smallCandidateAcrossAllThreeRepositories_yieldsCompleteWithEveryFileCorrectlyLabeled() {
        Map<RepositoryType, Map<String, String>> producedFiles = Map.of(RepositoryType.TEMPLATE, Map.of("src/Main.java", "public class Main {}"), RepositoryType.SOLUTION,
                Map.of("src/Solution.java", "public class Solution {}"), RepositoryType.TESTS, Map.of("src/test/MainTest.java", "class MainTest {}"));

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-1", producedFiles, "Problem statement", "# Spec");

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.completeness()).isEqualTo(ExerciseGenerationArtifactCompleteness.COMPLETE);
        assertThat(result.problemStatement()).isEqualTo("Problem statement");
        assertThat(result.specDocument()).isEqualTo("# Spec");
        assertThat(result.files()).hasSize(3)
                .extracting(ExerciseGenerationRetainedFileDTO::repo, ExerciseGenerationRetainedFileDTO::path, ExerciseGenerationRetainedFileDTO::content)
                .containsExactlyInAnyOrder(tuple("template", "src/Main.java", "public class Main {}"), tuple("solution", "src/Solution.java", "public class Solution {}"),
                        tuple("tests", "src/test/MainTest.java", "class MainTest {}"));
    }

    @Test
    void of_ordersFilesByRepositoryThenByPath_regardlessOfInputOrder() {
        Map<RepositoryType, Map<String, String>> producedFiles = new LinkedHashMap<>();
        // Fed in SOLUTION, TESTS, TEMPLATE order with each repository's entries reversed, so a passing output order can only come from the fixed repository order and a path sort.
        LinkedHashMap<String, String> solutionFiles = new LinkedHashMap<>();
        solutionFiles.put("z.txt", "zzz");
        solutionFiles.put("a.txt", "aaa");
        producedFiles.put(RepositoryType.SOLUTION, solutionFiles);
        LinkedHashMap<String, String> testsFiles = new LinkedHashMap<>();
        testsFiles.put("b.txt", "bbb");
        producedFiles.put(RepositoryType.TESTS, testsFiles);
        LinkedHashMap<String, String> templateFiles = new LinkedHashMap<>();
        templateFiles.put("y.txt", "yyy");
        templateFiles.put("x.txt", "xxx");
        producedFiles.put(RepositoryType.TEMPLATE, templateFiles);

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-order", producedFiles, null, null);

        assertThat(result.files()).extracting(ExerciseGenerationRetainedFileDTO::repo, ExerciseGenerationRetainedFileDTO::path).containsExactly(tuple("template", "x.txt"),
                tuple("template", "y.txt"), tuple("solution", "a.txt"), tuple("solution", "z.txt"), tuple("tests", "b.txt"));
    }

    @Test
    void of_onlyRetainsTemplateSolutionAndTests_excludingOtherRepositories() {
        Map<RepositoryType, Map<String, String>> producedFiles = Map.of(RepositoryType.TEMPLATE, Map.of("Main.java", "class Main {}"), RepositoryType.AUXILIARY,
                Map.of("Aux.java", "class Aux {}"));

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-aux", producedFiles, null, null);

        assertThat(result.files()).extracting(ExerciseGenerationRetainedFileDTO::repo).containsExactly("template");
    }

    @Test
    void of_exceedingMaxFiles_dropsTheTailAndDowngradesToPartial() {
        Map<String, String> templateFiles = new LinkedHashMap<>();
        for (int i = 0; i <= RetainedArtifacts.MAX_FILES; i++) {
            templateFiles.put(String.format("file%03d.txt", i), "x");
        }
        Map<RepositoryType, Map<String, String>> producedFiles = Map.of(RepositoryType.TEMPLATE, templateFiles);

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-overflow", producedFiles, null, null);

        assertThat(result.files()).hasSize(RetainedArtifacts.MAX_FILES);
        assertThat(result.files().getFirst().path()).isEqualTo("file000.txt");
        assertThat(result.files().getLast().path()).isEqualTo(String.format("file%03d.txt", RetainedArtifacts.MAX_FILES - 1));
        assertThat(result.completeness()).isEqualTo(ExerciseGenerationArtifactCompleteness.PARTIAL);
    }

    @Test
    void of_fileLongerThanMaxFileChars_isTruncatedToExactlyMaxFileCharsAndDowngradesToPartial() {
        String oversized = "a".repeat(RetainedArtifacts.MAX_FILE_CHARS + 100);
        Map<RepositoryType, Map<String, String>> producedFiles = Map.of(RepositoryType.TEMPLATE, Map.of("Big.txt", oversized));

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-big-file", producedFiles, null, null);

        assertThat(result.files()).singleElement().satisfies(file -> {
            assertThat(file.content()).hasSize(RetainedArtifacts.MAX_FILE_CHARS);
            assertThat(file.content()).isEqualTo("a".repeat(RetainedArtifacts.MAX_FILE_CHARS));
        });
        assertThat(result.completeness()).isEqualTo(ExerciseGenerationArtifactCompleteness.PARTIAL);
    }

    @Test
    void of_exceedingMaxTotalChars_stopsRetentionAndDowngradesToPartial() {
        int fileSize = 100_000;
        int fileCount = RetainedArtifacts.MAX_TOTAL_CHARS / fileSize + 1;
        Map<String, String> templateFiles = new LinkedHashMap<>();
        for (int i = 0; i < fileCount; i++) {
            templateFiles.put(String.format("file%03d.txt", i), "a".repeat(fileSize));
        }
        Map<RepositoryType, Map<String, String>> producedFiles = Map.of(RepositoryType.TEMPLATE, templateFiles);

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-total-cap", producedFiles, null, null);

        assertThat(result.files()).hasSize(fileCount - 1);
        assertThat(result.completeness()).isEqualTo(ExerciseGenerationArtifactCompleteness.PARTIAL);
    }

    @Test
    void of_problemStatementLongerThanCap_isTruncatedAndDowngradesToPartial() {
        String oversized = "p".repeat(RetainedArtifacts.MAX_PROBLEM_STATEMENT_CHARS + 50);

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-statement", Map.of(), oversized, null);

        assertThat(result.problemStatement()).hasSize(RetainedArtifacts.MAX_PROBLEM_STATEMENT_CHARS);
        assertThat(result.completeness()).isEqualTo(ExerciseGenerationArtifactCompleteness.PARTIAL);
    }

    @Test
    void of_problemStatementAtExactlyTheCap_staysComplete() {
        String exact = "p".repeat(RetainedArtifacts.MAX_PROBLEM_STATEMENT_CHARS);

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-statement-exact", Map.of(), exact, null);

        assertThat(result.problemStatement()).isEqualTo(exact);
        assertThat(result.completeness()).isEqualTo(ExerciseGenerationArtifactCompleteness.COMPLETE);
    }

    @Test
    void of_fileTrippingTheSecretMaterialPolicy_isWithheldWhileOtherFilesSurvive() {
        // The filename alone trips HyperionSecretMaterialPolicy's private-key-container classification, independent of content.
        Map<String, String> templateFiles = new LinkedHashMap<>();
        templateFiles.put("id_rsa", "not a real key, but the filename alone is enough to trip the policy");
        templateFiles.put("Main.java", "public class Main {}");
        Map<RepositoryType, Map<String, String>> producedFiles = Map.of(RepositoryType.TEMPLATE, templateFiles);

        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-secret", producedFiles, null, null);

        assertThat(result.files()).extracting(ExerciseGenerationRetainedFileDTO::path).containsExactly("Main.java");
        assertThat(result.completeness()).isEqualTo(ExerciseGenerationArtifactCompleteness.PARTIAL);
    }

    @Test
    void of_entirelyEmptyCandidate_yieldsAnEmptySnapshot() {
        ExerciseGenerationRetainedArtifactsDTO result = RetainedArtifacts.of("job-empty", Map.of(), null, null);

        assertThat(result.isEmpty()).isTrue();
    }
}
