package de.tum.cit.aet.artemis.hyperion.service.variants;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Unit tests for the {@code diffFile} tool: a unified diff between a file's content in the SOURCE exercise and its
 * current content in the variant, so the agent can self-check that its own changes stay within what the plan
 * requires.
 */
class ProgrammingVariantToolsDiffTest {

    private static final String JOB_ID = "job-1";

    private ProgrammingVariantTools tools;

    /** File content per checked-out Repository mock, so source and variant checkouts stay independent. */
    private final Map<Repository, Map<String, String>> filesByRepository = new HashMap<>();

    private Repository sourceSolutionRepo;

    private Repository variantSolutionRepo;

    @BeforeEach
    void setUp() throws Exception {
        LocalVCRepositoryUri sourceUri = mock(LocalVCRepositoryUri.class);
        LocalVCRepositoryUri variantUri = mock(LocalVCRepositoryUri.class);
        sourceSolutionRepo = mock(Repository.class);
        variantSolutionRepo = mock(Repository.class);
        filesByRepository.put(sourceSolutionRepo, new HashMap<>());
        filesByRepository.put(variantSolutionRepo, new HashMap<>());

        ProgrammingExercise sourceExercise = mock(ProgrammingExercise.class);
        when(sourceExercise.getId()).thenReturn(1L);
        when(sourceExercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(sourceUri);

        ProgrammingExercise variantExercise = mock(ProgrammingExercise.class);
        when(variantExercise.getId()).thenReturn(2L);
        when(variantExercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(variantUri);

        GitService gitService = mock(GitService.class);
        when(gitService.getOrCheckoutRepository(eq(sourceUri), anyBoolean(), anyString(), anyBoolean())).thenReturn(sourceSolutionRepo);
        when(gitService.getOrCheckoutRepository(eq(variantUri), anyBoolean(), anyString(), anyBoolean())).thenReturn(variantSolutionRepo);

        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.getFile(any(), anyString())).thenAnswer(invocation -> {
            Repository repo = invocation.getArgument(0);
            String path = invocation.getArgument(1);
            String content = filesByRepository.get(repo).get(path);
            if (content == null) {
                throw new IOException("no such file: " + path);
            }
            return content.getBytes(UTF_8);
        });

        ExerciseVariantJobService jobService = mock(ExerciseVariantJobService.class);
        when(jobService.isCancelRequested(JOB_ID)).thenReturn(false);

        tools = new ProgrammingVariantTools(variantExercise, null, JOB_ID, jobService, gitService, repositoryService, null, null, "main", sourceExercise, null,
                (exerciseArgument, jobArgument) -> {
                });
    }

    @Test
    void shouldRenderAUnifiedDiffWhenContentDiffers() {
        filesByRepository.get(sourceSolutionRepo).put("A.java", "class Cargo {\n    int a;\n}\n");
        filesByRepository.get(variantSolutionRepo).put("A.java", "class Freight {\n    int a;\n}\n");

        String result = tools.diffFile("SOLUTION", "A.java");

        assertThat(result).contains("-class Cargo {").contains("+class Freight {");
    }

    @Test
    void shouldReportNoDifferencesWhenContentIsIdentical() {
        filesByRepository.get(sourceSolutionRepo).put("A.java", "class Cargo {}");
        filesByRepository.get(variantSolutionRepo).put("A.java", "class Cargo {}");

        assertThat(tools.diffFile("SOLUTION", "A.java")).contains("No differences").contains("'A.java'").contains("unchanged from the source");
    }

    @Test
    void shouldReportNewFileWhenAbsentFromSource() {
        filesByRepository.get(variantSolutionRepo).put("New.java", "class New {}");

        assertThat(tools.diffFile("SOLUTION", "New.java")).contains("does not exist in the SOURCE").contains("new file introduced in this variant");
    }

    @Test
    void shouldReportDeletedWhenAbsentFromVariant() {
        filesByRepository.get(sourceSolutionRepo).put("Old.java", "class Old {}");

        assertThat(tools.diffFile("SOLUTION", "Old.java")).contains("no longer exists in the variant").contains("deleted");
    }

    @Test
    void shouldReportMissingFromBoth() {
        assertThat(tools.diffFile("SOLUTION", "Missing.java")).contains("does not exist in either the source or the variant");
    }

    @Test
    void shouldRejectAnUnknownRepository() {
        assertThat(tools.diffFile("BOGUS", "A.java")).startsWith("Error: unknown repository");
    }
}
