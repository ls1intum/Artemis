package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.InitialCommitAuthorMode;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.course.dto.InitialCommitAuthorDTO;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseInitialCommitAuthorIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String PREFIX = "initialcommitauthor";

    @Autowired
    private CourseConfigurationRepository configurations;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
    }

    @Test
    @WithMockUser(username = PREFIX + "instructor1", roles = "USER")
    void aCourseKeepsTheExerciseCreatorAsTheAuthorUntilAnInstructorChoosesAnother() throws Exception {
        assertStored(InitialCommitAuthorMode.EXERCISE_CREATOR, null, null);
        assertThat(loadedSetting()).isEqualTo(new InitialCommitAuthorDTO(InitialCommitAuthorMode.EXERCISE_CREATOR, null, null));

        // an update that does not mention the setting leaves it alone
        update(payload(), 200);
        assertStored(InitialCommitAuthorMode.EXERCISE_CREATOR, null, null);
    }

    @Test
    @WithMockUser(username = PREFIX + "instructor1", roles = "USER")
    void anInstructorCanChooseThePlaceholderAndACustomIdentityAndOmissionKeepsTheChoice() throws Exception {
        var payload = payload();
        setting(payload, "CUSTOM", "  Course Team ", " team@university.example ");
        update(payload, 200);
        assertStored(InitialCommitAuthorMode.CUSTOM, "Course Team", "team@university.example");
        assertThat(loadedSetting()).isEqualTo(new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", "team@university.example"));

        update(payload(), 200);
        assertStored(InitialCommitAuthorMode.CUSTOM, "Course Team", "team@university.example");

        // the identity of the custom mode is dropped, it is nobody's data once it is not used
        setting(payload, "PLACEHOLDER", "Course Team", "team@university.example");
        update(payload, 200);
        assertStored(InitialCommitAuthorMode.PLACEHOLDER, null, null);

        setting(payload, "EXERCISE_CREATOR", null, null);
        update(payload, 200);
        assertStored(InitialCommitAuthorMode.EXERCISE_CREATOR, null, null);
    }

    @Test
    @WithMockUser(username = PREFIX + "instructor1", roles = "USER")
    void aCustomIdentityThatCouldInjectGitHeaderDataOrIsIncompleteIsRejected() throws Exception {
        var payload = payload();
        setting(payload, "CUSTOM", "Course Team", "team@university.example");
        update(payload, 200);

        setting(payload, "CUSTOM", "Course Team\nauthor Forged <forged@example.org>", "team@university.example");
        update(payload, 400);
        setting(payload, "CUSTOM", "Course <Team>", "team@university.example");
        update(payload, 400);
        setting(payload, "CUSTOM", "   ", "team@university.example");
        update(payload, 400);
        setting(payload, "CUSTOM", "x".repeat(101), "team@university.example");
        update(payload, 400);
        setting(payload, "CUSTOM", "Course Team", "not an email");
        update(payload, 400);
        setting(payload, "CUSTOM", "Course Team", "team@university.example\ncommitter forged");
        update(payload, 400);
        setting(payload, "CUSTOM", "Course Team", "a".repeat(250) + "@example.org");
        update(payload, 400);
        setting(payload, "CUSTOM", "Course Team", null);
        update(payload, 400);
        payload.putObject("initialCommitAuthor").put("name", "Course Team");
        update(payload, 400);

        assertStored(InitialCommitAuthorMode.CUSTOM, "Course Team", "team@university.example");
    }

    @Test
    @WithMockUser(username = PREFIX + "student1", roles = "USER")
    void aStudentCannotChangeTheSetting() throws Exception {
        var payload = payload();
        setting(payload, "PLACEHOLDER", null, null);
        update(payload, 403);
        assertStored(InitialCommitAuthorMode.EXERCISE_CREATOR, null, null);
    }

    private void setting(ObjectNode payload, String mode, String name, String email) {
        ObjectNode setting = payload.putObject("initialCommitAuthor");
        setting.put("mode", mode);
        setting.put("name", name);
        setting.put("email", email);
    }

    private ObjectNode payload() {
        return (ObjectNode) request.getObjectMapper().valueToTree(course);
    }

    private void update(ObjectNode payload, int expected) throws Exception {
        var part = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, request.getObjectMapper().writeValueAsBytes(payload));
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + course.getId()).file(part);
        request.performMvcRequest(builder).andExpect(status().is(expected));
    }

    private InitialCommitAuthorDTO loadedSetting() throws Exception {
        return request.get("/api/course/courses/" + course.getId() + "/for-instructor", HttpStatus.OK, CourseManagementDTO.class).courseConfiguration().initialCommitAuthor();
    }

    private void assertStored(InitialCommitAuthorMode mode, String name, String email) {
        var configuration = configurations.findByCourseId(course.getId()).orElseThrow();
        assertThat(configuration.getInitialCommitAuthorMode()).isEqualTo(mode);
        assertThat(configuration.getInitialCommitAuthorName()).isEqualTo(name);
        assertThat(configuration.getInitialCommitAuthorEmail()).isEqualTo(email);
    }
}
