package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

public class CourseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void testGetActiveStudents() {
        SecurityUtils.setAuthorizationObject();
        var course = database.addEmptyCourse();
        var now = ZonedDateTime.now();
        var exercise = ModelFactory.generateTextExercise(now, now, now, course);
        course.addExercises(exercise);
        exercise = exerciseRepo.save(exercise);

        var users = database.addUsers(2, 0, 0);
        var student1 = users.get(0);
        var participation1 = new StudentParticipation();
        participation1.setParticipant(student1);
        participation1.exercise(exercise);
        studentParticipationRepo.save(participation1);
        var student2 = users.get(1);
        var participation2 = new StudentParticipation();
        participation2.setParticipant(student2);
        participation2.exercise(exercise);
        studentParticipationRepo.save(participation2);
        var participation3 = new StudentParticipation();
        participation3.setParticipant(student2);
        participation3.exercise(exercise);
        studentParticipationRepo.save(participation3);
        var participation4 = new StudentParticipation();
        participation4.setParticipant(student2);
        participation4.exercise(exercise);
        studentParticipationRepo.save(participation4);

        var submission1 = new TextSubmission();
        submission1.text("text of text submission1");
        submission1.setLanguage(Language.ENGLISH);
        submission1.setSubmitted(true);
        submission1.setParticipation(participation1);
        submission1.setSubmissionDate(now);

        var submission2 = new TextSubmission();
        submission2.text("text of text submission2");
        submission2.setLanguage(Language.ENGLISH);
        submission2.setSubmitted(true);
        submission2.setParticipation(participation2);
        submission2.setSubmissionDate(now);

        var submission3 = new TextSubmission();
        submission3.text("text of text submission3");
        submission3.setLanguage(Language.ENGLISH);
        submission3.setSubmitted(true);
        submission3.setSubmissionDate(now.minusDays(14));
        submission3.setParticipation(participation3);

        var submission4 = new TextSubmission();
        submission4.text("text of text submission4");
        submission4.setLanguage(Language.ENGLISH);
        submission4.setSubmitted(true);
        submission4.setSubmissionDate(now.minusDays(7));
        submission4.setParticipation(participation4);

        submissionRepository.save(submission1);
        submissionRepository.save(submission2);
        submissionRepository.save(submission3);
        submissionRepository.save(submission4);

        var exerciseList = new ArrayList<Long>();
        exerciseList.add(exercise.getId());
        var activeStudents = courseService.getActiveStudents(exerciseList);
        assertThat(activeStudents.length).isEqualTo(4);
        assertThat(activeStudents).isEqualTo(new Integer[] { 0, 1, 1, 2 });
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testGetOverviewAsAdmin() {
        // Minimal testcase: Admins always see all courses
        // Add two courses, one not active
        database.addEmptyCourse();
        var inactiveCourse = database.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        courseRepository.save(inactiveCourse);

        // 'addUsers' adds the admin as well
        database.addUsers(0, 0, 0);

        var courses = courseService.getAllCoursesForOverview(false);
        assertThat(courses.size()).isEqualTo(2);

        courses = courseService.getAllCoursesForOverview(true);
        assertThat(courses.size()).isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetOverviewAsInstructor() {
        // Testcase: Instructors see their courses
        // Add three courses, containing one not active and one not belonging to the instructor
        database.addEmptyCourse();
        var inactiveCourse = database.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        inactiveCourse.setInstructorGroupName("test-instructors");
        courseRepository.save(inactiveCourse);
        var instructorsCourse = database.createCourse();
        instructorsCourse.setInstructorGroupName("test-instructors");
        courseRepository.save(instructorsCourse);

        var users = database.addUsers(0, 0, 1);
        var instructor = users.get(0);
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepository.save(instructor);

        var courses = courseService.getAllCoursesForOverview(false);
        assertThat(courses.size()).isEqualTo(2);

        courses = courseService.getAllCoursesForOverview(true);
        assertThat(courses.size()).isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetOverviewAsStudent() {
        // Testcase: Students should not see courses
        // Add three courses, containing one not active and one not belonging to the student
        database.addEmptyCourse();
        var inactiveCourse = database.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        inactiveCourse.setStudentGroupName("test-students");
        courseRepository.save(inactiveCourse);
        var instructorsCourse = database.createCourse();
        instructorsCourse.setStudentGroupName("test-students");
        courseRepository.save(instructorsCourse);

        var users = database.addUsers(1, 0, 0);
        var student = users.get(0);
        var groups = new HashSet<String>();
        groups.add("test-students");
        student.setGroups(groups);
        userRepository.save(student);

        var courses = courseService.getAllCoursesForOverview(false);
        assertThat(courses.size()).isEqualTo(0);

        courses = courseService.getAllCoursesForOverview(true);
        assertThat(courses.size()).isEqualTo(0);
    }
}
