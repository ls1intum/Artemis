package de.tum.in.www1.artemis.util;

import static de.tum.in.www1.artemis.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;

@Service
public class CourseTestService {

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    LectureRepository lectureRepo;

    @Autowired
    ParticipationRepository participationRepo;

    @Autowired
    SubmissionRepository submissionRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    CustomAuditEventRepository auditEventRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    UserService userService;

    @Autowired
    NotificationRepository notificationRepo;

    @Autowired
    ExamRepository examRepo;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected RequestUtilService request;

    private final int numberOfStudents = 8;

    private final int numberOfTutors = 5;

    private final int numberOfInstructors = 1;

    private MockDelegate mockDelegate;

    private GroupNotificationService groupNotificationService;

    public void setup(MockDelegate mockDelegate, GroupNotificationService groupNotificationService) {
        this.mockDelegate = mockDelegate;
        this.groupNotificationService = groupNotificationService;

        database.addUsers(numberOfStudents, numberOfTutors, numberOfInstructors);

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("tutor6"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
    }

    public void tearDown() {
        database.resetDatabase();
    }

    public CourseRepository getCourseRepo() {
        return courseRepo;
    }

    // Test
    public void testCreateCourseWithPermission() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());

        request.post("/api/courses", course, HttpStatus.CREATED);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course got stored").isEqualTo(1);

        course = ModelFactory.generateCourse(1L, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        assertThat(courseRepo.findAll()).as("Course has not been stored").contains(repoContent.toArray(new Course[0]));
    }

    // Test
    public void testCreateCourseWithSameShortName() throws Exception {
        Course course1 = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course1.setShortName("shortName");
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultInstructorGroupName());

        request.post("/api/courses", course1, HttpStatus.CREATED);
        assertThat(courseRepo.findAll().size()).as("Course got stored").isEqualTo(1);

        Course course2 = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course2.setShortName("shortName");
        request.post("/api/courses", course2, HttpStatus.BAD_REQUEST);
        assertThat(courseRepo.findAll().size()).as("Course has not been stored").isEqualTo(1);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplainNumber() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        course.setMaxComplaints(-1);
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course has not been stored").isEqualTo(0);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplainTimeDays() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        course.setMaxComplaintTimeDays(-1);
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course has not been stored").isEqualTo(0);
    }

    // Test
    public void testCreateCourseWithNegativeMaxTeamComplainNumber() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        course.setMaxTeamComplaints(-1);
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course has not been stored").isEqualTo(0);
    }

    // Test
    public void testCreateCourseWithModifiedMaxComplainTimeDaysAndMaxComplains() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        course.setMaxComplaintTimeDays(0);
        course.setMaxComplaints(1);
        course.setMaxTeamComplaints(0);
        course.setMaxRequestMoreFeedbackTimeDays(0);
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course has not been stored").isEqualTo(0);

        // change configuration
        course.setMaxComplaintTimeDays(1);
        course.setMaxComplaints(0);
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course has not been stored").isEqualTo(0);

        // change configuration again
        course.setMaxComplaintTimeDays(0);
        course.setMaxRequestMoreFeedbackTimeDays(-1);
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course has not been stored").isEqualTo(0);
    }

    // Test
    public void testCreateCourseWithCustomNonExistingGroupNames() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setStudentGroupName("StudentGroupName");
        course.setTeachingAssistantGroupName("TeachingAssistantGroupName");
        course.setInstructorGroupName("InstructorGroupName");
        request.post("/api/courses", course, HttpStatus.CREATED);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course got stored").isEqualTo(1);
    }

    // Test
    public void testCreateCourseWithOptions() throws Exception {
        // Generate POST Request Body with maxComplaints = 5, maxComplaintTimeDays = 14, studentQuestionsEnabled = false
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), null, null, null, 5, 5, 14, false, 0);

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        course = request.postWithResponseBody("/api/courses", course, Course.class, HttpStatus.CREATED);
        // Because the courseId is automatically generated we cannot use the findById method to retrieve the saved course.
        Course getFromRepo = courseRepo.findAll().get(0);
        assertThat(getFromRepo.getMaxComplaints()).as("Course has right maxComplaints Value").isEqualTo(5);
        assertThat(getFromRepo.getMaxComplaintTimeDays()).as("Course has right maxComplaintTimeDays Value").isEqualTo(14);
        assertThat(getFromRepo.getStudentQuestionsEnabled()).as("Course has right studentQuestionsEnabled Value").isFalse();
        assertThat(getFromRepo.getRequestMoreFeedbackEnabled()).as("Course has right requestMoreFeedbackEnabled Value").isFalse();

        // Test edit course
        course.setId(getFromRepo.getId());
        course.setMaxComplaints(1);
        course.setMaxComplaintTimeDays(7);
        course.setStudentQuestionsEnabled(true);
        course.setMaxRequestMoreFeedbackTimeDays(7);
        Course updatedCourse = request.putWithResponseBody("/api/courses", course, Course.class, HttpStatus.OK);
        assertThat(updatedCourse.getMaxComplaints()).as("maxComplaints Value updated successfully").isEqualTo(course.getMaxComplaints());
        assertThat(updatedCourse.getMaxComplaintTimeDays()).as("maxComplaintTimeDays Value updated successfully").isEqualTo(course.getMaxComplaintTimeDays());
        assertThat(updatedCourse.getStudentQuestionsEnabled()).as("studentQuestionsEnabled Value updated successfully").isTrue();
        assertThat(updatedCourse.getRequestMoreFeedbackEnabled()).as("Course has right requestMoreFeedbackEnabled Value").isTrue();
    }

    // Test
    public void testDeleteCourseWithPermission() throws Exception {
        // add to new list so that we can add another course with ARTEMIS_GROUP_DEFAULT_PREFIX so that delete group will be tested properly
        List<Course> courses = new ArrayList<>(database.createCoursesWithExercisesAndLectures(true));
        Course course3 = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(4), new HashSet<>(), null, null, null);
        course3.setStudentGroupName(course3.getDefaultStudentGroupName());
        course3.setTeachingAssistantGroupName(course3.getDefaultTeachingAssistantGroupName());
        course3.setInstructorGroupName(course3.getDefaultInstructorGroupName());
        course3 = courseRepo.save(course3);
        courses.add(course3);
        database.addExamWithExerciseGroup(courses.get(0), true);
        // mock certain requests to JIRA Bitbucket and Bamboo
        for (Course course : courses) {
            if (course.getStudentGroupName().startsWith(ARTEMIS_GROUP_DEFAULT_PREFIX)) {
                mockDelegate.mockDeleteGroupInUserManagement(course.getStudentGroupName());
            }
            if (course.getTeachingAssistantGroupName().startsWith(ARTEMIS_GROUP_DEFAULT_PREFIX)) {
                mockDelegate.mockDeleteGroupInUserManagement(course.getTeachingAssistantGroupName());
            }
            if (course.getInstructorGroupName().startsWith(ARTEMIS_GROUP_DEFAULT_PREFIX)) {
                mockDelegate.mockDeleteGroupInUserManagement(course.getInstructorGroupName());
            }
            for (Exercise exercise : course.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    final var programmingExercise = (ProgrammingExercise) exercise;
                    final String projectKey = programmingExercise.getProjectKey();
                    final var templateRepoName = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
                    final var solutionRepoName = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
                    final var testsRepoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);
                    database.addSolutionParticipationForProgrammingExercise(programmingExercise);
                    database.addTemplateParticipationForProgrammingExercise(programmingExercise);
                    mockDelegate.mockDeleteBuildPlan(projectKey, programmingExercise.getTemplateBuildPlanId(), false);
                    mockDelegate.mockDeleteBuildPlan(projectKey, programmingExercise.getSolutionBuildPlanId(), false);
                    mockDelegate.mockDeleteBuildPlanProject(projectKey);
                    mockDelegate.mockDeleteRepository(projectKey, templateRepoName);
                    mockDelegate.mockDeleteRepository(projectKey, solutionRepoName);
                    mockDelegate.mockDeleteRepository(projectKey, testsRepoName);
                    mockDelegate.mockDeleteProjectInVcs(projectKey);
                }
            }
        }

        for (Course course : courses) {
            if (!course.getExercises().isEmpty()) {
                groupNotificationService.notifyStudentGroupAboutExerciseUpdate(course.getExercises().iterator().next(), "notify");
            }
            request.delete("/api/courses/" + course.getId(), HttpStatus.OK);
        }
        assertThat(courseRepo.findAll()).as("All courses deleted").hasSize(0);
        assertThat(notificationRepo.findAll()).as("All notifications are deleted").isEmpty();
        assertThat(examRepo.findAll()).as("All exams are deleted").isEmpty();
        assertThat(exerciseRepo.findAll()).as("All Exercises are deleted").isEmpty();
        assertThat(lectureRepo.findAll()).as("All Lectures are deleted").isEmpty();
    }

    // Test
    public void testDeleteNotExistingCourse() throws Exception {
        request.delete("/api/courses/1", HttpStatus.NOT_FOUND);
    }

    // Test
    public void testCreateCourseWithoutPermission() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.FORBIDDEN);
        assertThat(courseRepo.findAll().size()).as("Course got stored").isEqualTo(0);
    }

    // Test
    public void testCreateCourseWithWrongShortName() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setShortName("`badName~");
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
    }

    // Test
    public void testUpdateCourseWithWrongShortName() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setShortName("`badName~");
        courseRepo.save(course);
        request.put("/api/courses", course, HttpStatus.BAD_REQUEST);
    }

    // Test
    public void testUpdateCourseWithoutId() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        request.put("/api/courses", course, HttpStatus.CREATED);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course got stored").isEqualTo(1);
    }

    // Test
    public void testUpdateCourseIsEmpty() throws Exception {
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>());
        request.put("/api/courses", course, HttpStatus.NOT_FOUND);
    }

    // Test
    public void testEditCourseWithPermission() throws Exception {

        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        course.setTitle("Test Course");
        course.setStartDate(ZonedDateTime.now().minusDays(5));
        course.setEndDate(ZonedDateTime.now().plusDays(5));
        Course updatedCourse = request.putWithResponseBody("/api/courses", course, Course.class, HttpStatus.OK);
        assertThat(updatedCourse.getShortName()).as("short name was changed correctly").isEqualTo(course.getShortName());
        assertThat(updatedCourse.getTitle()).as("title was changed correctly").isEqualTo(course.getTitle());
        assertThat(updatedCourse.getStartDate()).as("start date was changed correctly").isEqualTo(course.getStartDate());
        assertThat(updatedCourse.getEndDate()).as("end date was changed correctly").isEqualTo(course.getEndDate());
    }

    // Test
    public void testUpdateCourseGroups() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        mockDelegate.mockUpdateCoursePermissions(course, oldInstructorGroup, oldTeachingAssistantGroup);
        Course updatedCourse = request.putWithResponseBody("/api/courses", course, Course.class, HttpStatus.OK);

        assertThat(updatedCourse.getInstructorGroupName()).isEqualTo("new-instructor-group");
        assertThat(updatedCourse.getTeachingAssistantGroupName()).isEqualTo("new-ta-group");
    }

    // Test
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToRemoveUser() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        mockDelegate.mockFailUpdateCoursePermissionsInCi(course, oldInstructorGroup, oldTeachingAssistantGroup, false, true);
        Course updatedCourse = request.putWithResponseBody("/api/courses", course, Course.class, HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(updatedCourse).isNull();
    }

    // Test
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToAddUser() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        mockDelegate.mockFailUpdateCoursePermissionsInCi(course, oldInstructorGroup, oldTeachingAssistantGroup, true, false);
        Course updatedCourse = request.putWithResponseBody("/api/courses", course, Course.class, HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(updatedCourse).isNull();
    }

    // Test
    public void testGetCourseWithoutPermission() throws Exception {
        request.getList("/api/courses", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCourse_tutorNotInCourse() throws Exception {
        var courses = database.createCoursesWithExercisesAndLectures(true);
        request.getList("/api/courses/" + courses.get(0).getId(), HttpStatus.FORBIDDEN, Course.class);
        request.get("/api/courses/" + courses.get(0).getId() + "/with-exercises", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCourseWithExercisesAndRelevantParticipationsWithoutPermissions() throws Exception {
        var courses = database.createCoursesWithExercisesAndLectures(true);
        request.get("/api/courses/" + courses.get(0).getId() + "/with-exercises-and-relevant-participations", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCoursesWithPermission() throws Exception {
        database.createCoursesWithExercisesAndLectures(true);
        List<Course> courses = request.getList("/api/courses", HttpStatus.OK, Course.class);
        assertThat(courses.size()).as("All courses are available").isEqualTo(2);
        for (Exercise exercise : courses.get(0).getExercises()) {
            assertThat(exercise.getGradingInstructions()).as("Grading instructions are not filtered out").isNotNull();
            assertThat(exercise.getProblemStatement()).as("Problem statements are not filtered out").isNotNull();
        }
    }

    // Test
    public void testGetCoursesWithQuizExercises() throws Exception {
        database.createCoursesWithExercisesAndLectures(true);
        List<Course> courses = request.getList("/api/courses/courses-with-quiz", HttpStatus.OK, Course.class);
        assertThat(courses.size()).as("All courses are available").isEqualTo(1);
        for (Exercise exercise : courses.get(0).getExercises()) {
            assertThat(exercise.getGradingInstructions()).as("Grading instructions are filtered out").isNull();
            assertThat(exercise.getProblemStatement()).as("Problem statements are filtered out").isNull();
        }
    }

    // Test
    public void testGetCourseForDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLecturesAndLectureUnits(true);
        Course receivedCourse = request.get("/api/courses/" + courses.get(0).getId() + "/for-dashboard", HttpStatus.OK, Course.class);

        // Test that the received course has five exercises
        assertThat(receivedCourse.getExercises().size()).as("Five exercises are returned").isEqualTo(5);
        // Test that the received course has two lectures
        assertThat(receivedCourse.getLectures().size()).as("Two lectures are returned").isEqualTo(2);

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
                            assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model1");
                        }
                    }
                }
            }
        }
    }

    // Test
    public void testGetAllCoursesForDashboard() throws Exception {
        database.createCoursesWithExercisesAndLecturesAndLectureUnits(true);

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
                            assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model1");
                        }
                    }
                }
            }
        }
    }

    // Test
    public void testGetCoursesWithoutActiveExercises() throws Exception {
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepo.save(course);
        List<Course> courses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);
        assertThat(courses.size()).as("Only one course is returned").isEqualTo(1);
        assertThat(courses.stream().findFirst().get().getExercises().size()).as("Course doesn't have any exercises").isEqualTo(0);
    }

    // Test
    public void testGetCoursesAccurateTimezoneEvaluation() throws Exception {
        Course courseActive = ModelFactory.generateCourse(1L, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25), new HashSet<>(), "tumuser", "tutor",
                "instructor");
        Course courseNotActivePast = ModelFactory.generateCourse(2L, ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusMinutes(25), new HashSet<>(), "tumuser", "tutor",
                "instructor");
        Course courseNotActiveFuture = ModelFactory.generateCourse(3L, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusDays(5), new HashSet<>(), "tumuser", "tutor",
                "instructor");
        courseActive = courseRepo.save(courseActive);
        courseRepo.save(courseNotActivePast);
        courseRepo.save(courseNotActiveFuture);
        List<Course> courses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);
        assertThat(courses.size()).as("Exactly one course is returned").isEqualTo(1);
        assertThat(courses.get(0)).as("Active course is returned").isEqualTo(courseActive);
        List<Course> coursesForNotifications = request.getList("/api/courses/for-notifications", HttpStatus.OK, Course.class);
        assertThat(coursesForNotifications.size()).as("Exactly one course is returned").isEqualTo(1);
        assertThat(coursesForNotifications.get(0)).as("Active course is returned").isEqualTo(courseActive);
    }

    // Test
    public void testGetAllCoursesWithUserStats() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        List<Course> receivedCourse = request.getList("/api/courses/with-user-stats", HttpStatus.OK, Course.class);
        assertThat(testCourses).isEqualTo(receivedCourse);
        assertThat(receivedCourse.get(0).getNumberOfStudents()).isEqualTo(numberOfStudents);
        assertThat(receivedCourse.get(0).getNumberOfTeachingAssistants()).isEqualTo(numberOfTutors);
        assertThat(receivedCourse.get(0).getNumberOfInstructors()).isEqualTo(numberOfInstructors);
    }

    // Test
    public void testGetCoursesToRegisterAndAccurateTimeZoneEvaluation() throws Exception {
        Course courseActiveRegistrationEnabled = ModelFactory.generateCourse(1L, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25), new HashSet<>(),
                "tumuser", "tutor", "instructor");
        courseActiveRegistrationEnabled.setRegistrationEnabled(true);
        Course courseActiveRegistrationDisabled = ModelFactory.generateCourse(2L, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25), new HashSet<>(),
                "tumuser", "tutor", "instructor");
        courseActiveRegistrationDisabled.setRegistrationEnabled(false);
        Course courseNotActivePast = ModelFactory.generateCourse(3L, ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusMinutes(25), new HashSet<>(), "tumuser", "tutor",
                "instructor");
        Course courseNotActiveFuture = ModelFactory.generateCourse(4L, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusDays(5), new HashSet<>(), "tumuser", "tutor",
                "instructor");
        courseRepo.save(courseActiveRegistrationEnabled);
        courseRepo.save(courseActiveRegistrationDisabled);
        courseRepo.save(courseNotActivePast);
        courseRepo.save(courseNotActiveFuture);

        List<Course> courses = request.getList("/api/courses/to-register", HttpStatus.OK, Course.class);
        assertThat(courses.size()).as("Exactly one course is available to register").isEqualTo(1);
        courses.get(0).setId(courseActiveRegistrationEnabled.getId());
        assertThat(courses.get(0)).as("Only active course is returned").isEqualTo(courseActiveRegistrationEnabled);
    }

    private void getCourseForDashboardWithStats(boolean isInstructor) throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        for (Course testCourse : testCourses) {
            Course course = request.get("/api/courses/" + testCourse.getId() + "/for-assessment-dashboard", HttpStatus.OK, Course.class);
            for (Exercise exercise : course.getExercises()) {
                assertThat(exercise.getTotalNumberOfAssessments().getInTime()).as("Number of in-time assessments is correct").isZero();
                assertThat(exercise.getTotalNumberOfAssessments().getLate()).as("Number of late assessments is correct").isZero();
                assertThat(exercise.getTutorParticipations().size()).as("Tutor participation was created").isEqualTo(1);
                // Mock data contains exactly two submissions for the modeling exercise
                if (exercise instanceof ModelingExercise) {
                    assertThat(exercise.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions is correct").isEqualTo(2);
                }
                // Mock data contains exactly one submission for the text exercise
                if (exercise instanceof TextExercise) {
                    assertThat(exercise.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions is correct").isEqualTo(1);
                }
                // Mock data contains no submissions for the file upload and programming exercise
                if (exercise instanceof FileUploadExercise || exercise instanceof ProgrammingExercise) {
                    assertThat(exercise.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions is correct").isEqualTo(0);
                }

                assertThat(exercise.getNumberOfSubmissions().getLate()).as("Number of late submissions is correct").isEqualTo(0);
                assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds().length).isEqualTo(1L);
                assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].getInTime()).isEqualTo(0L);
                // Check tutor participation
                if (exercise.getTutorParticipations().size() > 0) {
                    TutorParticipation tutorParticipation = exercise.getTutorParticipations().iterator().next();
                    assertThat(tutorParticipation.getStatus()).as("Tutor participation status is correctly initialized").isEqualTo(TutorParticipationStatus.NOT_PARTICIPATED);
                }
            }

            StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            long numberOfInTimeSubmissions = course.getId().equals(testCourses.get(0).getId()) ? 3 : 0; // course 1 has 3 submissions, course 2 has 0 submissions
            assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions is correct").isEqualTo(numberOfInTimeSubmissions);
            assertThat(stats.getNumberOfSubmissions().getLate()).as("Number of latte submissions is correct").isEqualTo(0);
            assertThat(stats.getTotalNumberOfAssessments().getInTime()).as("Number of in-time assessments is correct").isEqualTo(0);
            assertThat(stats.getTotalNumberOfAssessments().getLate()).as("Number of late assessments is correct").isEqualTo(0);
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds().length).isEqualTo(1L);
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].getInTime()).isEqualTo(0L);
            assertThat(stats.getTutorLeaderboardEntries().size()).as("Number of tutor leaderboard entries is correct").isEqualTo(5);

            StatsForDashboardDTO stats2 = request.get("/api/courses/" + testCourse.getId() + "/stats-for-instructor-dashboard", isInstructor ? HttpStatus.OK : HttpStatus.FORBIDDEN,
                    StatsForDashboardDTO.class);

            if (!isInstructor) {
                assertThat(stats2).as("Stats for instructor are not available to tutor").isNull();
            }
            else {
                assertThat(stats2).as("Stats are available for instructor").isNotNull();
                assertThat(stats2.getNumberOfSubmissions()).as("Submission stats for instructor are correct.").usingRecursiveComparison().isEqualTo(stats.getNumberOfSubmissions());
                assertThat(stats2.getTotalNumberOfAssessments()).as("Assessment stats for instructor are correct.").usingRecursiveComparison()
                        .isEqualTo(stats.getTotalNumberOfAssessments());
            }
        }
    }

    // Test
    public void testGetCourseForAssessmentDashboardWithStats() throws Exception {
        getCourseForDashboardWithStats(false);
    }

    // Test
    public void testGetCourseForInstructorDashboardWithStats() throws Exception {
        getCourseForDashboardWithStats(true);
    }

    // Test
    public void testGetCourseForInstructorDashboardWithStats_instructorNotInCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        request.get("/api/courses/" + testCourses.get(0).getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
        request.get("/api/courses/" + testCourses.get(0).getId() + "/stats-for-instructor-dashboard", HttpStatus.FORBIDDEN, StatsForDashboardDTO.class);
    }

    // Test
    public void testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        request.get("/api/courses/" + testCourses.get(0).getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
        request.get("/api/courses/" + testCourses.get(0).getId() + "/stats-for-assessment-dashboard", HttpStatus.FORBIDDEN, StatsForDashboardDTO.class);
    }

    // Test
    public void testGetAssessmentDashboardStats_withoutAssessments() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        // create 6 * 4 = 24 submissions
        Course testCourse = database.addCourseWithExercisesAndSubmissions(6, 4, 0, 0, true, 0, validModel);

        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);

        var currentTutorLeaderboard = stats.getTutorLeaderboardEntries().get(0);
        assertThat(currentTutorLeaderboard.getNumberOfTutorComplaints()).isEqualTo(0);
        assertThat(currentTutorLeaderboard.getNumberOfAcceptedComplaints()).isEqualTo(0);
        assertThat(currentTutorLeaderboard.getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(currentTutorLeaderboard.getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(0);
        assertThat(currentTutorLeaderboard.getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(0);
        assertThat(currentTutorLeaderboard.getNumberOfTutorMoreFeedbackRequests()).isEqualTo(0);
        assertThat(currentTutorLeaderboard.getNumberOfAssessments()).isEqualTo(0);
        assertThat(currentTutorLeaderboard.getPoints()).isEqualTo(0);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessments() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        Course testCourse = database.addCourseWithExercisesAndSubmissions(6, 4, 2, 0, true, 0, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAssessments()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAssessments()).isEqualTo(2);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaints() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        Course testCourse = database.addCourseWithExercisesAndSubmissions(6, 4, 4, 2, true, 0, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAssessments()).isEqualTo(4);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorComplaints()).isEqualTo(2);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(0);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        Course testCourse = database.addCourseWithExercisesAndSubmissions(6, 4, 4, 2, false, 0, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAssessments()).isEqualTo(4);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorComplaints()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorComplaints()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorComplaints()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorComplaints()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        Course testCourse = database.addCourseWithExercisesAndSubmissions(6, 4, 4, 2, true, 1, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAssessments()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfTutorComplaints()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAcceptedComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAcceptedComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAcceptedComplaints()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAcceptedComplaints()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAcceptedComplaints()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfComplaintResponses()).isEqualTo(6);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        Course testCourse = database.addCourseWithExercisesAndSubmissions(6, 4, 4, 2, false, 1, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAssessments()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAssessments()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(0);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        Course testCourse = database.addCourseWithExercisesAndSubmissions(9, 8, 8, 5, true, 5, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        // the first two tutors did assess 8 submissions of 3 exercises. The rest two only 8 of two exercises.
        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAssessments()).isEqualTo(3 * 8);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAssessments()).isEqualTo(2 * 8);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAssessments()).isEqualTo(2 * 8);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAssessments()).isEqualTo(2 * 8);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAssessments()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorComplaints()).isEqualTo(3 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfTutorComplaints()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAcceptedComplaints()).isEqualTo(3 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAcceptedComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAcceptedComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAcceptedComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAcceptedComplaints()).isEqualTo(0);

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfComplaintResponses()).isEqualTo(0);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfComplaintResponses()).isEqualTo(0);
        // 9 exercises, for each one there are 5 complaintResponese
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfComplaintResponses()).isEqualTo(9 * 5);
    }

    // Test
    public void testGetCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
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
            assertThat(courseOnly.getMaxComplaints()).as("Max complaints is correct").isEqualTo(3);
            assertThat(courseOnly.getPresentationScore()).as("Presentation score is correct").isEqualTo(2);
            assertThat(courseOnly.getExercises().size()).as("Course without exercises contains no exercises").isZero();
            assertThat(courseOnly.getNumberOfStudents()).as("Amount of students is correct").isEqualTo(8);
            assertThat(courseOnly.getNumberOfTeachingAssistants()).as("Amount of teaching assistants is correct").isEqualTo(5);
            assertThat(courseOnly.getNumberOfInstructors()).as("Amount of instructors is correct").isEqualTo(1);

            // Assert that course properties on courseWithExercises and courseWithExercisesAndRelevantParticipations match those of courseOnly
            String[] ignoringFields = { "exercises", "tutorGroups", "lectures", "exams", "fileService", "numberOfInstructorsTransient", "numberOfStudentsTransient",
                    "numberOfTeachingAssistantsTransient" };
            assertThat(courseWithExercises).as("courseWithExercises same as courseOnly").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(courseOnly);
            assertThat(courseWithExercisesAndRelevantParticipations).as("courseWithExercisesAndRelevantParticipations same as courseOnly").usingRecursiveComparison()
                    .ignoringFields(ignoringFields).isEqualTo(courseOnly);

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

    // Test
    public void testGetCategoriesInCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        Course course1 = testCourses.get(0);
        Course course2 = testCourses.get(1);
        Set<String> categories1 = request.get("/api/courses/" + course1.getId() + "/categories", HttpStatus.OK, Set.class);
        assertThat(categories1).as("Correct categories in course1").containsExactlyInAnyOrder("Category", "Modeling", "Quiz", "File", "Text", "Programming");
        Set<String> categories2 = request.get("/api/courses/" + course2.getId() + "/categories", HttpStatus.OK, Set.class);
        assertThat(categories2).as("No categories in course2").isEmpty();
    }

    // Test
    public void testGetCategoriesInCourse_instructorNotInCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        request.get("/api/courses/" + testCourses.get(0).getId() + "/categories", HttpStatus.FORBIDDEN, Set.class);
    }

    // Test
    public void testRegisterForCourse() throws Exception {
        User student = ModelFactory.generateActivatedUser("ab12cde");
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "instructor");
        course1.setRegistrationEnabled(true);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);

        mockDelegate.mockAddUserToGroupInUserManagement(student, course1.getStudentGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(student, course2.getStudentGroupName(), false);

        User updatedStudent = request.postWithResponseBody("/api/courses/" + course1.getId() + "/register", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getGroups()).as("User is registered for course").contains(course1.getStudentGroupName());

        List<AuditEvent> auditEvents = auditEventRepo.find("ab12cde", Instant.now().minusSeconds(20), Constants.REGISTER_FOR_COURSE);
        assertThat(auditEvents).as("Audit Event for course registration added").hasSize(1);
        AuditEvent auditEvent = auditEvents.get(0);
        assertThat(auditEvent.getData().get("course")).as("Correct Event Data").isEqualTo(course1.getTitle());

        request.postWithResponseBody("/api/courses/" + course2.getId() + "/register", null, User.class, HttpStatus.BAD_REQUEST);
    }

    // Test
    public void testRegisterForCourse_notMeetsDate() throws Exception {
        User student = ModelFactory.generateActivatedUser("ab12cde");
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course notYetStartedCourse = ModelFactory.generateCourse(null, futureTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "instructor");
        Course finishedCourse = ModelFactory.generateCourse(null, pastTimestamp, pastTimestamp, new HashSet<>(), "testcourse2", "tutor", "instructor");
        notYetStartedCourse.setRegistrationEnabled(true);

        notYetStartedCourse = courseRepo.save(notYetStartedCourse);
        finishedCourse = courseRepo.save(finishedCourse);

        mockDelegate.mockAddUserToGroupInUserManagement(student, notYetStartedCourse.getStudentGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(student, finishedCourse.getStudentGroupName(), false);

        request.post("/api/courses/" + notYetStartedCourse.getId() + "/register", User.class, HttpStatus.BAD_REQUEST);
        request.post("/api/courses/" + finishedCourse.getId() + "/register", User.class, HttpStatus.BAD_REQUEST);
    }

    // Test
    public void testUpdateCourse_instructorNotInCourse() throws Exception {
        var course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        request.put("/api/courses", course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        // Get all students for course
        List<User> students = request.getList("/api/courses/" + course.getId() + "/students", HttpStatus.OK, User.class);
        assertThat(students).as("All students in course found").hasSize(numberOfStudents);

        // Get all tutors for course
        List<User> tutors = request.getList("/api/courses/" + course.getId() + "/tutors", HttpStatus.OK, User.class);
        assertThat(tutors).as("All tutors in course found").hasSize(numberOfTutors);

        // Get all instructors for course
        List<User> instructors = request.getList("/api/courses/" + course.getId() + "/instructors", HttpStatus.OK, User.class);
        assertThat(instructors).as("All instructors in course found").hasSize(numberOfInstructors);
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-instructor");
        course = courseRepo.save(course);
        testGetAllStudentsOrTutorsOrInstructorsInCourse__forbidden(course);
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        testGetAllStudentsOrTutorsOrInstructorsInCourse__forbidden(course);
    }

    private void testGetAllStudentsOrTutorsOrInstructorsInCourse__forbidden(Course course) throws Exception {
        request.getList("/api/courses/" + course.getId() + "/students", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/courses/" + course.getId() + "/tutors", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/courses/" + course.getId() + "/instructors", HttpStatus.FORBIDDEN, User.class);
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrInstructorToCourse(course, HttpStatus.OK);

        // TODO check that the roles have changed accordingly
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrInstructorToCourse(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrInstructorToCourse(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/students/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
    }

    private void testAddStudentOrTutorOrInstructorToCourse(Course course, HttpStatus httpStatus) throws Exception {
        var student = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student1").get();
        var tutor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("tutor1").get();
        var instructor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("instructor1").get();

        mockDelegate.mockAddUserToGroupInUserManagement(student, course.getStudentGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(tutor1, course.getTeachingAssistantGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(instructor1, course.getInstructorGroupName(), false);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/students/student1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/tutor1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/instructor1", null, httpStatus, null);
    }

    // Test
    public void testAddTutorAndInstructorToCourse_failsToAddUserToGroup(HttpStatus expectedFailureCode) throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        database.addProgrammingExerciseToCourse(course, false);
        course = courseRepo.save(course);

        var tutor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("tutor1").get();
        var instructor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("instructor1").get();

        mockDelegate.mockAddUserToGroupInUserManagement(tutor1, course.getTeachingAssistantGroupName(), true);
        mockDelegate.mockAddUserToGroupInUserManagement(instructor1, course.getInstructorGroupName(), true);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/tutor1", null, expectedFailureCode, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/instructor1", null, expectedFailureCode, null);
    }

    // Test
    public void testRemoveTutorFromCourse_failsToRemoveUserFromGroup() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        database.addProgrammingExerciseToCourse(course, false);
        course = courseRepo.save(course);

        User tutor = userRepo.findOneWithGroupsByLogin("tutor1").get();
        mockDelegate.mockRemoveUserFromGroup(tutor, course.getTeachingAssistantGroupName(), true);
        request.delete("/api/courses/" + course.getId() + "/tutors/" + tutor.getLogin(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrInstructorFromCourse_forbidden(course, HttpStatus.OK);
        // TODO check that the roles have changed accordingly
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse_WithNonExistingUser() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        request.delete("/api/courses/" + course.getId() + "/students/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/tutors/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/instructors/maxMustermann", HttpStatus.NOT_FOUND);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrInstructorFromCourse_forbidden(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrInstructorFromCourse_forbidden(course, HttpStatus.FORBIDDEN);
    }

    private void testRemoveStudentOrTutorOrInstructorFromCourse_forbidden(Course course, HttpStatus httpStatus) throws Exception {
        // Retrieve users from whom to remove groups
        User student = userRepo.findOneWithGroupsByLogin("student1").get();
        User tutor = userRepo.findOneWithGroupsByLogin("tutor1").get();
        User instructor = userRepo.findOneWithGroupsByLogin("instructor1").get();

        // Mock remove requests
        mockDelegate.mockRemoveUserFromGroup(student, course.getStudentGroupName(), false);
        mockDelegate.mockRemoveUserFromGroup(tutor, course.getTeachingAssistantGroupName(), false);
        mockDelegate.mockRemoveUserFromGroup(instructor, course.getInstructorGroupName(), false);

        // Remove users from their group
        request.delete("/api/courses/" + course.getId() + "/students/" + student.getLogin(), httpStatus);
        request.delete("/api/courses/" + course.getId() + "/tutors/" + tutor.getLogin(), httpStatus);
        request.delete("/api/courses/" + course.getId() + "/instructors/" + instructor.getLogin(), httpStatus);
    }

    // Test
    public void testGetLockedSubmissionsForCourseAsTutor() throws Exception {
        Course course = database.addCourseWithDifferentModelingExercises();
        ModelingExercise classExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");

        List<Submission> lockedSubmissions = request.get("/api/courses/" + course.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).as("Locked Submissions is not null").isNotNull();
        assertThat(lockedSubmissions).as("Locked Submissions length is 0").hasSize(0);

        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student1", "tutor1");

        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student2", "tutor1");

        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student3", "tutor1");

        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).as("Locked Submissions is not null").isNotNull();
        assertThat(lockedSubmissions).as("Locked Submissions length is 3").hasSize(3);
    }

    // Test
    public void testGetLockedSubmissionsForCourseAsStudent() throws Exception {
        List<Submission> lockedSubmissions = request.get("/api/courses/1/lockedSubmissions", HttpStatus.FORBIDDEN, List.class);
        assertThat(lockedSubmissions).as("Locked Submissions is null").isNull();
    }

    // Test
    public void testArchiveCourseAsStudent_forbidden() throws Exception {
        request.put("/api/courses/" + 1 + "/archive", null, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testArchiveCourseAsTutor_forbidden() throws Exception {
        request.put("/api/courses/" + 1 + "/archive", null, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testArchiveCourseWithTestModelingAndFileUploadExercises() throws Exception {
        Course course = database.addCourseWithModelingAndTextAndFileUploadExercise();
        course.setEndDate(ZonedDateTime.now().minusMinutes(5));
        course = courseRepo.save(course);

        // Generate submissions
        var fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "FileUpload");
        var fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");

        var textExercise = database.findTextExerciseWithTitle(course.getExercises(), "Text");
        var textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");

        var modelingExercise = database.findModelingExerciseWithTitle(course.getExercises(), "Modeling");
        database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        String emptyActivityModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyActivityModel, false);
        database.addSubmission(modelingExercise, submission, "student1");

        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);

        final var courseId = course.getId();
        await().until(() -> courseRepo.findById(courseId).get().getCourseArchivePath() != null);

        var updatedCourse = courseRepo.findById(courseId).get();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    // Test
    public void testDownloadCourseArchiveAsStudent_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    // Test
    public void testDownloadCourseArchiveAsTutor_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    // Test
    public void testDownloadCourseArchiveAsInstructor_not_found() throws Exception {
        // Generate a course that has no archive and assert that an 404 status is thrown
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        var downloadedArchive = request.get("/api/courses/" + course.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    // Test
    public void testDownloadCourseArchiveAsInstructor() throws Exception {

        // Dummy course archive
        Path courseArchivePath = Path.of(courseArchivesDirPath, "some-course-archive.zip");
        if (!Files.exists(courseArchivePath)) {
            Files.createDirectories(Path.of(courseArchivesDirPath));
            Files.createFile(courseArchivePath);
        }

        // Generate a course that has an archive
        Course course = database.createCourse();
        course.setCourseArchivePath(courseArchivePath.toString());
        course = courseRepo.save(course);

        var downloadedArchive = request.get("/api/courses/" + course.getId() + "/download-archive", HttpStatus.OK, String.class);
        assertThat(downloadedArchive).isNotNull();

        Files.delete(courseArchivePath);
    }

    // Test
    public void testCleanupCourseAsStudent_forbidden() throws Exception {
        request.delete("/api/courses/" + 1 + "/cleanup", HttpStatus.FORBIDDEN);
    }

    // Test
    public void testCleanupCourseAsTutor_forbidden() throws Exception {
        request.delete("/api/courses/" + 1 + "/cleanup", HttpStatus.FORBIDDEN);
    }

    // Test
    public void testCleanupCourseAsInstructor_no_Archive() throws Exception {
        // Generate a course that has an archive
        Course course = courseRepo.save(database.createCourse());

        request.delete("/api/courses/" + course.getId() + "/cleanup", HttpStatus.BAD_REQUEST);
    }

    // Test
    public void testCleanupCourseAsInstructor() throws Exception {
        // Generate a course that has an archive
        var course = database.addCourseWithOneProgrammingExercise(false, ProgrammingLanguage.JAVA);
        course.setCourseArchivePath("some-archive-path");
        courseRepo.save(course);

        var programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

        mockDelegate.mockDeleteRepository(programmingExercise.getProjectKey(), (programmingExercise.getProjectKey()).toLowerCase() + "-student1");
        var buildPlanId = (programmingExercise.getProjectKey() + "-student1").toUpperCase();
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), buildPlanId, false);
        request.delete("/api/courses/" + course.getId() + "/cleanup", HttpStatus.OK);

        course.getExercises().forEach(exercise -> {
            var exerciseWithParticipations = exerciseRepo.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).get();
            if (exercise instanceof ProgrammingExercise) {
                for (StudentParticipation participation : exerciseWithParticipations.getStudentParticipations()) {
                    ProgrammingExerciseStudentParticipation programmingExerciseParticipation = (ProgrammingExerciseStudentParticipation) participation;
                    assertThat(programmingExerciseParticipation.getBuildPlanId()).as("Build plan id has been removed").isNull();
                }
            }

            // TODO: Assert the other exercises after it's implemented
        });
    }
}
