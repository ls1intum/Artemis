package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

class CourseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "courseservice";

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    static IntStream weekRangeProvider() {
        // test all weeks (including the year change from 52 or 53 to 0)
        return IntStream.range(0, 60);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("weekRangeProvider")
    void testGetActiveStudents(long weeks) {
        ZonedDateTime date = ZonedDateTime.now().minusWeeks(weeks);
        SecurityUtils.setAuthorizationObject();
        var course = database.addEmptyCourse();
        var exercise = ModelFactory.generateTextExercise(date, date, date, course);
        course.addExercises(exercise);
        exercise = exerciseRepo.save(exercise);

        var student1 = database.getUserByLogin(TEST_PREFIX + "student1");
        var participation1 = new StudentParticipation();
        participation1.setParticipant(student1);
        participation1.exercise(exercise);
        var student2 = database.getUserByLogin(TEST_PREFIX + "student2");
        var participation2 = new StudentParticipation();
        participation2.setParticipant(student2);
        participation2.exercise(exercise);
        var participation3 = new StudentParticipation();
        participation3.setParticipant(student2);
        participation3.exercise(exercise);
        var participation4 = new StudentParticipation();
        participation4.setParticipant(student2);
        participation4.exercise(exercise);
        studentParticipationRepo.saveAll(Arrays.asList(participation1, participation2, participation3, participation4));

        var submission1 = new TextSubmission();
        submission1.text("text of text submission1");
        submission1.setLanguage(Language.ENGLISH);
        submission1.setSubmitted(true);
        submission1.setParticipation(participation1);
        submission1.setSubmissionDate(date);

        var submission2 = new TextSubmission();
        submission2.text("text of text submission2");
        submission2.setLanguage(Language.ENGLISH);
        submission2.setSubmitted(true);
        submission2.setParticipation(participation2);
        submission2.setSubmissionDate(date);

        var submission3 = new TextSubmission();
        submission3.text("text of text submission3");
        submission3.setLanguage(Language.ENGLISH);
        submission3.setSubmitted(true);
        submission3.setSubmissionDate(date.minusDays(14));
        submission3.setParticipation(participation3);

        var submission4 = new TextSubmission();
        submission4.text("text of text submission4");
        submission4.setLanguage(Language.ENGLISH);
        submission4.setSubmitted(true);
        submission4.setSubmissionDate(date.minusDays(7));
        submission4.setParticipation(participation4);

        submissionRepository.saveAll(Arrays.asList(submission1, submission2, submission3, submission4));

        var exerciseList = new HashSet<Long>();
        exerciseList.add(exercise.getId());
        var activeStudents = courseService.getActiveStudents(exerciseList, 0, 4, date);
        assertThat(activeStudents).hasSize(4).containsExactly(0, 1, 1, 2);
    }

    @Test
    void testGetActiveStudents_UTCConversion() {
        ZonedDateTime date = ZonedDateTime.of(2022, 1, 2, 0, 0, 0, 0, ZonedDateTime.now().getZone());
        SecurityUtils.setAuthorizationObject();
        var course = database.addEmptyCourse();
        var exercise = ModelFactory.generateTextExercise(date, date, date, course);
        course.addExercises(exercise);
        exercise = exerciseRepo.save(exercise);

        var student1 = database.getUserByLogin(TEST_PREFIX + "student1");
        var participation1 = new StudentParticipation();
        participation1.setParticipant(student1);
        participation1.exercise(exercise);

        studentParticipationRepo.save(participation1);

        var submission1 = new TextSubmission();
        submission1.text("text of text submission1");
        submission1.setLanguage(Language.ENGLISH);
        submission1.setSubmitted(true);
        submission1.setParticipation(participation1);
        submission1.setSubmissionDate(date.plusDays(1).plusMinutes(59).plusSeconds(59).plusNanos(59));

        submissionRepository.save(submission1);

        var exerciseList = new HashSet<Long>();
        exerciseList.add(exercise.getId());
        var activeStudents = courseService.getActiveStudents(exerciseList, 0, 4, ZonedDateTime.of(2022, 1, 25, 0, 0, 0, 0, ZoneId.systemDefault()));
        assertThat(activeStudents).hasSize(4).containsExactly(1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOverviewAsAdmin() {
        // Minimal testcase: Admins always see all courses
        // Add two courses, one not active
        var course = database.addEmptyCourse();
        var inactiveCourse = database.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        courseRepository.save(inactiveCourse);

        var courses = courseService.getAllCoursesForManagementOverview(false);
        assertThat(courses).contains(inactiveCourse, course);

        courses = courseService.getAllCoursesForManagementOverview(true);
        assertThat(courses).contains(course);
        assertThat(courses).doesNotContain(inactiveCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetOverviewAsInstructor() {
        // Testcase: Instructors see their courses
        // Add three courses, containing one not active and one not belonging to the instructor
        var course = database.addEmptyCourse();
        var inactiveCourse = database.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        inactiveCourse.setInstructorGroupName("test-instructors");
        courseRepository.save(inactiveCourse);
        var instructorsCourse = database.createCourse();
        instructorsCourse.setInstructorGroupName("test-instructors");
        courseRepository.save(instructorsCourse);

        var instructor = database.getUserByLogin(TEST_PREFIX + "instructor1");
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepository.save(instructor);

        // TODO: investigate why this test fails

        var courses = courseService.getAllCoursesForManagementOverview(false);
        assertThat(courses).contains(instructorsCourse);
        assertThat(courses).contains(inactiveCourse);
        assertThat(courses).doesNotContain(course);

        courses = courseService.getAllCoursesForManagementOverview(true);
        assertThat(courses).contains(instructorsCourse);
        assertThat(courses).doesNotContain(inactiveCourse);
        assertThat(courses).doesNotContain(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOverviewAsStudent() {
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

        var student = database.getUserByLogin(TEST_PREFIX + "student1");
        var groups = new HashSet<String>();
        groups.add("test-students");
        student.setGroups(groups);
        userRepository.save(student);

        var courses = courseService.getAllCoursesForManagementOverview(false);
        assertThat(courses).isEmpty();

        courses = courseService.getAllCoursesForManagementOverview(true);
        assertThat(courses).isEmpty();
    }
}
