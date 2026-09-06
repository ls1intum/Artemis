package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.ACCOUNT_EMAIL_CHANGED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.ACCOUNT_REGISTERED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.AUTHENTICATION_SUCCESS;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.PASSWORD_RESET_COMPLETED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.PASSWORD_RESET_REQUESTED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.PASSWORD_RESET_REQUEST_REJECTED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.SECURITY_EVENT_TYPES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventTypeClassifier;
import de.tum.cit.aet.artemis.core.config.audit.AuditLogType;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Tests for {@link AccountSecurityEventService}: that each account-security event is audited, that the
 * notifications go to the address that can still reach the real owner, and that a failure in either does not
 * propagate into the account operation that triggered it.
 */
class AccountSecurityEventServiceTest {

    private AuditEventRepository auditEventRepository;

    private MailSendingService mailSendingService;

    private AccountSecurityEventService service;

    private User user;

    @BeforeEach
    void setUp() {
        auditEventRepository = mock(AuditEventRepository.class);
        mailSendingService = mock(MailSendingService.class);
        service = new AccountSecurityEventService(auditEventRepository, mailSendingService);

        user = new User();
        user.setId(42L);
        user.setLogin("ab12cde");
        user.setEmail("new@tum.de");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setLangKey("en");
    }

    private AuditEvent capturedAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).add(captor.capture());
        return captor.getValue();
    }

    @Test
    void testPasswordResetRequestedIsAuditedWithoutAnExtraMail() {
        service.recordPasswordResetRequested(user);

        AuditEvent event = capturedAuditEvent();
        assertThat(event.getType()).isEqualTo(PASSWORD_RESET_REQUESTED);
        assertThat(event.getPrincipal()).isEqualTo("ab12cde");
        // The table the row lands in already says it is a security event, so nothing is recorded alongside it.
        assertThat(event.getData()).isEmpty();
        // The reset mail itself is the notification; a second message would only add noise.
        verify(mailSendingService, never()).buildAndSendAsync(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void testRejectedPasswordResetRequestIsAuditedWithoutStoringTheSubmittedIdentifier() {
        service.recordPasswordResetRequestRejected("unknown-identifier");

        AuditEvent event = capturedAuditEvent();
        assertThat(event.getType()).isEqualTo(PASSWORD_RESET_REQUEST_REJECTED);
        assertThat(event.getPrincipal()).isEqualTo("anonymous");
        // Unauthenticated free-form input must not reach the audit table: the recorded data is limited to the fixed
        // reason describing why the request was rejected, and carries nothing derived from what the caller submitted.
        assertThat(event.getData()).containsExactly(entry("reason", "unknown-identifier"));
    }

    @Test
    void testEmailChangeNotifiesThePreviousAddressAndNotTheNewOne() {
        service.recordEmailChanged(user, "old@tum.de", "de");

        assertThat(capturedAuditEvent().getType()).isEqualTo(ACCOUNT_EMAIL_CHANGED);

        ArgumentCaptor<MailRecipientDTO> recipient = ArgumentCaptor.forClass(MailRecipientDTO.class);
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.captor();
        verify(mailSendingService).buildAndSendAsync(recipient.capture(), eq("email.notification.emailChanged.title"), eq("mail/notification/emailChangedEmail"),
                context.capture());

        // The whole point: the notice must reach the address the owner still controls, in that address's language.
        assertThat(recipient.getValue().email()).isEqualTo("old@tum.de");
        assertThat(recipient.getValue().langKey()).isEqualTo("de");
        assertThat(recipient.getValue().login()).isEqualTo("ab12cde");
        assertThat(context.getValue()).containsEntry("newEmail", "new@tum.de");
        assertThat(context.getValue()).containsEntry("emailRemoved", false);
    }

    @Test
    void testEmailRemovalNotifiesThePreviousAddress() {
        user.setEmail(null);

        service.recordEmailChanged(user, "old@tum.de", "de");

        assertThat(capturedAuditEvent().getType()).isEqualTo(ACCOUNT_EMAIL_CHANGED);
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.captor();
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.notification.emailChanged.title"), eq("mail/notification/emailChangedEmail"), context.capture());
        assertThat(context.getValue()).containsEntry("emailRemoved", true).containsEntry("newEmail", "");
    }

    @Test
    void testAddingFirstEmailIsAuditedWithoutNotification() {
        service.recordEmailChanged(user, null, "en");

        assertThat(capturedAuditEvent().getType()).isEqualTo(ACCOUNT_EMAIL_CHANGED);
        verify(mailSendingService, never()).buildAndSendAsync(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void testRegistrationIsAuditedWithoutAnExtraMail() {
        service.recordAccountRegistered(user);

        assertThat(capturedAuditEvent().getType()).isEqualTo(ACCOUNT_REGISTERED);
        // The activation mail already goes to the registered address.
        verify(mailSendingService, never()).buildAndSendAsync(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void testAuditFailureDoesNotBreakTheAccountOperation() {
        doThrow(new RuntimeException("audit backend down")).when(auditEventRepository).add(any());

        // A logging outage must not become an outage of the account operation itself.
        assertThatCode(() -> service.recordEmailChanged(user, "old@tum.de", "en")).doesNotThrowAnyException();
        // ... and the notification must still be attempted.
        verify(mailSendingService).buildAndSendAsync(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void testMailFailureDoesNotBreakTheAccountOperation() {
        doThrow(new RuntimeException("smtp down")).when(mailSendingService).buildAndSendAsync(any(), anyString(), anyString(), anyMap());

        assertThatCode(() -> service.recordEmailChanged(user, "old@tum.de", "en")).doesNotThrowAnyException();
        // The audit record is still written, so the change remains reconstructible even if the notice was not delivered.
        assertThat(capturedAuditEvent().getType()).isEqualTo(ACCOUNT_EMAIL_CHANGED);
    }

    @Test
    void everyEventTypeThisServiceEmitsIsClassifiedAsASecurityEvent() {
        // Routing and retention both key off the classifier: a type this service emits that is not classified as SECURITY
        // would be written to the wrong table and pruned on the short general schedule. The exact contents of the type
        // sets are asserted in AuditEventTypeClassifierTest; here the point is that this service's own events are covered.
        // PASSWORD_RESET_COMPLETED is no longer emitted - passwordChanged(..., RESET) records a completed reset instead -
        // but it stays in the list so rows written by an earlier build of this branch keep their security retention.
        for (String emittedType : List.of(PASSWORD_RESET_REQUESTED, PASSWORD_RESET_REQUEST_REJECTED, PASSWORD_RESET_COMPLETED, ACCOUNT_EMAIL_CHANGED, ACCOUNT_REGISTERED)) {
            assertThat(AuditEventTypeClassifier.classify(emittedType)).as("%s must be routed to the security audit log", emittedType).isEqualTo(AuditLogType.SECURITY);
            assertThat(SECURITY_EVENT_TYPES).contains(emittedType);
        }
        assertThat(SECURITY_EVENT_TYPES).doesNotContain(AUTHENTICATION_SUCCESS);
    }
}
