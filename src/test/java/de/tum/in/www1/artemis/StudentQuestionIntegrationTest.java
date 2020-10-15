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

public class StudentQuestionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void editStudentQuestion_asTA() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        studentQuestion.setVisibleForStudents(false);
        studentQuestion.setQuestionText("New Test Student Question");

        StudentQuestion updatedStudentQuestion = request.putWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.OK);
        assertThat(updatedStudentQuestion.getQuestionText().equals("New Test Student Question"));
        assertThat(updatedStudentQuestion.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void editStudentQuestion_asStudent() throws Exception {
        List<StudentQuestion> questions = database.createCourseWithExerciseAndStudentQuestions();
        StudentQuestion studentQuestion_student1 = questions.get(0);
        StudentQuestion studentQuestion_student2 = questions.get(1);

        // update own question --> OK
        studentQuestion_student1.setVisibleForStudents(false);
        studentQuestion_student1.setQuestionText("New Test Student Question");
        StudentQuestion updatedStudentQuestion1 = request.putWithResponseBody("/api/student-questions", studentQuestion_student1, StudentQuestion.class, HttpStatus.OK);
        assertThat(updatedStudentQuestion1.getQuestionText().equals("New Test Student Question"));
        assertThat(updatedStudentQuestion1.isVisibleForStudents()).isFalse();

        // update question from another student --> forbidden
        studentQuestion_student2.setVisibleForStudents(false);
        studentQuestion_student2.setQuestionText("New Test Student Question");
        StudentQuestion updatedStudentQuestion2 = request.putWithResponseBody("/api/student-questions", studentQuestion_student2, StudentQuestion.class, HttpStatus.FORBIDDEN);
        assertThat(updatedStudentQuestion2).isNull();
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
    public void deleteStudentQuestions_asInstructor() throws Exception {
        List<StudentQuestion> studentQuestions = database.createCourseWithExerciseAndStudentQuestions();
        StudentQuestion studentQuestion = studentQuestions.get(0);
        StudentQuestion studentQuestion1 = studentQuestions.get(1);

        request.delete("/api/student-questions/" + studentQuestion.getId(), HttpStatus.OK);
        assertThat(studentQuestionRepository.count()).isEqualTo(1);

        // try to delete not existing question
        request.delete("/api/student-questions/999", HttpStatus.NOT_FOUND);

        // delete question with no lecture id --> OK
        studentQuestion1.setLecture(null);
        studentQuestionRepository.save(studentQuestion1);
        request.delete("/api/student-questions/" + studentQuestion1.getId(), HttpStatus.OK);
        assertThat(studentQuestionRepository.count()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteStudentQuestions_asTA() throws Exception {
        List<StudentQuestion> studentQuestions = database.createCourseWithExerciseAndStudentQuestions();
        StudentQuestion studentQuestion_student1 = studentQuestions.get(0);
        StudentQuestion studentQuestion1_student2 = studentQuestions.get(1);

        // delete own question --> OK
        request.delete("/api/student-questions/" + studentQuestion_student1.getId(), HttpStatus.OK);
        assertThat(studentQuestionRepository.count()).isEqualTo(1);

        // delete question from another user --> OK
        request.delete("/api/student-questions/" + studentQuestion1_student2.getId(), HttpStatus.OK);
        assertThat(studentQuestionRepository.count()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteStudentQuestions_asStudent() throws Exception {
        List<StudentQuestion> studentQuestions = database.createCourseWithExerciseAndStudentQuestions();
        StudentQuestion studentQuestion_student1 = studentQuestions.get(0);
        StudentQuestion studentQuestion1_student2 = studentQuestions.get(1);

        // delete own question --> OK
        request.delete("/api/student-questions/" + studentQuestion_student1.getId(), HttpStatus.OK);
        assertThat(studentQuestionRepository.count()).isEqualTo(1);

        // delete question from another student --> forbidden
        request.delete("/api/student-questions/" + studentQuestion1_student2.getId(), HttpStatus.FORBIDDEN);
        assertThat(studentQuestionRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionVotes_asInstructor() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        StudentQuestion updatedStudentQuestion = request.putWithResponseBody("/api/student-questions/" + studentQuestion.getId() + "/votes", 1, StudentQuestion.class,
                HttpStatus.OK);
        assertThat(updatedStudentQuestion.getVotes().equals(1));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void editStudentQuestionVotes_asTA() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndStudentQuestions().get(0);

        StudentQuestion updatedStudentQuestion = request.putWithResponseBody("/api/student-questions/" + studentQuestion.getId() + "/votes", -1, StudentQuestion.class,
                HttpStatus.OK);
        assertThat(updatedStudentQuestion.getVotes().equals(-1));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void editStudentQuestionVotes_asStudent() throws Exception {
        List<StudentQuestion> questions = database.createCourseWithExerciseAndStudentQuestions();
        StudentQuestion studentQuestion = questions.get(0);

        StudentQuestion updatedStudentQuestion = request.putWithResponseBody("/api/student-questions/" + studentQuestion.getId() + "/votes", 2, StudentQuestion.class,
                HttpStatus.OK);
        assertThat(updatedStudentQuestion.getVotes().equals(2));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAllStudentQuestionsForCourse() throws Exception {
        StudentQuestion studentQuestion = database.createCourseWithExerciseAndLectureAndStudentQuestions().get(0);
        Long courseID = studentQuestion.getCourse().getId();

        List<StudentQuestion> returnedStudentQuestions = request.getList("/api/courses/" + courseID + "/student-questions", HttpStatus.OK, StudentQuestion.class);
        assertThat(returnedStudentQuestions.size()).isEqualTo(4);
    }
}
