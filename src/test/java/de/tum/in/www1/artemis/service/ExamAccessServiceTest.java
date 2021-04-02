package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.*;

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
        List<User> users = database.addUsers(1, 1, 2);
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
        Optional<ResponseEntity<Exam>> accessFailureCourse1 = examAccessService.checkCourseAccessForInstructor(course1.getId());
        assertThat(accessFailureCourse1.isPresent()).isTrue();
        assertThat(accessFailureCourse1.get()).isEqualTo(forbidden());
        Optional<ResponseEntity<Exam>> accessFailureCourse2 = examAccessService.checkCourseAccessForTeachingAssistant(course1.getId());
        assertThat(accessFailureCourse2.isPresent()).isTrue();
        assertThat(accessFailureCourse2.get()).isEqualTo(forbidden());
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam1 = examAccessService.checkCourseAndExamAccessForInstructor(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam1.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam1.get()).isEqualTo(forbidden());
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam2 = examAccessService.checkCourseAndExamAccessForTeachingAssistant(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam2.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam2.get()).isEqualTo(forbidden());
        // checkCourseAndExamAndExerciseGroupAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndExerciseGroup.get()).isEqualTo(forbidden());
        // checkCourseAndExamAndStudentExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(),
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndStudentExam.get()).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCheckCourseAccess_asTutor() {
        // checkCourseAccess
        Optional<ResponseEntity<Exam>> accessFailureCourse = examAccessService.checkCourseAccessForInstructor(course1.getId());
        assertThat(accessFailureCourse.isPresent()).isTrue();
        assertThat(accessFailureCourse.get()).isEqualTo(forbidden());
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccessForInstructor(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(forbidden());
        // checkCourseAndExamAndExerciseGroupAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndExerciseGroup.get()).isEqualTo(forbidden());
        // checkCourseAndExamAndStudentExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(),
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndStudentExam.get()).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testCheckCourseAccess_asInstructorWithoutCourseAccess() {
        // checkCourseAccess
        Optional<ResponseEntity<Exam>> accessFailureCourse1 = examAccessService.checkCourseAccessForInstructor(course1.getId());
        assertThat(accessFailureCourse1.isPresent()).isTrue();
        assertThat(accessFailureCourse1.get()).isEqualTo(forbidden());
        Optional<ResponseEntity<Exam>> accessFailureCourse2 = examAccessService.checkCourseAccessForTeachingAssistant(course1.getId());
        assertThat(accessFailureCourse2.isPresent()).isTrue();
        assertThat(accessFailureCourse2.get()).isEqualTo(forbidden());
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam1 = examAccessService.checkCourseAndExamAccessForInstructor(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam1.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam1.get()).isEqualTo(forbidden());
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam2 = examAccessService.checkCourseAndExamAccessForTeachingAssistant(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam2.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam2.get()).isEqualTo(forbidden());
        // checkCourseAndExamAndExerciseGroupAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndExerciseGroup.get()).isEqualTo(forbidden());
        // checkCourseAndExamAndStudentExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(),
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndStudentExam.get()).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCheckCourseAccess_asTutorWithCourseAccess() {
        // checkCourseAccess
        Optional<ResponseEntity<Exam>> accessFailureCourse = examAccessService.checkCourseAccessForTeachingAssistant(course1.getId());
        assertThat(accessFailureCourse.isEmpty()).isTrue();
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccessForTeachingAssistant(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAccess_asInstructorWithCourseAccess() {
        // checkCourseAccess
        Optional<ResponseEntity<Exam>> accessFailureCourse = examAccessService.checkCourseAccessForInstructor(course1.getId());
        assertThat(accessFailureCourse.isEmpty()).isTrue();
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccessForInstructor(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isEmpty()).isTrue();
        // checkCourseAndExamAndExerciseGroupAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isEmpty()).isTrue();
        // checkCourseAndExamAndStudentExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(),
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_notFound() {
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccessForInstructor(course1.getId(), 99999L);
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(notFound());
        // checkCourseAndExamAndExerciseGroupAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), 99999L,
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndExerciseGroup.get()).isEqualTo(notFound());
        // checkCourseAndExamAndStudentExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), 99999L,
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndStudentExam.get()).isEqualTo(notFound());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess_conflict() {
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccessForInstructor(course1.getId(), exam2.getId());
        assertThat(accessFailureCourseAndExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExam.get()).isEqualTo(conflict());
        // checkCourseAndExamAndExerciseGroupAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam2.getId(),
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndExerciseGroup.get()).isEqualTo(conflict());
        // checkCourseAndExamAndStudentExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam2.getId(),
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndStudentExam.get()).isEqualTo(conflict());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAccess() {
        // checkCourseAndExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExam = examAccessService.checkCourseAndExamAccessForInstructor(course1.getId(), exam1.getId());
        assertThat(accessFailureCourseAndExam.isEmpty()).isTrue();
        // checkCourseAndExamAndExerciseGroupAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isEmpty()).isTrue();
        // checkCourseAndExamAndStudentExamAccess
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(),
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndExerciseGroupAccess_notFound() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                99999L);
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndExerciseGroup.get()).isEqualTo(notFound());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndExerciseGroupAccess_conflict() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                exerciseGroup2.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndExerciseGroup.get()).isEqualTo(conflict());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndExerciseGroupAccess() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndExerciseGroup = examAccessService.checkCourseAndExamAndExerciseGroupAccess(course1.getId(), exam1.getId(),
                exerciseGroup1.getId());
        assertThat(accessFailureCourseAndExamAndExerciseGroup.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndStudentExamAccess_notFound() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(), 99999L);
        assertThat(accessFailureCourseAndExamAndStudentExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndStudentExam.get()).isEqualTo(notFound());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndStudentExamAccess_conflict() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(),
                studentExam2.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isPresent()).isTrue();
        assertThat(accessFailureCourseAndExamAndStudentExam.get()).isEqualTo(conflict());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckCourseAndExamAndStudentExamAccess() {
        Optional<ResponseEntity<Exam>> accessFailureCourseAndExamAndStudentExam = examAccessService.checkCourseAndExamAndStudentExamAccess(course1.getId(), exam1.getId(),
                studentExam1.getId());
        assertThat(accessFailureCourseAndExamAndStudentExam.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_isStudentInCourse() {
        Course course = database.addEmptyCourse();
        course.setStudentGroupName("another");
        courseRepository.save(course);
        ResponseEntity<StudentExam> result = examAccessService.checkAndGetCourseAndExamAccessForConduction(course.getId(), exam1.getId());
        assertThat(result).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_examExists() {
        ResponseEntity<StudentExam> result = examAccessService.checkAndGetCourseAndExamAccessForConduction(course1.getId(), 123155L);
        assertThat(result).isEqualTo(notFound());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_examBelongsToCourse() {
        studentExam2.setUser(database.getUserByLogin("student1"));
        studentExamRepository.save(studentExam2);
        ResponseEntity<StudentExam> result = examAccessService.checkAndGetCourseAndExamAccessForConduction(course1.getId(), exam2.getId());
        assertThat(result).isEqualTo(conflict());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_registeredUser() {
        studentExam1.setUser(database.getUserByLogin("student1"));
        studentExamRepository.save(studentExam1);
        ResponseEntity<StudentExam> result = examAccessService.checkAndGetCourseAndExamAccessForConduction(course1.getId(), exam1.getId());
        assertThat(result).isEqualTo(forbidden());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckAndGetCourseAndExamAccessForConduction_examIsVisible() {
        Exam exam = database.addActiveExamWithRegisteredUser(course1, database.getUserByLogin("student1"));
        exam.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        examRepository.save(exam);
        ResponseEntity<StudentExam> result = examAccessService.checkAndGetCourseAndExamAccessForConduction(course1.getId(), exam.getId());
        assertThat(result).isEqualTo(forbidden());
    }
}
