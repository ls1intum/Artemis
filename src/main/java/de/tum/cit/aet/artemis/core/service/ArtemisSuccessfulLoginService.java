package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.util.ClientEnvironment;

/**
 * Listener for successful authentication events in the Artemis system.
 * This component listens for successful login attempts and sends email notifications
 * to users when they successfully log in.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class ArtemisSuccessfulLoginService {

    private static final Logger log = LoggerFactory.getLogger(ArtemisSuccessfulLoginService.class);

    private final URL artemisServerUrl;

    private final String passwordResetLinkEnUrl;

    private final String passwordResetLinkDeUrl;

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    public ArtemisSuccessfulLoginService(UserRepository userRepository, MailSendingService mailSendingService,
            GlobalNotificationSettingRepository globalNotificationSettingRepository, @Value("${server.url}") URL artemisServerUrl,
            @Value("${artemis.user-management.password-reset.links.en:#{null}}") Optional<String> passwordResetLinkEnUrl,
            @Value("${artemis.user-management.password-reset.links.de:#{null}}") Optional<String> passwordResetLinkDeUrl) {
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.artemisServerUrl = artemisServerUrl;

        this.passwordResetLinkEnUrl = getResetLinkOrDefault(passwordResetLinkEnUrl);
        this.passwordResetLinkDeUrl = getResetLinkOrDefault(passwordResetLinkDeUrl);
    }

    /**
     * Returns a non-empty, non-placeholder reset link.
     *
     * @param resetLink The configured reset link.
     * @return The reset link, or if the configured link is empty or set to a placeholder, it uses the default link.
     */
    private String getResetLinkOrDefault(final Optional<String> resetLink) {
        final String defaultPasswordResetLink = artemisServerUrl + "/account/reset/request";

        if (isEmptyOrDefaultLink(resetLink)) {
            log.info("No password reset link configured, using default link {}", defaultPasswordResetLink);
            return defaultPasswordResetLink;
        }
        else {
            return resetLink.orElseThrow();
        }
    }

    private boolean isEmptyOrDefaultLink(final Optional<String> link) {
        if (link.isEmpty()) {
            return true;
        }
        else {
            final String configurationPlaceholder = "<link>";
            final String configuredLink = link.get();
            return configuredLink.isBlank() || configurationPlaceholder.equals(configuredLink);
        }
    }

    /**
     * Handles successful authentication events.
     * Sends a login notification email to users when they successfully authenticate.
     *
     * @param loginOrEmail         the username or email of the user who has successfully logged in
     * @param authenticationMethod the method used for authentication
     * @param clientEnvironment    the environment information of the client (optional)
     * @see AuthenticationMethod for available authentication methods
     */
    public void sendLoginEmail(String loginOrEmail, AuthenticationMethod authenticationMethod, @Nullable ClientEnvironment clientEnvironment) {
        try {
            User recipient;

            if (SecurityUtils.isEmail(loginOrEmail)) {
                recipient = userRepository.getUserByEmailElseThrow(loginOrEmail);
            }
            else {
                recipient = userRepository.getUserByLoginElseThrow(loginOrEmail);
            }

            if (!globalNotificationSettingRepository.isNotificationEnabled(recipient.getId(), GlobalNotificationType.NEW_LOGIN)) {
                return;
            }

            String localeKey = recipient.getLangKey();
            if (localeKey == null) {
                log.warn("User {} has no language set, using default language 'en'", loginOrEmail);
                localeKey = "en";
            }
            Language language = Language.fromLanguageShortName(localeKey);

            var contextVariables = new HashMap<String, Object>();
            contextVariables.put("authenticationMethod", authenticationMethod.getEmailDisplayName(language));
            ZonedDateTime now = ZonedDateTime.now();
            contextVariables.put("loginDate", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            contextVariables.put("loginTime", now.format(DateTimeFormatter.ofPattern("HH:mm:ss '('VV')'")));

            String environmentInfo = clientEnvironment != null ? clientEnvironment.getEnvironmentInfo(language) : ClientEnvironment.getUnknownEnvironmentDisplayName(language);
            contextVariables.put("requestOrigin", environmentInfo);

            if (recipient.isInternal()) {
                contextVariables.put("resetLink", artemisServerUrl.toString() + "/account/password");
            }
            else {
                if (language == Language.GERMAN) {
                    contextVariables.put("resetLink", passwordResetLinkDeUrl);
                }
                else {
                    contextVariables.put("resetLink", passwordResetLinkEnUrl);
                }
            }

            mailSendingService.buildAndSendAsync(recipient, "email.notification.login.title", "mail/notification/newLoginEmail", contextVariables);
        }
        catch (EntityNotFoundException ignored) {
            log.error("User with login {} not found when trying to send newLoginEmail", loginOrEmail);
        }
    }
}
