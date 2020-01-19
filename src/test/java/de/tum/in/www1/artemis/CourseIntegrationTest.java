package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.CustomAuditEventRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;

public class CourseIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    CustomAuditEventRepository auditEventRepo;

    @Autowired
    private JiraRequestMockProvider jiraRequestMockProvider;

    @Autowired
    UserRepository userRepo;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 5, 1);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCreateCourseWithPermission() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.CREATED);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course got stored").isEqualTo(1);

        course = ModelFactory.generateCourse(1L, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        assertThat(courseRepo.findAll()).as("Course has not been stored").contains(repoContent.toArray(new Course[0]));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDeleteCourseWithPermission() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        for (Course course : courses) {
            request.delete("/api/courses/" + course.getId(), HttpStatus.OK);
        }
        assertThat(courseRepo.findAll()).as("All courses deleted").hasSize(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateCourseWithoutPermission() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.FORBIDDEN);
        assertThat(courseRepo.findAll().size()).as("Course got stored").isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditCourseWithPermission() throws Exception {

        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        course.setShortName("test");
        course.setTitle("Test Course");
        course.setStartDate(ZonedDateTime.now().minusDays(5));
        course.setEndDate(ZonedDateTime.now().plusDays(5));
        Course updatedCourse = request.putWithResponseBody("/api/courses", course, Course.class, HttpStatus.OK);
        assertThat(updatedCourse.getShortName()).as("short name was changed correctly").isEqualTo(course.getShortName());
        assertThat(updatedCourse.getTitle()).as("title was changed correctly").isEqualTo(course.getTitle());
        assertThat(updatedCourse.getStartDate()).as("start date was changed correctly").isEqualTo(course.getStartDate());
        assertThat(updatedCourse.getEndDate()).as("end date was changed correctly").isEqualTo(course.getEndDate());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetCourseWithoutPermission() throws Exception {
        request.getList("/api/courses", HttpStatus.FORBIDDEN, Course.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCoursesWithPermission() throws Exception {
        database.createCoursesWithExercisesAndLectures();
        List<Course> courses = request.getList("/api/courses", HttpStatus.OK, Course.class);
        assertThat(courses.size()).as("All courses are available").isEqualTo(2);
        for (Exercise exercise : courses.get(0).getExercises()) {
            assertThat(exercise.getGradingInstructions()).as("Grading instructions are not filtered out").isNotNull();
            assertThat(exercise.getProblemStatement()).as("Problem statements are not filtered out").isNotNull();
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetAllCoursesForDashboard() throws Exception {
        database.createCoursesWithExercisesAndLectures();

        // Perform the request that is being tested here
        List<Course> courses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);

        // Test that the prepared inactive course was filtered out
        assertThat(courses.size()).as("Inactive course was filtered out").isEqualTo(1);

        // Test that the remaining course has five exercises
        assertThat(courses.get(0).getExercises().size()).as("Five exercises are returned").isEqualTo(5);

        // Iterate over all exercises of the remaining course
        for (Exercise exercise : courses.get(0).getExercises()) {
            // Test that the exercise does not have more than one participation.
            assertThat(exercise.getStudentParticipations().size()).as("At most one participation for exercise").isLessThanOrEqualTo(1);
            if (exercise.getStudentParticipations().size() > 0) {
                // Buffer participation so that null checking is easier.
                Participation participation = exercise.getStudentParticipations().iterator().next();
                if (participation.getSubmissions().size() > 0) {
                    // The call filters participations by submissions and their result. After the call each participation shouldn't have more than one submission.
                    assertThat(participation.getSubmissions().size()).as("At most one submission for participation").isLessThanOrEqualTo(1);
                    Submission submission = participation.getSubmissions().iterator().next();
                    if (submission != null) {
                        // Test that the correct text submission was filtered.
                        if (submission instanceof TextSubmission) {
                            TextSubmission textSubmission = (TextSubmission) submission;
                            assertThat(textSubmission.getText()).as("Correct text submission").isEqualTo("text");
                        }
                        // Test that the correct modeling submission was filtered.
                        if (submission instanceof ModelingSubmission) {
                            ModelingSubmission modelingSubmission = (ModelingSubmission) submission;
                            assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model2");
                        }
                    }
                }
            }
        }
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetCoursesToRegister() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        course1.setRegistrationEnabled(true);

        courseRepo.save(course1);
        courseRepo.save(course2);

        List<Course> courses = request.getList("/api/courses/to-register", HttpStatus.OK, Course.class);
        assertThat(courses.size()).as("One course is available to register").isEqualTo(1);
    }

    private void getCourseForDashboardWithStats(boolean isInstructor) throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures();
        for (Course testCourse : testCourses) {
            Course course = request.get("/api/courses/" + testCourse.getId() + "/for-tutor-dashboard", HttpStatus.OK, Course.class);
            for (Exercise exercise : course.getExercises()) {
                assertThat(exercise.getNumberOfAssessments()).as("Number of assessments is correct").isZero();
                assertThat(exercise.getTutorParticipations().size()).as("Tutor participation was created").isEqualTo(1);
                // Mock data contains exactly one participation for the modeling and text exercise
                if (exercise instanceof ModelingExercise || exercise instanceof TextExercise) {
                    assertThat(exercise.getNumberOfParticipations()).as("Number of participations is correct").isEqualTo(1);
                }
                // Mock data contains no participations for the file upload and programming exercise
                if (exercise instanceof FileUploadExercise || exercise instanceof ProgrammingExercise) {
                    assertThat(exercise.getNumberOfParticipations()).as("Number of participations is correct").isEqualTo(0);
                }
                // Check tutor participation
                if (exercise.getTutorParticipations().size() > 0) {
                    TutorParticipation tutorParticipation = exercise.getTutorParticipations().iterator().next();
                    assertThat(tutorParticipation.getStatus()).as("Tutor participation status is correctly initialized").isEqualTo(TutorParticipationStatus.NOT_PARTICIPATED);
                    assertThat(tutorParticipation.getPoints()).as("Tutor participation points are correctly initialized").isNull();
                }
            }

            StatsForInstructorDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                    StatsForInstructorDashboardDTO.class);
            long numberOfSubmissions = course.getId().equals(testCourses.get(0).getId()) ? 3 : 0; // course 1 has 3 submissions, course 2 has 0 submissions
            assertThat(stats.getNumberOfSubmissions()).as("Number of submissions is correct").isEqualTo(numberOfSubmissions);
            assertThat(stats.getNumberOfAssessments()).as("Number of assessments is correct").isEqualTo(0);
            assertThat(stats.getTutorLeaderboardEntries().size()).as("Number of tutor leaderboard entries is correct").isEqualTo(5);

            StatsForInstructorDashboardDTO stats2 = request.get("/api/courses/" + testCourse.getId() + "/stats-for-instructor-dashboard",
                    isInstructor ? HttpStatus.OK : HttpStatus.FORBIDDEN, StatsForInstructorDashboardDTO.class);

            if (!isInstructor) {
                assertThat(stats2).as("Stats for instructor are not available to tutor").isNull();
            }
            else {
                assertThat(stats2).as("Stats are available for instructor").isNotNull();
                assertThat(stats2).as("Stats for instructor are correct.").isEqualToComparingOnlyGivenFields(stats, "numberOfSubmissions", "numberOfAssessments");
            }
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCourseForTutorDashboardWithStats() throws Exception {
        getCourseForDashboardWithStats(false);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCourseForInstructorDashboardWithStats() throws Exception {
        getCourseForDashboardWithStats(true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures();
        for (Course testCourse : testCourses) {
            Course courseWithExercisesAndRelevantParticipations = request.get("/api/courses/" + testCourse.getId() + "/with-exercises-and-relevant-participations", HttpStatus.OK,
                    Course.class);
            Course courseWithExercises = request.get("/api/courses/" + testCourse.getId() + "/with-exercises", HttpStatus.OK, Course.class);
            Course courseOnly = request.get("/api/courses/" + testCourse.getId(), HttpStatus.OK, Course.class);

            // Check course properties on courseOnly
            assertThat(courseOnly.getStudentGroupName()).as("Student group name is correct").isEqualTo("tumuser");
            assertThat(courseOnly.getTeachingAssistantGroupName()).as("Teaching assistant group name is correct").isEqualTo("tutor");
            assertThat(courseOnly.getInstructorGroupName()).as("Instructor group name is correct").isEqualTo("instructor");
            assertThat(courseOnly.getEndDate()).as("End date is after start date").isAfter(courseOnly.getStartDate());
            assertThat(courseOnly.getMaxComplaints()).as("Max complaints is correct").isEqualTo(5);
            assertThat(courseOnly.getPresentationScore()).as("Presentation score is correct").isEqualTo(2);
            assertThat(courseOnly.getExercises().size()).as("Course without exercises contains no exercises").isZero();

            // Assert that course properties on courseWithExercises and courseWithExercisesAndRelevantParticipations match those of courseOnly
            String[] fields = { "studentGroupName", "teachingAssistantGroupName", "instructorGroupName", "startDate", "endDate", "maxComplaints", "presentationScore" };
            assertThat(courseWithExercises).as("courseWithExercises same as courseOnly").isEqualToComparingOnlyGivenFields(courseOnly, fields);
            assertThat(courseWithExercisesAndRelevantParticipations).as("courseWithExercisesAndRelevantParticipations same as courseOnly")
                    .isEqualToComparingOnlyGivenFields(courseOnly, fields);

            // Verify presence of exercises in mock courses
            // - Course 1 has 5 exercises in total, 4 exercises with relevant participations
            // - Course 2 has 0 exercises in total, 0 exercises with relevant participations
            boolean isFirstCourse = courseOnly.getId().equals(testCourses.get(0).getId());
            long numberOfExercises = isFirstCourse ? 5 : 0;
            long numberOfInterestingExercises = isFirstCourse ? 4 : 0;
            assertThat(courseWithExercises.getExercises().size()).as("Course contains correct number of exercises").isEqualTo(numberOfExercises);
            assertThat(courseWithExercisesAndRelevantParticipations.getExercises().size()).as("Course contains correct number of exercises")
                    .isEqualTo(numberOfInterestingExercises);
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCategoriesInCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures();
        Course course1 = testCourses.get(0);
        Course course2 = testCourses.get(1);
        Set<String> categories1 = request.get("/api/courses/" + course1.getId() + "/categories", HttpStatus.OK, Set.class);
        assertThat(categories1).as("Correct categories in course1").containsExactlyInAnyOrder("Category", "Modeling", "Quiz", "File", "Text", "Programming");
        Set<String> categories2 = request.get("/api/courses/" + course2.getId() + "/categories", HttpStatus.OK, Set.class);
        assertThat(categories2).as("No categories in course2").isEmpty();
    }

    @Test
    @WithMockUser(username = "ab123cd")
    public void testRegisterForCourse() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();
        User student = ModelFactory.generateActivatedUser("ab123cd");
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "instructor");
        course1.setRegistrationEnabled(true);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);
        jiraRequestMockProvider.mockAddUserToGroup(Set.of(course1.getStudentGroupName()));
        jiraRequestMockProvider.mockAddUserToGroup(Set.of(course2.getStudentGroupName()));

        User updatedStudent = request.postWithResponseBody("/api/courses/" + course1.getId() + "/register", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getGroups()).as("User is registered for course").contains(course1.getStudentGroupName());

        List<AuditEvent> auditEvents = auditEventRepo.find("ab123cd", Instant.now().minusSeconds(20), Constants.REGISTER_FOR_COURSE);
        assertThat(auditEvents).as("Audit Event for course registration added").hasSize(1);
        AuditEvent auditEvent = auditEvents.get(0);
        assertThat(auditEvent.getData().get("course")).as("Correct Event Data").isEqualTo(course1.getTitle());

        request.postWithResponseBody("/api/courses/" + course2.getId() + "/register", null, User.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateCourse_withExternalUserManagement_vcsUserManagementHasNotBeenCalled() throws Exception {
        var course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        request.put("/api/courses", course, HttpStatus.OK);

        verifyNoInteractions(versionControlService);
    }
}
