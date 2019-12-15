package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.GradingInstructionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")

public class GradingInstructionTest {

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

    @BeforeEach
    public void initTestCase() {
        database.addUsers(0, 10, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createGradingInstruction() throws Exception {
        database.addCourseWithOneTextExercise();

        long courseID = courseRepository.findAllActive().get(0).getId();
        Exercise exercise = exerciseRepository.findByCourseId(courseID).get(0);

        Set<GradingInstruction> gradingInstructionSet = database.addGradingInstructionsToExercise(exercise).getStructuredGradingInstructions();

        Iterator<GradingInstruction> iterator = gradingInstructionSet.iterator();
        while (iterator.hasNext()) {
            GradingInstruction gradingInstruction = iterator.next();
            System.out.println(gradingInstruction.getId());
            gradingInstruction = request.postWithResponseBody("/api/grading-instruction", gradingInstruction, GradingInstruction.class);
            assertThat(gradingInstruction).isNotNull();
            assertThat(gradingInstruction.getId()).isNotNull();
        }

    }
}
