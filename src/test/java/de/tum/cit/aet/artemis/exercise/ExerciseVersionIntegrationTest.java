package de.tum.cit.aet.artemis.exercise;

import static de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService.zonedDateTimeBiPredicate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Integration tests for exercise versioning on base Exercise operations.
 */
class ExerciseVersionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseversion";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private TextExercise textExercise;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = (TextExercise) course.getExercises().iterator().next();
        exerciseVersionService.createExerciseVersion(textExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleSecondCorrectionEnabled_createsExerciseVersion() throws Exception {

        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(textExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.TEXT);

        // Act: Toggle second correction enabled
        boolean result = request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/toggle-second-correction", null, Boolean.class, HttpStatus.OK);

        // Assert: Verify operation succeeded
        assertThat(result).isTrue();

        // Assert: Verify new exercise version was created
        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(textExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.TEXT);

        // Verify that the new version is different from the previous version
        assertThat(newVersion.getId()).isNotEqualTo(previousVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(previousVersion.getExerciseSnapshot());

    }

}
