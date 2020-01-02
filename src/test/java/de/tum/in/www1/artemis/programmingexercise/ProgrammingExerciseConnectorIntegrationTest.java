package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ProgrammingExerciseConnectorIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    private Course course;

    @BeforeEach
    public void setup() {
        database.addUsers(1, 1, 1);
        course = database.addEmptyCourse();
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        programmingExerciseRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_validExercise_created() throws Exception {
        final var exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(7), course);
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise);
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exercise);

        final var generated = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generated.getId());
        assertThat(exercise).isEqualTo(generated);
    }
}
