package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Listener for successful authentication events in the Artemis system.
 * This component listens for successful login attempts and sends email notifications
 * to users when they successfully log in.
 */
@Profile(PROFILE_CORE)
@Service
public class ArtemisSuccessfulLoginService {

    @Value("${artemis.user-management.password-reset.links.en:https://artemis.tum.de/account/reset/request}")
    private String passwordResetLinkEnUrl;

    @Value("${artemis.user-management.password-reset.links.de:https://artemis.tum.de/account/reset/request}")
    private String passwordResetLinkDeUrl;

    @Value("${server.url}")
    private URL artemisServerUrl;

    private static final Logger log = LoggerFactory.getLogger(ArtemisSuccessfulLoginService.class);

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    public ArtemisSuccessfulLoginService(UserRepository userRepository, MailSendingService mailSendingService) {
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Handles successful authentication events.
     * Sends a login notification email to users when they successfully authenticate.
     *
     * @param username the username of the user who has successfully logged in
     */
    public void sendLoginEmail(String username) {
        try {
            User recipient = userRepository.getUserByLoginElseThrow(username);
            var contextVariables = new HashMap<String, Object>();
            ZonedDateTime now = ZonedDateTime.now();
            contextVariables.put("loginDate", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            contextVariables.put("loginTime", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));

            String localeKey = recipient.getLangKey();
            if (localeKey == null) {
                localeKey = "en";
            }

            if (recipient.isInternal()) {
                contextVariables.put("resetLink", artemisServerUrl.toString() + "/account/password");
            }
            else {
                if (localeKey.equals("de")) {
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
