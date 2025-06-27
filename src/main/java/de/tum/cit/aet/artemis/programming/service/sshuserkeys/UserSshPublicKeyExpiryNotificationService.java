package de.tum.cit.aet.artemis.programming.service.sshuserkeys;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;

@Profile(PROFILE_CORE_AND_SCHEDULING)
@Lazy
@Service
public class UserSshPublicKeyExpiryNotificationService {

    private final UserSshPublicKeyRepository userSshPublicKeyRepository;

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    public UserSshPublicKeyExpiryNotificationService(UserSshPublicKeyRepository userSshPublicKeyRepository, UserRepository userRepository, MailSendingService mailSendingService,
            GlobalNotificationSettingRepository globalNotificationSettingRepository) {
        this.userSshPublicKeyRepository = userSshPublicKeyRepository;
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
    }

    /**
     * Schedules SSH key expiry notifications to users every morning at 7:00:00 am
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendKeyExpirationNotifications() {
        notifyUserOnExpiredKey();
    }

    /**
     * Notifies the user at the day of key expiry, that the key has expired
     */
    public void notifyUserOnExpiredKey() {
        notifyUsersForKeyExpiryWindow(now().minusDays(1), now(), this::notifyUserAboutExpiredSshKey);
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

    /**
     * Notify user about the expiration of an SSH key
     *
     * @param recipient the user to whose account the SSH key was added
     * @param key       the key which was added
     */
    public void notifyUserAboutExpiredSshKey(User recipient, UserSshPublicKey key) {
        if (globalNotificationSettingRepository.isNotificationEnabled(recipient.getId(), GlobalNotificationType.SSH_KEY_EXPIRED)) {
            var contextVariables = new HashMap<String, Object>();

            contextVariables.put("sshKey", key);
            if (key.getExpiryDate() != null) {
                contextVariables.put("expiryDate", key.getExpiryDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm:ss")));
            }
            else {
                contextVariables.put("expiryDate", "-");
            }

            mailSendingService.buildAndSendSync(recipient, "email.notification.sshKeyExpiry.sshKeysHasExpiredWarning", "mail/notification/sshKeyHasExpiredEmail", contextVariables);
        }
    }
}
