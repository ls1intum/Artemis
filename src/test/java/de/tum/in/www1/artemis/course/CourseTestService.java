package de.tum.in.www1.artemis.course;

import static de.tum.in.www1.artemis.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import javax.imageio.ImageIO;

import org.assertj.core.data.Offset;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.assessment.ComplaintUtilService;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.DefaultChannelType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.CustomAuditEventRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LtiPlatformConfigurationRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;
import de.tum.in.www1.artemis.service.export.CourseExamExportService;
import de.tum.in.www1.artemis.service.export.DataExportUtil;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.TestResourceUtils;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CourseForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.CourseForImportDTO;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementDetailViewDTO;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementOverviewStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.CoursesForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.OnlineCourseDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentUpdateDTO;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.user.UserNameAndLoginDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;

@Service
public class CourseTestService {

    @Value("${artemis.course-archives-path}")
    private Path courseArchivesDirPath;

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
    private ComplaintUtilService complaintUtilService;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Autowired
    private LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private LearningPathRepository learningPathRepository;

    @Autowired
    private ParticipantScoreScheduleService participantScoreScheduleService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    private static final int numberOfStudents = 8;

    private static final int numberOfTutors = 5;

    private static final int numberOfEditors = 1;

    private static final int numberOfInstructors = 1;

    private MockDelegate mockDelegate;

    private String userPrefix;

    public void setup(String userPrefix, MockDelegate mockDelegate) {
        this.userPrefix = userPrefix;
        this.mockDelegate = mockDelegate;

        userUtilService.addUsers(userPrefix, numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(userPrefix + "tutor6");
        userUtilService.createAndSaveUser(userPrefix + "instructor2");

        User customUser = userUtilService.createAndSaveUser(userPrefix + "custom1");
        customUser.setGroups(Set.of(userPrefix + "customGroup"));
        userRepo.save(customUser);
    }

    private void adjustUserGroupsToCustomGroups(String suffix) {
        userUtilService.adjustUserGroupsToCustomGroups(userPrefix, suffix, numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);
    }

    public void adjustUserGroupsToCustomGroups() {
        adjustUserGroupsToCustomGroups("");
    }

    private void adjustCourseGroups(Course course, String suffix) {
        course.setStudentGroupName(userPrefix + "student" + suffix);
        course.setTeachingAssistantGroupName(userPrefix + "tutor" + suffix);
        course.setEditorGroupName(userPrefix + "editor" + suffix);
        course.setInstructorGroupName(userPrefix + "instructor" + suffix);
        courseRepo.save(course);
    }

    // Test
    public void testCreateCourseWithPermission() throws Exception {
        assertThatThrownBy(() -> courseRepo.findByIdElseThrow(Long.MAX_VALUE)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> courseRepo.findByIdWithExercisesAndLecturesElseThrow(Long.MAX_VALUE)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> courseRepo.findWithEagerOrganizationsElseThrow(Long.MAX_VALUE)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> courseRepo.findByIdWithEagerExercisesElseThrow(Long.MAX_VALUE)).isInstanceOf(EntityNotFoundException.class);

        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());

        var result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        course = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        courseRepo.findByIdElseThrow(course.getId());

        course = CourseFactory.generateCourse(1L, null, null, new HashSet<>());
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testCreateCourseWithSameShortName() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course1.setShortName("shortName");
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultInstructorGroupName());

        var result = request.performMvcRequest(buildCreateCourse(course1)).andExpect(status().isCreated()).andReturn();
        course1 = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        assertThat(courseRepo.findByIdElseThrow(course1.getId())).isNotNull();

        Course course2 = CourseFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course2.setShortName("shortName");
        request.performMvcRequest(buildCreateCourse(course2)).andExpect(status().isBadRequest());
        assertThat(courseRepo.findAllByShortName(course2.getShortName())).as("Course has not been stored").hasSize(1);
    }

    // Test
    private void testCreateCourseWithNegativeValue(Course course) throws Exception {
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsString(course).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/admin/courses").file(coursePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.performMvcRequest(builder).andExpect(status().isBadRequest());
        List<Course> repoContent = courseRepo.findAllByShortName(course.getShortName());
        assertThat(repoContent).as("Course has not been stored").isEmpty();
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplainNumber() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaints(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplainTimeDays() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaintTimeDays(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxTeamComplainNumber() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxTeamComplaints(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplaintTextLimit() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaintTextLimit(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithNegativeMaxComplaintResponseTextLimit() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course.setMaxComplaintResponseTextLimit(-1);
        testCreateCourseWithNegativeValue(course);
    }

    // Test
    public void testCreateCourseWithModifiedMaxComplainTimeDaysAndMaxComplains() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        course.setMaxComplaintTimeDays(0);
        course.setMaxComplaints(1);
        course.setMaxTeamComplaints(0);
        course.setMaxRequestMoreFeedbackTimeDays(0);
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
        List<Course> repoContent = courseRepo.findAllByShortName(course.getShortName());
        assertThat(repoContent).as("Course has not been stored").isEmpty();

        // change configuration
        course.setMaxComplaintTimeDays(1);
        course.setMaxComplaints(0);
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
        repoContent = courseRepo.findAllByShortName(course.getShortName());
        assertThat(repoContent).as("Course has not been stored").isEmpty();

        // change configuration again
        course.setMaxComplaintTimeDays(0);
        course.setMaxRequestMoreFeedbackTimeDays(-1);
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
        repoContent = courseRepo.findAllByShortName(course.getShortName());
        assertThat(repoContent).as("Course has not been stored").isEmpty();
    }

    // Test
    public void testCreateCourseWithCustomNonExistingGroupNames() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course.setStudentGroupName("StudentGroupName");
        course.setTeachingAssistantGroupName("TeachingAssistantGroupName");
        course.setEditorGroupName("EditorGroupName");
        course.setInstructorGroupName("InstructorGroupName");
        var result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        course = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        courseRepo.findByIdElseThrow(course.getId());
        List<Course> repoContent = courseRepo.findAllByShortName(course.getShortName());
        assertThat(repoContent).as("Course got stored").hasSize(1);
    }

    // Test
    public void testCreateCourseWithOptions() throws Exception {
        // Generate POST Request Body with maxComplaints = 5, maxComplaintTimeDays = 14, communication = false, messaging = true
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), null, null, null, null, 5, 5, 14, 2000, 2000, false, true, 0);

        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());
        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        course = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        // Because the courseId is automatically generated we cannot use the findById method to retrieve the saved course.
        Course getFromRepo = courseRepo.findByIdElseThrow(course.getId());
        assertThat(getFromRepo.getMaxComplaints()).as("Course has right maxComplaints Value").isEqualTo(5);
        assertThat(getFromRepo.getMaxComplaintTimeDays()).as("Course has right maxComplaintTimeDays Value").isEqualTo(14);
        assertThat(getFromRepo.getCourseInformationSharingConfiguration()).as("Course has right information sharing config value")
                .isEqualTo(CourseInformationSharingConfiguration.MESSAGING_ONLY);
        assertThat(getFromRepo.getRequestMoreFeedbackEnabled()).as("Course has right requestMoreFeedbackEnabled value").isFalse();

        // Test edit course
        course.setId(getFromRepo.getId());
        course.setMaxComplaints(1);
        course.setMaxComplaintTimeDays(7);
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        course.setMaxRequestMoreFeedbackTimeDays(7);
        result = request.performMvcRequest(buildUpdateCourse(getFromRepo.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        assertThat(updatedCourse.getMaxComplaints()).as("maxComplaints Value updated successfully").isEqualTo(course.getMaxComplaints());
        assertThat(updatedCourse.getMaxComplaintTimeDays()).as("maxComplaintTimeDays Value updated successfully").isEqualTo(course.getMaxComplaintTimeDays());
        assertThat(updatedCourse.getCourseInformationSharingConfiguration()).as("information sharing config value updated successfully")
                .isEqualTo(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        assertThat(updatedCourse.getRequestMoreFeedbackEnabled()).as("Course has right requestMoreFeedbackEnabled Value").isTrue();
    }

    // Test
    public void testDeleteCourseWithPermission() throws Exception {
        // add to new list so that we can add another course with ARTEMIS_GROUP_DEFAULT_PREFIX so that delete group will be tested properly
        List<Course> courses = new ArrayList<>(courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, true, 5));
        Course course3 = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(4), new HashSet<>(), null, null, null, null, true);
        course3.setStudentGroupName(course3.getDefaultStudentGroupName());
        course3.setTeachingAssistantGroupName(course3.getDefaultTeachingAssistantGroupName());
        course3.setEditorGroupName(course3.getDefaultEditorGroupName());
        course3.setInstructorGroupName(course3.getDefaultInstructorGroupName());
        course3 = courseRepo.save(course3);
        courses.add(course3);
        addConversationsToCourse(course3);
        examUtilService.addExamWithExerciseGroup(courses.get(0), true);
        // mock certain requests
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
                    programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
                    programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
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

        for (Course course : courses) {
            assertThat(courseRepo.findById(course.getId())).as("All courses deleted").isEmpty();
            // assertThat(notificationRepo.findAll()).as("All notifications are deleted").isEmpty(); // TODO: Readd this and check only for notifications of course
            assertThat(examRepo.findByCourseId(course.getId())).as("All exams are deleted").isEmpty();
            assertThat(exerciseRepo.findAllExercisesByCourseId(course.getId())).as("All Exercises are deleted").isEmpty();
            assertThat(lectureRepo.findAllByCourseIdWithAttachments(course.getId())).as("All Lectures are deleted").isEmpty();
            assertThat(conversationRepository.findAllByCourseId(course.getId())).as("All Conversations are deleted").isEmpty();
        }
    }

    private void addConversationsToCourse(Course course3) throws Exception {
        var channelDTO = new ChannelDTO();
        channelDTO.setName("name test");
        channelDTO.setIsPublic(true);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setDescription("general channel");

        var chat = request.postWithResponseBody("/api/courses/" + course3.getId() + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        var user = userUtilService.getUserByLogin(userPrefix + "student1");

        var participant = new ConversationParticipant();
        participant.setConversation(conversationRepository.findByIdElseThrow(chat.getId()));
        participant.setIsModerator(false);
        participant.setUser(user);
        conversationParticipantRepository.save(participant);
    }

    // Test
    public void testDeleteNotExistingCourse() throws Exception {
        request.delete("/api/admin/courses/-1", HttpStatus.NOT_FOUND);
    }

    // Test
    public void testCreateCourseWithoutPermission() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isForbidden());
        assertThat(courseRepo.findAllByShortName(course.getShortName())).as("Course got stored").isEmpty();
    }

    // Test
    public void testCreateCourseWithWrongShortName() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course.setShortName("`badName~");
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testCreateCourseWithDefaultChannels() throws Exception {

        Course course1 = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        course1.setShortName("testdefaultchannels");
        course1.setEnrollmentEnabled(true);
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course1.getDefaultInstructorGroupName());

        var student = userRepo.findOneByLogin(userPrefix + "student1").orElseThrow();
        mockDelegate.mockAddUserToGroupInUserManagement(student, course1.getDefaultStudentGroupName(), false);

        var instructor1 = userRepo.findOneByLogin(userPrefix + "instructor1").orElseThrow();
        mockDelegate.mockAddUserToGroupInUserManagement(instructor1, course1.getDefaultInstructorGroupName(), false);

        var result = request.performMvcRequest(buildCreateCourse(course1)).andExpect(status().isCreated()).andReturn();
        Course course2 = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        assertThat(courseRepo.findByIdElseThrow(course2.getId())).isNotNull();

        request.postWithoutLocation("/api/courses/" + course2.getId() + "/students/" + userPrefix + "student1", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/courses/" + course2.getId() + "/instructors/" + userPrefix + "instructor1", null, HttpStatus.OK, null);

        // Check if all default channels are created
        await().untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            var channels = channelRepository.findChannelsByCourseId(course2.getId());
            assertThat(channels).hasSize(DefaultChannelType.values().length);
            channels.forEach(channel -> assertThat(Arrays.stream(DefaultChannelType.values()).map(DefaultChannelType::getName)).contains(channel.getName()));
        });

    }

    // Test
    public void testUpdateCourseIsEmpty() throws Exception {
        Course course = CourseFactory.generateCourse(1042001L, null, null, new HashSet<>());
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testEditCourseWithPermission() throws Exception {
        Course course = CourseFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        course.setTitle("Test Course");
        course.setStartDate(ZonedDateTime.now().minusDays(5));
        course.setEndDate(ZonedDateTime.now().plusDays(5));
        MvcResult result = request.performMvcRequest(buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        assertThat(updatedCourse.getShortName()).as("short name was changed correctly").isEqualTo(course.getShortName());
        assertThat(updatedCourse.getTitle()).as("title was changed correctly").isEqualTo(course.getTitle());
        assertThat(updatedCourse.getStartDate()).as("start date was changed correctly").isEqualTo(course.getStartDate());
        assertThat(updatedCourse.getEndDate()).as("end date was changed correctly").isEqualTo(course.getEndDate());
    }

    // Test
    public void testEditCourseShouldPreserveAssociations() throws Exception {
        Course course = courseUtilService.createCourseWithOrganizations();
        course = courseRepo.save(course);

        Set<Organization> organizations = course.getOrganizations();

        Set<Competency> competencies = new HashSet<>();
        competencies.add(competencyUtilService.createCompetency(course));
        course.setCompetencies(competencies);
        course = courseRepo.save(course);

        Set<Competency> prerequisites = new HashSet<>();
        prerequisites.add(competencyUtilService.createCompetency(courseUtilService.createCourse()));
        course.setPrerequisites(prerequisites);
        course = courseRepo.save(course);

        request.performMvcRequest(buildUpdateCourse(course.getId(), course)).andExpect(status().isOk());

        Course updatedCourse = courseRepo.findByIdForUpdateElseThrow(course.getId());
        assertThat(updatedCourse.getOrganizations()).containsExactlyElementsOf(organizations);
        assertThat(updatedCourse.getCompetencies()).containsExactlyElementsOf(competencies);
        assertThat(updatedCourse.getPrerequisites()).containsExactlyElementsOf(prerequisites);
    }

    // Test
    public void testUpdateCourseGroups() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldEditorGroup = course.getEditorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setEditorGroupName("new-editor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        // Create instructor in the course
        User user = userUtilService.createAndSaveUser("instructor11");
        user.setGroups(Set.of("new-instructor-group"));
        userRepo.save(user);

        // Create teaching assisstant in the course
        user = UserFactory.generateActivatedUser("teaching-assisstant11");
        user.setGroups(Set.of("new-ta-group"));
        userRepo.save(user);

        mockDelegate.mockUpdateCoursePermissions(course, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
        MvcResult result = request.performMvcRequest(buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
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
        request.performMvcRequest(buildUpdateCourse(createdCourse.getId(), createdCourse, "newTestIcon")).andExpect(status().isOk());

        var updatedCourse = courseRepo.findByIdElseThrow(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isNotEqualTo(courseIcon).isNotNull();
        assertThat(updatedCourse.getDescription()).isEqualTo("new description");
    }

    // Test
    public void testCreateAndUpdateCourseWithPersistentCourseImageOnUpdate() throws Exception {
        Course createdCourse = createCourseWithCourseImageAndReturn();

        // Update course
        request.performMvcRequest(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk());

        var updatedCourse = courseRepo.findByIdElseThrow(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isEqualTo(createdCourse.getCourseIcon());
    }

    // Test
    public void testCreateAndUpdateCourseWithRemoveCourseImageOnUpdate() throws Exception {
        Course createdCourse = createCourseWithCourseImageAndReturn();
        createdCourse.setCourseIcon(null);

        // Update course
        request.performMvcRequest(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk());

        var updatedCourse = courseRepo.findByIdElseThrow(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isNull();
    }

    // Test
    public void testCreateAndUpdateCourseWithSetNewImageDespiteRemoval() throws Exception {
        Course createdCourse = createCourseWithCourseImageAndReturn();
        var courseIcon = createdCourse.getCourseIcon();
        createdCourse.setCourseIcon(null);

        // Update course
        request.performMvcRequest(buildUpdateCourse(createdCourse.getId(), createdCourse, "newTestIcon")).andExpect(status().isOk());

        var updatedCourse = courseRepo.findByIdElseThrow(createdCourse.getId());
        assertThat(updatedCourse.getCourseIcon()).isNotNull().isNotEqualTo(courseIcon);
    }

    // Test
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToRemoveUser() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldEditorGroup = course.getEditorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setInstructorGroupName("new-editor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        mockDelegate.mockFailUpdateCoursePermissionsInCi(course, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, false, true);
        request.performMvcRequest(buildUpdateCourse(course.getId(), course)).andExpect(status().isInternalServerError()).andReturn();
    }

    // Test
    public void testUpdateCourseGroups_InExternalCiUserManagement_failToAddUser() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var oldInstructorGroup = course.getInstructorGroupName();
        var oldEditorGroup = course.getEditorGroupName();
        var oldTeachingAssistantGroup = course.getTeachingAssistantGroupName();

        course.setInstructorGroupName("new-instructor-group");
        course.setInstructorGroupName("new-editor-group");
        course.setTeachingAssistantGroupName("new-ta-group");

        mockDelegate.mockFailUpdateCoursePermissionsInCi(course, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, true, false);
        request.performMvcRequest(buildUpdateCourse(course.getId(), course)).andExpect(status().isInternalServerError());
    }

    // Test
    public void testGetCourseWithoutPermission() throws Exception {
        request.getList("/api/courses", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCourse_tutorNotInCourse() throws Exception {
        Course course = courseUtilService.createCourse();
        request.getList("/api/courses/" + course.getId(), HttpStatus.FORBIDDEN, Course.class);
        request.get("/api/courses/" + course.getId() + "/with-exercises", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCoursesWithPermission() throws Exception {
        List<Course> coursesCreated = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, false, 0);
        List<Course> courses = request.getList("/api/courses", HttpStatus.OK, Course.class);

        for (Course course : coursesCreated) {
            Optional<Course> found = courses.stream().filter(c -> Objects.equals(c.getId(), course.getId())).findFirst();
            assertThat(found).as("Course is available").isPresent();
            Course courseFound = found.orElseThrow();
            for (Exercise exercise : courseFound.getExercises()) {
                assertThat(exercise.getGradingInstructions()).as("Grading instructions are not filtered out").isNotNull();
                assertThat(exercise.getProblemStatement()).as("Problem statements are not filtered out").isNotNull();
            }
        }
    }

    // Test
    public void testGetCoursesWithQuizExercises() throws Exception {
        List<Course> coursesCreated = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, false, 0);
        Course activeCourse = coursesCreated.get(0);
        Course inactiveCourse = coursesCreated.get(1);

        List<Course> courses = request.getList("/api/courses/courses-with-quiz", HttpStatus.OK, Course.class);

        assertThat(courses.stream().filter(c -> Objects.equals(c.getId(), inactiveCourse.getId())).toList()).as("Inactive course was filtered out").isEmpty();

        Optional<Course> optionalCourse = courses.stream().filter(c -> Objects.equals(c.getId(), activeCourse.getId())).findFirst();
        assertThat(optionalCourse).as("Active course was not filtered").isPresent();
        Course activeCourseNotFiltered = optionalCourse.orElseThrow();

        for (Exercise exercise : activeCourseNotFiltered.getExercises()) {
            assertThat(exercise.getGradingInstructions()).as("Grading instructions are filtered out").isNull();
            assertThat(exercise.getProblemStatement()).as("Problem statements are filtered out").isNull();
        }
    }

    // Test
    public void testGetCourseForDashboard(boolean userRefresh) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLecturesAndLectureUnitsAndCompetencies(userPrefix, true, false, numberOfTutors);
        CourseForDashboardDTO receivedCourseForDashboard = request.get("/api/courses/" + courses.get(0).getId() + "/for-dashboard?refresh=" + userRefresh, HttpStatus.OK,
                CourseForDashboardDTO.class);
        Course receivedCourse = receivedCourseForDashboard.course();

        // Test that the received course has five exercises
        assertThat(receivedCourse.getExercises()).as("Five exercises are returned").hasSize(5);
        // Test that the received course has two lectures
        assertThat(receivedCourse.getLectures()).as("Two lectures are returned").hasSize(2);
        // Test that the received course has two competencies

        assertThat(receivedCourse.getCompetencies()).isEmpty();
        assertThat(receivedCourse.getPrerequisites()).isEmpty();
        assertThat(receivedCourse.getTutorialGroups()).isEmpty();
        assertThat(receivedCourse.getTutorialGroupsConfiguration()).isNull();

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

    private Course createCourseWithEnrollmentEnabled(boolean enrollmentEnabled) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLecturesAndLectureUnitsAndCompetencies(userPrefix, true, false, numberOfTutors);
        Course course = courses.get(0);
        course.setEnrollmentEnabled(enrollmentEnabled);
        courseRepo.save(course);
        return course;
    }

    private User removeAllGroupsFromStudent1() {
        User student = userUtilService.getUserByLogin(userPrefix + "student1");
        // remove student from all courses so that they are not already enrolled
        student.setGroups(new HashSet<>());
        userRepo.save(student);
        return student;
    }

    // Test
    public void testGetCourseForDashboardAccessDenied(boolean userRefresh) throws Exception {
        Course course = createCourseWithEnrollmentEnabled(true);
        removeAllGroupsFromStudent1();
        request.get("/api/courses/" + course.getId() + "/for-dashboard?refresh=" + userRefresh, HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCourseForDashboardForbiddenWithEnrollmentPossible() throws Exception {
        Course course = createCourseWithEnrollmentEnabled(true);
        removeAllGroupsFromStudent1();
        // still expect forbidden (403) from endpoint (only now the skipAlert flag will be set)
        request.get("/api/courses/" + course.getId() + "/for-dashboard", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCourseForEnrollment() throws Exception {
        Course course = createCourseWithEnrollmentEnabled(true);
        // remove student from course so that they are not already enrolled
        course.setStudentGroupName("someNonExistingStudentGroupName");
        courseRepo.save(course);
        request.get("/api/courses/" + course.getId() + "/for-enrollment", HttpStatus.OK, Course.class);
    }

    // Test
    public void testGetCourseForEnrollmentAccessDenied() throws Exception {
        Course course = createCourseWithEnrollmentEnabled(false);
        removeAllGroupsFromStudent1();
        request.get("/api/courses/" + course.getId() + "/for-enrollment", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetAllCoursesForDashboardExams(boolean userRefresh) throws Exception {
        User customUser = userRepo.findOneWithGroupsByLogin(userPrefix + "custom1").orElseThrow();
        User student = userRepo.findOneWithGroupsByLogin(userPrefix + "student1").orElseThrow();
        String suffix = "instructorExam";
        adjustUserGroupsToCustomGroups(suffix);

        // Custom user is student in 0 and 1, tutor in 2, editor in 3 and instructor in 4
        Course[] courses = new Course[5];
        courses[0] = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "customGroup", userPrefix + "tutor" + suffix, userPrefix + "editor" + suffix,
                userPrefix + "instructor" + suffix);
        courses[1] = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "customGroup", userPrefix + "tutor" + suffix, userPrefix + "editor" + suffix,
                userPrefix + "instructor" + suffix);
        courses[2] = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student" + suffix, userPrefix + "customGroup", userPrefix + "editor" + suffix,
                userPrefix + "instructor" + suffix);
        courses[3] = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student" + suffix, userPrefix + "tutor" + suffix, userPrefix + "customGroup",
                userPrefix + "instructor" + suffix);
        courses[4] = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student" + suffix, userPrefix + "tutor" + suffix, userPrefix + "editor" + suffix,
                userPrefix + "customGroup");

        for (int i = 0; i < courses.length; i++) {
            courses[i] = courseRepo.save(courses[i]);
            Exam examRegistered = ExamFactory.generateExam(courses[i]);
            Exam examUnregistered = ExamFactory.generateExam(courses[i]);
            Exam testExam = ExamFactory.generateTestExam(courses[i]);
            if (i == 0) {
                examRegistered.setVisibleDate(ZonedDateTime.now().plusHours(1));
                examUnregistered.setVisibleDate(ZonedDateTime.now().plusHours(1));
                testExam.setVisibleDate(ZonedDateTime.now().plusHours(1));
            }
            examRepo.saveAll(List.of(examRegistered, examUnregistered, testExam));

            if (i < 2) {
                ExamUser registeredCustomUser = new ExamUser();
                registeredCustomUser.setUser(customUser);
                registeredCustomUser.setExam(examRegistered);
                examRegistered.addExamUser(examUserRepository.save(registeredCustomUser));
            }
            ExamUser registeredStudent = new ExamUser();
            registeredStudent.setUser(student);
            registeredStudent.setExam(examRegistered);
            examRegistered.addExamUser(examUserRepository.save(registeredStudent));

            CourseForDashboardDTO receivedCourseForDashboard = request.get("/api/courses/" + courses[i].getId() + "/for-dashboard?refresh=" + userRefresh, HttpStatus.OK,
                    CourseForDashboardDTO.class);
            Course receivedCourse = receivedCourseForDashboard.course();
            assertThat(receivedCourse).isNotNull();
            if (i == 0) {
                assertThat(receivedCourse.getExams()).isEmpty();
            }
            else if (i == 1) {
                assertThat(receivedCourse.getExams()).containsExactlyInAnyOrder(examRegistered, testExam);
            }
            else {
                assertThat(receivedCourse.getExams()).containsExactlyInAnyOrder(examUnregistered, examRegistered, testExam);
            }
        }
        var receivedCoursesForDashboard = request.get("/api/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
        List<Course> receivedCourses = receivedCoursesForDashboard.courses().stream().map(CourseForDashboardDTO::course).toList();
        for (int i = 0; i < courses.length; i++) {
            Course receivedCourse = null;
            for (Course course : receivedCourses) {
                if (course.getId().equals(courses[i].getId())) {
                    receivedCourse = course;
                }
            }
            assertThat(receivedCourse).isNotNull();
            if (i == 0) {
                assertThat(receivedCourse.getExams()).isEmpty();
            }
            else {
                assertThat(receivedCourse.getExams()).hasSize(0);
                assertThat(receivedCourse.getNumberOfExams()).isEqualTo(3);
            }
        }
    }

    // Test
    public void testGetCoursesForDashboardPracticeRepositories() throws Exception {
        User student1 = userUtilService.getUserByLogin(userPrefix + "student1");

        Course course = courseUtilService.createCourse();
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusMinutes(90));

        programmingExerciseRepository.save(programmingExercise);
        Result gradedResult = participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, userPrefix + "student1");
        gradedResult.completionDate(ZonedDateTime.now().minusHours(3)).assessmentType(AssessmentType.AUTOMATIC).score(42D);
        resultRepo.save(gradedResult);
        StudentParticipation gradedParticipation = (StudentParticipation) gradedResult.getParticipation();
        gradedParticipation.setInitializationState(InitializationState.FINISHED);
        participationRepository.save(gradedParticipation);
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(gradedResult, gradedParticipation, "asdf");
        StudentParticipation practiceParticipation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise,
                student1);
        practiceParticipation.setPracticeMode(true);
        participationRepository.save(practiceParticipation);
        Result practiceResult = participationUtilService.addResultToParticipation(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(1), practiceParticipation);
        practiceResult.setRated(false);
        resultRepo.save(practiceResult);
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(practiceResult, practiceParticipation, "ghjk");
        participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, userPrefix + "student2");

        var receivedCoursesForDashboard = request.get("/api/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
        CourseForDashboardDTO receivedCourseForDashboard = request.get("/api/courses/" + course.getId() + "/for-dashboard", HttpStatus.OK, CourseForDashboardDTO.class);
        CourseForDashboardDTO receivedCourseForDashboardFromGeneralCall = receivedCoursesForDashboard.courses().stream().filter(dto -> dto.course().getId().equals(course.getId()))
                .findFirst().orElseThrow();

        assertThat(receivedCourseForDashboardFromGeneralCall.participationResults()).hasSize(1);
        assertThat(receivedCourseForDashboard.participationResults()).hasSize(1);

        assertThat(receivedCourseForDashboardFromGeneralCall.course().getExercises().stream().findFirst().orElseThrow().getStudentParticipations()).hasSize(1);
        assertThat(receivedCourseForDashboard.course().getExercises().stream().findFirst().orElseThrow().getStudentParticipations()).hasSize(2);

        assertThat(receivedCourseForDashboardFromGeneralCall.totalScores().studentScores().absoluteScore()).isEqualTo(0.42 * 42, Offset.offset(0.1));
        assertThat(receivedCourseForDashboard.totalScores().studentScores().absoluteScore()).isEqualTo(0.42 * 42, Offset.offset(0.1));
        assertThat(receivedCourseForDashboardFromGeneralCall.programmingScores().studentScores().absoluteScore()).isEqualTo(0.42 * 42, Offset.offset(0.1));
        assertThat(receivedCourseForDashboard.programmingScores().studentScores().absoluteScore()).isEqualTo(0.42 * 42, Offset.offset(0.1));
    }

    // Test
    public void testGetAllCoursesForDashboard() throws Exception {
        String suffix = "getall";
        adjustUserGroupsToCustomGroups(suffix);
        // Note: with the suffix, we reduce the amount of courses loaded below to prevent test issues
        List<Course> coursesCreated = lectureUtilService.createCoursesWithExercisesAndLecturesAndLectureUnits(userPrefix, true, false, numberOfTutors);
        for (var course : coursesCreated) {
            courseUtilService.updateCourseGroups(userPrefix, course, suffix);
        }

        // Perform the request that is being tested here
        var coursesForDashboard = request.get("/api/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
        List<Course> courses = coursesForDashboard.courses().stream().map(CourseForDashboardDTO::course).toList();

        Course activeCourse = coursesCreated.get(0);
        Course inactiveCourse = coursesCreated.get(1);

        // Test that the prepared inactive course was filtered out
        assertThat(courses.stream().filter(c -> Objects.equals(c.getId(), inactiveCourse.getId())).toList()).as("Inactive course was filtered out").isEmpty();

        Optional<Course> optionalCourse = courses.stream().filter(c -> Objects.equals(c.getId(), activeCourse.getId())).findFirst();
        assertThat(optionalCourse).as("Active course was not filtered").isPresent();
        Course activeCourseNotFiltered = optionalCourse.orElseThrow();

        // Test that the remaining course has five exercises
        assertThat(activeCourseNotFiltered.getExercises()).as("Five exercises are returned").hasSize(5);

        // Iterate over all exercises of the remaining course
        for (Exercise exercise : activeCourseNotFiltered.getExercises()) {
            if (exercise.getExerciseType() == ExerciseType.PROGRAMMING) {
                assertThat(exercise.getStudentParticipations()).as("At most two participations for programming exercise").hasSizeLessThanOrEqualTo(2);
            }
            else {
                assertThat(exercise.getStudentParticipations()).as("At most one participation for exercise").hasSizeLessThanOrEqualTo(1);
            }
            if (!exercise.getStudentParticipations().isEmpty()) {
                // Buffer participation so that null checking is easier.
                exercise.getStudentParticipations().forEach(participation -> {
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
                                assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isIn("model1", "model2");
                            }
                        }
                    }
                });
            }
        }
    }

    // Test
    public void testGetCoursesWithoutActiveExercises() throws Exception {
        String suffix = "active";
        adjustUserGroupsToCustomGroups(suffix);
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student" + suffix, userPrefix + "tutor" + suffix,
                userPrefix + "editor" + suffix, userPrefix + "instructor" + suffix);
        course = courseRepo.save(course);
        var coursesForDashboard = request.get("/api/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
        List<Course> courses = coursesForDashboard.courses().stream().map(CourseForDashboardDTO::course).toList();
        final var finalCourse = course;
        Course courseInList = courses.stream().filter(c -> c.getId().equals(finalCourse.getId())).findFirst().orElse(null);
        assertThat(courseInList).isNotNull();
        assertThat(courseInList.getExercises()).as("Course doesn't have any exercises").isEmpty();
    }

    // Test
    public void testGetCoursesAccurateTimezoneEvaluation() throws Exception {
        String suffix = "timezone";
        adjustUserGroupsToCustomGroups(suffix);
        Course courseActive = CourseFactory.generateCourse(null, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25), new HashSet<>(),
                userPrefix + "student" + suffix, userPrefix + "tutor" + suffix, userPrefix + "editor" + suffix, userPrefix + "instructor" + suffix);
        Course courseNotActivePast = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusMinutes(25), new HashSet<>(),
                userPrefix + "student" + suffix, userPrefix + "tutor" + suffix, userPrefix + "editor" + suffix, userPrefix + "instructor" + suffix);
        Course courseNotActiveFuture = CourseFactory.generateCourse(null, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusDays(5), new HashSet<>(),
                userPrefix + "student" + suffix, userPrefix + "tutor" + suffix, userPrefix + "editor" + suffix, userPrefix + "instructor" + suffix);
        courseActive = courseRepo.save(courseActive);
        courseNotActivePast = courseRepo.save(courseNotActivePast);
        courseNotActiveFuture = courseRepo.save(courseNotActiveFuture);
        var coursesForDashboard = request.get("/api/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
        List<Course> courses = coursesForDashboard.courses().stream().map(CourseForDashboardDTO::course).toList();

        long courseNotActivePastId = courseNotActivePast.getId();
        long courseNotActiveFutureId = courseNotActiveFuture.getId();
        assertThat(courses.stream().filter(c -> Objects.equals(c.getId(), courseNotActivePastId)).toList()).as("Past inactive course was filtered out").isEmpty();
        assertThat(courses.stream().filter(c -> Objects.equals(c.getId(), courseNotActiveFutureId)).toList()).as("Future inactive course was filtered out").isEmpty();

        Course finalCourseActive = courseActive;
        Optional<Course> optionalCourse = courses.stream().filter(c -> Objects.equals(c.getId(), finalCourseActive.getId())).findFirst();
        assertThat(optionalCourse).as("Active course was not filtered").isPresent();

        List<Course> coursesForNotifications = request.getList("/api/courses/for-notifications", HttpStatus.OK, Course.class);

        assertThat(coursesForNotifications.stream().filter(c -> Objects.equals(c.getId(), courseNotActivePastId)).toList()).as("Past inactive course was filtered out").isEmpty();
        assertThat(coursesForNotifications.stream().filter(c -> Objects.equals(c.getId(), courseNotActiveFutureId)).toList()).as("Future inactive course was filtered out")
                .isEmpty();

        optionalCourse = coursesForNotifications.stream().filter(c -> Objects.equals(c.getId(), finalCourseActive.getId())).findFirst();
        assertThat(optionalCourse).as("Active course was not filtered").isPresent();
    }

    // Test
    public void testGetCourseWithOrganizations() throws Exception {
        Course courseWithOrganization = courseUtilService.createCourseWithOrganizations();
        Course course = request.get("/api/courses/" + courseWithOrganization.getId() + "/with-organizations", HttpStatus.OK, Course.class);
        assertThat(course.getOrganizations()).isEqualTo(courseWithOrganization.getOrganizations());
        assertThat(course.getOrganizations()).isNotEmpty();
    }

    // Test
    public void testGetAllCoursesWithUserStats() throws Exception {
        adjustUserGroupsToCustomGroups();
        List<Course> testCourses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, false, 0);
        Course course = testCourses.get(0);
        course.setStudentGroupName(userPrefix + "student");
        course.setTeachingAssistantGroupName(userPrefix + "tutor");
        course.setInstructorGroupName(userPrefix + "instructor");
        courseRepo.save(course);

        List<Course> receivedCourses = request.getList("/api/courses/with-user-stats", HttpStatus.OK, Course.class);

        Optional<Course> optionalCourse = receivedCourses.stream().filter(c -> Objects.equals(c.getId(), course.getId())).findFirst();
        assertThat(optionalCourse).as("Course is returned").isPresent();
        Course returnedCourse = optionalCourse.orElseThrow();

        assertThat(returnedCourse.getNumberOfStudents()).isEqualTo(numberOfStudents);
        assertThat(returnedCourse.getNumberOfTeachingAssistants()).isEqualTo(numberOfTutors);
        assertThat(returnedCourse.getNumberOfInstructors()).isEqualTo(numberOfInstructors);
    }

    // Test
    public void testGetCoursesForEnrollmentAndAccurateTimeZoneEvaluation() throws Exception {
        Course courseEnrollmentActiveEnrollmentEnabled = CourseFactory.generateCourse(1L, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25),
                new HashSet<>(), "testuser", "tutor", "editor", "instructor");
        courseEnrollmentActiveEnrollmentEnabled.setEnrollmentEnabled(true);
        courseEnrollmentActiveEnrollmentEnabled.setEnrollmentStartDate(ZonedDateTime.now().minusMinutes(25));
        courseEnrollmentActiveEnrollmentEnabled.setEnrollmentEndDate(ZonedDateTime.now().plusMinutes(25));

        Course courseEnrollmentActiveEnrollmentDisabled = CourseFactory.generateCourse(2L, ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25),
                new HashSet<>(), "testuser", "tutor", "editor", "instructor");
        courseEnrollmentActiveEnrollmentDisabled.setEnrollmentEnabled(false);
        courseEnrollmentActiveEnrollmentDisabled.setEnrollmentStartDate(ZonedDateTime.now().minusMinutes(25));
        courseEnrollmentActiveEnrollmentDisabled.setEnrollmentEndDate(ZonedDateTime.now().plusMinutes(25));

        Course courseEnrollmentNotActivePast = CourseFactory.generateCourse(3L, ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusMinutes(25), new HashSet<>(), "testuser",
                "tutor", "editor", "instructor");
        courseEnrollmentNotActivePast.setEnrollmentEnabled(true);
        courseEnrollmentNotActivePast.setEnrollmentStartDate(ZonedDateTime.now().minusDays(5));
        courseEnrollmentNotActivePast.setEnrollmentEndDate(ZonedDateTime.now().minusMinutes(25));

        Course courseEnrollmentNotActiveFuture = CourseFactory.generateCourse(4L, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusDays(5), new HashSet<>(), "testuser",
                "tutor", "editor", "instructor");
        courseEnrollmentNotActiveFuture.setEnrollmentEnabled(true);
        courseEnrollmentNotActiveFuture.setEnrollmentStartDate(ZonedDateTime.now().plusMinutes(25));
        courseEnrollmentNotActiveFuture.setEnrollmentEndDate(ZonedDateTime.now().plusDays(5));

        courseRepo.save(courseEnrollmentActiveEnrollmentEnabled);
        courseRepo.save(courseEnrollmentActiveEnrollmentDisabled);
        courseRepo.save(courseEnrollmentNotActivePast);
        courseRepo.save(courseEnrollmentNotActiveFuture);

        List<Course> courses = request.getList("/api/courses/for-enrollment", HttpStatus.OK, Course.class);
        assertThat(courses).as("Only active course is returned").contains(courseEnrollmentActiveEnrollmentEnabled);
        assertThat(courses).as("Inactive courses are not returned").doesNotContain(courseEnrollmentActiveEnrollmentDisabled, courseEnrollmentNotActivePast,
                courseEnrollmentNotActiveFuture);
    }

    // Test
    public void testGetCourseForAssessmentDashboardWithStats() throws Exception {
        List<Course> testCourses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, true, 5);
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
                // Mock data contains exactly one in-time submission for the text and programming exercise
                if (exercise instanceof TextExercise || exercise instanceof ProgrammingExercise) {
                    assertThat(exercise.getNumberOfSubmissions().inTime()).as("Number of in-time submissions is correct").isEqualTo(1);
                }
                // Mock data contains no submissions for the file upload exercise
                if (exercise instanceof FileUploadExercise) {
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
            long numberOfInTimeSubmissions = course.getId().equals(testCourses.get(0).getId()) ? 5 : 0; // course 1 has 5 submissions, course 2 has 0 submissions
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
        var testCourse = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, true, 5).get(0);
        var exercise = exerciseUtilService.getFirstExerciseWithType(testCourse, TextExercise.class);

        int[] ratings = { 3, 4, 5 };
        for (int i = 0; i < ratings.length; i++) {
            var submission = textExerciseUtilService.createSubmissionForTextExercise(exercise, userUtilService.getUserByLogin(userPrefix + "student" + (i + 1)), "text");
            var assessment = participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, null, 0.0, true).getLatestResult();
            participationUtilService.addRatingToResult(assessment, ratings[i]);
        }

        var responseCourse = request.get("/api/courses/" + testCourse.getId() + "/for-assessment-dashboard", HttpStatus.OK, Course.class);
        var responseExercise = exerciseUtilService.getFirstExerciseWithType(responseCourse, TextExercise.class);

        // Ensure that average rating and number of ratings is computed correctly
        var averageRating = Arrays.stream(ratings).mapToDouble(Double::valueOf).sum() / ratings.length;
        assertThat(responseExercise.getAverageRating()).isEqualTo(averageRating);
        assertThat(responseExercise.getNumberOfRatings()).isEqualTo(ratings.length);
    }

    // Test
    public void testGetCourseForInstructorDashboardWithStats_instructorNotInCourse() throws Exception {
        Course testCourse = courseUtilService.createCourse();
        request.get("/api/courses/" + testCourse.getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
    }

    // Test
    public void testGetCourseForAssessmentDashboardWithStats_tutorNotInCourse() throws Exception {
        Course testCourse = courseUtilService.createCourse();
        request.get("/api/courses/" + testCourse.getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
        request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.FORBIDDEN, StatsForDashboardDTO.class);
    }

    // Test
    public void testGetAllGroupsForAllCourses() throws Exception {
        Set<String> allGroups = new HashSet<>(request.getList("/api/admin/courses/groups", HttpStatus.OK, String.class));
        courseRepo.findAll().stream().findAny().ifPresent(course -> {
            assertThat(allGroups)
                    .containsAll(List.of(course.getStudentGroupName(), course.getTeachingAssistantGroupName(), course.getEditorGroupName(), course.getInstructorGroupName()));
        });
    }

    // Test
    public void testGetAssessmentDashboardStats_withoutAssessments() throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        // create 6 * 4 = 24 submissions
        adjustUserGroupsToCustomGroups();
        Course testCourse = courseUtilService.addCourseWithExercisesAndSubmissions(userPrefix, "", 6, 4, 0, 0, true, 0, validModel);

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
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        String suffix = "statswithassessments";
        adjustUserGroupsToCustomGroups(suffix);
        Course testCourse = courseUtilService.addCourseWithExercisesAndSubmissions(userPrefix, suffix, 6, 4, 2, 0, true, 0, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        var tutorLeaderboardEntries = stats.getTutorLeaderboardEntries().stream().sorted(Comparator.comparing(TutorLeaderboardDTO::getUserId)).toList();

        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAssessments()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAssessments()).isEqualTo(2);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaints() throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        String suffix = "dashboardstatswithcomplaints";
        adjustUserGroupsToCustomGroups(suffix);
        Course testCourse = courseUtilService.addCourseWithExercisesAndSubmissions(userPrefix, suffix, 6, 4, 4, 2, true, 0, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        var tutorLeaderboardEntries = stats.getTutorLeaderboardEntries().stream().sorted(Comparator.comparing(TutorLeaderboardDTO::getUserId)).toList();

        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAssessments()).isEqualTo(4);

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfTutorComplaints()).isEqualTo(2);

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndFeedbackRequests() throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        String suffix = "statsfeedbackrequests";
        adjustUserGroupsToCustomGroups(suffix);
        Course testCourse = courseUtilService.addCourseWithExercisesAndSubmissions(userPrefix, suffix, 6, 4, 4, 2, false, 0, validModel);
        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        var tutorLeaderboardEntries = stats.getTutorLeaderboardEntries().stream().sorted(Comparator.comparing(TutorLeaderboardDTO::getUserId)).toList();

        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAssessments()).isEqualTo(4);

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfTutorComplaints()).isZero();
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfTutorComplaints()).isZero();
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfTutorComplaints()).isZero();
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfTutorComplaints()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses() throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");

        // Note: with the suffix, we reduce the amount of courses loaded below to prevent test issues
        String suffix = "assessStatsCom";
        adjustUserGroupsToCustomGroups(suffix);
        Course testCourse = courseUtilService.addCourseWithExercisesAndSubmissions(userPrefix, suffix, 6, 4, 4, 2, true, 1, validModel);

        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        var tutorLeaderboardEntries = stats.getTutorLeaderboardEntries().stream().sorted(Comparator.comparing(TutorLeaderboardDTO::getUserId)).toList();

        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfAssessments()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfTutorComplaints()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfTutorComplaints()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfTutorComplaints()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAcceptedComplaints()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAcceptedComplaints()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAcceptedComplaints()).isEqualTo(1);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAcceptedComplaints()).isEqualTo(1);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfAcceptedComplaints()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfComplaintResponses()).isZero();
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfComplaintResponses()).isZero();
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfComplaintResponses()).isZero();
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfComplaintResponses()).isZero();
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfComplaintResponses()).isEqualTo(6);
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndFeedBackRequestsAndResponses() throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");

        // Note: with the suffix, we reduce the amount of courses loaded below to prevent test issues
        String suffix = "assessStatsFR";
        adjustUserGroupsToCustomGroups(suffix);
        Course testCourse = courseUtilService.addCourseWithExercisesAndSubmissions(userPrefix, suffix, 6, 4, 4, 2, false, 1, validModel);

        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        var tutorLeaderboardEntries = stats.getTutorLeaderboardEntries().stream().sorted(Comparator.comparing(TutorLeaderboardDTO::getUserId)).toList();

        // the first two tutors did assess 2 submissions in 2 exercises. The second two only 2 in one exercise.
        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAssessments()).isEqualTo(8);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAssessments()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfAssessments()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(4);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfTutorMoreFeedbackRequests()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfTutorMoreFeedbackRequests()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfNotAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(2);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAnsweredMoreFeedbackRequests()).isEqualTo(1);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfAnsweredMoreFeedbackRequests()).isZero();
    }

    // Test
    public void testGetAssessmentDashboardStats_withAssessmentsAndComplaintsAndResponses_Large() throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        // Note: with the suffix, we reduce the amount of courses loaded below to prevent test issues
        String suffix = "assessStatsLarge";
        adjustUserGroupsToCustomGroups(suffix);

        int exercises = 9;
        int submissions = 5;
        int assessments = 5;
        int complaints = 3;

        Course testCourse = courseUtilService.addCourseWithExercisesAndSubmissions(userPrefix, suffix, exercises, submissions, assessments, complaints, true, complaints,
                validModel);

        StatsForDashboardDTO stats = request.get("/api/courses/" + testCourse.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        var tutorLeaderboardEntries = stats.getTutorLeaderboardEntries().stream().sorted(Comparator.comparing(TutorLeaderboardDTO::getUserId)).toList();

        // the first two tutors did assess 5 submissions of 3 exercises. The rest two only 5 of two exercises.
        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAssessments()).isEqualTo(3 * submissions);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAssessments()).isEqualTo(2 * submissions);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAssessments()).isEqualTo(2 * submissions);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAssessments()).isEqualTo(2 * submissions);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfAssessments()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfTutorComplaints()).isEqualTo(3 * complaints);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfTutorComplaints()).isEqualTo(2 * complaints);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfTutorComplaints()).isEqualTo(2 * complaints);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfTutorComplaints()).isEqualTo(2 * complaints);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfTutorComplaints()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfAcceptedComplaints()).isEqualTo(3 * complaints);
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfAcceptedComplaints()).isEqualTo(2 * complaints);
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfAcceptedComplaints()).isEqualTo(2 * complaints);
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfAcceptedComplaints()).isEqualTo(2 * complaints);
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfAcceptedComplaints()).isZero();

        assertThat(tutorLeaderboardEntries.get(0).getNumberOfComplaintResponses()).isZero();
        assertThat(tutorLeaderboardEntries.get(1).getNumberOfComplaintResponses()).isZero();
        assertThat(tutorLeaderboardEntries.get(2).getNumberOfComplaintResponses()).isZero();
        assertThat(tutorLeaderboardEntries.get(3).getNumberOfComplaintResponses()).isZero();
        // 9 exercises, for each one there are 5 complaintResponses
        assertThat(tutorLeaderboardEntries.get(4).getNumberOfComplaintResponses()).isEqualTo(exercises * complaints);
    }

    // Test
    public void testGetCourse() throws Exception {
        adjustUserGroupsToCustomGroups();
        List<Course> testCourses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, false, 0);
        for (Course testCourse : testCourses) {
            testCourse.setInstructorGroupName(userPrefix + "instructor");
            testCourse.setTeachingAssistantGroupName(userPrefix + "tutor");
            testCourse.setEditorGroupName(userPrefix + "editor");
            testCourse.setStudentGroupName(userPrefix + "student");
            courseRepo.save(testCourse);

            Course courseWithExercises = request.get("/api/courses/" + testCourse.getId() + "/with-exercises", HttpStatus.OK, Course.class);
            Course courseOnly = request.get("/api/courses/" + testCourse.getId(), HttpStatus.OK, Course.class);

            // Check course properties on courseOnly
            assertThat(courseOnly.getStudentGroupName()).as("Student group name is correct").isEqualTo(userPrefix + "student");
            assertThat(courseOnly.getTeachingAssistantGroupName()).as("Teaching assistant group name is correct").isEqualTo(userPrefix + "tutor");
            assertThat(courseOnly.getEditorGroupName()).as("Editor group name is correct").isEqualTo(userPrefix + "editor");
            assertThat(courseOnly.getInstructorGroupName()).as("Instructor group name is correct").isEqualTo(userPrefix + "instructor");
            assertThat(courseOnly.getEndDate()).as("End date is after start date").isAfter(courseOnly.getStartDate());
            assertThat(courseOnly.getMaxComplaints()).as("Max complaints is correct").isEqualTo(3);
            assertThat(courseOnly.getPresentationScore()).as("Presentation score is correct").isEqualTo(2);
            assertThat(courseOnly.getExercises()).as("Course without exercises contains no exercises").isEmpty();
            assertThat(courseOnly.getNumberOfStudents()).as("Amount of students is correct").isEqualTo(8);
            assertThat(courseOnly.getNumberOfTeachingAssistants()).as("Amount of teaching assistants is correct").isEqualTo(5);
            assertThat(courseOnly.getNumberOfEditors()).as("Amount of editors is correct").isEqualTo(1);
            assertThat(courseOnly.getNumberOfInstructors()).as("Amount of instructors is correct").isEqualTo(1);

            // Assert that course properties on courseWithExercises and courseWithExercisesAndRelevantParticipations match those of courseOnly
            String[] ignoringFields = { "exercises", "tutorGroups", "lectures", "exams", "fileService", "filePathService", "entityFileService", "numberOfInstructorsTransient",
                    "numberOfStudentsTransient", "numberOfTeachingAssistantsTransient", "numberOfEditorsTransient" };
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
        List<Course> testCourses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, false, 0);
        Course course1 = testCourses.get(0);
        Course course2 = testCourses.get(1);
        List<String> categories1 = request.getList("/api/courses/" + course1.getId() + "/categories", HttpStatus.OK, String.class);
        assertThat(categories1).as("Correct categories in course1").containsExactlyInAnyOrder("Category", "Modeling", "Quiz", "File", "Text", "Programming");
        List<String> categories2 = request.getList("/api/courses/" + course2.getId() + "/categories", HttpStatus.OK, String.class);
        assertThat(categories2).as("No categories in course2").isEmpty();
    }

    // Test
    public void testGetCategoriesInCourse_instructorNotInCourse() throws Exception {
        Course testCourse = courseUtilService.createCourse();
        request.get("/api/courses/" + testCourse.getId() + "/categories", HttpStatus.FORBIDDEN, Set.class);
    }

    // Test
    public void testEnrollInCourse() throws Exception {
        User student = userUtilService.createAndSaveUser("ab12cde");

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course course2 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
        course1.setEnrollmentEnabled(true);
        course1.setEnrollmentStartDate(pastTimestamp);
        course2.setEnrollmentEndDate(futureTimestamp);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);

        mockDelegate.mockAddUserToGroupInUserManagement(student, course1.getStudentGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(student, course2.getStudentGroupName(), false);

        Set<String> updatedGroups = request.postWithResponseBody("/api/courses/" + course1.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is enrolled in course").contains(course1.getStudentGroupName());

        List<AuditEvent> auditEvents = auditEventRepo.find("ab12cde", Instant.now().minusSeconds(20), Constants.ENROLL_IN_COURSE);
        AuditEvent auditEvent = auditEvents.stream().max(Comparator.comparing(AuditEvent::getTimestamp)).orElse(null);
        assertThat(auditEvent).as("Audit Event for course enrollment added").isNotNull();
        assertThat(auditEvent.getData()).as("Correct Event Data").containsEntry("course", course1.getTitle());

        request.postWithResponseBody("/api/courses/" + course2.getId() + "/enroll", null, Set.class, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testEnrollInCourse_notMeetsDate() throws Exception {
        User student = userUtilService.createAndSaveUser("ab12cde");

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course notYetStartedCourse = CourseFactory.generateCourse(null, futureTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course finishedCourse = CourseFactory.generateCourse(null, pastTimestamp, pastTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
        notYetStartedCourse.setEnrollmentEnabled(true);
        notYetStartedCourse.setEnrollmentStartDate(futureTimestamp);
        notYetStartedCourse.setEnrollmentEndDate(futureTimestamp);

        notYetStartedCourse = courseRepo.save(notYetStartedCourse);
        finishedCourse = courseRepo.save(finishedCourse);

        mockDelegate.mockAddUserToGroupInUserManagement(student, notYetStartedCourse.getStudentGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(student, finishedCourse.getStudentGroupName(), false);

        request.post("/api/courses/" + notYetStartedCourse.getId() + "/enroll", User.class, HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + finishedCourse.getId() + "/enroll", User.class, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testUnenrollFromCourse() throws Exception {
        User student = userUtilService.getUserByLogin(userPrefix + "student1");
        var groups = new HashSet<>(Set.of("unenrolltestcourse1", "unenrolltestcourse2", "unenrolltestcourse3"));
        student.setGroups(groups);
        userRepo.save(student);

        Course[] courses = generateCoursesForUnenrollmentTest();
        courseRepo.saveAll(Arrays.stream(courses).toList());

        for (Course course : courses) {
            mockDelegate.mockRemoveUserFromGroup(student, course.getStudentGroupName(), false);
        }

        testUnenrollFromCourseSuccessfull(courses[0]);

        request.postWithResponseBody("/api/courses/" + courses[1].getId() + "/unenroll", null, User.class, HttpStatus.FORBIDDEN);

        request.postWithResponseBody("/api/courses/" + courses[2].getId() + "/unenroll", null, User.class, HttpStatus.FORBIDDEN);
    }

    private void testUnenrollFromCourseSuccessfull(Course course) throws Exception {
        Set<String> updatedGroups = request.postWithResponseBody("/api/courses/" + course.getId() + "/unenroll", null, Set.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is not enrolled in course").doesNotContain(course.getStudentGroupName());
        List<AuditEvent> auditEvents = auditEventRepo.find(userPrefix + "student1", Instant.now().minusSeconds(20), Constants.UNENROLL_FROM_COURSE);
        assertThat(auditEvents).as("Audit Event for course unenrollment added").hasSize(1);
        AuditEvent auditEvent = auditEvents.get(0);
        assertThat(auditEvent.getData()).as("Correct Event Data").containsEntry("course", course.getTitle());
    }

    private Course[] generateCoursesForUnenrollmentTest() {
        Course[] courses = new Course[3];

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        courses[0] = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "unenrolltestcourse1", "tutor", "editor", "instructor");
        courses[1] = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "unenrolltestcourse2", "tutor", "editor", "instructor");
        courses[2] = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "unenrolltestcourse3", "tutor", "editor", "instructor");
        courses[0].setUnenrollmentEnabled(true);
        courses[0].setUnenrollmentEndDate(futureTimestamp);
        courses[1].setUnenrollmentEnabled(false);
        courses[2].setUnenrollmentEnabled(true);
        courses[2].setUnenrollmentEndDate(pastTimestamp);

        return courses;
    }

    // Test
    public void testUpdateCourse_instructorNotInCourse() throws Exception {
        var course = CourseFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        request.performMvcRequest(buildUpdateCourse(course.getId(), course)).andExpect(status().isForbidden());
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
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
     */
    public void testSearchStudentsAndTutorsAndInstructorsInCourse() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
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
     */
    public void testSearchStudentsAndTutorsAndInstructorsInOtherCourseForbidden() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("nameOfUser", "student2");

        // find a user in another course
        request.getList("/api/courses/" + course.getId() + "/search-other-users", HttpStatus.FORBIDDEN, User.class, parameters);
    }

    // Test
    public void testGetAllEditorsInCourse() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course = courseRepo.save(course);

        // Get all editors for course
        List<User> editors = request.getList("/api/courses/" + course.getId() + "/editors", HttpStatus.OK, User.class);
        assertThat(editors).as("All editors in course found").hasSize(numberOfEditors);
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);
        testGetAllStudentsOrTutorsOrInstructorsInCourse__forbidden(course);
    }

    // Test
    public void testGetAllStudentsOrTutorsOrInstructorsInCourse_AsTutor_forbidden() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
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
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course.setLearningPathsEnabled(true);
        course = courseRepo.save(course);
        testAddStudentOrTutorOrEditorOrInstructorToCourse(course, HttpStatus.OK);
        course = courseRepo.findWithEagerLearningPathsByIdElseThrow(course.getId());
        assertThat(course.getLearningPaths()).isNotEmpty();
        // TODO check that the roles have changed accordingly
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrEditorOrInstructorToCourse(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_AsTutor_forbidden() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course = courseRepo.save(course);
        testAddStudentOrTutorOrEditorOrInstructorToCourse(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testAddStudentOrTutorOrInstructorToCourse_WithNonExistingUser() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course = courseRepo.save(course);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/students/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/editors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/maxMustermann", null, HttpStatus.NOT_FOUND, null);
    }

    private void testAddStudentOrTutorOrEditorOrInstructorToCourse(Course course, HttpStatus httpStatus) throws Exception {
        adjustUserGroupsToCustomGroups();
        var student = userRepo.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "student1").orElseThrow();
        var tutor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "tutor1").orElseThrow();
        var editor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "editor1").orElseThrow();
        var instructor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "instructor1").orElseThrow();

        mockDelegate.mockAddUserToGroupInUserManagement(student, course.getStudentGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(tutor1, course.getTeachingAssistantGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(editor1, course.getEditorGroupName(), false);
        mockDelegate.mockAddUserToGroupInUserManagement(instructor1, course.getInstructorGroupName(), false);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/students/" + userPrefix + "student1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/" + userPrefix + "tutor1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/editors/" + userPrefix + "editor1", null, httpStatus, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/" + userPrefix + "instructor1", null, httpStatus, null);
    }

    // Test
    public void testAddTutorAndEditorAndInstructorToCourse_failsToAddUserToGroup(HttpStatus expectedFailureCode) throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course = courseRepo.save(course);
        programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        course = courseRepo.save(course);

        var tutor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "tutor1").orElseThrow();
        var editor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "editor1").orElseThrow();
        var instructor1 = userRepo.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "instructor1").orElseThrow();

        mockDelegate.mockAddUserToGroupInUserManagement(tutor1, course.getTeachingAssistantGroupName(), true);
        mockDelegate.mockAddUserToGroupInUserManagement(editor1, course.getEditorGroupName(), true);
        mockDelegate.mockAddUserToGroupInUserManagement(instructor1, course.getInstructorGroupName(), true);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/tutors/" + userPrefix + "tutor1", null, expectedFailureCode, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/editors/" + userPrefix + "editor1", null, expectedFailureCode, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/instructors/" + userPrefix + "instructor1", null, expectedFailureCode, null);
    }

    // Test
    public void testRemoveTutorFromCourse_failsToRemoveUserFromGroup() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course = courseRepo.save(course);
        programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        course = courseRepo.save(course);

        User tutor = userRepo.findOneWithGroupsByLogin(userPrefix + "tutor1").orElseThrow();
        mockDelegate.mockRemoveUserFromGroup(tutor, course.getTeachingAssistantGroupName(), true);
        request.delete("/api/courses/" + course.getId() + "/tutors/" + tutor.getLogin(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(course, HttpStatus.OK);
        // TODO check that the roles have changed accordingly
    }

    // Test
    public void testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_WithNonExistingUser() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course = courseRepo.save(course);
        request.delete("/api/courses/" + course.getId() + "/students/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/tutors/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/editors/maxMustermann", HttpStatus.NOT_FOUND);
        request.delete("/api/courses/" + course.getId() + "/instructors/maxMustermann", HttpStatus.NOT_FOUND);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsInstructorOfOtherCourse_forbidden() throws Exception {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), "other-tumuser", "other-tutor", "other-editor", "other-instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(course, HttpStatus.FORBIDDEN);
    }

    // Test
    public void testRemoveStudentOrTutorOrInstructorFromCourse_AsTutor_forbidden() throws Exception {
        adjustUserGroupsToCustomGroups();
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student", userPrefix + "tutor", userPrefix + "editor",
                userPrefix + "instructor");
        course = courseRepo.save(course);
        testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(course, HttpStatus.FORBIDDEN);
    }

    private void testRemoveStudentOrTutorOrEditorOrInstructorFromCourse_forbidden(Course course, HttpStatus httpStatus) throws Exception {
        // Retrieve users from whom to remove groups
        User student = userRepo.findOneWithGroupsByLogin(userPrefix + "student1").orElseThrow();
        User tutor = userRepo.findOneWithGroupsByLogin(userPrefix + "tutor1").orElseThrow();
        User editor = userRepo.findOneWithGroupsByLogin(userPrefix + "editor1").orElseThrow();
        User instructor = userRepo.findOneWithGroupsByLogin(userPrefix + "instructor1").orElseThrow();

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
        Course course = modelingExerciseUtilService.addCourseWithDifferentModelingExercises();
        ModelingExercise classExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");

        List<Submission> lockedSubmissions = request.getList("/api/courses/" + course.getId() + "/lockedSubmissions", HttpStatus.OK, Submission.class);
        assertThat(lockedSubmissions).as("Locked Submissions is not null").isNotNull();
        assertThat(lockedSubmissions).as("Locked Submissions length is 0").isEmpty();

        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");

        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, userPrefix + "student1", userPrefix + "tutor1");

        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, userPrefix + "student2", userPrefix + "tutor1");

        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, userPrefix + "student3", userPrefix + "tutor1");

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
    public Course testArchiveCourseWithTestModelingAndFileUploadExercises() throws Exception {
        var course = courseUtilService.createCourseWithTextModelingAndFileUploadExercisesAndSubmissions(userPrefix);
        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);
        await().until(() -> courseRepo.findById(course.getId()).orElseThrow().getCourseArchivePath() != null);
        var updatedCourse = courseRepo.findById(course.getId()).orElseThrow();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
        return updatedCourse;
    }

    public void testArchiveCourseWithQuizExercise(String userPrefix) throws Exception {
        var course = courseUtilService.createCourse();
        var quizSubmission = quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course, userPrefix + "student1", false);
        var quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2), QuizMode.INDIVIDUAL);
        quizExercise = exerciseRepo.save(quizExercise);
        participationUtilService.createAndSaveParticipationForExercise(quizExercise, userPrefix + "student2");
        var archivePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, Collections.synchronizedList(new ArrayList<>()));
        assertThat(archivePath).isNotEmpty();
        extractAndAssertContent(archivePath.orElseThrow(), quizSubmission);
    }

    public void testArchiveCourseWithQuizExerciseCannotExportExerciseDetails() throws IOException {
        var course = courseUtilService.createCourse();
        var quizSubmission = quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course, userPrefix + "student1", false);
        var archivePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, Collections.synchronizedList(new ArrayList<>()));
        assertThat(archivePath).isNotEmpty();
        Predicate<Path> missingPathPredicate = path -> "Exercise-Details-quiz.json".equals(path.getFileName().toString());
        extractAndAssertMissingContent(archivePath.orElseThrow(), quizSubmission, missingPathPredicate);
    }

    public void testArchiveCourseWithQuizExerciseCannotExportDragAndDropSubmission() throws IOException {
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());
        var course = courseUtilService.createCourse();
        var quizSubmission = quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course, userPrefix + "student1", false);
        try (MockedStatic<ImageIO> mockedImageIO = mockStatic(ImageIO.class)) {
            mockedImageIO.when(() -> ImageIO.read(any(File.class))).thenThrow(new IOException());
            var archivePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
            assertThat(archivePath).isNotEmpty();
            Predicate<Path> missingPathPredicate = path -> path.getFileName().toString().contains("dragAndDropQuestion") && path.getFileName().toString().endsWith(".pdf");
            extractAndAssertMissingContent(archivePath.orElseThrow(), quizSubmission, missingPathPredicate);
        }
    }

    public void testArchiveCourseWithQuizExerciseCannotCreateParticipationDirectory() throws IOException {
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());
        var course = courseUtilService.createCourse();
        quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course, userPrefix + "student1", false);
        try (MockedStatic<DataExportUtil> mockedFiles = mockStatic(DataExportUtil.class)) {
            mockedFiles.when(() -> DataExportUtil.createDirectoryIfNotExistent(any())).thenThrow(new IOException());
            var archivePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
            assertThat(archivePath).isNotEmpty();
        }

    }

    public void testArchiveCourseWithQuizExerciseCannotExportMCOrSAAnswersSubmission(String fileName, String dynamicErrorMsg) throws IOException {
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());
        var course = courseUtilService.createCourse();
        var quizSubmission = quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course, userPrefix + "student1", false);
        try (MockedStatic<org.apache.commons.io.FileUtils> mockedFiles = mockStatic(org.apache.commons.io.FileUtils.class)) {
            mockedFiles.when(() -> org.apache.commons.io.FileUtils.writeLines(argThat(file -> file.toString().contains(fileName)), anyString(), anyList()))
                    .thenThrow(new IOException());
            var archivePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
            assertThat(archivePath).isNotEmpty();
            Predicate<Path> missingPathPredicate = path -> path.getFileName().toString().contains(fileName) && path.getFileName().toString().endsWith(".txt");
            extractAndAssertMissingContent(archivePath.orElseThrow(), quizSubmission, missingPathPredicate);
            assertThat(exportErrors).hasSize(1);
            assertThat(exportErrors.get(0)).contains("Failed to export " + dynamicErrorMsg + " answers");
        }
    }

    private void extractAndAssertMissingContent(Path courseArchivePath, QuizSubmission quizSubmission, Predicate<Path> missingPathPredicate) throws IOException {
        Path courseArchiveDir = zipFileTestUtilService.extractZipFileRecursively(courseArchivePath.toString());
        var exercise = quizSubmission.getParticipation().getExercise();
        StudentParticipation studentParticipation = (StudentParticipation) quizSubmission.getParticipation();
        assertThat(courseArchiveDir).exists();

        try (var files = Files.walk(courseArchiveDir)) {
            assertThat(files.filter(file -> Files.isDirectory(file) || Files.isRegularFile(file)))
                    // exercise directory
                    .anyMatch(file -> (exercise.getSanitizedExerciseTitle() + "_" + exercise.getId()).equals(file.getFileName().toString()))
                    // participation directory
                    .anyMatch(
                            file -> ("participation-" + studentParticipation.getId() + "-" + studentParticipation.getParticipantIdentifier()).equals(file.getFileName().toString()))
                    .noneMatch(missingPathPredicate);
        }

        org.apache.commons.io.FileUtils.deleteDirectory(courseArchiveDir.toFile());
        org.apache.commons.io.FileUtils.delete(courseArchivePath.toFile());
    }

    private void extractAndAssertContent(Path courseArchivePath, QuizSubmission quizSubmission) throws IOException {
        Path courseArchiveDir = zipFileTestUtilService.extractZipFileRecursively(courseArchivePath.toString());
        var exercise = quizSubmission.getParticipation().getExercise();
        StudentParticipation studentParticipation = (StudentParticipation) quizSubmission.getParticipation();
        assertThat(courseArchiveDir).exists();
        try (var files = Files.walk(courseArchiveDir)) {
            assertThat(files.filter(file -> Files.isDirectory(file) || Files.isRegularFile(file)))
                    // exercise directory
                    .anyMatch(file -> (exercise.getSanitizedExerciseTitle() + "_" + exercise.getId()).equals(file.getFileName().toString()))
                    // participation directory
                    .anyMatch(
                            file -> ("participation-" + studentParticipation.getId() + "-" + studentParticipation.getParticipantIdentifier()).equals(file.getFileName().toString()))
                    // exercise details file
                    .anyMatch(file -> ("Exercise-Details-quiz.json").equals(file.getFileName().toString()))
                    // drag and drop question submission pdf
                    .anyMatch(file -> file.getFileName().toString().contains("dragAndDropQuestion") && file.getFileName().toString().endsWith(".pdf"))
                    // MC submission txt file
                    .anyMatch(file -> file.getFileName().toString().contains("multiple_choice_questions_answers") && file.getFileName().toString().endsWith(".txt"))
                    // short answer submission txt file
                    .anyMatch(file -> file.getFileName().toString().contains("short_answer_questions_answers") && file.getFileName().toString().endsWith(".txt"));
        }

        org.apache.commons.io.FileUtils.deleteDirectory(courseArchiveDir.toFile());
        org.apache.commons.io.FileUtils.delete(courseArchivePath.toFile());
    }

    /**
     * Test
     */
    public void searchStudentsInCourse() throws Exception {
        var course = courseUtilService.createCourse();

        MultiValueMap<String, String> params1 = new LinkedMultiValueMap<>();
        params1.add("loginOrName", userPrefix + "student");
        List<UserDTO> students = request.getList("/api/courses/" + course.getId() + "/students/search", HttpStatus.OK, UserDTO.class, params1);
        assertThat(students).size().isEqualTo(8);

        MultiValueMap<String, String> params2 = new LinkedMultiValueMap<>();
        params2.add("loginOrName", userPrefix + "tutor");
        // should be empty as we only search for students
        List<UserDTO> tutors = request.getList("/api/courses/" + course.getId() + "/students/search", HttpStatus.OK, UserDTO.class, params2);
        assertThat(tutors).isEmpty();
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForAllTutors_shouldReturnAllTutorsAndEditors() throws Exception {
        Course course = createCourseForUserSearchTest();
        // Test: search for all (no login or name) tutors (tutors includes also editors)
        var result = searchUsersTest(course, List.of("tutors"), Optional.empty(), numberOfTutors + numberOfEditors, true);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsEditor)).hasSize(numberOfEditors);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsTeachingAssistant)).hasSize(numberOfTutors);
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForAllInstructor_shouldReturnAllInstructors() throws Exception {
        var course = createCourseForUserSearchTest();
        // Test: search for all (no login or name) instructors
        var result = searchUsersTest(course, List.of("instructors"), Optional.empty(), numberOfInstructors, true);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsInstructor)).hasSize(numberOfInstructors);
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForAllStudents_shouldReturnBadRequest() throws Exception {
        var course = createCourseForUserSearchTest();
        // Test: Try to search for all students (should fail)
        searchUsersTest(course, List.of("students"), Optional.empty(), 0, false);
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForStudentsAndTooShortSearchTerm_shouldReturnBadRequest() throws Exception {
        var course = createCourseForUserSearchTest();
        // Test: Try to search for all students with a too short search term (at least 3 as students are included) (should fail)
        searchUsersTest(course, List.of("students"), Optional.of("st"), 0, false);
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForStudents_shouldReturnUsersMatchingSearchTerm() throws Exception {
        var course = createCourseForUserSearchTest();
        // Test: Try to search for students with a long enough search term (at least 3 as students are included)
        // Note: -1 as student1 is the requesting user and will not be returned
        var result = searchUsersTest(course, List.of("students"), Optional.of(userPrefix + "student"), numberOfStudents - 1, true);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsStudent)).hasSize(numberOfStudents - 1);
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForAllTutorsAndInstructors_shouldReturnAllTutorsEditorsAndInstructors() throws Exception {
        var course = createCourseForUserSearchTest();
        // Test: Try to search for all tutors (tutors includes also editors) and instructors
        var result = searchUsersTest(course, List.of("tutors", "instructors"), Optional.empty(), numberOfTutors + numberOfEditors + numberOfInstructors, true);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsEditor)).hasSize(numberOfEditors);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsTeachingAssistant)).hasSize(numberOfTutors);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsInstructor)).hasSize(numberOfInstructors);
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForTutorsAndInstructors_shouldReturnUsersMatchingSearchTerm() throws Exception {
        var course = createCourseForUserSearchTest();
        // Test : Try to search for all tutors (tutors includes also editors) and instructors with search term
        var result = searchUsersTest(course, List.of("tutors", "instructors"), Optional.of(userPrefix + "tutor"), numberOfTutors, true);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsEditor)).isEmpty();
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsTeachingAssistant)).hasSize(numberOfTutors);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsInstructor)).isEmpty();
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForStudentsTutorsAndInstructorsAndTooShortSearchTerm_shouldReturnBadRequest() throws Exception {
        var course = createCourseForUserSearchTest();
        // Test: Try to search or all students, tutors (tutors includes also editors) and instructors
        // with a too short search term (at least 3 as students are included)
        searchUsersTest(course, List.of("students", "tutors", "instructors"), Optional.of("tu"), 0, false);
    }

    /**
     * Test
     */
    public void searchUsersInCourse_searchForStudentsTutorsEditorsAndInstructors_shouldReturnUsersMatchingSearchTerm() throws Exception {
        var course = createCourseForUserSearchTest();

        // Test: Try to search or all students, tutors (tutors includes also editors)
        // and instructors with a long enough search term (at least 3 as students are included)
        var result = searchUsersTest(course, List.of("students", "tutors", "instructors"), Optional.of(userPrefix + "tutor"), numberOfTutors, true);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsEditor)).isEmpty();
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsTeachingAssistant)).hasSize(numberOfTutors);
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsInstructor)).isEmpty();
        assertThat(result.stream().filter(UserPublicInfoDTO::getIsStudent)).isEmpty();
    }

    /**
     * Test
     */
    public void testSearchMembersForUserMentionsSearchTermFilteringCorrect() throws Exception {
        var course = createCourseForUserSearchTest();
        MultiValueMap<String, String> queryParameter = new LinkedMultiValueMap<>();

        queryParameter.add("loginOrName", userPrefix + "tutor");
        var result = request.getList("/api/courses/" + course.getId() + "/members/search", HttpStatus.OK, UserNameAndLoginDTO.class, queryParameter);

        assertThat(result).hasSize(numberOfTutors);
        result.forEach(dto -> assertThat(dto.name().contains(userPrefix + "tutor")).isTrue());
    }

    /**
     * Test
     */
    public void testSearchMembersForUserMentionsSearchResultLimit() throws Exception {
        var course = createCourseForUserSearchTest();
        MultiValueMap<String, String> queryParameter = new LinkedMultiValueMap<>();

        queryParameter.set("loginOrName", "");
        var result = request.getList("/api/courses/" + course.getId() + "/members/search", HttpStatus.OK, UserNameAndLoginDTO.class, queryParameter);

        assertThat(result).hasSize(10);
        assertThat(numberOfStudents + numberOfTutors + numberOfEditors + numberOfInstructors).isGreaterThan(10);
    }

    /**
     * Test
     */
    public void testSearchMembersForUserMentionsNoSearchTerm() throws Exception {
        var course = createCourseForUserSearchTest();

        request.getList("/api/courses/" + course.getId() + "/members/search", HttpStatus.BAD_REQUEST, UserNameAndLoginDTO.class);
    }

    private Course createCourseForUserSearchTest() {
        String suffix = "searchUserTest";
        adjustUserGroupsToCustomGroups(suffix);
        var course = CourseFactory.generateCourse(null, null, null, new HashSet<>(), userPrefix + "student" + suffix, userPrefix + "tutor" + suffix, userPrefix + "editor" + suffix,
                userPrefix + "instructor" + suffix);
        course = courseRepo.save(course);
        return course;
    }

    private List<UserPublicInfoDTO> searchUsersTest(Course course, List<String> roles, Optional<String> loginOrName, int expectedSize, boolean shouldPass) throws Exception {
        MultiValueMap<String, String> queryParameter = new LinkedMultiValueMap<>();
        queryParameter.add("loginOrName", loginOrName.orElse(""));
        queryParameter.add("roles", String.join(",", roles));
        var status = shouldPass ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        var foundUsers = request.getList("/api/courses/" + course.getId() + "/users/search", status, UserPublicInfoDTO.class, queryParameter);
        if (shouldPass) {
            var foundUsersWithPrefix = foundUsers.stream().filter(user -> user.getLogin().startsWith(userPrefix)).toList();
            assertThat(foundUsersWithPrefix).hasSize(expectedSize);
            return foundUsersWithPrefix;
        }
        else {
            assertThat(foundUsers).isNull();
            return emptyList();
        }
    }

    // Test
    public void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportModelingExercise() throws Exception {
        Course course = courseUtilService.createCourseWithTextModelingAndFileUploadExercisesAndSubmissions(userPrefix);
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst();
        assertThat(modelingExercise).isPresent();
        archiveCourseAndAssertExerciseDoesntExist(course, modelingExercise.get());
    }

    // Test
    public void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportTextExercise() throws Exception {
        Course course = courseUtilService.createCourseWithTextModelingAndFileUploadExercisesAndSubmissions(userPrefix);
        Optional<TextExercise> textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst();
        assertThat(textExercise).isPresent();
        archiveCourseAndAssertExerciseDoesntExist(course, textExercise.get());
    }

    // Test
    public void testArchiveCourseWithTestModelingAndFileUploadExercisesFailToExportFileUploadExercise() throws Exception {
        Course course = courseUtilService.createCourseWithTextModelingAndFileUploadExercisesAndSubmissions(userPrefix);
        Optional<FileUploadExercise> fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst();
        assertThat(fileUploadExercise).isPresent();
        archiveCourseAndAssertExerciseDoesntExist(course, fileUploadExercise.get());
    }

    private void archiveCourseAndAssertExerciseDoesntExist(Course course, Exercise exercise) throws Exception {
        Files.createDirectories(courseArchivesDirPath);
        List<Path> files = archiveCourseAndExtractFiles(course);
        // report.csv, errors.text, one submission per exercise, one problem statement per exercise and no exercise details.json per exercise
        // because of the thrown exception. --> 2+3+3=8
        assertThat(files).hasSize(8);
        assertThat(files).doesNotContain(Path.of("Exercise-Details-" + exercise.getTitle() + ".json"));
    }

    private List<Path> archiveCourseAndExtractFiles(Course course) throws IOException {
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());
        Optional<Path> exportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
        assertThat(exportedCourse).isNotEmpty();

        // Extract the archive
        Path archivePath = exportedCourse.get();
        Path extractedArchiveDir = zipFileTestUtilService.extractZipFileRecursively(archivePath.toString());

        try (var files = Files.walk(extractedArchiveDir)) {
            return files.filter(Files::isRegularFile).map(Path::getFileName).filter(path -> !path.toString().endsWith(".zip")).toList();
        }
        finally {
            org.apache.commons.io.FileUtils.deleteDirectory(extractedArchiveDir.toFile());
            org.apache.commons.io.FileUtils.delete(archivePath.toFile());
        }
    }

    public void testExportCourse_cannotCreateTmpDir() throws Exception {
        Course course = courseUtilService.createCourseWithTextModelingAndFileUploadExercisesAndSubmissions(userPrefix);
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());

        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectories(argThat(path -> path.toString().contains("exports")))).thenThrow(IOException.class);
        Optional<Path> exportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
        mockedFiles.close();

        assertThat(exportedCourse).isEmpty();
    }

    public void testExportCourse_cannotCreateCourseExercisesDir() throws Exception {
        Course course = courseUtilService.createCourseWithTextModelingAndFileUploadExercisesAndSubmissions(userPrefix);
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());

        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectory(argThat(path -> path.toString().contains("course-exercises")))).thenThrow(IOException.class);
        Optional<Path> exportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);
        mockedFiles.close();

        assertThat(exportedCourse).isEmpty();
    }

    public void testExportCourseExam_cannotCreateTmpDir() throws Exception {
        Course course = courseUtilService.createCourseWithExamExercisesAndSubmissions(userPrefix);
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());

        Optional<Exam> exam = examRepo.findByCourseId(course.getId()).stream().findFirst();
        assertThat(exam).isPresent();

        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectories(argThat(path -> path.toString().contains("exports")))).thenThrow(IOException.class);
        Optional<Path> exportedCourse = courseExamExportService.exportExam(exam.get(), courseArchivesDirPath, exportErrors);
        mockedFiles.close();

        assertThat(exportedCourse).isEmpty();
    }

    public void testExportCourseExam_cannotCreateExamsDir() throws Exception {
        Course course = courseUtilService.createCourseWithExamExercisesAndSubmissions(userPrefix);
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());

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
        Course course = CourseFactory.generateCourse(1L, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        var downloadedArchive = request.get("/api/courses/" + course.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    // Test
    public void testDownloadCourseArchiveAsInstructor() throws Exception {
        // Archive the course and wait until it's complete
        Course updatedCourse = testArchiveCourseWithTestModelingAndFileUploadExercises();
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Content-Disposition", "attachment; filename=\"" + updatedCourse.getCourseArchivePath() + "\"");

        // Download the archive
        var archive = request.getFile("/api/courses/" + updatedCourse.getId() + "/download-archive", HttpStatus.OK, new LinkedMultiValueMap<>(), expectedHeaders);
        assertThat(archive).isNotNull();
        assertThat(archive).exists();
        assertThat(archive.getPath().length()).isGreaterThanOrEqualTo(4);

        // Extract the archive
        Path extractedArchiveDir = zipFileTestUtilService.extractZipFileRecursively(archive.getAbsolutePath());

        // We test for the filenames of the submissions since it's the easiest way.
        // We don't test the directory structure
        List<Path> filenames;
        try (var files = Files.walk(extractedArchiveDir)) {
            filenames = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
        }

        var exercises = exerciseRepo.findAllExercisesByCourseId(updatedCourse.getId());
        for (Exercise exercise : exercises) {
            var exerciseWithParticipation = exerciseRepo.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow();
            for (Participation participation : exerciseWithParticipation.getStudentParticipations()) {
                for (Submission submission : participation.getSubmissions()) {
                    if (submission instanceof FileUploadSubmission) {
                        assertThat(filenames).contains(Path.of("FileUpload-" + userPrefix + "student1-" + submission.getId() + ".png"));
                    }
                    if (submission instanceof TextSubmission) {
                        assertThat(filenames).contains(Path.of("Text-" + userPrefix + "student1-" + submission.getId() + ".txt"));
                    }
                    if (submission instanceof ModelingSubmission) {
                        assertThat(filenames).contains(Path.of("Modeling-" + userPrefix + "student1-" + submission.getId() + ".json"));
                    }
                }
            }
        }

        org.apache.commons.io.FileUtils.deleteDirectory(extractedArchiveDir.toFile());
        org.apache.commons.io.FileUtils.delete(archive);
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
        Course course = courseRepo.save(courseUtilService.createCourse());

        request.delete("/api/courses/" + course.getId() + "/cleanup", HttpStatus.BAD_REQUEST);
    }

    // Test
    public void testCleanupCourseAsInstructor() throws Exception {
        // Generate a course that has an archive
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        course.setCourseArchivePath("some-archive-path");
        course = courseRepo.save(course);

        final ProgrammingExercise courseExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        final var programmingExercise = programmingExerciseRepository.findWithEagerTemplateAndSolutionParticipationsById(courseExercise.getId()).orElseThrow();
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student1");

        final String repoSuffix = "-" + userPrefix + "student1";

        mockDelegate.mockDeleteRepository(programmingExercise.getProjectKey(), (programmingExercise.getProjectKey()).toLowerCase() + repoSuffix, false);
        var buildPlanId = (programmingExercise.getProjectKey() + repoSuffix).toUpperCase();
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), buildPlanId, false);
        request.delete("/api/courses/" + course.getId() + "/cleanup", HttpStatus.OK);

        course.getExercises().forEach(exercise -> {
            var exerciseWithParticipations = exerciseRepo.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow();
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
        Course course = courseUtilService.createCourse();
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
        var testCourses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, false, 0);
        var instructorsCourse = testCourses.get(0);
        instructorsCourse.setInstructorGroupName("test-instructors");
        courseRepo.save(instructorsCourse);
        var nonInstructorsCourse = testCourses.get(1);

        var instructor = userUtilService.getUserByLogin(userPrefix + "instructor1");
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepo.save(instructor);

        var courses = request.getList("/api/courses/course-management-overview", HttpStatus.OK, Course.class);

        assertThat(courses.stream().filter(c -> Objects.equals(c.getId(), nonInstructorsCourse.getId())).toList()).as("Non instructors course was filtered out").isEmpty();

        Optional<Course> optionalCourse = courses.stream().filter(c -> Objects.equals(c.getId(), instructorsCourse.getId())).findFirst();
        assertThat(optionalCourse).as("Instructors course is returned").isPresent();
        Course returnedCourse = optionalCourse.orElseThrow();

        assertThat(returnedCourse.getId()).isEqualTo(instructorsCourse.getId());
    }

    // Test
    public void testGetExercisesForCourseOverview() throws Exception {

        // Add two courses, containing one not belonging to the instructor
        var testCourses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, false, 0);
        var instructorsCourse = testCourses.get(0);
        instructorsCourse.setInstructorGroupName("test-instructors");
        courseRepo.save(instructorsCourse);
        var nonInstructorsCourse = testCourses.get(1);

        var instructor = userUtilService.getUserByLogin(userPrefix + "instructor1");
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepo.save(instructor);

        var courses = request.getList("/api/courses/exercises-for-management-overview", HttpStatus.OK, Course.class);

        assertThat(courses.stream().filter(c -> Objects.equals(c.getId(), nonInstructorsCourse.getId())).toList()).as("Non instructors course was filtered out").isEmpty();

        Optional<Course> optionalCourse = courses.stream().filter(c -> Objects.equals(c.getId(), instructorsCourse.getId())).findFirst();
        assertThat(optionalCourse).as("Instructors course is returned").isPresent();
        Course returnedCourse = optionalCourse.orElseThrow();

        var exerciseDetails = returnedCourse.getExercises();
        assertThat(exerciseDetails).isNotNull();
        assertThat(exerciseDetails).hasSize(5);

        var quizDetailsOptional = exerciseDetails.stream().filter(e -> e instanceof QuizExercise).findFirst();
        assertThat(quizDetailsOptional).isPresent();

        var quizExercise = exerciseUtilService.getFirstExerciseWithType(returnedCourse, QuizExercise.class);

        var quizDetails = quizDetailsOptional.get();
        assertThat(quizDetails.getCategories()).hasSize(quizExercise.getCategories().size());

        var detailsCategories = quizDetails.getCategories().stream().findFirst();
        var exerciseCategories = quizExercise.getCategories().stream().findFirst();
        assertThat(detailsCategories).isPresent();
        assertThat(exerciseCategories).isPresent();
        assertThat(detailsCategories).contains(exerciseCategories.get());
    }

    // Test
    public void testGetExerciseStatsForCourseOverview() throws Exception {
        String testSuffix = "exercisestatsoverview";
        adjustUserGroupsToCustomGroups(testSuffix);
        // Add a course and set the instructor group name
        var instructorsCourse = courseUtilService.createCourse();
        adjustCourseGroups(instructorsCourse, testSuffix);

        instructorsCourse.setStartDate(ZonedDateTime.now().minusWeeks(1).with(DayOfWeek.MONDAY));
        instructorsCourse.setEndDate(ZonedDateTime.now().minusWeeks(1).with(DayOfWeek.WEDNESDAY));

        var instructor = userUtilService.getUserByLogin(userPrefix + "instructor1");

        // Get two students
        var student = userUtilService.createAndSaveUser(userPrefix + "user1");
        var student2 = userUtilService.createAndSaveUser(userPrefix + "user2");

        // Add a team exercise which was just released but not due
        var releaseDate = ZonedDateTime.now().minusDays(4);
        var futureDueDate = ZonedDateTime.now().plusDays(2);
        var futureAssessmentDueDate = ZonedDateTime.now().plusDays(4);
        var teamExerciseNotEnded = textExerciseUtilService.createTeamTextExercise(instructorsCourse, releaseDate, futureDueDate, futureAssessmentDueDate);
        teamExerciseNotEnded = exerciseRepo.save(teamExerciseNotEnded);

        // Add a team with a participation to the exercise
        final var teamExerciseId = teamExerciseNotEnded.getId();
        var teamStudents = new HashSet<User>();
        teamStudents.add(student);
        var team = teamUtilService.createTeam(teamStudents, instructor, teamExerciseNotEnded, "team");
        textExerciseUtilService.createSubmissionForTextExercise(teamExerciseNotEnded, team, "Team Text");
        instructorsCourse.addExercises(teamExerciseNotEnded);

        // Create an exercise which has passed the due and assessment due date
        var dueDate = ZonedDateTime.now().minusDays(2);
        var passedAssessmentDueDate = ZonedDateTime.now().minusDays(1);
        var exerciseAssessmentDone = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, passedAssessmentDueDate, instructorsCourse);
        exerciseAssessmentDone.setMaxPoints(5.0);
        exerciseAssessmentDone = exerciseRepo.save(exerciseAssessmentDone);

        // Add a single participation to that exercise
        final var exerciseId = exerciseAssessmentDone.getId();
        participationUtilService.createParticipationSubmissionAndResult(exerciseId, student, 5.0, 0.0, 60, true);

        instructorsCourse.addExercises(exerciseAssessmentDone);

        // Create an exercise which is currently in assessment
        var exerciseInAssessment = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, futureAssessmentDueDate, instructorsCourse);
        exerciseInAssessment.setMaxPoints(15.0);
        exerciseInAssessment = exerciseRepo.save(exerciseInAssessment);

        // Add a participation and submission to that exercise
        final var exerciseIdInAssessment = exerciseInAssessment.getId();
        var resultToSetAssessorFor = participationUtilService.createParticipationSubmissionAndResult(exerciseIdInAssessment, student, 15.0, 0.0, 30, true);
        resultToSetAssessorFor.getSubmission().setSubmissionDate(dueDate.minusHours(1));
        resultToSetAssessorFor.getSubmission().setSubmitted(true);
        resultToSetAssessorFor.setAssessor(instructor);
        resultRepo.saveAndFlush(resultToSetAssessorFor);
        submissionRepository.saveAndFlush(resultToSetAssessorFor.getSubmission());

        // Add a participation without submission to that exercise (just starting)
        participationService.startExercise(exerciseInAssessment, student2, false);

        instructorsCourse.addExercises(exerciseInAssessment);

        courseRepo.save(instructorsCourse);

        TextExercise finalExerciseInAssessment = exerciseInAssessment;
        await().until(() -> !participantScoreRepository.findAllByExercise(finalExerciseInAssessment).isEmpty());
        TextExercise finalExerciseAssessmentDone = exerciseAssessmentDone;
        await().until(() -> !participantScoreRepository.findAllByExercise(finalExerciseAssessmentDone).isEmpty());

        var courseDtos = request.getList("/api/courses/stats-for-management-overview", HttpStatus.OK, CourseManagementOverviewStatisticsDTO.class);
        // We only added one course, so expect one dto
        assertThat(courseDtos).hasSize(1);

        Optional<CourseManagementOverviewStatisticsDTO> optionalCourseDTO = courseDtos.stream().filter(dto -> Objects.equals(dto.getCourseId(), instructorsCourse.getId()))
                .findFirst();
        assertThat(optionalCourseDTO).as("Active course was not filtered").isPresent();
        CourseManagementOverviewStatisticsDTO dto = optionalCourseDTO.orElseThrow();

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
        assertThat(statisticsDTO.getParticipationRateInPercent()).isZero();
        assertThat(statisticsDTO.getNoOfStudentsInCourse()).isEqualTo(8);
        assertThat(statisticsDTO.getNoOfRatedAssessments()).isZero();
        assertThat(statisticsDTO.getNoOfAssessmentsDoneInPercent()).isZero();
        assertThat(statisticsDTO.getNoOfSubmissionsInTime()).isZero();

        // Get the statistics of the team exercise
        var teamStatisticsOptional = exerciseDTOS.stream().filter(exercise -> exercise.getExerciseId().equals(teamExerciseId)).findFirst();
        assertThat(teamStatisticsOptional).isPresent();

        // Since that exercise is still "currently in progress", the participations are the only statistics we set
        var teamStatisticsDTO = teamStatisticsOptional.get();
        assertThat(teamStatisticsDTO.getAverageScoreInPercent()).isZero();
        assertThat(teamStatisticsDTO.getExerciseMaxPoints()).isEqualTo(10.0);
        assertThat(teamStatisticsDTO.getNoOfParticipatingStudentsOrTeams()).isEqualTo(1);
        assertThat(teamStatisticsDTO.getParticipationRateInPercent()).isEqualTo(100D);
        assertThat(teamStatisticsDTO.getNoOfStudentsInCourse()).isEqualTo(8);
        assertThat(teamStatisticsDTO.getNoOfTeamsInCourse()).isEqualTo(1);
        assertThat(teamStatisticsDTO.getNoOfRatedAssessments()).isZero();
        assertThat(teamStatisticsDTO.getNoOfAssessmentsDoneInPercent()).isZero();
        assertThat(teamStatisticsDTO.getNoOfSubmissionsInTime()).isEqualTo(1L);

        // Get the statistics of the exercise in assessment
        var exerciseInAssessmentStatisticsOptional = exerciseDTOS.stream().filter(exercise -> exercise.getExerciseId().equals(exerciseIdInAssessment)).findFirst();
        assertThat(exerciseInAssessmentStatisticsOptional).isPresent();

        // Since that exercise is "currently in assessment", we need the numberOfRatedAssessment, assessmentsDoneInPercent and the numberOfSubmissionsInTime
        var exerciseInAssessmentStatisticsDTO = exerciseInAssessmentStatisticsOptional.get();
        assertThat(exerciseInAssessmentStatisticsDTO.getAverageScoreInPercent()).isZero();
        assertThat(exerciseInAssessmentStatisticsDTO.getExerciseMaxPoints()).isEqualTo(15.0);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfParticipatingStudentsOrTeams()).isZero();
        assertThat(exerciseInAssessmentStatisticsDTO.getParticipationRateInPercent()).isZero();
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfStudentsInCourse()).isEqualTo(8);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfRatedAssessments()).isEqualTo(1);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfAssessmentsDoneInPercent()).isEqualTo(100.0);
        assertThat(exerciseInAssessmentStatisticsDTO.getNoOfSubmissionsInTime()).isEqualTo(1L);
    }

    // Test
    public void testGetExerciseStatsForCourseOverviewWithPastExercises() throws Exception {
        // Add a single course with six past exercises, from which only five are returned
        String testSuffix = "statspast";
        adjustUserGroupsToCustomGroups(testSuffix);

        var instructorsCourse = courseUtilService.createCourse();
        adjustCourseGroups(instructorsCourse, testSuffix);

        var releaseDate = ZonedDateTime.now().minusDays(7);
        var dueDate = ZonedDateTime.now().minusDays(4);
        var olderDueDate = ZonedDateTime.now().minusDays(4);
        var assessmentDueDate = ZonedDateTime.now().minusDays(2);
        var olderAssessmentDueDate = ZonedDateTime.now().minusDays(3);
        var oldestAssessmentDueDate = ZonedDateTime.now().minusDays(6);

        // Add five exercises with different combinations of due dates and assessment due dates
        instructorsCourse.addExercises(exerciseRepo.save(TextExerciseFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(TextExerciseFactory.generateTextExercise(releaseDate, null, assessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(TextExerciseFactory.generateTextExercise(releaseDate, olderDueDate, assessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(TextExerciseFactory.generateTextExercise(releaseDate, olderDueDate, olderAssessmentDueDate, instructorsCourse)));
        instructorsCourse.addExercises(exerciseRepo.save(TextExerciseFactory.generateTextExercise(releaseDate, null, olderAssessmentDueDate, instructorsCourse)));

        // Add one exercise which will be sorted last due to the oldest assessment due date
        var exerciseNotReturned = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, oldestAssessmentDueDate, instructorsCourse);
        exerciseNotReturned = exerciseRepo.save(exerciseNotReturned);
        final var exerciseId = exerciseNotReturned.getId();
        instructorsCourse.addExercises(exerciseNotReturned);
        courseRepo.save(instructorsCourse);

        var courseDtos = request.getList("/api/courses/stats-for-management-overview", HttpStatus.OK, CourseManagementOverviewStatisticsDTO.class);
        // We only added one course, so expect one dto
        assertThat(courseDtos).hasSize(1);

        var optionalCourseDTO = courseDtos.stream().filter(dto -> Objects.equals(dto.getCourseId(), instructorsCourse.getId())).findFirst();
        assertThat(optionalCourseDTO).as("Active course was not filtered").isPresent();
        CourseManagementOverviewStatisticsDTO dto = optionalCourseDTO.orElseThrow();

        assertThat(dto.getCourseId()).isEqualTo(instructorsCourse.getId());

        // Only five exercises should be returned
        var exerciseDTOS = dto.getExerciseDTOS();
        assertThat(exerciseDTOS).hasSize(5);

        // The one specific exercise should not be included
        var statisticsOptional = exerciseDTOS.stream().filter(exercise -> exercise.getExerciseId().equals(exerciseId)).findFirst();
        assertThat(statisticsOptional).isEmpty();
    }

    public void testGetCourseManagementDetailDataForFutureCourse() throws Exception {
        adjustUserGroupsToCustomGroups();
        ZonedDateTime now = ZonedDateTime.now();
        var course = courseUtilService.createCourse();
        course.setInstructorGroupName(userPrefix + "instructor");
        course.setEditorGroupName(userPrefix + "editor");
        course.setTeachingAssistantGroupName(userPrefix + "tutor");
        course.setStudentGroupName(userPrefix + "student");

        course.setStartDate(now.plusWeeks(3));

        userUtilService.createAndSaveUser(userPrefix + "user1");
        userUtilService.createAndSaveUser(userPrefix + "user2");

        var instructor2 = userUtilService.createAndSaveUser(userPrefix + "instructor2");
        instructor2.setGroups(Set.of(userPrefix + "instructor"));
        userRepo.save(instructor2);

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
        adjustUserGroupsToCustomGroups();
        ZonedDateTime now = ZonedDateTime.now();
        // add courses with exercises
        var courses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, true, 5);
        var course1 = courses.get(0);
        var course2 = courses.get(1);
        course1.setStartDate(now.minusWeeks(2));
        course1.setStudentGroupName(userPrefix + "student");
        course1.setTeachingAssistantGroupName(userPrefix + "tutor");
        course1.setEditorGroupName(userPrefix + "editor");
        course1.setInstructorGroupName(userPrefix + "instructor");

        /*
         * We will duplicate the following submission and result configuration with course2. course1 contains additional submissions created by the ParticipationUtilService. These
         * submissions would make the test of the active students distribution flaky but are necessary for other test statements to be meaningful. Thus, we test the actual test
         * distribution only for course2.
         */
        course2.setStartDate(now.minusWeeks(2));
        course2.setStudentGroupName(userPrefix + "student");
        course2.setTeachingAssistantGroupName(userPrefix + "tutor");
        course2.setEditorGroupName(userPrefix + "editor");
        course2.setInstructorGroupName(userPrefix + "instructor");

        var student1 = userUtilService.createAndSaveUser(userPrefix + "user1");
        var student2 = userUtilService.createAndSaveUser(userPrefix + "user2");
        // Fetch an instructor
        var instructor = userUtilService.getUserByLogin(userPrefix + "instructor1");

        var releaseDate = now.minusDays(7);
        var dueDate = now.minusDays(2);
        var assessmentDueDate = now.minusDays(1);
        var exercise1 = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, course1);
        exercise1.setMaxPoints(5.0);
        exercise1 = exerciseRepo.save(exercise1);

        var exercise2 = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, course2);
        exercise2.setMaxPoints(5.0);
        exercise2 = exerciseRepo.save(exercise2);

        // Add a single participation to that exercise
        final var exercise1Id = exercise1.getId();
        final var exercise2Id = exercise2.getId();

        var result1 = participationUtilService.createParticipationSubmissionAndResult(exercise1Id, student1, 5.0, 0.0, 60, true);
        var result2 = participationUtilService.createParticipationSubmissionAndResult(exercise1Id, student2, 5.0, 0.0, 40, true);

        var result21 = participationUtilService.createParticipationSubmissionAndResult(exercise2Id, student1, 5.0, 0.0, 60, true);
        var result22 = participationUtilService.createParticipationSubmissionAndResult(exercise2Id, student2, 5.0, 0.0, 40, true);

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
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(userPrefix + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        Feedback feedback1 = new Feedback();
        feedback1.setCredits(2.5);
        feedback1.setReference(TextExerciseFactory.generateTextBlock(0, 5, "test1").getId());
        Feedback feedback2 = new Feedback();
        feedback2.setCredits(-0.5);
        feedback2.setReference(TextExerciseFactory.generateTextBlock(0, 5, "test2").getId());
        Feedback feedback3 = new Feedback();
        feedback3.setCredits(1.5);
        feedback3.setReference(TextExerciseFactory.generateTextBlock(0, 5, "test3").getId());
        Feedback feedback4 = new Feedback();
        feedback4.setCredits(-1.5);
        feedback4.setReference(TextExerciseFactory.generateTextBlock(0, 5, "test4").getId());
        Feedback feedback5 = new Feedback();
        feedback5.setCredits(2.0);
        feedback5.setReference(TextExerciseFactory.generateTextBlock(0, 5, "test5").getId());
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

        ComplaintResponse feedbackResponse = complaintUtilService.createInitialEmptyResponse(userPrefix + "tutor2", feedbackRequest);
        feedbackResponse.getComplaint().setAccepted(true);
        feedbackResponse.setResponseText("accepted");
        var feedbackListForMoreFeedback = Arrays.asList(feedback1, feedback2, feedback3, feedback4);

        var feedbackUpdate = new TextAssessmentUpdateDTO();
        feedbackUpdate.feedbacks(feedbackListForMoreFeedback).complaintResponse(feedbackResponse);
        feedbackUpdate.setTextBlocks(new HashSet<>());

        request.putWithResponseBody("/api/participations/" + result2.getSubmission().getParticipation().getId() + "/submissions/" + result2.getSubmission().getId()
                + "/text-assessment-after-complaint", feedbackUpdate, Result.class, HttpStatus.OK);

        await().until(participantScoreScheduleService::isIdle);
        TextExercise finalExercise1 = exercise1;
        await().until(() -> participantScoreRepository.findAllByExercise(finalExercise1).size() == 2);
        TextExercise finalExercise2 = exercise2;
        await().until(() -> participantScoreRepository.findAllByExercise(finalExercise2).size() == 2);

        // API call
        var courseDTO = request.get("/api/courses/" + course1.getId() + "/management-detail", HttpStatus.OK, CourseManagementDetailViewDTO.class);

        // Check results
        assertThat(courseDTO).isNotNull();

        assertThat(courseDTO.activeStudents()).hasSize(3);

        // number of users in course
        assertThat(courseDTO.numberOfStudentsInCourse()).isEqualTo(8);
        assertThat(courseDTO.numberOfTeachingAssistantsInCourse()).isEqualTo(5);
        assertThat(courseDTO.numberOfInstructorsInCourse()).isEqualTo(1);

        // Assessments - 80 because each we have only 4 submissions which have assessments, but as some have complaints which got accepted
        // they now have 2 results each.
        assertThat(courseDTO.currentPercentageAssessments()).isEqualTo(80);
        assertThat(courseDTO.currentAbsoluteAssessments()).isEqualTo(4);
        assertThat(courseDTO.currentMaxAssessments()).isEqualTo(5);

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
        int periodSize = 8;
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("periodIndex", Integer.toString(periodIndex));
        parameters.add("periodSize", Integer.toString(periodSize));

        Integer[] activeStudents = request.get("/api/courses/" + course1.getId() + "/statistics", HttpStatus.OK, Integer[].class, parameters);

        assertThat(activeStudents).isNotNull();
        assertThat(activeStudents).hasSize(3);

        course1.setStartDate(now.plusWeeks(10));
        course1.setEndDate(now.plusWeeks(20));
        activeStudents = request.get("/api/courses/" + course1.getId() + "/statistics", HttpStatus.OK, Integer[].class, parameters);
        assertThat(activeStudents).isNotNull();
    }

    // Test
    public void testAddUsersToCourseGroup(String group, String registrationNumber1, String registrationNumber2, String email) throws Exception {

        var course = courseUtilService.createCourse();
        StudentDTO dto1 = new StudentDTO("newstudent1", null, null, registrationNumber1, null);
        StudentDTO dto2 = new StudentDTO("newstudent2", null, null, registrationNumber2, null);
        StudentDTO dto3 = new StudentDTO(null, null, null, null, email);

        var newStudents = request.postListWithResponseBody("/api/courses/" + course.getId() + "/" + group, List.of(dto1, dto2, dto3), StudentDTO.class, HttpStatus.OK);
        assertThat(newStudents).hasSize(3);
        assertThat(newStudents).contains(dto1, dto2, dto3);
    }

    // Test
    public void testCreateCourseWithValidStartAndEndDate() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated());
    }

    // Test
    public void testCreateCourseWithInvalidStartAndEndDate() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().plusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testCreateInvalidOnlineCourse() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");

        // with EnrollmentEnabled
        course.setOnlineCourse(true);
        course.setEnrollmentEnabled(true);
        request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isBadRequest());
    }

    // Test
    public void testCreateValidOnlineCourse() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        Course courseWithOnlineConfiguration = courseRepo.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(createdCourse.getId());
        assertThat(courseWithOnlineConfiguration.getOnlineCourseConfiguration()).isNotNull();
        assertThat(courseWithOnlineConfiguration.getOnlineCourseConfiguration().getUserPrefix()).isEqualTo(courseWithOnlineConfiguration.getShortName());
    }

    public void testUpdateToOnlineCourse() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        createdCourse.setOnlineCourse(true);
        result = request.performMvcRequest(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        assertThat(updatedCourse.getOnlineCourseConfiguration()).isNotNull();
        assertThat(updatedCourse.getOnlineCourseConfiguration().getUserPrefix()).isEqualTo(updatedCourse.getShortName());
    }

    public void testOnlineCourseConfigurationIsLazyLoaded() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        course = courseRepo.save(course);
        var courseId = course.getId();

        List<Course> courses = request.getList("/api/courses", HttpStatus.OK, Course.class);

        Course receivedCourse = courses.stream().filter(c -> courseId.equals(c.getId())).findFirst().orElseThrow();
        assertThat(receivedCourse.getOnlineCourseConfiguration()).as("Online course configuration is lazily loaded").isNull();
    }

    // Test
    public void testUpdateOnlineCourseConfiguration() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);

        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        course.setOnlineCourse(true);

        result = request.performMvcRequest(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        Course actualCourse = courseRepo.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(updatedCourse.getId());
        OnlineCourseConfiguration ocConfiguration = actualCourse.getOnlineCourseConfiguration();

        assertThat(ocConfiguration).isNotNull();
        assertThat(ocConfiguration.getUserPrefix()).isEqualTo(actualCourse.getShortName());
    }

    // Test
    public void testUpdateCourseRemoveOnlineCourseConfiguration() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);

        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        createdCourse.setOnlineCourse(false);
        result = request.performMvcRequest(buildUpdateCourse(createdCourse.getId(), createdCourse)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        Course courseWithoutOnlineConfiguration = courseRepo.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(updatedCourse.getId());
        assertThat(courseWithoutOnlineConfiguration.getOnlineCourseConfiguration()).isNull();
    }

    // Test
    public void testDeleteCourseDeletesOnlineConfiguration() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        CourseFactory.generateOnlineCourseConfiguration(course, "prefix", null);
        course = courseRepo.save(course);

        request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);

        assertThat(onlineCourseConfigurationRepository.findById(course.getOnlineCourseConfiguration().getId())).isNotPresent();
    }

    // Test
    public void testUpdateInvalidOnlineCourseConfiguration() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);

        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        String courseId = createdCourse.getId().toString();

        // without onlinecourseconfiguration
        OnlineCourseConfiguration ocConfiguration = null;
        request.putWithResponseBody(getUpdateOnlineCourseConfigurationPath(courseId), ocConfiguration, OnlineCourseConfiguration.class, HttpStatus.BAD_REQUEST);

        ocConfiguration = createdCourse.getOnlineCourseConfiguration();
        // with invalid user prefix - not matching regex
        CourseFactory.updateOnlineCourseConfiguration(ocConfiguration, "with space", null, "10000");
        request.putWithResponseBody(getUpdateOnlineCourseConfigurationPath(courseId), ocConfiguration, OnlineCourseConfiguration.class, HttpStatus.BAD_REQUEST);
    }

    public void testUpdateValidOnlineCourseConfigurationAsStudent_forbidden() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        CourseFactory.generateOnlineCourseConfiguration(course, "prefix", null);
        course = courseRepo.save(course);

        String courseId = course.getId().toString();

        request.putWithResponseBody(getUpdateOnlineCourseConfigurationPath(courseId), course.getOnlineCourseConfiguration(), OnlineCourseConfiguration.class, HttpStatus.FORBIDDEN);
    }

    public void testUpdateValidOnlineCourseConfigurationNotOnlineCourse() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(false);

        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        String courseId = createdCourse.getId().toString();

        OnlineCourseConfiguration onlineCourseConfiguration = CourseFactory.generateOnlineCourseConfiguration(course, "prefix", null);

        request.putWithResponseBody(getUpdateOnlineCourseConfigurationPath(courseId), onlineCourseConfiguration, OnlineCourseConfiguration.class, HttpStatus.BAD_REQUEST);
    }

    public void testUpdateValidOnlineCourseConfiguration_IdMismatch() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);

        MvcResult result = request.performMvcRequest(buildCreateCourse(course)).andExpect(status().isCreated()).andReturn();
        Course createdCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        String courseId = createdCourse.getId().toString();

        OnlineCourseConfiguration ocConfiguration = createdCourse.getOnlineCourseConfiguration();
        ocConfiguration.setId(10000L);
        request.putWithResponseBody(getUpdateOnlineCourseConfigurationPath(courseId), ocConfiguration, OnlineCourseConfiguration.class, HttpStatus.BAD_REQUEST);
    }

    public void testUpdateValidOnlineCourseConfiguration() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        CourseFactory.generateOnlineCourseConfiguration(course, "prefix", null);
        course = courseRepo.save(course);

        OnlineCourseConfiguration ocConfiguration = course.getOnlineCourseConfiguration();
        ocConfiguration.setUserPrefix("prefix");

        String courseId = course.getId().toString();

        OnlineCourseConfiguration response = request.putWithResponseBody(getUpdateOnlineCourseConfigurationPath(courseId), ocConfiguration, OnlineCourseConfiguration.class,
                HttpStatus.OK);
        assertThat(response).usingRecursiveComparison().ignoringFields("id").isEqualTo(ocConfiguration);
    }

    /**
     * Tests fetching online courses for an LTI dashboard using a client ID.
     * Verifies the response matches the expected course details.
     *
     * @throws Exception if any error occurs during the test execution.
     */
    public void testFindAllOnlineCoursesForLtiDashboard() throws Exception {
        LtiPlatformConfiguration ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setRegistrationId("registrationId");
        ltiPlatformConfiguration.setClientId("clientId");
        ltiPlatformConfiguration.setAuthorizationUri("authUri");
        ltiPlatformConfiguration.setTokenUri("tokenUri");
        ltiPlatformConfiguration.setJwkSetUri("jwkUri");
        ltiPlatformConfigurationRepository.save(ltiPlatformConfiguration);

        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setOnlineCourse(true);
        OnlineCourseConfiguration onlineCourseConfiguration = CourseFactory.generateOnlineCourseConfiguration(course, "prefix", "url");
        onlineCourseConfiguration.setLtiPlatformConfiguration(ltiPlatformConfiguration);

        course = courseRepo.save(course);
        onlineCourseConfigurationRepository.save(onlineCourseConfiguration);

        OnlineCourseConfiguration ocConfiguration = course.getOnlineCourseConfiguration();
        String clientId = ocConfiguration.getLtiPlatformConfiguration().getRegistrationId();

        String jsonResponse = request.get("/api/courses/for-lti-dashboard?clientId=" + clientId, HttpStatus.OK, String.class);
        List<OnlineCourseDTO> receivedCourseForDashboard = objectMapper.readValue(jsonResponse, new TypeReference<List<OnlineCourseDTO>>() {
            // This empty block is necessary to provide type information for JSON deserialization
        });

        assertThat(receivedCourseForDashboard).hasSize(1);

        OnlineCourseDTO dto = receivedCourseForDashboard.get(0);
        assertThat(dto.id()).isEqualTo(course.getId());
        assertThat(dto.title()).isEqualTo(course.getTitle());
        assertThat(dto.shortName()).isEqualTo(course.getShortName());
        assertThat(dto.registrationId()).isEqualTo(clientId);
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
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultStudentGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultTeachingAssistantGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultEditorGroupName());
        mockDelegate.mockCreateGroupInUserManagement(course.getDefaultInstructorGroupName());

        var result = request.performMvcRequest(buildCreateCourse(course, "testIcon")).andExpect(status().isCreated()).andReturn();
        course = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        assertThat(course.getCourseIcon()).as("Course icon got stored").isNotNull();
        var imgResult = request.performMvcRequest(get(course.getCourseIcon())).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();
        assertThat(imgResult.getResponse().getContentAsByteArray()).isNotEmpty();

        var createdCourse = courseRepo.findByIdElseThrow(course.getId());
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

        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Course savedCourse = courseRepo.save(course);
        byte[] iconBytes = "icon".getBytes();
        MockMultipartFile iconFile = new MockMultipartFile("file", "icon.png", MediaType.APPLICATION_JSON_VALUE, iconBytes);
        Course savedCourseWithFile = request.putWithMultipartFile("/api/courses/" + savedCourse.getId(), savedCourse, "course", iconFile, Course.class, HttpStatus.OK, null);
        Path path = FilePathService.actualPathForPublicPath(URI.create(savedCourseWithFile.getCourseIcon()));

        savedCourseWithFile.setCourseIcon(null);
        request.putWithMultipartFile("/api/courses/" + savedCourseWithFile.getId(), savedCourseWithFile, "course", null, Course.class, HttpStatus.OK, null);
        await().until(() -> !Files.exists(path));
        course = courseRepo.findByIdElseThrow(course.getId());
        assertThat(course.getCourseIcon()).as("course icon was deleted correctly").isNull();
        assertThat(path).as("course icon file was deleted correctly").doesNotExist();
    }

    private String getUpdateOnlineCourseConfigurationPath(String courseId) {
        return "/api/courses/" + courseId + "/onlineCourseConfiguration";
    }

    // Test
    public void testUpdateCourseEnableLearningPaths() throws Exception {
        Course course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), new HashSet<>(), "editlearningpathenabledcourse",
                "tutor", "editor", "instructor");
        course.setLearningPathsEnabled(false);
        courseRepo.save(course);
        User student = userUtilService.getUserByLogin(userPrefix + "student1");
        student.setGroups(Set.of("editlearningpathenabledcourse"));
        userRepo.save(student);

        course.setLearningPathsEnabled(true);

        MvcResult result = request.performMvcRequest(buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);
        assertThat(updatedCourse.getLearningPathsEnabled()).isTrue();
        final var learningPath = learningPathRepository.findByCourseIdAndUserId(course.getId(), student.getId());
        assertThat(learningPath).as("enable learning paths triggers generation").isPresent();
    }

    // Test
    public void testGetCoursesForImportWithoutPermission() throws Exception {
        request.getList("/api/courses/for-import", HttpStatus.FORBIDDEN, CourseForImportDTO.class);
    }

    // Test
    public void testGetCoursesForImport() throws Exception {
        List<Course> coursesExpected = new ArrayList<>();
        for (int i = 1; i < 3; i++) {
            coursesExpected.add(courseUtilService.createCourse((long) i));
        }
        var searchTerm = pageableSearchUtilService.configureSearch("");

        SearchResultPageDTO<CourseForImportDTO> result = request.getSearchResult("/api/courses/for-import", HttpStatus.OK, CourseForImportDTO.class,
                pageableSearchUtilService.searchMapping(searchTerm));

        List<CourseForImportDTO> courses = result.getResultsOnPage();

        for (Course course : coursesExpected) {
            Optional<CourseForImportDTO> found = courses.stream().filter(c -> Objects.equals(c.id(), course.getId())).findFirst();
            assertThat(found).as("Course is available").isPresent();
        }
    }
}
