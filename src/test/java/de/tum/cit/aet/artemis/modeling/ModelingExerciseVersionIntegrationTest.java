package de.tum.cit.aet.artemis.modeling;

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
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Integration tests for exercise versioning on ModelingExercise operations.
 */
class ModelingExerciseVersionIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "modelingexerciseversion";

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ModelingExerciseTestRepository modelingExerciseRepository;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    private Course course;

    private ModelingExercise modelingExercise;

    @BeforeEach
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        modelingExercise = modelingExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        exerciseVersionService.createExerciseVersion(modelingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateModelingExercise_createsExerciseVersion() throws Exception {
        // Arrange
        var now = ZonedDateTime.now();
        ModelingExercise newExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.plusDays(7), now.plusDays(14), DiagramType.ClassDiagram, course);
        newExercise.setTitle("New Modeling Exercise");
        newExercise.setChannelName("exercise-" + UUID.randomUUID().toString().substring(0, 8));

        // Act: Create the exercise
        ModelingExercise createdExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", newExercise, ModelingExercise.class, HttpStatus.CREATED);

        // Assert: Verify operation succeeded
        assertThat(createdExercise).isNotNull();
        assertThat(createdExercise.getId()).isNotNull();

        // Assert: Verify exercise version was created
        exerciseVersionUtilService.verifyExerciseVersionCreated(createdExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.MODELING);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExercise_createsExerciseVersion(boolean reEvaluate) throws Exception {
        // Arrange: Get existing exercise
        Long exerciseId = modelingExercise.getId();
        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exerciseId, TEST_PREFIX + "instructor1", ExerciseType.MODELING);

        // Modify the exercise
        ExerciseVersionUtilService.updateExercise(modelingExercise);
        modelingExercise.setExampleSolutionModel("Updated example solution");
        modelingExercise.setExampleSolutionExplanation("Updated example explanation");
        modelingExercise.setDiagramType(DiagramType.CommunicationDiagram);
        // Act: Update the exercise
        ModelingExercise updatedExercise;
        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(modelingExercise);
        if (reEvaluate) {
            updatedExercise = request.putWithResponseBody("/api/modeling/modeling-exercises/" + exerciseId + "/re-evaluate?deleteFeedback=false", updateModelingExerciseDTO,
                    ModelingExercise.class, HttpStatus.OK);
        }
        else {
            updatedExercise = request.putWithResponseBody("/api/modeling/modeling-exercises", updateModelingExerciseDTO, ModelingExercise.class, HttpStatus.OK);
        }

        // Assert: Verify operation succeeded
        assertThat(updatedExercise).isNotNull();

        // Assert: Verify new exercise version was created
        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(updatedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.MODELING);

        // Verify that the new version is different from the previous version
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(previousVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportModelingExercise_createsExerciseVersion() throws Exception {
        // Arrange: Create target course
        Course targetCourse = courseUtilService.addEmptyCourse();

        // Get original version
        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(modelingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.MODELING);

        // Prepare exercise for import
        var now = ZonedDateTime.now();
        ModelingExercise exerciseToImport = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.plusDays(7), now.plusDays(14), DiagramType.ClassDiagram,
                targetCourse);
        exerciseToImport.setTitle("Imported Modeling Exercise");
        exerciseToImport.setChannelName("imported-" + UUID.randomUUID().toString().substring(0, 8));

        // Act: Import the exercise
        ModelingExercise importedExercise = request.postWithResponseBody("/api/modeling/modeling-exercises/import/" + modelingExercise.getId(), exerciseToImport,
                ModelingExercise.class, HttpStatus.CREATED);

        // Assert: Verify operation succeeded
        assertThat(importedExercise).isNotNull();
        assertThat(importedExercise.getId()).isNotNull();
        assertThat(importedExercise.getId()).isNotEqualTo(modelingExercise.getId());

        // Assert: Verify new exercise version was created
        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(importedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.MODELING);

        // Verify that the new version is different from the original version
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());

    }
}
