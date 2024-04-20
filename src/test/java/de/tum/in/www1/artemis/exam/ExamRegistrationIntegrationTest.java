package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.exam.ExamRegistrationService;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

class ExamRegistrationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "examregistrationtest";

    public static final String STUDENT_111 = TEST_PREFIX + "student111";

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ExamRegistrationService examRegistrationService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ExamAccessService examAccessService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private Course course1;

    private Exam exam1;

    private Exam testExam1;

    private static final int NUMBER_OF_STUDENTS = 3;

    private static final int NUMBER_OF_TUTORS = 1;

    private User student1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);
        // Add a student that is not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42", passwordService.hashPassword(UserFactory.USER_PASSWORD));

        course1 = courseUtilService.addEmptyCourse();
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        exam1 = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam1, "exam1 channel");
        testExam1 = examUtilService.addTestExam(course1);
        examUtilService.addStudentExamForTestExam(testExam1, student1);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();
    }

    @AfterEach
    void tearDown() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
        participantScoreScheduleService.shutdown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterUserInExam_addedToCourseStudentsGroup() throws Exception {
        User student42 = userUtilService.getUserByLogin(TEST_PREFIX + "student42");

        Set<User> studentsInCourseBefore = userRepo.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course1.getStudentGroupName());
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student42", null, HttpStatus.OK, null);
        Set<User> studentsInCourseAfter = userRepo.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course1.getStudentGroupName());
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
        var savedExam = examUtilService.addExam(course1);

        List<String> registrationNumbers = Arrays.asList("1111111", "1111112", "1111113");
        List<User> students = userUtilService.setRegistrationNumberOfStudents(registrationNumbers, TEST_PREFIX);

        User student1 = students.get(0);
        User student2 = students.get(1);
        User student3 = students.get(2);

        var registrationNumber3WithTypo = "1111113" + "0";
        var registrationNumber4WithTypo = "1111115" + "1";
        var registrationNumber99 = "1111199";
        var registrationNumber111 = "1111100";
        var emptyRegistrationNumber = "";

        // mock the ldap service
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber3WithTypo);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(emptyRegistrationNumber);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber4WithTypo);

        var ldapUser111Dto = new LdapUserDto().registrationNumber(registrationNumber111).firstName(STUDENT_111).lastName(STUDENT_111).username(STUDENT_111)
                .email(STUDENT_111 + "@tum.de");
        doReturn(Optional.of(ldapUser111Dto)).when(ldapUserService).findByRegistrationNumber(registrationNumber111);

        userUtilService.createAndSaveUser("student99"); // not registered for the course
        userUtilService.setRegistrationNumberOfUserAndSave("student99", registrationNumber99);

        User student99 = userRepo.findOneWithGroupsAndAuthoritiesByLogin("student99").orElseThrow();
        assertThat(student99.getGroups()).doesNotContain(course1.getStudentGroupName());

        // Note: student111 is not yet a user of Artemis and should be retrieved from the LDAP
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/" + TEST_PREFIX + "student1", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", null, HttpStatus.NOT_FOUND, null);

        Exam storedExam = examRepository.findWithExamUsersById(savedExam.getId()).orElseThrow();
        ExamUser examUserStudent1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId()).orElseThrow();
        assertThat(storedExam.getExamUsers()).containsExactly(examUserStudent1);

        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.OK);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
        storedExam = examRepository.findWithExamUsersById(savedExam.getId()).orElseThrow();
        assertThat(storedExam.getExamUsers()).isEmpty();

        var studentDto1 = UserFactory.generateStudentDTOWithRegistrationNumber(student1.getRegistrationNumber());
        var studentDto2 = UserFactory.generateStudentDTOWithRegistrationNumber(student2.getRegistrationNumber());
        var studentDto3 = new StudentDTO(student3.getLogin(), null, null, registrationNumber3WithTypo, null); // explicit typo, should be a registration failure later
        var studentDto4 = UserFactory.generateStudentDTOWithRegistrationNumber(registrationNumber4WithTypo); // explicit typo, should fall back to login name later
        var studentDto10 = UserFactory.generateStudentDTOWithRegistrationNumber(null); // completely empty

        var studentDto99 = new StudentDTO(student99.getLogin(), null, null, registrationNumber99, null);
        var studentDto111 = new StudentDTO(null, null, null, registrationNumber111, null);

        // Add a student with login but empty registration number
        var studentsToRegister = List.of(studentDto1, studentDto2, studentDto3, studentDto4, studentDto99, studentDto111, studentDto10);

        // now we register all these students for the exam.
        List<StudentDTO> registrationFailures = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students",
                studentsToRegister, StudentDTO.class, HttpStatus.OK);
        // all students get registered if they can be found in the LDAP
        assertThat(registrationFailures).containsExactlyInAnyOrder(studentDto4, studentDto10);

        // TODO check audit events stored properly

        storedExam = examRepository.findWithExamUsersById(savedExam.getId()).orElseThrow();

        // now a new user student101 should exist
        var student111 = userUtilService.getUserByLogin(STUDENT_111);

        var examUser1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId()).orElseThrow();
        var examUser2 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student2.getId()).orElseThrow();
        var examUser3 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student3.getId()).orElseThrow();
        var examUser99 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student99.getId()).orElseThrow();
        var examUser111 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student111.getId()).orElseThrow();

        assertThat(storedExam.getExamUsers()).containsExactlyInAnyOrder(examUser1, examUser2, examUser3, examUser99, examUser111);

        for (var examUser : storedExam.getExamUsers()) {
            // all registered users must have access to the course
            var user = userRepo.findOneWithGroupsAndAuthoritiesByLogin(examUser.getUser().getLogin()).orElseThrow();
            assertThat(user.getGroups()).contains(course1.getStudentGroupName());
        }

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterLDAPUsersInExam() throws Exception {
        var savedExam = examUtilService.addExam(course1);
        String student100 = TEST_PREFIX + "student100";
        String student200 = TEST_PREFIX + "student200";
        String student300 = TEST_PREFIX + "student300";

        // setup mocks
        var ldapUser1Dto = new LdapUserDto().firstName(student100).lastName(student100).username(student100).registrationNumber("100000").email(student100 + "@tum.de");
        doReturn(Optional.of(ldapUser1Dto)).when(ldapUserService).findByUsername(student100);

        var ldapUser2Dto = new LdapUserDto().firstName(student200).lastName(student200).username(student200).registrationNumber("200000").email(student200 + "@tum.de");
        doReturn(Optional.of(ldapUser2Dto)).when(ldapUserService).findByEmail(student200 + "@tum.de");

        var ldapUser3Dto = new LdapUserDto().firstName(student300).lastName(student300).username(student300).registrationNumber("3000000").email(student300 + "@tum.de");
        doReturn(Optional.of(ldapUser3Dto)).when(ldapUserService).findByRegistrationNumber("3000000");

        // user with login
        StudentDTO dto1 = new StudentDTO(student100, student100, student100, null, null);
        // user with email
        StudentDTO dto2 = new StudentDTO(null, student200, student200, null, student200 + "@tum.de");
        // user with registration number
        StudentDTO dto3 = new StudentDTO(null, student300, student300, "3000000", null);
        // user without anything
        StudentDTO dto4 = new StudentDTO(null, null, null, null, null);

        List<StudentDTO> registrationFailures = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students",
                List.of(dto1, dto2, dto3, dto4), StudentDTO.class, HttpStatus.OK);
        assertThat(registrationFailures).containsExactly(dto4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentsToExam_testExam() throws Exception {
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "student1", "1111111");

        StudentDTO studentDto1 = UserFactory.generateStudentDTOWithRegistrationNumber("1111111");
        List<StudentDTO> studentDTOS = List.of(studentDto1);
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", studentDTOS, StudentDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRemoveAllStudentsFromExam_testExam() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudentThatDoesNotExist() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddAllRegisteredUsersToExam() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        Channel channel = examUtilService.addExamChannel(exam, "testchannel");
        int numberOfStudentsInCourse = userRepo.findAllByIsDeletedIsFalseAndGroupsContains(course1.getStudentGroupName()).size();

        User student99 = userUtilService.createAndSaveUser(TEST_PREFIX + "student99"); // not registered for the course
        student99.setGroups(Collections.singleton("tumuser"));
        userUtilService.setRegistrationNumberOfUserAndSave(student99, "1234");
        assertThat(student99.getGroups()).contains(course1.getStudentGroupName());

        var examUser99 = examUserRepository.findByExamIdAndUserId(exam.getId(), student99.getId());
        assertThat(examUser99).isEmpty();

        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/register-course-students", null, HttpStatus.OK, null);

        exam = examRepository.findWithExamUsersById(exam.getId()).orElseThrow();
        examUser99 = examUserRepository.findByExamIdAndUserId(exam.getId(), student99.getId());

        // the course students + our custom student99
        assertThat(exam.getExamUsers()).hasSize(numberOfStudentsInCourse + 1);
        assertThat(exam.getExamUsers()).contains(examUser99.orElseThrow());
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam.getId());

        Channel channelFromDB = channelRepository.findChannelByExamId(exam.getId());
        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getExam()).isEqualTo(exam);
        assertThat(channelFromDB.getName()).isEqualTo(channel.getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterCourseStudents_testExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/register-course-students", null, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsUserRegisteredForExam() {
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
}
