package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.PendingEmailChange;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.domain.UserRecoveryKey;
import de.tum.cit.aet.artemis.account.dto.EmailChangeConfirmationResultDTO;
import de.tum.cit.aet.artemis.account.dto.EmailChangeConfirmationResultDTO.EmailChangeAddress;
import de.tum.cit.aet.artemis.account.dto.PendingEmailChangeDTO;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.security.RandomUtil;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.SessionFreshnessService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Changes the e-mail address of an internal account only after the owners of both the current and the new address have confirmed it.
 * <p>
 * <b>Why.</b> The address is where a password reset goes. A session that survived an intrusion, or any other session of the
 * account, could therefore change it to an address of its own and reset the password there, without ever knowing the old one. So
 * a request only stores what was asked for and mails one link to each address; the address of the account changes when both links
 * have been followed.
 * <p>
 * <b>Both addresses, not only the new one.</b> Confirming only from the new address proves nothing to the account's owner: an
 * intruder controls that mailbox by choice. The link to the current address is what lets the legitimate owner refuse. And not
 * only the current one either, so that a typo or a stale mailbox cannot move the account to an address nobody can read.
 * <p>
 * <b>What cancels a request.</b> Everything that ends an intrusion also deletes the pending request, see
 * {@link UserRecoveryKeyService#clearPendingEmailChange}: a completed password reset, a password change, a deactivation, a
 * deletion and an administrator's edit. A request made by the intruder must not stay confirmable after the owner took the account
 * back.
 * <p>
 * <b>Without mail</b> nothing could be confirmed, so the change is refused instead of applied unconfirmed. An administrator can
 * still change the address, as an administrator does for an account whose mailbox is gone.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class EmailChangeService {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeService.class);

    private static final String ENTITY_NAME = "Account";

    /** How long after the request both addresses can confirm. */
    public static final Duration VALIDITY = Duration.ofHours(24);

    /**
     * The pause before a request can be made again or its mails sent again. Every request mails two addresses, one of which the
     * requester chooses, and the mail account is shared by the whole installation.
     */
    static final Duration MIN_INTERVAL_BETWEEN_REQUESTS = Duration.ofMinutes(1);

    /**
     * Name of the map that remembers when an account last had its confirmation mails sent. Kept apart from the pending request on
     * purpose: cancelling a request deletes it, and a pause that went with it could be skipped by cancelling after every request.
     */
    public static final String REQUEST_COOLDOWN_MAP_NAME = "email-change-request-cooldown";

    private final UserRecoveryKeyService userRecoveryKeyService;

    private final UserRepository userRepository;

    private final UserCreationService userCreationService;

    private final PasswordService passwordService;

    private final MailSendingService mailSendingService;

    private final AccountSecurityEventService accountSecurityEventService;

    private final Optional<Pattern> allowedEmailPattern;

    /** The accounts whose confirmation mails went out within {@link #MIN_INTERVAL_BETWEEN_REQUESTS}, shared by all nodes. */
    private final DistributedMap<Long, Long> requestCooldowns;

    private final SessionFreshnessService sessionFreshnessService;

    private final UserActivityService userActivityService;

    public EmailChangeService(UserRecoveryKeyService userRecoveryKeyService, UserRepository userRepository, UserCreationService userCreationService,
            PasswordService passwordService, MailSendingService mailSendingService, AccountSecurityEventService accountSecurityEventService,
            DistributedDataProvider distributedDataProvider, SessionFreshnessService sessionFreshnessService, UserActivityService userActivityService,
            @Value("${artemis.user-management.registration.allowed-email-pattern:#{null}}") Optional<Pattern> allowedEmailPattern) {
        this.userRecoveryKeyService = userRecoveryKeyService;
        this.userRepository = userRepository;
        this.userCreationService = userCreationService;
        this.passwordService = passwordService;
        this.mailSendingService = mailSendingService;
        this.accountSecurityEventService = accountSecurityEventService;
        this.allowedEmailPattern = allowedEmailPattern;
        this.requestCooldowns = distributedDataProvider.getExpiringMap(REQUEST_COOLDOWN_MAP_NAME, MIN_INTERVAL_BETWEEN_REQUESTS);
        this.sessionFreshnessService = sessionFreshnessService;
        this.userActivityService = userActivityService;
    }

    /**
     * Whether a change of the address of this account has to be confirmed. Only an internal account does: the address of an
     * externally managed one comes from its identity provider and is not changed here.
     *
     * @param user the account
     * @return true if a change has to go through {@link #requestChange}
     */
    public boolean requiresConfirmation(User user) {
        return user.isInternal();
    }

    /**
     * The e-mail change the account is waiting to have confirmed, if there is one that has not expired.
     *
     * @param userId the account
     * @return the pending change
     */
    public Optional<PendingEmailChangeDTO> findPendingChange(long userId) {
        return findValidPendingChange(userId).map(change -> PendingEmailChangeDTO.of(change, VALIDITY));
    }

    /**
     * Starts an e-mail change: stores what was asked for and mails a confirmation link to the current and to the new address. The
     * address of the account does not change.
     * <p>
     * Asking for the address that is already pending returns that request without mailing again, so that saving the settings form
     * twice does not send four messages.
     *
     * @param user           the account that asks for the change
     * @param requestedEmail the address the account should get
     * @param langKey        the language the owner saves with the same request, which the mails are written in because it is saved only after this call;
     *                           {@code null} or blank for the language the account has
     * @return the pending change
     * @throws BadRequestAlertException with {@code emailChangeUnavailable} if no mail is configured, {@code emailInvalid} if the
     *                                      address is empty or not allowed, and {@code emailChangeRequestedTooRecently} if
     *                                      another request was made a moment ago
     */
    public PendingEmailChangeDTO requestChange(User user, String requestedEmail, @Nullable String langKey) {
        // Read before the session is checked and stored with the request: a password change ends a pending request, but one that is stored after it would survive it, so the
        // request is applied only while the credentials are still as they were here. If they change after this read, the stored moment is the older one and nothing applies.
        Instant credentialsChangedAt = userActivityService.findCredentialsChangedDate(user.getId());
        // A session that survived a password reset must not be able to start a new request, which the owner could otherwise only keep cancelling.
        sessionFreshnessService.requireCurrentSession();
        String newEmail = User.canonicalEmail(requestedEmail);
        if (newEmail == null) {
            // Removing the address would leave an account that needs only the new address to confirm, and so would be the way
            // around the confirmation of the current one.
            throw new BadRequestAlertException("The e-mail address of an account cannot be removed", ENTITY_NAME, "emailInvalid");
        }
        requireMailConfigured();
        checkAddressAllowed(newEmail);
        userCreationService.validateEmailIsAvailable(newEmail, user.getId());

        Optional<PendingEmailChange> existing = findValidPendingChange(user.getId());
        if (existing.isPresent() && Objects.equals(existing.get().getNewEmail(), newEmail)) {
            return PendingEmailChangeDTO.of(existing.get(), VALIDITY);
        }
        return startRequest(user, newEmail, langKey, credentialsChangedAt);
    }

    /**
     * Mails new links for the pending change to both addresses. The earlier links stop working and the confirmations so far are
     * dropped, because the owner of an address confirms the link they have in front of them.
     *
     * @param user the account
     * @return the renewed pending change
     * @throws BadRequestAlertException with {@code noPendingEmailChange} if nothing is pending, and
     *                                      {@code emailChangeRequestedTooRecently} if the last mails went out a moment ago
     */
    public PendingEmailChangeDTO resend(User user) {
        // Read first for the same reason as in requestChange.
        Instant credentialsChangedAt = userActivityService.findCredentialsChangedDate(user.getId());
        sessionFreshnessService.requireCurrentSession();
        requireMailConfigured();
        PendingEmailChange pending = findValidPendingChange(user.getId())
                .orElseThrow(() -> new BadRequestAlertException("No e-mail change is pending", ENTITY_NAME, "noPendingEmailChange"));
        return startRequest(user, pending.getNewEmail(), null, credentialsChangedAt);
    }

    /**
     * Withdraws the pending change. Idempotent.
     *
     * @param userId the account
     */
    public void cancel(long userId) {
        userRecoveryKeyService.clearPendingEmailChange(userId);
    }

    /**
     * Confirms one address of a pending change, and applies the change once both have been confirmed.
     * <p>
     * Idempotent for an address that is already confirmed, so that a link opened twice can simply be followed again.
     * <p>
     * What decides the outcome is two statements that each check that the request still exists: recording the confirmation and
     * applying the address. What this method read before verifying the secret is only used to find the request and is never written
     * back, so a request that is cancelled, replaced or ended by a password reset while the secret is being verified can neither be
     * brought back by this confirmation nor be applied by it.
     *
     * @param keyId     the key id from the mail
     * @param keySecret the secret from the mail
     * @return which address was confirmed and whether that completed the change
     * @throws BadRequestAlertException with {@code invalidEmailChangeKey} if the key is unknown, wrong or expired
     */
    public EmailChangeConfirmationResultDTO confirm(String keyId, String keySecret) {
        long userId = userRecoveryKeyService.findByEmailChangeKeyId(keyId).map(UserRecoveryKey::getUserId).orElseThrow(EmailChangeService::invalidKey);
        PendingEmailChange change = pendingChangeWithKey(userId, keyId);
        if (isExpired(change)) {
            userRecoveryKeyService.clearPendingEmailChangeOfRequest(userId, change);
            throw invalidKey();
        }

        boolean isCurrentAddress = keyId.equals(change.getCurrentKeyId());
        String expectedHash = isCurrentAddress ? change.getCurrentKeyHash() : change.getNewKeyHash();
        if (!passwordService.checkPasswordMatch(keySecret, expectedHash)) {
            throw invalidKey();
        }
        EmailChangeAddress address = isCurrentAddress ? EmailChangeAddress.CURRENT : EmailChangeAddress.NEW;

        userRecoveryKeyService.confirmEmailChangeAddress(keyId, isCurrentAddress);

        // Read again: what the request looks like now, after the confirmation above, decides what happens next.
        PendingEmailChange confirmed = pendingChangeWithKey(userId, keyId);
        if (!confirmed.isFullyConfirmed()) {
            return new EmailChangeConfirmationResultDTO(false, address);
        }
        applyChange(userId, confirmed);
        return new EmailChangeConfirmationResultDTO(true, address);
    }

    /**
     * Reloads the pending request and requires it to still own the supplied confirmation key.
     * This check prevents a cancelled or replaced request from being confirmed through an older snapshot.
     *
     * @param userId the account whose pending request is checked
     * @param keyId  the confirmation key that must still belong to that request
     * @return the current pending request
     * @throws BadRequestAlertException if the request no longer owns the key
     */
    private PendingEmailChange pendingChangeWithKey(long userId, String keyId) {
        return userRecoveryKeyService.findPendingEmailChange(userId).filter(pending -> keyId.equals(pending.getCurrentKeyId()) || keyId.equals(pending.getNewKeyId()))
                .orElseThrow(EmailChangeService::invalidKey);
    }

    /**
     * Revalidates the account and destination address, then applies a fully confirmed request with a conditional update.
     * Both confirmation key ids identify the request at the write boundary; cleanup preserves any newer request.
     *
     * @param userId the account whose address is changed
     * @param change the fully confirmed request just reloaded from persistence
     */
    private void applyChange(long userId, PendingEmailChange change) {
        User user = userRepository.findById(userId).filter(account -> Boolean.TRUE.equals(account.getActivated()) && !account.isDeleted()).orElse(null);
        if (user == null) {
            userRecoveryKeyService.clearPendingEmailChangeOfRequest(userId, change);
            throw invalidKey();
        }

        String newEmail = change.getNewEmail();
        try {
            // Checked again: the address can have been taken, or the allowed addresses changed, since the request was made.
            checkAddressAllowed(newEmail);
            userCreationService.validateEmailIsAvailable(newEmail, userId);
        }
        catch (RuntimeException e) {
            userRecoveryKeyService.clearPendingEmailChangeOfRequest(userId, change);
            throw e;
        }

        String previousEmail = user.getEmail();
        String previousLangKey = user.getLangKey();
        if (userRepository.applyConfirmedEmailChange(userId, newEmail, change.getCurrentKeyId(), change.getNewKeyId()) == 0) {
            // Nothing was left to apply. Either the other confirmation completed the change a moment ago, which is not a failure for
            // this one, or the request was cancelled or the account recovered in the meantime, which must not apply anything. The same
            // holds if the account has another address than the one the request was made over, for example because an administrator
            // assigned one while the request was being issued.
            if (newEmail.equals(userRepository.findById(userId).map(User::getEmail).orElse(null))) {
                return;
            }
            // Dropped, so that it cannot be retried: it matches only the request that was read, a newer one stays.
            userRecoveryKeyService.clearPendingEmailChangeOfRequest(userId, change);
            throw invalidKey();
        }
        userRecoveryKeyService.clearPendingEmailChangeOfRequest(userId, change);
        user.setEmail(newEmail);
        log.info("Changed the e-mail address of user {} after both addresses confirmed", user.getLogin());
        // Tells the previous address, which is the one channel the owner may still read if both confirmations were not theirs.
        accountSecurityEventService.recordEmailChanged(user, previousEmail, previousLangKey);
    }

    /**
     * Claims the account cooldown, replaces pending confirmation keys and sends fresh links to both addresses.
     * Only hashes are persisted. An account without a current address starts with that side already confirmed.
     *
     * @param user                 the account requesting the change
     * @param newEmail             the canonical destination address, validated by the caller
     * @param langKey              the language of both mails, or {@code null} or blank for the language the account has
     * @param credentialsChangedAt when the credentials of the account last changed, as it was read before the session was checked, or null if they never have
     * @return the newly stored pending request without confirmation secrets
     */
    private PendingEmailChangeDTO startRequest(User user, String newEmail, @Nullable String langKey, @Nullable Instant credentialsChangedAt) {
        requireNotRequestedTooRecently(user.getId());
        String mailLangKey = langKey == null || langKey.isBlank() ? user.getLangKey() : langKey;
        String currentKeyId = RandomUtil.generateResetKeyId();
        String currentSecret = RandomUtil.generateResetKeySecret();
        String newKeyId = RandomUtil.generateResetKeyId();
        String newSecret = RandomUtil.generateResetKeySecret();
        // An account without an address has nothing for its owner to confirm, so it starts out confirmed on that side.
        Instant now = Instant.now();
        boolean hasCurrentAddress = user.getEmail() != null;
        // The link to the current address is mailed to this address, so it is the one the change may only be applied over. The account is read before the secrets are
        // hashed, which takes a moment, and the address can be replaced meanwhile: see the match in UserRepository#applyConfirmedEmailChange.
        PendingEmailChange change = new PendingEmailChange(newEmail, user.getEmail(), credentialsChangedAt, currentKeyId, passwordService.hashPassword(currentSecret), newKeyId,
                passwordService.hashPassword(newSecret), now, hasCurrentAddress ? null : now);
        userRecoveryKeyService.storePendingEmailChange(user.getId(), change);

        if (hasCurrentAddress) {
            sendConfirmationMail(new MailRecipientDTO(user.getEmail(), mailLangKey, user.getLogin(), user.getFirstName(), user.getLastName(), null, null, user.isTestUser()), true,
                    newEmail, currentKeyId, currentSecret);
        }
        sendConfirmationMail(new MailRecipientDTO(newEmail, mailLangKey, user.getLogin(), user.getFirstName(), user.getLastName(), null, null, user.isTestUser()), false, newEmail,
                newKeyId, newSecret);
        accountSecurityEventService.recordEmailChangeRequested(user);
        return PendingEmailChangeDTO.of(change, VALIDITY);
    }

    /**
     * Sends the address-specific confirmation template with its own key and the request validity period.
     *
     * @param recipient         the current or proposed address receiving this link
     * @param forCurrentAddress whether this link confirms the existing address
     * @param newEmail          the proposed account address shown in the message
     * @param keyId             the public lookup id of this confirmation key
     * @param keySecret         the unpersisted secret included in the confirmation link
     */
    private void sendConfirmationMail(MailRecipientDTO recipient, boolean forCurrentAddress, String newEmail, String keyId, String keySecret) {
        String subjectKey = forCurrentAddress ? "email.emailChange.current.title" : "email.emailChange.new.title";
        mailSendingService.buildAndSendAsync(recipient, subjectKey, "mail/emailChangeConfirmationEmail",
                Map.of("forCurrentAddress", forCurrentAddress, "newEmail", newEmail, "keyId", keyId, "keySecret", keySecret, "validityInHours", VALIDITY.toHours()));
    }

    /**
     * Reads the pending request only while its validity period remains open; this read does not delete expired rows.
     *
     * @param userId the account whose pending request is read
     * @return the unexpired pending request, if present
     */
    private Optional<PendingEmailChange> findValidPendingChange(long userId) {
        return userRecoveryKeyService.findPendingEmailChange(userId).filter(change -> !isExpired(change));
    }

    /**
     * Treats the exact end of the validity period as expired.
     *
     * @param change the request whose creation time defines the validity period
     * @return whether confirmation is no longer allowed
     */
    private static boolean isExpired(PendingEmailChange change) {
        return !change.getRequestedAt().plus(VALIDITY).isAfter(Instant.now());
    }

    /**
     * Claims the pause for this account, atomically and across nodes, so that two requests made at the same moment do not both send
     * their mails. The claim is not released by cancelling the request.
     */
    private void requireNotRequestedTooRecently(long userId) {
        if (requestCooldowns.putIfAbsent(userId, System.currentTimeMillis()) != null) {
            throw new BadRequestAlertException("An e-mail change was requested a moment ago, please wait a minute", ENTITY_NAME, "emailChangeRequestedTooRecently");
        }
    }

    /**
     * Rejects changes when the instance cannot deliver the confirmation links required to complete them.
     *
     * @throws BadRequestAlertException with {@code emailChangeUnavailable} when mail is unavailable
     */
    private void requireMailConfigured() {
        if (!mailSendingService.isMailConfigured()) {
            throw new BadRequestAlertException("The e-mail address cannot be changed here because this Artemis instance cannot send mail to confirm it", ENTITY_NAME,
                    "emailChangeUnavailable");
        }
    }

    /**
     * Applies the current configured address restriction, including when a previously requested change is confirmed.
     *
     * @param email the canonical address to validate
     * @throws BadRequestAlertException with {@code emailInvalid} if the address violates the configured pattern
     */
    private void checkAddressAllowed(String email) {
        if (allowedEmailPattern.isPresent() && !allowedEmailPattern.get().matcher(email).matches()) {
            throw new BadRequestAlertException("The provided email is invalid and does not follow the specified pattern", ENTITY_NAME, "emailInvalid");
        }
    }

    /**
     * Uses the same public error for unknown, replaced, invalid and expired confirmation keys.
     *
     * @return the confirmation-key validation error
     */
    private static BadRequestAlertException invalidKey() {
        return new BadRequestAlertException("The key to confirm the e-mail change is invalid or has expired", ENTITY_NAME, "invalidEmailChangeKey");
    }
}
