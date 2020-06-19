package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ExamAccessService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    JiraRequestMockProvider jiraRequestMockProvider;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    StudentExamRepository studentExamRepository;

    @Autowired
    TextExerciseRepository textExerciseRepository;

    @SpyBean
    ExamAccessService examAccessService;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(4, 5, 1);
        course1 = database.addEmptyCourse();
        course2 = database.addEmptyCourse();
        exam1 = database.addExam(course1);
        exam2 = database.addExamWithExerciseGroup(course1, true);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRegisterUsersInExam() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        var exam = createExam();
        var savedExam = examRepository.save(exam);
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        var student3 = database.getUserByLogin("student3");
        var registrationNumber1 = "1111111";
        var registrationNumber2 = "1111112";
        var registrationNumber3 = "1111113";
        var registrationNumber3WithTypo = registrationNumber3 + "0";
        var registrationNumber6 = "1111116";
        var registrationNumber7 = "1111117";
        student1.setRegistrationNumber(registrationNumber1);
        student2.setRegistrationNumber(registrationNumber2);
        student3.setRegistrationNumber(registrationNumber3);
        student1 = userRepo.save(student1);
        student2 = userRepo.save(student2);
        student3 = userRepo.save(student3);

        // mock the ldap service
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber3WithTypo);
        var ldapUser7Dto = new LdapUserDto().registrationNumber(registrationNumber7).firstName("Student7").lastName("Student7").username("student7").email("student7@tum.de");
        doReturn(Optional.of(ldapUser7Dto)).when(ldapUserService).findByRegistrationNumber(registrationNumber7);

        // first mocked call expected to add student 6 to course student
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName());  // expect once for student 6
        // second mocked call expected to create student 7
        jiraRequestMockProvider.mockCreateUserInExternalUserManagement(ldapUser7Dto.getUsername(), ldapUser7Dto.getFirstName() + " " + ldapUser7Dto.getLastName(),
                ldapUser7Dto.getEmail());
        // thirs mocked call expected to add student 7 to course student group
        jiraRequestMockProvider.mockAddUserToGroup(course1.getStudentGroupName());  // expect once for student 7

        var student6 = ModelFactory.generateActivatedUser("student6");     // not registered for the course
        student6.setRegistrationNumber(registrationNumber6);
        student6 = userRepo.save(student6);
        student6 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student6").get();
        assertThat(student6.getGroups()).doesNotContain(course1.getStudentGroupName());

        // Note: student7 is not yet a user of Artemis and should be retrieved from the LDAP

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
        var studentDto6 = new StudentDTO().registrationNumber(registrationNumber6);
        var studentDto7 = new StudentDTO().registrationNumber(registrationNumber7);
        var studentsToRegister = List.of(studentDto1, studentDto2, studentDto3, studentDto6, studentDto7);

        // now we register all these students for the exam.
        List<StudentDTO> registrationFailures = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students",
                studentsToRegister, StudentDTO.class, HttpStatus.OK);
        assertThat(registrationFailures).containsExactlyInAnyOrder(studentDto3);
        storedExam = examRepository.findWithRegisteredUsersById(savedExam.getId()).get();

        // now a new user student7 should exist
        var student7 = database.getUserByLogin("student7");

        assertThat(storedExam.getRegisteredUsers()).containsExactlyInAnyOrder(student1, student2, student6, student7);

        for (var user : storedExam.getRegisteredUsers()) {
            // all registered users must have access to the course
            user = userRepo.findOneWithGroupsAndAuthoritiesByLogin(user.getLogin()).get();
            assertThat(user.getGroups()).contains(course1.getStudentGroupName());
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerateStudentExams() throws Exception {
        var exam = createExam();
        exam.setNumberOfExercisesInExam(4);
        exam.setRandomizeExerciseOrder(true);
        exam = examRepository.save(exam);

        // add exercise groups: 3 mandatory, 2 optional
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(false, exam);
        ModelFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);

        // TODO: also add other exercise types

        // add exercises
        var exercise1a = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(0));
        var exercise1b = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(0));
        var exercise1c = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(0));
        exerciseRepo.saveAll(List.of(exercise1a, exercise1b, exercise1c));

        var exercise2a = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(1));
        var exercise2b = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(1));
        var exercise2c = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(1));
        exerciseRepo.saveAll(List.of(exercise2a, exercise2b, exercise2c));

        var exercise3a = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(2));
        var exercise3b = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(2));
        var exercise3c = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(2));
        exerciseRepo.saveAll(List.of(exercise3a, exercise3b, exercise3c));

        var exercise4a = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(3));
        var exercise4b = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(3));
        var exercise4c = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(3));
        exerciseRepo.saveAll(List.of(exercise4a, exercise4b, exercise4c));

        var exercise5a = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(4));
        var exercise5b = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(4));
        var exercise5c = ModelFactory.generateTextExerciseForExam(exam.getStartDate(), exam.getEndDate(), exam.getEndDate().plusWeeks(1), exam.getExerciseGroups().get(4));
        exerciseRepo.saveAll(List.of(exercise5a, exercise5b, exercise5c));

        // register user
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        var student3 = database.getUserByLogin("student3");
        var student4 = database.getUserByLogin("student4");
        var registeredUsers = Set.of(student1, student2, student3, student4);

        exam.setRegisteredUsers(registeredUsers);
        exam = examRepository.save(exam);

        // invoke generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(registeredUsers.size());

        for (var studentExam : studentExams) {
            assertThat(studentExam.getExercises()).hasSize(exam.getNumberOfExercisesInExam());
            assertThat(studentExam.getExam()).isEqualTo(exam);
            // TODO: check exercise configuration, each mandatory exercise group has to appear, one optional exercise should appear
        }
    }

    public Exam createExam() {
        ZonedDateTime currentTime = ZonedDateTime.now();
        Exam exam = new Exam();
        exam.setTitle("Test exam 1");
        exam.setVisibleDate(currentTime);
        exam.setStartDate(currentTime);
        exam.setEndDate(currentTime);
        exam.setStartText("Start Text");
        exam.setEndText("End Text");
        exam.setConfirmationStartText("Confirmation Start Text");
        exam.setConfirmationEndText("Confirmation End Text");
        exam.setMaxPoints(90);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setCourse(course1);
        return exam;
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
        request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.FORBIDDEN, Exam.class);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/student1", null, HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", Collections.singletonList(new StudentDTO()), HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/student1", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateExam_asInstructor() throws Exception {
        // Test for bad request when exam id is already set.
        Exam examA = ModelFactory.generateExam(course1);
        examA.setId(55L);
        request.post("/api/courses/" + course1.getId() + "/exams", examA, HttpStatus.BAD_REQUEST);
        // Test for conflict when course is null.
        Exam examB = ModelFactory.generateExam(course1);
        examB.setCourse(null);
        request.post("/api/courses/" + course1.getId() + "/exams", examB, HttpStatus.CONFLICT);
        // Test for conflict when course deviates from course specified in route.
        Exam examC = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course2.getId() + "/exams", examC, HttpStatus.CONFLICT);
        // Test for forbidden when user tries to create an exam with exercise groups.
        Exam examD = ModelFactory.generateExam(course1);
        examD.addExerciseGroup(ModelFactory.generateExerciseGroup(true, exam1));
        request.post("/api/courses/" + course1.getId() + "/exams", examD, HttpStatus.FORBIDDEN);
        // Test examAccessService.
        Exam examE = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", examE, HttpStatus.CREATED);
        verify(examAccessService, times(1)).checkCourseAccess(course1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateExam_asInstructor() throws Exception {
        Exam exam = ModelFactory.generateExam(course1);
        exam.setCourse(null);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.CONFLICT);
        exam = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course2.getId() + "/exams", exam, HttpStatus.CONFLICT);
        request.put("/api/courses/" + course1.getId() + "/exams", exam1, HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAccess(course1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExam_asInstructor() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK, Exam.class);
        verify(examAccessService, times(1)).checkCourseAndExamAccess(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamsForCourse_asInstructor() throws Exception {
        request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.OK, Exam.class);
        verify(examAccessService, times(1)).checkCourseAccess(course1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteEmptyExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccess(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        var now = ZonedDateTime.now();
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(now.minusDays(1), now.minusHours(2), now.minusHours(1), exam2.getExerciseGroups().get(0));
        exerciseRepo.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccess(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testDeleteExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/55", HttpStatus.NOT_FOUND);
    }
}
