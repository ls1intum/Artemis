package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
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

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private Exam testExam1;

    private Exam testExam2;

    private ExerciseGroup exerciseGroup1;

    private ExerciseGroup exerciseGroup2;

    private StudentExam studentExam1;

    private StudentExam studentExam2;

    private StudentExam studentExamForTestExam1;

    private StudentExam studentExamForTestExam2;

    @BeforeEach
    void init() {
        List<User> users = database.addUsers(2, 1, 1, 2);
        User instructor1 = users.get(4);
        User instructor2 = users.get(5);
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
        exam1 = database.addExamWithExerciseGroup(course1, true);
        exam2 = database.addExamWithExerciseGroup(course2, true);
        testExam1 = database.addTestExamWithExerciseGroup(course1, true);
        testExam2 = database.addTestExamWithExerciseGroup(course2, true);
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
        exerciseGroup2 = exam2.getExerciseGroups().get(0);
        studentExam1 = database.addStudentExam(exam1);
        studentExam2 = database.addStudentExam(exam2);
        studentExamForTestExam1 = database.addStudentExamForTestExam(testExam1, users.get(0));
        studentExamForTestExam2 = database.addStudentExamForTestExam(testExam2, users.get(0));
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCheckCourseAccess_asTutor() {
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
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testCheckCourseAccess_asEditor() {
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
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testCheckCourseAccess_asInstructorWithoutCourseAccess() {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCheckCourseAccess_asTutorWithCourseAccess() {
        // checkCourseAccess
        examAccessService.checkCourseAccessForTeachingAssistantElseThrow(course1.getId());
        // checkCourseAndExamAccessElseThrow
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
        // checkCourseAndExamAccessElseThrow
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
        // checkCourseAndExamAndExerciseGroupAccess
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        // checkCourseAndExamAndStudentExamAccess
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(course1.getId(), exam1.getId(), studentExam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_notFound() {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_conflict() {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess() {
        exerciseGroup1.setExam(exam1);
        // checkCourseAndExamAccessElseThrow
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
        testExam1.setRegisteredUsers(Set.of());
        examRepository.save(exam1);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_examIsVisible() {
        testExam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        examRepository.save(testExam1);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExam_studentExamExists() {
        assertThatThrownBy(() -> examAccessService.getStudentExamForTestExamElseThrow(course1.getId(), testExam1.getId(), 555L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExam_wrongExamId() {
        assertThatThrownBy(() -> examAccessService.getStudentExamForTestExamElseThrow(course1.getId(), 7777L, studentExamForTestExam1.getId()))
                .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExam_realExam() {
        assertThatThrownBy(() -> examAccessService.getStudentExamForTestExamElseThrow(course1.getId(), testExam1.getId(), studentExam1.getId()))
                .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExam_wrongExamId_examIsVisible() {
        testExam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        examRepository.save(testExam1);
        assertThatThrownBy(() -> examAccessService.getStudentExamForTestExamElseThrow(course1.getId(), testExam1.getId(), studentExamForTestExam1.getId()))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetExamInCourseElseThrow_noCourseAccess() {
        User student1 = database.getUserByLogin("student1");
        student1.setGroups(Set.of());
        userRepository.save(student1);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetExamInCourseElseThrow_realExam() {
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), exam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetExamInCourseElseThrow_notVisible() {
        testExam1.setVisibleDate(ZonedDateTime.now().plusHours(5));
        examRepository.save(testExam1);
        assertThatThrownBy(() -> examAccessService.getExamInCourseElseThrow(course1.getId(), testExam1.getId())).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetExamInCourseElseThrow_success_studentExamPresent() {
        StudentExam studentExam = examAccessService.getExamInCourseElseThrow(course1.getId(), testExam1.getId());
        assertThat(studentExam.equals(studentExamForTestExam1)).isEqualTo(true);

        StudentExam studentExam2 = examAccessService.getExamInCourseElseThrow(course2.getId(), testExam2.getId());
        assertThat(studentExam2.equals(studentExamForTestExam2)).isEqualTo(true);
    }

}
