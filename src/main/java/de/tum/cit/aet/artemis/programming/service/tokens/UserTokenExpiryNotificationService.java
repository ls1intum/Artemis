package de.tum.cit.aet.artemis.programming.service.tokens;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Profile(PROFILE_CORE_AND_SCHEDULING)
@Service
public class UserTokenExpiryNotificationService {

    private static final Logger log = LoggerFactory.getLogger(UserTokenExpiryNotificationService.class);

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    public UserTokenExpiryNotificationService(UserRepository userRepository, MailSendingService mailSendingService) {
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Schedules VCS access token expiry notifications to users every morning at 6:00:00 am
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void sendTokenExpirationNotifications() {
        log.info("Sending Token expiration notifications to single user");
        notifyOnExpiredToken();
    }

    /**
     * Notifies the users at the day of VCS access token expiry
     */
    public void notifyOnExpiredToken() {
        notifyUsersForKeyExpiryWindow(now().minusDays(1), now(), this::notifyUserAboutExpiredVcsAccessToken);
    }

    /**
     * Notifies users whose VCS access tokens are expiring within the specified date range, with the notification specified by the
     * notifyFunction
     *
     * @param fromDate       the start of the expiry date range
     * @param toDate         the end of the expiry date range
     * @param notifyFunction a function to handle user notification
     */
    private void notifyUsersForKeyExpiryWindow(ZonedDateTime fromDate, ZonedDateTime toDate, Consumer<User> notifyFunction) {
        userRepository.findByVcsAccessTokenExpiryDateBetween(fromDate, toDate).forEach(notifyFunction);
    }

    /**
     * Notify user about the expiration of the VCS access token
     *
     * @param recipient the user to whose account the VCS access token was added
     */
    private void notifyUserAboutExpiredVcsAccessToken(User recipient) {
        mailSendingService.buildAndSendSync(recipient, "email.notification.vcsAccessTokenExpiry.title", "mail/notification/vcsAccessTokenExpiredEmail", new HashMap<>());
    }
}
