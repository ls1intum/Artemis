package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;

/**
 * Tests the paths {@link ProgrammingExerciseExportService} takes when an export cannot be produced.
 * <p>
 * Every export entry point collects what went wrong in an {@code exportErrors} list instead of throwing, because a single failing repository must not abort the export of a
 * whole exercise. These tests pin that the export reports an empty result <em>and</em> says why, since an empty result without an explanation is indistinguishable from an
 * export that simply had nothing to do.
 */
class ProgrammingExerciseExportErrorTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexportfailure";

    private static final long EXERCISE_ID_THAT_DOES_NOT_EXIST = Long.MAX_VALUE;

    @Autowired
    private ProgrammingExerciseExportService programmingExerciseExportService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @TempDir
    Path outputDir;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportInstructorRepository_forAnExerciseThatDoesNotExist_reportsWhyNothingWasExported() {
        List<String> exportErrors = new ArrayList<>();

        var exported = programmingExerciseExportService.exportInstructorRepositoryForExercise(EXERCISE_ID_THAT_DOES_NOT_EXIST, RepositoryType.TEMPLATE, outputDir, exportErrors);

        assertThat(exported).as("nothing can be exported for an exercise that does not exist").isEmpty();
        assertThat(exportErrors).as("the caller is told why the export produced nothing").hasSize(1);
        assertThat(exportErrors.getFirst()).contains("does not exist").contains(String.valueOf(EXERCISE_ID_THAT_DOES_NOT_EXIST));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportInstructorAuxiliaryRepository_forAnExerciseThatDoesNotExist_reportsWhyNothingWasExported() {
        List<String> exportErrors = new ArrayList<>();
        var auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("aux");
        auxiliaryRepository.setExercise(programmingExercise);

        var exported = programmingExerciseExportService.exportInstructorAuxiliaryRepositoryForExercise(EXERCISE_ID_THAT_DOES_NOT_EXIST, auxiliaryRepository, outputDir,
                exportErrors);

        assertThat(exported).as("nothing can be exported for an exercise that does not exist").isEmpty();
        assertThat(exportErrors).as("the caller is told why the export produced nothing").hasSize(1);
        assertThat(exportErrors.getFirst()).contains("does not exist");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportInstructorAuxiliaryRepository_withoutARepositoryUri_reportsTheMissingUri() {
        // Auxiliary repositories of older exercises can be stored without a URI, and the export has to say so rather than fail silently.
        List<String> exportErrors = new ArrayList<>();
        var auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("aux-without-uri");
        auxiliaryRepository.setExercise(programmingExercise);
        auxiliaryRepository.setRepositoryUri(null);

        var exported = programmingExerciseExportService.exportInstructorAuxiliaryRepositoryForExercise(programmingExercise.getId(), auxiliaryRepository, outputDir, exportErrors);

        assertThat(exported).as("an auxiliary repository without a URI cannot be exported").isEmpty();
        assertThat(exportErrors).as("the missing URI is reported").hasSize(1);
        assertThat(exportErrors.getFirst()).contains("repository uri is not defined").contains("aux-without-uri");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportInstructorRepository_whenTheArchiveCannotBeWritten_reportsTheExerciseItBelongsTo() throws Exception {
        // A repository that is missing on disk is exported as an unborn repository rather than reported, so the failure this covers is the one the export cannot work
        // around: the directory the archive has to be written to cannot be created because a file already occupies its path.
        List<String> exportErrors = new ArrayList<>();
        Path blockedOutputDir = outputDir.resolve("blocked");
        FileUtils.write(blockedOutputDir.toFile(), "a file where the export directory should be", StandardCharsets.UTF_8);

        var exported = programmingExerciseExportService.exportInstructorRepositoryForExercise(programmingExercise.getId(), RepositoryType.TESTS, blockedOutputDir, exportErrors);

        assertThat(exported).as("no archive is handed back when it could not be written").isEmpty();
        assertThat(exportErrors).as("the failure names the exercise so an instructor can act on it").hasSize(1);
        assertThat(exportErrors.getFirst()).contains(programmingExercise.getTitle()).contains(String.valueOf(programmingExercise.getId())).contains(RepositoryType.TESTS.getName());
        assertThat(blockedOutputDir).as("the export leaves the path that blocked it untouched").isRegularFile();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportInstructorRepository_forTheTestsRepository_writesAnArchiveNamedAfterTheExercise() throws Exception {
        // The counterpart of the failure above: the same call on a usable output directory has to produce a readable archive and report nothing.
        List<String> exportErrors = new ArrayList<>();

        var exported = programmingExerciseExportService.exportInstructorRepositoryForExercise(programmingExercise.getId(), RepositoryType.TESTS, outputDir, exportErrors);

        assertThat(exportErrors).as("a successful export reports no errors").isEmpty();
        assertThat(exported).as("the export hands back the archive it wrote").isPresent();
        Path archive = exported.orElseThrow().toPath();
        assertThat(archive).as("the archive is on disk").isRegularFile();
        assertThat(archive.getFileName().toString()).as("the archive is named after the course, the exercise and the repository type")
                .isEqualTo(FileUtil.sanitizeFilename(
                        programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getTitle() + "-" + RepositoryType.TESTS.getName())
                        + ".zip");
        // A file of a positive size says nothing about what is in it: the archive has to be readable as a ZIP and carry the repository it names.
        List<String> entryNames = ZipTestUtil.listEntryNames(Files.readAllBytes(archive));
        assertThat(entryNames).as("the archive carries the repository, including its history").isNotEmpty().contains(".git/HEAD", ".git/config");
        assertThat(outputDir).as("no partial archive is left behind").isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(".part"));
    }
}
