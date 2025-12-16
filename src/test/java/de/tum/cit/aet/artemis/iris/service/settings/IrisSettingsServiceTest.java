package de.tum.cit.aet.artemis.iris.service.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;

class IrisSettingsServiceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irissettingsservice";

    @Autowired
    private IrisSettingsService irisSettingsService;

    private Course course;

    @BeforeEach
    void setUp() {
        course = courseUtilService.createCourse();
    }

    @Test
    void getCourseSettingsWithRateLimit_usesApplicationDefaultsWhenOverridesMissing() {
        enableIrisFor(course);

        var dto = irisSettingsService.getCourseSettingsWithRateLimit(course.getId());

        assertThat(dto.settings().enabled()).isTrue();
        assertThat(dto.applicationRateLimitDefaults()).isNotNull();
        assertThat(dto.effectiveRateLimit()).isNotNull();
    }

    @Test
    void updateCourseSettings_sanitizesCustomInstructions() {
        var payload = IrisCourseSettingsDTO.of(false, "  keep trimmed  ", IrisPipelineVariant.ADVANCED, new IrisRateLimitConfiguration(100, 24));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload);

        assertThat(dto.settings().customInstructions()).isEqualTo("keep trimmed");
        assertThat(dto.settings().rateLimit().requests()).isEqualTo(100);
        assertThat(dto.settings().rateLimit().timeframeHours()).isEqualTo(24);
    }

    @Test
    void updateCourseSettings_rejectsNegativeRateLimit() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(-1, 24));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Rate limit requests must be 0 or greater");
    }

    @Test
    void updateCourseSettings_rejectsPartialRateLimit_onlyRequests() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(100, null));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Both rate limit fields must be filled or both must be empty");
    }

    @Test
    void updateCourseSettings_rejectsPartialRateLimit_onlyTimeframe() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(null, 24));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Both rate limit fields must be filled or both must be empty");
    }

    @Test
    void updateCourseSettings_rejectsZeroTimeframe() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(100, 0));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Rate limit timeframe must be greater than 0");
    }

    @Test
    void updateCourseSettings_rejectsNegativeTimeframe() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(100, -5));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Rate limit timeframe must be greater than 0");
    }

    @Test
    void updateCourseSettings_acceptsBothFieldsEmpty() {
        enableIrisFor(course);

        // Both null = use defaults (should return null rateLimit)
        var payload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(null, null));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload);

        assertThat(dto.settings().rateLimit()).isNull();
    }

    @Test
    void updateCourseSettings_acceptsNullRateLimit() {
        enableIrisFor(course);

        // null rateLimit = use defaults
        var payload = IrisCourseSettingsDTO.of(true, null, null, null);

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload);

        assertThat(dto.settings().rateLimit()).isNull();
    }

    @Test
    void updateCourseSettings_acceptsBothFieldsFilled() {
        enableIrisFor(course);

        var payload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(100, 24));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload);

        assertThat(dto.settings().rateLimit()).isNotNull();
        assertThat(dto.settings().rateLimit().requests()).isEqualTo(100);
        assertThat(dto.settings().rateLimit().timeframeHours()).isEqualTo(24);
    }

    @Test
    void updateCourseSettings_acceptsZeroRequests() {
        enableIrisFor(course);

        // 0 requests means "unlimited" - should be allowed (rate limit service treats 0 as unlimited)
        var payload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(0, 24));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload);

        // 0 is stored as-is (rate limit service treats it as unlimited)
        assertThat(dto.settings().rateLimit()).isNotNull();
        assertThat(dto.settings().rateLimit().requests()).isZero();
        assertThat(dto.settings().rateLimit().timeframeHours()).isEqualTo(24);
    }

    @Test
    void getSettingsForCourseOrThrow_returnsSettings() {
        enableIrisFor(course);
        configureCourseSettings(course, "test instructions", IrisPipelineVariant.ADVANCED);

        var settings = irisSettingsService.getSettingsForCourseOrThrow(course.getId());

        assertThat(settings).isNotNull();
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.customInstructions()).isEqualTo("test instructions");
        assertThat(settings.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
    }

    @Test
    void getSettingsForCourseOrThrow_throwsWhenCourseNotFound() {
        long nonExistentCourseId = 99999L;

        assertThatThrownBy(() -> irisSettingsService.getSettingsForCourseOrThrow(nonExistentCourseId)).isInstanceOf(EntityNotFoundException.class).hasMessageContaining("Course");
    }

    @Test
    void deleteSettingsFor_byCourseObject() {
        enableIrisFor(course);

        // Verify settings exist
        var settingsBefore = irisSettingsService.getCourseSettingsWithRateLimit(course.getId());
        assertThat(settingsBefore.settings().enabled()).isTrue();

        // Delete settings
        irisSettingsService.deleteSettingsFor(course);

        // Verify settings are gone or reset to defaults
        var settingsAfter = irisSettingsService.getCourseSettingsWithRateLimit(course.getId());
        assertThat(settingsAfter.settings()).isEqualTo(IrisCourseSettingsDTO.defaultSettings());
    }

    @Test
    void deleteSettingsFor_byCourseId() {
        enableIrisFor(course);

        // Verify settings exist
        var settingsBefore = irisSettingsService.getCourseSettingsWithRateLimit(course.getId());
        assertThat(settingsBefore.settings().enabled()).isTrue();

        // Delete settings
        irisSettingsService.deleteSettingsFor(course.getId());

        // Verify settings are gone or reset to defaults
        var settingsAfter = irisSettingsService.getCourseSettingsWithRateLimit(course.getId());
        assertThat(settingsAfter.settings()).isEqualTo(IrisCourseSettingsDTO.defaultSettings());
    }
}
