package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class ProgrammingExerciseServiceTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "progexservice";

    private ProgrammingExercise programmingExercise1;

    private ProgrammingExercise programmingExercise2;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 2);
        var course1 = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var course2 = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        programmingExercise1 = exerciseUtilService.getFirstExerciseWithType(course1, ProgrammingExercise.class);
        programmingExercise2 = exerciseUtilService.getFirstExerciseWithType(course2, ProgrammingExercise.class);

        programmingExercise1.setReleaseDate(null);
        programmingExercise2.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExercise1);
        programmingExerciseRepository.save(programmingExercise2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldFindProgrammingExerciseWithBuildAndTestDateInFuture() {
        programmingExercise1.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise1);
        programmingExercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise2);

        List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAllWithBuildAndTestAfterDueDateInFuture();
        assertThat(programmingExercises).contains(programmingExercise1).doesNotContain(programmingExercise2);
    }
}
