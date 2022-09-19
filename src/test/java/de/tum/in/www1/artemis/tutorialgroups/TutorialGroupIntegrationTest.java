package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.google.common.collect.ImmutableSet;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.service.dto.StudentDTO;

class TutorialGroupIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    @Override
    void testJustForInstructorEndpoints() throws Exception {
        request.getList(getTutorialGroupsPath(), HttpStatus.FORBIDDEN, TutorialGroup.class);
        request.get(getTutorialGroupsPath() + exampleOneTutorialGroupId, HttpStatus.FORBIDDEN, TutorialGroup.class);
        request.postWithResponseBody(getTutorialGroupsPath(), buildTutorialGroupWithoutSchedule(), TutorialGroup.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId,
                tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get(), TutorialGroup.class, HttpStatus.FORBIDDEN);
        request.delete(getTutorialGroupsPath() + exampleOneTutorialGroupId, HttpStatus.FORBIDDEN);
        request.postWithoutResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/register/" + userRepository.findOneByLogin("student6").get().getLogin(),
                HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.delete(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/deregister/" + userRepository.findOneByLogin("student1").get().getLogin(), HttpStatus.FORBIDDEN);
        request.postListWithResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/register-multiple", new HashSet<>(), StudentDTO.class, HttpStatus.FORBIDDEN);
        request.getList(getTutorialGroupsPath() + "campus-values", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getTitle_asUser_shouldReturnTitle() throws Exception {
        // when
        var tutorialGroupTitle = request.get("/api/tutorial-groups/" + exampleOneTutorialGroupId + "/title", HttpStatus.OK, String.class);
        // then
        assertThat(tutorialGroupTitle).isEqualTo("ExampleTitle1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getUniqueCampusValues_asInstructor_shouldReturnUniqueCampusValues() throws Exception {
        // when
        var campusValues = request.getList(getTutorialGroupsPath() + "campus-values", HttpStatus.OK, String.class);
        // then
        assertThat(campusValues).containsExactlyInAnyOrder("LoremIpsum1", "LoremIpsum2");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllOfCourse_asInstructor_shouldReturnTutorialGroups() throws Exception {
        // when
        var tutorialGroupsOfCourse = request.getList(getTutorialGroupsPath(), HttpStatus.OK, TutorialGroup.class);
        // then
        assertThat(tutorialGroupsOfCourse).hasSize(2);
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).containsExactlyInAnyOrder(exampleOneTutorialGroupId,
                exampleTwoTutorialGroupId);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getOneOfCourse_asInstructor_shouldReturnTutorialGroup() throws Exception {
        // when
        var tutorialGroup = request.get(getTutorialGroupsPath() + exampleOneTutorialGroupId, HttpStatus.OK, TutorialGroup.class);
        // then
        assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroup() throws Exception {
        // when
        var persistedTutorialGroup = request.postWithResponseBody(getTutorialGroupsPath(), buildTutorialGroupWithoutSchedule(), TutorialGroup.class, HttpStatus.CREATED);
        // then
        assertThat(persistedTutorialGroup.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void create_WithIdInBody_shouldReturnBadRequest() throws Exception {
        // given
        var tutorialGroup = buildTutorialGroupWithoutSchedule();
        tutorialGroup.setId(22L);
        // when
        request.postWithResponseBody(getTutorialGroupsPath(), tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupRepository.findAll()).hasSize(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void create_tutorialGroupWithTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        // given
        var tutorialGroup = buildTutorialGroupWithoutSchedule();
        tutorialGroup.setTitle("ExampleTitle1");
        // when
        request.postWithResponseBody(getTutorialGroupsPath(), tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupRepository.findAll()).hasSize(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void delete_asInstructor_shouldDeleteTutorialGroup() throws Exception {
        // when
        request.delete(getTutorialGroupsPath() + exampleOneTutorialGroupId, HttpStatus.NO_CONTENT);
        // then
        request.get(getTutorialGroupsPath() + exampleOneTutorialGroupId, HttpStatus.NOT_FOUND, TutorialGroup.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void update_asInstructor_shouldUpdateTutorialGroup() throws Exception {
        // given
        var existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("Updated");

        // when
        var updatedTutorialGroup = request.putWithResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId, existingTutorialGroup, TutorialGroup.class, HttpStatus.OK);

        // then
        assertThat(updatedTutorialGroup.getTitle()).isEqualTo("Updated");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void update_withTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        // given
        var existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("  ExampleTitle2  ");
        // when
        request.putWithResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId, existingTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get().getTitle()).isEqualTo("ExampleTitle1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void update_withoutId_shouldReturnBadRequest() throws Exception {
        // then
        request.putWithResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId, buildTutorialGroupWithoutSchedule(), TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerStudent_asInstructor_shouldRegisterStudent() throws Exception {
        // given
        var student6 = userRepository.findOneByLogin("student6").get();
        // when
        request.postWithoutResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/register/" + student6.getLogin(), HttpStatus.NO_CONTENT,
                new LinkedMultiValueMap<>());
        // then
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student6);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerStudent_studentNotFound_shouldReturnNotFound() throws Exception {
        // then
        request.postWithoutResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/register/" + "studentXX", HttpStatus.NOT_FOUND, new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerStudent_studentRegistered_shouldReturnNoContent() throws Exception {
        // given
        var student1 = userRepository.findOneByLogin("student1").get();
        // when
        request.postWithoutResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/register/" + student1.getLogin(), HttpStatus.NO_CONTENT,
                new LinkedMultiValueMap<>());
        // then
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student1);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_asInstructor_shouldDeRegisterStudent() throws Exception {
        // given
        var student1 = userRepository.findOneByLogin("student1").get();
        // when
        request.delete(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/deregister/" + student1.getLogin(), HttpStatus.NO_CONTENT);
        // then
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).doesNotContain(student1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_studentNotRegistered_shouldReturnNoContent() throws Exception {
        // given
        var student6 = userRepository.findOneByLogin("student6").get();
        // when
        request.delete(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/deregister/" + student6.getLogin(), HttpStatus.NO_CONTENT, new LinkedMultiValueMap<>());
        // then
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).doesNotContain(student6);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_studentNotFound_shouldReturnNotFound() throws Exception {
        // then
        request.delete(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/deregister/" + "studentXX", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerMultipleStudents_asInstructor_shouldRegisterStudents() throws Exception {
        // given
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
        // when
        List<StudentDTO> notFoundStudents = request.postListWithResponseBody(getTutorialGroupsPath() + exampleOneTutorialGroupId + "/register-multiple", studentsToAdd,
                StudentDTO.class, HttpStatus.OK);
        // then
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student6);
        assertThat(notFoundStudents).containsExactly(studentNotInCourse);
    }

}
