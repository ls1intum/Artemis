package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentQuestionAnswerRepository;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;
import de.tum.in.www1.artemis.service.StudentQuestionService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class StudentQuestionAnswerIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    StudentQuestionRepository studentQuestionRepository;

    @Autowired
    StudentQuestionAnswerRepository studentQuestionAnswerRepository;

    @Autowired
    StudentQuestionService studentQuestionService;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(5, 5, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createStudentQuestionAnswer() throws Exception {
        StudentQuestionAnswer studentQuestionAnswer = createStudentQuestionAnswerOnServer();

        // trying to create same studentQuestionAnswer again --> bad request
        request.postWithResponseBody("/api/student-question-answers", studentQuestionAnswer, StudentQuestionAnswer.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionAnswer() throws Exception {
        StudentQuestionAnswer studentQuestionAnswer = createStudentQuestionAnswerOnServer();

        studentQuestionAnswer.setAuthor(database.getUserByLogin("tutor2"));
        studentQuestionAnswer.setAnswerText("New Answer Text");
        studentQuestionAnswer.setAnswerDate(ZonedDateTime.now().minusHours(1));
        StudentQuestionAnswer updatedStudentQuestionAnswerServer = request.putWithResponseBody("/api/student-question-answers", studentQuestionAnswer, StudentQuestionAnswer.class,
                HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswerServer).isEqualTo(studentQuestionAnswer);

        // try to update answer which is not yet on the server (no id) --> bad request
        StudentQuestionAnswer newStudentQuestionAnswer = new StudentQuestionAnswer();
        StudentQuestionAnswer newStudentQuestionAnswerServer = request.putWithResponseBody("/api/student-question-answers", newStudentQuestionAnswer, StudentQuestionAnswer.class,
                HttpStatus.BAD_REQUEST);
        assertThat(newStudentQuestionAnswerServer).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getStudentQuestionAnswer() throws Exception {
        StudentQuestionAnswer studentQuestionAnswer = createStudentQuestionAnswerOnServer();

        StudentQuestionAnswer returnedStudentQuestionAnswer = request.get("/api/student-question-answers/" + studentQuestionAnswer.getId(), HttpStatus.OK,
                StudentQuestionAnswer.class);
        assertThat(returnedStudentQuestionAnswer).isEqualTo(studentQuestionAnswer);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteStudentQuestionAnswer() throws Exception {
        StudentQuestionAnswer studentQuestionAnswer = createStudentQuestionAnswerOnServer();

        request.delete("/api/student-question-answers/" + studentQuestionAnswer.getId(), HttpStatus.OK);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer.getId())).isEmpty();

        // try to delete not existing answer --> not found
        request.delete("/api/student-question-answers/999", HttpStatus.NOT_FOUND);

        // create and delete answer without lecture id --> OK
        StudentQuestionAnswer studentQuestionAnswer1 = createStudentQuestionAnswerOnServer();
        StudentQuestion question = studentQuestionAnswer1.getQuestion();
        question.setLecture(null);
        studentQuestionRepository.save(question);
        request.delete("/api/student-question-answers/" + studentQuestionAnswer1.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteStudentQuestionAnswerAsStudent() throws Exception {
        StudentQuestionAnswer studentQuestionAnswer = createStudentQuestionAnswerOnServer();

        request.delete("/api/student-question-answers/" + studentQuestionAnswer.getId(), HttpStatus.FORBIDDEN);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer.getId())).isNotEmpty();
    }

    private StudentQuestionAnswer createStudentQuestionAnswerOnServer() throws Exception {
        StudentQuestion studentQuestion = database.createExercisesAndLecturesWithStudentQuestions().get(0);

        StudentQuestionAnswer studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.setAuthor(database.getUserByLogin("tutor1"));
        studentQuestionAnswer.setAnswerText("Test Answer");
        studentQuestionAnswer.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer.setQuestion(studentQuestion);
        return request.postWithResponseBody("/api/student-question-answers", studentQuestionAnswer, StudentQuestionAnswer.class, HttpStatus.CREATED);
    }
}
