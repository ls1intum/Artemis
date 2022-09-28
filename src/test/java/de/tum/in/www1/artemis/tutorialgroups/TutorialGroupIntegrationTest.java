package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.google.common.collect.ImmutableSet;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource.TutorialGroupRegistrationImportDTO;

class TutorialGroupIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private Long exampleCourseId;

    private Long exampleOneTutorialGroupId;

    private Long exampleTwoTutorialGroupId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        // creating the users student1-student10, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(10, 10, 10, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("editor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        // Add registration number to student 8
        User student8 = userRepository.findOneByLogin("student8").get();
        student8.setRegistrationNumber("123456");
        userRepository.save(student8);
        // Add registration number to student 9
        User student9 = userRepository.findOneByLogin("student9").get();
        student9.setRegistrationNumber("654321");
        userRepository.save(student9);

        var course = this.database.createCourse();
        exampleCourseId = course.getId();

        exampleOneTutorialGroupId = createAndSaveTutorialGroup(exampleCourseId, "ExampleTitle1", "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH,
                userRepository.findOneByLogin("tutor1").get(), ImmutableSet.of(userRepository.findOneByLogin("student1").get(), userRepository.findOneByLogin("student2").get(),
                        userRepository.findOneByLogin("student3").get(), userRepository.findOneByLogin("student4").get(), userRepository.findOneByLogin("student5").get())).getId();

        exampleTwoTutorialGroupId = createAndSaveTutorialGroup(exampleCourseId, "ExampleTitle2", "LoremIpsum2", 10, true, "LoremIpsum2", Language.GERMAN,
                userRepository.findOneByLogin("tutor2").get(), ImmutableSet.of(userRepository.findOneByLogin("student6").get(), userRepository.findOneByLogin("student7").get()))
                        .getId();
    }

    private TutorialGroup createAndSaveTutorialGroup(Long courseId, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus,
            Language language, User teachingAssistant, Set<User> registeredStudents) {
        var course = courseRepository.findByIdElseThrow(courseId);

        var tutorialGroup = tutorialGroupRepository
                .saveAndFlush(new TutorialGroup(course, title, additionalInformation, capacity, isOnline, campus, language, teachingAssistant, new HashSet<>()));

        var registrations = new HashSet<TutorialGroupRegistration>();
        for (var student : registeredStudents) {
            registrations.add(new TutorialGroupRegistration(student, tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
        }
        tutorialGroupRegistrationRepository.saveAllAndFlush(registrations);
        return tutorialGroup;
    }

    private TutorialGroup createNewTutorialGroup() {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle("NewTitle");
        tutorialGroup.setTeachingAssistant(userRepository.findOneByLogin("tutor1").get());
        return tutorialGroup;
    }

    private void testJustForInstructorEndpoints() throws Exception {
        request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.FORBIDDEN, TutorialGroup.class);
        request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.FORBIDDEN, TutorialGroup.class);
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", createNewTutorialGroup(), TutorialGroup.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId,
                tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleOneTutorialGroupId).get(), TutorialGroup.class, HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.FORBIDDEN);
        request.postWithoutResponseBody(
                "/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/register/" + userRepository.findOneByLogin("student6").get().getLogin(),
                HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.delete(
                "/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/deregister/" + userRepository.findOneByLogin("student1").get().getLogin(),
                HttpStatus.FORBIDDEN);
        request.postListWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/register-multiple", new HashSet<>(),
                StudentDTO.class, HttpStatus.FORBIDDEN);
        request.postListWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + "import", new HashSet<>(), TutorialGroupRegistrationImportDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor42", roles = "INSTRUCTOR")
    void request_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void request_asTutor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void request_asStudent_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void request_asEditor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getTitle_asUser_shouldReturnTitle() throws Exception {
        var tutorialGroupTitle = request.get("/api/tutorial-groups/" + exampleOneTutorialGroupId + "/title", HttpStatus.OK, String.class);
        assertThat(tutorialGroupTitle).isEqualTo("ExampleTitle1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllOfCourse_asInstructor_shouldReturnTutorialGroups() throws Exception {
        var tutorialGroupsOfCourse = request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
        assertThat(tutorialGroupsOfCourse).hasSize(2);
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).containsExactlyInAnyOrder(exampleOneTutorialGroupId,
                exampleTwoTutorialGroupId);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getOneOfCourse_asInstructor_shouldReturnTutorialGroup() throws Exception {
        var tutorialGroup = request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.OK, TutorialGroup.class);
        assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroup() throws Exception {
        var persistedTutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", createNewTutorialGroup(), TutorialGroup.class,
                HttpStatus.CREATED);
        assertThat(persistedTutorialGroup.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void create_WithIdInBody_shouldReturnBadRequest() throws Exception {
        var tutorialGroup = createNewTutorialGroup();
        tutorialGroup.setId(22L);
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void create_tutorialGroupWithTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        var tutorialGroup = createNewTutorialGroup();
        tutorialGroup.setTitle("ExampleTitle1");
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void delete_asInstructor_shouldDeleteTutorialGroup() throws Exception {
        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.NO_CONTENT);
        request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.NOT_FOUND, TutorialGroup.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void update_asInstructor_shouldUpdateTutorialGroup() throws Exception {
        var existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("Updated");

        var updatedTutorialGroup = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, existingTutorialGroup,
                TutorialGroup.class, HttpStatus.OK);

        assertThat(updatedTutorialGroup.getTitle()).isEqualTo("Updated");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void update_withTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        var existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("  ExampleTitle2  ");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, existingTutorialGroup, TutorialGroup.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void update_withoutId_shouldReturnBadRequest() throws Exception {
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, createNewTutorialGroup(), TutorialGroup.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerStudent_asInstructor_shouldRegisterStudent() throws Exception {
        var student6 = userRepository.findOneByLogin("student6").get();
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/register/" + student6.getLogin(),
                HttpStatus.NO_CONTENT, new LinkedMultiValueMap<>());
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student6);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerStudent_studentNotFound_shouldReturnNotFound() throws Exception {
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/register/" + "studentXX", HttpStatus.NOT_FOUND,
                new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerStudent_studentRegistered_shouldReturnNoContent() throws Exception {
        var student1 = userRepository.findOneByLogin("student1").get();
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/register/" + student1.getLogin(),
                HttpStatus.NO_CONTENT, new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_asInstructor_shouldDeRegisterStudent() throws Exception {
        var student1 = userRepository.findOneByLogin("student1").get();
        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/deregister/" + student1.getLogin(), HttpStatus.NO_CONTENT);
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).doesNotContain(student1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_studentNotRegistered_shouldReturnNoContent() throws Exception {
        var student6 = userRepository.findOneByLogin("student6").get();
        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/deregister/" + student6.getLogin(), HttpStatus.NO_CONTENT,
                new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_studentNotFound_shouldReturnNotFound() throws Exception {
        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/deregister/" + "studentXX", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerMultipleStudents_asInstructor_shouldRegisterStudents() throws Exception {
        var student6 = userRepository.findOneByLogin("student6").get();
        student6.setRegistrationNumber("number6");
        userRepository.saveAndFlush(student6);

        var studentsToAdd = new ArrayList<StudentDTO>();

        var studentInCourse = new StudentDTO();
        studentInCourse.setLogin(student6.getLogin());
        studentInCourse.setRegistrationNumber(student6.getRegistrationNumber());

        var studentNotInCourse = new StudentDTO();
        studentNotInCourse.setLogin("studentXX");
        studentNotInCourse.setRegistrationNumber("numberXX");

        studentsToAdd.add(studentInCourse);
        studentsToAdd.add(studentNotInCourse);

        List<StudentDTO> notFoundStudents = request.postListWithResponseBody(
                "/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/register-multiple", studentsToAdd, StudentDTO.class, HttpStatus.OK);
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student6);
        assertThat(notFoundStudents).containsExactly(studentNotInCourse);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_justTutorialGroupTitle_shouldCreateTutorialGroups() throws Exception {
        // given
        var freshTitleOne = "freshTitleOne";
        var freshTitleTwo = "freshTitleTwo";
        var existingTitle = "ExampleTitle1";
        var regNullStudent = new TutorialGroupRegistrationImportDTO(freshTitleOne, null);
        var regBlankStudent = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO("", "", "", ""));
        var regStudentPropertiesNull = new TutorialGroupRegistrationImportDTO(freshTitleOne, new StudentDTO(null, null, null, null));
        var regExistingTutorialGroup = new TutorialGroupRegistrationImportDTO(existingTitle, null);
        assertTutorialWithTitleDoesNotExistInDb(freshTitleOne);
        assertTutorialWithTitleDoesNotExistInDb(freshTitleTwo);
        assertTutorialGroupWithTitleExistsInDb(existingTitle);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(regNullStudent);
        tutorialGroupRegistrations.add(regBlankStudent);
        tutorialGroupRegistrations.add(regExistingTutorialGroup);
        tutorialGroupRegistrations.add(regStudentPropertiesNull);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertThat(importResult.size()).isEqualTo(4);
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::importSuccessful)).allMatch(status -> status.equals(true));
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::error)).allMatch(Objects::isNull);
        var regBlankExpected = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO(null, null, null, null));
        var studentPropertiesNullExpected = new TutorialGroupRegistrationImportDTO(freshTitleOne, new StudentDTO(null, null, null, null));
        assertThat(importResult.stream()).containsExactlyInAnyOrder(regNullStudent, regBlankExpected, regExistingTutorialGroup, studentPropertiesNullExpected);

        assertImportedTutorialGroupWithTitleInDB(freshTitleOne, new HashSet<>());
        assertImportedTutorialGroupWithTitleInDB(freshTitleTwo, new HashSet<>());
        assertTutorialGroupWithTitleExistsInDb(existingTitle);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_tutorialGroupTitleAndStudents_shouldCreateTutorialAndRegisterStudents() throws Exception {
        // given
        var existingGroup1 = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleOneTutorialGroupId).get();
        var existingGroup2 = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrations(exampleTwoTutorialGroupId).get();

        // we test with student1 that the student will be deregistered from the old tutorial group
        var student1 = userRepository.findOneByLogin("student1").get();
        assertUserIsRegisteredInTutorialWithTitle(existingGroup1.getTitle(), student1);
        // we test with student8 that a previously unregistered student will be registered to an existing tutorial group
        var student8 = userRepository.findOneByLogin("student8").get();
        assertUserIsNotRegisteredInATutorialGroup(student8);
        // we test with student9 that a previously unregistered student will be registered to a fresh tutorial group
        var freshTitle = "freshTitle";
        assertTutorialWithTitleDoesNotExistInDb(freshTitle);
        var student9 = userRepository.findOneByLogin("student9").get();
        assertUserIsNotRegisteredInATutorialGroup(student9);
        // we test with student6 that a previously registered student will be registered to a fresh tutorial group
        var student6 = userRepository.findOneByLogin("student6").get();
        assertUserIsRegisteredInTutorialWithTitle(existingGroup2.getTitle(), student6);

        // student 1 from existing group1 to existing group 2
        // + test if identifying just with login works
        var student1Reg = new TutorialGroupRegistrationImportDTO(existingGroup2.getTitle(),
                new StudentDTO(student1.getLogin(), student1.getFirstName(), student1.getLastName(), ""));

        // student 8 to existing group 1
        // + test if identifying just with registration number works
        var student8Reg = new TutorialGroupRegistrationImportDTO(existingGroup1.getTitle(),
                new StudentDTO("", student8.getFirstName(), student8.getLastName(), student8.getRegistrationNumber()));

        // student 9 to fresh tutorial group
        // + test if identifying with both login and registration number works
        var student9Reg = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student9));

        // student 6 to fresh tutorial group
        var student6Reg = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student6));

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(student1Reg);
        tutorialGroupRegistrations.add(student8Reg);
        tutorialGroupRegistrations.add(student9Reg);
        tutorialGroupRegistrations.add(student6Reg);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertThat(importResult.size()).isEqualTo(4);
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::importSuccessful)).allMatch(status -> status.equals(true));
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::error)).allMatch(Objects::isNull);
        assertThat(importResult.stream()).containsExactlyInAnyOrder(student1Reg, student8Reg, student9Reg, student6Reg);

        assertUserIsRegisteredInTutorialWithTitle(existingGroup2.getTitle(), student1);
        assertUserIsRegisteredInTutorialWithTitle(existingGroup1.getTitle(), student8);
        assertImportedTutorialGroupWithTitleInDB(freshTitle, Set.of(student9, student6));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_withoutTitle_shouldNotCreateTutorialGroup() throws Exception {
        var student1 = userRepository.findOneByLogin("student1").get();
        assertUserIsRegisteredInTutorialWithTitle("ExampleTitle1", student1);

        // given
        var emptyTitle = "";
        var reg = new TutorialGroupRegistrationImportDTO(emptyTitle, new StudentDTO(student1));
        assertTutorialWithTitleDoesNotExistInDb(emptyTitle);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(reg);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertThat(importResult.size()).isEqualTo(1);
        assertTutorialWithTitleDoesNotExistInDb(emptyTitle);
        var importResultDTO = importResult.get(0);
        assertThat(importResultDTO.importSuccessful()).isFalse();
        assertThat(importResultDTO.error()).isEqualTo(TutorialGroupResource.TutorialGroupImportErrors.NO_TITLE);
        // student1 should still be registered in the old tutorial group
        assertUserIsRegisteredInTutorialWithTitle("ExampleTitle1", student1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_titleButNonExistingStudent_shouldStillCreateTutorialGroupButNoRegistration() throws Exception {
        // given
        var freshTitle = "freshTitleOne";
        var reg = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO("notExisting", "firstName", "firstName1", ""));
        assertTutorialWithTitleDoesNotExistInDb(freshTitle);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(reg);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertImportedTutorialGroupWithTitleInDB(freshTitle, new HashSet<>());
        assertThat(importResult.size()).isEqualTo(1);
        var importResultDTO = importResult.get(0);
        assertThat(importResultDTO.importSuccessful()).isFalse();
        assertThat(importResultDTO.error()).isEqualTo(TutorialGroupResource.TutorialGroupImportErrors.NO_USER_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_titleButSameStudentToMultipleGroups_shouldStillCreateTutorialGroupsButNoRegistration() throws Exception {
        // given
        var freshTitle = "freshTitleOne";
        var freshTitleTwo = "freshTitleTwo";

        var student1 = userRepository.findOneByLogin("student1").get();
        assertUserIsRegisteredInTutorialWithTitle("ExampleTitle1", student1);
        var student8 = userRepository.findOneByLogin("student8").get();
        assertUserIsNotRegisteredInATutorialGroup(student8);

        var reg1 = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student1));
        var reg2 = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO(student1));
        var reg3 = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student8));
        var reg4 = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO(student8));
        assertTutorialWithTitleDoesNotExistInDb(freshTitle);
        assertTutorialWithTitleDoesNotExistInDb(freshTitleTwo);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(reg1);
        tutorialGroupRegistrations.add(reg2);
        tutorialGroupRegistrations.add(reg3);
        tutorialGroupRegistrations.add(reg4);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertImportedTutorialGroupWithTitleInDB(freshTitle, new HashSet<>());
        assertThat(importResult.size()).isEqualTo(4);
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::importSuccessful)).allMatch(status -> status.equals(false));
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::error)).allMatch(TutorialGroupResource.TutorialGroupImportErrors.MULTIPLE_REGISTRATIONS::equals);
        assertThat(importResult.stream()).containsExactlyInAnyOrder(reg1, reg2, reg3, reg4);
        // should still be registered in the old tutorial group
        assertUserIsRegisteredInTutorialWithTitle("ExampleTitle1", student1);
        assertUserIsNotRegisteredInATutorialGroup(student8);
    }

    private List<TutorialGroupRegistrationImportDTO> sendImportRequest(ArrayList<TutorialGroupRegistrationImportDTO> tutorialGroupRegistrations) throws Exception {
        return request.postListWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/import", tutorialGroupRegistrations, TutorialGroupRegistrationImportDTO.class,
                HttpStatus.OK);
    }

    private void assertTutorialWithTitleDoesNotExistInDb(String title) {
        assertThat(tutorialGroupRepository.existsByTitleAndCourse_Id(title, exampleCourseId)).isFalse();
    }

    private void assertTutorialGroupWithTitleExistsInDb(String title) {
        assertThat(tutorialGroupRepository.existsByTitleAndCourse_Id(title, exampleCourseId)).isTrue();
    }

    private void assertUserIsRegisteredInTutorialWithTitle(String expectedTitle, User expectedStudent) {
        assertThat(tutorialGroupRegistrationRepository.countByStudentAndTutorialGroup_Course_IdAndType(expectedStudent, exampleCourseId,
                TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);
        assertThat(tutorialGroupRegistrationRepository.existsByTutorialGroup_TitleAndStudentAndType(expectedTitle, expectedStudent,
                TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isTrue();
    }

    private void assertUserIsNotRegisteredInATutorialGroup(User expectedStudent) {
        assertThat(tutorialGroupRegistrationRepository.countByStudentAndTutorialGroup_Course_IdAndType(expectedStudent, exampleCourseId,
                TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(0);
    }

    private void assertImportedTutorialGroupWithTitleInDB(String expectedTitle, Set<User> expectedRegisteredStudents) {
        assertTutorialGroupWithTitleInDB(expectedTitle, expectedRegisteredStudents, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, false, null, null, null, null, null);
    }

    private void assertTutorialGroupWithTitleInDB(String expectedTitle, Set<User> expectedRegisteredStudents, TutorialGroupRegistrationType expectedRegistrationType,
            Boolean isOnline, String additionalInformation, Integer capacity, String campus, Language language, User teachingAssistant) {
        var tutorialGroupOptional = tutorialGroupRepository.findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(expectedTitle, exampleCourseId);
        assertThat(tutorialGroupOptional).isPresent();
        var tutorialGroup = tutorialGroupOptional.get();
        assertThat(tutorialGroup.getIsOnline()).isEqualTo(isOnline);
        assertThat(tutorialGroup.getAdditionalInformation()).isEqualTo(additionalInformation);
        assertThat(tutorialGroup.getCapacity()).isEqualTo(capacity);
        assertThat(tutorialGroup.getCampus()).isEqualTo(campus);
        assertThat(tutorialGroup.getLanguage()).isEqualTo(language);
        assertThat(tutorialGroup.getTeachingAssistant()).isEqualTo(teachingAssistant);
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).containsExactlyInAnyOrderElementsOf(expectedRegisteredStudents);
        // assert that all registrations are instructor registrations (always the case for import)
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getType)).allMatch(regType -> regType.equals(expectedRegistrationType));

    }

}
