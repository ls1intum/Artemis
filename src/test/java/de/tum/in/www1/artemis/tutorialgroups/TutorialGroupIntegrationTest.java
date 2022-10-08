package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @WithMockUser(username = "student1", roles = "USER")
    void getAllForCourse_asStudent_shouldReturnTutorialGroups() throws Exception {
        var tutorialGroupsOfCourse = request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
        assertThat(tutorialGroupsOfCourse).hasSize(2);
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).containsExactlyInAnyOrder(exampleOneTutorialGroupId,
                exampleTwoTutorialGroupId);
        for (var tutorialGroup : tutorialGroupsOfCourse) { // private information hidden
            assertThat(tutorialGroup.getRegistrations()).isEqualTo(Set.of());
            assertThat(tutorialGroup.getTeachingAssistant()).isEqualTo(null);
            assertThat(tutorialGroup.getCourse()).isEqualTo(null);
        }

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getOneOfCourse_asInstructor_shouldReturnTutorialGroup() throws Exception {
        var tutorialGroup = request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.OK, TutorialGroup.class);
        assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
        // private information hidden
        assertThat(tutorialGroup.getRegistrations()).isEqualTo(Set.of());
        assertThat(tutorialGroup.getTeachingAssistant()).isEqualTo(null);
        assertThat(tutorialGroup.getCourse()).isEqualTo(null);
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

}
