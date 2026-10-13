package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.notification.domain.setting_presets.IgnoreUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingSpecificationRepository;

/**
 * Keeps test users silent until they ask for e-mail.
 * <p>
 * A test user is an account whose {@code isTestUser} flag is set (in the user management, or by the CSV import of users). Everywhere
 * else a notification setting that was never configured means "send", because a person who never opened their settings expects the
 * notifications Artemis is built to send. For a test user it means "do not send": such accounts are created in the thousands by scripts
 * and end-to-end tests, none of them waits for a mail, and every mail they trigger is counted against the quota of the one SMTP
 * account the whole deployment shares. A setting the account chose explicitly always wins, so a test that is about e-mail enables what
 * it needs and receives exactly that.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class TestAccountEmailService {

    /**
     * The preset that is described by specification rows instead of by a preset of its own.
     */
    private static final short CUSTOM_PRESET_ID = 0;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    public TestAccountEmailService(GlobalNotificationSettingRepository globalNotificationSettingRepository,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService) {
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.courseNotificationSettingPresetRegistryService = courseNotificationSettingPresetRegistryService;
    }

    /**
     * Whether no e-mail is to be sent to this account at all, whatever the kind of mail. This is the safety net for the
     * mails that have no notification setting of their own (activation, password reset, notices about the retention of
     * data), where nothing else would decide whether a test account is meant to receive them.
     * <p>
     * A test account that has explicitly enabled something about e-mail has shown that it wants e-mail, so nothing is
     * suppressed for it here. Which mail it then receives is decided by the settings of each kind of mail.
     *
     * @param login    the login of the recipient
     * @param testUser whether the recipient is a test user
     * @return true for a test user that has not opted in to any e-mail
     */
    public boolean suppressesEmailTo(@Nullable String login, boolean testUser) {
        return testUser && login != null && !hasOptedInToEmail(login);
    }

    private boolean hasOptedInToEmail(String login) {
        return globalNotificationSettingRepository.existsEnabledSettingByUserLogin(login)
                || userCourseNotificationSettingSpecificationRepository.existsEmailEnabledByUserLogin(login)
                || userCourseNotificationSettingPresetRepository.existsChosenPresetByUserLoginOtherThan(login, presetsThatDoNotDeliverEmail());
    }

    private Set<Short> presetsThatDoNotDeliverEmail() {
        Integer ignorePresetId = courseNotificationSettingPresetRegistryService.getPresetId(IgnoreUserCourseNotificationSettingPreset.class);
        return ignorePresetId == null ? Set.of(CUSTOM_PRESET_ID) : Set.of(CUSTOM_PRESET_ID, ignorePresetId.shortValue());
    }
}
