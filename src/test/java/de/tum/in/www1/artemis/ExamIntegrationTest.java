package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final int numberOfStudents = 4;

    private final int numberOfTutors = 5;

    private final int numberOfInstructors = 1;

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

    private List<User> users;

    private Course course;

    @BeforeEach
    public void initTestCase() throws URISyntaxException {
        users = database.addUsers(numberOfStudents, numberOfTutors, numberOfInstructors);
        course = database.addEmptyCourse();
        jiraRequestMockProvider.enableMockingOfRequests();
        jiraRequestMockProvider.mockAddUserToGroup(Set.of(course.getStudentGroupName()));
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void registerUsersInExam() throws Exception {
        var exam = createExam();
        var savedExam = examRepository.save(exam);
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        var student3 = database.getUserByLogin("student3");
        var registrationNumber1 = "1234567";
        var registrationNumber2 = "2345678";
        var registrationNumber3 = "3456789";
        var registrationNumber6 = "9876543";
        student1.setRegistrationNumber(registrationNumber1);
        student2.setRegistrationNumber(registrationNumber2);
        student3.setRegistrationNumber(registrationNumber3);
        student1 = userRepo.save(student1);
        student2 = userRepo.save(student2);
        student3 = userRepo.save(student3);

        var student6 = ModelFactory.generateActivatedUser("student6");     // not registered for the course
        student6.setRegistrationNumber(registrationNumber6);
        student6 = userRepo.save(student6);
        student6 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student6").get();
        assertThat(student6.getGroups()).doesNotContain(course.getStudentGroupName());

        request.postWithoutLocation("/api/courses/" + course.getId() + "/exams/" + savedExam.getId() + "/students/student1", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", null, HttpStatus.NOT_FOUND, null);

        Exam storedExam = examRepository.findWithRegisteredUsersById(savedExam.getId()).get();
        assertThat(storedExam.getRegisteredUsers()).containsExactly(student1);

        request.delete("/api/courses/" + course.getId() + "/exams/" + savedExam.getId() + "/students/student1", HttpStatus.OK);
        request.delete("/api/courses/" + course.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
        storedExam = examRepository.findWithRegisteredUsersById(savedExam.getId()).get();
        assertThat(storedExam.getRegisteredUsers()).isEmpty();

        var studentDto1 = new StudentDTO();
        studentDto1.setRegistrationNumber(registrationNumber1);
        var studentDto2 = new StudentDTO();
        studentDto2.setRegistrationNumber(registrationNumber2);
        var studentDto3 = new StudentDTO();
        studentDto3.setRegistrationNumber(registrationNumber3 + "0"); // explicit typo, should be a registration failure
        var studentDto6 = new StudentDTO();
        studentDto6.setRegistrationNumber(registrationNumber6);
        var studentsToRegister = List.of(studentDto1, studentDto2, studentDto3, studentDto6);

        List<StudentDTO> registrationFailures = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + savedExam.getId() + "/students", studentsToRegister,
                StudentDTO.class, HttpStatus.OK);
        assertThat(registrationFailures).containsExactlyInAnyOrder(studentDto3);
        storedExam = examRepository.findWithRegisteredUsersById(savedExam.getId()).get();
        assertThat(storedExam.getRegisteredUsers()).containsExactlyInAnyOrder(student1, student2, student6);

        for (var user : storedExam.getRegisteredUsers()) {
            // all registered users must have access to the course
            user = userRepo.findOneWithGroupsAndAuthoritiesByLogin(user.getLogin()).get();
            assertThat(user.getGroups()).contains(course.getStudentGroupName());
        }

        // TODO: also mock the LdapService to make sure students who are not yet in the Artemis database can be registered for an exam using a registration number
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
        exam.setCourse(course);
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
}
