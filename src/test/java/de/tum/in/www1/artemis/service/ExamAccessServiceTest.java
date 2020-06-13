package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
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
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

public class ExamAccessServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    private ExamAccessService examAccessService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private UserRepository userRepository;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    @BeforeEach
    void init() {
        List<User> users = database.addUsers(1, 1, 2);
        User instructor1 = users.get(2);
        User instructor2 = users.get(3);
        instructor1.setGroups(Collections.singleton("course1InstructorGroup"));
        instructor2.setGroups(Collections.singleton("course2InstructorGroup"));
        userRepository.save(instructor1);
        userRepository.save(instructor2);
        course1 = database.addEmptyCourse();
        course2 = database.addEmptyCourse();
        course1.setInstructorGroupName("course1InstructorGroup");
        course2.setInstructorGroupName("course2InstructorGroup");
        courseRepository.save(course1);
        courseRepository.save(course2);
        exam1 = database.addExam(course1);
        exam2 = database.addExam(course2);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        courseRepository.deleteAll();
        examRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckCourseAccess_asStudent() {
        Optional<ResponseEntity<Exam>> accessFailureCourse = examAccessService.checkCourseAccess(course1.getId());
        assertThat(accessFailureCourse.isPresent()).isTrue();
        assertThat(accessFailureCourse.get()).isEqualTo(forbidden());
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccess(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCheckCourseAccess_asTutor() {
        Optional<ResponseEntity<Exam>> accessFailureCourse = examAccessService.checkCourseAccess(course1.getId());
        assertThat(accessFailureCourse.isPresent()).isTrue();
        assertThat(accessFailureCourse.get()).isEqualTo(forbidden());
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccess(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testCheckCourseAccess_asInstructorWithoutCourseAccess() {
        Optional<ResponseEntity<Exam>> accessFailureCourse = examAccessService.checkCourseAccess(course1.getId());
        assertThat(accessFailureCourse.isPresent()).isTrue();
        assertThat(accessFailureCourse.get()).isEqualTo(forbidden());
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccess(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAccess_asInstructorWithCourseAccess() {
        Optional<ResponseEntity<Exam>> accessFailureCourse = examAccessService.checkCourseAccess(course1.getId());
        assertThat(accessFailureCourse.isEmpty()).isTrue();
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccess(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_notFound() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccess(course1.getId(), 99999L);
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(notFound());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_conflict() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccess(course1.getId(), exam2.getId());
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(conflict());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccess(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isEmpty()).isTrue();
    }
}
