package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ModelingExerciseIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    private ModelingExercise classExercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 1, 1);
        database.addCourseWithOneModelingExercise();
        classExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(roles = "USER")
    public void getCompassStatistic_asStudent_Forbidden() throws Exception {
        request.get("/api/exercises/" + classExercise.getId() + "/compass-statistic", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(roles = "TA")
    public void getCompassStatistic_asTutor_Forbidden() throws Exception {
        request.get("/api/exercises/" + classExercise.getId() + "/compass-statistic", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void getCompassStatistic_asInstructor_Forbidden() throws Exception {
        request.get("/api/exercises/" + classExercise.getId() + "/compass-statistic", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void getCompassStatistic_asAdmin_Success() throws Exception {
        request.getNullable("/api/exercises/" + classExercise.getId() + "/compass-statistic", HttpStatus.OK, String.class);
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetModelingExercise_asStudent() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetModelingExercise_asTA() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.OK, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteModelingExercise_asInstructor() throws Exception {
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.OK);
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteModelingExercise_asTutor_Forbidden() throws Exception {
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN);
    }
}
