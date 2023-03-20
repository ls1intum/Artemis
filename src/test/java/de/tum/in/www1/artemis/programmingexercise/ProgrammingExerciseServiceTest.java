package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

class ProgrammingExerciseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progexservice";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise1;

    private ProgrammingExercise programmingExercise2;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 0, 0, 0, 2);
        var course1 = database.addCourseWithOneProgrammingExercise();
        var course2 = database.addCourseWithOneProgrammingExercise();

        programmingExercise1 = database.getFirstExerciseWithType(course1, ProgrammingExercise.class);
        programmingExercise2 = database.getFirstExerciseWithType(course2, ProgrammingExercise.class);

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
