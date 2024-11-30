package de.tum.cit.aet.artemis.programming.service.sshuserkeys;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;

@Profile(PROFILE_SCHEDULING)
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
     * Notifies the user one week in advance about the upcoming expiry of one of their SSH keys
     */
    @Scheduled(cron = "0 0 6 * * *") // execute this every night at 6:00:00 am
    public void notifyUserOnUpcomingKeyExpiry() {
        ZonedDateTime fromDate = ZonedDateTime.now().plusDays(6);
        ZonedDateTime toDate = ZonedDateTime.now().plusDays(7);
        userSshPublicKeyRepository.findByExpiryDateBetween(fromDate, toDate).forEach(userSshPublicKey -> {
            var user = userRepository.findById(userSshPublicKey.getUserId());
            user.ifPresent(recipient -> singleUserNotificationService.notifyUserAboutSoonExpiringSshKey(recipient, userSshPublicKey));
        });
    }

    /**
     * Notifies the user about the expiry of one of their SSH keys
     */
    @Scheduled(cron = "0 0 6 * * *") // execute this every night at 6:00:00 am
    public void notifyUserOnKeyExpiry() {
        ZonedDateTime fromDate = ZonedDateTime.now().minusDays(1);
        ZonedDateTime toDate = ZonedDateTime.now();
        userSshPublicKeyRepository.findByExpiryDateBetween(fromDate, toDate).forEach(userSshPublicKey -> {
            var user = userRepository.findById(userSshPublicKey.getUserId());
            user.ifPresent(recipient -> singleUserNotificationService.notifyUserAboutExpiredSshKey(recipient, userSshPublicKey));
        });
    }
}
