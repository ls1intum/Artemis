package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.dto.CourseIrisSettingsDTO;

class IrisSettingsResourceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irissettingsresource";

    private Course course1;

    @BeforeEach
    void initTestCase() {
        // Create course with the test prefix
        course1 = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        // Create users for the course (1 student, 1 tutor, 1 editor, 1 instructor)
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    // ==================== GET /api/iris/courses/{courseId}/iris-settings ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseSettings_asStudent() throws Exception {
        enableIrisFor(course1);

        var response = request.get("/api/iris/courses/" + course1.getId() + "/iris-settings", HttpStatus.OK, CourseIrisSettingsDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.settings().enabled()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseSettings_asInstructor() throws Exception {
        enableIrisFor(course1);

        var response = request.get("/api/iris/courses/" + course1.getId() + "/iris-settings", HttpStatus.OK, CourseIrisSettingsDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.settings().enabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetCourseSettings_asAdmin() throws Exception {
        enableIrisFor(course1);

        var response = request.get("/api/iris/courses/" + course1.getId() + "/iris-settings", HttpStatus.OK, CourseIrisSettingsDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.settings().enabled()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseSettings_nonExistentCourse() throws Exception {
        // Security annotation checks enrollment first, so we get 403 instead of 404
        request.get("/api/iris/courses/999999/iris-settings", HttpStatus.FORBIDDEN, CourseIrisSettingsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseSettings_notEnrolled() throws Exception {
        // Students can read Iris settings for any course (read-only access)
        var response = request.get("/api/iris/courses/" + course1.getId() + "/iris-settings", HttpStatus.OK, CourseIrisSettingsDTO.class);
        assertThat(response).isNotNull();
    }

    // ==================== PUT /api/iris/courses/{courseId}/iris-settings ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateCourseSettings_asInstructor_toggleEnabled() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var update = IrisCourseSettingsDTO.of(false, current.customInstructions(), current.variant(), current.rateLimit());

        var response = request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.settings().enabled()).isFalse();

        // Verify persistence
        var saved = irisSettingsService.getSettingsForCourse(course1);
        assertThat(saved.enabled()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateCourseSettings_asInstructor_updateCustomInstructions() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var update = IrisCourseSettingsDTO.of(current.enabled(), "Custom instructions for this course", current.variant(), current.rateLimit());

        var response = request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.settings().customInstructions()).isEqualTo("Custom instructions for this course");

        // Verify persistence
        var saved = irisSettingsService.getSettingsForCourse(course1);
        assertThat(saved.customInstructions()).isEqualTo("Custom instructions for this course");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseSettings_asAdmin_changeVariant() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), IrisPipelineVariant.ADVANCED, current.rateLimit());

        var response = request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.settings().variant()).isEqualTo(IrisPipelineVariant.ADVANCED);

        // Verify persistence
        var saved = irisSettingsService.getSettingsForCourse(course1);
        assertThat(saved.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseSettings_asAdmin_changeRateLimits() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var newRateLimit = new IrisRateLimitConfiguration(100, 24);
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), current.variant(), newRateLimit);

        var response = request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.settings().rateLimit()).isNotNull();
        assertThat(response.settings().rateLimit().requests()).isEqualTo(100);
        assertThat(response.settings().rateLimit().timeframeHours()).isEqualTo(24);

        // Verify persistence
        var saved = irisSettingsService.getSettingsForCourse(course1);
        assertThat(saved.rateLimit().requests()).isEqualTo(100);
        assertThat(saved.rateLimit().timeframeHours()).isEqualTo(24);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateCourseSettings_asInstructor_cannotChangeVariant() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), IrisPipelineVariant.ADVANCED, current.rateLimit());

        request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateCourseSettings_asInstructor_cannotChangeRateLimits() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var newRateLimit = new IrisRateLimitConfiguration(100, 24);
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), current.variant(), newRateLimit);

        request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateCourseSettings_asStudent_forbidden() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var update = IrisCourseSettingsDTO.of(false, current.customInstructions(), current.variant(), current.rateLimit());

        request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateCourseSettings_nonExistentCourse() throws Exception {
        var update = IrisCourseSettingsDTO.of(true, null, IrisPipelineVariant.DEFAULT, null);
        // Security annotation checks enrollment first, so we get 403 instead of 404
        request.putWithResponseBody("/api/iris/courses/999999/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.FORBIDDEN);
    }

    // ==================== Validation Tests ====================

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseSettings_oversizedCustomInstructions() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var oversizedInstructions = "a".repeat(2049); // Max is 2048
        var update = IrisCourseSettingsDTO.of(current.enabled(), oversizedInstructions, current.variant(), current.rateLimit());

        request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseSettings_negativeRateLimit() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var negativeRateLimit = new IrisRateLimitConfiguration(-1, 24);
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), current.variant(), negativeRateLimit);

        request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseSettings_negativeTimeframe() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var negativeTimeframe = new IrisRateLimitConfiguration(100, -1);
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), current.variant(), negativeTimeframe);

        request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseSettings_zeroRateLimit_treatedAsUnlimited() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        var zeroRateLimit = new IrisRateLimitConfiguration(0, 0);
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), current.variant(), zeroRateLimit);

        var response = request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        // Zero should be normalized to null (unlimited), not fall back to defaults
        assertThat(response.settings().rateLimit()).isNotNull();
        assertThat(response.settings().rateLimit().requests()).isNull();
        assertThat(response.settings().rateLimit().timeframeHours()).isNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourseSettings_nullRateLimit_usesDefaults() throws Exception {
        enableIrisFor(course1);

        var current = irisSettingsService.getSettingsForCourse(course1);
        // null rateLimit means "no override, use defaults"
        var update = IrisCourseSettingsDTO.of(current.enabled(), current.customInstructions(), current.variant(), null);

        var response = request.putWithResponseBody("/api/iris/courses/" + course1.getId() + "/iris-settings", update, CourseIrisSettingsDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        // The stored rateLimit should be null (no override)
        assertThat(response.settings().rateLimit()).isNull();
        // But the effectiveRateLimit should come from application defaults
        assertThat(response.effectiveRateLimit()).isEqualTo(response.applicationRateLimitDefaults());
    }

    // ==================== 404 Tests for Removed Endpoints ====================

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovedGlobalSettingsEndpoint_notFound() throws Exception {
        request.get("/api/iris/global-iris-settings", HttpStatus.NOT_FOUND, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovedExerciseSettingsEndpoint_notFound() throws Exception {
        request.get("/api/iris/exercises/1/settings", HttpStatus.NOT_FOUND, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovedGlobalSettingsUpdateEndpoint_notFound() throws Exception {
        request.put("/api/iris/global-iris-settings", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovedExerciseSettingsUpdateEndpoint_notFound() throws Exception {
        request.put("/api/iris/exercises/1/settings", null, HttpStatus.NOT_FOUND);
    }
}
