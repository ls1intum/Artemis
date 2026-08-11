package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.AccountSecurityNotificationService.PasswordChangeActor;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Verifies what the owner is told and what an administrator can reconstruct afterwards.
 * <p>
 * A unit test rather than an integration one because every property worth pinning here is a decision this class makes
 * from its arguments: which audit event type the actor produces, who the email goes to, and whether the opt-out is
 * honoured. None of that needs a database, and mocking the collaborators is what makes the recipient assertions
 * unambiguous.
 */
class AccountSecurityNotificationServiceTest {

    private MailSendingService mailSendingService;

    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private AuditEventRepository auditEventRepository;

    private AccountSecurityNotificationService accountSecurityNotificationService;

    private User user;

    @BeforeEach
    void init() {
        mailSendingService = mock(MailSendingService.class);
        globalNotificationSettingRepository = mock(GlobalNotificationSettingRepository.class);
        auditEventRepository = mock(AuditEventRepository.class);
        accountSecurityNotificationService = new AccountSecurityNotificationService(mailSendingService, globalNotificationSettingRepository, auditEventRepository);

        user = new User();
        user.setId(42L);
        user.setLogin("affected-user");
        user.setEmail("affected-user@example.com");
        user.setLangKey("en");
        // The opt-out defaults to enabled, which is what an account without an explicit setting looks like.
        when(globalNotificationSettingRepository.isNotificationEnabled(anyLong(), any())).thenReturn(true);
    }

    @Test
    void shouldRecordTheOwnerAsThePrincipalWhenTheyChangeTheirOwnPassword() {
        accountSecurityNotificationService.passwordChanged(user, CredentialRevocationChoiceDTO.none(), PasswordChangeActor.OWNER);

        AuditEvent event = captureAuditEvent();
        assertThat(event.getPrincipal()).isEqualTo("affected-user");
        assertThat(event.getType()).isEqualTo(Constants.OWN_PASSWORD_CHANGED);
    }

    @Test
    void shouldUseTheResetAuditTypeForACompletedReset() {
        accountSecurityNotificationService.passwordChanged(user, CredentialRevocationChoiceDTO.none(), PasswordChangeActor.RESET);

        assertThat(captureAuditEvent().getType()).isEqualTo(Constants.COMPLETE_PASSWORD_RESET);
    }

    @Test
    void shouldNameTheAffectedUserInTheAuditDataOfAnAdministratorChange() {
        // The acting administrator is the principal, so the target has to be in the data or the event says nothing about
        // whose account was touched. Without a security context the principal falls back to the affected user.
        accountSecurityNotificationService.passwordChanged(user, new CredentialRevocationChoiceDTO(true, true, true), PasswordChangeActor.ADMINISTRATOR);

        AuditEvent event = captureAuditEvent();
        assertThat(event.getType()).isEqualTo(Constants.ADMIN_USER_PASSWORD_CHANGED);
        assertThat(event.getData()).containsEntry("user", "affected-user").containsEntry("revokedPasskeys", true).containsEntry("revokedSshKeys", true)
                .containsEntry("revokedVcsAccessTokens", true);
    }

    @Test
    void shouldEmailTheAffectedUserWhenAnAdministratorChangesTheirPassword() {
        accountSecurityNotificationService.passwordChanged(user, new CredentialRevocationChoiceDTO(true, false, false), PasswordChangeActor.ADMINISTRATOR);

        ArgumentCaptor<MailRecipientDTO> recipient = ArgumentCaptor.forClass(MailRecipientDTO.class);
        ArgumentCaptor<Map<String, Object>> variables = captureVariables();
        verify(mailSendingService).buildAndSendAsync(recipient.capture(), eq("email.notification.passwordChanged.title"), eq("mail/notification/passwordChangedEmail"),
                variables.capture());

        // The owner is the recipient even though an administrator acted: they are the one who has to tell this apart from
        // an intruder, and the administrator already knows what they did.
        assertThat(recipient.getValue().email()).isEqualTo("affected-user@example.com");
        assertThat(variables.getValue()).containsEntry("changedByAdministrator", true).containsEntry("changedViaReset", false).containsEntry("revokedPasskeys", true)
                .containsEntry("revokedSshKeys", false).containsEntry("revokedAnything", true);
    }

    @Test
    void shouldTellTheOwnerWhenNothingWasRevokedAlongsideTheirPasswordChange() {
        // A routine rotation still gets an email, and it has to say that the keys and tokens were kept - otherwise the
        // user cannot tell this apart from a change that took them away.
        accountSecurityNotificationService.passwordChanged(user, CredentialRevocationChoiceDTO.none(), PasswordChangeActor.OWNER);

        ArgumentCaptor<Map<String, Object>> variables = captureVariables();
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.notification.passwordChanged.title"), any(), variables.capture());
        assertThat(variables.getValue()).containsEntry("revokedAnything", false);
    }

    @Test
    void shouldRecordAndAnnounceAStandaloneRevocation() {
        accountSecurityNotificationService.credentialsRevoked(user, new CredentialRevocationChoiceDTO(false, true, false));

        AuditEvent event = captureAuditEvent();
        assertThat(event.getPrincipal()).isEqualTo("affected-user");
        assertThat(event.getType()).isEqualTo(Constants.REVOKE_OWN_CREDENTIALS);
        assertThat(event.getData()).containsEntry("revokedSshKeys", true).containsEntry("revokedPasskeys", false);

        ArgumentCaptor<Map<String, Object>> variables = captureVariables();
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.notification.credentialsRevoked.title"), eq("mail/notification/credentialsRevokedEmail"),
                variables.capture());
        assertThat(variables.getValue()).containsEntry("revokedSshKeys", true).containsEntry("revokedAnything", true);
    }

    @Test
    void shouldNotEmailAUserWhoTurnedTheNotificationOff() {
        when(globalNotificationSettingRepository.isNotificationEnabled(42L, GlobalNotificationType.CREDENTIALS_REVOKED)).thenReturn(false);

        accountSecurityNotificationService.credentialsRevoked(user, new CredentialRevocationChoiceDTO(true, true, true));

        verify(mailSendingService, never()).buildAndSendAsync(any(), any(), any(), any());
        // The audit event is not the user's to switch off: it is what an administrator needs afterwards.
        verify(auditEventRepository).add(any(AuditEvent.class));
    }

    @Test
    void shouldStillRecordTheEventWhenTheEmailCannotBeSent() {
        // The revocation has already happened by the time this runs. Letting a mail failure propagate would report the
        // whole request as failed and invite the user to repeat an action that already took effect.
        //
        // The failure is thrown from the send itself rather than from the settings lookup: throwing from the lookup would
        // mean the mail was never attempted, so the test would not cover the case it is named after.
        doThrow(new RuntimeException("mail backend unreachable")).when(mailSendingService).buildAndSendAsync(any(), any(), any(), any());

        accountSecurityNotificationService.credentialsRevoked(user, new CredentialRevocationChoiceDTO(true, false, false));

        verify(mailSendingService).buildAndSendAsync(any(), any(), any(), any());
        verify(auditEventRepository).add(any(AuditEvent.class));
    }

    private AuditEvent captureAuditEvent() {
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).add(event.capture());
        return event.getValue();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureVariables() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
