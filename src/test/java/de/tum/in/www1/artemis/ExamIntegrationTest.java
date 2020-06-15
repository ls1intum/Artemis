package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
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

    private List<User> users;

    private Course course1;

    private Course course2;

    private Exam exam1;

    @BeforeEach
    public void initTestCase() throws URISyntaxException {
        users = database.addUsers(4, 5, 1);
        course1 = database.addEmptyCourse();
        course2 = database.addEmptyCourse();
        exam1 = database.addExam(course1);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void registerUsersInExam() throws Exception {

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
    public void testSaveExamToDatabase() {

        // create exercise
        TextExercise savedTextExercise1 = textExerciseRepository.save(new TextExercise());
        TextExercise savedTextExercise2 = textExerciseRepository.save(new TextExercise());
        TextExercise savedTextExercise3 = textExerciseRepository.save(new TextExercise());

        // create ExamGroup
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle("Exercise Group Title");
        exerciseGroup.setIsMandatory(true);
        exerciseGroup.addExercise(savedTextExercise1);
        exerciseGroup.addExercise(savedTextExercise2);
        exerciseGroup.addExercise(savedTextExercise3);

        ExerciseGroup savedExerciseGroup1 = exerciseGroupRepository.save(exerciseGroup);
        ExerciseGroup savedExerciseGroup2 = exerciseGroupRepository.save(exerciseGroup);
        ExerciseGroup savedExerciseGroup3 = exerciseGroupRepository.save(exerciseGroup);

        // assert savedExerciseGroup to equal exerciseGroup
        assertThat(savedExerciseGroup1.getTitle()).isEqualTo(exerciseGroup.getTitle());
        assertThat(savedExerciseGroup1.getIsMandatory()).isEqualTo(exerciseGroup.getIsMandatory());
        assertThat(savedExerciseGroup1.getExam()).isEqualTo(exerciseGroup.getExam());
        assertThat(savedExerciseGroup1.getExercises()).isEqualTo(exerciseGroup.getExercises());

        // create exam
        Exam exam = createExam();
        exam.addExerciseGroup(savedExerciseGroup1);
        exam.addExerciseGroup(savedExerciseGroup2);
        exam.addExerciseGroup(savedExerciseGroup3);
        Exam savedExam = examRepository.save(exam);

        exerciseGroupRepository.save(savedExerciseGroup1);
        exerciseGroupRepository.save(savedExerciseGroup2);
        exerciseGroupRepository.save(savedExerciseGroup3);

        // assert savedExam equals exam
        assertThat(savedExam.getTitle()).isEqualTo(exam.getTitle());
        assertThat(savedExam.getVisibleDate()).isEqualTo(exam.getVisibleDate());
        assertThat(savedExam.getStartDate()).isEqualTo(exam.getStartDate());
        assertThat(savedExam.getEndDate()).isEqualTo(exam.getEndDate());
        assertThat(savedExam.getStartText()).isEqualTo(exam.getStartText());
        assertThat(savedExam.getEndText()).isEqualTo(exam.getEndText());
        assertThat(savedExam.getConfirmationStartText()).isEqualTo(exam.getConfirmationStartText());
        assertThat(savedExam.getConfirmationEndText()).isEqualTo(exam.getConfirmationEndText());
        assertThat(savedExam.getMaxPoints()).isEqualTo(exam.getMaxPoints());
        assertThat(savedExam.getNumberOfExercisesInExam()).isEqualTo(exam.getNumberOfExercisesInExam());
        assertThat(savedExam.getRandomizeExerciseOrder()).isEqualTo(exam.getRandomizeExerciseOrder());
        assertThat(savedExam.getCourse()).isEqualTo(exam.getCourse());
        assertThat(savedExam.getExerciseGroups()).isEqualTo(exam.getExerciseGroups());

        assertThat(savedExam.getExerciseGroups().get(0)).isEqualTo(exam.getExerciseGroups().get(0));
        assertThat(savedExam.getExerciseGroups().get(1)).isEqualTo(exam.getExerciseGroups().get(1));
        assertThat(savedExam.getExerciseGroups().get(2)).isEqualTo(exam.getExerciseGroups().get(2));

        ExerciseGroup updatedExerciseGroup = exerciseGroupRepository.findByIdWithEagerExam(savedExerciseGroup1.getId()).get();
        assertThat(updatedExerciseGroup.getExam()).isEqualTo(savedExam);

        // create studentExam
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(savedExam);
        studentExam.setUser(users.get(0));
        studentExam.addExercise(savedTextExercise1);
        studentExam.addExercise(savedTextExercise2);
        studentExam.addExercise(savedTextExercise3);

        StudentExam savedStudentExam = studentExamRepository.save(studentExam);

        // assert savedStudentExam to equal studentExam
        assertThat(savedStudentExam.getExam()).isEqualTo(studentExam.getExam());
        assertThat(savedStudentExam.getUser()).isEqualTo(studentExam.getUser());
        assertThat(savedStudentExam.getExercises()).isEqualTo(studentExam.getExercises());

        assertThat(savedStudentExam.getExercises().get(0)).isEqualTo(studentExam.getExercises().get(0));
        assertThat(savedStudentExam.getExercises().get(1)).isEqualTo(studentExam.getExercises().get(1));
        assertThat(savedStudentExam.getExercises().get(2)).isEqualTo(studentExam.getExercises().get(2));
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
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateExam_asInstructor() throws Exception {
        Exam exam = ModelFactory.generateExam(course1);
        exam.setId(55L);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        exam = ModelFactory.generateExam(course1);
        exam.setCourse(null);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.CONFLICT);
        exam = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course2.getId() + "/exams", exam, HttpStatus.CONFLICT);
        exam = ModelFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.CREATED);
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
    public void testDeleteExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAccess(course1.getId(), exam1.getId());
    }
}
