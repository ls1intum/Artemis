package de.tum.cit.aet.artemis.programming.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseImportFromFileServiceTest {

    @Mock
    private ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    @Mock
    private ProgrammingExerciseValidationService programmingExerciseValidationService;

    @Mock
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Mock
    private ProgrammingExerciseProjectNameService programmingExerciseProjectNameService;

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
                new ZipFileService(fileService), staticCodeAnalysisService, programmingExerciseProjectNameService, programmingExerciseImportRepositoryService, fileService,
                profileService, buildPlanRepository, tempFileUtilService, Optional.of(legacyBuildPlanConverterService), mock(SecurityFrameworkService.class));
    }

    @Test
    void importProgrammingExerciseFromFile_triggersBuildsOnlyAfterImportedRepositoriesWerePushed() throws Exception {
        var importedExercise = importFixture("valid-import.zip");

        InOrder importOrder = inOrder(programmingExerciseImportRepositoryService, programmingExerciseCreationUpdateService);
        importOrder.verify(programmingExerciseCreationUpdateService).createProgrammingExercise(any(ProgrammingExercise.class), any(ProgrammingExerciseBuildConfig.class), eq(false),
                eq(true));
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

        verify(programmingExerciseProjectNameService).adjustProjectNames(expectedTitle, importedExercise);
    }

    /**
     * Runs an import of the given zip from the test resources and returns the created exercise.
     *
     * @param fixtureName the name of the zip in {@code test-data/import-from-file}
     * @return the exercise the creation service returned
     */
    private ProgrammingExercise importFixture(String fixtureName) throws Exception {
        return importFixture(fixtureName, java.util.Map.of(), false);
    }

    private ProgrammingExercise importFixture(String fixtureName, java.util.Map<String, String> additionalEntries, boolean staticAnalysis) throws Exception {
        Path importExerciseDir = tempDir.resolve("imported-exercise-dir");
        Path zipPath = importExerciseDir.resolve("exercise-for-import.zip");
        Files.createDirectories(importExerciseDir);

        when(tempFileUtilService.createTempDirectory("imported-exercise-dir")).thenReturn(importExerciseDir);
        when(tempFileUtilService.createTempFile(importExerciseDir, "exercise-for-import", ".zip")).thenReturn(zipPath);

        var originalExercise = new ProgrammingExercise();
        originalExercise.setStaticCodeAnalysisEnabled(staticAnalysis);
        var importedExercise = new ProgrammingExercise();
        importedExercise.setTemplateParticipation(new TemplateProgrammingExerciseParticipation());
        importedExercise.setSolutionParticipation(new SolutionProgrammingExerciseParticipation());
        importedExercise.setTemplateRepositoryUri("http://artemis.example/git/ABC/abc-exercise.git");
        importedExercise.setSolutionRepositoryUri("http://artemis.example/git/ABC/abc-solution.git");
        importedExercise.setTestRepositoryUri("http://artemis.example/git/ABC/abc-tests.git");
        when(programmingExerciseCreationUpdateService.createProgrammingExercise(eq(originalExercise), any(ProgrammingExerciseBuildConfig.class), eq(false), eq(true)))
                .thenReturn(importedExercise);
        when(programmingExerciseCreationUpdateService.setupBuildPlansAndTriggerInitialBuilds(importedExercise)).thenReturn(importedExercise);

        var importZip = new ClassPathResource("test-data/import-from-file/" + fixtureName);
        byte[] bytes;
        try (var input = importZip.getInputStream()) {
            bytes = input.readAllBytes();
        }
        if (!additionalEntries.isEmpty()) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var original = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes)); var output = new java.util.zip.ZipOutputStream(buffer)) {
                java.util.zip.ZipEntry entry;
                while ((entry = original.getNextEntry()) != null) {
                    output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    original.transferTo(output);
                    output.closeEntry();
                }
                for (var addition : additionalEntries.entrySet()) {
                    output.putNextEntry(new java.util.zip.ZipEntry(addition.getKey()));
                    output.write(addition.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    output.closeEntry();
                }
            }
            bytes = buffer.toByteArray();
        }
        MockMultipartFile zipFile = new MockMultipartFile("file", fixtureName, "application/zip", bytes);

        programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(originalExercise, new ProgrammingExerciseBuildConfig(), zipFile, new Course(), new User());
        return importedExercise;
    }

    @Test
    void jenkinsImportRestoresBuildPlanAndEmbeddedFilesWithoutOverwritingExistingAssets() throws Exception {
        when(profileService.isJenkinsActive()).thenReturn(true);
        Path markdown = Files.createDirectories(tempDir.resolve("markdown"));
        org.apache.commons.io.FileUtils.writeStringToFile((markdown.resolve("existing.txt")).toFile(), "Existing asset must remain", java.nio.charset.StandardCharsets.UTF_8);
        try (var locations = org.mockito.Mockito.mockStatic(de.tum.cit.aet.artemis.core.util.FilePathConverter.class)) {
            locations.when(de.tum.cit.aet.artemis.core.util.FilePathConverter::getMarkdownFilePath).thenReturn(markdown);
            String plan = "pipeline { agent any }";
            var imported = importFixture("valid-import.zip", java.util.Map.of("buildPlan.txt", plan, "files/new.txt", "Imported asset", "files/existing.txt", "Replacement"), true);
            verify(buildPlanRepository).setBuildPlanForExercise(plan, imported);
            verify(staticCodeAnalysisService).createDefaultCategories(imported);
            org.assertj.core.api.Assertions.assertThat(Files.readString(markdown.resolve("new.txt"))).isEqualTo("Imported asset");
            org.assertj.core.api.Assertions.assertThat(Files.readString(markdown.resolve("existing.txt"))).isEqualTo("Existing asset must remain");
        }
    }

    @Test
    void jenkinsImportWithoutBuildPlanRetainsDefaultBuildSetup() throws Exception {
        when(profileService.isJenkinsActive()).thenReturn(true);
        var imported = importFixture("valid-import.zip");
        org.mockito.Mockito.verifyNoInteractions(buildPlanRepository);
        verify(programmingExerciseCreationUpdateService).setupBuildPlansAndTriggerInitialBuilds(imported);
    }
}
