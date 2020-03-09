package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.StudentQuestionService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class StudentQuestionIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    LectureRepository lectureRepo;

    @Autowired
    AttachmentRepository attachmentRepo;

    @Autowired
    StudentQuestionRepository studentQuestionRepository;

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
    public void createStudentQuestion() throws Exception {
        StudentQuestion studentQuestion = new StudentQuestion();
        studentQuestion.setQuestionText("Test Student Question 1");
        studentQuestion.setVisibleForStudents(true);

        StudentQuestion createdStudentQuestion = request.postWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.CREATED);

        assertThat(createdStudentQuestion).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExistingStudentQuestion() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        request.postWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestion_asInstructor() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        studentQuestion.setVisibleForStudents(false);
        studentQuestion.setQuestionText("New Test Student Question");

        StudentQuestion updatedStudentQuestion = request.putWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.OK);
        assertThat(updatedStudentQuestion.getQuestionText().equals("New Test Student Question"));
        assertThat(updatedStudentQuestion.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    public void editStudentQuestion_asStudent() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        studentQuestion.setVisibleForStudents(false);
        studentQuestion.setQuestionText("New Test Student Question");

        StudentQuestion updatedStudentQuestion = request.putWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.OK);
        assertThat(updatedStudentQuestion.getQuestionText().equals("New Test Student Question"));
        assertThat(updatedStudentQuestion.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllStudentQuestionsForExercise() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Long exerciseID = studentQuestion.getExercise().getId();

        List<StudentQuestion> returnedStudentQuestions = request.getList("/api/exercises/" + exerciseID + "/student-questions", HttpStatus.OK, StudentQuestion.class);
        assertThat(returnedStudentQuestions.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllStudentQuestionsForLecture() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");

        Lecture lecture1 = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment1 = ModelFactory.generateAttachment(pastTimestamp, lecture1);
        lecture1.addAttachments(attachment1);
        courseRepo.save(course1);
        lectureRepo.save(lecture1);
        attachmentRepo.save(attachment1);

        StudentQuestion studentQuestion1 = database.createCourseWithExerciseAndStudentQuestions().get(0);
        studentQuestion1.setLecture(lecture1);
        StudentQuestion studentQuestion2 = database.createCourseWithExerciseAndStudentQuestions().get(0);
        studentQuestion2.setLecture(lecture1);
        studentQuestionRepository.save(studentQuestion1);
        studentQuestionRepository.save(studentQuestion2);

        List<StudentQuestion> returnedStudentQuestions = request.getList("/api/lectures/" + lecture1.getId() + "/student-questions", HttpStatus.OK, StudentQuestion.class);
        assertThat(returnedStudentQuestions.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteStudentQuestion() throws Exception {
        List<StudentQuestion> studentQuestions = database.createCourseWithExerciseAndStudentQuestions();
        StudentQuestion studentQuestion = studentQuestions.get(0);

        request.delete("/api/student-questions/" + studentQuestion.getId(), HttpStatus.OK);
        assertThat(studentQuestionRepository.count()).isEqualTo(1);

        // try to delete not existing question
        request.delete("/api/student-questions/999", HttpStatus.NOT_FOUND);

        StudentQuestion studentQuestion1 = studentQuestions.get(1);
        studentQuestion1.setLecture(null);
        studentQuestionRepository.save(studentQuestion1);
        request.delete("/api/student-questions/" + studentQuestion1.getId(), HttpStatus.OK);
        assertThat(studentQuestionRepository.count()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "student5", roles = "USER")
    public void deleteStudentQuestionFromOtherStudent() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        request.delete("/api/student-questions/" + studentQuestion.getId(), HttpStatus.FORBIDDEN);
        assertThat(studentQuestionRepository.count()).isEqualTo(2);
    }

}
