package de.tum.in.www1.artemis.util;

import static de.tum.in.www1.artemis.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementDetailViewDTO;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementOverviewStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentUpdateDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class CourseTestService {

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private CustomAuditEventRepository auditEventRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private NotificationRepository notificationRepo;

    @Autowired
    private ExamRepository examRepo;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private CourseExamExportService courseExamExportService;

    @Autowired
    private ZipFileService zipFileService;

    @Autowired
    private FileService fileService;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private GroupNotificationService groupNotificationService;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final static int numberOfStudents = 8;

    private final static int numberOfTutors = 5;

    private final static int numberOfEditors = 1;

    private final static int numberOfInstructors = 1;

    private MockDelegate mockDelegate;

    public void setup(MockDelegate mockDelegate) {
        this.mockDelegate = mockDelegate;

        database.addUsers(numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("tutor6"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
    }

    public void tearDown() {
        database.resetDatabase();
    }

    // Test
    public void testCreateCourseWithPermission() throws Exception {
        assertThrows(EntityNotFoundException.class, () -> courseRepo.findByIdWithLecturesAndExamsElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> courseRepo.findByIdElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> courseRepo.findByIdWithExercisesAndLecturesElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> courseRepo.findWithEagerOrganizationsElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> courseRepo.findByIdWithEagerExercisesElseThrow(Long.MAX_VALUE));

        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());

        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isCreated());
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent).as("Course got stored").hasSize(1);

        course = ModelFactory.generateCourse(1L, null, null, new HashSet<>());
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
        assertThat(courseRepo.findAll()).as("Course has not been stored").contains(repoContent.toArray(new Course[0]));
    }

    // Test
    public void testCreateCourseWithSameShortName() throws Exception {
        Course course1 = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course1.setShortName("shortName");
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultInstructorGroupName());

        request.getMvc().perform(buildCreateCourse(course1)).andExpect(status().isCreated());
        assertThat(courseRepo.findAll()).as("Course got stored").hasSize(1);

        Course course2 = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course2.setShortName("shortName");
        request.getMvc().perform(buildCreateCourse(course2)).andExpect(status().isBadRequest());
        assertThat(courseRepo.findAll()).as("Course has not been stored").hasSize(1);
    }

    // Test
    private void testCreateCourseWithNegativeValue(Course course) throws Exception {
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsString(course).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/admin/courses").file(coursePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.getMvc().perform(builder).andExpect(status().isBadRequest());
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent).as("Course has not been stored").isEmpty();
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplainNumber() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaints(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplainTimeDays() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaintTimeDays(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxTeamComplainNumber() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxTeamComplaints(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplaintTextLimit() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaintTextLimit(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplaintResponseTextLimit() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaintResponseTextLimit(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithModifiedMaxComplainTimeDaysAndMaxComplains() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        course.setMaxComplaintTimeDays(0);
        course.setMaxComplaints(1);
        course.setMaxTeamComplaints(0);
        course.setMaxRequestMoreFeedbackTimeDays(0);
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent).as("Course has not been stored").isEmpty();

        // change configuration
        course.setMaxComplaintTimeDays(1);
        course.setMaxComplaints(0);
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
        repoContent = courseRepo.findAll();
        assertThat(repoContent).as("Course has not been stored").isEmpty();

        // change configuration again
        course.setMaxComplaintTimeDays(0);
        course.setMaxRequestMoreFeedbackTimeDays(-1);
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
        repoContent = courseRepo.findAll();
        assertThat(repoContent).as("Course has not been stored").isEmpty();
    }

    // Test
    public void testCreateCourseWithCustomNonExistingGroupNames() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setStudentGroupName("StudentGroupName");
        course.setTeachingAssistantGroupName("TeachingAssistantGroupName");
        course.setEditorGroupName("EditorGroupName");
        course.setInstructorGroupName("InstructorGroupName");

        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isCreated());
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent).as("Course got stored").hasSize(1);
    }

    // Test
    public void testCreateCourseWithOptions() throws Exception {
        // Generate POST Request Body with maxComplaints = 5, maxComplaintTimeDays = 14, postsEnabled = false
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), null, null, null, null, 5, 5, 14, 2000, 2000, false, 0);

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        MvcResult result = request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        course = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        // Because the courseId is automatically generated we cannot use the findById method to retrieve the saved course.
        Course getFromRepo = courseRepo.findAll().get(0);
        assertThat(getFromRepo.getMaxComplaints()).as("Course has right maxComplaints Value").isEqualTo(5);
        assertThat(getFromRepo.getMaxComplaintTimeDays()).as("Course has right maxComplaintTimeDays Value").isEqualTo(14);
        assertThat(getFromRepo.getPostsEnabled()).as("Course has right postsEnabled Value").isFalse();
        assertThat(getFromRepo.getRequestMoreFeedbackEnabled()).as("Course has right requestMoreFeedbackEnabled Value").isFalse();

        // Test edit course
        course.setId(getFromRepo.getId());
        course.setMaxComplaints(1);
        course.setMaxComplaintTimeDays(7);
        course.setPostsEnabled(true);
        course.setMaxRequestMoreFeedbackTimeDays(7);
        result = request.getMvc().perform(buildUpdateCourse(getFromRepo.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        assertThat(updatedCourse.getMaxComplaints()).as("maxComplaints Value updated successfully").isEqualTo(course.getMaxComplaints());
        assertThat(updatedCourse.getMaxComplaintTimeDays()).as("maxComplaintTimeDays Value updated successfully").isEqualTo(course.getMaxComplaintTimeDays());
        assertThat(updatedCourse.getPostsEnabled()).as("postsEnabled Value updated successfully").isTrue();
        assertThat(updatedCourse.getRequestMoreFeedbackEnabled()).as("Course has right requestMoreFeedbackEnabled Value").isTrue();
    }

    // Test
    public void testDeleteCourseWithPermission() throws Exception {
        // add to new list so that we can add another course with ARTEMIS_GROUP_DEFAULT_PREFIX so that delete group will be tested properly
        List<Course> courses = new ArrayList<>(database.createCoursesWithExercisesAndLectures(true));
        Course course3 = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(4), new HashSet<>(), null, null, null, null);
        course3.setStudentGroupName(course3.getDefaultStudentGroupName());
        course3.setTeachingAssistantGroupName(course3.getDefaultTeachingAssistantGroupName());
        course3.setEditorGroupName(course3.getDefaultEditorGroupName());
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
            if (course.getEditorGroupName().startsWith(ARTEMIS_GROUP_DEFAULT_PREFIX)) {
                mockDelegate.mockDeleteGroupInUserManagement(course.getEditorGroupName());
            }
            if (course.getInstructorGroupName().startsWith(ARTEMIS_GROUP_DEFAULT_PREFIX)) {
                mockDelegate.mockDeleteGroupInUserManagement(course.getInstructorGroupName());
            }
            for (Exercise exercise : course.getExercises()) {
                if (exercise instanceof final ProgrammingExercise programmingExercise) {
                    final String projectKey = programmingExercise.getProjectKey();
                    final var templateRepoName = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
                    final var solutionRepoName = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
                    final var testsRepoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);
                    database.addSolutionParticipationForProgrammingExercise(programmingExercise);
                    database.addTemplateParticipationForProgrammingExercise(programmingExercise);
                    mockDelegate.mockDeleteBuildPlan(projectKey, programmingExercise.getTemplateBuildPlanId(), false);
                    mockDelegate.mockDeleteBuildPlan(projectKey, programmingExercise.getSolutionBuildPlanId(), false);
                    mockDelegate.mockDeleteBuildPlanProject(projectKey, false);
                    mockDelegate.mockDeleteRepository(projectKey, templateRepoName, false);
                    mockDelegate.mockDeleteRepository(projectKey, solutionRepoName, false);
                    mockDelegate.mockDeleteRepository(projectKey, testsRepoName, false);
                    mockDelegate.mockDeleteProjectInVcs(projectKey, false);
                }
            }
        }

        for (Course course : courses) {
            if (!course.getExercises().isEmpty()) {
                groupNotificationService.notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(course.getExercises().iterator().next(), "notify");
            }
            request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);
        }
        assertThat(courseRepo.findAll()).as("All courses deleted").isEmpty();
        assertThat(notificationRepo.findAll()).as("All notifications are deleted").isEmpty();
        assertThat(examRepo.findAll()).as("All exams are deleted").isEmpty();
        assertThat(exerciseRepo.findAll()).as("All Exercises are deleted").isEmpty();
        assertThat(lectureRepo.findAll()).as("All Lectures are deleted").isEmpty();
    }

    // Test
    public void testDeleteNotExistingCourse() throws Exception {
        request.delete("/api/admin/courses/1", HttpStatus.NOT_FOUND);
    }

    // Test
    public void testCreateCourseWithoutPermission() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isForbidden());
        assertThat(courseRepo.findAll()).as("Course got stored").isEmpty();
    }

    // Test
    public void testCreateCourseWithWrongShortName() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        course.setShortName("`badName~");
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testUpdateCourseIsEmpty() throws Exception {
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>());
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testEditCourseWithPermission() throws Exception {
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        course.setTitle("Test Course");
        course.setStartDate(ZonedDateTime.now().minusDays(5));
        course.setEndDate(ZonedDateTime.now().plusDays(5));
        MvcResult result = request.getMvc().perform(buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        assertThat(updatedCourse.getShortName()).as("short name was changed correctly").isEqualTo(course.getShortName());
        assertThat(updatedCourse.getTitle()).as("title was changed correctly").isEqualTo(course.getTitle());
        assertThat(updatedCourse.getStartDate()).as("start date was changed correctly").isEqualTo(course.getStartDate());
        assertThat(updatedCourse.getEndDate()).as("end date was changed correctly").isEqualTo(course.getEndDate());
    }

    // Test
    public void testEditCourseShouldPreserveAssociations() throws Exception {
        Course course = database.createCourseWithOrganizations();
        course = courseRepo.save(course);

        Set<Organization> organizations = course.getOrganizations();

        Set<LearningGoal> learningGoals = new HashSet<>();
        learningGoals.add(database.createLearningGoal(course));
        course.setLearningGoals(learningGoals);
        course = courseRepo.save(course);

        Set<LearningGoal> prerequisites = new HashSet<>();
        prerequisites.add(database.createLearningGoal(database.createCourse()));
        course.setPrerequisites(prerequisites);
        course = courseRepo.save(course);

        request.getMvc().perform(buildUpdateCourse(course.getId(), course)).andExpect(status().isOk());

        Course updatedCourse = courseRepo.findByIdWithOrganizationsAndLearningGoalsElseThrow(course.getId());
        assertThat(updatedCourse.getOrganizations()).containsExactlyElementsOf(organizations);
        assertThat(updatedCourse.getLearningGoals()).containsExactlyElementsOf(learningGoals);
        assertThat(updatedCourse.getPrerequisites()).containsExactlyElementsOf(prerequisites);
    }

    // Test
    public void testUpdateCourseGroups() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldEditorGroup = course.getEditorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setEditorGroupName("new-editor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        // Create instructor in the course
        User user = ModelFactory.generateActivatedUser("instructor11");
        user.setGroups(Set.of("new-instructor-group"));
        userRepo.save(user);

        // Create teaching assistant in the course
        user = ModelFactory.generateActivatedUser("teaching-assisstant11");
        user.setGroups(Set.of("new-ta-group"));
        userRepo.save(user);

        mockDelegate.mockUpdateCoursePermissions(course, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
        MvcResult result = request.getMvc().perform(buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        assertThat(updatedCourse.getInstructorGroupName()).isEqualTo("new-instructor-group");
        assertThat(updatedCourse.getEditorGroupName()).isEqualTo("new-editor-group");
        assertThat(updatedCourse.getTeachingAssistantGroupName()).isEqualTo("new-ta-group");
    }

    // Test
    public void testCreateAndUpdateCourseWithCourseImage() throws Exception {
        var createdCourse = createCourseWithCourseImageAndReturn();
        var courseIcon = createdCourse.getCourseIcon();
        createdCourse.setDescription("new description"); // do additional update

        // Update course
        request.getMvc().perform(buildUpdateCourse(createdCourse.getId(), createdCourse, "newTestIcon")).andExpect(status().isOk());

        List<Course> updatedCourses = courseRepo.findAll();
        assertThat(updatedCourses).hasSize(1);
        var updatedCourse = updatedCourses.get(0);
        assertThat(updatedCourse.getId()).isEqualTo(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isNotEqualTo(courseIcon).isNotNull();
        assertThat(updatedCourse.getDescription()).isEqualTo("new description");
    }

    // Test
    public void testCreateAndUpdateCourseWithPersistentCourseImageOnUpdate() throws Exception {
        Course createdCourse = createCourseWithCourseImageAndReturn();

        // Update course
        request.getMvc().perform(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk());

        List<Course> updatedCourses = courseRepo.findAll();
        assertThat(updatedCourses).hasSize(1);
        var updatedCourse = updatedCourses.get(0);
        assertThat(updatedCourse.getId()).isEqualTo(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isEqualTo(createdCourse.getCourseIcon());
    }

    // Test
    public void testCreateAndUpdateCourseWithRemoveCourseImageOnUpdate() throws Exception {
        Course createdCourse = createCourseWithCourseImageAndReturn();
        createdCourse.setCourseIcon(null);

        // Update course
        request.getMvc().perform(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk());

        List<Course> updatedCourses = courseRepo.findAll();
        assertThat(updatedCourses).hasSize(1);
        var updatedCourse = updatedCourses.get(0);
        assertThat(updatedCourse.getId()).isEqualTo(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isNull();
    }

    // Test
    public void testCreateAndUpdateCourseWithSetNewImageDespiteRemoval() throws Exception {
        Course createdCourse = createCourseWithCourseImageAndReturn();
        var courseIcon = createdCourse.getCourseIcon();
        createdCourse.setCourseIcon(null);

        // Update course
        request.getMvc().perform(buildUpdateCourse(createdCourse.getId(), createdCourse, "newTestIcon")).andExpect(status().isOk());

        List<Course> updatedCourses = courseRepo.findAll();
        assertThat(updatedCourses).hasSize(1);
        var updatedCourse = updatedCourses.get(0);
        assertThat(updatedCourse.getId()).isEqualTo(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isNotNull().isNotEqualTo(courseIcon);
    }

    // Test
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToRemoveUser() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldEditorGroup = course.getEditorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setInstructorGroupName("new-editor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        mockDelegate.mockFailUpdateCoursePermissionsInCi(course, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, false, true);
        request.getMvc().perform(buildUpdateCourse(course.getId(), course)).andExpect(status().isInternalServerError()).andReturn();
    }

    // Test
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToAddUser() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldEditorGroup = course.getEditorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setInstructorGroupName("new-editor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        mockDelegate.mockFailUpdateCoursePermissionsInCi(course, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, true, false);
        request.getMvc().perform(buildUpdateCourse(course.getId(), course)).andExpect(status().isInternalServerError());
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
    public void testGetCoursesWithPermission() throws Exception {
        database.createCoursesWithExercisesAndLectures(true);
        List<Course> courses = request.getList("/api/courses", HttpStatus.OK, Course.class);
        assertThat(courses).as("All courses are available").hasSize(2);
        for (Exercise exercise : courses.get(0).getExercises()) {
            assertThat(exercise.getGradingInstructions()).as("Grading instructions are not filtered out").isNotNull();
            assertThat(exercise.getProblemStatement()).as("Problem statements are not filtered out").isNotNull();
        }
    }

    // Test
    public void testGetCoursesWithQuizExercises() throws Exception {
        database.createCoursesWithExercisesAndLectures(true);
        List<Course> courses = request.getList("/api/courses/courses-with-quiz", HttpStatus.OK, Course.class);
        assertThat(courses).as("All courses are available").hasSize(1);
        for (Exercise exercise : courses.get(0).getExercises()) {
            assertThat(exercise.getGradingInstructions()).as("Grading instructions are filtered out").isNull();
            assertThat(exercise.getProblemStatement()).as("Problem statements are filtered out").isNull();
        }
    }

    // Test
    public void testGetCourseForDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLecturesAndLectureUnits(true, false);
        Course receivedCourse = request.get("/api/courses/" + courses.get(0).getId() + "/for-dashboard", HttpStatus.OK, Course.class);

        // Test that the received course has five exercises
        assertThat(receivedCourse.getExercises()).as("Five exercises are returned").hasSize(5);
        // Test that the received course has two lectures
        assertThat(receivedCourse.getLectures()).as("Two lectures are returned").hasSize(2);

        // Iterate over all exercises of the remaining course
        for (Exercise exercise : courses.get(0).getExercises()) {
            // Test that the exercise does not have more than one participation.
            assertThat(exercise.getStudentParticipations()).as("At most one participation for exercise").hasSizeLessThanOrEqualTo(1);
            if (!exercise.getStudentParticipations().isEmpty()) {
                // Buffer participation so that null checking is easier.
                Participation participation = exercise.getStudentParticipations().iterator().next();
                if (!participation.getSubmissions().isEmpty()) {
                    // The call filters participations by submissions and their result. After the call each participation shouldn't have more than one submission.
                    assertThat(participation.getSubmissions()).as("At most one submission for participation").hasSizeLessThanOrEqualTo(1);
                    Submission submission = participation.getSubmissions().iterator().next();
                    if (submission != null) {
                        // Test that the correct text submission was filtered.
                        if (submission instanceof TextSubmission textSubmission) {
                            assertThat(textSubmission.getText()).as("Correct text submission").isEqualTo("text");
                        }
                        // Test that the correct modeling submission was filtered.
                        else if (submission instanceof ModelingSubmission modelingSubmission) {
                            assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model1");
                        }
                    }
                }
            }
        }
    }

    // Test
    public void testGetAllCoursesForDashboard() throws Exception {
        database.createCoursesWithExercisesAndLecturesAndLectureUnits(true, false);

        // Perform the request that is being tested here
        List<Course> courses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);

        // Test that the prepared inactive course was filtered out
        assertThat(courses).as("Inactive course was filtered out").hasSize(1);

        // Test that the remaining course has five exercises
        assertThat(courses.get(0).getExercises()).as("Five exercises are returned").hasSize(5);

        // Iterate over all exercises of the remaining course
        for (Exercise exercise : courses.get(0).getExercises()) {
            // Test that the exercise does not have more than one participation.
            assertThat(exercise.getStudentParticipations()).as("At most one participation for exercise").hasSizeLessThanOrEqualTo(1);
            if (!exercise.getStudentParticipations().isEmpty()) {
                // Buffer participation so that null checking is easier.
                Participation participation = exercise.getStudentParticipations().iterator().next();
                if (!participation.getSubmissions().isEmpty()) {
                    // The call filters participations by submissions and their result. After the call each participation shouldn't have more than one submission.
                    assertThat(participation.getSubmissions()).as("At most one submission for participation").hasSizeLessThanOrEqualTo(1);
                    Submission submission = participation.getSubmissions().iterator().next();
                    if (submission != null) {
                        // Test that the correct text submission was filtered.
                        if (submission instanceof TextSubmission textSubmission) {
                            assertThat(textSubmission.getText()).as("Correct text submission").isEqualTo("text");
                        }
                        // Test that the correct modeling submission was filtered.
                        else if (submission instanceof ModelingSubmission modelingSubmission) {
                            assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model1");
                        }
                    }
                }
            }
        }
    }

    // Test
    public void testGetCoursesWithoutActiveExercises() throws Exception {
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        courseRepo.save(course);
        List<Course> courses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);
        assertThat(courses).as("Only one course is returned").hasSize(1);
        assertThat(courses.stream().findFirst().get().getExercises()).as("Course doesn't have any exercises").isEmpty();
    }

    // Test
    public void testGetCoursesAccurateTimezoneEvaluation() throws Exception {
        Course courseActive = ModelFactory.generateCourse(1L, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25), new HashSet<>(), "tumuser", "tutor",
                "editor", "instructor");
        Course courseNotActivePast = ModelFactory.generateCourse(2L, ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusMinutes(25), new HashSet<>(), "tumuser", "tutor",
                "editor", "instructor");
        Course courseNotActiveFuture = ModelFactory.generateCourse(3L, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusDays(5), new HashSet<>(), "tumuser", "tutor",
                "editor", "instructor");
        courseActive = courseRepo.save(courseActive);
        courseRepo.save(courseNotActivePast);
        courseRepo.save(courseNotActiveFuture);
        List<Course> courses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);
        assertThat(courses).as("Exactly one course is returned").hasSize(1);
        assertThat(courses.get(0)).as("Active course is returned").isEqualTo(courseActive);
        List<Course> coursesForNotifications = request.getList("/api/courses/for-notifications", HttpStatus.OK, Course.class);
        assertThat(coursesForNotifications).as("Exactly one course is returned").hasSize(1);
        assertThat(coursesForNotifications.get(0)).as("Active course is returned").isEqualTo(courseActive);
    }

    // Test
    public void testGetCourseWithOrganizations() throws Exception {
        Course courseWithOrganization = database.createCourseWithOrganizations();
        Course course = request.get("/api/courses/" + courseWithOrganization.getId() + "/with-organizations", HttpStatus.OK, Course.class);
        assertThat(course.getOrganizations()).isEqualTo(courseWithOrganization.getOrganizations());
        assertThat(course.getOrganizations()).isNotEmpty();
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
                "testuser", "tutor", "editor", "instructor");
        courseActiveRegistrationEnabled.setRegistrationEnabled(true);
        Course courseActiveRegistrationDisabled = ModelFactory.generateCourse(2L, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25), new HashSet<>(),
                "testuser", "tutor", "editor", "instructor");
        courseActiveRegistrationDisabled.setRegistrationEnabled(false);
        Course courseNotActivePast = ModelFactory.generateCourse(3L, ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusMinutes(25), new HashSet<>(), "testuser", "tutor",
                "editor", "instructor");
        Course courseNotActiveFuture = ModelFactory.generateCourse(4L, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusDays(5), new HashSet<>(), "testuser", "tutor",
                "editor", "instructor");
        courseRepo.save(courseActiveRegistrationEnabled);
        courseRepo.save(courseActiveRegistrationDisabled);
        courseRepo.save(courseNotActivePast);
        courseRepo.save(courseNotActiveFuture);

        List<Course> courses = request.getList("/api/courses/for-registration", HttpStatus.OK, Course.class);
        assertThat(courses).as("Exactly one course is available to register").hasSize(1);
        courses.get(0).setId(courseActiveRegistrationEnabled.getId());
        assertThat(courses.get(0)).as("Only active course is returned").isEqualTo(courseActiveRegistrationEnabled);
    }

    // Test
    public void testGetCourseForAssessmentDashboardWithStats() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        for (Course testCourse : testCourses) {
            Course course = request.get("/api/courses/" + testCourse.getId() + "/for-assessment-dashboard", HttpStatus.OK, Course.class);
            for (Exercise exercise : course.getExercises()) {
                assertThat(exercise.getTotalNumberOfAssessments().inTime()).as("Number of in-time assessments is correct").isZero();
                assertThat(exercise.getTotalNumberOfAssessments().late()).as("Number of late assessments is correct").isZero();
                assertThat(exercise.getTutorParticipations()).as("Tutor participation was created").hasSize(1);
                // Mock data contains exactly two submissions for the modeling exercise
                if (exercise instanceof ModelingExercise) {
                    assertThat(exercise.getNumberOfSubmissions().inTime()).as("Number of in-time submissions is correct").isEqualTo(2);
                }
                // Mock data contains exactly one submission for the text exercise
                if (exercise instanceof TextExercise) {
                    assertThat(exercise.getNumberOfSubmissions().inTime()).as("Number of in-time submissions is correct").isEqualTo(1);
                }
                // Mock data contains no submissions for the file upload and programming exercise
                if (exercise instanceof FileUploadExercise || exercise instanceof ProgrammingExercise) {
                    assertThat(exercise.getNumberOfSubmissions().inTime()).as("Number of in-time submissions is correct").isZero();
                }

                assertThat(exercise.getNumberOfSubmissions().late()).as("Number of late submissions is correct").isZero();
                assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
                assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
                // Check tutor participation
                if (!exercise.getTutorParticipations().isEmpty()) {
                    TutorParticipation tutorParticipation = exercise.getTutorParticipations().iterator().next();
                    assertThat(tutorParticipation.getStatus()).as("Tutor participation status is correctly initialized").isEqualTo(TutorParticipationStatus.NOT_PARTICIPATED);
                }

                // There is no average rating for exercises with no assessments and therefore no ratings
                assertThat(exercise.getAverageRating()).isNull();
            }

            StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            long numberOfInTimeSubmissions = course.getId().equals(testCourses.get(0).getId()) ? 3 : 0; // course 1 has 3 submissions, course 2 has 0 submissions
            assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions is correct").isEqualTo(numberOfInTimeSubmissions);
            assertThat(stats.getNumberOfSubmissions().late()).as("Number of latte submissions is correct").isZero();
            assertThat(stats.getTotalNumberOfAssessments().inTime()).as("Number of in-time assessments is correct").isZero();
            assertThat(stats.getTotalNumberOfAssessments().late()).as("Number of late assessments is correct").isZero();
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
            assertThat(stats.getTutorLeaderboardEntries()).as("Number of tutor leaderboard entries is correct").hasSize(5);
        }
    }

    // Tests that average rating and number of ratings are computed correctly in '/for-assessment-dashboard'
    public void testGetCourseForAssessmentDashboard_averageRatingComputedCorrectly() throws Exception {
        var testCourse = database.createCoursesWithExercisesAndLectures(true).get(0);
        var exercise = (TextExercise) testCourse.getExercises().stream().filter(ex -> ex.getExerciseType() == ExerciseType.TEXT).findFirst().get();

        int[] ratings = { 3, 4, 5 };
        for (int i = 0; i < ratings.length; i++) {
            var submission = database.createSubmissionForTextExercise(exercise, database.getUserByLogin("student" + (i + 1)), "text");
            var assessment = database.addResultToSubmission(submission, AssessmentType.MANUAL, null, 0.0, true).getLatestResult();
            database.addRatingToResult(assessment, ratings[i]);
        }

        var responseCourse = request.get("/api/courses/" + testCourse.getId() + "/for-assessment-dashboard", HttpStatus.OK, Course.class);
        var responseExercise = responseCourse.getExercises().stream().filter(ex -> ex.getExerciseType() == ExerciseType.TEXT).findFirst().get();

        // Ensure that average rating and number of ratings is computed correctly
        var averageRating = Arrays.stream(ratings).mapToDouble(Double::valueOf).sum() / ratings.length;
        assertThat(responseExercise.getAverageRating()).isEqualTo(averageRating);
        assertThat(responseExercise.getNumberOfRatings()).isEqualTo(ratings.length);
    }

    // Test
    public void testGetCourseForInstructorDashboardWithStats_instructorNotInCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        request.get("/api/courses/" + testCourses.get(0).getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
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
        assertThat(currentTutorLeaderboard.getNumberOfTutorComplaints()).isZero();
        assertThat(currentTutorLeaderboard.getNumberOfAcceptedComplaints()).isZero();
        assertThat(currentTutorLeaderboard.getNumberOfComplaintResponses()).isZero();
        assertThat(currentTutorLeaderboard.getNumberOfAnsweredMoreFeedbackRequests()).isZero();
        assertThat(currentTutorLeaderboard.getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(currentTutorLeaderboard.getNumberOfTutorMoreFeedbackRequests()).isZero();
        assertThat(currentTutorLeaderboard.getNumberOfAssessments()).isZero();
        assertThat(currentTutorLeaderboard.getPoints()).isZero();
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

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
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

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorComplaints()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorComplaints()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorComplaints()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorComplaints()).isZero();

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
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAssessments()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfTutorComplaints()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAcceptedComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAcceptedComplaints()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAcceptedComplaints()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAcceptedComplaints()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAcceptedComplaints()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfComplaintResponses()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfComplaintResponses()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfComplaintResponses()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfComplaintResponses()).isZero();
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
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAssessments()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(4);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfTutorMoreFeedbackRequests()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAnsweredMoreFeedbackRequests()).isZero();
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
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAssessments()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfTutorComplaints()).isEqualTo(3 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfTutorComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfTutorComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfTutorComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfTutorComplaints()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfAcceptedComplaints()).isEqualTo(3 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfAcceptedComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfAcceptedComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfAcceptedComplaints()).isEqualTo(2 * 5);
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfAcceptedComplaints()).isZero();

        assertThat(stats.getTutorLeaderboardEntries().get(0).getNumberOfComplaintResponses()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(1).getNumberOfComplaintResponses()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(2).getNumberOfComplaintResponses()).isZero();
        assertThat(stats.getTutorLeaderboardEntries().get(3).getNumberOfComplaintResponses()).isZero();
        // 9 exercises, for each one there are 5 complaintResponses
        assertThat(stats.getTutorLeaderboardEntries().get(4).getNumberOfComplaintResponses()).isEqualTo(9 * 5);
    }

    // Test
    public void testGetCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        for (Course testCourse : testCourses) {
            Course courseWithExercises = request.get("/api/courses/" + testCourse.getId() + "/with-exercises", HttpStatus.OK, Course.class);
            Course courseOnly = request.get("/api/courses/" + testCourse.getId(), HttpStatus.OK, Course.class);

            // Check course properties on courseOnly
            assertThat(courseOnly.getStudentGroupName()).as("Student group name is correct").isEqualTo("tumuser");
            assertThat(courseOnly.getTeachingAssistantGroupName()).as("Teaching assistant group name is correct").isEqualTo("tutor");
            assertThat(courseOnly.getEditorGroupName()).as("Editor group name is correct").isEqualTo("editor");
            assertThat(courseOnly.getInstructorGroupName()).as("Instructor group name is correct").isEqualTo("instructor");
            assertThat(courseOnly.getEndDate()).as("End date is after start date").isAfter(courseOnly.getStartDate());
            assertThat(courseOnly.getMaxComplaints()).as("Max complaints is correct").isEqualTo(3);
            assertThat(courseOnly.getPresentationScore()).as("Presentation score is correct").isEqualTo(2);
            assertThat(courseOnly.getExercises()).as("Course without exercises contains no exercises").isEmpty();
            assertThat(courseOnly.getNumberOfStudents()).as("Amount of students is correct").isEqualTo(8);
            assertThat(courseOnly.getNumberOfTeachingAssistants()).as("Amount of teaching assistants is correct").isEqualTo(5);
            assertThat(courseOnly.getNumberOfEditors()).as("Amount of editors is correct").isEqualTo(1);
            assertThat(courseOnly.getNumberOfInstructors()).as("Amount of instructors is correct").isEqualTo(1);

            // Assert that course properties on courseWithExercises and courseWithExercisesAndRelevantParticipations match those of courseOnly
            String[] ignoringFields = { "exercises", "tutorGroups", "lectures", "exams", "fileService", "numberOfInstructorsTransient", "numberOfStudentsTransient",
                    "numberOfTeachingAssistantsTransient", "numberOfEditorsTransient" };
            assertThat(courseWithExercises).as("courseWithExercises same as courseOnly").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(courseOnly);

            // Verify presence of exercises in mock courses
            // - Course 1 has 5 exercises in total, 4 exercises with relevant participations
            // - Course 2 has 0 exercises in total, 0 exercises with relevant participations
            boolean isFirstCourse = courseOnly.getId().equals(testCourses.get(0).getId());
            int numberOfExercises = isFirstCourse ? 5 : 0;
            assertThat(courseWithExercises.getExercises()).as("Course contains correct number of exercises").hasSize(numberOfExercises);
        }
    }

    // Test
    public void testGetCategoriesInCourse() throws Exception {
        List<Course> testCourses = database.createCoursesWithExercisesAndLectures(true);
        Course course1 = testCourses.get(0);
        Course course2 = testCourses.get(1);
        List<String> categories1 = request.getList("/api/courses/" + course1.getId() + "/categories", HttpStatus.OK, String.class);
        assertThat(categories1).as("Correct categories in course1").containsExactlyInAnyOrder("Category", "Modeling", "Quiz", "File", "Text", "Programming");
        List<String> categories2 = request.getList("/api/courses/" + course2.getId() + "/categories", HttpStatus.OK, String.class);
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
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
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
        assertThat(auditEvent.getData()).as("Correct Event Data").containsEntry("course", course1.getTitle());

        request.postWithResponseBody("/api/courses/" + course2.getId() + "/register", null, User.class, HttpStatus.BAD_REQUEST);
    }

    // Test
    public void testRegisterForCourse_notMeetsDate() throws Exception {
        User student = ModelFactory.generateActivatedUser("ab12cde");
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course notYetStartedCourse = ModelFactory.generateCourse(null, futureTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course finishedCourse = ModelFactory.generateCourse(null, pastTimestamp, pastTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
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
        var course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        request.getMvc().perform(buildUpdateCourse(course.getId(), course)).andExpect(status().isForbidden());
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
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

    /**
     * Searches for others users of a course in multiple roles
     *
     * @throws Exception
     */
    public void testSearchStudentsAndTutorsAndInstructorsInCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("nameOfUser", "student1");

        // users must not be able to find themselves
        List<User> student1 = request.getList("/api/courses/" + course.getId() + "/search-other-users", HttpStatus.OK, User.class, parameters);
        assertThat(student1).as("User could not find themself").hasSize(0);

        // find another student for course
        parameters.set("nameOfUser", "student2");
        List<User> student2 = request.getList("/api/courses/" + course.getId() + "/search-other-users", HttpStatus.OK, User.class, parameters);
        assertThat(student2).as("Another student in course found").hasSize(1);

        // find a tutor for course
        parameters.set("nameOfUser", "tutor1");
        List<User> tutor = request.getList("/api/courses/" + course.getId() + "/search-other-users", HttpStatus.OK, User.class, parameters);
        assertThat(tutor).as("A tutors in course found").hasSize(1);

        // find an instructors for course
        parameters.set("nameOfUser", "instructor");
        List<User> instructor = request.getList("/api/courses/" + course.getId() + "/search-other-users", HttpStatus.OK, User.class, parameters);
        assertThat(instructor).as("An instructors in course found").hasSize(1);
    }

    /**
     * Tries to search for users of another course and expects to be forbidden
     *
     * @throws Exception
     */
    public void testSearchStudentsAndTutorsAndInstructorsInOtherCourseForbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("nameOfUser", "student2");

        // find a user in another course
        request.getList("/api/courses/" + course.getId() + "/search-other-users", HttpStatus.FORBIDDEN, User.class, parameters);
    }

    // Test
    public void testGetAllEditorsInCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        // Get all editors for course
        List<User> editors = request.getList("/api/courses/" + course.getId() + "/editors", HttpStatus.OK, User.class);
        assertThat(editors).as("All editors in course found").hasSize(numberOfEditors);
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);
        testGetAllStudentsOrTutorsOrInstructorsInCourse__forbidden(course);
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        testGetAllStudentsOrTutorsOrInstructorsInCourse__forbidden(course);
    }

    private void testGetAllStudentsOrTutorsOrInstructorsInCourse__forbidden(Course course) throws Exception {
        request.getList("/api/courses/" + course.getId() + "/students", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/courses/" + course.getId() + "/tutors", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/courses/" + course.getId() + "/instructors", HttpStatus.FORBIDDEN, User.class);
    }

    // Test
    public void testAddStudentOrTutorOrEditorOrInstructorToCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrEditorOrInstructorToCourse(course, HttpStatus.OK);

        // TODO check that the roles have changed accordingly
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrEditorOrInstructorToCourse(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrEditorOrInstructorToCourse(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/students/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/editors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
    }

    private void testAddStudentOrTutorOrEditorOrInstructorToCourse(Course course, HttpStatus httpStatus) throws Exception {
        var student = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student1").get();
        var tutor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("tutor1").get();
        var editor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("editor1").get();
        var instructor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("instructor1").get();

        mockDelegate.mockAddUserToGroupInUserManagement(student, course.getStudentGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(tutor1, course.getTeachingAssistantGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(editor1, course.getEditorGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(instructor1, course.getInstructorGroupName(), false);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/students/student1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/tutor1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/editors/editor1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/instructor1", null, httpStatus, null);
    }

    // Test
    public void testAddTutorAndEditorAndInstructorToCourse_failsToAddUserToGroup(HttpStatus expectedFailureCode) throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        database.addProgrammingExerciseToCourse(course, false);
        course = courseRepo.save(course);

        var tutor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("tutor1").get();
        var editor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("editor1").get();
        var instructor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("instructor1").get();

        mockDelegate.mockAddUserToGroupInUserManagement(tutor1, course.getTeachingAssistantGroupName(), true);
        mockDelegate.mockAddUserToGroupInUserManagement(editor1, course.getEditorGroupName(), true);
        mockDelegate.mockAddUserToGroupInUserManagement(instructor1, course.getInstructorGroupName(), true);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/tutor1", null, expectedFailureCode, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/editors/editor1", null, expectedFailureCode, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/instructor1", null, expectedFailureCode, null);
    }

    // Test
    public void testRemoveTutorFromCourse_failsToRemoveUserFromGroup() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        database.addProgrammingExerciseToCourse(course, false);
        course = courseRepo.save(course);

        User tutor = userRepo.findOneWithGroupsByLogin("tutor1").get();
        mockDelegate.mockRemoveUserFromGroup(tutor, course.getTeachingAssistantGroupName(), true);
        request.delete("/api/courses/" + course.getId() + "/tutors/" + tutor.getLogin(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(course, HttpStatus.OK);
        // TODO check that the roles have changed accordingly
    }

    // Test
    public void testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_WithNonExistingUser() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        request.delete("/api/courses/" + course.getId() + "/students/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/tutors/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/editors/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/instructors/maxMustermann", HttpStatus.NOT_FOUND);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(course, HttpStatus.FORBIDDEN);
    }

    private void testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(Course course, HttpStatus httpStatus) throws Exception {
        // Retrieve users from whom to remove groups
        User student = userRepo.findOneWithGroupsByLogin("student1").get();
        User tutor = userRepo.findOneWithGroupsByLogin("tutor1").get();
        User editor = userRepo.findOneWithGroupsByLogin("editor1").get();
        User instructor = userRepo.findOneWithGroupsByLogin("instructor1").get();

        // Mock remove requests
        mockDelegate.mockRemoveUserFromGroup(student, course.getStudentGroupName(), false);
        mockDelegate.mockRemoveUserFromGroup(tutor, course.getTeachingAssistantGroupName(), false);
        mockDelegate.mockRemoveUserFromGroup(editor, course.getEditorGroupName(), false);
        mockDelegate.mockRemoveUserFromGroup(instructor, course.getInstructorGroupName(), false);

        // Remove users from their group
        request.delete("/api/courses/" + course.getId() + "/students/" + student.getLogin(), httpStatus);
        request.delete("/api/courses/" + course.getId() + "/tutors/" + tutor.getLogin(), httpStatus);
        request.delete("/api/courses/" + course.getId() + "/editors/" + editor.getLogin(), httpStatus);
        request.delete("/api/courses/" + course.getId() + "/instructors/" + instructor.getLogin(), httpStatus);
    }

    // Test
    public void testGetLockedSubmissionsForCourseAsTutor() throws Exception {
        Course course = database.addCourseWithDifferentModelingExercises();
        ModelingExercise classExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");

        List<Submission> lockedSubmissions = request.getList("/api/courses/" + course.getId() + "/lockedSubmissions", HttpStatus.OK, Submission.class);
        assertThat(lockedSubmissions).as("Locked Submissions is not null").isNotNull();
        assertThat(lockedSubmissions).as("Locked Submissions length is 0").isEmpty();

        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student1", "tutor1");

        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student2", "tutor1");

        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student3", "tutor1");

        lockedSubmissions = request.getList("/api/courses/" + course.getId() + "/lockedSubmissions", HttpStatus.OK, Submission.class);
        assertThat(lockedSubmissions).as("Locked Submissions is not null").isNotNull();
        assertThat(lockedSubmissions).as("Locked Submissions length is 3").hasSize(3);
    }

    // Test
    public void testGetLockedSubmissionsForCourseAsStudent() throws Exception {
        List<Submission> lockedSubmissions = request.getList("/api/courses/1/lockedSubmissions", HttpStatus.FORBIDDEN, Submission.class);
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
        var course = database.createCourseWithTestModelingAndFileUploadExercisesAndSubmissions();
        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);
        await().until(() -> courseRepo.findById(course.getId()).get().getCourseArchivePath() != null);
        var updatedCourse = courseRepo.findById(course.getId()).get();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    /**
     * Test
     *
     * @throws Exception
     */
    public void searchStudentsInCourse() throws Exception {
        var course = database.createCourse();

        MultiValueMap<String, String> params1 = new LinkedMultiValueMap<>();
        params1.add("loginOrName", "student");
        List<UserDTO> students = request.getList("/api/courses/" + course.getId() + "/students/search", HttpStatus.OK, UserDTO.class, params1);
        assertThat(students).size().isEqualTo(8);

        MultiValueMap<String, String> params2 = new LinkedMultiValueMap<>();
        params2.add("loginOrName", "tutor");
        // should be empty as we only search for students
        List<UserDTO> tutors = request.getList("/api/courses/" + course.getId() + "/students/search", HttpStatus.OK, UserDTO.class, params2);
        assertThat(tutors).isEmpty();
    }

    // Test
    public void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportModelingExercise() throws Exception {
        Course course = database.createCourseWithTestModelingAndFileUploadExercisesAndSubmissions();
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst();
        assertThat(modelingExercise).isPresent();
        archiveCourseAndAssertExerciseDoesntExist(course, modelingExercise.get());
    }

    // Test
    public void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportTextExercise() throws Exception {
        Course course = database.createCourseWithTestModelingAndFileUploadExercisesAndSubmissions();
        Optional<TextExercise> textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst();
        assertThat(textExercise).isPresent();
        archiveCourseAndAssertExerciseDoesntExist(course, textExercise.get());
    }

    // Test
    public void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportFileUploadExercise() throws Exception {
        Course course = database.createCourseWithTestModelingAndFileUploadExercisesAndSubmissions();
        Optional<FileUploadExercise> fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst();
        assertThat(fileUploadExercise).isPresent();
        archiveCourseAndAssertExerciseDoesntExist(course, fileUploadExercise.get());
    }

    private void archiveCourseAndAssertExerciseDoesntExist(Course course, Exercise exercise) throws Exception {
        Files.createDirectories(Path.of(courseArchivesDirPath));

        String zipGroupName = course.getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId();
        String cleanZipGroupName = fileService.removeIllegalCharacters(zipGroupName);
        doThrow(new IOException("IOException")).when(zipFileService).createZipFile(ArgumentMatchers.argThat(argument -> argument.toString().contains(cleanZipGroupName)), anyList(),
                any(Path.class));

        List<Path> files = archiveCourseAndExtractFiles(course);
        assertThat(files).hasSize(4);

        String exerciseType = "";
        if (exercise instanceof FileUploadExercise) {
            exerciseType = "FileUpload";
        }
        else if (exercise instanceof ModelingExercise) {
            exerciseType = "Modeling";
        }
        else if (exercise instanceof TextExercise) {
            exerciseType = "Text";
        }
        assertThat(files).doesNotContain(Path.of(exerciseType + "-student1"));
    }

    private List<Path> archiveCourseAndExtractFiles(Course course) throws IOException {
        List<String> exportErrors = new ArrayList<>();
        Optional<Path> exportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
        assertThat(exportedCourse).isNotEmpty();

        // Extract the archive
        Path archivePath = exportedCourse.get();
        zipFileTestUtilService.extractZipFileRecursively(archivePath.toString());
        String extractedArchiveDir = archivePath.toString().substring(0, archivePath.toString().length() - 4);

        try (var files = Files.walk(Path.of(extractedArchiveDir))) {
            return files.filter(Files::isRegularFile).map(Path::getFileName).filter(path -> !path.toString().endsWith(".zip")).toList();
        }
    }

    public void testExportCourse_cannotCreateTmpDir() throws Exception {
        Course course = database.createCourseWithTestModelingAndFileUploadExercisesAndSubmissions();
        List<String> exportErrors = new ArrayList<>();

        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectories(argThat(path -> path.toString().contains("exports")))).thenThrow(IOException.class);
        Optional<Path> exportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
        mockedFiles.close();

        assertThat(exportedCourse).isEmpty();
    }

    public void testExportCourse_cannotCreateCourseExercisesDir() throws Exception {
        Course course = database.createCourseWithTestModelingAndFileUploadExercisesAndSubmissions();
        List<String> exportErrors = new ArrayList<>();

        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectory(argThat(path -> path.toString().contains("course-exercises")))).thenThrow(IOException.class);
        Optional<Path> exportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
        mockedFiles.close();

        assertThat(exportedCourse).isEmpty();
    }

    public void testExportCourseExam_cannotCreateTmpDir() throws Exception {
        Course course = database.createCourseWithExamAndExercises();
        List<String> exportErrors = new ArrayList<>();

        Optional<Exam> exam = examRepo.findByCourseId(course.getId()).stream().findFirst();
        assertThat(exam).isPresent();

        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectories(argThat(path -> path.toString().contains("exports")))).thenThrow(IOException.class);
        Optional<Path> exportedCourse = courseExamExportService.exportExam(exam.get(), courseArchivesDirPath, exportErrors);
        mockedFiles.close();

        assertThat(exportedCourse).isEmpty();
    }

    public void testExportCourseExam_cannotCreateExamsDir() throws Exception {
        Course course = database.createCourseWithExamAndExercises();
        List<String> exportErrors = new ArrayList<>();

        course = courseRepo.findWithEagerExercisesById(course.getId());

        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectory(argThat(path -> path.toString().contains("exams")))).thenThrow(IOException.class);
        Optional<Path> exportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
        mockedFiles.close();

        assertThat(exportedCourse).isEmpty();
        assertThat(exportErrors).isNotEmpty();
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
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        var downloadedArchive = request.get("/api/courses/" + course.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    // Test
    public void testDownloadCourseArchiveAsInstructor() throws Exception {
        submissionRepository.deleteAll();

        // Archive the course and wait until it's complete
        testArchiveCourseWithTestModelingAndFileUploadExercises();

        var optCourse = courseRepo.findAll().stream().findFirst();
        assertThat(optCourse).isPresent();
        var course = optCourse.get();

        // Download the archive
        var archive = request.getFile("/api/courses/" + course.getId() + "/download-archive", HttpStatus.OK, new LinkedMultiValueMap<>());
        assertThat(archive).isNotNull();
        assertThat(archive).exists();
        assertThat(archive.getPath().length()).isGreaterThanOrEqualTo(4);

        // Extract the archive
        zipFileTestUtilService.extractZipFileRecursively(archive.getAbsolutePath());
        String extractedArchiveDir = archive.getPath().substring(0, archive.getPath().length() - 4);

        // We test for the filenames of the submissions since it's the easiest way.
        // We don't test the directory structure
        List<Path> filenames;
        try (var files = Files.walk(Path.of(extractedArchiveDir))) {
            filenames = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
        }

        var submissions = submissionRepository.findAll();

        var fileUploadSubmissionId = submissions.stream().filter(submission -> submission instanceof FileUploadSubmission).findFirst().get().getId();
        assertThat(filenames).contains(Path.of("FileUpload-student1-" + fileUploadSubmissionId + ".png"));

        var textSubmissionId = submissions.stream().filter(submission -> submission instanceof TextSubmission).findFirst().get().getId();
        assertThat(filenames).contains(Path.of("Text-student1-" + textSubmissionId + ".txt"));

        var modelingSubmission = submissions.stream().filter(submission -> submission instanceof ModelingSubmission).findFirst().get().getId();
        assertThat(filenames).contains(Path.of("Modeling-student1-" + modelingSubmission + ".json"));
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
        var course = database.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        course.setCourseArchivePath("some-archive-path");
        courseRepo.save(course);

        var programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

        mockDelegate.mockDeleteRepository(programmingExercise.getProjectKey(), (programmingExercise.getProjectKey()).toLowerCase() + "-student1", false);
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

    // Test
    public void testGetCourseTitle() throws Exception {
        Course course = database.createCourse();
        course.setTitle("Test Course");
        course = courseRepo.save(course);

        final var title = request.get("/api/courses/" + course.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(course.getTitle());
    }

    // Test
    public void testGetCourseTitleForNonExistingCourse() throws Exception {
        request.get("/api/courses/12312412321/title", HttpStatus.NOT_FOUND, String.class);
    }

    // Test
    public void testGetAllCoursesForManagementOverview() throws Exception {
        // Add two courses, containing one not belonging to the instructor
        var testCourses = database.createCoursesWithExercisesAndLectures(true);
        var instructorsCourse = testCourses.get(0);
        instructorsCourse.setInstructorGroupName("test-instructors");
        courseRepo.save(instructorsCourse);

        var instructor = database.getUserByLogin("instructor1");
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepo.save(instructor);

        var courses = request.getList("/api/courses/course-management-overview", HttpStatus.OK, Course.class);
        assertThat(courses).hasSize(1);

        var course = courses.get(0);
        assertThat(course.getId()).isEqualTo(instructorsCourse.getId());
    }

    // Test
    public void testGetExercisesForCourseOverview() throws Exception {
        // Add two courses, containing one not belonging to the instructor
        var testCourses = database.createCoursesWithExercisesAndLectures(true);
        var instructorsCourse = testCourses.get(0);
        instructorsCourse.setInstructorGroupName("test-instructors");
        courseRepo.save(instructorsCourse);

        var instructor = database.getUserByLogin("instructor1");
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepo.save(instructor);

        var courses = request.getList("/api/courses/exercises-for-management-overview", HttpStatus.OK, Course.class);
        assertThat(courses).hasSize(1);

        var course = courses.get(0);
        assertThat(course.getId()).isEqualTo(instructorsCourse.getId());

        var exerciseDetails = course.getExercises();
        assertThat(exerciseDetails).isNotNull();
        assertThat(exerciseDetails).hasSize(5);

        var quizDetailsOptional = exerciseDetails.stream().filter(e -> e instanceof QuizExercise).findFirst();
        assertThat(quizDetailsOptional).isPresent();

        var quizExerciseOptional = instructorsCourse.getExercises().stream().filter(e -> e instanceof QuizExercise).findFirst();
        assertThat(quizExerciseOptional).isPresent();

        var quizDetails = quizDetailsOptional.get();
        var quizExercise = quizExerciseOptional.get();
        assertThat(quizDetails.getCategories()).hasSize(quizExercise.getCategories().size());

        var detailsCategories = quizDetails.getCategories().stream().findFirst();
        var exerciseCategories = quizExercise.getCategories().stream().findFirst();
        assertThat(detailsCategories).isPresent();
        assertThat(exerciseCategories).isPresent();
        assertThat(detailsCategories).contains(exerciseCategories.get());
    }

    // Test
    public void testGetExerciseStatsForCourseOverview() throws Exception {
        // Add a course and set the instructor group name
        var instructorsCourse = database.createCourse();
        instructorsCourse.setStartDate(ZonedDateTime.now().minusWeeks(1).with(DayOfWeek.MONDAY));
        instructorsCourse.setEndDate(ZonedDateTime.now().minusWeeks(1).with(DayOfWeek.WEDNESDAY));
        instructorsCourse.setInstructorGroupName("test-instructors");

        // Fetch and update an instructor
        var instructor = database.getUserByLogin("instructor1");
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepo.save(instructor);

        // Get two students
        var student = ModelFactory.generateActivatedUser("user1");
        userRepo.save(student);
        var student2 = ModelFactory.generateActivatedUser("user2");
        userRepo.save(student2);

        // Add a team exercise which was just released but not due
        var releaseDate = ZonedDateTime.now().minusDays(4);
        var futureDueDate = ZonedDateTime.now().plusDays(2);
        var futureAssessmentDueDate = ZonedDateTime.now().plusDays(4);
        var teamExerciseNotEnded = database.createTeamTextExercise(instructorsCourse, releaseDate, futureDueDate, futureAssessmentDueDate);
        teamExerciseNotEnded = exerciseRepo.save(teamExerciseNotEnded);

        // Add a team with a participation to the exercise
        final var teamExerciseId = teamExerciseNotEnded.getId();
        var teamStudents = new HashSet<User>();
        teamStudents.add(student);
        var team = database.createTeam(teamStudents, instructor, teamExerciseNotEnded, "team");
        database.createSubmissionForTextExercise(teamExerciseNotEnded, team, "Team Text");
        instructorsCourse.addExercises(teamExerciseNotEnded);

        // Create an exercise which has passed the due and assessment due date
        var dueDate = ZonedDateTime.now().minusDays(2);
        var passedAssessmentDueDate = ZonedDateTime.now().minusDays(1);
        var exerciseAssessmentDone = ModelFactory.generateTextExercise(releaseDate, dueDate, passedAssessmentDueDate, instructorsCourse);
        exerciseAssessmentDone.setMaxPoints(5.0);
        exerciseAssessmentDone = exerciseRepo.save(exerciseAssessmentDone);

        // Add a single participation to that exercise
        final var exerciseId = exerciseAssessmentDone.getId();
        database.createParticipationSubmissionAndResult(exerciseId, student, 5.0, 0.0, 60, true);

        instructorsCourse.addExercises(exerciseAssessmentDone);

        // Create an exercise which is currently in assessment
        var exerciseInAssessment = ModelFactory.generateTextExercise(releaseDate, dueDate, futureAssessmentDueDate, instructorsCourse);
        exerciseInAssessment.setMaxPoints(15.0);
        exerciseInAssessment = exerciseRepo.save(exerciseInAssessment);

        // Add a participation and submission to that exercise
        final var exerciseIdInAssessment = exerciseInAssessment.getId();
        var resultToSetAssessorFor = database.createParticipationSubmissionAndResult(exerciseIdInAssessment, student, 15.0, 0.0, 30, true);
        resultToSetAssessorFor.getSubmission().setSubmissionDate(dueDate.minusHours(1));
        resultToSetAssessorFor.getSubmission().setSubmitted(true);
        resultToSetAssessorFor.setAssessor(instructor);
        resultRepo.saveAndFlush(resultToSetAssessorFor);
        submissionRepository.saveAndFlush(resultToSetAssessorFor.getSubmission());

        // Add a participation without submission to that exercise (just starting)
        participationService.startExercise(exerciseInAssessment, student2, false);

        instructorsCourse.addExercises(exerciseInAssessment);

        courseRepo.save(instructorsCourse);

        await().until(() -> participantScoreRepository.findAll().size() == 2);

        // We only added one course, so expect one dto
        var courseDtos = request.getList("/api/courses/stats-for-management-overview", HttpStatus.OK, CourseManagementOverviewStatisticsDTO.class);
        assertThat(courseDtos).hasSize(1);
        var dto = courseDtos.get(0);
        assertThat(dto.getCourseId()).isEqualTo(instructorsCourse.getId());
        assertThat(dto.getActiveStudents()).as("course was only active for 3 days").hasSize(1);

        // Expect our three created exercises
        var exerciseDTOS = dto.getExerciseDTOS();
        assertThat(exerciseDTOS).hasSize(3);

        // Get the statistics of the exercise with a passed assessment due date
        var statisticsOptional = exerciseDTOS.stream().filter(exercise -> exercise.getExerciseId().equals(exerciseId)).findFirst();
        assertThat(statisticsOptional).isPresent();

        // Since the exercise is a "past exercise", the average score are the only statistics we set
        var statisticsDTO = statisticsOptional.get();
        assertThat(statisticsDTO.getAverageScoreInPercent()).isEqualTo(60.0);
        assertThat(statisticsDTO.getExerciseMaxPoints()).isEqualTo(5.0);
        assertThat(statisticsDTO.getNoOfParticipatingStudentsOrTeams()).isZero();
        assertThat(statisticsDTO.getParticipationRateInPercent()).isEqualTo(0.0);
        assertThat(statisticsDTO.getNoOfStudentsInCourse()).isEqualTo(8);
        assertThat(statisticsDTO.getNoOfRatedAssessments()).isZero();
        assertThat(statisticsDTO.getNoOfAssessmentsDoneInPercent()).isEqualTo(0.0);
        assertThat(statisticsDTO.getNoOfSubmissionsInTime()).isZero();

        // Get the statistics of the team exercise
        var teamStatisticsOptional = exerciseDTOS.stream().filter(exercise -> exercise.getExerciseId().equals(teamExerciseId)).findFirst();
        assertThat(teamStatisticsOptional).isPresent();

        // Since that exercise is still "currently in progress", the participations are the only statistics we set
        var teamStatisticsDTO = teamStatisticsOptional.get();
        assertThat(teamStatisticsDTO.getAverageScoreInPercent()).isEqualTo(0.0);
        assertThat(teamStatisticsDTO.getExerciseMaxPoints()).isEqualTo(10.0);
        assertThat(teamStatisticsDTO.getNoOfParticipatingStudentsOrTeams()).isEqualTo(1);
        assertThat(teamStatisticsDTO.getParticipationRateInPercent()).isEqualTo(100D);
        assertThat(teamStatisticsDTO.getNoOfStudentsInCourse()).isEqualTo(8);
        assertThat(teamStatisticsDTO.getNoOfTeamsInCourse()).isEqualTo(1);
        assertThat(teamStatisticsDTO.getNoOfRatedAssessments()).isZero();
        assertThat(teamStatisticsDTO.getNoOfAssessmentsDoneInPercent()).isEqualTo(0.0);
        assertThat(teamStatisticsDTO.getNoOfSubmissionsInTime()).isEqualTo(1L);

        // Get the statistics of the exercise in assessment
        var exerciseInAssessmentStatisticsOptional = exerciseDTOS.stream().filter(exercise -> exercise.getExerciseId().equals(exerciseIdInAssessment)).findFirst();
        assertThat(exerciseInAssessmentStatisticsOptional).isPresent();

        // Since that exercise is "currently in assessment", we need the numberOfRatedAssessment, assessmentsDoneInPercent and the numberOfSubmissionsInTime
        var exerciseInAssessmentStatisticsDTO = exerciseInAssessmentStatisticsOptional.get();
        assertThat(exerciseInAssessmentStatisticsDTO.getAverageScoreInPercent()).isEqualTo(0.0);
        assertThat(exerciseInAssessmentStatisticsDTO.getExerciseMaxPoints()).isEqualTo(15.0);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfParticipatingStudentsOrTeams()).isZero();
        assertThat(exerciseInAssessmentStatisticsDTO.getParticipationRateInPercent()).isEqualTo(0D);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfStudentsInCourse()).isEqualTo(8);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfRatedAssessments()).isEqualTo(1);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfAssessmentsDoneInPercent()).isEqualTo(100.0);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfSubmissionsInTime()).isEqualTo(1L);
    }

    // Test
    public void testGetExerciseStatsForCourseOverviewWithPastExercises() throws Exception {
        // Add a single course with six past exercises, from which only five are returned
        var instructorsCourse = database.createCourse();
        var releaseDate = ZonedDateTime.now().minusDays(7);
        var dueDate = ZonedDateTime.now().minusDays(4);
        var olderDueDate = ZonedDateTime.now().minusDays(4);
        var assessmentDueDate = ZonedDateTime.now().minusDays(2);
        var olderAssessmentDueDate = ZonedDateTime.now().minusDays(3);
        var oldestAssessmentDueDate = ZonedDateTime.now().minusDays(6);

        // Add five exercises with different combinations of due dates and assessment due dates
        instructorsCourse.addExercises(exerciseRepo.save(ModelFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(ModelFactory.generateTextExercise(releaseDate, null, assessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(ModelFactory.generateTextExercise(releaseDate, olderDueDate, assessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(ModelFactory.generateTextExercise(releaseDate, olderDueDate, olderAssessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(ModelFactory.generateTextExercise(releaseDate, null, olderAssessmentDueDate, instructorsCourse)));

        // Add one exercise which will be sorted last due to the oldest assessment due date
        var exerciseNotReturned = ModelFactory.generateTextExercise(releaseDate, dueDate, oldestAssessmentDueDate, instructorsCourse);
        exerciseNotReturned = exerciseRepo.save(exerciseNotReturned);
        final var exerciseId = exerciseNotReturned.getId();
        instructorsCourse.addExercises(exerciseNotReturned);
        courseRepo.save(instructorsCourse);

        // We only added one course, so expect one dto
        var courseDtos = request.getList("/api/courses/stats-for-management-overview", HttpStatus.OK, CourseManagementOverviewStatisticsDTO.class);
        assertThat(courseDtos).hasSize(1);
        var dto = courseDtos.get(0);
        assertThat(dto.getCourseId()).isEqualTo(instructorsCourse.getId());

        // Only five exercises should be returned
        var exerciseDTOS = dto.getExerciseDTOS();
        assertThat(exerciseDTOS).hasSize(5);

        // The one specific exercise should not be included
        var statisticsOptional = exerciseDTOS.stream().filter(exercise -> exercise.getExerciseId().equals(exerciseId)).findFirst();
        assertThat(statisticsOptional).isEmpty();
    }

    public void testGetCourseManagementDetailDataForFutureCourse() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        var course = database.createCourse();

        course.setStartDate(now.plusWeeks(3));

        var student1 = ModelFactory.generateActivatedUser("user1");
        var student2 = ModelFactory.generateActivatedUser("user2");
        // Fetch and update an instructor
        var instructor1 = database.getUserByLogin("instructor1");
        var instructor2 = database.getUserByLogin("instructor2");
        var groups = new HashSet<String>();
        groups.add("instructor");
        instructor1.setGroups(groups);
        instructor2.setGroups(groups);

        userRepo.save(instructor1);
        userRepo.save(instructor2);
        userRepo.save(student1);
        userRepo.save(student2);

        courseRepo.save(course);

        // API call
        var courseDTO = request.get("/api/courses/" + course.getId() + "/management-detail", HttpStatus.OK, CourseManagementDetailViewDTO.class);

        // Check results
        assertThat(courseDTO).isNotNull();

        assertThat(courseDTO.activeStudents()).isNullOrEmpty();

        // number of users in course
        assertThat(courseDTO.numberOfStudentsInCourse()).isEqualTo(8);
        assertThat(courseDTO.numberOfTeachingAssistantsInCourse()).isEqualTo(5);
        assertThat(courseDTO.numberOfInstructorsInCourse()).isEqualTo(2);
    }

    // Test
    public void testGetCourseManagementDetailData() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        // add courses with exercises
        var courses = database.createCoursesWithExercisesAndLectures(true);
        var course1 = courses.get(0);
        var course2 = courses.get(1);
        course1.setStartDate(now.minusWeeks(2));
        /*
         * We will duplicate the following submission and result configuration with course2. course1 contains additional submissions created by the DatabaseUtilService. These
         * submissions would make the test of the active students distribution flaky but are necessary for other test statements to be meaningful. Thus, we test the actual test
         * distribution only for course2.
         */
        course2.setStartDate(now.minusWeeks(2));

        var student1 = ModelFactory.generateActivatedUser("user1");
        var student2 = ModelFactory.generateActivatedUser("user2");
        // Fetch and update an instructor
        var instructor = database.getUserByLogin("instructor1");
        var groups = new HashSet<String>();
        groups.add("instructor");
        instructor.setGroups(groups);

        userRepo.save(instructor);
        userRepo.save(student1);
        userRepo.save(student2);

        var releaseDate = now.minusDays(7);
        var dueDate = now.minusDays(2);
        var assessmentDueDate = now.minusDays(1);
        var exercise1 = ModelFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, course1);
        exercise1.setMaxPoints(5.0);
        exercise1 = exerciseRepo.save(exercise1);

        var exercise2 = ModelFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, course2);
        exercise2.setMaxPoints(5.0);
        exercise2 = exerciseRepo.save(exercise2);

        // Add a single participation to that exercise
        final var exercise1Id = exercise1.getId();
        final var exercise2Id = exercise2.getId();

        var result1 = database.createParticipationSubmissionAndResult(exercise1Id, student1, 5.0, 0.0, 60, true);
        var result2 = database.createParticipationSubmissionAndResult(exercise1Id, student2, 5.0, 0.0, 40, true);

        var result21 = database.createParticipationSubmissionAndResult(exercise2Id, student1, 5.0, 0.0, 60, true);
        var result22 = database.createParticipationSubmissionAndResult(exercise2Id, student2, 5.0, 0.0, 40, true);

        Submission submission1 = result1.getSubmission();
        submission1.setSubmissionDate(now);
        submissionRepository.save(submission1);

        Submission submission21 = result21.getSubmission();
        submission21.setSubmissionDate(now);
        submissionRepository.save(submission21);

        Submission submission2 = result2.getSubmission();
        submission2.setSubmissionDate(now.minusWeeks(2));
        submissionRepository.save(submission2);

        Submission submission22 = result22.getSubmission();
        submission22.setSubmissionDate(now.minusWeeks(2));
        submissionRepository.save(submission22);

        result1.setAssessor(instructor);
        resultRepo.saveAndFlush(result1);
        result2.setAssessor(instructor);
        resultRepo.saveAndFlush(result2);
        course1.addExercises(exercise1);
        courseRepo.save(course1);

        result21.setAssessor(instructor);
        resultRepo.saveAndFlush(result21);
        result22.setAssessor(instructor);
        resultRepo.saveAndFlush(result22);
        course2.addExercises(exercise2);
        courseRepo.save(course2);

        // Complaint
        Complaint complaint = new Complaint().complaintType(ComplaintType.COMPLAINT);
        complaint.setResult(result1);
        complaint = complaintRepo.save(complaint);

        complaint.getResult().setParticipation(null);

        // Accept Complaint and update Assessment
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        Feedback feedback1 = new Feedback();
        feedback1.setCredits(2.5);
        feedback1.setReference(ModelFactory.generateTextBlock(0, 5, "test1").getId());
        Feedback feedback2 = new Feedback();
        feedback2.setCredits(-0.5);
        feedback2.setReference(ModelFactory.generateTextBlock(0, 5, "test2").getId());
        Feedback feedback3 = new Feedback();
        feedback3.setCredits(1.5);
        feedback3.setReference(ModelFactory.generateTextBlock(0, 5, "test3").getId());
        Feedback feedback4 = new Feedback();
        feedback4.setCredits(-1.5);
        feedback4.setReference(ModelFactory.generateTextBlock(0, 5, "test4").getId());
        Feedback feedback5 = new Feedback();
        feedback5.setCredits(2.0);
        feedback5.setReference(ModelFactory.generateTextBlock(0, 5, "test5").getId());
        var feedbackListForComplaint = Arrays.asList(feedback1, feedback2, feedback3, feedback4, feedback5);

        var assessmentUpdate = new TextAssessmentUpdateDTO();
        assessmentUpdate.feedbacks(feedbackListForComplaint).complaintResponse(complaintResponse);
        assessmentUpdate.setTextBlocks(new HashSet<>());

        request.putWithResponseBody("/api/participations/" + result1.getSubmission().getParticipation().getId() + "/submissions/" + result1.getSubmission().getId()
                + "/text-assessment-after-complaint", assessmentUpdate, Result.class, HttpStatus.OK);

        // Feedback request
        Complaint feedbackRequest = new Complaint().complaintType(ComplaintType.MORE_FEEDBACK);
        feedbackRequest.setResult(result2);
        feedbackRequest = complaintRepo.save(feedbackRequest);

        feedbackRequest.getResult().setParticipation(null);

        ComplaintResponse feedbackResponse = database.createInitialEmptyResponse("tutor2", feedbackRequest);
        feedbackResponse.getComplaint().setAccepted(true);
        feedbackResponse.setResponseText("accepted");
        var feedbackListForMoreFeedback = Arrays.asList(feedback1, feedback2, feedback3, feedback4);

        var feedbackUpdate = new TextAssessmentUpdateDTO();
        feedbackUpdate.feedbacks(feedbackListForMoreFeedback).complaintResponse(feedbackResponse);
        feedbackUpdate.setTextBlocks(new HashSet<>());

        request.putWithResponseBody("/api/participations/" + result2.getSubmission().getParticipation().getId() + "/submissions/" + result2.getSubmission().getId()
                + "/text-assessment-after-complaint", feedbackUpdate, Result.class, HttpStatus.OK);

        await().until(() -> participantScoreRepository.findAll().size() == 4);

        // API call
        var courseDTO = request.get("/api/courses/" + course1.getId() + "/management-detail", HttpStatus.OK, CourseManagementDetailViewDTO.class);

        // Check results
        assertThat(courseDTO).isNotNull();

        assertThat(courseDTO.activeStudents()).hasSize(3);

        // number of users in course
        assertThat(courseDTO.numberOfStudentsInCourse()).isEqualTo(8);
        assertThat(courseDTO.numberOfTeachingAssistantsInCourse()).isEqualTo(5);
        assertThat(courseDTO.numberOfInstructorsInCourse()).isEqualTo(1);

        // Assessments - 133 because each we have only 2 submissions which have assessments, but as they have complaints which got accepted
        // they now have 2 results each.
        assertThat(courseDTO.currentPercentageAssessments()).isEqualTo(133.3);
        assertThat(courseDTO.currentAbsoluteAssessments()).isEqualTo(4);
        assertThat(courseDTO.currentMaxAssessments()).isEqualTo(3);

        // Complaints
        assertThat(courseDTO.currentPercentageComplaints()).isEqualTo(100);
        assertThat(courseDTO.currentAbsoluteComplaints()).isEqualTo(1);
        assertThat(courseDTO.currentMaxComplaints()).isEqualTo(1);

        // More feedback requests
        assertThat(courseDTO.currentPercentageMoreFeedbacks()).isEqualTo(100);
        assertThat(courseDTO.currentAbsoluteMoreFeedbacks()).isEqualTo(1);
        assertThat(courseDTO.currentMaxMoreFeedbacks()).isEqualTo(1);

        // Average Score
        assertThat(courseDTO.currentPercentageAverageScore()).isEqualTo(60);
        assertThat(courseDTO.currentAbsoluteAverageScore()).isEqualTo(18);
        assertThat(courseDTO.currentMaxAverageScore()).isEqualTo(30);

        course2.setStartDate(now.minusWeeks(20));
        course2.setEndDate(null);
        courseRepo.save(course2);

        // API call for the lifetime overview
        var lifetimeOverviewStats = request.get("/api/courses/" + course2.getId() + "/statistics-lifetime-overview", HttpStatus.OK, List.class);

        var expectedLifetimeOverviewStats = Arrays.stream(new int[21]).boxed().collect(Collectors.toCollection(ArrayList::new));
        expectedLifetimeOverviewStats.set(18, 1);
        expectedLifetimeOverviewStats.set(20, 1);
        assertThat(lifetimeOverviewStats).as("should depict 21 weeks in total").isEqualTo(expectedLifetimeOverviewStats);

        course2.setEndDate(now.minusWeeks(1));
        courseRepo.save(course2);

        lifetimeOverviewStats = request.get("/api/courses/" + course2.getId() + "/statistics-lifetime-overview", HttpStatus.OK, List.class);

        expectedLifetimeOverviewStats = Arrays.stream(new int[20]).boxed().collect(Collectors.toCollection(ArrayList::new));
        expectedLifetimeOverviewStats.set(18, 1);
        assertThat(lifetimeOverviewStats).as("should only depict data until the end date of the course").isEqualTo(expectedLifetimeOverviewStats);

        course2.setStartDate(now.minusWeeks(2));
        courseRepo.save(course2);

        // API call for course2
        courseDTO = request.get("/api/courses/" + course2.getId() + "/management-detail", HttpStatus.OK, CourseManagementDetailViewDTO.class);

        var expectedActiveStudentDistribution = List.of(1, 0);
        assertThat(courseDTO.activeStudents()).as("submission today should not be included").isEqualTo(expectedActiveStudentDistribution);

        // Active Users
        int periodIndex = 0;
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("periodIndex", Integer.toString(periodIndex));

        var activeStudents = request.get("/api/courses/" + course1.getId() + "/statistics", HttpStatus.OK, Integer[].class, parameters);

        assertThat(activeStudents).isNotNull();
        assertThat(activeStudents).hasSize(3);

    }

    // Test
    public void testAddUsersToCourseGroup(String group, String registrationNumber1, String registrationNumber2) throws Exception {
        var course = database.createCoursesWithExercisesAndLectures(true).get(0);
        StudentDTO dto1 = new StudentDTO().registrationNumber(registrationNumber1);
        dto1.setLogin("newstudent1");
        StudentDTO dto2 = new StudentDTO().registrationNumber(registrationNumber2);
        dto1.setLogin("newstudent2");
        var newStudents = request.postListWithResponseBody("/api/courses/" + course.getId() + "/" + group, List.of(dto1, dto2), StudentDTO.class, HttpStatus.OK);
        assertThat(newStudents).hasSize(2);
        assertThat(newStudents).contains(dto1, dto2);
    }

    // Test
    public void testCreateCourseWithValidStartAndEndDate() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isCreated());
    }

    // Test
    public void testCreateCourseWithInvalidStartAndEndDate() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().plusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testCreateInvalidOnlineCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");

        // with RegistrationEnabled
        course.setOnlineCourse(true);
        course.setRegistrationEnabled(true);
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());

        // without onlinecourseconfiguration
        course.setRegistrationEnabled(false);
        course.setOnlineCourseConfiguration(null);
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());

        // with invalid online course configuration - no key and secret
        ModelFactory.generateOnlineCourseConfiguration(course, null, null, null, null);
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());

        // with invalid user prefix - not matching regex
        ModelFactory.generateOnlineCourseConfiguration(course, "key", "secret", "with space", null);
        request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testCreateValidOnlineCourse() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        ModelFactory.generateOnlineCourseConfiguration(course, "key", "secret", "validprefix", null);
        MvcResult result = request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        Course courseWithOnlineConfiguration = courseRepo.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(createdCourse.getId());
        assertThat(courseWithOnlineConfiguration.getOnlineCourseConfiguration()).isNotNull();
    }

    public void testOnlineCourseConfigurationIsLazyLoaded() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        ModelFactory.generateOnlineCourseConfiguration(course, "key", "secret", "validprefix", null);
        course = courseRepo.save(course);
        var courseId = course.getId();

        List<Course> courses = request.getList("/api/courses", HttpStatus.OK, Course.class);

        Course receivedCourse = courses.stream().filter(c -> courseId.equals(c.getId())).findFirst().get();
        assertThat(receivedCourse.getOnlineCourseConfiguration()).as("Online course configuration is lazily loaded").isNull();
    }

    // Test
    public void testUpdateOnlineCourseConfiguration() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        ModelFactory.generateOnlineCourseConfiguration(course, "key", "secret", "validprefix", null);

        MvcResult result = request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        OnlineCourseConfiguration onlineCourseConfiguration = createdCourse.getOnlineCourseConfiguration();
        onlineCourseConfiguration.setLtiKey("changedKey");
        onlineCourseConfiguration.setLtiSecret("changedSecret");
        onlineCourseConfiguration.setUserPrefix("changedUserPrefix");

        result = request.getMvc().perform(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        Course actualCourse = courseRepo.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(updatedCourse.getId());

        assertThat(actualCourse.getOnlineCourseConfiguration().getLtiKey()).isEqualTo("changedKey");
        assertThat(actualCourse.getOnlineCourseConfiguration().getLtiSecret()).isEqualTo("changedSecret");
        assertThat(actualCourse.getOnlineCourseConfiguration().getUserPrefix()).isEqualTo("changedUserPrefix");
    }

    // Test
    public void testUpdateCourseRemoveOnlineCourseConfiguration() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        ModelFactory.generateOnlineCourseConfiguration(course, "key", "secret", "validprefix", null);

        MvcResult result = request.getMvc().perform(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        createdCourse.setOnlineCourse(false);
        createdCourse.setOnlineCourseConfiguration(null);
        result = request.getMvc().perform(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        Course courseWithoutOnlineConfiguration = courseRepo.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(updatedCourse.getId());
        assertThat(courseWithoutOnlineConfiguration.getOnlineCourseConfiguration()).isNull();
    }

    // Test
    public void testDeleteCourseDeletesOnlineConfiguration() throws Exception {
        Course course = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        ModelFactory.generateOnlineCourseConfiguration(course, "key", "secret", "validprefix", null);
        course = courseRepo.save(course);

        request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);

        assertThat(onlineCourseConfigurationRepository.findById(course.getOnlineCourseConfiguration().getId())).isNotPresent();
    }

    public MockHttpServletRequestBuilder buildCreateCourse(@NotNull Course course) throws JsonProcessingException {
        return buildCreateCourse(course, null);
    }

    public MockHttpServletRequestBuilder buildCreateCourse(@NotNull Course course, String fileContent) throws JsonProcessingException {
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsString(course).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/admin/courses").file(coursePart);
        if (fileContent != null) {
            var filePart = new MockMultipartFile("file", "placeholderName.png", MediaType.IMAGE_PNG_VALUE, fileContent.getBytes());
            builder.file(filePart);
        }
        return builder.contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    public MockHttpServletRequestBuilder buildUpdateCourse(long id, @NotNull Course course) throws JsonProcessingException {
        return buildUpdateCourse(id, course, null);
    }

    public MockHttpServletRequestBuilder buildUpdateCourse(long id, @NotNull Course course, String fileContent) throws JsonProcessingException {
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsString(course).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/courses/" + id).file(coursePart);
        if (fileContent != null) {
            var filePart = new MockMultipartFile("file", "placeholderName.png", MediaType.IMAGE_PNG_VALUE, fileContent.getBytes());
            builder.file(filePart);
        }
        return builder.contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private Course createCourseWithCourseImageAndReturn() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());

        request.getMvc().perform(buildCreateCourse(course, "testIcon")).andExpect(status().isCreated());

        List<Course> courses = courseRepo.findAll();
        assertThat(courses).as("Course got stored").hasSize(1);
        var createdCourse = courses.get(0);
        assertThat(createdCourse.getCourseIcon()).as("Course icon got stored").isNotNull();

        return createdCourse;
    }

    /**
     * Test courseIcon of Course and the file is deleted when updating courseIcon of a Course to null
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    public void testEditCourseRemoveExistingIcon() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);

        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        byte[] iconBytes = "icon".getBytes();
        MockMultipartFile iconFile = new MockMultipartFile("file", "icon.png", MediaType.APPLICATION_JSON_VALUE, iconBytes);
        String iconPath = fileService.handleSaveFile(iconFile, false, false);
        iconPath = fileService.manageFilesForUpdatedFilePath(null, iconPath, FilePathService.getCourseIconFilePath(), course.getId());
        course.setCourseIcon(iconPath);
        course = courseRepo.save(course);
        iconPath = iconPath.replace(Constants.FILEPATH_ID_PLACEHOLDER, course.getId().toString());
        assertThat(course.getCourseIcon()).as("course icon was set correctly").isEqualTo(iconPath);

        course.setCourseIcon(null);
        request.putWithMultipartFile("/api/courses/" + course.getId(), course, "course", null, Course.class, HttpStatus.OK);

        course = courseRepo.findByIdElseThrow(course.getId());
        assertThat(course.getCourseIcon()).as("course icon was deleted correctly").isNull();
        assertThat(fileService.getFileForPath(fileService.actualPathForPublicPath(iconPath))).as("course icon file was deleted correctly").isNull();
    }
}
