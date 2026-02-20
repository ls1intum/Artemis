package de.tum.cit.aet.artemis.text;

import static de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService.zonedDateTimeBiPredicate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Integration tests for exercise versioning on TextExercise operations.
 */
class TextExerciseVersionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textexerciseversion";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    private TextExercise textExercise;

    @BeforeEach
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        exerciseVersionService.createExerciseVersion(textExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTextExercise_createsExerciseVersion() throws Exception {
        textExercise.setId(null);
        textExercise.setTitle("New Text Exercise");
        textExercise.setDifficulty(DifficultyLevel.HARD);
        textExercise.setChannelName("channel");

        // Act: Create the exercise
        TextExercise createdExercise = request.postWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);

        // Assert: Verify operation succeeded
        assertThat(createdExercise).isNotNull();
        assertThat(createdExercise.getId()).isNotNull();

        // Assert: Verify exercise version was created
        exerciseVersionUtilService.verifyExerciseVersionCreated(createdExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.TEXT);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateTextExercise_createsExerciseVersion(boolean reEvaluate) throws Exception {
        // Arrange: Get existing exercise
        Long exerciseId = textExercise.getId();
        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exerciseId, TEST_PREFIX + "instructor1", ExerciseType.TEXT);

        // Modify the exercise
        ExerciseVersionUtilService.updateExercise(textExercise);
        textExercise.setExampleSolution("Updated example solution");

        // Act: Update the exercise
        TextExercise updatedExercise;
        if (reEvaluate) {
            updatedExercise = request.putWithResponseBody("/api/text/text-exercises/" + exerciseId + "/re-evaluate?deleteFeedback=false", textExercise, TextExercise.class,
                    HttpStatus.OK);
        }
        else {
            updatedExercise = request.putWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.OK);
        }

        // Assert: Verify operation succeeded
        assertThat(updatedExercise).isNotNull();

        // Assert: Verify new exercise version was created
        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(updatedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.TEXT);

        // Verify that the new version is different from the previous version
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(previousVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_createsExerciseVersion() throws Exception {
        Course course2 = courseUtilService.addEmptyCourse();
        textExercise.setCourse(course2);
        textExercise.setTitle("New title");

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(textExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.TEXT);

        var newTextExercise = request.postWithResponseBody("/api/text/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);

        // Assert: Verify operation succeeded
        assertThat(newTextExercise).isNotNull();
        assertThat(newTextExercise.getId()).isNotNull();

        // Assert: Verify new exercise version was created
        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(newTextExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.TEXT);

        // Verify that the new version is different from the original version
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
    }
}
