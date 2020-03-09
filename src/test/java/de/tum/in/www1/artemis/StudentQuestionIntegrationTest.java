package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;
import de.tum.in.www1.artemis.service.StudentQuestionService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
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
        Course course1 = database.createCoursesWithExercisesAndLectures().get(0);
        StudentQuestion studentQuestion = new StudentQuestion();
        studentQuestion.setExercise(course1.getExercises().iterator().next());
        studentQuestion.setLecture(course1.getLectures().iterator().next());
        studentQuestion.setQuestionText("Test Student Question");
        studentQuestion.setVisibleForStudents(true);
        studentQuestion.setAuthor(database.getUserByLogin("student1"));
        request.postWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.CREATED);

        assertThat(studentQuestion.getExercise()).isNotNull();
        assertThat(studentQuestion.getLecture()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExistingStudentQuestion() throws Exception {
        StudentQuestion studentQuestion = database.createExerciseAndLectureWithStudentQuestions().get(0);

        request.postWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestion() throws Exception {
        StudentQuestion studentQuestion = database.createExerciseAndLectureWithStudentQuestions().get(0);

        studentQuestion.setVisibleForStudents(false);
        studentQuestion.setQuestionText("New Test Student Question");

        StudentQuestion updatedStudentQuestion = request.putWithResponseBody("/api/student-questions", studentQuestion, StudentQuestion.class, HttpStatus.OK);
        assertThat(updatedStudentQuestion.getQuestionText().equals("New Test Student Question"));
        assertThat(updatedStudentQuestion.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllStudentQuestionsForExercise() throws Exception {
        StudentQuestion studentQuestion = database.createExerciseAndLectureWithStudentQuestions().get(0);
        Long exerciseID = studentQuestion.getExercise().getId();

        List<StudentQuestion> returnedStudentQuestions = request.getList("/api/exercises/" + exerciseID + "/student-questions", HttpStatus.OK, StudentQuestion.class);
        assertThat(returnedStudentQuestions.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllStudentQuestionsForLecture() throws Exception {
        StudentQuestion studentQuestion = database.createExerciseAndLectureWithStudentQuestions().get(0);
        Long lectureID = studentQuestion.getLecture().getId();

        List<StudentQuestion> returnedStudentQuestions = request.getList("/api/lectures/" + lectureID + "/student-questions", HttpStatus.OK, StudentQuestion.class);
        assertThat(returnedStudentQuestions.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteStudentQuestion() throws Exception {
        List<StudentQuestion> studentQuestions = database.createExerciseAndLectureWithStudentQuestions();
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
        StudentQuestion studentQuestion = database.createExerciseAndLectureWithStudentQuestions().get(0);

        request.delete("/api/student-questions/" + studentQuestion.getId(), HttpStatus.FORBIDDEN);
        assertThat(studentQuestionRepository.count()).isEqualTo(2);
    }

}
