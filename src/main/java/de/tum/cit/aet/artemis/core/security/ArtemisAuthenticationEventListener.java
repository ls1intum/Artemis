package de.tum.cit.aet.artemis.core.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;

import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Listener for successful authentication events in the Artemis system.
 * This component listens for successful login attempts and sends email notifications
 * to non-internal users when they successfully log in.
 */
@Profile(PROFILE_CORE)
@Component
public class ArtemisAuthenticationEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    public ArtemisAuthenticationEventListener(UserRepository userRepository, MailSendingService mailSendingService) {
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Handles successful authentication events.
     * Sends a login notification email to non-internal users when they successfully authenticate.
     *
     * @param event The authentication success event to process
     */
    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        var authentication = event.getAuthentication();
        try {
            var recipient = userRepository.getUserByLoginElseThrow(authentication.getName());

            if (!recipient.isInternal()) {
                mailSendingService.buildAndSendAsync(recipient, "email.notification.login.title", "mail/notification/newLoginEmail", new HashMap<>());
            }
        }
        catch (EntityNotFoundException ignored) {
        }
    }

    /**
     * Determines whether this listener supports asynchronous execution. Async not needed since email is sent asynchronously.
     *
     * @return false, indicating this listener should be executed synchronously
     */
    @Override
    public boolean supportsAsyncExecution() {
        return false;
    }
}
