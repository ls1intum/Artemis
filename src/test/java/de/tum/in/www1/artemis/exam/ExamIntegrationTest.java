package de.tum.in.www1.artemis.exam;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.assessment.GradingScaleUtilService;
import de.tum.in.www1.artemis.bonus.BonusFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.QuizSubmissionService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.exam.*;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "examintegration";

    public static final String STUDENT_111 = TEST_PREFIX + "student111";

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private QuizSubmissionService quizSubmissionService;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ExamService examService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private ExamDateService examDateService;

    @Autowired
    private ExamRegistrationService examRegistrationService;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ParticipationTestRepository participationTestRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ExamAccessService examAccessService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    private Course course1;

    private Course course2;

    private Course course10;

    private Exam exam1;

    private Exam exam2;

    private Exam testExam1;

    private static final int NUMBER_OF_STUDENTS = 4;

    private static final int NUMBER_OF_TUTORS = 2;

    private final List<LocalRepository> studentRepos = new ArrayList<>();

    private User instructor;

    @BeforeEach
    void initTestCase() {

        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor10", passwordService.hashPassword(UserFactory.USER_PASSWORD));

        course1 = courseUtilService.addEmptyCourse();
        course2 = courseUtilService.addEmptyCourse();

        course10 = courseUtilService.createCourse();
        course10.setInstructorGroupName("instructor10-test-group");
        course10 = courseRepo.save(course10);

        User instructor10 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor10");
        instructor10.setGroups(Set.of(course10.getInstructorGroupName()));
        userRepo.save(instructor10);

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setGroups(Set.of(course1.getStudentGroupName()));
        userRepo.save(student1);

        exam1 = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam1, "exam1 channel");
        exam2 = examUtilService.addExamWithExerciseGroup(course1, true);
        examUtilService.addExamChannel(exam2, "exam2 channel");
        testExam1 = examUtilService.addTestExam(course1);
        examUtilService.addStudentExamForTestExam(testExam1, student1);

        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        bitbucketRequestMockProvider.enableMockingOfRequests();

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();
    }

    @AfterEach
    void tearDown() throws Exception {
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        if (programmingExerciseTestService.exerciseRepo != null) {
            programmingExerciseTestService.tearDown();
        }

        for (var repo : studentRepos) {
            repo.resetLocalRepo();
        }

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
        participantScoreScheduleService.shutdown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterUserInExam_addedToCourseStudentsGroup() throws Exception {
        User student42 = userUtilService.getUserByLogin(TEST_PREFIX + "student42");
        jiraRequestMockProvider.enableMockingOfRequests();
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        bitbucketRequestMockProvider.mockUpdateUserDetails(student42.getLogin(), student42.getEmail(), student42.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();

        List<User> studentsInCourseBefore = userRepo.findAllInGroupWithAuthorities(course1.getStudentGroupName());
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student42", null, HttpStatus.OK, null);
        List<User> studentsInCourseAfter = userRepo.findAllInGroupWithAuthorities(course1.getStudentGroupName());
        studentsInCourseBefore.add(student42);
        assertThat(studentsInCourseBefore).containsExactlyInAnyOrderElementsOf(studentsInCourseAfter);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentToExam_testExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students/" + TEST_PREFIX + "student42", null, HttpStatus.BAD_REQUEST,
                null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveStudentToExam_testExam() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students/" + TEST_PREFIX + "student42", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterUsersInExam() throws Exception {
        userUtilService.addStudents(TEST_PREFIX, 5, 10);
        jiraRequestMockProvider.enableMockingOfRequests();

        var exam = ExamFactory.generateExam(course1);
        var savedExam = examRepository.save(exam);
        examUtilService.addExamChannel(savedExam, "test exam");
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student3 = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        var student5 = userUtilService.getUserByLogin(TEST_PREFIX + "student5");
        var student6 = userUtilService.getUserByLogin(TEST_PREFIX + "student6");
        var student7 = userUtilService.getUserByLogin(TEST_PREFIX + "student7");
        var student8 = userUtilService.getUserByLogin(TEST_PREFIX + "student8");
        var student9 = userUtilService.getUserByLogin(TEST_PREFIX + "student9");
        var student10 = userUtilService.getUserByLogin(TEST_PREFIX + "student10");
        var registrationNumber1 = "1111111";
        var registrationNumber2 = "1111112";
        var registrationNumber3 = "1111113";
        var registrationNumber3WithTypo = registrationNumber3 + "0";
        var registrationNumber5 = "1111115";
        var registrationNumber5WithTypo = registrationNumber5 + "1";
        var registrationNumber6 = "1111116";
        var registrationNumber99 = "1111199";
        var registrationNumber111 = "1111100";
        var emptyRegistrationNumber = "";
        student1.setRegistrationNumber(registrationNumber1);
        student2.setRegistrationNumber(registrationNumber2);
        student3.setRegistrationNumber(registrationNumber3);
        student5.setRegistrationNumber(registrationNumber5);
        student6.setRegistrationNumber(registrationNumber6);
        student7.setRegistrationNumber(null);
        student8.setRegistrationNumber("");
        student9.setRegistrationNumber(" ");
        student10.setRegistrationNumber(null);
        student1 = userRepo.save(student1);
        student2 = userRepo.save(student2);
        userRepo.save(student3);
        userRepo.save(student5);
        userRepo.save(student6);
        userRepo.save(student7);
        userRepo.save(student8);
        userRepo.save(student9);
        userRepo.save(student10);

        // mock the ldap service
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber3WithTypo);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(emptyRegistrationNumber);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber5WithTypo);
        var ldapUser111Dto = new LdapUserDto().registrationNumber(registrationNumber111).firstName(STUDENT_111).lastName(STUDENT_111).username(STUDENT_111)
                .email(STUDENT_111 + "@tum.de");
        doReturn(Optional.of(ldapUser111Dto)).when(ldapUserService).findByRegistrationNumber(registrationNumber111);

        // first and second mocked calls are expected to add student 5 and 99 to the course students
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        // third mocked call expected to create student 111
        jiraRequestMockProvider.mockCreateUserInExternalUserManagement(ldapUser111Dto.getUsername(), ldapUser111Dto.getFirstName() + " " + ldapUser111Dto.getLastName(),
                ldapUser111Dto.getEmail());
        // the last two mocked calls are expected to add students 111, 6, 7, 8, and 9 to the course student group
        for (int i = 0; i < 5; i++) {
            jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        }

        bitbucketRequestMockProvider.mockUpdateUserDetails(student5.getLogin(), student5.getEmail(), student5.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();

        var student99 = userUtilService.createAndSaveUser("student99"); // not registered for the course
        student99.setRegistrationNumber(registrationNumber99);
        userRepo.save(student99);
        bitbucketRequestMockProvider.mockUpdateUserDetails(student99.getLogin(), student99.getEmail(), student99.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        student99 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student99").get();
        assertThat(student99.getGroups()).doesNotContain(course1.getStudentGroupName());

        // Note: student101 is not yet a user of Artemis and should be retrieved from the LDAP

        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/" + TEST_PREFIX + "student1", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", null, HttpStatus.NOT_FOUND, null);

        Exam storedExam = examRepository.findWithExamUsersById(savedExam.getId()).get();
        ExamUser examUserStudent1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId()).get();
        assertThat(storedExam.getExamUsers()).containsExactly(examUserStudent1);

        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.OK);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
        storedExam = examRepository.findWithExamUsersById(savedExam.getId()).get();
        assertThat(storedExam.getExamUsers()).isEmpty();

        var studentDto1 = new StudentDTO().registrationNumber(registrationNumber1);
        var studentDto2 = new StudentDTO().registrationNumber(registrationNumber2);
        var studentDto3 = new StudentDTO().registrationNumber(registrationNumber3WithTypo); // explicit typo, should be a registration failure later
        var studentDto5 = new StudentDTO().registrationNumber(registrationNumber5WithTypo); // explicit typo, should fall back to login name later
        studentDto5.setLogin(student5.getLogin());
        var studentDto7 = new StudentDTO();
        studentDto7.setLogin(student7.getLogin());
        var studentDto8 = new StudentDTO();
        studentDto8.setLogin(student8.getLogin());
        var studentDto9 = new StudentDTO();
        studentDto9.setLogin(student9.getLogin());
        var studentDto10 = new StudentDTO(); // completely empty

        var studentDto99 = new StudentDTO().registrationNumber(registrationNumber99);
        var studentDto111 = new StudentDTO().registrationNumber(registrationNumber111);

        // Add a student with login but empty registration number
        var studentDto6 = new StudentDTO().registrationNumber(emptyRegistrationNumber);
        studentDto6.setLogin(student6.getLogin());
        var studentsToRegister = List.of(studentDto1, studentDto2, studentDto3, studentDto5, studentDto99, studentDto111, studentDto6, studentDto7, studentDto8, studentDto9,
                studentDto10);
        bitbucketRequestMockProvider.mockUpdateUserDetails(student6.getLogin(), student6.getEmail(), student6.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(student7.getLogin(), student7.getEmail(), student7.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(student8.getLogin(), student8.getEmail(), student8.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(student9.getLogin(), student9.getEmail(), student9.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(student10.getLogin(), student10.getEmail(), student10.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();

        // now we register all these students for the exam.
        List<StudentDTO> registrationFailures = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students",
                studentsToRegister, StudentDTO.class, HttpStatus.OK);
        assertThat(registrationFailures).containsExactlyInAnyOrder(studentDto3, studentDto10);
        storedExam = examRepository.findWithExamUsersById(savedExam.getId()).get();

        // now a new user student101 should exist
        var student101 = userUtilService.getUserByLogin(STUDENT_111);

        var examUser1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId()).get();
        var examUser2 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student2.getId()).get();
        var examUser5 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student5.getId()).get();
        var examUser99 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student99.getId()).get();
        var examUser101 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student101.getId()).get();
        var examUser6 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student6.getId()).get();
        var examUser7 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student7.getId()).get();
        var examUser8 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student8.getId()).get();
        var examUser9 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student9.getId()).get();

        assertThat(storedExam.getExamUsers()).containsExactlyInAnyOrder(examUser1, examUser2, examUser5, examUser99, examUser101, examUser6, examUser7, examUser8, examUser9);

        for (var examUser : storedExam.getExamUsers()) {
            // all registered users must have access to the course
            var user = userRepo.findOneWithGroupsAndAuthoritiesByLogin(examUser.getUser().getLogin()).get();
            assertThat(user.getGroups()).contains(course1.getStudentGroupName());
        }

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentsToExam_testExam() throws Exception {
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        String registrationNumber1 = "1111111";
        student1.setRegistrationNumber(registrationNumber1);
        userRepo.save(student1);

        StudentDTO studentDto1 = new StudentDTO().registrationNumber(registrationNumber1);
        List<StudentDTO> studentDTOS = List.of(studentDto1);
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", studentDTOS, StudentDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllActiveExams() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();
        jiraRequestMockProvider.mockCreateGroup(course10.getInstructorGroupName());
        jiraRequestMockProvider.mockAddUserToGroup(course10.getInstructorGroupName(), false);

        // switch to instructor10
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "instructor10"));
        // add additional active exam
        var exam3 = examUtilService.addExamWithExerciseGroup(course10, true);
        exam3.setVisibleDate(ZonedDateTime.now().plusDays(1));
        exam3 = examRepository.save(exam3);

        // add additional exam not active
        var exam4 = examUtilService.addExamWithExerciseGroup(course10, true);
        exam4.setVisibleDate(ZonedDateTime.now().minusDays(10));
        examRepository.save(exam4);

        List<Exam> activeExams = request.getList("/api/exams/active", HttpStatus.OK, Exam.class);
        // only exam3 should be returned (size 1)
        assertThat(activeExams).hasSize(1);
        assertThat(activeExams).containsExactlyInAnyOrder(exam3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveAllStudentsFromExam_testExam() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", HttpStatus.BAD_REQUEST);
    }

    // TODO IMPORTANT test more complex exam configurations (mixed exercise type, more variants and more registered students)
    @Nested
    class ExamStartTest {

        private Set<User> registeredUsers;

        private final List<StudentExam> createdStudentExams = new ArrayList<>();

        private User student1;

        private User student2;

        @BeforeEach
        void init() throws Exception {
            doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

            // registering users
            student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            registeredUsers = Set.of(student1, student2);
            exam2.setExamUsers(Set.of(new ExamUser()));
            // setting dates
            exam2.setStartDate(now().plusHours(2));
            exam2.setEndDate(now().plusHours(3));
            exam2.setVisibleDate(now().plusHours(1));
        }

        @AfterEach
        void cleanup() {
            // Cleanup of Bidirectional Relationships
            for (StudentExam studentExam : createdStudentExams) {
                exam2.removeStudentExam(studentExam);
            }
            examRepository.save(exam2);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testStartExercisesWithTextExercise() throws Exception {
            // creating exercise
            ExerciseGroup exerciseGroup = exam2.getExerciseGroups().get(0);

            TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
            exerciseGroup.addExercise(textExercise);
            exerciseGroupRepository.save(exerciseGroup);
            textExercise = exerciseRepo.save(textExercise);

            createStudentExams(textExercise);

            List<Participation> studentParticipations = invokePrepareExerciseStart();

            for (Participation participation : studentParticipations) {
                assertThat(participation.getExercise()).isEqualTo(textExercise);
                assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
                assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam2.getExerciseGroups().get(0));
                assertThat(participation.getSubmissions()).hasSize(1);
                var textSubmission = (TextSubmission) participation.getSubmissions().iterator().next();
                assertThat(textSubmission.getText()).isNull();
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testStartExercisesWithModelingExercise() throws Exception {
            // creating exercise
            ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exam2.getExerciseGroups().get(0));
            exam2.getExerciseGroups().get(0).addExercise(modelingExercise);
            exerciseGroupRepository.save(exam2.getExerciseGroups().get(0));
            modelingExercise = exerciseRepo.save(modelingExercise);

            createStudentExams(modelingExercise);

            List<Participation> studentParticipations = invokePrepareExerciseStart();

            for (Participation participation : studentParticipations) {
                assertThat(participation.getExercise()).isEqualTo(modelingExercise);
                assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
                assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam2.getExerciseGroups().get(0));
                assertThat(participation.getSubmissions()).hasSize(1);
                var modelingSubmission = (ModelingSubmission) participation.getSubmissions().iterator().next();
                assertThat(modelingSubmission.getModel()).isNull();
                assertThat(modelingSubmission.getExplanationText()).isNull();
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testStartExerciseWithProgrammingExercise() throws Exception {
            bitbucketRequestMockProvider.enableMockingOfRequests(true);
            bambooRequestMockProvider.enableMockingOfRequests(true);

            ProgrammingExercise programmingExercise = createProgrammingExercise();

            participationUtilService.mockCreationOfExerciseParticipation(programmingExercise, versionControlService, continuousIntegrationService);

            createStudentExams(programmingExercise);

            var studentParticipations = invokePrepareExerciseStart();

            for (Participation participation : studentParticipations) {
                assertThat(participation.getExercise()).isEqualTo(programmingExercise);
                assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
                assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam2.getExerciseGroups().get(0));
                // No initial submissions should be created for programming exercises
                assertThat(participation.getSubmissions()).isEmpty();
                assertThat(((ProgrammingExerciseParticipation) participation).isLocked()).isTrue();
                verify(versionControlService, times(0)).configureRepository(eq(programmingExercise), (ProgrammingExerciseStudentParticipation) eq(participation), eq(true));
            }
        }

        private static class ExamStartDateSource implements ArgumentsProvider {

            @Override
            public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
                return Stream.of(Arguments.of(ZonedDateTime.now().minusHours(1)), // after exam start
                        Arguments.arguments(ZonedDateTime.now().plusMinutes(3)) // before exam start but after pe unlock date
                );
            }
        }

        @ParameterizedTest(name = "{displayName} [{index}]")
        @ArgumentsSource(ExamStartDateSource.class)
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testStartExerciseWithProgrammingExercise_participationUnlocked(ZonedDateTime startDate) throws Exception {
            exam2.setVisibleDate(ZonedDateTime.now().minusHours(2));
            exam2.setStartDate(startDate);
            examRepository.save(exam2);

            bitbucketRequestMockProvider.enableMockingOfRequests(true);
            bambooRequestMockProvider.enableMockingOfRequests(true);

            ProgrammingExercise programmingExercise = createProgrammingExercise();

            participationUtilService.mockCreationOfExerciseParticipation(programmingExercise, versionControlService, continuousIntegrationService);

            createStudentExams(programmingExercise);

            var studentParticipations = invokePrepareExerciseStart();

            for (Participation participation : studentParticipations) {
                assertThat(participation.getExercise()).isEqualTo(programmingExercise);
                assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
                assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam2.getExerciseGroups().get(0));
                // No initial submissions should be created for programming exercises
                assertThat(participation.getSubmissions()).isEmpty();
                // The participation should not get locked if it gets created after the exam already started
                assertThat(((ProgrammingExerciseParticipation) participation).isLocked()).isFalse();
                verify(versionControlService, atLeast(1)).configureRepository(eq(programmingExercise), (ProgrammingExerciseStudentParticipation) eq(participation), eq(true));
            }
        }

        private void createStudentExams(Exercise exercise) {
            // creating student exams
            for (User user : registeredUsers) {
                StudentExam studentExam = new StudentExam();
                studentExam.addExercise(exercise);
                studentExam.setUser(user);
                exam2.addStudentExam(studentExam);
                createdStudentExams.add(studentExamRepository.save(studentExam));
            }

            exam2 = examRepository.save(exam2);
        }

        private ProgrammingExercise createProgrammingExercise() {
            ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exam2.getExerciseGroups().get(0));
            programmingExercise = exerciseRepo.save(programmingExercise);
            programmingExercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
            exam2.getExerciseGroups().get(0).addExercise(programmingExercise);
            exerciseGroupRepository.save(exam2.getExerciseGroups().get(0));
            return programmingExercise;
        }

        private List<Participation> invokePrepareExerciseStart() throws Exception {
            // invoke start exercises
            int noGeneratedParticipations = prepareExerciseStart(exam2);
            verify(gitService, times(getNumberOfProgrammingExercises(exam2))).combineAllCommitsOfRepositoryIntoOne(any());
            assertThat(noGeneratedParticipations).isEqualTo(exam2.getStudentExams().size());
            return participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam2.getId());
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExams() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);

        // invoke generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getExamUsers().size());
        for (StudentExam studentExam : studentExams) {
            assertThat(studentExam.getWorkingTime()).as("Working time is set correctly").isEqualTo(120 * 60);
        }

        for (var studentExam : studentExams) {
            assertThat(studentExam.getExercises()).hasSize(exam.getNumberOfExercisesInExam());
            assertThat(studentExam.getExam()).isEqualTo(exam);
            // TODO: check exercise configuration, each mandatory exercise group has to appear, one optional exercise should appear
        }

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsCleanupOldParticipations() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 4);

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.OK);

        List<Participation> studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).isEmpty();

        // invoke start exercises
        studentExamService.startExercises(exam.getId()).join();

        studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).hasSize(16);

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.OK);

        studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).isEmpty();

        // invoke start exercises
        studentExamService.startExercises(exam.getId()).join();

        studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).hasSize(16);

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExams_testExam() throws Exception {
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNoExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, true);
        exam.setStartDate(now());
        exam.setEndDate(now().plusHours(2));
        exam = examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNoExerciseNumber_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setNumberOfExercisesInExam(null);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNotEnoughExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() + 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsTooManyMandatoryExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() - 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExams() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);
        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getExamUsers().size());
        exam = examRepository.save(exam);

        // Register two new students
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 3, 4);

        // Generate individual exams for the two missing students
        List<StudentExam> missingStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(missingStudentExams).hasSize(2);

        // Fetch student exams
        List<StudentExam> studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(exam.getExamUsers().size());

        // Another request should not create any exams
        missingStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        assertThat(missingStudentExams).isEmpty();
        studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(exam.getExamUsers().size());

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExams_testExam() throws Exception {
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/generate-missing-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEvaluateQuizExercises_testExam() throws Exception {
        request.post("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovingAllStudents() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 4);

        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(4);
        assertThat(exam.getExamUsers()).hasSize(4);

        int numberOfGeneratedParticipations = prepareExerciseStart(exam);

        assertThat(numberOfGeneratedParticipations).isEqualTo(16);
        verify(gitService, times(getNumberOfProgrammingExercises(exam))).combineAllCommitsOfRepositoryIntoOne(any());
        // Fetch student exams
        List<StudentExam> studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(4);
        List<StudentParticipation> participationList = new ArrayList<>();
        Exercise[] exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(new Exercise[0]);
        for (Exercise value : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(value.getId()));
        }
        assertThat(participationList).hasSize(16);

        // TODO there should be some participation but no submissions unfortunately
        // remove all students
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", HttpStatus.OK);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(storedExam.getExamUsers()).isEmpty();

        // Fetch student exams
        studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).isEmpty();

        // Fetch participations
        exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(new Exercise[0]);
        participationList = new ArrayList<>();
        for (Exercise exercise : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(exercise.getId()));
        }
        assertThat(participationList).hasSize(16);

    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovingAllStudentsAndParticipations() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 4);

        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(4);
        assertThat(exam.getExamUsers()).hasSize(4);

        int numberOfGeneratedParticipations = prepareExerciseStart(exam);
        verify(gitService, times(getNumberOfProgrammingExercises(exam))).combineAllCommitsOfRepositoryIntoOne(any());
        assertThat(numberOfGeneratedParticipations).isEqualTo(16);
        // Fetch student exams
        List<StudentExam> studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(4);
        List<StudentParticipation> participationList = new ArrayList<>();
        Exercise[] exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(new Exercise[0]);
        for (Exercise value : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(value.getId()));
        }
        assertThat(participationList).hasSize(16);

        // TODO there should be some participation but no submissions unfortunately
        // remove all students
        var paramsParticipations = new LinkedMultiValueMap<String, String>();
        paramsParticipations.add("withParticipationsAndSubmission", "true");
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", HttpStatus.OK, paramsParticipations);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(storedExam.getExamUsers()).isEmpty();

        // Fetch student exams
        studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).isEmpty();

        // Fetch participations
        exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(new Exercise[0]);
        participationList = new ArrayList<>();
        for (Exercise exercise : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(exercise.getId()));
        }
        assertThat(participationList).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testSaveExamWithExerciseGroupWithExerciseToDatabase() {
        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
        ExamFactory.generateExam(course1);
        request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN, Exam.class);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reset", HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", null, HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", Collections.singletonList(new StudentDTO()), HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testCreateExam_checkCourseAccess_InstructorNotInCourse_forbidden() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor() throws Exception {
        // Test for bad request when exam id is already set.
        Exam examA = ExamFactory.generateExam(course1, "examA");
        examA.setId(55L);
        request.post("/api/courses/" + course1.getId() + "/exams", examA, HttpStatus.BAD_REQUEST);
        // Test for bad request when course is null.
        Exam examB = ExamFactory.generateExam(course1, "examB");
        examB.setCourse(null);
        request.post("/api/courses/" + course1.getId() + "/exams", examB, HttpStatus.BAD_REQUEST);
        // Test for bad request when course deviates from course specified in route.
        Exam examC = ExamFactory.generateExam(course1, "examC");
        request.post("/api/courses/" + course2.getId() + "/exams", examC, HttpStatus.BAD_REQUEST);
        // Test invalid dates
        List<Exam> examsWithInvalidDate = createExamsWithInvalidDates(course1);
        for (var exam : examsWithInvalidDate) {
            request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        }
        // Test for conflict when user tries to create an exam with exercise groups.
        Exam examD = ExamFactory.generateExam(course1, "examD");
        examD.addExerciseGroup(ExamFactory.generateExerciseGroup(true, exam1));
        request.post("/api/courses/" + course1.getId() + "/exams", examD, HttpStatus.CONFLICT);

        courseUtilService.enableMessagingForCourse(course1);
        // Test examAccessService.
        Exam examE = ExamFactory.generateExam(course1, "examE");
        examE.setTitle("          Exam 123              ");
        URI examUri = request.post("/api/courses/" + course1.getId() + "/exams", examE, HttpStatus.CREATED);
        Exam savedExam = request.get(String.valueOf(examUri), HttpStatus.OK, Exam.class);
        assertThat(savedExam.getTitle()).isEqualTo("Exam 123");
        verify(examAccessService, times(1)).checkCourseAccessForInstructorElseThrow(course1.getId());

        Channel channelFromDB = channelRepository.findChannelByExamId(savedExam.getId());
        assertThat(channelFromDB).isNotNull();

        // Check that the conversation participants are added correctly to the exercise channel
        await().until(() -> {
            SecurityUtils.setAuthorizationObject();
            Set<ConversationParticipant> conversationParticipants = conversationParticipantRepository.findConversationParticipantByConversationId(channelFromDB.getId());
            return conversationParticipants.size() == 3; // only the instructors and tutors should be added to exam channel, not students (see @BeforeEach)
        });
    }

    private List<Exam> createExamsWithInvalidDates(Course course) {
        // Test for bad request, visible date not set
        Exam examA = ExamFactory.generateExam(course);
        examA.setVisibleDate(null);
        // Test for bad request, start date not set
        Exam examB = ExamFactory.generateExam(course);
        examB.setStartDate(null);
        // Test for bad request, end date not set
        Exam examC = ExamFactory.generateExam(course);
        examC.setEndDate(null);
        // Test for bad request, start date not after visible date
        Exam examD = ExamFactory.generateExam(course);
        examD.setStartDate(examD.getVisibleDate());
        // Test for bad request, end date not after start date
        Exam examE = ExamFactory.generateExam(course);
        examE.setEndDate(examE.getStartDate());
        // Test for bad request, when visibleDate equals the startDate
        Exam examF = ExamFactory.generateExam(course);
        examF.setVisibleDate(examF.getStartDate());
        // Test for bad request, when exampleSolutionPublicationDate is before the visibleDate
        Exam examG = ExamFactory.generateExam(course);
        examG.setExampleSolutionPublicationDate(examG.getVisibleDate().minusHours(1));
        return List.of(examA, examB, examC, examD, examE, examF, examG);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor() throws Exception {
        // Test the creation of a test exam
        Exam examA = ExamFactory.generateTestExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", examA, HttpStatus.CREATED);

        verify(examAccessService, times(1)).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_withVisibleDateEqualsStartDate() throws Exception {
        // Test the creation of a test exam, where visibleDate equals StartDate
        Exam examB = ExamFactory.generateTestExam(course1);
        examB.setVisibleDate(examB.getStartDate());
        request.post("/api/courses/" + course1.getId() + "/exams", examB, HttpStatus.CREATED);

        verify(examAccessService, times(1)).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_badRequestWithWorkingTimeGreaterThanWorkingWindow() throws Exception {
        // Test for bad request, where workingTime is greater than difference between StartDate and EndDate
        Exam examC = ExamFactory.generateTestExam(course1);
        examC.setWorkingTime(5000);
        request.post("/api/courses/" + course1.getId() + "/exams", examC, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_badRequestWithWorkingTimeSetToZero() throws Exception {
        // Test for bad request, if the working time is 0
        Exam examD = ExamFactory.generateTestExam(course1);
        examD.setWorkingTime(0);
        request.post("/api/courses/" + course1.getId() + "/exams", examD, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_testExam_CorrectionRoundViolation() throws Exception {
        Exam exam = ExamFactory.generateTestExam(course1);
        exam.setNumberOfCorrectionRoundsInExam(1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_realExam_CorrectionRoundViolation() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setNumberOfCorrectionRoundsInExam(0);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);

        exam.setNumberOfCorrectionRoundsInExam(3);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateTestExam_asInstructor_withExamModeChanged() throws Exception {
        // The Exam-Mode should not be changeable with a PUT / update operation, a CONFLICT should be returned instead
        // Case 1: test exam should be updated to real exam
        Exam examA = ExamFactory.generateTestExam(course1);
        Exam createdExamA = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams", examA, Exam.class, HttpStatus.CREATED);
        createdExamA.setNumberOfCorrectionRoundsInExam(1);
        createdExamA.setTestExam(false);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", createdExamA, Exam.class, HttpStatus.CONFLICT);

        // Case 2: real exam should be updated to test exam
        Exam examB = ExamFactory.generateTestExam(course1);
        examB.setNumberOfCorrectionRoundsInExam(1);
        examB.setTestExam(false);
        examB.setChannelName("examB");
        Exam createdExamB = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams", examB, Exam.class, HttpStatus.CREATED);
        createdExamB.setTestExam(true);
        createdExamB.setNumberOfCorrectionRoundsInExam(0);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", createdExamB, Exam.class, HttpStatus.CONFLICT);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_asInstructor() throws Exception {
        // Create instead of update if no id was set
        Exam exam = ExamFactory.generateExam(course1, "exam1");
        exam.setTitle("Over 9000!");
        long examCountBefore = examRepository.count();
        Exam createdExam = request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam, Exam.class, HttpStatus.CREATED);

        assertThat(exam.getEndDate()).isEqualTo(createdExam.getEndDate());
        assertThat(exam.getStartDate()).isEqualTo(createdExam.getStartDate());
        assertThat(exam.getVisibleDate()).isEqualTo(createdExam.getVisibleDate());
        // Note: ZonedDateTime has problems with comparison due to time zone differences for values saved in the database and values not saved in the database
        assertThat(exam).usingRecursiveComparison().ignoringFields("id", "course", "endDate", "startDate", "visibleDate").isEqualTo(createdExam);
        assertThat(examCountBefore + 1).isEqualTo(examRepository.count());
        // No course is set -> bad request
        exam = ExamFactory.generateExam(course1);
        exam.setId(1L);
        exam.setCourse(null);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        // Course id in the updated exam and in the REST resource url do not match -> bad request
        exam = ExamFactory.generateExam(course1);
        exam.setId(1L);
        request.put("/api/courses/" + course2.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        // Dates in the updated exam are not valid -> bad request
        List<Exam> examsWithInvalidDate = createExamsWithInvalidDates(course1);
        for (var examWithInvDate : examsWithInvalidDate) {
            examWithInvDate.setId(1L);
            request.put("/api/courses/" + course1.getId() + "/exams", examWithInvDate, HttpStatus.BAD_REQUEST);
        }
        // Update the exam -> ok
        exam1.setTitle("Best exam ever");
        var returnedExam = request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam1, Exam.class, HttpStatus.OK);
        assertThat(returnedExam).isEqualTo(exam1);
        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_reschedule_visibleAndStartDateChanged() throws Exception {
        // Add a programming exercise to the exam and change the dates in order to invoke a rescheduling
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setVisibleDate(examWithProgrammingEx.getVisibleDate().plusSeconds(1));
        examWithProgrammingEx.setStartDate(examWithProgrammingEx.getStartDate().plusSeconds(1));
        examWithProgrammingEx.setWorkingTime(examWithProgrammingEx.getWorkingTime() - 1);
        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_reschedule_visibleDateChanged() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setVisibleDate(examWithProgrammingEx.getVisibleDate().plusSeconds(1));
        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_reschedule_startDateChanged() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setStartDate(examWithProgrammingEx.getStartDate().plusSeconds(1));
        examWithProgrammingEx.setWorkingTime(examWithProgrammingEx.getWorkingTime() - 1);
        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleModeling_endDateChanged() throws Exception {
        var modelingExercise = modelingExerciseUtilService.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();
        examWithModelingEx.setEndDate(examWithModelingEx.getEndDate().plusSeconds(2));
        examWithModelingEx.setWorkingTime(examWithModelingEx.getWorkingTime() + 2);
        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendModelingExerciseSchedule(modelingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleModeling_workingTimeChanged() throws Exception {
        var modelingExercise = modelingExerciseUtilService.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();
        examWithModelingEx.setVisibleDate(now().plusHours(1));
        examWithModelingEx.setStartDate(now().plusHours(2));
        examWithModelingEx.setEndDate(now().plusHours(3));
        examWithModelingEx.setWorkingTime(3600);
        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);

        StudentExam studentExam = examUtilService.addStudentExam(examWithModelingEx);
        request.patch("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams/" + examWithModelingEx.getId() + "/student-exams/" + studentExam.getId() + "/working-time",
                3, HttpStatus.OK);
        verify(instanceMessageSendService, times(2)).sendModelingExerciseSchedule(modelingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_exampleSolutionPublicationDateChanged() throws Exception {
        var modelingExercise = modelingExerciseUtilService.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();

        assertThat(modelingExercise.isExampleSolutionPublished()).isFalse();
        examWithModelingEx.setVisibleDate(now().minusHours(5));
        examWithModelingEx.setStartDate(examWithModelingEx.getVisibleDate().plusMinutes(1));
        examWithModelingEx.setEndDate(examWithModelingEx.getStartDate().plusMinutes(1));
        examWithModelingEx.setPublishResultsDate(examWithModelingEx.getEndDate().plusMinutes(1));
        examWithModelingEx.setExampleSolutionPublicationDate(examWithModelingEx.getPublishResultsDate().plusMinutes(1));
        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);

        Exam fetchedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examWithModelingEx.getId());
        Exercise exercise = fetchedExam.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow();
        assertThat(exercise.isExampleSolutionPublished()).isTrue();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_asInstructor() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExamUsersElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExerciseGroupsElseThrow(Long.MAX_VALUE));

        assertThat(examRepository.findAllExercisesByExamId(Long.MAX_VALUE)).isEmpty();

        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK, Exam.class);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_asInstructor_WithTestRunQuizExerciseSubmissions() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);
        examRepository.save(exam);

        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setTestRun(true);

        QuizExercise quizExercise = quizExerciseUtilService.createQuizForExam(exerciseGroup);
        quizExercise.setStudentParticipations(Set.of(studentParticipation));
        studentParticipation.setExercise(quizExercise);

        exerciseRepo.save(quizExercise);
        studentParticipationRepository.save(studentParticipation);

        Exam returnedExam = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "?withExerciseGroups=true", HttpStatus.OK, Exam.class);

        assertThat(returnedExam.getExerciseGroups()).anyMatch(groups -> groups.getExercises().stream().anyMatch(Exercise::getTestRunParticipationsExist));
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamsForCourse_asInstructor() throws Exception {

        var exams = request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.OK, Exam.class);
        verify(examAccessService, times(1)).checkCourseAccessForTeachingAssistantElseThrow(course1.getId());

        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            assertThat(exam.getCourse().getId()).as("for exam with index %d and id %d", i, exam.getId()).isEqualTo(course1.getId());
            assertThat(exam.getNumberOfExamUsers()).as("for exam with index %d and id %d", i, exam.getId()).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamsForUser_asInstructor() throws Exception {
        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        var exams = request.getList("/api/courses/" + course1.getId() + "/exams-for-user", HttpStatus.OK, Exam.class);
        assertThat(course1.getInstructorGroupName()).isIn(currentUser.getGroups());

        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            assertThat(exam.getCourse().getInstructorGroupName()).as("should be instructor for exam with index %d and id %d", i, exam.getId()).isIn(currentUser.getGroups());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetCurrentAndUpcomingExams() throws Exception {
        var exams = request.getList("/api/admin/courses/upcoming-exams", HttpStatus.OK, Exam.class);
        ZonedDateTime currentDay = now().truncatedTo(ChronoUnit.DAYS);
        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            assertThat(exam.getEndDate()).as("for exam with index %d and id %d", i, exam.getId()).isAfterOrEqualTo(currentDay);
            assertThat(exam.getCourse().isTestCourse()).as("for exam with index %d and id %d", i, exam.getId()).isFalse();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "USER")
    void testGetCurrentAndUpcomingExamsForbiddenForUser() throws Exception {
        request.getList("/api/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentAndUpcomingExamsForbiddenForInstructor() throws Exception {
        request.getList("/api/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentAndUpcomingExamsForbiddenForTutor() throws Exception {
        request.getList("/api/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteEmptyExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exam2.getExerciseGroups().get(0));
        exerciseRepo.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/654555", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetEmptyExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exam2.getExerciseGroups().get(0));
        exerciseRepo.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testResetExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/654555/reset", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExamWithQuizExercise_asInstructor() throws Exception {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExerciseForExam(exam2.getExerciseGroups().get(0));
        quizExercise = exerciseRepo.save(quizExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        quizExercise = (QuizExercise) exerciseRepo.findByIdElseThrow(quizExercise.getId());
        assertThat(quizExercise.getReleaseDate()).isNull();
        assertThat(quizExercise.getDueDate()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudent() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        // Create an exam with registered students
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 4);
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

        // Remove student1 from the exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.OK);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student1 was removed from the exam
        var examUser = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId());
        assertThat(examUser).isEmpty();
        assertThat(storedExam.getExamUsers()).hasSize(3);

        // Create individual student exams
        List<StudentExam> generatedStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(generatedStudentExams).hasSize(storedExam.getExamUsers().size());

        // Start the exam to create participations
        prepareExerciseStart(exam);

        verify(gitService, times(getNumberOfProgrammingExercises(exam))).combineAllCommitsOfRepositoryIntoOne(any());
        // Get the student exam of student2
        Optional<StudentExam> optionalStudent1Exam = generatedStudentExams.stream().filter(studentExam -> studentExam.getUser().equals(student2)).findFirst();
        assertThat(optionalStudent1Exam.get()).isNotNull();
        var studentExam2 = optionalStudent1Exam.get();

        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // Remove student2 from the exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student2", HttpStatus.OK);

        // Get the exam with all registered users
        params = new LinkedMultiValueMap<>();
        params.add("withStudents", "true");
        storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student2 was removed from the exam
        var examUser2 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student2.getId());
        assertThat(examUser2).isEmpty();
        assertThat(storedExam.getExamUsers()).hasSize(2);

        // Ensure that the student exam of student2 was deleted
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams).hasSameSizeAs(storedExam.getExamUsers()).doesNotContain(studentExam2);

        // Ensure that the participations were not deleted
        List<StudentParticipation> participationsStudent2 = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student2.getId(), studentExam2.getExercises());
        assertThat(participationsStudent2).hasSize(studentExam2.getExercises().size());

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudentForTestExam_badRequest() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        // Create an exam with registered students
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setTestExam(true);
        examRepository.save(exam);

        // Remove student1 from the exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamWithOptions() throws Exception {
        User user = userRepo.findOneByLogin(TEST_PREFIX + "student1").get();
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(user, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(course.getExams().iterator().next().getId()).get();
        // Get the exam with all registered users
        // 1. without options
        var exam1 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class);
        assertThat(exam1.getExamUsers()).isEmpty();
        assertThat(exam1.getExerciseGroups()).isEmpty();

        // 2. with students, without exercise groups
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        var exam2 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam2.getExamUsers()).hasSize(1);
        assertThat(exam2.getExerciseGroups()).isEmpty();

        // 3. with students, with exercise groups
        params.add("withExerciseGroups", "true");
        var exam3 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam3.getExamUsers()).hasSize(1);
        assertThat(exam3.getExerciseGroups()).hasSize(exam.getExerciseGroups().size());
        assertThat(exam3.getExerciseGroups().get(0).getExercises()).hasSize(exam.getExerciseGroups().get(0).getExercises().size());
        assertThat(exam3.getExerciseGroups().get(1).getExercises()).hasSize(exam.getExerciseGroups().get(1).getExercises().size());
        assertThat(exam3.getNumberOfExamUsers()).isNotNull().isEqualTo(1);

        // 4. without students, with exercise groups
        params = new LinkedMultiValueMap<>();
        params.add("withExerciseGroups", "true");
        var exam4 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam4.getExamUsers()).isEmpty();
        assertThat(exam4.getExerciseGroups()).hasSize(exam.getExerciseGroups().size());
        assertThat(exam4.getExerciseGroups().get(0).getExercises()).hasSize(exam.getExerciseGroups().get(0).getExercises().size());
        assertThat(exam4.getExerciseGroups().get(1).getExercises()).hasSize(exam.getExerciseGroups().get(1).getExercises().size());
        exam4.getExerciseGroups().get(1).getExercises().forEach(exercise -> {
            assertThat(exercise.getNumberOfParticipations()).isNotNull();
            assertThat(exercise.getNumberOfParticipations()).isZero();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudentWithParticipationsAndSubmissions() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        // Create an exam with registered students
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 4);
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Create individual student exams
        List<StudentExam> generatedStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);

        // Get the student exam of student1
        Optional<StudentExam> optionalStudent1Exam = generatedStudentExams.stream().filter(studentExam -> studentExam.getUser().equals(student1)).findFirst();
        assertThat(optionalStudent1Exam.get()).isNotNull();
        var studentExam1 = optionalStudent1Exam.get();

        // Start the exam to create participations
        prepareExerciseStart(exam);
        verify(gitService, times(getNumberOfProgrammingExercises(exam))).combineAllCommitsOfRepositoryIntoOne(any());
        List<StudentParticipation> participationsStudent1 = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student1.getId(), studentExam1.getExercises());
        assertThat(participationsStudent1).hasSize(studentExam1.getExercises().size());

        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Remove student1 from the exam and his participations
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withParticipationsAndSubmission", "true");
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.OK, params);

        // Get the exam with all registered users
        params = new LinkedMultiValueMap<>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student1 was removed from the exam
        var examUser1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId());
        assertThat(examUser1).isEmpty();
        assertThat(storedExam.getExamUsers()).hasSize(3);

        // Ensure that the student exam of student1 was deleted
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams).hasSameSizeAs(storedExam.getExamUsers()).doesNotContain(studentExam1);

        // Ensure that the participations of student1 were deleted
        participationsStudent1 = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student1.getId(),
                studentExam1.getExercises());
        assertThat(participationsStudent1).isEmpty();

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForTestRunDashboard_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForTestRunDashboard_badRequest() throws Exception {
        request.get("/api/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.BAD_REQUEST, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithOneTestRuns() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithMultipleTestRuns() throws Exception {
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, true);
        mockDeleteProgrammingExercise(exerciseUtilService.getFirstExerciseWithType(exam, ProgrammingExercise.class), Set.of(instructor));

        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        assertThat(studentExamRepository.findAllTestRunsByExamId(exam.getId())).hasSize(3);
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteCourseWithMultipleTestRuns() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        assertThat(studentExamRepository.findAllTestRunsByExamId(exam.getId())).hasSize(3);
        request.delete("/api/courses/" + exam.getCourse().getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForTestRunDashboard_ok() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        exam = request.get("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.OK, Exam.class);
        assertThat(exam.getExerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.getExercises().stream()).toList()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudentThatDoesNotExist() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForStart() throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(1).minusMinutes(5));
        StudentExam response = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/start", HttpStatus.OK, StudentExam.class);
        assertThat(response.getExam()).isEqualTo(exam);
        verify(examAccessService, times(1)).getExamInCourseElseThrow(course1.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddAllRegisteredUsersToExam() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course);
        Exam exam = examUtilService.addExam(course);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
        exam = examRepository.save(exam);
        course.addExam(exam);
        course = courseRepo.save(course);

        Channel channel = new Channel();
        channel.setName("testchannel" + UUID.randomUUID().toString().substring(0, 8));
        channel.setIsArchived(false);
        channel.setIsPublic(false);
        channel.setIsAnnouncementChannel(false);
        channel.setExam(exam);
        channel = channelRepository.save(channel);

        int numberOfStudentsInCourse = userRepo.findAllInGroup(course.getStudentGroupName()).size();

        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setGroups(Collections.singleton("instructor"));
        userRepo.save(instructor);

        var student99 = userUtilService.createAndSaveUser(TEST_PREFIX + "student99"); // not registered for the course
        student99.setRegistrationNumber("1234");
        userRepo.save(student99);
        student99 = userRepo.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student99").get();
        student99.setGroups(Collections.singleton("tumuser"));
        userRepo.save(student99);
        assertThat(student99.getGroups()).contains(course.getStudentGroupName());
        var examUser99 = examUserRepository.findByExamIdAndUserId(exam.getId(), student99.getId());
        assertThat(examUser99).isEmpty();

        request.postWithoutLocation("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/register-course-students", null, HttpStatus.OK, null);

        exam = examRepository.findWithExamUsersById(exam.getId()).get();
        examUser99 = examUserRepository.findByExamIdAndUserId(exam.getId(), student99.getId());

        // the course students + our custom student99
        assertThat(exam.getExamUsers()).hasSize(numberOfStudentsInCourse + 1);
        assertThat(exam.getExamUsers()).contains(examUser99.get());
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course.getId(), exam.getId());

        Channel channelFromDB = channelRepository.findChannelByExamId(exam.getId());
        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getExam()).isEqualTo(exam);
        assertThat(channelFromDB.getName()).isEqualTo(channel.getName());

        // Check that the conversation participants are added correctly to the exercise channel
        await().until(() -> {
            SecurityUtils.setAuthorizationObject();
            Set<ConversationParticipant> conversationParticipants = conversationParticipantRepository.findConversationParticipantByConversationId(channelFromDB.getId());
            return conversationParticipants.size() == 5; // 4 students should be added (see @BeforeEach) + 1 new student = 5
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterCourseStudents_testExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/register-course-students", null, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateOrderOfExerciseGroups() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        exerciseGroup1.setTitle("first");
        ExerciseGroup exerciseGroup2 = new ExerciseGroup();
        exerciseGroup2.setTitle("second");
        ExerciseGroup exerciseGroup3 = new ExerciseGroup();
        exerciseGroup3.setTitle("third");

        Exam exam = examUtilService.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam.addExerciseGroup(exerciseGroup2);
        exam.addExerciseGroup(exerciseGroup3);
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        exerciseGroup2 = examWithExerciseGroups.getExerciseGroups().get(1);
        exerciseGroup3 = examWithExerciseGroups.getExerciseGroups().get(2);

        TextExercise exercise1_1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        TextExercise exercise1_2 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        TextExercise exercise2_1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup2);
        TextExercise exercise3_1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_2 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_3 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup3);
        exercise1_1 = textExerciseRepository.save(exercise1_1);
        exercise1_2 = textExerciseRepository.save(exercise1_2);
        exercise2_1 = textExerciseRepository.save(exercise2_1);
        exercise3_1 = textExerciseRepository.save(exercise3_1);
        exercise3_2 = textExerciseRepository.save(exercise3_2);
        exercise3_3 = textExerciseRepository.save(exercise3_3);

        examWithExerciseGroups = examRepository.findWithExerciseGroupsById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        exerciseGroup2 = examWithExerciseGroups.getExerciseGroups().get(1);
        exerciseGroup3 = examWithExerciseGroups.getExerciseGroups().get(2);
        List<ExerciseGroup> orderedExerciseGroups = new ArrayList<>();
        orderedExerciseGroups.add(exerciseGroup2);
        orderedExerciseGroups.add(exerciseGroup3);
        orderedExerciseGroups.add(exerciseGroup1);

        // Should save new order
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam.getId());
        List<ExerciseGroup> savedExerciseGroups = examRepository.findWithExerciseGroupsById(exam.getId()).get().getExerciseGroups();
        assertThat(savedExerciseGroups.get(0).getTitle()).isEqualTo("second");
        assertThat(savedExerciseGroups.get(1).getTitle()).isEqualTo("third");
        assertThat(savedExerciseGroups.get(2).getTitle()).isEqualTo("first");

        // Exercises should be preserved
        Exam savedExam = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        ExerciseGroup savedExerciseGroup1 = savedExam.getExerciseGroups().get(2);
        ExerciseGroup savedExerciseGroup2 = savedExam.getExerciseGroups().get(0);
        ExerciseGroup savedExerciseGroup3 = savedExam.getExerciseGroups().get(1);
        assertThat(savedExerciseGroup1.getExercises()).hasSize(2);
        assertThat(savedExerciseGroup2.getExercises()).hasSize(1);
        assertThat(savedExerciseGroup3.getExercises()).hasSize(3);
        assertThat(savedExerciseGroup1.getExercises()).contains(exercise1_1);
        assertThat(savedExerciseGroup1.getExercises()).contains(exercise1_2);
        assertThat(savedExerciseGroup2.getExercises()).contains(exercise2_1);
        assertThat(savedExerciseGroup3.getExercises()).contains(exercise3_1);
        assertThat(savedExerciseGroup3.getExercises()).contains(exercise3_2);
        assertThat(savedExerciseGroup3.getExercises()).contains(exercise3_3);

        // Should fail with too many exercise groups
        orderedExerciseGroups.add(exerciseGroup1);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.BAD_REQUEST);

        // Should fail with too few exercise groups
        orderedExerciseGroups.remove(3);
        orderedExerciseGroups.remove(2);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.BAD_REQUEST);

        // Should fail with different exercise group
        orderedExerciseGroups = new ArrayList<>();
        orderedExerciseGroups.add(exerciseGroup2);
        orderedExerciseGroups.add(exerciseGroup3);
        orderedExerciseGroups.add(ExamFactory.generateExerciseGroup(true, exam));
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void lockAllRepositories_noInstructor() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = examUtilService.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/lock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void lockAllRepositories() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = examUtilService.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);

        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        exerciseGroup1.addExercise(programmingExercise);

        ProgrammingExercise programmingExercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
        exerciseGroup1.addExercise(programmingExercise2);

        exerciseGroupRepository.save(exerciseGroup1);

        Integer numOfLockedExercises = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/lock-all-repositories",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numOfLockedExercises).isEqualTo(2);

        verify(programmingExerciseScheduleService, times(1)).lockAllStudentRepositoriesAndParticipations(programmingExercise);
        verify(programmingExerciseScheduleService, times(1)).lockAllStudentRepositoriesAndParticipations(programmingExercise2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void unlockAllRepositories_preAuthNoInstructor() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = examUtilService.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/unlock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void unlockAllRepositories() throws Exception {
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        assertThat(studentExamRepository.findStudentExam(new ProgrammingExercise(), null)).isEmpty();

        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = examUtilService.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        exam = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = exam.getExerciseGroups().get(0);

        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        exerciseGroup1.addExercise(programmingExercise);

        ProgrammingExercise programmingExercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
        exerciseGroup1.addExercise(programmingExercise2);

        exerciseGroupRepository.save(exerciseGroup1);

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var studentExam1 = examUtilService.addStudentExamWithUser(exam, student1, 10);
        studentExam1.setExercises(List.of(programmingExercise, programmingExercise2));
        var studentExam2 = examUtilService.addStudentExamWithUser(exam, student2, 0);
        studentExam2.setExercises(List.of(programmingExercise, programmingExercise2));
        studentExamRepository.saveAll(Set.of(studentExam1, studentExam2));

        var participationExSt1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var participationExSt2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        var participationEx2St1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise2, TEST_PREFIX + "student1");
        var participationEx2St2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise2, TEST_PREFIX + "student2");

        assertThat(studentExamRepository.findStudentExam(programmingExercise, participationExSt1)).contains(studentExam1);
        assertThat(studentExamRepository.findStudentExam(programmingExercise, participationExSt2)).contains(studentExam2);
        assertThat(studentExamRepository.findStudentExam(programmingExercise2, participationEx2St1)).contains(studentExam1);
        assertThat(studentExamRepository.findStudentExam(programmingExercise2, participationEx2St2)).contains(studentExam2);

        mockConfigureRepository(programmingExercise, TEST_PREFIX + "student1", Set.of(student1), true);
        mockConfigureRepository(programmingExercise, TEST_PREFIX + "student2", Set.of(student2), true);
        mockConfigureRepository(programmingExercise2, TEST_PREFIX + "student1", Set.of(student1), true);
        mockConfigureRepository(programmingExercise2, TEST_PREFIX + "student2", Set.of(student2), true);

        Integer numOfUnlockedExercises = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/unlock-all-repositories",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numOfUnlockedExercises).isEqualTo(2);

        verify(programmingExerciseScheduleService, times(1)).unlockAllStudentRepositoriesAndParticipations(programmingExercise);
        verify(programmingExerciseScheduleService, times(1)).unlockAllStudentRepositoriesAndParticipations(programmingExercise2);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(ints = { 0, 1, 2 })
    void testGetExamForExamAssessmentDashboard(int numberOfCorrectionRounds) throws Exception {
        User user = userRepo.findOneByLogin(TEST_PREFIX + "student1").get();
        // we need an exam from the past, otherwise the tutor won't have access
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(user, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        Exam exam = course.getExams().iterator().next();

        // Ensure the API endpoint works for all number of correctionRounds
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRounds);
        examRepository.save(exam);

        Exam receivedExam = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.OK, Exam.class);

        // Test that the received exam has two text exercises
        assertThat(receivedExam.getExerciseGroups().get(0).getExercises()).as("Two exercises are returned").hasSize(2);
        // Test that the received exam has zero quiz exercises, because quiz exercises do not need to be corrected manually
        assertThat(receivedExam.getExerciseGroups().get(1).getExercises()).as("Zero exercises are returned").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForExamAssessmentDashboard_beforeDueDate() throws Exception {
        User user = userRepo.findOneByLogin(TEST_PREFIX + "student1").get();
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(user);
        Exam exam = course.getExams().iterator().next();
        exam.setEndDate(now().plusWeeks(1));
        examRepository.save(exam);

        request.get("/api/courses/" + course.getId() + "/exams/" + course.getExams().iterator().next().getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN,
                Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetExamForExamAssessmentDashboard_asStudent_forbidden() throws Exception {
        User user = userRepo.findOneByLogin(TEST_PREFIX + "student1").get();
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(user);
        request.get("/api/courses/" + course.getId() + "/exams/" + course.getExams().iterator().next().getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN,
                Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForExamAssessmentDashboard_courseIdDoesNotMatch_badRequest() throws Exception {
        request.get("/api/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-assessment-dashboard", HttpStatus.BAD_REQUEST, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForExamAssessmentDashboard_notFound() throws Exception {
        request.get("/api/courses/-1/exams/-1/exam-for-assessment-dashboard", HttpStatus.NOT_FOUND, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExamForExamDashboard_NotTAOfCourse_forbidden() throws Exception {
        User user = userRepo.findOneByLogin(TEST_PREFIX + "student1").get();
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(user);
        Exam exam = course.getExams().iterator().next();
        exam.setEndDate(now().plusWeeks(1));
        examRepository.save(exam);

        request.get("/api/courses/" + course.getId() + "/exams/" + course.getExams().iterator().next().getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN,
                Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExamScore_tutorNotInCourse_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamScore_tutor_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    private int getNumberOfProgrammingExercises(Exam exam) {
        exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        int count = 0;
        for (var exerciseGroup : exam.getExerciseGroups()) {
            for (var exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    count++;
                }
            }
        }
        return count;
    }

    private void configureCourseAsBonusWithIndividualAndTeamResults(Course course, GradingScale bonusToGradingScale) {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        Long individualTextExerciseId = textExercise.getId();
        textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);

        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        User student1 = userRepo.findOneByLogin(TEST_PREFIX + "student1").get();
        User tutor1 = userRepo.findOneByLogin(TEST_PREFIX + "tutor1").get();
        Long teamTextExerciseId = teamExercise.getId();
        Long team1Id = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();
        User student2 = userRepo.findOneByLogin(TEST_PREFIX + "student2").get();
        User student3 = userRepo.findOneByLogin(TEST_PREFIX + "student3").get();
        User tutor2 = userRepo.findOneByLogin(TEST_PREFIX + "tutor2").get();
        Long team2Id = teamUtilService.createTeam(Set.of(student2, student3), tutor2, teamExercise, TEST_PREFIX + "team2").getId();

        participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student1, 10.0, 10.0, 50, true);

        Team team1 = teamRepository.findById(team1Id).get();
        var result = participationUtilService.createParticipationSubmissionAndResult(teamTextExerciseId, team1, 10.0, 10.0, 40, true);
        // Creating a second results for team1 to test handling multiple results.
        participationUtilService.createSubmissionAndResult((StudentParticipation) result.getParticipation(), 50, true);

        var student2Result = participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student2, 10.0, 10.0, 50, true);

        var student3Result = participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student3, 10.0, 10.0, 30, true);

        Team team2 = teamRepository.findById(team2Id).get();
        participationUtilService.createParticipationSubmissionAndResult(teamTextExerciseId, team2, 10.0, 10.0, 80, true);

        // Adding plagiarism cases
        var bonusPlagiarismCase = new PlagiarismCase();
        bonusPlagiarismCase.setStudent(student3);
        bonusPlagiarismCase.setExercise(student3Result.getParticipation().getExercise());
        bonusPlagiarismCase.setVerdict(PlagiarismVerdict.PLAGIARISM);
        plagiarismCaseRepository.save(bonusPlagiarismCase);

        var bonusPlagiarismCase2 = new PlagiarismCase();
        bonusPlagiarismCase2.setStudent(student2);
        bonusPlagiarismCase2.setExercise(student2Result.getParticipation().getExercise());
        bonusPlagiarismCase2.setVerdict(PlagiarismVerdict.POINT_DEDUCTION);
        bonusPlagiarismCase2.setVerdictPointDeduction(50);
        plagiarismCaseRepository.save(bonusPlagiarismCase2);

        BonusStrategy bonusStrategy = BonusStrategy.GRADES_CONTINUOUS;
        bonusToGradingScale.setBonusStrategy(bonusStrategy);
        gradingScaleRepository.save(bonusToGradingScale);

        GradingScale sourceGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 40, 50 }, Optional.of(new String[] { "0", "0.3", "0.6" }),
                true, 1);
        sourceGradingScale.setGradeType(GradeType.BONUS);
        sourceGradingScale.setCourse(course);
        gradingScaleRepository.save(sourceGradingScale);

        var bonus = BonusFactory.generateBonus(bonusStrategy, -1.0, sourceGradingScale.getId(), bonusToGradingScale.getId());
        bonusRepository.save(bonus);

        course.setMaxPoints(100);
        course.setPresentationScore(null);
        courseRepo.save(course);

    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "false, false", "true, false", "false, true", "true, true" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamScore(boolean withCourseBonus, boolean withSecondCorrectionAndStarted) throws Exception {
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);

        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        var visibleDate = now().minusMinutes(5);
        var startDate = now().plusMinutes(5);
        var endDate = now().plusMinutes(20);

        // register users. Instructors are ignored from scores as they are exclusive for test run exercises
        Set<User> registeredStudents = getRegisteredStudentsForExam();

        var studentExams = programmingExerciseTestService.prepareStudentExamsForConduction(TEST_PREFIX, visibleDate, startDate, endDate, registeredStudents, studentRepos);
        Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(studentExams.get(0).getExam().getId());
        Course course = exam.getCourse();

        Integer noGeneratedParticipations = registeredStudents.size() * exam.getExerciseGroups().size();

        verify(gitService, times(getNumberOfProgrammingExercises(exam))).combineAllCommitsOfRepositoryIntoOne(any());
        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // instructor exam checklist checks
        ExamChecklistDTO examChecklistDTO = examService.getStatsForChecklist(exam, true);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isEqualTo(exam.getExamUsers().size());
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isTrue();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isZero();

        // check that an adapted version is computed for tutors
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        examChecklistDTO = examService.getStatsForChecklist(exam, false);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isNull();
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isFalse();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isZero();

        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // set start and submitted date as results are created below
        studentExams.forEach(studentExam -> {
            studentExam.setStarted(true);
            studentExam.setStartedDate(now().minusMinutes(2));
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(now().minusMinutes(1));
        });
        studentExamRepository.saveAll(studentExams);

        // Fetch the created participations and assign them to the exercises
        int participationCounter = 0;
        List<Exercise> exercisesInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).toList();
        for (var exercise : exercisesInExam) {
            List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndTestRunWithEagerLegalSubmissionsResult(exercise.getId(), false);
            exercise.setStudentParticipations(new HashSet<>(participations));
            participationCounter += exercise.getStudentParticipations().size();
        }
        assertThat(noGeneratedParticipations).isEqualTo(participationCounter);

        if (withSecondCorrectionAndStarted) {
            exercisesInExam.forEach(exercise -> exercise.setSecondCorrectionEnabled(true));
            exerciseRepo.saveAll(exercisesInExam);
        }

        // Scores used for all exercise results
        double correctionResultScore = 60D;
        double resultScore = 75D;

        // Assign results to participations and submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                // Programming exercises don't have a submission yet
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(participation.getSubmissions()).isEmpty();
                    submission = new ProgrammingSubmission();
                    submission.setParticipation(participation);
                    submission = submissionRepository.save(submission);
                }
                else {
                    // There should only be one submission for text, quiz, modeling and file upload
                    assertThat(participation.getSubmissions()).hasSize(1);
                    submission = participation.getSubmissions().iterator().next();
                }

                // make sure to create submitted answers
                if (exercise instanceof QuizExercise quizExercise) {
                    var quizQuestions = quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId()).getQuizQuestions();
                    for (var quizQuestion : quizQuestions) {
                        var submittedAnswer = quizExerciseUtilService.generateSubmittedAnswerFor(quizQuestion, true);
                        var quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(submission.getId());
                        quizSubmission.addSubmittedAnswers(submittedAnswer);
                        quizSubmissionService.saveSubmissionForExamMode(quizExercise, quizSubmission, participation.getStudent().get());
                    }
                }

                // Create results
                if (withSecondCorrectionAndStarted) {
                    var firstResult = new Result().score(correctionResultScore).rated(true).completionDate(now().minusMinutes(5));
                    firstResult.setParticipation(participation);
                    firstResult.setAssessor(instructor);
                    firstResult = resultRepository.save(firstResult);
                    firstResult.setSubmission(submission);
                    submission.addResult(firstResult);
                }

                var finalResult = new Result().score(resultScore).rated(true).completionDate(now().minusMinutes(5));
                finalResult.setParticipation(participation);
                finalResult.setAssessor(instructor);
                finalResult = resultRepository.save(finalResult);
                finalResult.setSubmission(submission);
                submission.addResult(finalResult);

                submission.submitted(true);
                submission.setSubmissionDate(now().minusMinutes(6));
                submissionRepository.save(submission);
            }
        }
        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        final var exerciseWithNoUsers = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseRepo.save(exerciseWithNoUsers);

        GradingScale gradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 25, 15, 50 },
                Optional.of(new String[] { "5.0", "3.0", "1.0", "1.0+" }), true, 1);
        gradingScale.setExam(exam);
        gradingScaleRepository.save(gradingScale);

        await().until(() -> participantScoreScheduleService.isIdle());

        if (withCourseBonus) {
            configureCourseAsBonusWithIndividualAndTeamResults(course, gradingScale);
        }

        await().timeout(Duration.ofMinutes(1)).until(() -> {
            for (Exercise exercise : exercisesInExam) {
                if (participantScoreRepository.findAllByExercise(exercise).size() != exercise.getStudentParticipations().size()) {
                    return false;
                }
            }
            return true;
        });

        var examScores = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/scores", HttpStatus.OK, ExamScoresDTO.class);

        // Compare generated results to data in ExamScoresDTO
        // Compare top-level DTO properties
        assertThat(examScores.maxPoints()).isEqualTo(exam.getExamMaxPoints());

        assertThat(examScores.hasSecondCorrectionAndStarted()).isEqualTo(withSecondCorrectionAndStarted);

        // For calculation assume that all exercises within an exerciseGroups have the same max points
        double calculatedAverageScore = 0.0;
        for (var exerciseGroup : exam.getExerciseGroups()) {
            var exercise = exerciseGroup.getExercises().stream().findAny().get();
            if (exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                continue;
            }
            calculatedAverageScore += Math.round(exercise.getMaxPoints() * resultScore / 100.00 * 10) / 10.0;
        }

        assertThat(examScores.averagePointsAchieved()).isEqualTo(calculatedAverageScore);
        assertThat(examScores.title()).isEqualTo(exam.getTitle());
        assertThat(examScores.examId()).isEqualTo(exam.getId());

        // Ensure that all exerciseGroups of the exam are present in the DTO
        Set<Long> exerciseGroupIdsInDTO = examScores.exerciseGroups().stream().map(ExamScoresDTO.ExerciseGroup::id).collect(Collectors.toSet());
        Set<Long> exerciseGroupIdsInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getId).collect(Collectors.toSet());
        assertThat(exerciseGroupIdsInExam).isEqualTo(exerciseGroupIdsInDTO);

        // Compare exerciseGroups in DTO to exam exerciseGroups
        // Tolerated absolute difference for floating-point number comparisons
        double EPSILON = 0000.1;
        for (var exerciseGroupDTO : examScores.exerciseGroups()) {
            // Find the original exerciseGroup of the exam using the id in ExerciseGroupId
            ExerciseGroup originalExerciseGroup = exam.getExerciseGroups().stream().filter(exerciseGroup -> exerciseGroup.getId().equals(exerciseGroupDTO.id())).findFirst().get();

            // Assume that all exercises in a group have the same max score
            Double groupMaxScoreFromExam = originalExerciseGroup.getExercises().stream().findAny().get().getMaxPoints();
            assertThat(exerciseGroupDTO.maxPoints()).isEqualTo(originalExerciseGroup.getExercises().stream().findAny().get().getMaxPoints());
            assertThat(groupMaxScoreFromExam).isEqualTo(exerciseGroupDTO.maxPoints(), withPrecision(EPSILON));

            // EPSILON
            // Compare exercise information
            long noOfExerciseGroupParticipations = 0;
            for (var originalExercise : originalExerciseGroup.getExercises()) {
                // Find the corresponding ExerciseInfo object
                var exerciseDTO = exerciseGroupDTO.containedExercises().stream().filter(exerciseInfo -> exerciseInfo.exerciseId().equals(originalExercise.getId())).findFirst()
                        .get();
                // Check the exercise title
                assertThat(originalExercise.getTitle()).isEqualTo(exerciseDTO.title());
                // Check the max points of the exercise
                assertThat(originalExercise.getMaxPoints()).isEqualTo(exerciseDTO.maxPoints());
                // Check the number of exercise participants and update the group participant counter
                var noOfExerciseParticipations = originalExercise.getStudentParticipations().size();
                noOfExerciseGroupParticipations += noOfExerciseParticipations;
                assertThat(Long.valueOf(originalExercise.getStudentParticipations().size())).isEqualTo(exerciseDTO.numberOfParticipants());
            }
            assertThat(noOfExerciseGroupParticipations).isEqualTo(exerciseGroupDTO.numberOfParticipants());
        }

        // Ensure that all registered students have a StudentResult
        Set<Long> studentIdsWithStudentResults = examScores.studentResults().stream().map(ExamScoresDTO.StudentResult::userId).collect(Collectors.toSet());
        Set<User> registeredUsers = exam.getRegisteredUsers();
        Set<Long> registeredUsersIds = registeredUsers.stream().map(User::getId).collect(Collectors.toSet());
        assertThat(studentIdsWithStudentResults).isEqualTo(registeredUsersIds);

        // Compare StudentResult with the generated results
        for (var studentResult : examScores.studentResults()) {
            // Find the original user using the id in StudentResult
            User originalUser = userRepo.findByIdElseThrow(studentResult.userId());
            StudentExam studentExamOfUser = studentExams.stream().filter(studentExam -> studentExam.getUser().equals(originalUser)).findFirst().get();

            assertThat(studentResult.name()).isEqualTo(originalUser.getName());
            assertThat(studentResult.email()).isEqualTo(originalUser.getEmail());
            assertThat(studentResult.login()).isEqualTo(originalUser.getLogin());
            assertThat(studentResult.registrationNumber()).isEqualTo(originalUser.getRegistrationNumber());

            // Calculate overall points achieved

            var calculatedOverallPoints = calculateOverallPoints(resultScore, studentExamOfUser);

            assertThat(studentResult.overallPointsAchieved()).isEqualTo(calculatedOverallPoints, withPrecision(EPSILON));

            double expectedPointsAchievedInFirstCorrection = withSecondCorrectionAndStarted ? calculateOverallPoints(correctionResultScore, studentExamOfUser) : 0.0;
            assertThat(studentResult.overallPointsAchievedInFirstCorrection()).isEqualTo(expectedPointsAchievedInFirstCorrection, withPrecision(EPSILON));

            // Calculate overall score achieved
            var calculatedOverallScore = calculatedOverallPoints / examScores.maxPoints() * 100;
            assertThat(studentResult.overallScoreAchieved()).isEqualTo(calculatedOverallScore, withPrecision(EPSILON));

            assertThat(studentResult.overallGrade()).isNotNull();
            assertThat(studentResult.hasPassed()).isNotNull();
            assertThat(studentResult.mostSeverePlagiarismVerdict()).isNull();
            if (withCourseBonus) {
                String studentLogin = studentResult.login();
                assertThat(studentResult.gradeWithBonus().bonusStrategy()).isEqualTo(BonusStrategy.GRADES_CONTINUOUS);
                switch (studentLogin) {
                    case TEST_PREFIX + "student1" -> {
                        assertThat(studentResult.gradeWithBonus().mostSeverePlagiarismVerdict()).isNull();
                        assertThat(studentResult.gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(10.0);
                        assertThat(studentResult.gradeWithBonus().bonusGrade()).isEqualTo("0.0");
                        assertThat(studentResult.gradeWithBonus().finalGrade()).isEqualTo("1.0");
                    }
                    case TEST_PREFIX + "student2" -> {
                        assertThat(studentResult.gradeWithBonus().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.POINT_DEDUCTION);
                        assertThat(studentResult.gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(10.5); // 10.5 = 8 + 5 * 50% plagiarism point deduction.
                        assertThat(studentResult.gradeWithBonus().finalGrade()).isEqualTo("1.0");
                    }
                    case TEST_PREFIX + "student3" -> {
                        assertThat(studentResult.gradeWithBonus().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.PLAGIARISM);
                        assertThat(studentResult.gradeWithBonus().studentPointsOfBonusSource()).isZero();
                        assertThat(studentResult.gradeWithBonus().bonusGrade()).isEqualTo(GradingScale.DEFAULT_PLAGIARISM_GRADE);
                        assertThat(studentResult.gradeWithBonus().finalGrade()).isEqualTo("1.0");
                    }
                    default -> {
                    }
                }
            }
            else {
                assertThat(studentResult.gradeWithBonus()).isNull();
            }

            // Ensure that the exercise ids of the student exam are the same as the exercise ids in the students exercise results
            Set<Long> exerciseIdsOfStudentResult = studentResult.exerciseGroupIdToExerciseResult().values().stream().map(ExamScoresDTO.ExerciseResult::exerciseId)
                    .collect(Collectors.toSet());
            Set<Long> exerciseIdsInStudentExam = studentExamOfUser.getExercises().stream().map(DomainObject::getId).collect(Collectors.toSet());
            assertThat(exerciseIdsOfStudentResult).isEqualTo(exerciseIdsInStudentExam);
            for (Map.Entry<Long, ExamScoresDTO.ExerciseResult> entry : studentResult.exerciseGroupIdToExerciseResult().entrySet()) {
                var exerciseResult = entry.getValue();

                // Find the original exercise using the id in ExerciseResult
                Exercise originalExercise = studentExamOfUser.getExercises().stream().filter(exercise -> exercise.getId().equals(exerciseResult.exerciseId())).findFirst().get();

                // Check that the key is associated with the exerciseGroup which actually contains the exercise in the exerciseResult
                assertThat(originalExercise.getExerciseGroup().getId()).isEqualTo(entry.getKey());

                assertThat(exerciseResult.title()).isEqualTo(originalExercise.getTitle());
                assertThat(exerciseResult.maxScore()).isEqualTo(originalExercise.getMaxPoints());
                assertThat(exerciseResult.achievedScore()).isEqualTo(resultScore);
                if (originalExercise instanceof QuizExercise) {
                    assertThat(exerciseResult.hasNonEmptySubmission()).isTrue();
                }
                else {
                    assertThat(exerciseResult.hasNonEmptySubmission()).isFalse();
                }
                // TODO: create a test where hasNonEmptySubmission() is false for a quiz
                assertThat(exerciseResult.achievedPoints()).isEqualTo(originalExercise.getMaxPoints() * resultScore / 100, withPrecision(EPSILON));
            }
        }

        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        var expectedTotalExamAssessmentsFinishedByCorrectionRound = new Long[] { noGeneratedParticipations.longValue(), noGeneratedParticipations.longValue() };
        if (!withSecondCorrectionAndStarted) {
            // The second correction has not started in this case.
            expectedTotalExamAssessmentsFinishedByCorrectionRound[1] = 0L;
        }

        // check if stats are set correctly for the instructor
        examChecklistDTO = examService.getStatsForChecklist(exam, true);
        assertThat(examChecklistDTO).isNotNull();
        var size = examScores.studentResults().size();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isEqualTo(size);
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isEqualTo(size);
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isEqualTo(size);
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isTrue();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isEqualTo(size * 6L);
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isZero();
        assertThat(examChecklistDTO.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound()).hasSize(2).containsExactly(expectedTotalExamAssessmentsFinishedByCorrectionRound);

        // change to a tutor
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        // check that a modified version is returned
        // check if stats are set correctly for the instructor
        examChecklistDTO = examService.getStatsForChecklist(exam, false);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isNull();
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isNull();
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isNull();
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isFalse();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isEqualTo(size * 6L);
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isNull();
        assertThat(examChecklistDTO.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound()).hasSize(2).containsExactly(expectedTotalExamAssessmentsFinishedByCorrectionRound);

        bambooRequestMockProvider.reset();

        final ProgrammingExercise programmingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(6).getExercises().iterator().next();

        var usersOfExam = exam.getRegisteredUsers();
        mockDeleteProgrammingExercise(programmingExercise, usersOfExam);

        await().until(() -> participantScoreScheduleService.isIdle());

        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // Make sure delete also works if so many objects have been created before
        await().until(() -> participantScoreScheduleService.isIdle());
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
        assertThat(examRepository.findById(exam.getId())).isEmpty();
    }

    private double calculateOverallPoints(Double correctionResultScore, StudentExam studentExamOfUser) {
        return studentExamOfUser.getExercises().stream().filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED))
                .map(Exercise::getMaxPoints).reduce(0.0, (total, maxScore) -> (Math.round((total + maxScore * correctionResultScore / 100) * 10) / 10.0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStatistics() throws Exception {
        ExamChecklistDTO actualStatistics = examService.getStatsForChecklist(exam1, true);
        ExamChecklistDTO returnedStatistics = request.get("/api/courses/" + exam1.getCourse().getId() + "/exams/" + exam1.getId() + "/statistics", HttpStatus.OK,
                ExamChecklistDTO.class);
        assertThat(returnedStatistics.isAllExamExercisesAllStudentsPrepared()).isEqualTo(actualStatistics.isAllExamExercisesAllStudentsPrepared());
        assertThat(returnedStatistics.getAllExamExercisesAllStudentsPrepared()).isEqualTo(actualStatistics.getAllExamExercisesAllStudentsPrepared());
        assertThat(returnedStatistics.getNumberOfAllComplaints()).isEqualTo(actualStatistics.getNumberOfAllComplaints());
        assertThat(returnedStatistics.getNumberOfAllComplaintsDone()).isEqualTo(actualStatistics.getNumberOfAllComplaintsDone());
        assertThat(returnedStatistics.getNumberOfExamsStarted()).isEqualTo(actualStatistics.getNumberOfExamsStarted());
        assertThat(returnedStatistics.getNumberOfExamsSubmitted()).isEqualTo(actualStatistics.getNumberOfExamsSubmitted());
        assertThat(returnedStatistics.getNumberOfTestRuns()).isEqualTo(actualStatistics.getNumberOfTestRuns());
        assertThat(returnedStatistics.getNumberOfGeneratedStudentExams()).isEqualTo(actualStatistics.getNumberOfGeneratedStudentExams());
        assertThat(returnedStatistics.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound())
                .isEqualTo(actualStatistics.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound());
        assertThat(returnedStatistics.getNumberOfTotalParticipationsForAssessment()).isEqualTo(actualStatistics.getNumberOfTotalParticipationsForAssessment());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLatestExamEndDate() throws Exception {
        // Setup exam and user
        User user = userRepo.findOneByLogin(TEST_PREFIX + "student1").get();

        // Set student exam without working time and save into database
        StudentExam studentExam = new StudentExam();
        studentExam.setUser(user);
        studentExam.setTestRun(false);
        studentExam = studentExamRepository.save(studentExam);

        // Add student exam to exam and save into database
        exam2.addStudentExam(studentExam);
        exam2 = examRepository.save(exam2);

        // Get the latest exam end date DTO from server -> This returns the endDate as no specific student working time is set
        ExamInformationDTO examInfo = request.get("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/latest-end-date", HttpStatus.OK,
                ExamInformationDTO.class);
        // Check that latest end date is equal to endDate (no specific student working time). Do not check for equality as we lose precision when saving to the database
        assertThat(examInfo.latestIndividualEndDate()).isCloseTo(exam2.getEndDate(), within(1, ChronoUnit.SECONDS));

        // Set student exam with working time and save
        studentExam.setWorkingTime(3600);
        studentExamRepository.save(studentExam);

        // Get the latest exam end date DTO from server -> This returns the startDate + workingTime
        ExamInformationDTO examInfo2 = request.get("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/latest-end-date", HttpStatus.OK,
                ExamInformationDTO.class);
        // Check that latest end date is equal to startDate + workingTime
        assertThat(examInfo2.latestIndividualEndDate()).isCloseTo(exam2.getStartDate().plusHours(1), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testCourseAndExamAccessForInstructors_notInstructorInCourse_forbidden() throws Exception {
        // Instructor10 is not instructor for the course
        // Update exam
        request.put("/api/courses/" + course1.getId() + "/exams", exam1, HttpStatus.FORBIDDEN);
        // Get exam
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN, Exam.class);
        // Add student to exam
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", null, HttpStatus.FORBIDDEN);
        // Generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
        // Generate missing exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-missing-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
        // Start exercises
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/start-exercises", null, HttpStatus.FORBIDDEN, null);
        // Unlock all repositories
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/unlock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Lock all repositories
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/lock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Add students to exam
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", Collections.singletonList(new StudentDTO()), HttpStatus.FORBIDDEN);
        // Delete student from exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.FORBIDDEN);
        // Update order of exerciseGroups
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups-order", new ArrayList<ExerciseGroup>(), HttpStatus.FORBIDDEN);
        // Get the latest individual end date
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/latest-end-date", HttpStatus.FORBIDDEN, ExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLatestIndividualEndDate_noStudentExams() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        final var exam = examRepository.save(exam1);
        final var latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(exam.getId());
        assertThat(latestIndividualExamEndDate.isEqual(exam.getEndDate())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllIndividualExamEndDates() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        final var exam = examRepository.save(exam1);

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        final var studentExam1 = new StudentExam();
        studentExam1.setExam(exam);
        studentExam1.setUser(student1);
        studentExam1.setWorkingTime(120);
        studentExam1.setTestRun(false);
        studentExamRepository.save(studentExam1);

        final var studentExam2 = new StudentExam();
        studentExam2.setExam(exam);
        studentExam2.setUser(student1);
        studentExam2.setWorkingTime(120);
        studentExam2.setTestRun(false);
        studentExamRepository.save(studentExam2);

        final var studentExam3 = new StudentExam();
        studentExam3.setExam(exam);
        studentExam3.setUser(student1);
        studentExam3.setWorkingTime(60);
        studentExam3.setTestRun(false);
        studentExamRepository.save(studentExam3);

        final var individualWorkingTimes = examDateService.getAllIndividualExamEndDates(exam.getId());
        assertThat(individualWorkingTimes).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsExamOver_GracePeriod() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        exam1.setGracePeriod(180);
        final var exam = examRepository.save(exam1);
        final var isOver = examDateService.isExamWithGracePeriodOver(exam.getId());
        assertThat(isOver).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsUserRegisteredForExam() {
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var examUser = new ExamUser();
        examUser.setExam(exam1);
        examUser.setUser(student1);
        examUser = examUserRepository.save(examUser);
        exam1.addExamUser(examUser);
        final var exam = examRepository.save(exam1);
        final var isUserRegistered = examRegistrationService.isUserRegisteredForExam(exam.getId(), student1.getId());
        final var isCurrentUserRegistered = examRegistrationService.isCurrentUserRegisteredForExam(exam.getId());
        assertThat(isUserRegistered).isTrue();
        assertThat(isCurrentUserRegistered).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterInstructorToExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "instructor1", null, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithExam() throws Exception {
        Course course = courseUtilService.createCourseWithExamAndExercises(TEST_PREFIX);
        course.setEndDate(now().minusMinutes(5));
        course = courseRepo.save(course);

        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);

        final var courseId = course.getId();
        await().until(() -> courseRepo.findById(courseId).get().getCourseArchivePath() != null);

        var updatedCourse = courseRepo.findById(courseId).get();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamAsInstructor() throws Exception {
        archiveExamAsInstructor();
    }

    private Course archiveExamAsInstructor() throws Exception {
        var course = courseUtilService.createCourseWithExamAndExercises(TEST_PREFIX);
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().get();

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.OK);

        final var examId = exam.getId();
        await().until(() -> examRepository.findById(examId).get().getExamArchivePath() != null);

        var updatedExam = examRepository.findById(examId).get();
        assertThat(updatedExam.getExamArchivePath()).isNotEmpty();
        return course;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testArchiveExamAsStudent_forbidden() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        course.setEndDate(now().minusMinutes(5));
        course = courseRepo.save(course);

        Exam exam = examUtilService.addExam(course);
        exam = examRepository.save(exam);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamBeforeEndDate_badRequest() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        course.setEndDate(now().plusMinutes(5));
        course = courseRepo.save(course);

        Exam exam = examUtilService.addExam(course);
        exam = examRepository.save(exam);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDownloadExamArchiveAsStudent_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDownloadExamArchiveAsTutor_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructor_not_found() throws Exception {
        // Create an exam with no archive
        Course course = courseUtilService.createCourse();
        course = courseRepo.save(course);

        // Return not found if the exam doesn't exist
        var downloadedArchive = request.get("/api/courses/" + course.getId() + "/exams/-1/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();

        // Returns not found if there is no archive
        var exam = examUtilService.addExam(course);
        downloadedArchive = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructorNotInCourse_forbidden() throws Exception {
        // Create an exam with no archive
        Course course = courseUtilService.createCourse();
        course.setInstructorGroupName("some-group");
        course = courseRepo.save(course);
        var exam = examUtilService.addExam(course);

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructor() throws Exception {
        var course = archiveExamAsInstructor();

        // Download the archive
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().get();
        var archive = request.getFile("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.OK, new LinkedMultiValueMap<>());
        assertThat(archive).isNotNull();

        // Extract the archive
        zipFileTestUtilService.extractZipFileRecursively(archive.getAbsolutePath());
        String extractedArchiveDir = archive.getPath().substring(0, archive.getPath().length() - 4);

        // Check that the dummy files we created exist in the archive.
        List<Path> filenames;
        try (var files = Files.walk(Path.of(extractedArchiveDir))) {
            filenames = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
        }

        var submissions = submissionRepository.findByParticipation_Exercise_ExerciseGroup_Exam_Id(exam.getId());

        var savedSubmission = submissions.stream().filter(submission -> submission instanceof FileUploadSubmission).findFirst().get();
        assertSubmissionFilename(filenames, savedSubmission, ".png");

        savedSubmission = submissions.stream().filter(submission -> submission instanceof TextSubmission).findFirst().get();
        assertSubmissionFilename(filenames, savedSubmission, ".txt");

        savedSubmission = submissions.stream().filter(submission -> submission instanceof ModelingSubmission).findFirst().get();
        assertSubmissionFilename(filenames, savedSubmission, ".json");
    }

    private void assertSubmissionFilename(List<Path> expectedFilenames, Submission submission, String filenameExtension) {
        var studentParticipation = (StudentParticipation) submission.getParticipation();
        var exerciseTitle = submission.getParticipation().getExercise().getTitle();
        var studentLogin = studentParticipation.getStudent().get().getLogin();
        var filename = exerciseTitle + "-" + studentLogin + "-" + submission.getId() + filenameExtension;
        assertThat(expectedFilenames).contains(Path.of(filename));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(ints = { 0, 1, 2 })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStatsForExamAssessmentDashboard(int numberOfCorrectionRounds) throws Exception {
        log.debug("testGetStatsForExamAssessmentDashboard: step 1 done");
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        User examTutor1 = userRepo.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        User examTutor2 = userRepo.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow();

        var examVisibleDate = now().minusMinutes(5);
        var examStartDate = now().plusMinutes(5);
        var examEndDate = now().plusMinutes(20);
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRounds);
        exam = examRepository.save(exam);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);

        log.debug("testGetStatsForExamAssessmentDashboard: step 2 done");

        var stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfSubmissions()).isInstanceOf(DueDateStat.class);
        assertThat(stats.getTutorLeaderboardEntries()).isInstanceOf(List.class);
        if (numberOfCorrectionRounds != 0) {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).isInstanceOf(DueDateStat[].class);
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        }
        else {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).isNull();
        }
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        assertThat(stats.getNumberOfSubmissions().inTime()).isZero();
        if (numberOfCorrectionRounds > 0) {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        }
        else {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).isNull();
        }
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        if (numberOfCorrectionRounds == 0) {
            // We do not need any more assertions, as numberOfCorrectionRounds is only 0 for test exams (no manual assessment)
            return;
        }

        var lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();

        log.debug("testGetStatsForExamAssessmentDashboard: step 3 done");

        // register users. Instructors are ignored from scores as they are exclusive for test run exercises
        Set<User> registeredStudents = getRegisteredStudentsForExam();
        for (var student : registeredStudents) {
            var registeredExamUser = new ExamUser();
            registeredExamUser.setExam(exam);
            registeredExamUser.setUser(student);
            exam.addExamUser(registeredExamUser);
        }
        exam.setNumberOfExercisesInExam(exam.getExerciseGroups().size());
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);
        exam = examRepository.findWithExamUsersAndExerciseGroupsAndExercisesById(exam.getId()).get();

        log.debug("testGetStatsForExamAssessmentDashboard: step 4 done");

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        int noGeneratedParticipations = ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course);
        verify(gitService, times(getNumberOfProgrammingExercises(exam))).combineAllCommitsOfRepositoryIntoOne(any());
        // set start and submitted date as results are created below
        studentExams.forEach(studentExam -> {
            studentExam.setStarted(true);
            studentExam.setStartedDate(now().minusMinutes(2));
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(now().minusMinutes(1));
        });
        studentExamRepository.saveAll(studentExams);

        log.debug("testGetStatsForExamAssessmentDashboard: step 5 done");

        // Fetch the created participations and assign them to the exercises
        int participationCounter = 0;
        List<Exercise> exercisesInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).toList();
        for (var exercise : exercisesInExam) {
            List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndTestRunWithEagerLegalSubmissionsResult(exercise.getId(), false);
            exercise.setStudentParticipations(new HashSet<>(participations));
            participationCounter += exercise.getStudentParticipations().size();
        }
        assertThat(noGeneratedParticipations).isEqualTo(participationCounter);

        log.debug("testGetStatsForExamAssessmentDashboard: step 6 done");

        // Assign submissions to the participations
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                assertThat(participation.getSubmissions()).hasSize(1);
                Submission submission = participation.getSubmissions().iterator().next();
                submission.submitted(true);
                submission.setSubmissionDate(now().minusMinutes(6));
                submissionRepository.save(submission);
            }
        }

        log.debug("testGetStatsForExamAssessmentDashboard: step 7 done");

        // check the stats again - check the count of submitted submissions
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        // 85 = (17 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        // Score used for all exercise results
        Double resultScore = 75.0;

        log.debug("testGetStatsForExamAssessmentDashboard: step 7 done");

        // Lock all submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
                // Create results
                var result = new Result().score(resultScore);
                if (exercise instanceof QuizExercise) {
                    result.completionDate(now().minusMinutes(4));
                    result.setRated(true);
                }
                result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
                result.setParticipation(participation);
                result.setAssessor(examTutor1);
                result = resultRepository.save(result);
                result.setSubmission(submission);
                submission.addResult(result);
                submissionRepository.save(submission);
            }
        }
        log.debug("testGetStatsForExamAssessmentDashboard: step 8 done");

        // check the stats again
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);

        assertThat(stats.getNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);
        // (studentExams.size() users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // the studentExams.size() quiz submissions are already assessed
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size());
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);

        log.debug("testGetStatsForExamAssessmentDashboard: step 9 done");

        // test the query needed for assessment information
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        exam.getExerciseGroups().forEach(group -> {
            var locks = group.getExercises().stream().map(
                    exercise -> resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds, examTutor2)[0]
                            .inTime())
                    .reduce(Long::sum).get();
            if (group.getExercises().stream().anyMatch(exercise -> !(exercise instanceof QuizExercise)))
                assertThat(locks).isEqualTo(studentExams.size());
        });

        log.debug("testGetStatsForExamAssessmentDashboard: step 10 done");

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).hasSize(studentExams.size() * 5);

        log.debug("testGetStatsForExamAssessmentDashboard: step 11 done");

        // Finish assessment of all submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
                var result = submission.getLatestResult().completionDate(now().minusMinutes(5));
                result.setRated(true);
                resultRepository.save(result);
            }
        }

        log.debug("testGetStatsForExamAssessmentDashboard: step 12 done");

        // check the stats again
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // 75 + the 19 quiz submissions
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size() * 5L + studentExams.size());
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        log.debug("testGetStatsForExamAssessmentDashboard: step 13 done");

        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();
        if (numberOfCorrectionRounds == 2) {
            lockAndAssessForSecondCorrection(exam, course, studentExams, exercisesInExam, numberOfCorrectionRounds);
        }

        log.debug("testGetStatsForExamAssessmentDashboard: step 14 done");
    }

    private void lockAndAssessForSecondCorrection(Exam exam, Course course, List<StudentExam> studentExams, List<Exercise> exercisesInExam, int numberOfCorrectionRounds)
            throws Exception {
        // Lock all submissions
        User examInstructor = userRepo.findOneByLogin(TEST_PREFIX + "instructor1").get();
        User examTutor2 = userRepo.findOneByLogin(TEST_PREFIX + "tutor2").get();

        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                assertThat(participation.getSubmissions()).hasSize(1);
                Submission submission = participation.getSubmissions().iterator().next();
                // Create results
                var result = new Result().score(50D).rated(true);
                if (exercise instanceof QuizExercise) {
                    result.completionDate(now().minusMinutes(3));
                }
                result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
                result.setParticipation(participation);
                result.setAssessor(examInstructor);
                result = resultRepository.save(result);
                result.setSubmission(submission);
                submission.addResult(result);
                submissionRepository.save(submission);
            }
        }
        // check the stats again
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // the 15 quiz submissions are already assessed - and all are assessed in the first correctionRound
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size() * 6L);
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[1].inTime()).isEqualTo(studentExams.size());
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);

        // test the query needed for assessment information
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        exam.getExerciseGroups().forEach(group -> {
            var locksRound1 = group.getExercises().stream().map(
                    exercise -> resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds, examTutor2)[0]
                            .inTime())
                    .reduce(Long::sum).get();
            if (group.getExercises().stream().anyMatch(exercise -> !(exercise instanceof QuizExercise))) {
                assertThat(locksRound1).isZero();
            }

            var locksRound2 = group.getExercises().stream().map(
                    exercise -> resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds, examTutor2)[1]
                            .inTime())
                    .reduce(Long::sum).get();
            if (group.getExercises().stream().anyMatch(exercise -> !(exercise instanceof QuizExercise))) {
                assertThat(locksRound2).isEqualTo(studentExams.size());
            }
        });

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).hasSize(studentExams.size() * 5);

        // Finish assessment of all submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
                var result = submission.getLatestResult().completionDate(now().minusMinutes(5));
                result.setRated(true);
                resultRepository.save(result);
            }
        }

        // check the stats again
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // 75 + the 15 quiz submissions
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size() * 6L);
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsTemplateCombine() throws Exception {
        Exam examWithProgramming = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, true);
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + examWithProgramming.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);

        verify(gitService, times(0)).combineAllCommitsOfRepositoryIntoOne(any());

        // invoke prepare exercise start
        prepareExerciseStart(exam1);

        verify(gitService, times(getNumberOfProgrammingExercises(exam1))).combineAllCommitsOfRepositoryIntoOne(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExamTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    private void testGetExamTitle() throws Exception {
        Course course = courseUtilService.createCourse();
        Exam exam = ExamFactory.generateExam(course);
        exam.setTitle("Test Exam");
        exam = examRepository.save(exam);
        course.addExam(exam);
        courseRepo.save(course);

        final var title = request.get("/api/exams/" + exam.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(exam.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExamTitleForNonExistingExam() throws Exception {
        request.get("/api/exams/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamMonitoringStatus() throws Exception {
        exam1.setMonitoring(true);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam1, Exam.class, HttpStatus.OK);

        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam1.getId() + "/update", true);

        exam1.setMonitoring(false);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam1, Exam.class, HttpStatus.OK);

        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam1.getId() + "/update", false);
    }

    // ExamRegistration Service - checkRegistrationOrRegisterStudentToTestExam
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckRegistrationOrRegisterStudentToTestExam_noTestExam() {
        assertThatThrownBy(
                () -> examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, exam1.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1")))
                        .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testCheckRegistrationOrRegisterStudentToTestExam_studentNotPartOfCourse() {
        assertThatThrownBy(
                () -> examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, exam1.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student42")))
                        .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckRegistrationOrRegisterStudentToTestExam_successfulRegistration() {
        Exam testExam = ExamFactory.generateTestExam(course1);
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        testExam = examRepository.save(testExam);
        var examUser = new ExamUser();
        examUser.setExam(testExam);
        examUser.setUser(student1);
        examUser = examUserRepository.save(examUser);
        testExam.addExamUser(examUser);
        testExam = examRepository.save(testExam);
        examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, testExam.getId(), student1);
        Exam testExamReloaded = examRepository.findByIdWithExamUsersElseThrow(testExam.getId());
        assertThat(testExamReloaded.getExamUsers()).contains(examUser);
    }

    // ExamResource - getStudentExamForTestExamForStart
    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetStudentExamForTestExamForStart_notRegisteredInCourse() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/start", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_notVisible() throws Exception {
        testExam1.setVisibleDate(now().plusMinutes(60));
        testExam1 = examRepository.save(testExam1);

        request.get("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/start", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_ExamDoesNotBelongToCourse() throws Exception {
        Exam testExam = examUtilService.addTestExam(course2);

        request.get("/api/courses/" + course1.getId() + "/exams/" + testExam.getId() + "/start", HttpStatus.CONFLICT, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_fetchExam_successful() throws Exception {
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var testExam = examUtilService.addTestExam(course2);
        testExam = examRepository.save(testExam);
        var examUser = new ExamUser();
        examUser.setExam(testExam);
        examUser.setUser(student1);
        examUser = examUserRepository.save(examUser);
        testExam.addExamUser(examUser);
        examRepository.save(testExam);
        var studentExam5 = examUtilService.addStudentExamForTestExam(testExam, student1);
        StudentExam studentExamReceived = request.get("/api/courses/" + course2.getId() + "/exams/" + testExam.getId() + "/start", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamReceived).isEqualTo(studentExam5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForImportWithExercises_successful() throws Exception {
        Exam received = request.get("/api/exams/" + exam2.getId(), HttpStatus.OK, Exam.class);
        assertThat(received).isEqualTo(exam2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testGetExamForImportWithExercises_noInstructorAccess() throws Exception {
        request.get("/api/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testGetExamForImportWithExercises_noTutorAccess() throws Exception {
        request.get("/api/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExamForImportWithExercises_noEditorAccess() throws Exception {
        request.get("/api/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_WithoutExercises_instructor_successful() throws Exception {
        var title = "My fancy search title for the exam which is not used somewhere else";
        var exam = ExamFactory.generateExam(course1);
        exam.setTitle(title);
        examRepository.save(exam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).containsExactly(exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_WithExercises_instructor_successful() throws Exception {
        var newExam = examUtilService.addTestExamWithExerciseGroup(course1, true);
        var searchTerm = "A very distinct title that should only ever exist once in the database";
        newExam.setTitle(searchTerm);
        examRepository.save(newExam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(searchTerm);
        final var result = request.getSearchResult("/api/exams?withExercises=true", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        List<Exam> foundExams = result.getResultsOnPage();
        assertThat(foundExams).hasSize(1).containsExactly(newExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_WithoutExercisesAndExamsNotLinkedToCourse_instructor_successful() throws Exception {
        var title = "Another fancy exam search title for the exam which is not used somewhere else";
        Course course3 = courseUtilService.addEmptyCourse();
        course3.setInstructorGroupName("non-instructors");
        courseRepo.save(course3);
        var exam = examUtilService.addExamWithExerciseGroup(course3, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllExamsOnPage_WithoutExercisesAndExamsNotLinkedToCourse_admin_successful() throws Exception {
        var title = "Yet another 3rd exam search title for the exam which is not used somewhere else";
        Course course3 = courseUtilService.addEmptyCourse();
        course3.setInstructorGroupName("non-instructors");
        courseRepo.save(course3);
        var exam = examUtilService.addExamWithExerciseGroup(course3, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).contains(exam);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testGetAllExamsOnPage_tutor() throws Exception {
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllExamsOnPage_student() throws Exception {
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testImportExamWithExercises_student() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam1, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testImportExamWithExercises_tutor() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam1, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_idExists() throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);
        exam.setId(2L);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_courseMismatch() throws Exception {
        // No Course
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setCourse(null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Exam Course and REST-Course mismatch
        final Exam examB = ExamFactory.generateExam(course1);
        examB.setCourse(course2);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_dateConflict() throws Exception {
        // Visible Date after Started Date
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setVisibleDate(ZonedDateTime.now().plusHours(2));
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Visible Date missing
        final Exam examB = ExamFactory.generateExam(course1);
        examB.setVisibleDate(null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);

        // Started Date after End Date
        final Exam examC = ExamFactory.generateExam(course1);
        examC.setStartDate(ZonedDateTime.now().plusHours(2));
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examC, HttpStatus.BAD_REQUEST, null);

        // Started Date missing
        final Exam examD = ExamFactory.generateExam(course1);
        examD.setStartDate(null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examD, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_dateConflictTestExam() throws Exception {
        // Working Time larger than Working window
        final Exam examA = ExamFactory.generateTestExam(course1);
        examA.setWorkingTime(3 * 60 * 60);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Working Time larger than Working window
        final Exam examB = ExamFactory.generateTestExam(course1);
        examB.setWorkingTime(0);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_pointConflict() throws Exception {
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setExamMaxPoints(-5);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_correctionRoundConflict() throws Exception {
        // Correction round <= 0
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setNumberOfCorrectionRoundsInExam(0);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Correction round >= 2
        final Exam examB = ExamFactory.generateExam(course1);
        examB.setNumberOfCorrectionRoundsInExam(3);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);

        // Correction round != 0 for test exam
        final Exam examC = ExamFactory.generateTestExam(course1);
        examC.setNumberOfCorrectionRoundsInExam(1);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examC, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithoutExercises() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        exam.setId(null);

        exam.setChannelName("channelname-imported");
        final Exam received = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exam-import", exam, Exam.class, HttpStatus.CREATED);
        assertThat(received.getId()).isNotNull();
        assertThat(received.getTitle()).isEqualTo(exam.getTitle());
        assertThat(received.isTestExam()).isFalse();
        assertThat(received.getWorkingTime()).isEqualTo(3000);
        assertThat(received.getStartText()).isEqualTo("Start Text");
        assertThat(received.getEndText()).isEqualTo("End Text");
        assertThat(received.getConfirmationStartText()).isEqualTo("Confirmation Start Text");
        assertThat(received.getConfirmationEndText()).isEqualTo("Confirmation End Text");
        assertThat(received.getExamMaxPoints()).isEqualTo(90);
        assertThat(received.getNumberOfExercisesInExam()).isEqualTo(1);
        assertThat(received.getRandomizeExerciseOrder()).isFalse();
        assertThat(received.getNumberOfCorrectionRoundsInExam()).isEqualTo(1);
        assertThat(received.getCourse().getId()).isEqualTo(course1.getId());

        exam.setVisibleDate(ZonedDateTime.ofInstant(exam.getVisibleDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setVisibleDate(ZonedDateTime.ofInstant(received.getVisibleDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getVisibleDate()).isEqualToIgnoringSeconds(exam.getVisibleDate());
        exam.setStartDate(ZonedDateTime.ofInstant(exam.getStartDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setStartDate(ZonedDateTime.ofInstant(received.getStartDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getStartDate()).isEqualToIgnoringSeconds(exam.getStartDate());
        exam.setEndDate(ZonedDateTime.ofInstant(exam.getEndDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setEndDate(ZonedDateTime.ofInstant(received.getEndDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getEndDate()).isEqualToIgnoringSeconds(exam.getEndDate());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithExercises() throws Exception {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        exam.setId(null);
        exam.setChannelName("testchannelname-imported");
        final Exam received = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exam-import", exam, Exam.class, CREATED);
        assertThat(received.getId()).isNotNull();
        assertThat(received.getTitle()).isEqualTo(exam.getTitle());
        assertThat(received.getCourse()).isEqualTo(course1);
        assertThat(received.getCourse()).isEqualTo(exam.getCourse());
        assertThat(received.getExerciseGroups()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithImportToOtherCourse() throws Exception {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course2);
        exam.setCourse(course1);
        exam.setId(null);
        exam.setChannelName("testchannelname");
        final Exam received = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exam-import", exam, Exam.class, CREATED);
        assertThat(received.getExerciseGroups()).hasSize(4);

        for (int i = 0; i <= 3; i++) {
            Exercise expected = exam.getExerciseGroups().get(i).getExercises().stream().findFirst().get();
            Exercise exerciseReceived = received.getExerciseGroups().get(i).getExercises().stream().findFirst().get();
            assertThat(exerciseReceived.getExerciseGroup()).isNotEqualTo(expected.getExerciseGroup());
            assertThat(exerciseReceived.getTitle()).isEqualTo(expected.getTitle());
            assertThat(exerciseReceived.getId()).isNotEqualTo(expected.getId());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_preCheckFailed() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        ExerciseGroup programmingGroup = ExamFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);
        exam.setId(null);
        ProgrammingExercise programming = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(programmingGroup, ProgrammingLanguage.JAVA);
        programmingGroup.addExercise(programming);
        exerciseRepo.save(programming);

        doReturn(true).when(versionControlService).checkIfProjectExists(any(), any());
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(any(), any());

        request.getMvc().perform(post("/api/courses/" + course1.getId() + "/exam-import").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(exam)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).hasMessage("Exam contains programming exercise(s) with invalid short name."));
    }

    private int prepareExerciseStart(Exam exam) throws Exception {
        return ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course1);
    }

    private Set<User> getRegisteredStudentsForExam() {
        var registeredStudents = new HashSet<User>();
        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
            registeredStudents.add(userUtilService.getUserByLogin(TEST_PREFIX + "student" + i));
        }
        for (int i = 1; i <= NUMBER_OF_TUTORS; i++) {
            registeredStudents.add(userUtilService.getUserByLogin(TEST_PREFIX + "tutor" + i));
        }

        return registeredStudents;
    }
}
