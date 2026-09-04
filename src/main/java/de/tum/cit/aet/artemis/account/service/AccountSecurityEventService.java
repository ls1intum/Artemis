package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.ACCOUNT_EMAIL_CHANGED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.ACCOUNT_REGISTERED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.PASSWORD_RESET_REQUESTED;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.PASSWORD_RESET_REQUEST_REJECTED;

import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Records the account lifecycle events that change an account's credentials or identity, and sends the
 * corresponding notifications to the account owner.
 * <p>
 * Both concerns live here on purpose: every one of these events needs an audit record, and the ones that
 * change a credential or the recovery address also need to tell the owner. Keeping them together gives
 * each flow a single call site, so a new account operation cannot pick up the audit trail while forgetting
 * the notification.
 * <p>
 * <b>These notifications are deliberately unconditional</b> - they do not consult
 * {@code GlobalNotificationSettingRepository}, and the corresponding types are intentionally absent from
 * {@code GlobalNotificationType}. Their whole purpose is to inform the owner about a change they may not
 * have made themselves, so they must not be silenceable from inside a session; and a settings toggle that
 * must not be honoured would be misleading in the UI. Treating credential and e-mail change notices as
 * mandatory is the common convention.
 * <p>
 * Every method is best-effort with respect to the caller: an audit-log or mail failure must never fail the
 * user-visible operation it accompanies, because that would turn a logging outage into an outage of
 * password reset. Failures are logged at error level instead.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AccountSecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(AccountSecurityEventService.class);

    /** Recorded as the audit principal when the actor could not be resolved to an account. */
    private static final String ANONYMOUS_PRINCIPAL = "anonymous";

    private final AuditEventRepository auditEventRepository;

    private final MailSendingService mailSendingService;

    public AccountSecurityEventService(AuditEventRepository auditEventRepository, MailSendingService mailSendingService) {
        this.auditEventRepository = auditEventRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Records that a password reset was requested for an existing account and the reset mail was sent.
     * No extra notification is sent: the reset mail itself is the notification, and sending a second
     * message to the same address would only add noise.
     *
     * @param user the account the reset was requested for
     */
    public void recordPasswordResetRequested(User user) {
        addAuditEvent(user.getLogin(), PASSWORD_RESET_REQUESTED, Map.of());
    }

    /**
     * Records a password-reset request that produced no reset mail, so that repeated fruitless requests are
     * visible even though each individual request is answered with a success response.
     * <p>
     * The submitted identifier is deliberately <em>not</em> stored. It is unauthenticated free-form input,
     * so persisting it would let anyone write arbitrary strings into the audit table and would capture
     * third-party e-mail addresses (someone else's, if mistyped) as a side effect. The reason plus the
     * event count is enough to see the pattern.
     *
     * @param reason why no reset mail was sent, e.g. {@code unknown-identifier}
     */
    public void recordPasswordResetRequestRejected(String reason) {
        addAuditEvent(ANONYMOUS_PRINCIPAL, PASSWORD_RESET_REQUEST_REJECTED, Map.of("reason", reason));
    }

    // A completed reset is recorded and announced by AccountSecurityNotificationService.passwordChanged with the RESET
    // actor, called from UserService.completePasswordReset. That path also reports what was revoked alongside the reset,
    // so a second notice here would only mean two audit rows and two near-identical emails for one reset.

    /**
     * Records a self-service e-mail change and notifies the <b>previous</b> address.
     * <p>
     * Notifying the previous address is the entire point of this notice. Once the address on the account has
     * been replaced, every subsequent message - including password reset and the login notification - goes
     * to the new one, so the previous address is the only channel that still reaches whoever owned the
     * account beforehand.
     *
     * @param user            the account after the change was applied
     * @param previousEmail   the address the account had before the change, or {@code null} when an address was added
     * @param previousLangKey the language the previous address was being written to, so the notice is not
     *                            sent in a language the recipient did not pick
     */
    public void recordEmailChanged(User user, @Nullable String previousEmail, String previousLangKey) {
        addAuditEvent(user.getLogin(), ACCOUNT_EMAIL_CHANGED, Map.of());

        if (previousEmail == null) {
            return;
        }

        // Addressed to the previous e-mail, but otherwise the user's own identity, so the greeting still reads correctly.
        var previousAddressRecipient = new MailRecipientDTO(previousEmail, previousLangKey, user.getLogin(), user.getFirstName(), user.getLastName(), null, null);
        String newEmail = user.getEmail();
        sendSecurityNotification(previousAddressRecipient, "email.notification.emailChanged.title", "mail/notification/emailChangedEmail",
                Map.of("emailRemoved", newEmail == null, "newEmail", newEmail == null ? "" : newEmail));
    }

    /**
     * Records that an account was created through self-registration. No extra notification is sent: the
     * activation mail already goes to the address that was registered.
     *
     * @param user the newly registered account
     */
    public void recordAccountRegistered(User user) {
        addAuditEvent(user.getLogin(), ACCOUNT_REGISTERED, Map.of());
    }

    /**
     * Writes an audit event. No category is recorded alongside it: every type this service emits is classified as a
     * security event, so {@code CustomAuditEventRepository} routes the row into {@code security_audit_event} and the
     * table it lives in already says what an extra data entry would.
     */
    private void addAuditEvent(String principal, String type, Map<String, Object> data) {
        try {
            auditEventRepository.add(new AuditEvent(Instant.now(), principal, type, data));
        }
        catch (Exception e) {
            // Never let an audit failure break the account operation that triggered it.
            log.error("Could not record account security audit event {} for principal {}", type, principal, e);
        }
    }

    private void sendSecurityNotification(MailRecipientDTO recipient, String subjectKey, String template, Map<String, Object> contextVariables) {
        try {
            mailSendingService.buildAndSendAsync(recipient, subjectKey, template, contextVariables);
        }
        catch (Exception e) {
            log.error("Could not send account security notification '{}' to user {}", subjectKey, recipient.login(), e);
        }
    }
}
