package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.util.ClientEnvironment;

/**
 * Listener for successful authentication events in the Artemis system.
 * This component listens for successful login attempts and sends email notifications
 * to users when they successfully log in.
 */
@Profile(PROFILE_CORE)
@Service
public class ArtemisSuccessfulLoginService {

    private static final Logger log = LoggerFactory.getLogger(ArtemisSuccessfulLoginService.class);

    @Value("${artemis.user-management.password-reset.links.en}")
    private String passwordResetLinkEnUrl;

    @Value("${artemis.user-management.password-reset.links.de}")
    private String passwordResetLinkDeUrl;

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    /**
     * Ensures that the password reset links for both English and German are initialized properly.
     * If the configured links are empty or set to a placeholder, it uses the default link, the ArtemisServerURL/account/reset/request.
     */
    @PostConstruct
    public void ensurePasswordResetLinksAreInitializedProperly() {
        String defaultPasswordResetLink = artemisServerUrl + "/account/reset/request";
        String configurationPlaceholder = "<link>";
        if (passwordResetLinkEnUrl == null || passwordResetLinkEnUrl.isEmpty() || passwordResetLinkEnUrl.equals(configurationPlaceholder)) {
            log.info("No password reset link configured for English, using default link {}", defaultPasswordResetLink);
            passwordResetLinkEnUrl = defaultPasswordResetLink;
        }
        if (passwordResetLinkDeUrl == null || passwordResetLinkDeUrl.isEmpty() || passwordResetLinkDeUrl.equals(configurationPlaceholder)) {
            log.info("No password reset link configured for German, using default link {}", defaultPasswordResetLink);
            passwordResetLinkDeUrl = defaultPasswordResetLink;
        }
    }

    public ArtemisSuccessfulLoginService(UserRepository userRepository, MailSendingService mailSendingService) {
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Handles successful authentication events.
     * Sends a login notification email to users when they successfully authenticate.
     *
     * @param username             the username of the user who has successfully logged in
     * @param authenticationMethod the method used for authentication
     * @param clientEnvironment    the environment information of the client (optional)
     * @see AuthenticationMethod for available authentication methods
     */
    public void sendLoginEmail(String username, AuthenticationMethod authenticationMethod, @Nullable ClientEnvironment clientEnvironment) {
        try {
            User recipient = userRepository.getUserByLoginElseThrow(username);

            String localeKey = recipient.getLangKey();
            if (localeKey == null) {
                log.warn("User {} has no language set, using default language 'en'", username);
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
            log.error("User with login {} not found when trying to send newLoginEmail", username);
        }
    }
}
