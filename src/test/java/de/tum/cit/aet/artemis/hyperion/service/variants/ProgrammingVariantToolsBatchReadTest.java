package de.tum.cit.aet.artemis.hyperion.service.variants;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.variants.ProgrammingVariantTools.FileRead;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Unit tests for the batch {@code readFiles} tool: collapses several one-file-at-a-time readFile round trips
 * (observed in live traces — up to a dozen per round) into a single call, each entry reporting independently.
 */
class ProgrammingVariantToolsBatchReadTest {

    private static final String JOB_ID = "job-1";

    private ProgrammingVariantTools tools;

    /** In-memory view of the SOLUTION repository the mocked RepositoryService reads from. */
    private final Map<String, String> files = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        GitService gitService = mock(GitService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ExerciseVariantJobService jobService = mock(ExerciseVariantJobService.class);
        when(jobService.isCancelRequested(JOB_ID)).thenReturn(false);

        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getRepositoryURI(any())).thenReturn(mock(LocalVCRepositoryUri.class));
        Repository repository = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(any(), anyBoolean(), anyString(), anyBoolean())).thenReturn(repository);

        when(repositoryService.getFile(any(), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            if (!files.containsKey(path)) {
                throw new IOException("no such file: " + path);
            }
            return files.get(path).getBytes(UTF_8);
        });

        tools = new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, gitService, repositoryService, null, null, "main", null, null, (exerciseArgument, jobArgument) -> {
        });
    }

    @Test
    void shouldReadMultipleFilesInOneCall() {
        files.put("A.java", "class Cargo {}");
        files.put("B.java", "new Cargo();");

        String result = tools.readFiles(List.of(new FileRead("SOLUTION", "A.java"), new FileRead("SOLUTION", "B.java")));

        assertThat(result).contains("--- SOLUTION:A.java ---").contains("class Cargo {}").contains("--- SOLUTION:B.java ---").contains("new Cargo();");
    }

    @Test
    void shouldReportPerFileErrorsWithoutBlockingTheOthers() {
        files.put("A.java", "class Cargo {}");

        String result = tools.readFiles(List.of(new FileRead("SOLUTION", "A.java"), new FileRead("SOLUTION", "Missing.java")));

        assertThat(result).contains("--- SOLUTION:A.java ---").contains("class Cargo {}").contains("--- SOLUTION:Missing.java ---")
                .contains("Error: could not read file 'Missing.java'");
    }

    @Test
    void shouldRejectAnUnknownRepository() {
        String result = tools.readFiles(List.of(new FileRead("BOGUS", "A.java")));

        assertThat(result).contains("--- BOGUS:A.java ---").contains("Error: unknown repository");
    }

    @Test
    void shouldRejectAnEmptyReadList() {
        assertThat(tools.readFiles(List.of())).startsWith("Error: no files were provided");
    }

    @Test
    void shouldTruncateAnOversizedFileTheSameWayReadFileDoes() {
        files.put("Big.java", "x".repeat(150_000));

        String result = tools.readFiles(List.of(new FileRead("SOLUTION", "Big.java")));

        assertThat(result).contains("[truncated]");
    }

    @Test
    void shouldStopEarlyOnceTheBatchBudgetIsExceeded() {
        files.put("A.java", "a".repeat(25_000));
        files.put("B.java", "b".repeat(25_000));
        files.put("C.java", "c".repeat(25_000));

        String result = tools.readFiles(List.of(new FileRead("SOLUTION", "A.java"), new FileRead("SOLUTION", "B.java"), new FileRead("SOLUTION", "C.java")));

        assertThat(result).contains("--- SOLUTION:A.java ---").contains("--- SOLUTION:B.java ---").contains("remaining file(s) omitted").doesNotContain("SOLUTION:C.java");
    }

    @Test
    void shouldReadAcrossMultipleRepositoriesInOneCall() throws Exception {
        LocalVCRepositoryUri templateUri = mock(LocalVCRepositoryUri.class);
        LocalVCRepositoryUri solutionUri = mock(LocalVCRepositoryUri.class);
        Repository templateRepo = mock(Repository.class);
        Repository solutionRepo = mock(Repository.class);

        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(templateUri);
        when(exercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(solutionUri);

        GitService gitService = mock(GitService.class);
        when(gitService.getOrCheckoutRepository(org.mockito.ArgumentMatchers.eq(templateUri), anyBoolean(), anyString(), anyBoolean())).thenReturn(templateRepo);
        when(gitService.getOrCheckoutRepository(org.mockito.ArgumentMatchers.eq(solutionUri), anyBoolean(), anyString(), anyBoolean())).thenReturn(solutionRepo);

        Map<Repository, Map<String, String>> filesByRepo = new HashMap<>();
        filesByRepo.put(templateRepo, new HashMap<>(Map.of("A.java", "class Cargo {}")));
        filesByRepo.put(solutionRepo, new HashMap<>(Map.of("A.java", "class Cargo { void ship() {} }")));

        RepositoryService repositoryServiceLocal = mock(RepositoryService.class);
        when(repositoryServiceLocal.getFile(any(), anyString())).thenAnswer(invocation -> {
            Repository repo = invocation.getArgument(0);
            String path = invocation.getArgument(1);
            String content = filesByRepo.get(repo).get(path);
            if (content == null) {
                throw new IOException("no such file: " + path);
            }
            return content.getBytes(UTF_8);
        });

        ExerciseVariantJobService jobService = mock(ExerciseVariantJobService.class);
        when(jobService.isCancelRequested(JOB_ID)).thenReturn(false);

        ProgrammingVariantTools crossRepoTools = new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, gitService, repositoryServiceLocal, null, null, "main", null, null,
                (exerciseArgument, jobArgument) -> {
                });

        String result = crossRepoTools.readFiles(List.of(new FileRead("TEMPLATE", "A.java"), new FileRead("SOLUTION", "A.java")));

        assertThat(result).contains("--- TEMPLATE:A.java ---").contains("class Cargo {}").contains("--- SOLUTION:A.java ---").contains("class Cargo { void ship() {} }");
    }
}
