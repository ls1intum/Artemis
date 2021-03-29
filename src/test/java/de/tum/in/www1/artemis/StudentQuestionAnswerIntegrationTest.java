package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentQuestionAnswerRepository;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StudentQuestionAnswerIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    StudentQuestionRepository studentQuestionRepository;

    @Autowired
    StudentQuestionAnswerRepository studentQuestionAnswerRepository;

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
    public void createStudentQuestionAnswerAsInstructor() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        StudentQuestionAnswer studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.setAuthor(database.getUserByLoginWithoutAuthorities("instructor1"));
        studentQuestionAnswer.setAnswerText("Test Answer");
        studentQuestionAnswer.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer.setQuestion(studentQuestion);
        StudentQuestionAnswer response = request.postWithResponseBody("/api/courses/" + studentQuestion.getCourse().getId() + "/student-question-answers", studentQuestionAnswer,
                StudentQuestionAnswer.class, HttpStatus.CREATED);

        // should be automatically approved
        assertThat(response.isTutorApproved()).isTrue();
        // trying to create same studentQuestionAnswer again --> bad request
        request.postWithResponseBody("/api/courses/" + studentQuestion.getCourse().getId() + "/student-question-answers", response, StudentQuestionAnswer.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createStudentQuestionAnswerAsTA() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        StudentQuestionAnswer studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.setAuthor(database.getUserByLoginWithoutAuthorities("tutor1"));
        studentQuestionAnswer.setAnswerText("Test Answer");
        studentQuestionAnswer.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer.setQuestion(studentQuestion);
        StudentQuestionAnswer response = request.postWithResponseBody("/api/courses/" + studentQuestion.getCourse().getId() + "/student-question-answers", studentQuestionAnswer,
                StudentQuestionAnswer.class, HttpStatus.CREATED);

        // shouldn't be automatically approved
        assertThat(response.isTutorApproved()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void createStudentQuestionAnswerAsStudent() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        StudentQuestionAnswer studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        studentQuestionAnswer.setAnswerText("Test Answer");
        studentQuestionAnswer.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer.setQuestion(studentQuestion);
        StudentQuestionAnswer response = request.postWithResponseBody("/api/courses/" + studentQuestion.getCourse().getId() + "/student-question-answers", studentQuestionAnswer,
                StudentQuestionAnswer.class, HttpStatus.CREATED);

        // shouldn't be automatically approved
        assertThat(response.isTutorApproved()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionAnswer_asInstructor() throws Exception {
        StudentQuestionAnswer studentQuestionAnswer = createStudentQuestionAnswersOnServer().get(0);

        studentQuestionAnswer.setAuthor(database.getUserByLoginWithoutAuthorities("tutor2"));
        studentQuestionAnswer.setAnswerText("New Answer Text");
        studentQuestionAnswer.setAnswerDate(ZonedDateTime.now().minusHours(1));
        StudentQuestionAnswer updatedStudentQuestionAnswerServer = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer, StudentQuestionAnswer.class,
                HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswerServer).isEqualTo(studentQuestionAnswer);

        // try to update answer which is not yet on the server (no id) --> bad request
        StudentQuestionAnswer newStudentQuestionAnswer = new StudentQuestionAnswer();
        StudentQuestionAnswer newStudentQuestionAnswerServer = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer.getQuestion().getCourse().getId() + "/student-question-answers", newStudentQuestionAnswer, StudentQuestionAnswer.class,
                HttpStatus.BAD_REQUEST);
        assertThat(newStudentQuestionAnswerServer).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void editStudentQuestionAnswer_asTA() throws Exception {
        List<StudentQuestionAnswer> answers = createStudentQuestionAnswersOnServer();
        StudentQuestionAnswer studentQuestionAnswer_tutor1 = answers.get(0);
        StudentQuestionAnswer studentQuestionAnswer_tutor2 = answers.get(1);
        StudentQuestionAnswer studentQuestionAnswer_student1 = answers.get(2);

        // edit own answer --> OK
        studentQuestionAnswer_tutor1.setAnswerText("New Answer Text");
        studentQuestionAnswer_tutor1.setAnswerDate(ZonedDateTime.now().minusHours(1));
        StudentQuestionAnswer updatedStudentQuestionAnswerServer1 = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer_tutor1.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer_tutor1,
                StudentQuestionAnswer.class, HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswerServer1).isEqualTo(studentQuestionAnswer_tutor1);

        // edit answer of other TA --> OK
        studentQuestionAnswer_tutor2.setAnswerText("New Answer Text");
        studentQuestionAnswer_tutor2.setAnswerDate(ZonedDateTime.now().minusHours(1));
        StudentQuestionAnswer updatedStudentQuestionAnswerServer2 = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer_tutor2.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer_tutor2,
                StudentQuestionAnswer.class, HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswerServer2).isEqualTo(studentQuestionAnswer_tutor2);

        // edit answer of other student --> OK
        studentQuestionAnswer_student1.setAnswerText("New Answer Text");
        studentQuestionAnswer_student1.setAnswerDate(ZonedDateTime.now().minusHours(1));
        StudentQuestionAnswer updatedStudentQuestionAnswerServer3 = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer_student1.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer_student1,
                StudentQuestionAnswer.class, HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswerServer3).isEqualTo(studentQuestionAnswer_student1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void editStudentQuestionAnswer_asStudent() throws Exception {
        List<StudentQuestionAnswer> answers = createStudentQuestionAnswersOnServer();
        StudentQuestionAnswer studentQuestionAnswer_tutor1 = answers.get(0);
        StudentQuestionAnswer studentQuestionAnswer_student1 = answers.get(2);

        // update own answer --> OK
        studentQuestionAnswer_student1.setAnswerText("New Answer Text");
        studentQuestionAnswer_student1.setAnswerDate(ZonedDateTime.now().minusHours(1));
        StudentQuestionAnswer updatedStudentQuestionAnswerServer1 = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer_student1.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer_student1,
                StudentQuestionAnswer.class, HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswerServer1).isEqualTo(studentQuestionAnswer_student1);

        // update answer of other user --> forbidden
        studentQuestionAnswer_tutor1.setAnswerText("New Answer Text");
        studentQuestionAnswer_tutor1.setAnswerDate(ZonedDateTime.now().minusHours(1));
        StudentQuestionAnswer updatedStudentQuestionAnswerServer = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer_tutor1.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer_tutor1,
                StudentQuestionAnswer.class, HttpStatus.FORBIDDEN);
        assertThat(updatedStudentQuestionAnswerServer).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getStudentQuestionAnswer() throws Exception {
        StudentQuestionAnswer studentQuestionAnswer = createStudentQuestionAnswersOnServer().get(0);

        StudentQuestionAnswer returnedStudentQuestionAnswer = request.get(
                "/api/courses/" + studentQuestionAnswer.getQuestion().getCourse().getId() + "/student-question-answers/" + studentQuestionAnswer.getId(), HttpStatus.OK,
                StudentQuestionAnswer.class);
        assertThat(returnedStudentQuestionAnswer).isEqualTo(studentQuestionAnswer);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteStudentQuestionAnswer_asInstructor() throws Exception {
        List<StudentQuestionAnswer> answers = createStudentQuestionAnswersOnServer();
        StudentQuestionAnswer studentQuestionAnswer_tutor1 = answers.get(0);
        StudentQuestionAnswer studentQuestionAnswer_tutor2 = answers.get(1);

        request.delete("/api/courses/" + studentQuestionAnswer_tutor1.getQuestion().getCourse().getId() + "/student-question-answers/" + studentQuestionAnswer_tutor1.getId(),
                HttpStatus.OK);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer_tutor1.getId())).isEmpty();

        // try to delete not existing answer --> not found
        request.delete("/api/courses/" + studentQuestionAnswer_tutor1.getQuestion().getCourse().getId() + "/student-question-answers/999", HttpStatus.NOT_FOUND);

        // delete answer without lecture id --> OK
        StudentQuestion question = studentQuestionAnswer_tutor2.getQuestion();
        question.setLecture(null);
        studentQuestionRepository.save(question);
        request.delete("/api/courses/" + studentQuestionAnswer_tutor2.getQuestion().getCourse().getId() + "/student-question-answers/" + studentQuestionAnswer_tutor2.getId(),
                HttpStatus.OK);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer_tutor2.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteStudentQuestionAnswer_AsTA() throws Exception {
        List<StudentQuestionAnswer> answers = createStudentQuestionAnswersOnServer();
        StudentQuestionAnswer studentQuestionAnswer_tutor1 = answers.get(0);
        StudentQuestionAnswer studentQuestionAnswer_student2 = answers.get(3);

        // delete own answer --> OK
        request.delete("/api/courses/" + studentQuestionAnswer_tutor1.getQuestion().getCourse().getId() + "/student-question-answers/" + studentQuestionAnswer_tutor1.getId(),
                HttpStatus.OK);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer_tutor1.getId())).isEmpty();

        // delete answer of other student --> OK
        request.delete("/api/courses/" + studentQuestionAnswer_student2.getQuestion().getCourse().getId() + "/student-question-answers/" + studentQuestionAnswer_student2.getId(),
                HttpStatus.OK);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer_student2.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteStudentQuestionAnswer_AsStudent() throws Exception {
        List<StudentQuestionAnswer> answers = createStudentQuestionAnswersOnServer();
        StudentQuestionAnswer studentQuestionAnswer_student1 = answers.get(2);
        StudentQuestionAnswer studentQuestionAnswer_student2 = answers.get(3);

        // delete own answer --> OK
        request.delete("/api/courses/" + studentQuestionAnswer_student1.getQuestion().getCourse().getId() + "/student-question-answers/" + studentQuestionAnswer_student1.getId(),
                HttpStatus.OK);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer_student1.getId())).isEmpty();

        // delete answer of other student --> forbidden
        request.delete("/api/courses/" + studentQuestionAnswer_student2.getQuestion().getCourse().getId() + "/student-question-answers/" + studentQuestionAnswer_student2.getId(),
                HttpStatus.FORBIDDEN);
        assertThat(studentQuestionAnswerRepository.findById(studentQuestionAnswer_student2.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void toggleStudentQuestionAnswerApproved() throws Exception {
        List<StudentQuestionAnswer> answers = createStudentQuestionAnswersOnServer();
        StudentQuestionAnswer studentQuestionAnswer = answers.get(0);

        // approve answer
        studentQuestionAnswer.setTutorApproved(true);
        StudentQuestionAnswer updatedStudentQuestionAnswer1 = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer, StudentQuestionAnswer.class,
                HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswer1).isEqualTo(studentQuestionAnswer);

        // unapprove answer
        studentQuestionAnswer.setTutorApproved(false);
        StudentQuestionAnswer updatedStudentQuestionAnswer2 = request.putWithResponseBody(
                "/api/courses/" + studentQuestionAnswer.getQuestion().getCourse().getId() + "/student-question-answers", studentQuestionAnswer, StudentQuestionAnswer.class,
                HttpStatus.OK);
        assertThat(updatedStudentQuestionAnswer2).isEqualTo(studentQuestionAnswer);
    }

    private List<StudentQuestionAnswer> createStudentQuestionAnswersOnServer() {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);
        List<StudentQuestionAnswer> answers = new ArrayList<>();

        StudentQuestionAnswer studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.setAuthor(database.getUserByLoginWithoutAuthorities("tutor1"));
        studentQuestionAnswer.setAnswerText("Test Answer");
        studentQuestionAnswer.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer.setQuestion(studentQuestion);
        studentQuestionAnswerRepository.save(studentQuestionAnswer);
        answers.add(studentQuestionAnswer);

        StudentQuestionAnswer studentQuestionAnswer1 = new StudentQuestionAnswer();
        studentQuestionAnswer1.setAuthor(database.getUserByLoginWithoutAuthorities("tutor2"));
        studentQuestionAnswer1.setAnswerText("Test Answer");
        studentQuestionAnswer1.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer1.setQuestion(studentQuestion);
        studentQuestionAnswerRepository.save(studentQuestionAnswer1);
        answers.add(studentQuestionAnswer1);

        StudentQuestionAnswer studentQuestionAnswer2 = new StudentQuestionAnswer();
        studentQuestionAnswer2.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        studentQuestionAnswer2.setAnswerText("Test Answer");
        studentQuestionAnswer2.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer2.setQuestion(studentQuestion);
        studentQuestionAnswerRepository.save(studentQuestionAnswer2);
        answers.add(studentQuestionAnswer2);

        StudentQuestionAnswer studentQuestionAnswer3 = new StudentQuestionAnswer();
        studentQuestionAnswer3.setAuthor(database.getUserByLoginWithoutAuthorities("student2"));
        studentQuestionAnswer3.setAnswerText("Test Answer");
        studentQuestionAnswer3.setAnswerDate(ZonedDateTime.now());
        studentQuestionAnswer3.setQuestion(studentQuestion);
        studentQuestionAnswerRepository.save(studentQuestionAnswer3);
        answers.add(studentQuestionAnswer3);

        return answers;
    }
}
