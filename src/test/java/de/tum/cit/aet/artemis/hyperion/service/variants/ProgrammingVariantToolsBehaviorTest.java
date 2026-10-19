package de.tum.cit.aet.artemis.hyperion.service.variants;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingVariantToolsBehaviorTest {

    private final GitService git = mock(GitService.class);

    private final RepositoryService repositories = mock(RepositoryService.class);

    private final ExerciseVariantJobService jobs = mock(ExerciseVariantJobService.class);

    private final ProgrammingExerciseTestRepository exercises = mock(ProgrammingExerciseTestRepository.class);

    private final ProgrammingExerciseTestCaseTestRepository testCases = mock(ProgrammingExerciseTestCaseTestRepository.class);

    private final ProgrammingExerciseTaskService tasks = mock(ProgrammingExerciseTaskService.class);

    private final Repository checkout = mock(Repository.class);

    private final Map<String, String> files = new HashMap<>();

    private ProgrammingVariantTools tools;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() throws Exception {
        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getRepositoryURI(any())).thenReturn(mock(LocalVCRepositoryUri.class));
        when(git.getOrCheckoutRepository(any(), anyBoolean(), anyString(), anyBoolean())).thenReturn(checkout);
        when(repositories.getFiles(checkout)).thenAnswer(_ -> {
            Map<String, FileType> paths = new HashMap<>();
            files.keySet().forEach(path -> paths.put(path, FileType.FILE));
            paths.put("src", FileType.FOLDER);
            return paths;
        });
        when(repositories.getFile(any(), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            if (!files.containsKey(path)) {
                throw new IOException("missing");
            }
            return files.get(path).getBytes(UTF_8);
        });
        when(git.getFileByName(any(), anyString())).thenAnswer(
                invocation -> files.containsKey(invocation.getArgument(1)) ? Optional.of(mock(de.tum.cit.aet.artemis.programming.domain.File.class)) : Optional.empty());
        doAnswer(invocation -> {
            files.remove(invocation.getArgument(1));
            return null;
        }).when(repositories).deleteFile(any(), anyString());
        doAnswer(invocation -> {
            files.put(invocation.getArgument(1), new String(((InputStream) invocation.getArgument(2)).readAllBytes(), UTF_8));
            return null;
        }).when(repositories).createFile(any(), anyString(), any());
        tools = new ProgrammingVariantTools(exercise, null, "job", jobs, git, repositories, exercises, tasks, "main", null, testCases, (ignored, jobId) -> {
        });
    }

    @Test
    void searchesSortedFilesWithLineNumbersAndTruncatesLongMatchingLines() {
        files.put("B.java", "no match\nmatch " + "x".repeat(400));
        files.put("A.java", " match first \nnot found");
        String result = tools.searchFiles(" solution ", "match");
        assertThat(result).isEqualTo("A.java:1: match first\nB.java:1: no match\nB.java:2: match " + "x".repeat(294) + " [truncated]\n");
        assertThat(tools.listFiles("SOLUTION")).isEqualTo("A.java\nB.java");
        assertThat(tools.searchFiles("TEST", "absent")).isEqualTo("No matches for the search text in the tests repository.");
    }

    @Test
    void searchBoundsMatchesAndReportsInvalidInputAndCheckoutErrors() throws Exception {
        files.put("Many.txt", "match\n".repeat(51));
        String result = tools.searchFiles("TESTS", "match");
        assertThat(result).contains("Many.txt:50: match\n[more matches omitted").doesNotContain("Many.txt:51:");
        assertThat(tools.searchFiles(null, "match")).contains("unknown repository 'null'");
        assertThat(tools.searchFiles("unknown", "match")).contains("Valid values are TEMPLATE, SOLUTION, and TESTS");
        assertThat(tools.searchFiles("SOLUTION", null)).isEqualTo("Error: the search text must not be empty.");
        assertThat(tools.searchFiles("SOLUTION", " ")).isEqualTo("Error: the search text must not be empty.");
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(null);
        assertThat(tools.searchFiles("TEMPLATE", "match")).contains("No exercise repository URI");
        assertThat(tools.listFiles("TEMPLATE")).contains("could not list files");
    }

    @Test
    void statementUpdatesRepairMalformedTestIdsAndReportUnresolvedNames() {
        ProgrammingExercise persisted = new ProgrammingExercise();
        persisted.setId(42L);
        when(exercises.findByIdElseThrow(Long.valueOf(42))).thenReturn(persisted);
        when(tasks.findUnresolvedTaskTestReferences(persisted)).thenReturn(List.of("typo"));
        ProgrammingExerciseTestCase a = new ProgrammingExerciseTestCase();
        a.setId(1L);
        a.setTestName("aTest");
        ProgrammingExerciseTestCase z = new ProgrammingExerciseTestCase();
        z.setId(2L);
        z.setTestName("zTest");
        when(testCases.findByExerciseId(42L)).thenReturn(Set.of(z, a));
        String result = tools.updateProblemStatement("[task][Implement](<testid>typo</testid>)");
        assertThat(persisted.getProblemStatement()).isEqualTo("[task][Implement](typo)");
        assertThat(result).contains("They were rewritten to plain test names", "WARNING: these task-marker references match no test", "[aTest, zTest]");
        when(tasks.findUnresolvedTaskTestReferences(persisted)).thenReturn(List.of());
        assertThat(tools.updateProblemStatement("[task][Implement](aTest)")).isEqualTo("Problem statement updated.");
        when(tasks.findUnresolvedTaskTestReferences(persisted)).thenThrow(new IllegalStateException("cannot check"));
        assertThat(tools.updateProblemStatement("A statement")).isEqualTo("Problem statement updated.");
        assertThat(persisted.getProblemStatement()).isEqualTo("A statement");
    }

    @Test
    void statementValidationAndPersistenceFailuresPreserveHelpfulErrors() {
        assertThat(tools.updateProblemStatement(null)).isEqualTo("Error: the problem statement must not be empty.");
        assertThat(tools.updateProblemStatement(" ")).isEqualTo("Error: the problem statement must not be empty.");
        when(exercises.findByIdElseThrow(Long.valueOf(42))).thenThrow(new IllegalStateException("exercise removed"));
        assertThat(tools.updateProblemStatement("Changed")).isEqualTo("Error: could not update the problem statement: exercise removed");
    }

    @Test
    void nullBatchEntriesAndArgumentsProduceIndependentValidationErrors() {
        files.put("A.java", "original");
        assertThat(tools.applyEdits(
                Arrays.asList(null, new ProgrammingVariantTools.BatchEdit(null, "A.java", "x", "y"), new ProgrammingVariantTools.BatchEdit("SOLUTION", "A.java", null, "x"),
                        new ProgrammingVariantTools.BatchEdit("SOLUTION", "A.java", "", "x"), new ProgrammingVariantTools.BatchEdit("SOLUTION", "A.java", "original", null))))
                .contains("edit entry is missing", "unknown repository 'null'", "search text must not be empty", "replace text must not be null", "0 of 5 edit(s) applied");
        assertThat(files.get("A.java")).isEqualTo("original");
        assertThat(tools.writeFiles(Arrays.asList(null, new ProgrammingVariantTools.FileWrite(null, "x", "x")))).contains("entry is missing", "unknown repository 'null'",
                "0 of 2 file(s) written");
        assertThat(tools.deleteFiles(Arrays.asList(null, new ProgrammingVariantTools.FileDelete(null, "x")))).contains("entry is missing", "unknown repository 'null'",
                "0 of 2 file(s) deleted");
    }

    @Test
    void failedOverwriteRestoresPreviousContentInsteadOfLosingFile() throws Exception {
        files.put("A.java", "original");
        doAnswer(invocation -> {
            String content = new String(((InputStream) invocation.getArgument(2)).readAllBytes(), UTF_8);
            if (content.equals("replacement")) {
                files.put("A.java", "partial write");
                throw new IOException("disk full");
            }
            files.put(invocation.getArgument(1), content);
            return null;
        }).when(repositories).createFile(any(), anyString(), any());
        assertThat(tools.writeFiles(List.of(new ProgrammingVariantTools.FileWrite("SOLUTION", "A.java", "replacement")))).contains("disk full", "0 of 1 file(s) written");
        assertThat(files.get("A.java")).isEqualTo("original");
    }

    @Test
    void flushingPendingChangesMarksTestRepositoryAndSurfacesCommitFailures() throws Exception {
        files.put("A.java", "content");
        tools.listFiles("TESTS");
        when(git.isWorkingCopyClean(checkout)).thenReturn(true);
        tools.flushPendingChanges();
        assertThat(tools.touchedTestRepo()).isFalse();
        when(git.isWorkingCopyClean(checkout)).thenReturn(false);
        tools.flushPendingChanges();
        assertThat(tools.touchedTestRepo()).isTrue();
        doThrow(new GitAPIException("push rejected") {
        }).when(repositories).commitChanges(checkout, null);
        assertThatThrownBy(tools::flushPendingChanges).isInstanceOf(IllegalStateException.class).hasMessageContaining("Could not persist pending tests repository changes")
                .hasRootCauseMessage("push rejected");
    }

    @Test
    void cancellationStopsEveryMutatingAndReadingToolBeforeWorkAndFinishRecordsSummary() {
        when(jobs.isCancelRequested("job")).thenReturn(true);
        assertThat(tools.listFiles("SOLUTION")).contains("CANCELLED");
        assertThat(tools.readFiles(List.of())).contains("CANCELLED");
        assertThat(tools.searchFiles("SOLUTION", "x")).contains("CANCELLED");
        assertThat(tools.applyEdits(List.of())).contains("CANCELLED");
        assertThat(tools.writeFiles(List.of())).contains("CANCELLED");
        assertThat(tools.deleteFiles(List.of())).contains("CANCELLED");
        assertThat(tools.listTestCases()).contains("CANCELLED");
        assertThat(tools.updateProblemStatement("x")).contains("CANCELLED");
        assertThat(tools.diffFile("SOLUTION", "A.java")).contains("CANCELLED");
        assertThat(tools.finish("Stopped")).isEqualTo("Summary recorded. You are done with this round.");
        assertThat(tools.finishSummary()).isEqualTo("Stopped");
        assertThat(tools.toolCallStats()).isEmpty();
    }
}
