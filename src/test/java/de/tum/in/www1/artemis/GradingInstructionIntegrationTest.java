package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.web.rest.GradingInstructionResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.GradingInstructionRepository;
import de.tum.in.www1.artemis.service.GradingInstructionService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class GradingInstructionIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    GradingInstructionRepository gradingInstructionRepository;

    @Autowired
    GradingInstructionService gradingInstructionService;

    private Exercise exercise;

    private List<GradingInstruction> gradingInstructions;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 2, 1);
        database.addCourseWithOneTextExercise();
        long courseID = courseRepository.findAllActive().get(0).getId();
        exercise = exerciseRepository.findByCourseId(courseID).get(0);

        gradingInstructionSet = database.addGradingInstructionsToExercise(exercise);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createGradingInstruction_asTutor_forbidden() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            request.post(ROOT + GRADING_INSTRUCTIONS, gradingInstruction, HttpStatus.FORBIDDEN);
        }
        assertThat(gradingInstructionRepository.findAll().isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createGradingInstruction() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            gradingInstruction = request.postWithResponseBody(ROOT + GRADING_INSTRUCTIONS, gradingInstruction, GradingInstruction.class);
            assertThat(gradingInstruction).isNotNull();
            assertThat(gradingInstruction.getId()).isNotNull();
            assertThat(gradingInstruction.getInstructionDescription()).isNotNull();
        }
        assertThat(gradingInstructions.size()).isEqualTo(2);
        var gradingInstruction = new GradingInstruction();
        gradingInstruction.setId(1l);
        request.postWithResponseBody(ROOT + GRADING_INSTRUCTIONS, gradingInstruction, GradingInstruction.class, HttpStatus.BAD_REQUEST);
        assertThat(gradingInstructionRepository.findAll()).as("Grading instruction has not been stored").size().isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteGradingInstruction() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            GradingInstruction savedGradingInstruction = gradingInstructionRepository.save(gradingInstruction);
            request.delete(ROOT + GRADING_INSTRUCTIONS + "/" + savedGradingInstruction.getId(), HttpStatus.OK);
        }
        assertThat(gradingInstructionRepository.findAll().isEmpty()).isTrue();

        request.delete(ROOT + GRADING_INSTRUCTIONS + "/" + null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteGradingInstruction_asTutor_forbidden() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            GradingInstruction savedGradingInstruction = gradingInstructionRepository.save(gradingInstruction);
            request.delete(ROOT + GRADING_INSTRUCTIONS + "/" + savedGradingInstruction.getId(), HttpStatus.FORBIDDEN);
        }
        assertThat(gradingInstructionRepository.findAll().isEmpty()).isFalse();
        assertThat(gradingInstructionRepository.findAll().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getAllGradingInstructionsOfExercise_asStudent_forbidden() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            gradingInstructionRepository.save(gradingInstruction);
        }
        request.getList(ROOT + EXERCISES + "/" + exercise.getId() + GRADING_INSTRUCTIONS, HttpStatus.FORBIDDEN, GradingInstruction.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void getAllGradingInstructionsOfExercise() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            gradingInstructionRepository.save(gradingInstruction);
        }
        request.getList(ROOT + EXERCISES + "/" + exercise.getId() + GRADING_INSTRUCTIONS, HttpStatus.OK, GradingInstruction.class);
        assertThat(gradingInstructionRepository.findAll().isEmpty()).isFalse();
        assertThat(gradingInstructionRepository.findAll().size()).isEqualTo(2);
        request.getList(ROOT + EXERCISES + "/" + null + GRADING_INSTRUCTIONS, HttpStatus.BAD_REQUEST, GradingInstruction.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGradingInstruction() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            gradingInstructionRepository.save(gradingInstruction);
            gradingInstruction.setCredits(0.5);
            gradingInstruction.setGradingScale("bad");
            gradingInstruction.setInstructionDescription("UPDATE DESCRIPTION");
            gradingInstruction.setFeedback("UPDATE FEEDBACK");
            gradingInstruction.setUsageCount(0);
            gradingInstruction = request.putWithResponseBody(ROOT + GRADING_INSTRUCTIONS, gradingInstruction, GradingInstruction.class, HttpStatus.OK);
            assertThat(gradingInstruction).isNotNull();
            assertThat(gradingInstruction.getId()).isNotNull();
            assertThat(gradingInstruction.getCredits()).isEqualTo(0.5);
            assertThat(gradingInstruction.getGradingScale()).isEqualTo("bad");
            assertThat(gradingInstruction.getInstructionDescription()).isEqualTo("UPDATE DESCRIPTION");
            assertThat(gradingInstruction.getFeedback()).isEqualTo("UPDATE FEEDBACK");
            assertThat(gradingInstruction.getUsageCount()).isEqualTo(0);

            request.putWithResponseBody(ROOT + GRADING_INSTRUCTIONS, null, GradingInstruction.class, HttpStatus.BAD_REQUEST);

            gradingInstruction.getExercise().setId(0l);
            request.putWithResponseBody(ROOT + GRADING_INSTRUCTIONS, gradingInstruction, GradingInstruction.class, HttpStatus.NOT_FOUND);
        }
        assertThat(gradingInstructions.size()).isEqualTo(2);
        assertThat(gradingInstructionRepository.findAll().isEmpty()).isFalse();
        assertThat(gradingInstructionRepository.findAll().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void updateGradingInstruction_asTutor_forbidden() throws Exception {
        for (GradingInstruction gradingInstruction : gradingInstructions) {
            gradingInstructionRepository.save(gradingInstruction);
            request.putWithResponseBody(ROOT + GRADING_INSTRUCTIONS, gradingInstruction, GradingInstruction.class, HttpStatus.FORBIDDEN);
        }
        assertThat(gradingInstructionRepository.findAll().size()).isEqualTo(2);
    }
}
