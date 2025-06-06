package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExamAccessServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examaccessservicetest";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private Exam testExam1;

    private Exam testExam2;

    private ExerciseGroup exerciseGroup1;

    private ExerciseGroup exerciseGroup2;

    private User student1;

    private StudentExam studentExam1;

    private StudentExam studentExam2;

    private StudentExam studentExamForTestExam1;

    private StudentExam studentExamForTestExam2;

    @AfterEach
    void cleanup() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
    }

    @BeforeEach
    void init() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 50;
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 2);
        User instructor1 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        User instructor2 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor2");
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        instructor1.setGroups(Collections.singleton("course1InstructorGroup"));
        instructor2.setGroups(Collections.singleton("course2InstructorGroup"));
        userRepository.save(instructor1);
        userRepository.save(instructor2);
        course1 = courseUtilService.addEmptyCourse();
        course2 = courseUtilService.addEmptyCourse();
        course1.setInstructorGroupName("course1InstructorGroup");
        course2.setInstructorGroupName("course2InstructorGroup");
        courseRepository.save(course1);
        courseRepository.save(course2);
        exam1 = examUtilService.addExamWithExerciseGroup(course1, true);
        exam2 = examUtilService.addExamWithExerciseGroup(course2, true);
        testExam1 = examUtilService.addTestExamWithExerciseGroup(course1, true);
        testExam1 = examRepository.save(testExam1);
        ExamUser examUser = new ExamUser();
        examUser.setExam(testExam1);
        examUser.setUser(student1);
        examUser = examUserRepository.save(examUser);
        testExam1.setExamUsers(Set.of(examUser));
        testExam2 = examUtilService.addTestExamWithExerciseGroup(course2, true);
        testExam2 = examRepository.save(testExam2);
        ExamUser examUser1 = new ExamUser();
        examUser1.setExam(testExam2);
        examUser1.setUser(student1);
        examUser1 = examUserRepository.save(examUser1);
        testExam2.setExamUsers(Set.of(examUser1));
        exerciseGroup1 = exam1.getExerciseGroups().getFirst();
        QuizExercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(exerciseGroup1);
        exerciseGroup1.addExercise(quiz);
        exerciseRepository.save(quiz);
        exerciseGroup2 = exam2.getExerciseGroups().getFirst();
        studentExam1 = examUtilService.addStudentExamWithUser(exam1, student1);
        studentExam2 = examUtilService.addStudentExam(exam2);
        studentExamForTestExam1 = examUtilService.addStudentExamForTestExam(testExam1, student1);
        studentExamForTestExam2 = examUtilService.addStudentExamForTestExam(testExam2, student1);
        ExamUser examUser2 = new ExamUser();
        examUser2.setExam(exam1);
        examUser2.setUser(student1);
        examUser2 = examUserRepository.save(examUser2);
        exam1.setExamUsers(Set.of(examUser2));
        exam1 = examRepository.save(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckCourseAccess_asStudent() {
        // checkCourseAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForTeachingAssistantElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAccessElseThrow
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(course1.getId(), exam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAndExerciseGroupAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1))
                .isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.STUDENT, course1.getId(), exam1.getId(), exerciseGroup1))
                .isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCheckCourseAccess_asTutor() {
        // checkCourseAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForEditorElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAccessElseThrow
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAndExerciseGroupAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1))
                .isInstanceOf(AccessForbiddenException.class);

        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCheckCourseAccess_asEditor() {
        // checkCourseAccess
        examAccessService.checkCourseAccessForEditorElseThrow(course1.getId());
        // checkCourseAndExamAccessElseThrow
        examAccessService.checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
        // checkCourseAndExamAndExerciseGroupAccess
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testCheckCourseAccess_asInstructorWithoutCourseAccess() {
        // checkCourseAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForTeachingAssistantElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAccessElseThrow
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(course1.getId(), exam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAndExerciseGroupAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1))
                .isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
    }

    /**
     * This test intentionally contains no asserts as the methods work as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCheckCourseAccess_asTutorWithCourseAccess() {
        // checkCourseAccess
        examAccessService.checkCourseAccessForTeachingAssistantElseThrow(course1.getId());
        // checkCourseAndExamAccessElseThrow
        examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(course1.getId(), exam1.getId());
    }

    /**
     * This test intentionally contains no asserts as the methods work as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAccess_asInstructorWithCourseAccess() {
        // checkCourseAccess
        examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId());
        // checkCourseAndExamAccessElseThrow
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
        // checkCourseAndExamAndExerciseGroupAccess
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        // checkCourseAndExamAndStudentExamAccess
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAccess_notFound() {
        // checkCourseAndExamAccessElseThrow
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), 99999L)).isInstanceOf(EntityNotFoundException.class);
        // checkCourseAndExamAndExerciseGroupAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), 99999L, exerciseGroup1))
                .isInstanceOf(EntityNotFoundException.class);
        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), 99999L, studentExam1.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAccess_conflict() {
        // checkCourseAndExamAccessElseThrow
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId())).isInstanceOf(ConflictException.class);
        // checkCourseAndExamAndExerciseGroupAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam2.getId(), exerciseGroup1))
                .isInstanceOf(ConflictException.class);
        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam2.getId(), studentExam1.getId()))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * This test intentionally contains no asserts as the methods work as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAccess() {
        exerciseGroup1.setExam(exam1);
        // checkCourseAndExamAccessElseThrow
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
        // checkCourseAndExamAndExerciseGroupAccess
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        // checkCourseAndExamAndStudentExamAccess
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAndExerciseGroupAccess_badRequest() {
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup2))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * This test intentionally contains no asserts as the method works as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAndExerciseGroupAccess() {
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAndStudentExamAccess_notFound() {
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), 99999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAndStudentExamAccess_conflict() {
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam2.getId()))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * This test intentionally contains no asserts as the method works as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckCourseAndExamAndStudentExamAccess() {
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_isStudentInCourse() {
        Course course = courseUtilService.addEmptyCourse();
        course.setStudentGroupName("another");
        courseRepository.save(course);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_examExists() {
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), 123155L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_examBelongsToCourse() {
        studentExam2.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentExamRepository.save(studentExam2);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), exam2.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_notRegisteredUser() {
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course2.getId(), exam2.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckAndGetCourseAndExamAccessForConduction_instructor() {
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenAlertException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_registeredUser_noStudentExamPresent_examCanBeStarted() {
        exam1.setStudentExams(Set.of());
        exam1.setStartDate(ZonedDateTime.now().plusMinutes(3));
        exam1.setEndDate(ZonedDateTime.now().plusMinutes(10));
        examRepository.save(exam1);
        studentExamRepository.delete(studentExam1);
        assertThatNoException().isThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), exam1.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_registeredUser_noStudentExamPresent_examCannotBeStarted() {
        exam1.setStudentExams(Set.of());
        exam1.setStartDate(ZonedDateTime.now().plusMinutes(7));
        examRepository.save(exam1);
        studentExamRepository.delete(studentExam1);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_registeredUser_noStudentExamPresent_examHasEnded() {
        exam1.setStudentExams(Set.of());
        exam1.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam1.setEndDate(ZonedDateTime.now().minusMinutes(7));
        examRepository.save(exam1);
        studentExamRepository.delete(studentExam1);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), exam1.getId())).asInstanceOf(type(BadRequestAlertException.class))
                .satisfies(error -> assertThat(error.getParameters().get("skipAlert")).isEqualTo(Boolean.TRUE));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_registeredUser_studentExamPresent() {
        StudentExam studentExam = examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), exam1.getId());
        assertThat(studentExam.equals(studentExam1)).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckAndGetCourseAndExamAccessForConduction_examIsVisible() {
        testExam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        examRepository.save(testExam1);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOrCreateStudentExamAccess() {
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setGroups(Set.of());
        userRepository.save(student1);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOrCreateStudentExamElseThrow_notVisible() {
        testExam1.setVisibleDate(ZonedDateTime.now().plusHours(5));
        examRepository.save(testExam1);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOrCreateStudentExamElseThrow_testExamEnded() {
        testExam1.setEndDate(ZonedDateTime.now().minusHours(5));
        examRepository.save(testExam1);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOrCreateStudentExamElseThrow_multipleUnfinishedStudentExams() {
        User user = studentExamForTestExam1.getUser();
        examUtilService.addStudentExamForTestExam(testExam1, user);
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExamInCourseElseThrow_success_studentOrCreateStudentExamPresent() {
        StudentExam studentExam = examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), testExam1.getId());
        assertThat(studentExam.equals(studentExamForTestExam1)).isEqualTo(true);

        StudentExam studentExam2 = examAccessService.getOrCreateStudentExamElseThrow(course2.getId(), testExam2.getId());
        assertThat(studentExam2.equals(studentExamForTestExam2)).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetOrCreateStudentExamElseThrow_tutor_skipAlert() {
        assertThatThrownBy(() -> examAccessService.getOrCreateStudentExamElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAllowedToGetExamResult_nonExamExercise() {
        var exercise = new FileUploadExercise(); // implicitly, no exam exercise
        exerciseRepository.save(exercise);

        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student1);
        studentParticipationRepository.save(participation);

        assertThatThrownBy(() -> examAccessService.checkIfAllowedToGetExamResult(exercise, participation, student1)).isInstanceOf(ConflictException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAllowedToGetExamResult_periodNotOver() {
        exam1.setEndDate(ZonedDateTime.now().plusMinutes(10));
        examRepository.save(exam1);

        exerciseGroup1.setExam(exam1);
        var exercise = exerciseGroup1.getExercises().stream().findFirst().orElseThrow();
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student1);
        studentParticipationRepository.save(participation);

        assertThatNoException().isThrownBy(() -> examAccessService.checkIfAllowedToGetExamResult(exercise, participation, student1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAllowedToGetExamResult_periodOver_resultsPublished() {
        exam1.setStartDate(ZonedDateTime.now().minusMinutes(11));
        exam1.setEndDate(ZonedDateTime.now().minusMinutes(10));
        exam1.setPublishResultsDate(ZonedDateTime.now().minusMinutes(5));
        examRepository.save(exam1);

        studentExam1.setWorkingTime(1);
        studentExamRepository.save(studentExam1);

        exerciseGroup1.setExam(exam1);
        var exercise = exerciseGroup1.getExercises().stream().findFirst().orElseThrow();
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student1);
        studentParticipationRepository.save(participation);

        assertThatNoException().isThrownBy(() -> examAccessService.checkIfAllowedToGetExamResult(exercise, participation, student1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAllowedToGetExamResult_periodOver_forbidden() {
        exam1.setStartDate(ZonedDateTime.now().minusMinutes(11));
        exam1.setEndDate(ZonedDateTime.now().minusMinutes(10));
        exam1.setPublishResultsDate(null);
        examRepository.save(exam1);

        studentExam1.setWorkingTime(1);
        studentExamRepository.save(studentExam1);

        exerciseGroup1.setExam(exam1);
        var exercise = exerciseGroup1.getExercises().stream().findFirst().orElseThrow();
        exerciseGroup1.setExam(exam1);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student1);
        studentParticipationRepository.save(participation);

        assertThatThrownBy(() -> examAccessService.checkIfAllowedToGetExamResult(exercise, participation, student1)).isInstanceOf(AccessForbiddenException.class);
    }
}
