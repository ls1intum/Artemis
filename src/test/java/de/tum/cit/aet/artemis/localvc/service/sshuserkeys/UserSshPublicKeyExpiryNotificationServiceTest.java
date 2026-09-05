package de.tum.cit.aet.artemis.localvc.service.sshuserkeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;

/**
 * Unit tests for the mail that tells a user their SSH key has expired.
 * <p>
 * A key that expired stops working for pushing and pulling without any visible reason, so the notification is the only
 * thing that tells the user why. It is sent from a scheduled job, which is why the window it looks at, the recipient it
 * resolves and the user's own notification setting all have to hold without anyone watching.
 */
@ExtendWith(MockitoExtension.class)
class UserSshPublicKeyExpiryNotificationServiceTest {

    private static final long USER_ID = 42L;

    private static final ZonedDateTime EXPIRY_DATE = ZonedDateTime.parse("2200-01-10T12:34:56Z");

    @Mock
    private UserSshPublicKeyRepository userSshPublicKeyRepository;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    @InjectMocks
    private UserSshPublicKeyExpiryNotificationService service;

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setLogin("ge12abc");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("ada@example.com");
        return user;
    }

    private static UserSshPublicKey expiredKey() {
        UserSshPublicKey key = new UserSshPublicKey();
        key.setUserId(USER_ID);
        key.setLabel("laptop");
        key.setExpiryDate(EXPIRY_DATE);
        return key;
    }

    @Test
    void notifyUserOnExpiredKey_mailsTheOwnerOfEveryKeyThatExpired() {
        UserSshPublicKey key = expiredKey();
        when(userSshPublicKeyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of(key));
        when(userRepository.findAllByIdIn(anyList())).thenReturn(List.of(user()));
        when(globalNotificationSettingRepository.isNotificationEnabled(USER_ID, GlobalNotificationType.SSH_KEY_EXPIRED)).thenReturn(true);

        service.notifyUserOnExpiredKey();

        ArgumentCaptor<MailRecipientDTO> recipient = ArgumentCaptor.forClass(MailRecipientDTO.class);
        ArgumentCaptor<Map<String, Object>> contextVariables = ArgumentCaptor.captor();
        verify(mailSendingService).buildAndSendSync(recipient.capture(), eq("email.notification.sshKeyExpiry.sshKeysHasExpiredWarning"),
                eq("mail/notification/sshKeyHasExpiredEmail"), contextVariables.capture());
        assertThat(recipient.getValue().email()).as("the mail goes to the owner of the key").isEqualTo("ada@example.com");
        assertThat(contextVariables.getValue()).as("the template is told which key expired").containsEntry("sshKey", key);
        // The template renders this string directly, so the format is part of what the user sees.
        assertThat(contextVariables.getValue()).as("the expiry date is rendered for the reader, not as an ISO timestamp").containsEntry("expiryDate", "10.01.2200 - 12:34:56");
    }

    @Test
    void notifyUserOnExpiredKey_asksForTheKeysThatExpiredSinceYesterday() {
        when(userSshPublicKeyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of());

        service.notifyUserOnExpiredKey();

        // The job runs daily, so the window has to be the last day: a wider one would notify the same user again, a narrower one would miss keys.
        ArgumentCaptor<ZonedDateTime> from = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> to = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(userSshPublicKeyRepository).findByExpiryDateBetween(from.capture(), to.capture());
        // Compared as a calendar day rather than as 24 hours: across a daylight saving change the two differ, and the service asks for one day.
        assertThat(from.getValue().plusDays(1)).as("the window covers the last day").isCloseTo(to.getValue(),
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MINUTES));
        assertThat(to.getValue()).as("the window ends now").isCloseTo(ZonedDateTime.now(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MINUTES));
        verifyNoInteractions(mailSendingService);
    }

    @Test
    void notifyUserAboutExpiredSshKey_whenTheUserTurnedTheNotificationOff_sendsNothing() {
        when(globalNotificationSettingRepository.isNotificationEnabled(USER_ID, GlobalNotificationType.SSH_KEY_EXPIRED)).thenReturn(false);

        service.notifyUserAboutExpiredSshKey(user(), expiredKey());

        verify(mailSendingService, never()).buildAndSendSync(any(), any(), any(), any());
    }

    @Test
    void notifyUserAboutExpiredSshKey_forAKeyWithoutAnExpiryDate_rendersAPlaceholder() {
        // A key without an expiry date never expires, but the mail template reads the variable unconditionally, so it has to be there.
        UserSshPublicKey keyWithoutExpiryDate = expiredKey();
        keyWithoutExpiryDate.setExpiryDate(null);
        when(globalNotificationSettingRepository.isNotificationEnabled(USER_ID, GlobalNotificationType.SSH_KEY_EXPIRED)).thenReturn(true);

        service.notifyUserAboutExpiredSshKey(user(), keyWithoutExpiryDate);

        ArgumentCaptor<Map<String, Object>> contextVariables = ArgumentCaptor.captor();
        verify(mailSendingService).buildAndSendSync(any(), any(), any(), contextVariables.capture());
        assertThat(contextVariables.getValue()).as("a missing expiry date is rendered as a dash rather than as null").containsEntry("expiryDate", "-");
    }

    @Test
    void sendKeyExpirationNotifications_notifiesTheOwnersOfTheExpiredKeys() {
        // This is the entry point the scheduler calls; it must do the same work as the method a caller would invoke directly.
        when(userSshPublicKeyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of(expiredKey()));
        when(userRepository.findAllByIdIn(anyList())).thenReturn(List.of(user()));
        when(globalNotificationSettingRepository.isNotificationEnabled(USER_ID, GlobalNotificationType.SSH_KEY_EXPIRED)).thenReturn(true);

        service.sendKeyExpirationNotifications();

        verify(mailSendingService).buildAndSendSync(any(), eq("email.notification.sshKeyExpiry.sshKeysHasExpiredWarning"), eq("mail/notification/sshKeyHasExpiredEmail"), any());
    }
}
