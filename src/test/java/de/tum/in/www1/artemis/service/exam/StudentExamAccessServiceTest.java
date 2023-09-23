package de.tum.in.www1.artemis.service.exam;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class StudentExamAccessServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "seastest"; // only lower case is supported

    @Autowired
    private StudentExamAccessService studentExamAccessService;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private User student1;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private StudentExam studentExam1;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        course1 = courseUtilService.addEmptyCourse();
        course2 = courseUtilService.addEmptyCourse();
        course2.setStudentGroupName("another-group");
        courseRepository.save(course2);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        exam1 = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        studentExam1 = examUtilService.addStudentExam(exam1);
        studentExam1.setUser(student1);
        studentExamRepository.save(studentExam1);
        exam2 = examUtilService.addExam(course2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testIsAtLeastStudentInCourse() {
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course2.getId(), exam2.getId(), student1, false, true));

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course2.getId(), exam2.getId(), studentExam1.getId()));

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course2.getId(), exam2.getId(), studentExam1, student1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamExists() {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), -1L, student1, false, true));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), -1L, studentExam1.getId()));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), -1L, studentExam1, student1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamBelongsToCourse() {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), exam2.getId(), student1, false, true));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam2.getId(), studentExam1.getId()));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam2.getId(), studentExam1, student1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamIsLive() {
        // Exam is not visible.
        Exam examNotStarted = examUtilService.addExamWithUser(course1, student1, false, ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2),
                ZonedDateTime.now().plusHours(3));
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), examNotStarted.getId(), student1, false, true));

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotStarted.getId(), studentExam1.getId()));

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotStarted.getId(), studentExam1, student1));

        // Exam has ended. After exam has ended, it should still be retrievable by the students to see their participation
        Exam examEnded = examUtilService.addExamWithUser(course1, student1, false, ZonedDateTime.now().minusHours(4), ZonedDateTime.now().minusHours(3),
                ZonedDateTime.now().minusHours(1));
        StudentExam studentExamEnded = examUtilService.addStudentExam(examEnded);
        studentExamEnded.setUser(student1);
        studentExamRepository.save(studentExamEnded);
        // does not throw
        studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), examEnded.getId(), student1, false, true);
        // does not throw
        studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examEnded.getId(), studentExamEnded.getId());
        // does not throw
        studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examEnded.getId(), studentExamEnded, student1);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserIsRegisteredForExam() {
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        Exam examNotRegistered = examUtilService.addExamWithUser(course1, student2, false, ZonedDateTime.now().minusHours(4), ZonedDateTime.now().minusHours(1),
                ZonedDateTime.now().plusHours(1));
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), examNotRegistered.getId(), student1, false, true));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotRegistered.getId(), studentExam1.getId()));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotRegistered.getId(), studentExam1, student1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserStudentExamExists() {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamIdEqualsExamOfStudentExam() {
        StudentExam studentExamNotRelatedToExam1 = examUtilService.addStudentExam(exam2);
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamNotRelatedToExam1, student1));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamNotRelatedToExam1.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCurrentUserIsUserOfStudentExam() {
        StudentExam studentExamWithOtherUser = examUtilService.addStudentExam(exam1);
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        studentExamWithOtherUser.setUser(student2);
        studentExamRepository.save(studentExamWithOtherUser);

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamWithOtherUser, student1));

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamWithOtherUser.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCurrentUserHasCourseAccess() {
        assertThatNoException().isThrownBy(() -> studentExamAccessService.checkCourseAccessForStudentElseThrow(course1.getId(), student1));

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> studentExamAccessService.checkCourseAccessForStudentElseThrow(course2.getId(), student1));
    }

}
