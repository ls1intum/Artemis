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

import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Unit tests for {@code prefetchContext} (performance lever A4): seeds the round's opening user message with the
 * file trees of all three repositories plus the content of files the plan's backtick-quoted identifiers point at,
 * bounded by a character budget.
 */
class ProgrammingVariantToolsPrefetchTest {

    private static final String JOB_ID = "job-1";

    private ProgrammingVariantTools tools;

    /** Same in-memory file map backs all three repository checkouts (they share one mock Repository here). */
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

        when(repositoryService.getFiles(any())).thenAnswer(invocation -> {
            Map<String, FileType> tree = new HashMap<>();
            files.keySet().forEach(path -> tree.put(path, FileType.FILE));
            return tree;
        });
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
    void shouldIncludeFileTreesOfAllThreeRepositories() {
        files.put("A.java", "class Cargo {}");
        ChangePlan plan = new ChangePlan("Variant", "statement", List.of("no identifiers here"), List.of());

        String context = tools.prefetchContext(plan);

        assertThat(context).contains("solution file tree").contains("exercise file tree").contains("tests file tree").contains("A.java");
    }

    @Test
    void shouldPrefetchContentOfFilesMatchingBacktickedIdentifiers() {
        files.put("Cargo.java", "class Cargo { int weight; }");
        files.put("Unrelated.java", "class Unrelated {}");
        ChangePlan plan = new ChangePlan("Variant", "statement", List.of("rename `Cargo` to `Freight` in solution, template, and test repositories"), List.of());

        String context = tools.prefetchContext(plan);

        assertThat(context).contains("Cargo.java (prefetched").contains("class Cargo { int weight; }");
        assertThat(context).doesNotContain("Unrelated.java (prefetched");
    }

    @Test
    void shouldOmitContentSectionWhenPlanNamesNoIdentifiers() {
        files.put("A.java", "class Cargo {}");
        ChangePlan plan = new ChangePlan("Variant", "statement", List.of("re-theme comments"), List.of());

        String context = tools.prefetchContext(plan);

        assertThat(context).doesNotContain("(prefetched");
    }
}
