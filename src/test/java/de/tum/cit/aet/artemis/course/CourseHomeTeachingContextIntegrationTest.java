package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseCreateDTO;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseHomeTeachingContextIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String PREFIX = "hometeachingcontext";

    @Autowired
    private CourseConfigurationRepository configurations;

    @Autowired
    private jakarta.validation.Validator validator;

    private Course course;

    private long configurationId;

    private ZonedDateTime warning;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        var configuration = configurations.findByCourseId(course.getId()).orElseThrow();
        configuration.setCourse(course);
        configuration.setStudentWelcomeContext("Learn by exploring, reflecting, and trying again.");
        configuration.setGradeRelevant(false);
        configuration.setDataRetentionHold(true);
        warning = ZonedDateTime.now().minusDays(40).withNano(0);
        configuration.setResetWarningSentDate(warning);
        configuration.setAutoOrchestratorEnabled(true);
        configuration.setDebounceWindowSecondsOverride(12);
        configuration.setMaxDailyOrchestrationOverride(4);
        course.setCourseConfiguration(configuration);
        configurations.saveAndFlush(configuration);
        configurationId = configurations.findByCourseId(course.getId()).orElseThrow().getId();
    }

    @Test
    @WithMockUser(username = PREFIX + "instructor1", roles = "USER")
    void contextUpdatePreservesConfigurationAndOmissionPreservesContext() throws Exception {
        var payload = payload();
        payload.put("studentWelcomeContext", "Work together and explain your reasoning.");
        update(payload, 200);
        assertPreserved("Work together and explain your reasoning.");
        payload.remove("studentWelcomeContext");
        update(payload, 200);
        assertPreserved("Work together and explain your reasoning.");
        payload.putNull("studentWelcomeContext");
        update(payload, 200);
        assertPreserved("Work together and explain your reasoning.");
        payload.put("studentWelcomeContext", "");
        update(payload, 200);
        assertPreserved(null);
    }

    @Test
    @WithMockUser(username = PREFIX + "instructor1", roles = "USER")
    void rejectsOverlongContextAndAcceptsBoundary() throws Exception {
        var payload = payload();
        payload.put("studentWelcomeContext", "x".repeat(2001));
        update(payload, 400);
        assertPreserved("Learn by exploring, reflecting, and trying again.");
        payload.put("studentWelcomeContext", "x".repeat(2000));
        update(payload, 200);
        assertPreserved("x".repeat(2000));
    }

    @Test
    void createDtoCarriesTeachingContext() {
        var json = payload();
        json.put("shortName", PREFIX + "new");
        json.put("studentWelcomeContext", "Learn through practice.");
        var dto = request.getObjectMapper().treeToValue(json, CourseCreateDTO.class);
        var created = courseRepository.saveWithDefaultConfigurations(dto.toCourse());
        assertThat(configurations.findByCourseId(created.getId()).orElseThrow().getStudentWelcomeContext()).isEqualTo("Learn through practice.");
    }

    @Test
    void createValidationAccepts2000AndRejects2001Characters() {
        var json = payload();
        json.put("studentWelcomeContext", "x".repeat(2000));
        var accepted = request.getObjectMapper().treeToValue(json, CourseCreateDTO.class);
        json.put("studentWelcomeContext", "x".repeat(2001));
        var rejected = request.getObjectMapper().treeToValue(json, CourseCreateDTO.class);
        assertThat(validator.validate(accepted)).isEmpty();
        assertThat(validator.validate(rejected)).anyMatch(v -> v.getPropertyPath().toString().equals("studentWelcomeContext"));
    }

    private ObjectNode payload() {
        return (ObjectNode) request.getObjectMapper().valueToTree(course);
    }

    private void update(ObjectNode payload, int expected) throws Exception {
        var part = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, request.getObjectMapper().writeValueAsBytes(payload));
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + course.getId()).file(part);
        request.performMvcRequest(builder).andExpect(status().is(expected));
    }

    private void assertPreserved(String context) throws Exception {
        var configuration = configurations.findByCourseId(course.getId()).orElseThrow();
        var loaded = request.get("/api/course/courses/" + course.getId() + "/for-instructor", org.springframework.http.HttpStatus.OK,
                de.tum.cit.aet.artemis.course.dto.CourseManagementDTO.class);
        assertThat(loaded.courseConfiguration().studentWelcomeContext()).isEqualTo(context);
        assertThat(configuration.getId()).isEqualTo(configurationId);
        assertThat(configuration.getStudentWelcomeContext()).isEqualTo(context);
        assertThat(configuration.isGradeRelevant()).isFalse();
        assertThat(configuration.isDataRetentionHold()).isTrue();
        assertThat(configuration.isAutoOrchestratorEnabled()).isTrue();
        assertThat(configuration.getDebounceWindowSecondsOverride()).isEqualTo(12);
        assertThat(configuration.getMaxDailyOrchestrationOverride()).isEqualTo(4);
        assertThat(configuration.getResetWarningSentDate().toInstant()).isEqualTo(warning.toInstant());
    }
}
