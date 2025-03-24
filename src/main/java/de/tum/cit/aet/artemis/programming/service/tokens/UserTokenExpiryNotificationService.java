package de.tum.cit.aet.artemis.programming.service.tokens;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Profile(PROFILE_CORE_AND_SCHEDULING)
@Service
public class UserTokenExpiryNotificationService {

    private static final Logger log = LoggerFactory.getLogger(UserTokenExpiryNotificationService.class);

    private final SingleUserNotificationService singleUserNotificationService;

    private final UserRepository userRepository;

    public UserTokenExpiryNotificationService(SingleUserNotificationService singleUserNotificationService, UserRepository userRepository) {
        this.singleUserNotificationService = singleUserNotificationService;
        this.userRepository = userRepository;
    }

    /**
     * Schedules VCS access token expiry notifications to users every morning at 6:00:00 am
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void sendTokenExpirationNotifications() {
        log.info("Sending Token expiration notifications to single user");
        notifyOnExpiredToken();
        notifyUsersOnUpcomingVcsAccessTokenExpiry();
    }

    /**
     * Notifies the users at the day of VCS access token expiry
     */
    public void notifyOnExpiredToken() {
        notifyUsersForKeyExpiryWindow(now().minusDays(1), now(), singleUserNotificationService::notifyUserAboutExpiredVcsAccessToken);
    }

    /**
     * Notifies the users one week before the VCS access tokens expiry
     */
    public void notifyUsersOnUpcomingVcsAccessTokenExpiry() {
        notifyUsersForKeyExpiryWindow(now().plusDays(6), now().plusDays(7), singleUserNotificationService::notifyUserAboutSoonExpiringVcsAccessToken);
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
}
