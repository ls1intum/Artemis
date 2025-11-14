package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.util.ZipFileTestUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class ProgrammingExerciseLocalVCExportsIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progex-localvc-export";

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationTestRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseIntegrationTestService.setup(TEST_PREFIX, this, versionControlService, continuousIntegrationService);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).orElseThrow();

        // add two students and create LocalVC repos using util
        var p1 = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        var p2 = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student2");
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, p1);
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, p2);
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseIntegrationTestService.tearDown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportByParticipationIds_happyPath() throws Exception {
        var participationIds = programmingExerciseStudentParticipationTestRepository.findByExerciseId(exercise.getId()).stream().map(p -> p.getId().toString()).toList();
        var url = "/api/programming/programming-exercises/" + exercise.getId() + "/export-repos-by-participation-ids/" + String.join(",", participationIds);
        var exportOptions = new RepositoryExportOptionsDTO(false, false, false, null, false, true, false, false, false);
        var file = request.postWithResponseBodyFile(url, exportOptions, HttpStatus.OK);
        assertThat(file).exists();

        // Unzip and assert that both participant repos are present (by login suffix) and contain .git
        Path extracted = zipFileTestUtilService.extractZipFileRecursively(file.getAbsolutePath());
        try (var stream = Files.walk(extracted)) {
            List<Path> allPaths = stream.toList();
            assertThat(allPaths).anyMatch(p -> p.toString().endsWith(Path.of(TEST_PREFIX + "student1", ".git").toString()));
            assertThat(allPaths).anyMatch(p -> p.toString().endsWith(Path.of(TEST_PREFIX + "student2", ".git").toString()));
        }
        finally {
            // cleanup extracted folder
            RepositoryExportTestUtil.deleteDirectoryIfExists(extracted);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generateTests_happyPath() throws Exception {
        // Ensure template, solution and tests repos exist and are wired to LocalVC via util
        var baseRepositories = RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, exercise);
        exercise = programmingExerciseRepository.save(exercise);
        // Reload with buildConfig eagerly to avoid LazyInitializationException
        exercise = programmingExerciseRepository.getProgrammingExerciseWithBuildConfigElseThrow(exercise);

        // Create tests path in tests repo so generator can write test.json
        var testsRepo = baseRepositories.testsRepository();
        String testsPath = java.nio.file.Path.of("test", exercise.getPackageFolderName()).toString();
        if (exercise.getBuildConfig().hasSequentialTestRuns()) {
            testsPath = java.nio.file.Path.of("structural", testsPath).toString();
        }
        Path testsDir = testsRepo.workingCopyGitRepoFile.toPath().resolve(testsPath);
        Files.createDirectories(testsDir);
        // Add placeholder file to ensure directory is committed
        Files.createFile(testsDir.resolve(".placeholder"));
        testsRepo.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(testsRepo.workingCopyGitRepo).setMessage("Init tests dir").call();
        testsRepo.workingCopyGitRepo.push().setRemote("origin").call();

        // Also make sure exercise/solution repos have at least initial commit (already done by createAndConfigureLocalRepository)
        // Call generate-tests endpoint
        var path = "/api/programming/programming-exercises/" + exercise.getId() + "/generate-tests";
        var result = request.putWithResponseBody(path, exercise, String.class, HttpStatus.OK);
        assertThat(result).startsWith("Successfully generated the structure oracle");
    }
}
