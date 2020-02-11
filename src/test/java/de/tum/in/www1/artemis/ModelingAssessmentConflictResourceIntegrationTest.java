package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.ConflictingResult;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ModelAssessmentConflictService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ModelingAssessmentConflictResourceIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ModelAssessmentConflictRepository modelAssessmentConflictRepository;

    @Autowired
    ModelAssessmentConflictService conflictService;

    @Autowired
    ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    AuthorizationCheckService authCheckService;

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    private Course course;

    private ModelingExercise modelingExercise;

    private StudentParticipation studentParticipation;

    private Result result;

    private ConflictingResult conflictingResult;

    private ModelAssessmentConflict modelAssessmentConflict;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);
        database.addCourseWithOneModelingExercise();
        course = courseRepo.findAll().get(0);
        modelingExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        modelingExercise.setCourse(course);
        modelingExerciseRepository.save(modelingExercise);
        studentParticipation = database.addParticipationForExercise(modelingExercise, "student1");
        studentParticipation.setExercise(modelingExercise);
        result = database.addResultToParticipation(studentParticipation);
        conflictingResult = new ConflictingResult();
        conflictingResult.setResult(result);
        modelAssessmentConflict = new ModelAssessmentConflict();
        modelAssessmentConflict.setCausingConflictingResult(conflictingResult);
        modelAssessmentConflict.setState(EscalationState.UNHANDLED);
        modelAssessmentConflictRepository.save(modelAssessmentConflict);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
        modelAssessmentConflictRepository.deleteAll();
        modelingExerciseRepository.deleteAll();
    }

    @Test
    @WithMockUser(value = "student1", roles = "INSTRUCTOR")
    public void testGetAllConflicts_FORBIDDEN() throws Exception {
        request.get("/api/exercises/" + modelingExercise.getId() + "/model-assessment-conflicts", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllConflicts_OK() throws Exception {
        assertThat(userService.getUserWithGroupsAndAuthorities().getGroups()).contains("instructor");
        assertThat(modelingExercise.getCourse()).isEqualTo(course);
        assertThat(authCheckService.isAtLeastInstructorForExercise(modelingExercise)).isTrue();

        assertThat(modelingExerciseRepository.findById(modelingExercise.getId()).get()).as("modeling exercise is present").isNotNull();
        assertThat(modelAssessmentConflictRepository.findAll().isEmpty()).as("modeling assessment conflict repository is not empty").isFalse();
        List<ModelAssessmentConflict> response = request.get("/api/exercises/" + modelingExercise.getId() + "/model-assessment-conflicts", HttpStatus.OK, List.class);

        assertThat(response.isEmpty()).as("List is not empty").isFalse();
        assertThat(response.size()).as("List contains 1 conflict").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "INSTRUCTOR")
    public void testEscalateConflicts_FORBIDDEN() throws Exception {
        request.put("/api/model-assessment-conflicts/" + modelAssessmentConflict.getId() + "/escalate", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testEscalateConflicts_OK() throws Exception {
        assertThat(modelAssessmentConflictRepository.findAll().isEmpty()).as("modeling assessment conflict repository is not empty").isFalse();
        assertThat(modelAssessmentConflictRepository.findById(modelAssessmentConflict.getId()).get()).as("model assessment conflict is saved").isNotNull();
        assertThat(conflictService.getExerciseOfConflict(modelAssessmentConflict.getId())).as("conflict has exercise").isNotNull();
        request.put("/api/model-assessment-conflicts/" + modelAssessmentConflict.getId() + "/escalate", null, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student1", roles = "INSTRUCTOR")
    public void testEscalateConflicts_With_Body_FORBIDDEN() throws Exception {
        List<ModelAssessmentConflict> body = new ArrayList<ModelAssessmentConflict>();
        body.add(modelAssessmentConflict);
        request.put("/api/model-assessment-conflicts/escalate", body, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testEscalateConflicts_With_Body_OK() throws Exception {
        List<ModelAssessmentConflict> body = new ArrayList<ModelAssessmentConflict>();
        body.add(modelAssessmentConflict);

        List<ModelAssessmentConflict> response = request.putWithResponseBody("/api/model-assessment-conflicts/escalate", body, List.class, HttpStatus.OK);
        assertThat(response.isEmpty()).as("response not empty").isFalse();
        assertThat(response.size()).as("size equals body length").isEqualTo(body.size());
    }
}
