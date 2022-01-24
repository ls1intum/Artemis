package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Collections;
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
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class ExamAccessServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    private Course course1;

    private Exam exam1;

    private Exam exam2;

    private ExerciseGroup exerciseGroup1;

    private ExerciseGroup exerciseGroup2;

    private StudentExam studentExam1;

    private StudentExam studentExam2;

    @BeforeEach
    void init() {
        List<User> users = database.addUsers(1, 1, 0, 2);
        User instructor1 = users.get(2);
        User instructor2 = users.get(3);
        instructor1.setGroups(Collections.singleton("course1InstructorGroup"));
        instructor2.setGroups(Collections.singleton("course2InstructorGroup"));
        userRepository.save(instructor1);
        userRepository.save(instructor2);
        course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        course1.setInstructorGroupName("course1InstructorGroup");
        course2.setInstructorGroupName("course2InstructorGroup");
        courseRepository.save(course1);
        courseRepository.save(course2);
        exam1 = database.addExamWithExerciseGroup(course1, true);
        exam2 = database.addExamWithExerciseGroup(course2, true);
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
        exerciseGroup2 = exam2.getExerciseGroups().get(0);
        studentExam1 = database.addStudentExam(exam1);
        studentExam2 = database.addStudentExam(exam2);
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
        // checkCourseAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForTeachingAssistantElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAccess
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

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCheckCourseAccess_asTutor() {
        // checkCourseAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAndExerciseGroupAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1))
                .isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testCheckCourseAccess_asInstructorWithoutCourseAccess() {
        // checkCourseAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> examAccessService.checkCourseAccessForTeachingAssistantElseThrow(course1.getId())).isInstanceOf(AccessForbiddenException.class);
        // checkCourseAndExamAccess
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCheckCourseAccess_asTutorWithCourseAccess() {
        // checkCourseAccess
        examAccessService.checkCourseAccessForTeachingAssistantElseThrow(course1.getId());
        // checkCourseAndExamAccess
        examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(course1.getId(), exam1.getId());
    }

    /**
     * This test intentionally contains no asserts as the methods work as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAccess_asInstructorWithCourseAccess() {
        // checkCourseAccess
        examAccessService.checkCourseAccessForInstructorElseThrow(course1.getId());
        // checkCourseAndExamAccess
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
        // checkCourseAndExamAndExerciseGroupAccess
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        // checkCourseAndExamAndStudentExamAccess
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_notFound() {
        // checkCourseAndExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), 99999L)).isInstanceOf(EntityNotFoundException.class);
        // checkCourseAndExamAndExerciseGroupAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), 99999L, exerciseGroup1))
                .isInstanceOf(EntityNotFoundException.class);
        // checkCourseAndExamAndStudentExamAccess
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), 99999L, studentExam1.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_conflict() {
        // checkCourseAndExamAccess
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess() {
        exerciseGroup1.setExam(exam1);
        // checkCourseAndExamAccess
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
        // checkCourseAndExamAndExerciseGroupAccess
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        // checkCourseAndExamAndStudentExamAccess
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndExerciseGroupAccess_badRequest() {
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup2))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * This test intentionally contains no asserts as the method works as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndExerciseGroupAccess() {
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndStudentExamAccess_notFound() {
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), 99999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndStudentExamAccess_conflict() {
        assertThatThrownBy(() -> examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam2.getId()))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * This test intentionally contains no asserts as the method works as expected if no exception is thrown.
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndStudentExamAccess() {
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_isStudentInCourse() {
        Course course = database.addEmptyCourse();
        course.setStudentGroupName("another");
        courseRepository.save(course);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_examExists() {
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), 123155L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_examBelongsToCourse() {
        studentExam2.setUser(database.getUserByLogin("student1"));
        studentExamRepository.save(studentExam2);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), exam2.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_registeredUser() {
        studentExam1.setUser(database.getUserByLogin("student1"));
        studentExamRepository.save(studentExam1);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_examIsVisible() {
        Exam exam = database.addActiveExamWithRegisteredUser(course1, database.getUserByLogin("student1"));
        exam.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        examRepository.save(exam);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), exam.getId())).isInstanceOf(AccessForbiddenException.class);
    }
}
