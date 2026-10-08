package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.notification.config.MailDeliveryProperties;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.IgnoreUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingPresetTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingSpecificationTestRepository;

class TestAccountEmailServiceTest {

    private static final String TEST_ACCOUNT = "artemis_test_user_17";

    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private UserCourseNotificationSettingSpecificationTestRepository specificationRepository;

    private UserCourseNotificationSettingPresetTestRepository presetRepository;

    private CourseNotificationSettingPresetRegistryService presetRegistry;

    private TestAccountEmailService policy;

    @BeforeEach
    void setUp() {
        globalNotificationSettingRepository = mock(GlobalNotificationSettingRepository.class);
        specificationRepository = mock(UserCourseNotificationSettingSpecificationTestRepository.class);
        presetRepository = mock(UserCourseNotificationSettingPresetTestRepository.class);
        presetRegistry = mock(CourseNotificationSettingPresetRegistryService.class);
        when(presetRegistry.getPresetId(IgnoreUserCourseNotificationSettingPreset.class)).thenReturn(3);

        var properties = new MailDeliveryProperties();
        properties.setTestAccountLoginPatterns(List.of("artemis_test_user_\\d+", "load-test-.*"));
        policy = new TestAccountEmailService(properties, globalNotificationSettingRepository, specificationRepository, presetRepository, presetRegistry);
    }

    @Test
    void shouldRecognizeALoginThatMatchesAPatternCompletely() {
        assertThat(policy.isTestAccount("artemis_test_user_1")).isTrue();
        assertThat(policy.isTestAccount("artemis_test_user_2000")).isTrue();
        assertThat(policy.isTestAccount("load-test-batch-7")).isTrue();
    }

    @Test
    void shouldNotRecognizeALoginThatOnlyContainsAPattern() {
        assertThat(policy.isTestAccount("artemis_test_user_")).isFalse();
        assertThat(policy.isTestAccount("artemis_test_user_1x")).isFalse();
        assertThat(policy.isTestAccount("my_artemis_test_user_1")).isFalse();
        assertThat(policy.isTestAccount("ge12abc")).isFalse();
        assertThat(policy.isTestAccount("")).isFalse();
        assertThat(policy.isTestAccount(null)).isFalse();
    }

    @Test
    void shouldRecognizeNoTestAccountWhenNoPatternIsConfigured() {
        var withoutPatterns = new TestAccountEmailService(new MailDeliveryProperties(), globalNotificationSettingRepository, specificationRepository, presetRepository,
                presetRegistry);

        assertThat(withoutPatterns.isTestAccount("artemis_test_user_1")).isFalse();
        assertThat(withoutPatterns.isEmailEnabledByDefault("artemis_test_user_1")).isTrue();
        assertThat(withoutPatterns.suppressesEmailTo("artemis_test_user_1")).isFalse();
    }

    @Test
    void shouldNameThePropertyAndThePatternWhenAPatternIsNotARegularExpression() {
        var invalid = new MailDeliveryProperties();
        invalid.setTestAccountLoginPatterns(List.of("artemis_test_user_(\\d+"));

        assertThatThrownBy(() -> new TestAccountEmailService(invalid, globalNotificationSettingRepository, specificationRepository, presetRepository, presetRegistry))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("artemis.mail.test-account-login-patterns").hasMessageContaining("artemis_test_user_(\\d+");
    }

    @Test
    void shouldEnableEmailByDefaultForEverybodyExceptTestAccounts() {
        assertThat(policy.isEmailEnabledByDefault("ge12abc")).isTrue();
        assertThat(policy.isEmailEnabledByDefault(null)).isTrue();
        assertThat(policy.isEmailEnabledByDefault(TEST_ACCOUNT)).isFalse();
    }

    @Test
    void shouldNotAskTheDatabaseAboutAccountsThatAreNoTestAccounts() {
        assertThat(policy.suppressesEmailTo("ge12abc")).isFalse();
        assertThat(policy.suppressesEmailTo(null)).isFalse();

        verifyNoInteractions(globalNotificationSettingRepository, specificationRepository, presetRepository);
    }

    @Test
    void shouldSuppressEmailToATestAccountThatHasNotOptedInToAnything() {
        assertThat(policy.suppressesEmailTo(TEST_ACCOUNT)).isTrue();
    }

    @Test
    void shouldStopSuppressingWhenTheAccountSwitchedAGlobalNotificationOn() {
        when(globalNotificationSettingRepository.existsEnabledSettingByUserLogin(TEST_ACCOUNT)).thenReturn(true);

        assertThat(policy.suppressesEmailTo(TEST_ACCOUNT)).isFalse();
    }

    @Test
    void shouldStopSuppressingWhenTheAccountEnabledEmailInACourseSpecification() {
        when(specificationRepository.existsEmailEnabledByUserLogin(TEST_ACCOUNT)).thenReturn(true);

        assertThat(policy.suppressesEmailTo(TEST_ACCOUNT)).isFalse();
    }

    @Test
    void shouldStopSuppressingWhenTheAccountChoseAPresetThatDeliversEmail() {
        when(presetRepository.existsChosenPresetByUserLoginOtherThan(anyString(), any())).thenReturn(true);

        assertThat(policy.suppressesEmailTo(TEST_ACCOUNT)).isFalse();
    }

    @Test
    void shouldNotCountTheCustomPresetOrThePresetThatSwitchesEverythingOffAsAChoiceForEmail() {
        policy.suppressesEmailTo(TEST_ACCOUNT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Short>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(presetRepository).existsChosenPresetByUserLoginOtherThan(anyString(), excluded.capture());
        // The custom preset is described by specifications, which are asked for separately; "ignore" is a choice not to receive anything.
        assertThat(excluded.getValue()).containsExactlyInAnyOrder((short) 0, (short) 3);
    }

    @Test
    void shouldStillExcludeTheCustomPresetWhenTheIgnorePresetIsUnknown() {
        when(presetRegistry.getPresetId(IgnoreUserCourseNotificationSettingPreset.class)).thenReturn(null);

        policy.suppressesEmailTo(TEST_ACCOUNT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Short>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(presetRepository).existsChosenPresetByUserLoginOtherThan(anyString(), excluded.capture());
        assertThat(excluded.getValue()).containsExactly((short) 0);
    }
}
