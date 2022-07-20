package de.tum.in.www1.artemis.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.exam.StudentExamAccessService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class StudentExamAccessServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private StudentExamAccessService studentExamAccessService;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private CourseRepository courseRepository;

    private List<User> users;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private StudentExam studentExam1;

    @BeforeEach
    void init() {
        users = database.addUsers(2, 0, 0, 0);
        course1 = database.addEmptyCourse();
        course2 = database.addEmptyCourse();
        course2.setStudentGroupName("another-group");
        courseRepository.save(course2);
        exam1 = database.addActiveExamWithRegisteredUser(course1, users.get(0));
        studentExam1 = database.addStudentExam(exam1);
        studentExam1.setUser(users.get(0));
        studentExamRepository.save(studentExam1);
        exam2 = database.addExam(course2);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testIsAtLeastStudentInCourse() {
        assertThrows(AccessForbiddenException.class, () -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course2.getId(), exam2.getId(), users.get(0), false, true));
        assertThrows(AccessForbiddenException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course2.getId(), exam2.getId(), studentExam1, users.get(0)));
        assertThrows(AccessForbiddenException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course2.getId(), exam2.getId(), studentExam1.getId()));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testExamExists() {
        assertThrows(EntityNotFoundException.class, () -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), 1255L, users.get(0), false, true));
        assertThrows(EntityNotFoundException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), 1255L, studentExam1.getId()));
        assertThrows(EntityNotFoundException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), 1255L, studentExam1.getId()));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testExamBelongsToCourse() {
        assertThrows(ConflictException.class, () -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), exam2.getId(), users.get(0), false, true));
        assertThrows(ConflictException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam2.getId(), studentExam1.getId()));
        assertThrows(ConflictException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam2.getId(), studentExam1, users.get(0)));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testExamIsLive() {
        // Exam is not visible.
        Exam examNotStarted = database.addExam(course1, users.get(0), ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusHours(3));
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), examNotStarted.getId(), users.get(0), false, true));
        assertThrows(AccessForbiddenException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotStarted.getId(), studentExam1.getId()));
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotStarted.getId(), studentExam1, users.get(0)));

        // Exam has ended. After exam has ended, it should still be retrievable by the students to see their participation
        Exam examEnded = database.addExam(course1, users.get(0), ZonedDateTime.now().minusHours(4), ZonedDateTime.now().minusHours(3), ZonedDateTime.now().minusHours(1));
        StudentExam studentExamEnded = database.addStudentExam(examEnded);
        studentExamEnded.setUser(users.get(0));
        studentExamRepository.save(studentExamEnded);
        // does not throw
        studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), examEnded.getId(), users.get(0), false, true);
        // does not throw
        studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examEnded.getId(), studentExamEnded.getId());
        // does not throw
        studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examEnded.getId(), studentExamEnded, users.get(0));

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testUserIsRegisteredForExam() {
        Exam examNotRegistered = database.addExam(course1, users.get(1), ZonedDateTime.now().minusHours(4), ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1));
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkCourseAndExamAccessElseThrow(course1.getId(), examNotRegistered.getId(), users.get(0), false, true));
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotRegistered.getId(), studentExam1.getId()));
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), examNotRegistered.getId(), studentExam1, users.get(0)));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testUserStudentExamExists() {
        assertThrows(EntityNotFoundException.class, () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), 55L));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testExamIdEqualsExamOfStudentExam() {
        StudentExam studentExamNotRelatedToExam1 = database.addStudentExam(exam2);
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamNotRelatedToExam1, users.get(0)));
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamNotRelatedToExam1.getId()));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCurrentUserIsUserOfStudentExam() {
        StudentExam studentExamWithOtherUser = database.addStudentExam(exam1);
        studentExamWithOtherUser.setUser(users.get(1));
        studentExamRepository.save(studentExamWithOtherUser);
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamWithOtherUser, users.get(0)));
        assertThrows(AccessForbiddenException.class,
                () -> studentExamAccessService.checkStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExamWithOtherUser.getId()));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCurrentUserHasCourseAccess() {
        assertDoesNotThrow(() -> studentExamAccessService.checkCourseAccessForStudentElseThrow(course1.getId(), users.get(0)));
        assertThrows(AccessForbiddenException.class, () -> studentExamAccessService.checkCourseAccessForStudentElseThrow(course2.getId(), users.get(0)));
    }

}
