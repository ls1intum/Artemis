package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

class CourseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    @Autowired
    private FileService fileService;

    @AfterEach
    void tearDown() {
        database.resetDatabase();
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

        var users = database.addUsers(2, 0, 0, 0);
        var student1 = users.get(0);
        var participation1 = new StudentParticipation();
        participation1.setParticipant(student1);
        participation1.exercise(exercise);
        var student2 = users.get(1);
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

        var users = database.addUsers(2, 0, 0, 0);
        var student1 = users.get(0);
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
        database.addEmptyCourse();
        var inactiveCourse = database.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        courseRepository.save(inactiveCourse);

        // 'addUsers' adds the admin as well
        database.addUsers(0, 0, 0, 0);

        var courses = courseService.getAllCoursesForManagementOverview(false);
        assertThat(courses).hasSize(2);

        courses = courseService.getAllCoursesForManagementOverview(true);
        assertThat(courses).hasSize(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetOverviewAsInstructor() {
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

        var users = database.addUsers(0, 0, 0, 1);
        var instructor = users.get(0);
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepository.save(instructor);

        var courses = courseService.getAllCoursesForManagementOverview(false);
        assertThat(courses).hasSize(2);

        courses = courseService.getAllCoursesForManagementOverview(true);
        assertThat(courses).hasSize(1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
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

        var users = database.addUsers(1, 0, 0, 0);
        var student = users.get(0);
        var groups = new HashSet<String>();
        groups.add("test-students");
        student.setGroups(groups);
        userRepository.save(student);

        var courses = courseService.getAllCoursesForManagementOverview(false);
        assertThat(courses).isEmpty();

        courses = courseService.getAllCoursesForManagementOverview(true);
        assertThat(courses).isEmpty();
    }

    @Test
    void testDeleteIcon() throws IOException {
        byte[] iconBytes = "icon1".getBytes();
        MockMultipartFile iconFile = new MockMultipartFile("file 1", "icon1.png", MediaType.APPLICATION_JSON_VALUE, iconBytes);
        String iconPath = fileService.handleSaveFile(iconFile, false, false);
        Course course = database.addCourseWithIcon(iconPath);

        courseService.deleteIcon(course);

        course = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(course.getCourseIcon()).isNull();

        iconBytes = fileService.getFileForPath(iconPath);
        assertThat(iconBytes).isNull();
    }
}
