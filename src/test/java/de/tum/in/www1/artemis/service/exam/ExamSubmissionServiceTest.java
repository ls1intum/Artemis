package de.tum.in.www1.artemis.service.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ExamSubmissionServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "esstest"; // only lower case is supported

    @Autowired
    private ExamSubmissionService examSubmissionService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private User student1;

    private Exam exam;

    private Exercise exercise;

    private StudentExam studentExam;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        exercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        exam = examRepository.findByCourseId(course.getId()).get(0);
        studentExam = examUtilService.addStudentExam(exam);
        studentExam.setWorkingTime(7200); // 2 hours
        studentExam.setUser(student1);
        studentExam.addExercise(exercise);
        studentExam = studentExamRepository.save(studentExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckSubmissionAllowance_passIfNonExamSubmission() {
        Course tmpCourse = courseUtilService.addEmptyCourse();
        Exercise nonExamExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), tmpCourse);
        // should not throw
        examSubmissionService.checkSubmissionAllowanceElseThrow(nonExamExercise, student1);
        boolean result2 = examSubmissionService.isAllowedToSubmitDuringExam(nonExamExercise, student1, false);
        assertThat(result2).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckSubmissionAllowance_isSubmissionInTime() {
        // Should fail when submission is made before start date
        exam.setStartDate(ZonedDateTime.now().plusMinutes(5));
        examRepository.save(exam);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, student1));

        boolean result2 = examSubmissionService.isAllowedToSubmitDuringExam(exercise, student1, false);
        assertThat(result2).isFalse();
        // Should fail when submission is made after (start date + working time)
        exam.setStartDate(ZonedDateTime.now().minusMinutes(130));
        examRepository.save(exam);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, student1));

        result2 = examSubmissionService.isAllowedToSubmitDuringExam(exercise, student1, false);
        assertThat(result2).isFalse();
        // Should pass if submission is made in time
        exam.setStartDate(ZonedDateTime.now().minusMinutes(90));
        examRepository.save(exam);
        // should not throw
        examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, student1);
        result2 = examSubmissionService.isAllowedToSubmitDuringExam(exercise, student1, false);
        assertThat(result2).isTrue();
        // Should fail when submission is made after end date (if no working time is set)
        studentExam.setWorkingTime(0);
        studentExamRepository.save(studentExam);
        exam.setStartDate(ZonedDateTime.now().minusMinutes(130));
        exam.setEndDate(ZonedDateTime.now().minusMinutes(120));
        examRepository.save(exam);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, student1));
        result2 = examSubmissionService.isAllowedToSubmitDuringExam(exercise, student1, false);
        assertThat(result2).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckSubmissionAllowance_allowedToSubmitToExercise() {
        // Should fail if there is no student exam for user
        exam.setStartDate(ZonedDateTime.now().minusMinutes(90));
        examRepository.save(exam);
        studentExam.setUser(null);
        studentExamRepository.save(studentExam);

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, student1));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examSubmissionService.isAllowedToSubmitDuringExam(exercise, student1, false));
        // Should fail if the user's student exam does not have the exercise
        studentExam.setUser(student1);
        studentExam.removeExercise(exercise);
        studentExamRepository.save(studentExam);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, student1));

        boolean result2 = examSubmissionService.isAllowedToSubmitDuringExam(exercise, student1, false);
        assertThat(result2).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckSubmissionAllowance_testRun() {
        final var instructor = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        studentExam.setTestRun(true);
        studentExam.setUser(instructor);
        studentExamRepository.save(studentExam);
        assertThat(examSubmissionService.isAllowedToSubmitDuringExam(exercise, instructor, false)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckSubmissionAllowance_submittedStudentExam() {
        studentExam.setSubmitted(true);
        studentExamRepository.save(studentExam);
        assertThat(examSubmissionService.isAllowedToSubmitDuringExam(exercise, student1, false)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPreventMultipleSubmissions() {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        Submission existingSubmission = ParticipationFactory.generateTextSubmission("The initial submission", Language.ENGLISH, true);
        existingSubmission = participationUtilService.addSubmission(participation, existingSubmission);
        Submission receivedSubmission = ParticipationFactory.generateTextSubmission("This is a submission", Language.ENGLISH, true);
        receivedSubmission = examSubmissionService.preventMultipleSubmissions(exercise, receivedSubmission, student1);
        assertThat(receivedSubmission.getId()).isEqualTo(existingSubmission.getId());
    }

}
