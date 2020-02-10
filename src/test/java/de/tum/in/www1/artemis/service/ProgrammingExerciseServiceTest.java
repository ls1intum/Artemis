package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

class ProgrammingExerciseServiceTest extends AbstractSpringIntegrationTest {

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    DatabaseUtilService database;

    ProgrammingExercise programmingExercise1;

    ProgrammingExercise programmingExercise2;

    @BeforeEach
    void init() {
        database.addUsers(0, 0, 2);
        database.addCourseWithOneProgrammingExercise();
        database.addCourseWithOneProgrammingExercise();

        programmingExercise1 = programmingExerciseRepository.findAll().get(0);
        programmingExercise2 = programmingExerciseRepository.findAll().get(1);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldFindProgrammingExerciseWithBuildAndTestDateInFuture() {
        programmingExercise1.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise1);
        programmingExercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise2);

        List<ProgrammingExercise> programmingExercises = programmingExerciseService.findAllWithBuildAndTestAfterDueDateInFuture();

        assertThat(programmingExercises).hasSize(1);
        assertThat(programmingExercises.get(0)).isEqualTo(programmingExercise1);
    }
}
