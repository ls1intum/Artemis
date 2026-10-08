package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.notification.config.MailDeliveryProperties;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.IgnoreUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingSpecificationRepository;

/**
 * Keeps test accounts silent until they ask for e-mail.
 * <p>
 * An account whose login matches one of the configured patterns ({@code artemis.mail.test-account-login-patterns}) is a
 * test account. Everywhere else a notification setting that was never configured means "send", because a person who
 * never opened their settings expects the notifications Artemis is built to send. For a test account it means "do not
 * send": such accounts are created in the thousands by scripts and end-to-end tests, none of them waits for a mail, and
 * every mail they trigger is counted against the quota of the one SMTP account the whole deployment shares. A setting
 * the account chose explicitly always wins, so a test that is about e-mail enables what it needs and receives exactly that.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class TestAccountEmailService {

    /**
     * The preset that is described by specification rows instead of by a preset of its own.
     */
    private static final short CUSTOM_PRESET_ID = 0;

    private final List<Pattern> testAccountLoginPatterns;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    public TestAccountEmailService(MailDeliveryProperties properties, GlobalNotificationSettingRepository globalNotificationSettingRepository,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService) {
        List<Pattern> patterns = new ArrayList<>();
        for (String pattern : properties.getTestAccountLoginPatterns()) {
            try {
                patterns.add(Pattern.compile(pattern));
            }
            catch (PatternSyntaxException e) {
                throw new IllegalStateException("Invalid regular expression in artemis.mail.test-account-login-patterns: '" + pattern + "'", e);
            }
        }
        this.testAccountLoginPatterns = List.copyOf(patterns);
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.courseNotificationSettingPresetRegistryService = courseNotificationSettingPresetRegistryService;
    }

    /**
     * @param login the login to check
     * @return whether the login matches one of the configured test-account patterns
     */
    public boolean isTestAccount(@Nullable String login) {
        return login != null && testAccountLoginPatterns.stream().anyMatch(pattern -> pattern.matcher(login).matches());
    }

    /**
     * What a notification setting that was never configured means for this account.
     *
     * @param login the login of the account
     * @return true for everybody except test accounts
     */
    public boolean isEmailEnabledByDefault(@Nullable String login) {
        return !isTestAccount(login);
    }

    /**
     * Whether no e-mail is to be sent to this account at all, whatever the kind of mail. This is the safety net for the
     * mails that have no notification setting of their own (activation, password reset, notices about the retention of
     * data), where nothing else would decide whether a test account is meant to receive them.
     * <p>
     * A test account that has explicitly enabled something about e-mail has shown that it wants e-mail, so nothing is
     * suppressed for it here. Which mail it then receives is decided by the settings of each kind of mail.
     *
     * @param login the login of the recipient
     * @return true for a test account that has not opted in to any e-mail
     */
    public boolean suppressesEmailTo(@Nullable String login) {
        return login != null && isTestAccount(login) && !hasOptedInToEmail(login);
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
