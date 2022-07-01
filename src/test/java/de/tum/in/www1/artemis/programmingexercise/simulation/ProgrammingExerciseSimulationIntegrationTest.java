package de.tum.in.www1.artemis.programmingexercise.simulation;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseSimulationResource.Endpoints.EXERCISES_SIMULATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringDevelopmentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseSimulationResource;

class ProgrammingExerciseSimulationIntegrationTest extends AbstractSpringDevelopmentTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise exercise;

    private Course course;

    private static final int numberOfStudents = 2;

    @BeforeEach
    void setup() {
        database.addUsers(numberOfStudents, 1, 0, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseWithoutConnectionToVCSandCI_exerciseIsNull_badRequest() throws Exception {
        request.post(ROOT + EXERCISES_SIMULATION, null, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseWithoutConnectionToVCSandCI_validExercise_created(ExerciseMode mode) throws Exception {
        exercise.setMode(mode);
        assertThat(programmingExerciseRepository.count()).isZero();
        database.addSubmissionPolicyToExercise(ModelFactory.generateLockRepositoryPolicy(1, true), exercise);
        final var generatedExercise = request.postWithResponseBody(
                ProgrammingExerciseSimulationResource.Endpoints.ROOT + ProgrammingExerciseSimulationResource.Endpoints.EXERCISES_SIMULATION, exercise, ProgrammingExercise.class,
                HttpStatus.CREATED);

        assertThat(programmingExerciseRepository.findById(generatedExercise.getId())).isPresent();
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

}
