package de.tum.in.www1.artemis;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.TextAssessmentKnowledgeService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.exam.*;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class ExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TextAssessmentKnowledgeService textAssessmentKnowledgeService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamService examService;

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

    private List<User> users;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private Exam testExam1;

    private StudentExam studentExam1;

    private final int numberOfStudents = 10;

    private User instructor;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(numberOfStudents, 5, 0, 1);
        course1 = database.addEmptyCourse();
        course2 = database.addEmptyCourse();

        User student1 = users.get(0);
        student1.setGroups(Set.of(course1.getStudentGroupName()));
        userRepo.save(student1);

        exam1 = database.addExam(course1);
        exam2 = database.addExamWithExerciseGroup(course1, true);
        testExam1 = database.addTestExam(course1);
        studentExam1 = database.addStudentExamForTestExam(testExam1, users.get(0));

        instructor = users.get(users.size() - 1);

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("student42", passwordService.hashPassword(ModelFactory.USER_PASSWORD)));
        userRepo.save(ModelFactory.generateActivatedUser("tutor6", passwordService.hashPassword(ModelFactory.USER_PASSWORD)));
        userRepo.save(ModelFactory.generateActivatedUser("instructor6", passwordService.hashPassword(ModelFactory.USER_PASSWORD)));
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void resetDatabase() {
        bitbucketRequestMockProvider.reset();
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRegisterUserInExam_addedToCourseStudentsGroup() throws Exception {
        User student42 = database.getUserByLogin("student42");
        jiraRequestMockProvider.enableMockingOfRequests();
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        bitbucketRequestMockProvider.mockUpdateUserDetails(student42.getLogin(), student42.getEmail(), student42.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();

        List<User> studentsInCourseBefore = userRepo.findAllInGroupWithAuthorities(course1.getStudentGroupName());
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/student42", null, HttpStatus.OK, null);
        List<User> studentsInCourseAfter = userRepo.findAllInGroupWithAuthorities(course1.getStudentGroupName());
        studentsInCourseBefore.add(student42);
        assertThat(studentsInCourseBefore).containsExactlyInAnyOrderElementsOf(studentsInCourseAfter);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddStudentToExam_testExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students/student42", null, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRemoveStudentToExam_testExam() throws Exception {

        request.delete("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students/student42", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRegisterUsersInExam() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        var exam = ModelFactory.generateExam(course1);
        var savedExam = examRepository.save(exam);
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        var student3 = database.getUserByLogin("student3");
        var student5 = database.getUserByLogin("student5");
        var student6 = database.getUserByLogin("student6");
        var student7 = database.getUserByLogin("student7");
        var student8 = database.getUserByLogin("student8");
        var student9 = database.getUserByLogin("student9");
        var student10 = database.getUserByLogin("student10");
        var registrationNumber1 = "1111111";
        var registrationNumber2 = "1111112";
        var registrationNumber3 = "1111113";
        var registrationNumber3WithTypo = registrationNumber3 + "0";
        var registrationNumber5 = "1111115";
        var registrationNumber5WithTypo = registrationNumber5 + "1";
        var registrationNumber6 = "1111116";
        var registrationNumber99 = "1111199";
        var registrationNumber100 = "1111100";
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
        var ldapUser100Dto = new LdapUserDto().registrationNumber(registrationNumber100).firstName("Student100").lastName("Student100").username("student100")
                .email("student100@tum.de");
        doReturn(Optional.of(ldapUser100Dto)).when(ldapUserService).findByRegistrationNumber(registrationNumber100);

        // first and second mocked calls are expected to add student 5 and 99 to the course students
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        // third mocked call expected to create student 100
        jiraRequestMockProvider.mockCreateUserInExternalUserManagement(ldapUser100Dto.getUsername(), ldapUser100Dto.getFirstName() + " " + ldapUser100Dto.getLastName(),
                ldapUser100Dto.getEmail());
        // the last two mocked calls are expected to add students 100, 6, 7, 8, and 9 to the course student group
        for (int i = 0; i < 5; i++) {
            jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName(), false);
        }

        bitbucketRequestMockProvider.mockUpdateUserDetails(student5.getLogin(), student5.getEmail(), student5.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();

        var student99 = ModelFactory.generateActivatedUser("student99");     // not registered for the course
        student99.setRegistrationNumber(registrationNumber99);
        userRepo.save(student99);
        bitbucketRequestMockProvider.mockUpdateUserDetails(student99.getLogin(), student99.getEmail(), student99.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        student99 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student99").get();
        assertThat(student99.getGroups()).doesNotContain(course1.getStudentGroupName());

        // Note: student100 is not yet a user of Artemis and should be retrieved from the LDAP

        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/student1", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", null, HttpStatus.NOT_FOUND, null);

        Exam storedExam = examRepository.findWithRegisteredUsersById(savedExam.getId()).get();
        assertThat(storedExam.getRegisteredUsers()).containsExactly(student1);

        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/student1", HttpStatus.OK);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
        storedExam = examRepository.findWithRegisteredUsersById(savedExam.getId()).get();
        assertThat(storedExam.getRegisteredUsers()).isEmpty();

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
        var studentDto10 = new StudentDTO();    // completely empty

        var studentDto99 = new StudentDTO().registrationNumber(registrationNumber99);
        var studentDto100 = new StudentDTO().registrationNumber(registrationNumber100);

        // Add a student with login but empty registration number
        var studentDto6 = new StudentDTO().registrationNumber(emptyRegistrationNumber);
        studentDto6.setLogin(student6.getLogin());
        var studentsToRegister = List.of(studentDto1, studentDto2, studentDto3, studentDto5, studentDto99, studentDto100, studentDto6, studentDto7, studentDto8, studentDto9,
                studentDto10);
        bitbucketRequestMockProvider.mockUpdateUserDetails("student100", "student100@tum.de", "Student100 Student100");
        bitbucketRequestMockProvider.mockAddUserToGroups();
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
        bitbucketRequestMockProvider.mockUpdateUserDetails("student100", "student100@tum.de", "Student100 Student100");
        bitbucketRequestMockProvider.mockAddUserToGroups();

        // now we register all these students for the exam.
        List<StudentDTO> registrationFailures = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students",
                studentsToRegister, StudentDTO.class, HttpStatus.OK);
        assertThat(registrationFailures).containsExactlyInAnyOrder(studentDto3, studentDto10);
        storedExam = examRepository.findWithRegisteredUsersById(savedExam.getId()).get();

        // now a new user student100 should exist
        var student100 = database.getUserByLogin("student100");

        assertThat(storedExam.getRegisteredUsers()).containsExactlyInAnyOrder(student1, student2, student5, student99, student100, student6, student7, student8, student9);

        for (var user : storedExam.getRegisteredUsers()) {
            // all registered users must have access to the course
            user = userRepo.findOneWithGroupsAndAuthoritiesByLogin(user.getLogin()).get();
            assertThat(user.getGroups()).contains(course1.getStudentGroupName());
        }

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddStudentsToExam_testExam() throws Exception {
        User student1 = database.getUserByLogin("student1");
        String registrationNumber1 = "1111111";
        student1.setRegistrationNumber(registrationNumber1);
        userRepo.save(student1);

        StudentDTO studentDto1 = new StudentDTO().registrationNumber(registrationNumber1);
        List<StudentDTO> studentDTOS = List.of(studentDto1);
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", studentDTOS, StudentDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRemoveAllStudentsFromExam_testExam() throws Exception {

        request.delete("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testStartExercisesWithTextExercise() throws Exception {

        // TODO IMPORTANT test more complex exam configurations (mixed exercise type, more variants and more registered students)

        // registering users
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        var registeredUsers = Set.of(student1, student2);
        exam2.setRegisteredUsers(registeredUsers);
        // setting dates
        exam2.setStartDate(now().plusHours(2));
        exam2.setEndDate(now().plusHours(3));
        exam2.setVisibleDate(now().plusHours(1));

        // creating exercise
        ExerciseGroup exerciseGroup = exam2.getExerciseGroups().get(0);

        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        exerciseGroup.addExercise(textExercise);
        exerciseGroupRepository.save(exerciseGroup);
        textExercise = exerciseRepo.save(textExercise);

        List<StudentExam> createdStudentExams = new ArrayList<>();

        // creating student exams
        for (User user : registeredUsers) {
            StudentExam studentExam = new StudentExam();
            studentExam.addExercise(textExercise);
            studentExam.setUser(user);
            exam2.addStudentExam(studentExam);
            createdStudentExams.add(studentExamRepository.save(studentExam));
        }

        exam2 = examRepository.save(exam2);

        // invoke start exercises
        Integer noGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);
        assertThat(noGeneratedParticipations).isEqualTo(exam2.getStudentExams().size());
        List<Participation> studentParticipations = participationTestRepository.findAllWithSubmissions();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(textExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam2.getExerciseGroups().get(0));
            assertThat(participation.getSubmissions()).hasSize(1);
            var textSubmission = (TextSubmission) participation.getSubmissions().iterator().next();
            assertThat(textSubmission.getText()).isNull();
        }

        // Cleanup of Bidirectional Relationships
        for (StudentExam studentExam : createdStudentExams) {
            exam2.removeStudentExam(studentExam);
        }
        examRepository.save(exam2);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testStartExercisesWithModelingExercise() throws Exception {
        // TODO IMPORTANT test more complex exam configurations (mixed exercise type, more variants and more registered students)

        // registering users
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        var registeredUsers = Set.of(student1, student2);
        exam2.setRegisteredUsers(registeredUsers);
        // setting dates
        exam2.setStartDate(now().plusHours(2));
        exam2.setEndDate(now().plusHours(3));
        exam2.setVisibleDate(now().plusHours(1));

        // creating exercise
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exam2.getExerciseGroups().get(0));
        exam2.getExerciseGroups().get(0).addExercise(modelingExercise);
        exerciseGroupRepository.save(exam2.getExerciseGroups().get(0));
        modelingExercise = exerciseRepo.save(modelingExercise);

        List<StudentExam> createdStudentExams = new ArrayList<>();

        // creating student exams
        for (User user : registeredUsers) {
            StudentExam studentExam = new StudentExam();
            studentExam.addExercise(modelingExercise);
            studentExam.setUser(user);
            exam2.addStudentExam(studentExam);
            createdStudentExams.add(studentExamRepository.save(studentExam));
        }

        exam2 = examRepository.save(exam2);

        // invoke start exercises
        Integer noGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);
        assertThat(noGeneratedParticipations).isEqualTo(exam2.getStudentExams().size());
        List<Participation> studentParticipations = participationTestRepository.findAllWithSubmissions();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(modelingExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam2.getExerciseGroups().get(0));
            assertThat(participation.getSubmissions()).hasSize(1);
            var textSubmission = (ModelingSubmission) participation.getSubmissions().iterator().next();
            assertThat(textSubmission.getModel()).isNull();
            assertThat(textSubmission.getExplanationText()).isNull();
        }

        // Cleanup of Bidirectional Relationships
        for (StudentExam studentExam : createdStudentExams) {
            exam2.removeStudentExam(studentExam);
        }
        examRepository.save(exam2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExams() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);

        // invoke generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getRegisteredUsers().size());
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExams_testExam() throws Exception {

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExamsNoExerciseGroups_badRequest() throws Exception {
        Exam exam = database.addExamWithExerciseGroup(course1, true);
        exam.setStartDate(now());
        exam.setEndDate(now().plusHours(2));
        exam = examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExamsNoExerciseNumber_badRequest() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);
        exam.setNumberOfExercisesInExam(null);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExamsNotEnoughExerciseGroups_badRequest() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() + 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExamsTooManyMandatoryExerciseGroups_badRequest() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() - 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateMissingStudentExams() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);

        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getRegisteredUsers().size());

        // Register two new students
        var student5 = database.getUserByLogin("student5");
        var student6 = database.getUserByLogin("student6");
        exam.getRegisteredUsers().addAll(Set.of(student5, student6));
        examRepository.save(exam);

        // Generate individual exams for the two missing students
        List<StudentExam> missingStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(missingStudentExams).hasSize(2);

        // Fetch student exams
        List<StudentExam> studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(exam.getRegisteredUsers().size());

        // Another request should not create any exams
        missingStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        assertThat(missingStudentExams).isEmpty();
        studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(exam.getRegisteredUsers().size());

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateMissingStudentExams_testExam() throws Exception {

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/generate-missing-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEvaluateQuizExercises_testExam() throws Exception {

        request.post("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testRemovingAllStudents() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);

        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(4);
        assertThat(exam.getRegisteredUsers()).hasSize(4);

        // /courses/{courseId}/exams/{examId}/student-exams/start-exercises
        Integer numberOfGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

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
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", HttpStatus.OK);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(storedExam.getRegisteredUsers()).isEmpty();

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
    public void testRemovingAllStudentsAndParticipations() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);

        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(4);
        assertThat(exam.getRegisteredUsers()).hasSize(4);

        // /courses/{courseId}/exams/{examId}/student-exams/start-exercises
        Integer numberOfGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

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
        assertThat(storedExam.getRegisteredUsers()).isEmpty();

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
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testSaveExamWithExerciseGroupWithExerciseToDatabase() {
        database.addCourseExamExerciseGroupWithOneTextExercise();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
        ModelFactory.generateExam(course1);
        request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        Exam exam = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN, Exam.class);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reset", HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/student1", null, HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", Collections.singletonList(new StudentDTO()), HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/student1", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor6", roles = "INSTRUCTOR")
    public void testCreateExam_checkCourseAccess_InstructorNotInCourse_forbidden() throws Exception {
        Exam exam = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateExam_asInstructor() throws Exception {
        // Test for bad request when exam id is already set.
        Exam examA = ModelFactory.generateExam(course1);
        examA.setId(55L);
        request.post("/api/courses/" + course1.getId() + "/exams", examA, HttpStatus.BAD_REQUEST);
        // Test for bad request when course is null.
        Exam examB = ModelFactory.generateExam(course1);
        examB.setCourse(null);
        request.post("/api/courses/" + course1.getId() + "/exams", examB, HttpStatus.BAD_REQUEST);
        // Test for bad request when course deviates from course specified in route.
        Exam examC = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course2.getId() + "/exams", examC, HttpStatus.BAD_REQUEST);
        // Test invalid dates
        List<Exam> examsWithInvalidDate = createExamsWithInvalidDates(course1);
        for (var exam : examsWithInvalidDate) {
            request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        }
        // Test for conflict when user tries to create an exam with exercise groups.
        Exam examD = ModelFactory.generateExam(course1);
        examD.addExerciseGroup(ModelFactory.generateExerciseGroup(true, exam1));
        request.post("/api/courses/" + course1.getId() + "/exams", examD, HttpStatus.CONFLICT);
        // Test examAccessService.
        Exam examE = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", examE, HttpStatus.CREATED);
        verify(examAccessService, times(1)).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    private List<Exam> createExamsWithInvalidDates(Course course) {
        // Test for bad request, visible date not set
        Exam examA = ModelFactory.generateExam(course);
        examA.setVisibleDate(null);
        // Test for bad request, start date not set
        Exam examB = ModelFactory.generateExam(course);
        examB.setStartDate(null);
        // Test for bad request, end date not set
        Exam examC = ModelFactory.generateExam(course);
        examC.setEndDate(null);
        // Test for bad request, start date not after visible date
        Exam examD = ModelFactory.generateExam(course);
        examD.setStartDate(examD.getVisibleDate());
        // Test for bad request, end date not after start date
        Exam examE = ModelFactory.generateExam(course);
        examE.setEndDate(examE.getStartDate());
        // Test for bad request, when visibleDate equals the startDate
        Exam examF = ModelFactory.generateExam(course);
        examF.setVisibleDate(examF.getStartDate());
        return List.of(examA, examB, examC, examD, examE, examF);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateTestExam_asInstructor() throws Exception {
        // Test the creation of a test exam
        Exam examA = ModelFactory.generateTestExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", examA, HttpStatus.CREATED);

        verify(examAccessService, times(1)).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateTestExam_asInstructor_withVisibleDateEqualsStartDate() throws Exception {
        // Test the creation of a test exam, where visibleDate equals StartDate
        Exam examB = ModelFactory.generateTestExam(course1);
        examB.setVisibleDate(examB.getStartDate());
        request.post("/api/courses/" + course1.getId() + "/exams", examB, HttpStatus.CREATED);

        verify(examAccessService, times(1)).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateTestExam_asInstructor_badReuestWithWorkingTimeGreatherThanWorkingWindow() throws Exception {
        // Test for bad request, where workingTime is greater than difference between StartDate and EndDate
        Exam examC = ModelFactory.generateTestExam(course1);
        examC.setWorkingTime(5000);
        request.post("/api/courses/" + course1.getId() + "/exams", examC, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateTestExam_asInstructor_badRequestWithWorkingTimeSetToZero() throws Exception {
        // Test for bad request, if the working time is 0
        Exam examD = ModelFactory.generateTestExam(course1);
        examD.setWorkingTime(0);
        request.post("/api/courses/" + course1.getId() + "/exams", examD, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateTestExam_asInstructor_withExamModeChanged() throws Exception {
        // The Exam-Mode should not be changeable with a PUT / update operation, a CONFLICT should be returned instead
        // Case 1: test exam should be updated to real exam
        Exam examA = ModelFactory.generateTestExam(course1);
        Exam createdExamA = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams", examA, Exam.class, HttpStatus.CREATED);
        createdExamA.setTestExam(false);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", createdExamA, Exam.class, HttpStatus.CONFLICT);

        // Case 2: real exam should be updated to test exam
        Exam examB = ModelFactory.generateTestExam(course1);
        examB.setTestExam(false);
        Exam createdExamB = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams", examB, Exam.class, HttpStatus.CREATED);
        createdExamB.setTestExam(true);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", createdExamB, Exam.class, HttpStatus.CONFLICT);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExam_asInstructor() throws Exception {
        // Create instead of update if no id was set
        Exam exam = ModelFactory.generateExam(course1);
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
        exam = ModelFactory.generateExam(course1);
        exam.setId(1L);
        exam.setCourse(null);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        // Course id in the updated exam and in the REST resource url do not match -> bad request
        exam = ModelFactory.generateExam(course1);
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
        assertEquals(exam1, returnedExam);
        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(any());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExam_reschedule_visibleAndStartDateChanged() throws Exception {
        // Add a programming exercise to the exam and change the dates in order to invoke a rescheduling
        var programmingEx = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setVisibleDate(examWithProgrammingEx.getVisibleDate().plusSeconds(1));
        examWithProgrammingEx.setStartDate(examWithProgrammingEx.getStartDate().plusSeconds(1));
        examWithProgrammingEx.setWorkingTime(examWithProgrammingEx.getWorkingTime() - 1);
        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExam_reschedule_visibleDateChanged() throws Exception {
        var programmingEx = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setVisibleDate(examWithProgrammingEx.getVisibleDate().plusSeconds(1));
        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExam_reschedule_startDateChanged() throws Exception {
        var programmingEx = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setStartDate(examWithProgrammingEx.getStartDate().plusSeconds(1));
        examWithProgrammingEx.setWorkingTime(examWithProgrammingEx.getWorkingTime() - 1);
        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExam_rescheduleModeling_endDateChanged() throws Exception {
        var modelingExercise = database.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();
        examWithModelingEx.setEndDate(examWithModelingEx.getEndDate().plusSeconds(2));
        examWithModelingEx.setWorkingTime(examWithModelingEx.getWorkingTime() + 2);
        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);
        verify(instanceMessageSendService, times(1)).sendModelingExerciseSchedule(modelingExercise.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExam_rescheduleModeling_workingTimeChanged() throws Exception {
        var modelingExercise = database.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();
        examWithModelingEx.setVisibleDate(now().plusHours(1));
        examWithModelingEx.setStartDate(now().plusHours(2));
        examWithModelingEx.setEndDate(now().plusHours(3));
        examWithModelingEx.setWorkingTime(3600);
        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);

        StudentExam studentExam = database.addStudentExam(examWithModelingEx);
        request.patch("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams/" + examWithModelingEx.getId() + "/student-exams/" + studentExam.getId() + "/working-time",
                3, HttpStatus.OK);
        verify(instanceMessageSendService, times(2)).sendModelingExerciseSchedule(modelingExercise.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExam_asInstructor() throws Exception {
        assertThrows(EntityNotFoundException.class, () -> examRepository.findByIdElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> examRepository.findByIdWithRegisteredUsersElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> examRepository.findByIdWithExerciseGroupsElseThrow(Long.MAX_VALUE));
        assertThat(examRepository.findAllExercisesByExamId(Long.MAX_VALUE)).isEmpty();

        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK, Exam.class);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExam_asInstructor_WithTestRunQuizExerciseSubmissions() throws Exception {
        Course course = database.addEmptyCourse();
        Exam exam = database.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);
        examRepository.save(exam);

        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setTestRun(true);

        QuizExercise quizExercise = database.createQuizForExam(exerciseGroup);
        quizExercise.setStudentParticipations(Set.of(studentParticipation));
        studentParticipation.setExercise(quizExercise);

        exerciseRepo.save(quizExercise);
        studentParticipationRepository.save(studentParticipation);

        Exam returnedExam = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "?withExerciseGroups=true", HttpStatus.OK, Exam.class);

        assertThat(returnedExam.getExerciseGroups()).anyMatch(groups -> groups.getExercises().stream().anyMatch(Exercise::getTestRunParticipationsExist));
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamsForCourse_asInstructor() throws Exception {
        request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.OK, Exam.class);
        verify(examAccessService, times(1)).checkCourseAccessForTeachingAssistantElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamsForUser_asInstructor() throws Exception {
        request.getList("/api/courses/" + course1.getId() + "/exams-for-user", HttpStatus.OK, Exam.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetCurrentAndUpcomingExams() throws Exception {
        request.getList("/api/courses/upcoming-exams", HttpStatus.OK, Exam.class);
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    public void testGetCurrentAndUpcomingExamsForbiddenForUser() throws Exception {
        request.getList("/api/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCurrentAndUpcomingExamsForbiddenForInstructor() throws Exception {
        request.getList("/api/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCurrentAndUpcomingExamsForbiddenForTutor() throws Exception {
        request.getList("/api/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteEmptyExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exam2.getExerciseGroups().get(0));
        textExercise.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        exerciseRepo.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDeleteExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/654555", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testResetEmptyExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testResetExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exam2.getExerciseGroups().get(0));
        exerciseRepo.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testResetExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/654555/reset", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteStudent() throws Exception {
        // Create an exam with registered students
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");

        // Remove student1 from the exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/student1", HttpStatus.OK);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student1 was removed from the exam
        assertThat(storedExam.getRegisteredUsers()).doesNotContain(student1);
        assertThat(storedExam.getRegisteredUsers()).hasSize(3);

        // Create individual student exams
        List<StudentExam> generatedStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(generatedStudentExams).hasSize(storedExam.getRegisteredUsers().size());

        // Start the exam to create participations
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises", Optional.empty(), Integer.class,
                HttpStatus.OK);

        // Get the student exam of student2
        Optional<StudentExam> optionalStudent1Exam = generatedStudentExams.stream().filter(studentExam -> studentExam.getUser().equals(student2)).findFirst();
        assertThat(optionalStudent1Exam.get()).isNotNull();
        var studentExam2 = optionalStudent1Exam.get();

        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        database.changeUser("instructor1");
        // Remove student2 from the exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/student2", HttpStatus.OK);

        // Get the exam with all registered users
        params = new LinkedMultiValueMap<>();
        params.add("withStudents", "true");
        storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student2 was removed from the exam
        assertThat(storedExam.getRegisteredUsers()).doesNotContain(student2);
        assertThat(storedExam.getRegisteredUsers()).hasSize(2);

        // Ensure that the student exam of student2 was deleted
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams).hasSameSizeAs(storedExam.getRegisteredUsers()).doesNotContain(studentExam2);

        // Ensure that the participations were not deleted
        List<StudentParticipation> participationsStudent2 = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student2.getId(), studentExam2.getExercises());
        assertThat(participationsStudent2).hasSize(studentExam2.getExercises().size());

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamWithOptions() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        Course course = database.createCourseWithExamAndExerciseGroupAndExercises(user, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(course.getExams().iterator().next().getId()).get();
        // Get the exam with all registered users
        // 1. without options
        var exam1 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class);
        assertThat(exam1.getRegisteredUsers()).isEmpty();
        assertThat(exam1.getExerciseGroups()).isEmpty();

        // 2. with students, without exercise groups
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        var exam2 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam2.getRegisteredUsers()).hasSize(1);
        assertThat(exam2.getExerciseGroups()).isEmpty();

        // 3. with students, with exercise groups
        params.add("withExerciseGroups", "true");
        var exam3 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam3.getRegisteredUsers()).hasSize(1);
        assertThat(exam3.getExerciseGroups()).hasSize(exam.getExerciseGroups().size());
        assertThat(exam3.getExerciseGroups().get(0).getExercises()).hasSize(exam.getExerciseGroups().get(0).getExercises().size());
        assertThat(exam3.getExerciseGroups().get(1).getExercises()).hasSize(exam.getExerciseGroups().get(1).getExercises().size());
        assertThat(exam3.getNumberOfRegisteredUsers()).isNotNull().isEqualTo(1);

        // 4. without students, with exercise groups
        params = new LinkedMultiValueMap<>();
        params.add("withExerciseGroups", "true");
        var exam4 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam4.getRegisteredUsers()).isEmpty();
        assertThat(exam4.getExerciseGroups()).hasSize(exam.getExerciseGroups().size());
        assertThat(exam4.getExerciseGroups().get(0).getExercises()).hasSize(exam.getExerciseGroups().get(0).getExercises().size());
        assertThat(exam4.getExerciseGroups().get(1).getExercises()).hasSize(exam.getExerciseGroups().get(1).getExercises().size());
        exam4.getExerciseGroups().get(1).getExercises().forEach(exercise -> {
            assertThat(exercise.getNumberOfParticipations()).isNotNull();
            assertThat(exercise.getNumberOfParticipations()).isZero();
        });
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteStudentWithParticipationsAndSubmissions() throws Exception {
        // Create an exam with registered students
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);
        var student1 = database.getUserByLogin("student1");

        // Create individual student exams
        List<StudentExam> generatedStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);

        // Get the student exam of student1
        Optional<StudentExam> optionalStudent1Exam = generatedStudentExams.stream().filter(studentExam -> studentExam.getUser().equals(student1)).findFirst();
        assertThat(optionalStudent1Exam.get()).isNotNull();
        var studentExam1 = optionalStudent1Exam.get();

        // Start the exam to create participations
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises", Optional.empty(), Integer.class,
                HttpStatus.OK);
        List<StudentParticipation> participationsStudent1 = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student1.getId(), studentExam1.getExercises());
        assertThat(participationsStudent1).hasSize(studentExam1.getExercises().size());

        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        database.changeUser("instructor1");

        // Remove student1 from the exam and his participations
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withParticipationsAndSubmission", "true");
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/student1", HttpStatus.OK, params);

        // Get the exam with all registered users
        params = new LinkedMultiValueMap<>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student1 was removed from the exam
        assertThat(storedExam.getRegisteredUsers()).doesNotContain(student1);
        assertThat(storedExam.getRegisteredUsers()).hasSize(3);

        // Ensure that the student exam of student1 was deleted
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams).hasSameSizeAs(storedExam.getRegisteredUsers()).doesNotContain(studentExam1);

        // Ensure that the participations of student1 were deleted
        participationsStudent1 = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student1.getId(),
                studentExam1.getExercises());
        assertThat(participationsStudent1).isEmpty();

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExamForTestRunDashboard_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamForTestRunDashboard_badRequest() throws Exception {
        request.get("/api/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.BAD_REQUEST, Exam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteExamWithOneTestRun() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var exam = database.addExam(course1);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamForTestRunDashboard_ok() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var exam = database.addExam(course1);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        exam = request.get("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.OK, Exam.class);
        assertThat(exam.getExerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.getExercises().stream()).collect(Collectors.toList())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteStudentThatDoesNotExist() throws Exception {
        Exam exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(course1);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForStart() throws Exception {
        Exam exam = database.addActiveExamWithRegisteredUser(course1, users.get(0));
        StudentExam response = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/start", HttpStatus.OK, StudentExam.class);
        assertThat(response.getExam()).isEqualTo(exam);
        verify(examAccessService, times(1)).getExamInCourseElseThrow(course1.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddAllRegisteredUsersToExam() throws Exception {
        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, false);
        exam = examRepository.save(exam);
        course.addExam(exam);
        course = courseRepo.save(course);

        var instructor = database.getUserByLogin("instructor1");
        instructor.setGroups(Collections.singleton("instructor"));
        userRepo.save(instructor);

        var student99 = ModelFactory.generateActivatedUser("student99");     // not registered for the course
        student99.setRegistrationNumber("1234");
        userRepo.save(student99);
        student99 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student99").get();
        student99.setGroups(Collections.singleton("tumuser"));
        userRepo.save(student99);
        assertThat(student99.getGroups()).contains(course.getStudentGroupName());
        assertThat(exam.getRegisteredUsers()).doesNotContain(student99);

        request.postWithoutLocation("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/register-course-students", null, HttpStatus.OK, null);

        exam = examRepository.findWithRegisteredUsersById(exam.getId()).get();

        // 10 normal students + our custom student99
        assertThat(exam.getRegisteredUsers()).hasSize(this.numberOfStudents + 1);
        assertThat(exam.getRegisteredUsers()).contains(student99);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForInstructorElseThrow(course.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRegisterCourseStudents_testExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/register-course-students", null, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateOrderOfExerciseGroups() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        exerciseGroup1.setTitle("first");
        ExerciseGroup exerciseGroup2 = new ExerciseGroup();
        exerciseGroup2.setTitle("second");
        ExerciseGroup exerciseGroup3 = new ExerciseGroup();
        exerciseGroup3.setTitle("third");

        Exam exam = database.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam.addExerciseGroup(exerciseGroup2);
        exam.addExerciseGroup(exerciseGroup3);
        examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        exerciseGroup2 = examWithExerciseGroups.getExerciseGroups().get(1);
        exerciseGroup3 = examWithExerciseGroups.getExerciseGroups().get(2);

        TextExercise exercise1_1 = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        TextExercise exercise1_2 = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        TextExercise exercise2_1 = ModelFactory.generateTextExerciseForExam(exerciseGroup2);
        TextExercise exercise3_1 = ModelFactory.generateTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_2 = ModelFactory.generateTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_3 = ModelFactory.generateTextExerciseForExam(exerciseGroup3);
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
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.FORBIDDEN);

        // Should fail with too few exercise groups
        orderedExerciseGroups.remove(3);
        orderedExerciseGroups.remove(2);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.FORBIDDEN);

        // Should fail with different exercise group
        orderedExerciseGroups = new ArrayList<>();
        orderedExerciseGroups.add(exerciseGroup2);
        orderedExerciseGroups.add(exerciseGroup3);
        orderedExerciseGroups.add(ModelFactory.generateExerciseGroup(true, exam));
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void lockAllRepositories_noInstructor() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = database.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/lock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void lockAllRepositories() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = database.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);

        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        exerciseGroup1.addExercise(programmingExercise);

        ProgrammingExercise programmingExercise2 = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
        exerciseGroup1.addExercise(programmingExercise2);

        exerciseGroupRepository.save(exerciseGroup1);

        Integer numOfLockedExercises = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/lock-all-repositories",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numOfLockedExercises).isEqualTo(2);

        verify(programmingExerciseScheduleService, times(1)).lockAllStudentRepositories(programmingExercise);
        verify(programmingExerciseScheduleService, times(1)).lockAllStudentRepositories(programmingExercise2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void unlockAllRepositories_preAuthNoInstructor() throws Exception {
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = database.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/unlock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void unlockAllRepositories() throws Exception {
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        assertThat(studentExamRepository.findStudentExam(new ProgrammingExercise(), null)).isEmpty();

        ExerciseGroup exerciseGroup1 = new ExerciseGroup();

        Exam exam = database.addExam(course1);
        exam.addExerciseGroup(exerciseGroup1);
        exam = examRepository.save(exam);

        exam = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = exam.getExerciseGroups().get(0);

        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        exerciseGroup1.addExercise(programmingExercise);

        ProgrammingExercise programmingExercise2 = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
        exerciseGroup1.addExercise(programmingExercise2);

        exerciseGroupRepository.save(exerciseGroup1);

        User student1 = database.getUserByLogin("student1");
        User student2 = database.getUserByLogin("student2");
        var studentExam1 = database.addStudentExamWithUser(exam, student1, 10);
        studentExam1.setExercises(List.of(programmingExercise, programmingExercise2));
        var studentExam2 = database.addStudentExamWithUser(exam, student2, 0);
        studentExam2.setExercises(List.of(programmingExercise, programmingExercise2));
        studentExamRepository.saveAll(Set.of(studentExam1, studentExam2));

        var participationExSt1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        var participationExSt2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");

        var participationEx2St1 = database.addStudentParticipationForProgrammingExercise(programmingExercise2, "student1");
        var participationEx2St2 = database.addStudentParticipationForProgrammingExercise(programmingExercise2, "student2");

        assertThat(studentExamRepository.findStudentExam(programmingExercise, participationExSt1)).contains(studentExam1);
        assertThat(studentExamRepository.findStudentExam(programmingExercise, participationExSt2)).contains(studentExam2);
        assertThat(studentExamRepository.findStudentExam(programmingExercise2, participationEx2St1)).contains(studentExam1);
        assertThat(studentExamRepository.findStudentExam(programmingExercise2, participationEx2St2)).contains(studentExam2);

        mockConfigureRepository(programmingExercise, "student1", Set.of(student1), true);
        mockConfigureRepository(programmingExercise, "student2", Set.of(student2), true);
        mockConfigureRepository(programmingExercise2, "student1", Set.of(student1), true);
        mockConfigureRepository(programmingExercise2, "student2", Set.of(student2), true);

        Integer numOfUnlockedExercises = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/unlock-all-repositories",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numOfUnlockedExercises).isEqualTo(2);

        verify(programmingExerciseScheduleService, times(1)).unlockAllStudentRepositories(programmingExercise);
        verify(programmingExerciseScheduleService, times(1)).unlockAllStudentRepositories(programmingExercise2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExamForExamAssessmentDashboard() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        // we need an exam from the past, otherwise the tutor won't have access
        Course course = database.createCourseWithExamAndExerciseGroupAndExercises(user, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        Exam receivedExam = request.get("/api/courses/" + course.getId() + "/exams/" + course.getExams().iterator().next().getId() + "/exam-for-assessment-dashboard",
                HttpStatus.OK, Exam.class);

        // Test that the received exam has two text exercises
        assertThat(receivedExam.getExerciseGroups().get(0).getExercises()).as("Two exercises are returned").hasSize(2);
        // Test that the received exam has zero quiz exercises, because quiz exercises do not need to be corrected manually
        assertThat(receivedExam.getExerciseGroups().get(1).getExercises()).as("Zero exercises are returned").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExamForExamAssessmentDashboard_beforeDueDate() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        Course course = database.createCourseWithExamAndExerciseGroupAndExercises(user);
        Exam exam = course.getExams().iterator().next();
        exam.setEndDate(now().plusWeeks(1));
        examRepository.save(exam);

        request.get("/api/courses/" + course.getId() + "/exams/" + course.getExams().iterator().next().getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN,
                Exam.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetExamForExamAssessmentDashboard_asStudent_forbidden() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        Course course = database.createCourseWithExamAndExerciseGroupAndExercises(user);
        request.get("/api/courses/" + course.getId() + "/exams/" + course.getExams().iterator().next().getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN,
                Course.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamForExamAssessmentDashboard_courseIdDoesNotMatch_badRequest() throws Exception {
        request.get("/api/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-assessment-dashboard", HttpStatus.BAD_REQUEST, Course.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExamForExamAssessmentDashboard_notFound() throws Exception {
        request.get("/api/courses/1/exams/1/exam-for-assessment-dashboard", HttpStatus.NOT_FOUND, Course.class);
    }

    @Test
    @WithMockUser(username = "tutor6", roles = "TA")
    public void testGetExamForExamDashboard_NotTAOfCourse_forbidden() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        Course course = database.createCourseWithExamAndExerciseGroupAndExercises(user);
        Exam exam = course.getExams().iterator().next();
        exam.setEndDate(now().plusWeeks(1));
        examRepository.save(exam);

        request.get("/api/courses/" + course.getId() + "/exams/" + course.getExams().iterator().next().getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN,
                Course.class);
    }

    @Test
    @WithMockUser(username = "tutor6", roles = "TA")
    public void testGetExamScore_tutorNotInCourse_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExamScore_tutor_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamScore() throws Exception {

        // TODO avoid duplicated code with StudentExamIntegrationTest

        var examVisibleDate = now().minusMinutes(5);
        var examStartDate = now().plusMinutes(5);
        var examEndDate = now().plusMinutes(20);

        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);

        // TODO: it would be nice if we can support programming exercises here as well
        exam = database.addExerciseGroupsAndExercisesToExam(exam, false);

        // register users. Instructors are ignored from scores as they are exclusive for test run exercises
        Set<User> registeredStudents = users.stream().filter(user -> !user.getLogin().contains("instructor") && !user.getLogin().contains("admin")).collect(Collectors.toSet());
        exam.setRegisteredUsers(registeredStudents);
        exam.setNumberOfExercisesInExam(exam.getExerciseGroups().size());
        exam.setRandomizeExerciseOrder(false);
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam = examRepository.save(exam);
        exam = examRepository.findWithRegisteredUsersAndExerciseGroupsAndExercisesById(exam.getId()).get();

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getRegisteredUsers().size());

        assertThat(studentExamRepository.findAll()).hasSize(registeredStudents.size() + 1); // We create one studentExam in the before Method

        // start exercises

        Integer noGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);
        assertThat(noGeneratedParticipations).isEqualTo(registeredStudents.size() * exam.getExerciseGroups().size());

        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        database.changeUser("instructor1");

        // instructor exam checklist checks
        ExamChecklistDTO examChecklistDTO = examService.getStatsForChecklist(exam, true);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isEqualTo(15L);
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isTrue();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isZero();

        // check that an adapted version is computed for tutors
        database.changeUser("tutor1");

        examChecklistDTO = examService.getStatsForChecklist(exam, false);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isNull();
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isFalse();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isZero();

        database.changeUser("instructor1");

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
            List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdWithEagerLegalSubmissionsResult(exercise.getId());
            exercise.setStudentParticipations(new HashSet<>(participations));
            participationCounter += exercise.getStudentParticipations().size();
        }
        assertEquals(participationCounter, noGeneratedParticipations);

        // Scores used for all exercise results
        Double correctionResultScore = 60D;
        Double resultScore = 75D;

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
                // Create results
                var firstResult = new Result().score(correctionResultScore).rated(true).completionDate(now().minusMinutes(5));
                firstResult.setParticipation(participation);
                firstResult.setAssessor(instructor);
                firstResult = resultRepository.save(firstResult);
                firstResult.setSubmission(submission);
                submission.addResult(firstResult);

                var correctionResult = new Result().score(resultScore).rated(true).completionDate(now().minusMinutes(5));
                correctionResult.setParticipation(participation);
                correctionResult.setAssessor(instructor);
                correctionResult = resultRepository.save(correctionResult);
                correctionResult.setSubmission(submission);
                submission.addResult(correctionResult);

                submission.submitted(true);
                submission.setSubmissionDate(now().minusMinutes(6));
                submissionRepository.save(submission);
            }
        }
        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        database.changeUser("instructor1");
        final var exerciseWithNoUsers = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseWithNoUsers.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        exerciseRepo.save(exerciseWithNoUsers);

        GradingScale gradingScale = new GradingScale();
        gradingScale.setExam(exam);
        gradingScale.setGradeType(GradeType.GRADE);
        gradingScale.setGradeSteps(database.generateGradeStepSet(gradingScale, true));
        gradingScaleRepository.save(gradingScale);

        var response = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/scores", HttpStatus.OK, ExamScoresDTO.class);

        // Compare generated results to data in ExamScoresDTO
        // Compare top-level DTO properties
        assertThat(response.maxPoints).isEqualTo(exam.getMaxPoints());
        assertThat(response.hasSecondCorrectionAndStarted).isTrue();

        // For calculation assume that all exercises within an exerciseGroups have the same max points
        double calculatedAverageScore = 0.0;
        for (var exerciseGroup : exam.getExerciseGroups()) {
            var exercise = exerciseGroup.getExercises().stream().findAny().get();
            if (exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                continue;
            }
            calculatedAverageScore += Math.round(exercise.getMaxPoints() * resultScore / 100.00 * 10) / 10.0;
        }

        assertThat(response.averagePointsAchieved).isEqualTo(calculatedAverageScore);
        assertThat(response.title).isEqualTo(exam.getTitle());
        assertThat(response.examId).isEqualTo(exam.getId());

        // Ensure that all exerciseGroups of the exam are present in the DTO
        List<Long> exerciseGroupIdsInDTO = response.exerciseGroups.stream().map(exerciseGroup -> exerciseGroup.id).collect(Collectors.toList());
        List<Long> exerciseGroupIdsInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getId).collect(Collectors.toList());
        assertThat(exerciseGroupIdsInExam).containsExactlyInAnyOrderElementsOf(exerciseGroupIdsInDTO);

        // Compare exerciseGroups in DTO to exam exerciseGroups
        // Tolerated absolute difference for floating-point number comparisons
        double EPSILON = 0000.1;
        for (var exerciseGroupDTO : response.exerciseGroups) {
            // Find the original exerciseGroup of the exam using the id in ExerciseGroupId
            ExerciseGroup originalExerciseGroup = exam.getExerciseGroups().stream().filter(exerciseGroup -> exerciseGroup.getId().equals(exerciseGroupDTO.id)).findFirst().get();

            // Assume that all exercises in a group have the same max score
            Double groupMaxScoreFromExam = originalExerciseGroup.getExercises().stream().findAny().get().getMaxPoints();
            assertThat(exerciseGroupDTO.maxPoints).isEqualTo(originalExerciseGroup.getExercises().stream().findAny().get().getMaxPoints());
            assertEquals(exerciseGroupDTO.maxPoints, groupMaxScoreFromExam, EPSILON);

            // Compare exercise information
            long noOfExerciseGroupParticipations = 0;
            for (var originalExercise : originalExerciseGroup.getExercises()) {
                // Find the corresponding ExerciseInfo object
                var exerciseDTO = exerciseGroupDTO.containedExercises.stream().filter(exerciseInfo -> exerciseInfo.exerciseId.equals(originalExercise.getId())).findFirst().get();
                // Check the exercise title
                assertThat(originalExercise.getTitle()).isEqualTo(exerciseDTO.title);
                // Check the max points of the exercise
                assertThat(originalExercise.getMaxPoints()).isEqualTo(exerciseDTO.maxPoints);
                // Check the number of exercise participants and update the group participant counter
                var noOfExerciseParticipations = originalExercise.getStudentParticipations().size();
                noOfExerciseGroupParticipations += noOfExerciseParticipations;
                assertThat(Long.valueOf(originalExercise.getStudentParticipations().size())).isEqualTo(exerciseDTO.numberOfParticipants);
            }
            assertThat(noOfExerciseGroupParticipations).isEqualTo(exerciseGroupDTO.numberOfParticipants);
        }

        // Ensure that all registered students have a StudentResult
        List<Long> studentIdsWithStudentResults = response.studentResults.stream().map(studentResult -> studentResult.userId).collect(Collectors.toList());
        List<Long> registeredUsersIds = exam.getRegisteredUsers().stream().map(DomainObject::getId).collect(Collectors.toList());
        assertThat(studentIdsWithStudentResults).containsExactlyInAnyOrderElementsOf(registeredUsersIds);

        // Compare StudentResult with the generated results
        for (var studentResult : response.studentResults) {
            // Find the original user using the id in StudentResult
            User originalUser = exam.getRegisteredUsers().stream().filter(users -> users.getId().equals(studentResult.userId)).findFirst().get();
            StudentExam studentExamOfUser = studentExams.stream().filter(studentExam -> studentExam.getUser().equals(originalUser)).findFirst().get();

            assertThat(studentResult.name).isEqualTo(originalUser.getName());
            assertThat(studentResult.eMail).isEqualTo(originalUser.getEmail());
            assertThat(studentResult.login).isEqualTo(originalUser.getLogin());
            assertThat(studentResult.registrationNumber).isEqualTo(originalUser.getRegistrationNumber());

            // Calculate overall points achieved

            var calculatedOverallPoints = calculateOverallPoints(resultScore, studentExamOfUser);

            assertEquals(studentResult.overallPointsAchieved, calculatedOverallPoints, EPSILON);
            assertEquals(studentResult.overallPointsAchievedInFirstCorrection, calculateOverallPoints(correctionResultScore, studentExamOfUser), EPSILON);

            // Calculate overall score achieved
            var calculatedOverallScore = calculatedOverallPoints / response.maxPoints * 100;
            assertEquals(studentResult.overallScoreAchieved, calculatedOverallScore, EPSILON);

            assertThat(studentResult.overallGrade).isNotNull();
            assertThat(studentResult.hasPassed).isNotNull();

            // Ensure that the exercise ids of the student exam are the same as the exercise ids in the students exercise results
            List<Long> exerciseIdsOfStudentResult = studentResult.exerciseGroupIdToExerciseResult.values().stream().map(exerciseResult -> exerciseResult.exerciseId)
                    .collect(Collectors.toList());
            List<Long> exerciseIdsInStudentExam = studentExamOfUser.getExercises().stream().map(DomainObject::getId).collect(Collectors.toList());
            assertThat(exerciseIdsOfStudentResult).containsExactlyInAnyOrderElementsOf(exerciseIdsInStudentExam);
            for (Map.Entry<Long, ExamScoresDTO.ExerciseResult> entry : studentResult.exerciseGroupIdToExerciseResult.entrySet()) {
                var exerciseResult = entry.getValue();

                // Find the original exercise using the id in ExerciseResult
                Exercise originalExercise = studentExamOfUser.getExercises().stream().filter(exercise -> exercise.getId().equals(exerciseResult.exerciseId)).findFirst().get();

                // Check that the key is associated with the exerciseGroup which actually contains the exercise in the exerciseResult
                assertThat(originalExercise.getExerciseGroup().getId()).isEqualTo(entry.getKey());

                assertThat(exerciseResult.title).isEqualTo(originalExercise.getTitle());
                assertThat(exerciseResult.maxScore).isEqualTo(originalExercise.getMaxPoints());
                assertThat(exerciseResult.achievedScore).isEqualTo(resultScore);
                assertEquals(exerciseResult.achievedPoints, originalExercise.getMaxPoints() * resultScore / 100, EPSILON);
            }
        }

        // change back to instructor user
        database.changeUser("instructor1");

        // check if stats are set correctly for the instructor
        examChecklistDTO = examService.getStatsForChecklist(exam, true);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isEqualTo(15);
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isEqualTo(15);
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isEqualTo(15);
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isTrue();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isEqualTo(75);
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isZero();
        assertThat(examChecklistDTO.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound()).hasSize(2).containsAll((Collections.singletonList(90L)));

        // change to a tutor
        database.changeUser("tutor1");

        // check that a modified version is returned
        // check if stats are set correctly for the instructor
        examChecklistDTO = examService.getStatsForChecklist(exam, false);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isNull();
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isNull();
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isNull();
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isFalse();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isEqualTo(75);
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isNull();
        assertThat(examChecklistDTO.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound()).hasSize(2).containsExactly(90L, 90L);

        // change back to instructor user
        database.changeUser("instructor1");

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    private double calculateOverallPoints(Double correctionResultScore, StudentExam studentExamOfUser) {
        return studentExamOfUser.getExercises().stream().filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED))
                .map(Exercise::getMaxPoints).reduce(0.0, (total, maxScore) -> (Math.round((total + maxScore * correctionResultScore / 100) * 10) / 10.0));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamStatistics() throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testLatestExamEndDate() throws Exception {
        // Setup exam and user
        User user = userRepo.findOneByLogin("student1").get();

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
        assertThat(examInfo.getLatestIndividualEndDate()).isEqualToIgnoringNanos(exam2.getEndDate());

        // Set student exam with working time and save
        studentExam.setWorkingTime(3600);
        studentExamRepository.save(studentExam);

        // Get the latest exam end date DTO from server -> This returns the startDate + workingTime
        ExamInformationDTO examInfo2 = request.get("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/latest-end-date", HttpStatus.OK,
                ExamInformationDTO.class);
        // Check that latest end date is equal to startDate + workingTime
        assertThat(examInfo2.getLatestIndividualEndDate()).isEqualToIgnoringNanos(exam2.getStartDate().plusHours(1));
    }

    @Test
    @WithMockUser(username = "instructor6", roles = "INSTRUCTOR")
    public void testCourseAndExamAccessForInstructors_notInstructorInCourse_forbidden() throws Exception {
        // Instructor6 is not instructor for the course
        // Update exam
        request.put("/api/courses/" + course1.getId() + "/exams", exam1, HttpStatus.FORBIDDEN);
        // Get exam
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN, Exam.class);
        // Add student to exam
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/student1", null, HttpStatus.FORBIDDEN);
        // Generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
        // Generate missing exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-missing-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
        // Start exercises
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/start-exercises", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Unlock all repositories
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/unlock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Lock all repositories
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/lock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Add students to exam
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", Collections.singletonList(new StudentDTO()), HttpStatus.FORBIDDEN);
        // Delete student from exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/student1", HttpStatus.FORBIDDEN);
        // Update order of exerciseGroups
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups-order", new ArrayList<ExerciseGroup>(), HttpStatus.FORBIDDEN);
        // Get the latest individual end date
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/latest-end-date", HttpStatus.FORBIDDEN, ExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testLatestIndividualEndDate_noStudentExams() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        final var exam = examRepository.save(exam1);
        final var latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(exam.getId());
        assertThat(latestIndividualExamEndDate.isEqual(exam.getEndDate())).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllIndividualExamEndDates() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        final var exam = examRepository.save(exam1);

        final var studentExam1 = new StudentExam();
        studentExam1.setExam(exam);
        studentExam1.setUser(users.get(0));
        studentExam1.setWorkingTime(120);
        studentExam1.setTestRun(false);
        studentExamRepository.save(studentExam1);

        final var studentExam2 = new StudentExam();
        studentExam2.setExam(exam);
        studentExam2.setUser(users.get(0));
        studentExam2.setWorkingTime(120);
        studentExam2.setTestRun(false);
        studentExamRepository.save(studentExam2);

        final var studentExam3 = new StudentExam();
        studentExam3.setExam(exam);
        studentExam3.setUser(users.get(0));
        studentExam3.setWorkingTime(60);
        studentExam3.setTestRun(false);
        studentExamRepository.save(studentExam3);

        final var individualWorkingTimes = examDateService.getAllIndividualExamEndDates(exam.getId());
        assertThat(individualWorkingTimes).hasSize(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testIsExamOver_GracePeriod() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        exam1.setGracePeriod(180);
        final var exam = examRepository.save(exam1);
        final var isOver = examDateService.isExamWithGracePeriodOver(exam.getId());
        assertThat(isOver).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testIsUserRegisteredForExam() {
        exam1.addRegisteredUser(users.get(0));
        final var exam = examRepository.save(exam1);
        final var isUserRegistered = examRegistrationService.isUserRegisteredForExam(exam.getId(), users.get(0).getId());
        final var isCurrentUserRegistered = examRegistrationService.isCurrentUserRegisteredForExam(exam.getId());
        assertThat(isUserRegistered).isTrue();
        assertThat(isCurrentUserRegistered).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRegisterInstructorToExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/instructor1", null, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testArchiveCourseWithExam() throws Exception {
        Course course = database.createCourseWithExamAndExercises();
        course.setEndDate(now().minusMinutes(5));
        course = courseRepo.save(course);

        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);

        final var courseId = course.getId();
        await().until(() -> courseRepo.findById(courseId).get().getCourseArchivePath() != null);

        var updatedCourse = courseRepo.findById(courseId).get();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testArchiveExamAsInstructor() throws Exception {
        var course = database.createCourseWithExamAndExercises();
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().get();

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.OK);

        final var examId = exam.getId();
        await().until(() -> examRepository.findById(examId).get().getExamArchivePath() != null);

        var updatedExam = examRepository.findById(examId).get();
        assertThat(updatedExam.getExamArchivePath()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testArchiveExamAsStudent_forbidden() throws Exception {
        Course course = database.addEmptyCourse();
        course.setEndDate(now().minusMinutes(5));
        course = courseRepo.save(course);

        Exam exam = database.addExam(course);
        exam = examRepository.save(exam);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testArchiveExamBeforeEndDate_badRequest() throws Exception {
        Course course = database.addEmptyCourse();
        course.setEndDate(now().plusMinutes(5));
        course = courseRepo.save(course);

        Exam exam = database.addExam(course);
        exam = examRepository.save(exam);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDownloadExamArchiveAsStudent_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDownloadExamArchiveAsTutor_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDownloadExamArchiveAsInstructor_not_found() throws Exception {
        // Create an exam with no archive
        Course course = database.createCourse();
        course = courseRepo.save(course);

        // Return not found if the exam doesn't exist
        var downloadedArchive = request.get("/api/courses/" + course.getId() + "/exams/" + 12 + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();

        // Returns not found if there is no archive
        var exam = database.addExam(course);
        downloadedArchive = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDownloadExamArchiveAsInstructorNotInCourse_forbidden() throws Exception {
        // Create an exam with no archive
        Course course = database.createCourse();
        course.setInstructorGroupName("some-group");
        course = courseRepo.save(course);
        var exam = database.addExam(course);

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDownloadExamArchiveAsInstructor() throws Exception {
        testArchiveExamAsInstructor();

        // Download the archive
        var courses = courseRepo.findAll();
        var course = courses.get(courses.size() - 1);
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

        var submissions = submissionRepository.findAll();

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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStatsForExamAssessmentDashboardOneCorrectionRound() throws Exception {
        testGetStatsForExamAssessmentDashboard(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStatsForExamAssessmentDashboardTwoCorrectionRounds() throws Exception {
        testGetStatsForExamAssessmentDashboard(2);
    }

    public void testGetStatsForExamAssessmentDashboard(int numberOfCorrectionRounds) throws Exception {
        User examTutor1 = userRepo.findOneByLogin("tutor1").get();
        User examTutor2 = userRepo.findOneByLogin("tutor2").get();

        var examVisibleDate = now().minusMinutes(5);
        var examStartDate = now().plusMinutes(5);
        var examEndDate = now().plusMinutes(20);
        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRounds);
        exam = examRepository.save(exam);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, false);

        var stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfSubmissions()).isInstanceOf(DueDateStat.class);
        assertThat(stats.getTutorLeaderboardEntries()).isInstanceOf(List.class);
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).isInstanceOf(DueDateStat[].class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        assertThat(stats.getNumberOfSubmissions().inTime()).isZero();
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        var lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();

        // register users. Instructors are ignored from scores as they are exclusive for test run exercises
        Set<User> registeredStudents = users.stream().filter(user -> !user.getLogin().contains("instructor") && !user.getLogin().contains("admin")).collect(Collectors.toSet());
        exam.setRegisteredUsers(registeredStudents);
        exam.setNumberOfExercisesInExam(exam.getExerciseGroups().size());
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);
        exam = examRepository.findWithRegisteredUsersAndExerciseGroupsAndExercisesById(exam.getId()).get();

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        Integer noGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

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
        List<Exercise> exercisesInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toList());
        for (var exercise : exercisesInExam) {
            List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdWithEagerLegalSubmissionsResult(exercise.getId());
            exercise.setStudentParticipations(new HashSet<>(participations));
            participationCounter += exercise.getStudentParticipations().size();
        }
        assertEquals(participationCounter, noGeneratedParticipations);

        // Assign submissions to the participations
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
                submission.submitted(true);
                submission.setSubmissionDate(now().minusMinutes(6));
                submissionRepository.save(submission);
            }
        }

        // check the stats again - check the count of submitted submissions
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(75L);
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        // Score used for all exercise results
        Double resultScore = 75.0;

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
        // check the stats again
        database.changeUser("tutor1");
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isEqualTo(75L);
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(75L);
        // the 15 quiz submissions are already assessed
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(15L);
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isEqualTo(75L);

        // test the query needed for assessment information
        database.changeUser("tutor2");
        exam.getExerciseGroups().forEach(group -> {
            var locks = group.getExercises().stream().map(
                    exercise -> resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds, examTutor2)[0]
                            .inTime())
                    .reduce(Long::sum).get();
            if (group.getExercises().stream().anyMatch(exercise -> !(exercise instanceof QuizExercise)))
                assertThat(locks).isEqualTo(15L);
        });

        database.changeUser("instructor1");
        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).hasSize(75);

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
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(75L);
        // 75 + the 15 quiz submissions
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(90L);
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();
        if (numberOfCorrectionRounds == 2) {
            lockAndAssessForSecondCorrection(exam, course, exercisesInExam, numberOfCorrectionRounds);
        }
    }

    public void lockAndAssessForSecondCorrection(Exam exam, Course course, List<Exercise> exercisesInExam, int numberOfCorrectionRounds) throws Exception {
        // Lock all submissions
        User examInstructor = userRepo.findOneByLogin("instructor1").get();
        User examTutor2 = userRepo.findOneByLogin("tutor2").get();

        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
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
        database.changeUser("instructor1");
        var stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isEqualTo(75L);
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(75L);
        // the 15 quiz submissions are already assessed - and all are assessed in the first correctionRound
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(90L);
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[1].inTime()).isEqualTo(15L);
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isEqualTo(75L);

        // test the query needed for assessment information
        database.changeUser("tutor2");
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
                assertThat(locksRound2).isEqualTo(15L);
            }
        });

        database.changeUser("instructor1");
        var lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).hasSize(75);

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
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(75L);
        // 75 + the 15 quiz submissions
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(90L);
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExamsTemplateCombine() throws Exception {
        Exam examWithProgramming = database.addExerciseGroupsAndExercisesToExam(exam1, true);
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + examWithProgramming.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        verify(gitService, times(1)).combineAllCommitsOfRepositoryIntoOne(any());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExamTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetExamTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    private void testGetExamTitle() throws Exception {
        Course course = database.createCourse();
        Exam exam = ModelFactory.generateExam(course);
        exam.setTitle("Test Exam");
        exam = examRepository.save(exam);
        course.addExam(exam);
        courseRepo.save(course);

        final var title = request.get("/api/exams/" + exam.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(exam.getTitle());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetExamTitleForNonExistingExam() throws Exception {
        request.get("/api/exams/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExamMonitoringStatus() throws Exception {
        exam1.setMonitoring(true);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam1, Exam.class, HttpStatus.OK);

        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam1.getId() + "/update", true);

        exam1.setMonitoring(false);
        request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam1, Exam.class, HttpStatus.OK);

        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam1.getId() + "/update", false);
    }

    // ExamRegistration Service - checkRegistrationOrRegisterStudentToTestExam
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckRegistrationOrRegisterStudentToTestExam_noTestExam() {
        assertThatThrownBy(() -> examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, exam1.getId(), database.getUserByLogin("student1")))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void testCheckRegistrationOrRegisterStudentToTestExam_studentNotPartOfCourse() {
        assertThatThrownBy(() -> examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, exam1.getId(), database.getUserByLogin("student42")))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckRegistrationOrRegisterStudentToTestExam_successfulRegistration() {
        Exam testExam = ModelFactory.generateTestExam(course1);
        testExam.addRegisteredUser(users.get(0));
        testExam = examRepository.save(testExam);
        examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, testExam.getId(), users.get(0));
        Exam testExamReladed = examRepository.findByIdWithRegisteredUsersElseThrow(testExam.getId());
        assertTrue(testExamReladed.getRegisteredUsers().contains(users.get(0)));
    }

    // ExamResource - getStudentExamForTestExamForStart
    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void testGetStudentExamForTestExamForStart_notRegisteredInCourse() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/start", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExamForStart_notVisible() throws Exception {
        testExam1.setVisibleDate(now().plusMinutes(60));
        testExam1 = examRepository.save(testExam1);

        request.get("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/start", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExamForStart_ExamDoesNotBelongToCourse() throws Exception {
        Exam testExam = database.addTestExam(course2);

        request.get("/api/courses/" + course1.getId() + "/exams/" + testExam.getId() + "/start", HttpStatus.CONFLICT, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExamForStart_fetchExam() throws Exception {
        StudentExam studentExamReceived = request.get("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/start", HttpStatus.OK, StudentExam.class);
        assertEquals(studentExam1, studentExamReceived);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStudentExamForTestExamForStart_successful() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/start", HttpStatus.OK, StudentExam.class);
    }

}
