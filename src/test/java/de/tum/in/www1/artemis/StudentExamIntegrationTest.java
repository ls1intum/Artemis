package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ExamAccessService;
import de.tum.in.www1.artemis.service.StudentExamAccessService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class StudentExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @SpyBean
    ExamAccessService examAccessService;

    @SpyBean
    StudentExamAccessService studentExamAccessService;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    StudentExamRepository studentExamRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    private List<User> users;

    private Course course1;

    private Exam exam1;

    private StudentExam studentExam1;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(1, 1, 1);
        course1 = database.addEmptyCourse();
        exam1 = database.addActiveExamWithRegisteredUser(course1, users.get(0));
        Exam exam2 = database.addExam(course1);
        studentExam1 = database.addStudentExam(exam1);
        studentExam1.setUser(users.get(0));
        studentExamRepository.save(studentExam1);
        database.addStudentExam(exam2);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId(), HttpStatus.FORBIDDEN, StudentExam.class);
        request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams", HttpStatus.FORBIDDEN, StudentExam.class);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExam_asInstructor() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId(), HttpStatus.OK, StudentExam.class);
        verify(examAccessService, times(1)).checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExamsForExam_asInstructor() throws Exception {
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams.size()).isEqualTo(1);
        verify(examAccessService, times(1)).checkCourseAndExamAccess(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForConduction() throws Exception {
        Course course = database.addEmptyCourse();
        Exam exam = database.addActiveExamWithRegisteredUser(course, users.get(0));
        StudentExam studentExam = database.addStudentExamWithExercisesAndParticipationAndSubmission(exam, users.get(0));
        var response = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/studentExams/conduction", HttpStatus.OK, StudentExam.class);
        verify(studentExamAccessService, times(1)).checkCourseAndExamAccess(course.getId(), exam.getId(), users.get(0));
        assertThat(response).isEqualTo(studentExam);
        assertThat(response.getExercises().size()).isEqualTo(1);
        assertThat(response.getExercises().get(0).getStudentParticipations().size()).isEqualTo(1);
        // Check that sensitive information has been removed
        TextExercise textExercise = (TextExercise) response.getExercises().get(0);
        assertThat(textExercise.getGradingCriteria()).isEmpty();
        assertThat(textExercise.getGradingInstructions()).isEqualTo(null);
        assertThat(textExercise.getSampleSolution()).isEqualTo(null);
        // Clean up
        Exercise exercise = response.getExercises().get(0);
        for (StudentParticipation s : response.getExercises().get(0).getStudentParticipations()) {
            assertThat(s.getSubmissions().size()).isEqualTo(1);
            exercise.removeParticipation(s);
        }
        exerciseRepository.save(exercise);
    }

}
