package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Covers the {@code @Size(max = 10000)} limit enforced on
 * {@link de.tum.cit.aet.artemis.course.dto.CourseUpdateDTO#courseInformationSharingMessagingCodeOfConduct()}.
 * Length is counted in UTF-16 code units on both sides, matching the Angular client cap.
 */
class CourseUpdateCodeOfConductLengthIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "coccaplen";

    private static final int CODE_OF_CONDUCT_LIMIT = 10_000;

    private static final String SENTINEL = "Code of Conduct";

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createEnrolledCourseWithMessagingEnabled(TEST_PREFIX);
    }

    private MvcResult performUpdate(String codeOfConductValue) throws Exception {
        JsonMapper mapper = request.getObjectMapper();
        course.setCourseInformationSharingMessagingCodeOfConduct(codeOfConductValue);
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(course).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + course.getId()).file(coursePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        return request.performMvcRequest(builder).andReturn();
    }

    private String persistedCodeOfConduct() {
        return courseRepository.findByIdElseThrow(course.getId()).getCourseInformationSharingMessagingCodeOfConduct();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_codeOfConductAtExactLimit_isAccepted() throws Exception {
        String atLimit = "x".repeat(CODE_OF_CONDUCT_LIMIT);

        MvcResult result = performUpdate(atLimit);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String persisted = persistedCodeOfConduct();
        assertThat(persisted).hasSize(CODE_OF_CONDUCT_LIMIT).isEqualTo(atLimit);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_codeOfConductOverLimit_isRejectedAndDbUnchanged() throws Exception {
        assertThat(persistedCodeOfConduct()).isEqualTo(SENTINEL);
        String overLimit = "x".repeat(CODE_OF_CONDUCT_LIMIT + 1);

        MvcResult result = performUpdate(overLimit);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("courseInformationSharingMessagingCodeOfConduct");
        assertThat(persistedCodeOfConduct()).isEqualTo(SENTINEL);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_codeOfConductJustUnderLimit_isAccepted() throws Exception {
        String justUnder = "x".repeat(CODE_OF_CONDUCT_LIMIT - 1);

        MvcResult result = performUpdate(justUnder);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(persistedCodeOfConduct()).hasSize(CODE_OF_CONDUCT_LIMIT - 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_codeOfConductNull_isAccepted() throws Exception {
        MvcResult result = performUpdate(null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(persistedCodeOfConduct()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_codeOfConductEmpty_isAccepted() throws Exception {
        MvcResult result = performUpdate("");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        // @Size permits null and empty; the applied value stays as sent.
        String persisted = persistedCodeOfConduct();
        assertThat(persisted == null || persisted.isEmpty()).isTrue();
    }

    @Test
    void bundledCodeOfConductTemplate_isWithinLimit() throws Exception {
        var templateResource = getClass().getClassLoader().getResourceAsStream("templates/codeofconduct/README.md");
        assertThat(templateResource).as("bundled code-of-conduct template must be present on the classpath").isNotNull();
        try (var stream = templateResource) {
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(template.length()).as("bundled template must stay within the request-boundary size cap").isLessThanOrEqualTo(CODE_OF_CONDUCT_LIMIT);
        }
    }
}
