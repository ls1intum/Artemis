package de.tum.in.www1.artemis;

import java.time.ZonedDateTime;
import java.util.List;

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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class QuizExerciseIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @BeforeEach
    public void init() {
        database.addUsers(10, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateQuizExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        Course course = courses.get(0);

        QuizExercise quizExercise = ModelFactory.generateQuizExercise(ZonedDateTime.now().plusSeconds(5), null, course);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(1).text("Q1");
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));

        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(1).text("Q2");
        dnd.getDropLocations().add(new DropLocation().posX(10).posY(10).height(10).width(10));
        dnd.getDropLocations().add(new DropLocation().posX(20).posY(20).height(10).width(10));
        dnd.getDragItems().add(new DragItem().text("D1"));
        dnd.getDragItems().add(new DragItem().text("D2"));
        dnd.getCorrectMappings().add(new DragAndDropMapping().dragItem(dnd.getDragItems().get(0)).dropLocation(dnd.getDropLocations().get(0)));
        dnd.getCorrectMappings().add(new DragAndDropMapping().dragItem(dnd.getDragItems().get(1)).dropLocation(dnd.getDropLocations().get(1)));

        ShortAnswerQuestion sa = (ShortAnswerQuestion) new ShortAnswerQuestion().title("SA").score(2).text("This is a long answer text");
        sa.getSpots().add(new ShortAnswerSpot().spotNr(0).width(1));
        sa.getSpots().add(new ShortAnswerSpot().spotNr(2).width(2));
        sa.getSolutions().add(new ShortAnswerSolution().text("is"));
        sa.getSolutions().add(new ShortAnswerSolution().text("long"));
        sa.getCorrectMappings().add(new ShortAnswerMapping().spot(sa.getSpots().get(0)).solution(sa.getSolutions().get(0)));
        sa.getCorrectMappings().add(new ShortAnswerMapping().spot(sa.getSpots().get(1)).solution(sa.getSolutions().get(1)));

        quizExercise.addQuestions(mc);
        quizExercise.addQuestions(dnd);
        quizExercise.addQuestions(sa);
        QuizExercise quizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class);

        // TODO: add some checks
    }

}
