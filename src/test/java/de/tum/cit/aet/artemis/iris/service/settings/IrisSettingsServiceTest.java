package de.tum.cit.aet.artemis.iris.service.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisAskUserModeSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseSettingsRepository;

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
    void getSettingsForCourse_returnsDefaultsWhenNoSettingsExist() {
        var settings = irisSettingsService.getSettingsForCourse(course);

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.askUserModeEnabled()).isTrue();
        assertThat(settings.askUserModeSettings()).isEqualTo(IrisAskUserModeSettings.defaultSettings());
        assertThat(settings.customInstructions()).isNull();
        assertThat(settings.variant()).isEqualTo(IrisPipelineVariant.DEFAULT);
        assertThat(settings.rateLimit()).isNull();
    }

    @Test
    void getSettingsForCourse_returnsStoredSettings() {
        var customRateLimit = new IrisRateLimitConfiguration(12, 6);
        var askUserModeSettings = new IrisAskUserModeSettings(2, 6, 40, 20);
        var payload = IrisCourseSettings.of(false, false, askUserModeSettings, "stored", IrisPipelineVariant.ADVANCED, IrisSupportLevel.MODERATE, customRateLimit);
        irisSettingsService.updateCourseSettings(course.getId(), payload, true);

        var settings = irisSettingsService.getSettingsForCourse(course.getId());

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.askUserModeEnabled()).isFalse();
        assertThat(settings.askUserModeSettings()).isEqualTo(askUserModeSettings);
        assertThat(settings.customInstructions()).isEqualTo("stored");
        assertThat(settings.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(settings.rateLimit()).isEqualTo(customRateLimit);
    }

    @Test
    void getSettingsForCourse_throwsOnNullCourse() {
        assertThatThrownBy(() -> irisSettingsService.getSettingsForCourse((Course) null)).isInstanceOf(NullPointerException.class).hasMessageContaining("course must not be null");
    }

    @Test
    void isEnabledForCourse_usesDefaultWhenMissing() {
        assertThat(irisSettingsService.isEnabledForCourse(course.getId())).isTrue();
    }

    @Test
    void isEnabledForCourse_reflectsDisabledCourse() {
        disableIrisFor(course);

        assertThat(irisSettingsService.isEnabledForCourse(course)).isFalse();
        assertThat(irisSettingsService.isEnabledForCourse(course.getId())).isFalse();
    }

    @Test
    void isEnabledForCourse_throwsOnNullCourse() {
        assertThatThrownBy(() -> irisSettingsService.isEnabledForCourse((Course) null)).isInstanceOf(NullPointerException.class).hasMessageContaining("course must not be null");
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
        var payload = IrisCourseSettings.of(false, "  keep trimmed  ", IrisPipelineVariant.ADVANCED, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(100, 24));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, true);

        assertThat(dto.settings().customInstructions()).isEqualTo("keep trimmed");
        assertThat(dto.settings().rateLimit().requests()).isEqualTo(100);
        assertThat(dto.settings().rateLimit().timeframeHours()).isEqualTo(24);
    }

    @Test
    void updateCourseSettings_rejectsNegativeRateLimit() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(-1, 24));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload, true)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Rate limit requests must be 0 or greater");
    }

    @Test
    void updateCourseSettings_rejectsPartialRateLimit_onlyRequests() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(100, null));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload, true)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Both rate limit fields must be filled or both must be empty");
    }

    @Test
    void updateCourseSettings_rejectsPartialRateLimit_onlyTimeframe() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(null, 24));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload, true)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Both rate limit fields must be filled or both must be empty");
    }

    @Test
    void updateCourseSettings_rejectsZeroTimeframe() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(100, 0));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload, true)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Rate limit timeframe must be greater than 0");
    }

    @Test
    void updateCourseSettings_rejectsNegativeTimeframe() {
        enableIrisFor(course);

        var invalidPayload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(100, -5));

        assertThatThrownBy(() -> irisSettingsService.updateCourseSettings(course.getId(), invalidPayload, true)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Rate limit timeframe must be greater than 0");
    }

    @Test
    void updateCourseSettings_acceptsBothFieldsEmpty() {
        enableIrisFor(course);

        // Both null = use defaults (should return null rateLimit)
        var payload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(null, null));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, true);

        assertThat(dto.settings().rateLimit()).isNull();
    }

    @Test
    void updateCourseSettings_acceptsNullRateLimit() {
        enableIrisFor(course);

        // null rateLimit = use defaults
        var payload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, null);

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, true);

        assertThat(dto.settings().rateLimit()).isNull();
    }

    @Test
    void updateCourseSettings_acceptsBothFieldsFilled() {
        enableIrisFor(course);

        var payload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(100, 24));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, true);

        assertThat(dto.settings().rateLimit()).isNotNull();
        assertThat(dto.settings().rateLimit().requests()).isEqualTo(100);
        assertThat(dto.settings().rateLimit().timeframeHours()).isEqualTo(24);
    }

    @Test
    void updateCourseSettings_acceptsZeroRequests() {
        enableIrisFor(course);

        // 0 requests means "blocking" (no requests allowed) - should be allowed as a valid configuration
        var payload = IrisCourseSettings.of(true, null, null, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(0, 24));

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, true);

        // 0 is stored as-is (rate limit service treats it as blocking)
        assertThat(dto.settings().rateLimit()).isNotNull();
        assertThat(dto.settings().rateLimit().requests()).isZero();
        assertThat(dto.settings().rateLimit().timeframeHours()).isEqualTo(24);
    }

    @Test
    void sanitizePayload_returnsDefaultSettingsForNull() {
        var sanitized = irisSettingsService.sanitizePayload(null);

        assertThat(sanitized).isEqualTo(IrisCourseSettings.defaultSettings());
    }

    @Test
    void sanitizePayload_convertsEmptyRateLimitToNull() {
        var payload = IrisCourseSettings.of(true, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(null, null));

        var sanitized = irisSettingsService.sanitizePayload(payload);

        assertThat(sanitized.rateLimit()).isNull();
    }

    @Test
    void sanitizePayload_preservesNullRateLimit() {
        var payload = IrisCourseSettings.of(true, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        var sanitized = irisSettingsService.sanitizePayload(payload);

        assertThat(sanitized.rateLimit()).isNull();
    }

    @Test
    void sanitizePayload_acceptsValidRateLimit() {
        var payload = IrisCourseSettings.of(true, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, new IrisRateLimitConfiguration(5, 2));

        var sanitized = irisSettingsService.sanitizePayload(payload);

        assertThat(sanitized.rateLimit()).isEqualTo(new IrisRateLimitConfiguration(5, 2));
    }

    @Test
    void sanitizePayload_preservesSupportLevel() {
        var payload = IrisCourseSettings.of(true, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.HIGH, null);

        var sanitized = irisSettingsService.sanitizePayload(payload);

        assertThat(sanitized.supportLevel()).isEqualTo(IrisSupportLevel.HIGH);
    }

    @Test
    void sanitizePayload_preservesAskUserModeEnabled() {
        var payload = IrisCourseSettings.of(true, false, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        var sanitized = irisSettingsService.sanitizePayload(payload);

        assertThat(sanitized.askUserModeEnabled()).isFalse();
    }

    @Test
    void sanitizePayload_preservesAskUserModeSettings() {
        var askUserModeSettings = new IrisAskUserModeSettings(2, 7, 45, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        var sanitized = irisSettingsService.sanitizePayload(payload);

        assertThat(sanitized.askUserModeSettings()).isEqualTo(askUserModeSettings);
    }

    @Test
    void sanitizePayload_rejectsAskUserModeMinQuestionsGreaterThanMaxQuestions() {
        var askUserModeSettings = new IrisAskUserModeSettings(7, 2, 45, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Minimum questions must not exceed maximum questions");
    }

    @Test
    void updateCourseSettings_asInstructor_canChangeAskUserModeSettings() {
        enableIrisFor(course);
        var current = irisSettingsService.getSettingsForCourse(course.getId());
        var askUserModeSettings = new IrisAskUserModeSettings(2, 6, 45, 20);
        var payload = IrisCourseSettings.of(current.enabled(), current.askUserModeEnabled(), askUserModeSettings, current.customInstructions(), current.variant(),
                current.supportLevel(), current.rateLimit());

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, false);

        assertThat(dto.settings().askUserModeSettings()).isEqualTo(askUserModeSettings);
        assertThat(irisSettingsService.getSettingsForCourse(course.getId()).askUserModeSettings()).isEqualTo(askUserModeSettings);
    }

    @Test
    void updateCourseSettings_asInstructor_canChangeAskUserModeEnabled() {
        enableIrisFor(course);
        var current = irisSettingsService.getSettingsForCourse(course.getId());
        var payload = IrisCourseSettings.of(current.enabled(), false, current.customInstructions(), current.variant(), current.supportLevel(), current.rateLimit());

        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, false);

        assertThat(dto.settings().askUserModeEnabled()).isFalse();
        assertThat(irisSettingsService.getSettingsForCourse(course.getId()).askUserModeEnabled()).isFalse();
    }

    @Test
    void updateCourseSettings_asInstructor_canChangeSupportLevel() {
        enableIrisFor(course);
        var current = irisSettingsService.getSettingsForCourse(course.getId());
        var payload = IrisCourseSettings.of(current.enabled(), current.askUserModeEnabled(), current.customInstructions(), current.variant(), IrisSupportLevel.LOW,
                current.rateLimit());

        // isAdmin = false: support level is intentionally instructor-editable, so this must not throw
        var dto = irisSettingsService.updateCourseSettings(course.getId(), payload, false);

        assertThat(dto.settings().supportLevel()).isEqualTo(IrisSupportLevel.LOW);
        assertThat(irisSettingsService.getSettingsForCourse(course.getId()).supportLevel()).isEqualTo(IrisSupportLevel.LOW);
    }

    @Test
    void getSettingsForExercise_returnsCourseLevelSettings() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        configureCourseSettings(course, "exercise instructions", IrisPipelineVariant.ADVANCED);

        var settings = irisSettingsService.getSettingsForExercise(exercise);

        assertThat(settings.customInstructions()).isEqualTo("exercise instructions");
        assertThat(settings.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
    }

    @Test
    void getSettingsForExercise_throwsOnNullExercise() {
        assertThatThrownBy(() -> irisSettingsService.getSettingsForExercise(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("exercise must not be null");
    }

    @Test
    void isExerciseChatEnabledForExercise_reflectsCourseLevelEnabledFlag() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        assertThat(irisSettingsService.isExerciseChatEnabledForExercise(exercise)).isTrue();

        disableIrisFor(course);

        assertThat(irisSettingsService.isExerciseChatEnabledForExercise(exercise)).isFalse();
    }

    @Test
    void isAskUserModeEnabledForExercise_requiresBothIrisAndAskUserModeEnabled() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        assertThat(irisSettingsService.isAskUserModeEnabledForExercise(exercise)).isTrue();

        var current = irisSettingsService.getSettingsForCourse(course.getId());
        irisSettingsService.updateCourseSettings(course.getId(),
                IrisCourseSettings.of(current.enabled(), false, current.customInstructions(), current.variant(), current.supportLevel(), current.rateLimit()), true);

        assertThat(irisSettingsService.isAskUserModeEnabledForExercise(exercise)).isFalse();
    }

    @Test
    void isAskUserModeEnabledForExercise_isFalseWhenIrisDisabledEvenIfAskUserModeEnabled() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        disableIrisFor(course);

        assertThat(irisSettingsService.isAskUserModeEnabledForExercise(exercise)).isFalse();
    }

    @Test
    void ensureExerciseChatEnabledForExerciseOrElseThrow_doesNotThrowWhenEnabled() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        assertThatCode(() -> irisSettingsService.ensureExerciseChatEnabledForExerciseOrElseThrow(exercise)).doesNotThrowAnyException();
    }

    @Test
    void ensureExerciseChatEnabledForExerciseOrElseThrow_throwsWhenDisabled() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        disableIrisFor(course);

        assertThatThrownBy(() -> irisSettingsService.ensureExerciseChatEnabledForExerciseOrElseThrow(exercise)).isInstanceOf(AccessForbiddenAlertException.class)
                .hasMessageContaining("Iris is disabled for exercise " + exercise.getId());
    }

    @Test
    void ensureAskUserModeEnabledForExerciseOrElseThrow_doesNotThrowWhenEnabled() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        assertThatCode(() -> irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise)).doesNotThrowAnyException();
    }

    @Test
    void ensureAskUserModeEnabledForExerciseOrElseThrow_throwsWhenAskUserModeDisabled() {
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        var current = irisSettingsService.getSettingsForCourse(course.getId());
        irisSettingsService.updateCourseSettings(course.getId(),
                IrisCourseSettings.of(current.enabled(), false, current.customInstructions(), current.variant(), current.supportLevel(), current.rateLimit()), true);

        assertThatThrownBy(() -> irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise)).isInstanceOf(AccessForbiddenAlertException.class)
                .hasMessageContaining("Iris ask-user mode is disabled for exercise " + exercise.getId());
    }

    @Test
    void sanitizePayload_rejectsAskUserModeMinQuestionsBelowOne() {
        var askUserModeSettings = new IrisAskUserModeSettings(0, 5, 45, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode question limits must be greater than 0");
    }

    @Test
    void sanitizePayload_rejectsAskUserModeMaxQuestionsBelowOne() {
        var askUserModeSettings = new IrisAskUserModeSettings(1, 0, 45, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode question limits must be greater than 0");
    }

    @Test
    void sanitizePayload_rejectsAskUserModeMinQuestionsAboveLimit() {
        var askUserModeSettings = new IrisAskUserModeSettings(11, 11, 45, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode question limits are too high");
    }

    @Test
    void sanitizePayload_rejectsAskUserModeMaxQuestionsAboveLimit() {
        var askUserModeSettings = new IrisAskUserModeSettings(1, 11, 45, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode question limits are too high");
    }

    @Test
    void sanitizePayload_rejectsAskUserModeQuestionTimeLimitBelowOne() {
        var askUserModeSettings = new IrisAskUserModeSettings(1, 5, 0, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode question time limit is invalid");
    }

    @Test
    void sanitizePayload_rejectsAskUserModeQuestionTimeLimitAboveMax() {
        var askUserModeSettings = new IrisAskUserModeSettings(1, 5, 181, 20);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode question time limit is invalid");
    }

    @Test
    void sanitizePayload_rejectsAskUserModeInClassTimeLimitBelowOne() {
        var askUserModeSettings = new IrisAskUserModeSettings(1, 5, 45, 0);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode in-class quiz time limit is invalid");
    }

    @Test
    void sanitizePayload_rejectsAskUserModeInClassTimeLimitAboveMax() {
        var askUserModeSettings = new IrisAskUserModeSettings(1, 5, 45, 31);
        var payload = IrisCourseSettings.of(true, true, askUserModeSettings, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        assertThatThrownBy(() -> irisSettingsService.sanitizePayload(payload)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Ask-user mode in-class quiz time limit is invalid");
    }

    @Test
    void sanitizePayload_defaultsAskUserModeSettingsWhenNull() {
        var payload = IrisCourseSettings.of(true, true, null, "instructions", IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null);

        var sanitized = irisSettingsService.sanitizePayload(payload);

        assertThat(sanitized.askUserModeSettings()).isEqualTo(IrisAskUserModeSettings.defaultSettings());
    }

    @Test
    void getApplicationRateLimitDefaults_returnsUnlimitedForMinusOnes() {
        var service = createServiceWithDefaults(-1, -1);

        var defaults = service.getApplicationRateLimitDefaults();

        assertThat(defaults.requests()).isNull();
        assertThat(defaults.timeframeHours()).isNull();
    }

    @Test
    void getApplicationRateLimitDefaults_returnsUnlimitedForLegacyZeros() {
        var service = createServiceWithDefaults(0, 0);

        var defaults = service.getApplicationRateLimitDefaults();

        assertThat(defaults.requests()).isNull();
        assertThat(defaults.timeframeHours()).isNull();
    }

    @Test
    void getApplicationRateLimitDefaults_sanitizesNegativeAndZeroValues() {
        var service = createServiceWithDefaults(-5, 0);

        var defaults = service.getApplicationRateLimitDefaults();

        assertThat(defaults.requests()).isZero();
        assertThat(defaults.timeframeHours()).isEqualTo(1);
    }

    @Test
    void getApplicationRateLimitDefaults_returnsZeroOneWhenConfigNotLegacy() {
        var service = createServiceWithDefaults(0, 1);

        var defaults = service.getApplicationRateLimitDefaults();

        assertThat(defaults.requests()).isZero();
        assertThat(defaults.timeframeHours()).isEqualTo(1);
    }

    @Test
    void getApplicationRateLimitDefaults_sanitizesNegativeTimeframe() {
        var service = createServiceWithDefaults(5, -2);

        var defaults = service.getApplicationRateLimitDefaults();

        assertThat(defaults.requests()).isEqualTo(5);
        assertThat(defaults.timeframeHours()).isEqualTo(1);
    }

    @Test
    void getApplicationRateLimitDefaults_sanitizesNegativeRequests() {
        var service = createServiceWithDefaults(-3, 4);

        var defaults = service.getApplicationRateLimitDefaults();

        assertThat(defaults.requests()).isZero();
        assertThat(defaults.timeframeHours()).isEqualTo(4);
    }

    @Test
    void getApplicationRateLimitDefaults_returnsConfiguredValues() {
        var service = createServiceWithDefaults(20, 12);

        var defaults = service.getApplicationRateLimitDefaults();

        assertThat(defaults.requests()).isEqualTo(20);
        assertThat(defaults.timeframeHours()).isEqualTo(12);
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
    void getSettingsForCourseOrThrow_returnsDefaultsWhenNoSettingsExist() {
        var settings = irisSettingsService.getSettingsForCourseOrThrow(course.getId());

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.askUserModeEnabled()).isTrue();
        assertThat(settings.askUserModeSettings()).isEqualTo(IrisAskUserModeSettings.defaultSettings());
        assertThat(settings.customInstructions()).isNull();
        assertThat(settings.variant()).isEqualTo(IrisPipelineVariant.DEFAULT);
        assertThat(settings.rateLimit()).isNull();
    }

    private IrisSettingsService createServiceWithDefaults(int defaultLimit, int defaultTimeframeHours) {
        return new IrisSettingsService(mock(IrisCourseSettingsRepository.class), mock(CourseRepository.class), defaultLimit, defaultTimeframeHours);
    }
}
