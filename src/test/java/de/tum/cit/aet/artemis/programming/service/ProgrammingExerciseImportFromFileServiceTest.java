package de.tum.cit.aet.artemis.programming.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseImportFromFileServiceTest {

    @Mock
    private ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    @Mock
    private ProgrammingExerciseValidationService programmingExerciseValidationService;

    @Mock
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Mock
    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private GitService gitService;

    @Mock
    private FileService fileService;

    @Mock
    private ProfileService profileService;

    @Mock
    private BuildPlanRepository buildPlanRepository;

    @Mock
    private TempFileUtilService tempFileUtilService;

    private ProgrammingExerciseImportFromFileService programmingExerciseImportFromFileService;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        programmingExerciseImportFromFileService = new ProgrammingExerciseImportFromFileService(programmingExerciseCreationUpdateService, programmingExerciseValidationService,
                new ZipFileService(fileService), staticCodeAnalysisService, programmingExerciseRepositoryService, repositoryService, gitService, fileService, profileService,
                buildPlanRepository, tempFileUtilService);
    }

    @Test
    void importProgrammingExerciseFromFile_triggersBuildsOnlyAfterImportedRepositoriesWerePushed() throws Exception {
        Path importExerciseDir = tempDir.resolve("imported-exercise-dir");
        Path repositoryCloneDir = tempDir.resolve("repository-clones");
        Path zipPath = importExerciseDir.resolve("exercise-for-import.zip");
        Files.createDirectories(importExerciseDir);

        when(tempFileUtilService.createTempDirectory("imported-exercise-dir")).thenReturn(importExerciseDir);
        when(tempFileUtilService.createTempFile(importExerciseDir, "exercise-for-import", ".zip")).thenReturn(zipPath);

        var originalExercise = new ProgrammingExercise();
        var importedExercise = new ProgrammingExercise();
        importedExercise.setTemplateParticipation(new TemplateProgrammingExerciseParticipation());
        importedExercise.setSolutionParticipation(new SolutionProgrammingExerciseParticipation());
        importedExercise.setTemplateRepositoryUri("http://artemis.example/git/ABC/abc-exercise.git");
        importedExercise.setSolutionRepositoryUri("http://artemis.example/git/ABC/abc-solution.git");
        importedExercise.setTestRepositoryUri("http://artemis.example/git/ABC/abc-tests.git");
        when(programmingExerciseCreationUpdateService.createProgrammingExercise(originalExercise, false, true)).thenReturn(importedExercise);
        when(programmingExerciseCreationUpdateService.setupBuildPlansAndTriggerInitialBuilds(importedExercise)).thenReturn(importedExercise);

        Repository templateRepository = mockRepository(repositoryCloneDir.resolve("exercise-template"));
        Repository solutionRepository = mockRepository(repositoryCloneDir.resolve("exercise-solution"));
        Repository testRepository = mockRepository(repositoryCloneDir.resolve("exercise-tests"));
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(false), eq(true))).thenReturn(templateRepository, solutionRepository, testRepository);

        var importZip = new ClassPathResource("test-data/import-from-file/valid-import.zip");
        MockMultipartFile zipFile = new MockMultipartFile("file", "valid-import.zip", "application/zip", importZip.getInputStream());
        User user = new User();

        programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(originalExercise, zipFile, new Course(), user);

        verify(programmingExerciseCreationUpdateService).createProgrammingExercise(originalExercise, false, true);
        InOrder importOrder = inOrder(gitService, programmingExerciseCreationUpdateService);
        importOrder.verify(gitService).commitAndPush(templateRepository, "Import template from file", true, user);
        importOrder.verify(gitService).commitAndPush(solutionRepository, "Import solution from file", true, user);
        importOrder.verify(gitService).commitAndPush(testRepository, "Import tests from file", true, user);
        importOrder.verify(programmingExerciseCreationUpdateService).setupBuildPlansAndTriggerInitialBuilds(importedExercise);
    }

    private static Repository mockRepository(Path localPath) throws Exception {
        Files.createDirectories(localPath);
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(localPath);
        return repository;
    }
}
