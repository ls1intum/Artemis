package de.tum.cit.aet.artemis.hyperion.service.variants;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.variants.ProgrammingVariantTools.BatchEdit;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Unit tests for the batch {@code applyEdits} tool (performance lever A1). Edits are applied in order, each sees
 * the previous ones, and a failed edit reports its own precise error without blocking the others.
 */
class ProgrammingVariantToolsBatchEditTest {

    private static final String JOB_ID = "job-1";

    private RepositoryService repositoryService;

    private ProgrammingVariantTools tools;

    /** In-memory view of the SOLUTION repository the mocked RepositoryService reads from and writes to. */
    private final Map<String, String> files = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        GitService gitService = mock(GitService.class);
        repositoryService = mock(RepositoryService.class);
        ExerciseVariantJobService jobService = mock(ExerciseVariantJobService.class);
        when(jobService.isCancelRequested(JOB_ID)).thenReturn(false);

        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getRepositoryURI(any())).thenReturn(mock(LocalVCRepositoryUri.class));
        Repository repository = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(any(), anyBoolean(), anyString(), anyBoolean())).thenReturn(repository);

        // Back the mocked RepositoryService by the in-memory file map so edits are observable end to end.
        when(repositoryService.getFile(any(), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            if (!files.containsKey(path)) {
                throw new IOException("no such file: " + path);
            }
            return files.get(path).getBytes(UTF_8);
        });
        when(gitService.getFileByName(any(), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            return files.containsKey(path) ? Optional.of(mock(de.tum.cit.aet.artemis.programming.domain.File.class)) : Optional.empty();
        });
        doAnswer(invocation -> {
            String path = invocation.getArgument(1);
            files.put(path, new String(((java.io.InputStream) invocation.getArgument(2)).readAllBytes(), UTF_8));
            return null;
        }).when(repositoryService).createFile(any(), anyString(), any());

        tools = new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, gitService, repositoryService, null, null, "main", null, null, (exerciseArgument, jobArgument) -> {
        });
    }

    @Test
    void shouldApplyMultipleEditsAcrossFilesInOneCall() {
        files.put("A.java", "class Cargo {}");
        files.put("B.java", "new Cargo();");

        String result = tools.applyEdits(List.of(new BatchEdit("SOLUTION", "A.java", "Cargo", "Freight"), new BatchEdit("SOLUTION", "B.java", "Cargo", "Freight")));

        assertThat(result).contains("Edit 1 (SOLUTION:A.java): applied").contains("Edit 2 (SOLUTION:B.java): applied").contains("2 of 2 edit(s) applied");
        assertThat(files.get("A.java")).isEqualTo("class Freight {}");
        assertThat(files.get("B.java")).isEqualTo("new Freight();");
    }

    @Test
    void shouldLetLaterEditsSeeEarlierEditsToTheSameFile() {
        files.put("A.java", "int a = 1;");

        String result = tools.applyEdits(List.of(new BatchEdit("SOLUTION", "A.java", "int a = 1;", "int a = 2;"), new BatchEdit("SOLUTION", "A.java", "int a = 2;", "int a = 3;")));

        assertThat(result).contains("2 of 2 edit(s) applied");
        assertThat(files.get("A.java")).isEqualTo("int a = 3;");
    }

    @Test
    void shouldReportPerEditErrorsWithoutBlockingTheOthers() {
        files.put("A.java", "class Cargo {}");

        String result = tools.applyEdits(List.of(new BatchEdit("SOLUTION", "A.java", "Cargo", "Freight"), new BatchEdit("SOLUTION", "A.java", "DoesNotExist", "x"),
                new BatchEdit("SOLUTION", "Missing.java", "y", "z")));

        assertThat(result).contains("Edit 1 (SOLUTION:A.java): applied").contains("Edit 2 (SOLUTION:A.java): Error: the search text was not found")
                .contains("Edit 3 (SOLUTION:Missing.java): Error: file 'Missing.java' does not exist").contains("1 of 3 edit(s) applied");
        assertThat(files.get("A.java")).isEqualTo("class Freight {}");
    }

    @Test
    void shouldRejectAmbiguousSearchTextPerEdit() {
        files.put("A.java", "x x");

        String result = tools.applyEdits(List.of(new BatchEdit("SOLUTION", "A.java", "x", "y")));

        assertThat(result).contains("Error: the search text occurs more than once").contains("0 of 1 edit(s) applied");
        assertThat(files.get("A.java")).isEqualTo("x x");
    }

    @Test
    void shouldRejectEditsIntoGitMetadata() throws Exception {
        String result = tools.applyEdits(List.of(new BatchEdit("SOLUTION", ".git/config", "a", "b")));

        assertThat(result).contains("is not an editable path").contains("0 of 1 edit(s) applied");
        verify(repositoryService, never()).createFile(any(), eq(".git/config"), any());
    }

    @Test
    void shouldRejectAnEmptyEditList() {
        assertThat(tools.applyEdits(List.of())).startsWith("Error: no edits were provided");
    }

    @Test
    void shouldRejectAnUnknownRepository() {
        assertThat(tools.applyEdits(List.of(new BatchEdit("BOGUS", "A.java", "a", "b")))).contains("Error: unknown repository").contains("0 of 1 edit(s) applied");
    }

    @Test
    void shouldApplyEditsAcrossMultipleRepositoriesInOneCall() throws Exception {
        LocalVCRepositoryUri templateUri = mock(LocalVCRepositoryUri.class);
        LocalVCRepositoryUri solutionUri = mock(LocalVCRepositoryUri.class);
        Repository templateRepo = mock(Repository.class);
        Repository solutionRepo = mock(Repository.class);

        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(templateUri);
        when(exercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(solutionUri);

        GitService gitService = mock(GitService.class);
        when(gitService.getOrCheckoutRepository(eq(templateUri), anyBoolean(), anyString(), anyBoolean())).thenReturn(templateRepo);
        when(gitService.getOrCheckoutRepository(eq(solutionUri), anyBoolean(), anyString(), anyBoolean())).thenReturn(solutionRepo);

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
        when(gitService.getFileByName(any(), anyString())).thenReturn(Optional.of(mock(de.tum.cit.aet.artemis.programming.domain.File.class)));
        doAnswer(invocation -> {
            Repository repo = invocation.getArgument(0);
            String path = invocation.getArgument(1);
            filesByRepo.get(repo).put(path, new String(((java.io.InputStream) invocation.getArgument(2)).readAllBytes(), UTF_8));
            return null;
        }).when(repositoryServiceLocal).createFile(any(), anyString(), any());

        ExerciseVariantJobService jobService = mock(ExerciseVariantJobService.class);
        when(jobService.isCancelRequested(JOB_ID)).thenReturn(false);

        ProgrammingVariantTools crossRepoTools = new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, gitService, repositoryServiceLocal, null, null, "main", null, null,
                (exerciseArgument, jobArgument) -> {
                });

        String result = crossRepoTools.applyEdits(List.of(new BatchEdit("TEMPLATE", "A.java", "Cargo", "Freight"), new BatchEdit("SOLUTION", "A.java", "Cargo", "Freight")));

        assertThat(result).contains("Edit 1 (TEMPLATE:A.java): applied").contains("Edit 2 (SOLUTION:A.java): applied").contains("2 of 2 edit(s) applied");
        assertThat(filesByRepo.get(templateRepo).get("A.java")).isEqualTo("class Freight {}");
        assertThat(filesByRepo.get(solutionRepo).get("A.java")).isEqualTo("class Freight { void ship() {} }");
    }
}
