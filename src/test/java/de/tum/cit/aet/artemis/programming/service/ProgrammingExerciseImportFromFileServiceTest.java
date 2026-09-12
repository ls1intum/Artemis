package de.tum.cit.aet.artemis.programming.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import de.tum.cit.aet.artemis.localci.service.LegacyBuildPlanConverterService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
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
    private ProgrammingExerciseImportRepositoryService programmingExerciseImportRepositoryService;

    @Mock
    private LegacyBuildPlanConverterService legacyBuildPlanConverterService;

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
                new ZipFileService(fileService), staticCodeAnalysisService, programmingExerciseRepositoryService, programmingExerciseImportRepositoryService, fileService,
                profileService, buildPlanRepository, tempFileUtilService, Optional.of(legacyBuildPlanConverterService));
    }

    @Test
    void importProgrammingExerciseFromFile_triggersBuildsOnlyAfterImportedRepositoriesWerePushed() throws Exception {
        var importedExercise = importFixture("valid-import.zip");

        InOrder importOrder = inOrder(programmingExerciseImportRepositoryService, programmingExerciseCreationUpdateService);
        importOrder.verify(programmingExerciseCreationUpdateService).createProgrammingExercise(any(ProgrammingExercise.class), eq(false), eq(true));
        importOrder.verify(programmingExerciseImportRepositoryService).importRepositoriesFromFile(eq(importedExercise), any(Path.class), any(User.class));
        importOrder.verify(programmingExerciseCreationUpdateService).setupBuildPlansAndTriggerInitialBuilds(importedExercise);
    }

    /**
     * The exercise details file used to be written as the exercise entity and is written as a record now. Both shapes
     * name the title the same way, so an archive of an older Artemis version still imports.
     */
    @ParameterizedTest
    @CsvSource({ "valid-import.zip, validImport", "valid-import-dto-details.zip, dtoShapedImport" })
    void importProgrammingExerciseFromFile_readsTheTitleFromEntityShapedAndFromRecordShapedDetails(String fixtureName, String expectedTitle) throws Exception {
        var importedExercise = importFixture(fixtureName);

        verify(programmingExerciseRepositoryService).adjustProjectNames(expectedTitle, importedExercise);
    }

    /**
     * Runs an import of the given zip from the test resources and returns the created exercise.
     *
     * @param fixtureName the name of the zip in {@code test-data/import-from-file}
     * @return the exercise the creation service returned
     */
    private ProgrammingExercise importFixture(String fixtureName) throws Exception {
        Path importExerciseDir = tempDir.resolve("imported-exercise-dir");
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

        var importZip = new ClassPathResource("test-data/import-from-file/" + fixtureName);
        MockMultipartFile zipFile = new MockMultipartFile("file", fixtureName, "application/zip", importZip.getInputStream());

        programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(originalExercise, zipFile, new Course(), new User());
        return importedExercise;
    }
}
