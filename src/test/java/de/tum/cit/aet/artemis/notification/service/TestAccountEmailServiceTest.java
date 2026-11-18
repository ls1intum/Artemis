package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.notification.domain.setting_presets.IgnoreUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingPresetTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingSpecificationTestRepository;

class TestAccountEmailServiceTest {

    private static final String TEST_USER = "test_user_17";

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

        policy = new TestAccountEmailService(globalNotificationSettingRepository, specificationRepository, presetRepository, presetRegistry);
    }

    @Test
    void shouldNotAskTheDatabaseAboutAccountsThatAreNoTestUsers() {
        assertThat(policy.suppressesEmailTo("ge12abc", false)).isFalse();
        assertThat(policy.suppressesEmailTo(null, false)).isFalse();

        verifyNoInteractions(globalNotificationSettingRepository, specificationRepository, presetRepository);
    }

    @Test
    void shouldNotSuppressEmailToATestUserWithoutALogin() {
        assertThat(policy.suppressesEmailTo(null, true)).isFalse();
    }

    @Test
    void shouldSuppressEmailToATestUserThatHasNotOptedInToAnything() {
        assertThat(policy.suppressesEmailTo(TEST_USER, true)).isTrue();
    }

    @Test
    void shouldStopSuppressingWhenTheAccountSwitchedAGlobalNotificationOn() {
        when(globalNotificationSettingRepository.existsEnabledSettingByUserLogin(TEST_USER)).thenReturn(true);

        assertThat(policy.suppressesEmailTo(TEST_USER, true)).isFalse();
    }

    @Test
    void shouldStopSuppressingWhenTheAccountEnabledEmailInACourseSpecification() {
        when(specificationRepository.existsEmailEnabledByUserLogin(TEST_USER)).thenReturn(true);

        assertThat(policy.suppressesEmailTo(TEST_USER, true)).isFalse();
    }

    @Test
    void shouldStopSuppressingWhenTheAccountChoseAPresetThatDeliversEmail() {
        when(presetRepository.existsChosenPresetByUserLoginOtherThan(anyString(), any())).thenReturn(true);

        assertThat(policy.suppressesEmailTo(TEST_USER, true)).isFalse();
    }

    @Test
    void shouldNotCountTheCustomPresetOrThePresetThatSwitchesEverythingOffAsAChoiceForEmail() {
        policy.suppressesEmailTo(TEST_USER, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Short>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(presetRepository).existsChosenPresetByUserLoginOtherThan(anyString(), excluded.capture());
        // The custom preset is described by specifications, which are asked for separately; "ignore" is a choice not to receive anything.
        assertThat(excluded.getValue()).containsExactlyInAnyOrder((short) 0, (short) 3);
    }

    @Test
    void shouldStillExcludeTheCustomPresetWhenTheIgnorePresetIsUnknown() {
        when(presetRegistry.getPresetId(IgnoreUserCourseNotificationSettingPreset.class)).thenReturn(null);

        policy.suppressesEmailTo(TEST_USER, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Short>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(presetRepository).existsChosenPresetByUserLoginOtherThan(anyString(), excluded.capture());
        assertThat(excluded.getValue()).containsExactly((short) 0);
    }
}
