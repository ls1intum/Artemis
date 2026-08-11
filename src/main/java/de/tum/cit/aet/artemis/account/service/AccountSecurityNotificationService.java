package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Records and announces the security-relevant changes to an account: its password changing, and its other credentials
 * being revoked.
 * <p>
 * Both halves belong together because both answer the same question for the same event, for two different audiences. The
 * audit event lets an administrator reconstruct afterwards who did what to which account; the email tells the account
 * owner, at the time, that something changed - which is the only way they can notice a change they did not make.
 * <p>
 * The owner is the recipient in every case, including when an administrator acts. Whoever performed the action already
 * knows they did; the person whose passkeys just stopped working is the one who needs to be told, and who needs to
 * recognise that an administrator did it rather than an intruder. Emailing the acting administrator would add nothing
 * they cannot see in the response, and emailing every administrator would be noise plus a needless disclosure of another
 * user's account activity - the acting principal is recorded in the audit event, which is where that belongs.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AccountSecurityNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AccountSecurityNotificationService.class);

    private final MailSendingService mailSendingService;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private final AuditEventRepository auditEventRepository;

    public AccountSecurityNotificationService(MailSendingService mailSendingService, GlobalNotificationSettingRepository globalNotificationSettingRepository,
            AuditEventRepository auditEventRepository) {
        this.mailSendingService = mailSendingService;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Who changed the password, which decides both the audit event type and what the email tells the owner.
     */
    public enum PasswordChangeActor {

        /**
         * The owner changed it from inside their account, having supplied the current one.
         */
        OWNER(Constants.OWN_PASSWORD_CHANGED),
        /**
         * The owner completed a password reset from an emailed link.
         */
        RESET(Constants.COMPLETE_PASSWORD_RESET),
        /**
         * An administrator replaced it through the user management form.
         */
        ADMINISTRATOR(Constants.ADMIN_USER_PASSWORD_CHANGED);

        private final String auditEventType;

        PasswordChangeActor(String auditEventType) {
            this.auditEventType = auditEventType;
        }
    }

    /**
     * Records and announces that the account's password changed, naming which other credentials were revoked with it.
     *
     * @param user    the account whose password changed, and the recipient of the email
     * @param revoked which credential types were revoked alongside the change; may revoke nothing
     * @param actor   who changed the password
     */
    public void passwordChanged(User user, CredentialRevocationChoiceDTO revoked, PasswordChangeActor actor) {
        String principal = actor == PasswordChangeActor.ADMINISTRATOR ? currentPrincipal(user) : user.getLogin();
        auditEventRepository.add(new AuditEvent(principal, actor.auditEventType, auditData(user, revoked)));

        Map<String, Object> variables = revocationVariables(revoked);
        variables.put("changedByAdministrator", actor == PasswordChangeActor.ADMINISTRATOR);
        variables.put("changedViaReset", actor == PasswordChangeActor.RESET);
        sendIfEnabled(user, GlobalNotificationType.PASSWORD_CHANGED, "email.notification.passwordChanged.title", "mail/notification/passwordChangedEmail", variables);
    }

    /**
     * Records and announces that the account's other credentials were revoked without its password changing.
     *
     * @param user    the account whose credentials were revoked, and the recipient of the email
     * @param revoked which credential types were revoked
     */
    public void credentialsRevoked(User user, CredentialRevocationChoiceDTO revoked) {
        auditEventRepository.add(new AuditEvent(user.getLogin(), Constants.REVOKE_OWN_CREDENTIALS, auditData(user, revoked)));
        sendIfEnabled(user, GlobalNotificationType.CREDENTIALS_REVOKED, "email.notification.credentialsRevoked.title", "mail/notification/credentialsRevokedEmail",
                revocationVariables(revoked));
    }

    /**
     * Falls back to the affected user's login when no principal is on the security context, so an action taken outside a
     * request (a scheduled task, a test) still produces an audit event rather than throwing.
     */
    private String currentPrincipal(User affectedUser) {
        return SecurityUtils.getCurrentUserLogin().orElse(affectedUser.getLogin());
    }

    private Map<String, Object> auditData(User user, CredentialRevocationChoiceDTO revoked) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", user.getLogin());
        data.put("revokedPasskeys", revoked.passkeys());
        data.put("revokedSshKeys", revoked.sshKeys());
        data.put("revokedVcsAccessTokens", revoked.vcsAccessTokens());
        return data;
    }

    private Map<String, Object> revocationVariables(CredentialRevocationChoiceDTO revoked) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("revokedPasskeys", revoked.passkeys());
        variables.put("revokedSshKeys", revoked.sshKeys());
        variables.put("revokedVcsAccessTokens", revoked.vcsAccessTokens());
        variables.put("revokedAnything", revoked.revokesAnything());
        return variables;
    }

    /**
     * Sends the email unless the owner switched this notification type off. Failures are logged rather than propagated:
     * the password change or revocation has already happened, and undoing it because an email could not be delivered
     * would be worse than the missing email.
     */
    private void sendIfEnabled(User user, GlobalNotificationType type, String subjectKey, String template, Map<String, Object> variables) {
        try {
            if (globalNotificationSettingRepository.isNotificationEnabled(user.getId(), type)) {
                mailSendingService.buildAndSendAsync(MailRecipientDTO.from(user), subjectKey, template, variables);
            }
        }
        catch (Exception exception) {
            log.warn("Could not send the {} notification to user {}", type, user.getLogin(), exception);
        }
    }
}
