package de.tum.cit.aet.artemis.fileupload;

import static de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService.zonedDateTimeBiPredicate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExercisesDTO;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;

/**
 * Integration tests for exercise versioning on FileUploadExercise operations.
 */
class FileUploadExerciseVersionIntegrationTest extends AbstractFileUploadIntegrationTest {

    private static final String TEST_PREFIX = "fileuploadexerciseversion";

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    private Course course;

    private FileUploadExercise fileUploadExercise;

    private final String filePattern = "png, pdf, jpg";

    @BeforeEach
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        exerciseVersionService.createExerciseVersion(fileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFileUploadExercise_createsExerciseVersion() throws Exception {
        // Arrange
        FileUploadExercise newExercise = FileUploadExerciseFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7),
                ZonedDateTime.now().plusDays(14), filePattern, course);
        newExercise.setTitle("New FileUpload Exercise");
        newExercise.setChannelName("exercise-" + UUID.randomUUID().toString().substring(0, 8));

        // Act: Create the exercise
        FileUploadExercise createdExercise = request.postWithResponseBody("/api/fileupload/file-upload-exercises", newExercise, FileUploadExercise.class, HttpStatus.CREATED);

        // Assert: Verify operation succeeded
        assertThat(createdExercise).isNotNull();
        assertThat(createdExercise.getId()).isNotNull();

        // Assert: Verify exercise version was created
        exerciseVersionUtilService.verifyExerciseVersionCreated(createdExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.FILE_UPLOAD);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateFileUploadExercise_createsExerciseVersion(boolean reEvaluate) throws Exception {
        // Arrange: Get existing exercise
        Long exerciseId = fileUploadExercise.getId();
        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exerciseId, TEST_PREFIX + "instructor1", ExerciseType.FILE_UPLOAD);

        // Modify the exercise
        ExerciseVersionUtilService.updateExercise(fileUploadExercise);
        fileUploadExercise.setExampleSolution("Updated example solution");
        fileUploadExercise.setFilePattern("png, svg");
        // Act: Update the exercise
        FileUploadExercise updatedExercise;
        if (reEvaluate) {
            updatedExercise = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + exerciseId + "/re-evaluate?deleteFeedback=false",
                    UpdateFileUploadExercisesDTO.of(fileUploadExercise), FileUploadExercise.class, HttpStatus.OK);
        }
        else {
            updatedExercise = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + exerciseId, UpdateFileUploadExercisesDTO.of(fileUploadExercise),
                    FileUploadExercise.class, HttpStatus.OK);
        }

        // Assert: Verify operation succeeded
        assertThat(updatedExercise).isNotNull();

        // Assert: Verify new exercise version was created
        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(updatedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.FILE_UPLOAD);

        // Verify that the new version is different from the previous version
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(previousVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportFileUploadExercise_createsExerciseVersion() throws Exception {
        // Arrange: Create target course
        Course targetCourse = courseUtilService.addEmptyCourse();

        // Get original version
        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(fileUploadExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.FILE_UPLOAD);

        // Prepare exercise for import
        FileUploadExercise exerciseToImport = FileUploadExerciseFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7),
                ZonedDateTime.now().plusDays(14), filePattern, targetCourse);
        exerciseToImport.setTitle("Imported FileUpload Exercise");
        exerciseToImport.setChannelName("imported-" + UUID.randomUUID().toString().substring(0, 8));

        // Act: Import the exercise
        FileUploadExercise importedExercise = request.postWithResponseBody("/api/fileupload/file-upload-exercises/import/" + fileUploadExercise.getId(), exerciseToImport,
                FileUploadExercise.class, HttpStatus.CREATED);

        // Assert: Verify operation succeeded
        assertThat(importedExercise).isNotNull();
        assertThat(importedExercise.getId()).isNotNull();
        assertThat(importedExercise.getId()).isNotEqualTo(fileUploadExercise.getId());

        // Assert: Verify new exercise version was created
        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(importedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.FILE_UPLOAD);

        // Verify that the new version is different from the original version
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
    }
}
