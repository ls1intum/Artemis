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
    void getCourseSettingsDTO_usesApplicationDefaultsWhenOverridesMissing() {
        enableIrisFor(course);

        var dto = irisSettingsService.getCourseSettingsDTO(course.getId());

        assertThat(dto.settings().enabled()).isTrue();
        assertThat(dto.applicationRateLimitDefaults()).isNotNull();
        assertThat(dto.effectiveRateLimit()).isNotNull();
    }

    @Test
    void updateCourseSettings_sanitizesRateLimitAndInstructions() {
        var payload = IrisCourseSettingsDTO.of(false, "  keep trimmed  ", IrisPipelineVariant.ADVANCED, new IrisRateLimitConfiguration(0, 5));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload);

        assertThat(dto.settings().customInstructions()).isEqualTo("keep trimmed");
        assertThat(dto.settings().rateLimit().requests()).isNull(); // 0 normalized to null
        assertThat(dto.settings().rateLimit().timeframeHours()).isEqualTo(5);
    }

    @Test
    void updateCourseSettings_rejectsNegativeRateLimit() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettingsDTO.of(true, null, null, new IrisRateLimitConfiguration(-1, null));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload)).isInstanceOf(BadRequestAlertException.class);
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
        var settingsBefore = irisSettingsService.getCourseSettingsDTO(course.getId());
        assertThat(settingsBefore.settings().enabled()).isTrue();

        // Delete settings
        irisSettingsService.deleteSettingsFor(course);

        // Verify settings are gone or reset to defaults
        var settingsAfter = irisSettingsService.getCourseSettingsDTO(course.getId());
        assertThat(settingsAfter.settings()).isEqualTo(IrisCourseSettingsDTO.defaultSettings());
    }

    @Test
    void deleteSettingsFor_byCourseId() {
        enableIrisFor(course);

        // Verify settings exist
        var settingsBefore = irisSettingsService.getCourseSettingsDTO(course.getId());
        assertThat(settingsBefore.settings().enabled()).isTrue();

        // Delete settings
        irisSettingsService.deleteSettingsFor(course.getId());

        // Verify settings are gone or reset to defaults
        var settingsAfter = irisSettingsService.getCourseSettingsDTO(course.getId());
        assertThat(settingsAfter.settings()).isEqualTo(IrisCourseSettingsDTO.defaultSettings());
    }
}
