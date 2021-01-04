package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class ExamSubmissionServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExamSubmissionService examSubmissionService;

    @Autowired
    ExamService examService;

    @Autowired
    StudentExamRepository studentExamRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    UserRepository userRepository;

    private User user;

    private Course course;

    private Exam exam;

    private Exercise exercise;

    private StudentExam studentExam;

    @BeforeEach
    void init() {
        List<User> users = database.addUsers(1, 0, 1);
        user = users.get(0);
        exercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        course = exercise.getCourseViaExerciseGroupOrCourseMember();
        exam = examService.findAllByCourseId(course.getId()).get(0);
        studentExam = database.addStudentExam(exam);
        studentExam.setWorkingTime(7200); // 2 hours
        studentExam.setUser(user);
        studentExam.addExercise(exercise);
        studentExam = studentExamRepository.save(studentExam);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckSubmissionAllowance_passIfNonExamSubmission() {
        Course tmpCourse = database.addEmptyCourse();
        Exercise nonExamExercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), tmpCourse);
        Optional<ResponseEntity<Submission>> result = examSubmissionService.checkSubmissionAllowance(nonExamExercise, user);
        assertThat(result.isEmpty()).isTrue();
        boolean result2 = examSubmissionService.isAllowedToSubmit(nonExamExercise, user);
        assertThat(result2).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckSubmissionAllowance_isSubmissionInTime() {
        // Should fail when submission is made before start date
        exam.setStartDate(ZonedDateTime.now().plusMinutes(5));
        examService.save(exam);
        Optional<ResponseEntity<Submission>> result = examSubmissionService.checkSubmissionAllowance(exercise, user);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(forbidden());
        boolean result2 = examSubmissionService.isAllowedToSubmit(exercise, user);
        assertThat(result2).isFalse();
        // Should fail when submission is made after (start date + working time)
        exam.setStartDate(ZonedDateTime.now().minusMinutes(130));
        examService.save(exam);
        result = examSubmissionService.checkSubmissionAllowance(exercise, user);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(forbidden());
        result2 = examSubmissionService.isAllowedToSubmit(exercise, user);
        assertThat(result2).isFalse();
        // Should pass if submission is made in time
        exam.setStartDate(ZonedDateTime.now().minusMinutes(90));
        examService.save(exam);
        result = examSubmissionService.checkSubmissionAllowance(exercise, user);
        assertThat(result.isEmpty()).isTrue();
        result2 = examSubmissionService.isAllowedToSubmit(exercise, user);
        assertThat(result2).isTrue();
        // Should fail when submission is made after end date (if no working time is set)
        studentExam.setWorkingTime(0);
        studentExamRepository.save(studentExam);
        exam.setStartDate(ZonedDateTime.now().minusMinutes(130));
        exam.setEndDate(ZonedDateTime.now().minusMinutes(120));
        examService.save(exam);
        result = examSubmissionService.checkSubmissionAllowance(exercise, user);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(forbidden());
        result2 = examSubmissionService.isAllowedToSubmit(exercise, user);
        assertThat(result2).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckSubmissionAllowance_allowedToSubmitToExercise() {
        // Should fail if there is no student exam for user
        exam.setStartDate(ZonedDateTime.now().minusMinutes(90));
        examService.save(exam);
        studentExam.setUser(null);
        studentExamRepository.save(studentExam);
        assertThrows(EntityNotFoundException.class, () -> {
            examSubmissionService.checkSubmissionAllowance(exercise, user);
        });
        assertThrows(EntityNotFoundException.class, () -> {
            examSubmissionService.isAllowedToSubmit(exercise, user);
        });
        // Should fail if the user's student exam does not have the exercise
        studentExam.setUser(user);
        studentExam.removeExercise(exercise);
        studentExamRepository.save(studentExam);
        Optional<ResponseEntity<Submission>> result = examSubmissionService.checkSubmissionAllowance(exercise, user);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(forbidden());
        boolean result2 = examSubmissionService.isAllowedToSubmit(exercise, user);
        assertThat(result2).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckSubmissionAllowance_testRun() {
        final var instructor = userRepository.findOneWithGroupsAndAuthoritiesByLogin("instructor1").get();
        studentExam.setTestRun(true);
        studentExam.setUser(instructor);
        studentExamRepository.save(studentExam);
        assertThat(examSubmissionService.isAllowedToSubmit(exercise, instructor)).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckSubmissionAllowance_submittedStudentExam() {
        studentExam.setSubmitted(true);
        studentExamRepository.save(studentExam);
        assertThat(examSubmissionService.isAllowedToSubmit(exercise, user)).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testPreventMultipleSubmissions() {
        StudentParticipation participation = database.createAndSaveParticipationForExercise(exercise, "student1");
        Submission existingSubmission = ModelFactory.generateTextSubmission("The initial submission", Language.ENGLISH, true);
        existingSubmission = database.addSubmission(participation, existingSubmission);
        Submission receivedSubmission = ModelFactory.generateTextSubmission("This is a submission", Language.ENGLISH, true);
        receivedSubmission = examSubmissionService.preventMultipleSubmissions(exercise, receivedSubmission, user);
        assertThat(receivedSubmission.getId()).isEqualTo(existingSubmission.getId());
    }

}
