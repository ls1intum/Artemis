package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.domain.enumeration.DiagramType.CommunicationDiagram;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ModelingExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    UserRepository userRepo;

    private ModelingExercise classExercise;

    private List<GradingCriterion> gradingCriteria;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 1, 1);
        database.addCourseWithOneModelingExercise();
        classExercise = (ModelingExercise) exerciseRepo.findAll().get(0);

        // Add users that are not in course
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
        userRepo.save(ModelFactory.generateActivatedUser("tutor2"));
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(roles = "USER")
    public void getCompassStatistic_asStudent_Forbidden() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId() + "/print-statistic", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(roles = "TA")
    public void getCompassStatistic_asTutor_Forbidden() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId() + "/print-statistic", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void getCompassStatistic_asInstructor_Forbidden() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId() + "/print-statistic", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetModelingExercise_asStudent_Forbidden() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetModelingExercise_asTA() throws Exception {
        ModelingExercise receivedModelingExercise = request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.OK, ModelingExercise.class);
        gradingCriteria = database.addGradingInstructionsToExercise(receivedModelingExercise);
        assertThat(receivedModelingExercise.getGradingCriteria().get(0).getTitle()).isEqualTo(null);
        assertThat(receivedModelingExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(gradingCriteria.get(1).getStructuredGradingInstructions().size()).isEqualTo(3);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testGetModelingExercise_tutorNotInCourse() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetModelingExerciseForCourse_asTA() throws Exception {
        request.get("/api/courses/" + classExercise.getCourse().getId() + "/modeling-exercises", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testGetModelingExerciseForCourse_tutorNotInCourse() throws Exception {
        request.get("/api/courses/" + classExercise.getCourse().getId() + "/modeling-exercises", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetModelingExerciseStatistics_asTA() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId() + "/statistics", HttpStatus.OK, String.class);
        request.get("/api/modeling-exercises/" + classExercise.getId() + 1 + "/statistics", HttpStatus.NOT_FOUND, String.class);

        classExercise.setDiagramType(CommunicationDiagram);
        exerciseRepo.save(classExercise);
        request.get("/api/modeling-exercises/" + classExercise.getId() + "/statistics", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testGetModelingExerciseStatistics_tutorNotInCourse() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId() + "/statistics", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateModelingExercise_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourse().getId());
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);
        ModelingExercise receivedModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(receivedModelingExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(receivedModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().size()).isEqualTo(3);

        modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourse().getId(), 1L);
        request.post("/api/modeling-exercises", modelingExercise, HttpStatus.BAD_REQUEST);

        modelingExercise = modelingExerciseUtilService.createModelingExercise(2L);
        request.post("/api/modeling-exercises", modelingExercise, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testCreateModelingExercise_instructorNotInCourse() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourse().getId());
        request.post("/api/modeling-exercises", modelingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExercise_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourse().getId());
        ModelingExercise createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);

        ModelingExercise modelingExerciseWithSubmission = modelingExerciseUtilService.addExampleSubmission(createdModelingExercise);
        var params = new LinkedMultiValueMap<String, String>();
        var notificationText = "notified!";
        params.add("notificationText", notificationText);
        ModelingExercise returnedModelingExercise = request.putWithResponseBodyAndParams("/api/modeling-exercises", modelingExerciseWithSubmission, ModelingExercise.class,
                HttpStatus.OK, params);
        assertThat(returnedModelingExercise.getExampleSubmissions().size()).isEqualTo(1);
        verify(groupNotificationService).notifyStudentGroupAboutExerciseUpdate(returnedModelingExercise, notificationText);

        // use an arbitrary course id that was not yet stored on the server to get a bad request in the PUT call
        modelingExercise = modelingExerciseUtilService.createModelingExercise(100L, classExercise.getId());
        request.put("/api/modeling-exercises", modelingExercise, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExerciseCriteria_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourse().getId());
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);
        var currentCriteriaSize = modelingExercise.getGradingCriteria().size();
        var newCriteria = new GradingCriterion();
        newCriteria.setTitle("new");
        modelingExercise.addGradingCriteria(newCriteria);
        ModelingExercise createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().size()).isEqualTo(currentCriteriaSize + 1);

        modelingExercise.getGradingCriteria().get(1).setTitle("UPDATE");
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("UPDATE");

        // If the grading criteria are deleted then their instructions should also be deleted
        modelingExercise.setGradingCriteria(null);
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExerciseInstructions_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourse().getId());
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);

        var currentInstructionsSize = modelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().size();
        var newInstruction = new GradingInstruction();
        newInstruction.setInstructionDescription("New Instruction");
        modelingExercise.getGradingCriteria().get(1).addStructuredGradingInstructions(newInstruction);
        ModelingExercise createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().size()).isEqualTo(currentInstructionsSize + 1);

        modelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().get(0).setInstructionDescription("UPDATE");
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().get(0).getInstructionDescription()).isEqualTo("UPDATE");

        modelingExercise.getGradingCriteria().get(1).setStructuredGradingInstructions(null);
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().size()).isGreaterThan(0);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions()).isEqualTo(null);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testUpdateModelingExercise_instructorNotInCourse() throws Exception {
        request.put("/api/modeling-exercises", classExercise, HttpStatus.FORBIDDEN);
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

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testDeleteModelingExercise_notInstructorInCourse() throws Exception {
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN);
    }
}
