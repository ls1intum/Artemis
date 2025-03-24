package de.tum.cit.aet.artemis.programming.service.sshuserkeys;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;

@Profile(PROFILE_CORE_AND_SCHEDULING)
@Service
public class UserSshPublicKeyExpiryNotificationService {

    private final UserSshPublicKeyRepository userSshPublicKeyRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final UserRepository userRepository;

    public UserSshPublicKeyExpiryNotificationService(UserSshPublicKeyRepository userSshPublicKeyRepository, SingleUserNotificationService singleUserNotificationService,
            UserRepository userRepository) {
        this.userSshPublicKeyRepository = userSshPublicKeyRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.userRepository = userRepository;
    }

    /**
     * Schedules SSH key expiry notifications to users every morning at 7:00:00 am
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendKeyExpirationNotifications() {
        notifyUserOnExpiredKey();
        notifyUserOnUpcomingKeyExpiry();
    }

    /**
     * Notifies the user at the day of key expiry, that the key has expired
     */
    public void notifyUserOnExpiredKey() {
        notifyUsersForKeyExpiryWindow(now().minusDays(1), now(), singleUserNotificationService::notifyUserAboutExpiredSshKey);
    }

    /**
     * Notifies the user one week in advance about the upcoming expiry
     */
    public void notifyUserOnUpcomingKeyExpiry() {
        notifyUsersForKeyExpiryWindow(now().plusDays(6), now().plusDays(7), singleUserNotificationService::notifyUserAboutSoonExpiringSshKey);
    }

    /**
     * Notifies users whose SSH keys are expiring within the specified date range, with the notification specified by the
     * notifyFunction
     *
     * @param fromDate       the start of the expiry date range
     * @param toDate         the end of the expiry date range
     * @param notifyFunction a function to handle user notification
     */
    private void notifyUsersForKeyExpiryWindow(ZonedDateTime fromDate, ZonedDateTime toDate, BiConsumer<User, UserSshPublicKey> notifyFunction) {
        var soonExpiringKeys = userSshPublicKeyRepository.findByExpiryDateBetween(fromDate, toDate);
        List<User> users = userRepository.findAllByIdIn(soonExpiringKeys.stream().map(UserSshPublicKey::getUserId).toList());
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        soonExpiringKeys.forEach(key -> notifyFunction.accept(userMap.get(key.getUserId()), key));
    }
}
