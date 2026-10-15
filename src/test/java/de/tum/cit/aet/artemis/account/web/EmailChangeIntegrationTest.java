package de.tum.cit.aet.artemis.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.account.domain.PendingEmailChange;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.BasicInformationUpdateResultDTO;
import de.tum.cit.aet.artemis.account.dto.EmailChangeConfirmationResultDTO;
import de.tum.cit.aet.artemis.account.dto.EmailChangeConfirmationResultDTO.EmailChangeAddress;
import de.tum.cit.aet.artemis.account.dto.EmailChangeKeyDTO;
import de.tum.cit.aet.artemis.account.dto.PendingEmailChangeDTO;
import de.tum.cit.aet.artemis.account.repository.UserRecoveryKeyRepository;
import de.tum.cit.aet.artemis.account.service.EmailChangeService;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.service.UserRecoveryKeyService;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.PasswordChangeDTO;
import de.tum.cit.aet.artemis.core.dto.PasswordResetKeyDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.util.ConfigUtil;
import de.tum.cit.aet.artemis.core.util.SessionTestUtil;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the confirmed change of the e-mail address of an internal account.
 * <p>
 * The address is where a password reset goes, so a session that is not the owner's must not be able to move it. These tests cover the
 * happy path and, more importantly, the ways the confirmation could be bypassed: confirming only from the new address, confirming
 * after the owner took the account back with a password change or reset, an expired request, and a request without mail.
 */
class EmailChangeIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String LOGIN = "emailchangeuser";

    private static final String NEW_EMAIL = "new-address@test.de";

    private static final String CONFIRM_PATH = "/api/core/public/account/confirm-email-change";

    private static final String BASIC_INFORMATION_PATH = "/api/account/basic-information";

    private static final String PENDING_PATH = "/api/account/pending-email-change";

    private static final String CONFIRMATION_TEMPLATE = "mail/emailChangeConfirmationEmail";

    @Autowired
    private UserRecoveryKeyService userRecoveryKeyService;

    @Autowired
    private UserRecoveryKeyRepository userRecoveryKeyRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private EmailChangeService emailChangeService;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private DistributedDataProvider distributedDataProvider;

    @Autowired
    private UserActivityService userActivityService;

    private User user;

    private String originalEmail;

    /**
     * What a confirmation mail carried: where it went and the key that confirms the address it went to.
     */
    private record ConfirmationMail(String recipientEmail, String keyId, String keySecret) {
    }

    @BeforeEach
    void setUp() {
        user = userUtilService.createAndSaveUser(LOGIN, passwordService.hashPassword(UserFactory.USER_PASSWORD));
        originalEmail = user.getEmail();
        waitOutTheCooldown();
    }

    @AfterEach
    void tearDown() {
        // The accounts keep their addresses between tests, and an address is unique, so one test taking NEW_EMAIL would refuse it to all others.
        userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), "emailchange"));
        waitOutTheCooldown();
    }

    /**
     * Lets the pause between two requests of the account pass without waiting a minute, and keeps it from carrying over to the next test.
     */
    private void waitOutTheCooldown() {
        DistributedMap<Long, Long> cooldowns = distributedDataProvider.getExpiringMap(EmailChangeService.REQUEST_COOLDOWN_MAP_NAME, Duration.ofMinutes(1));
        cooldowns.remove(user.getId());
    }

    private UserDTO updateWithEmail(String email) {
        UserDTO update = new UserDTO(user);
        update.setEmail(email);
        return update;
    }

    private BasicInformationUpdateResultDTO requestChangeTo(String email) throws Exception {
        return request.putWithResponseBody(BASIC_INFORMATION_PATH, updateWithEmail(email), BasicInformationUpdateResultDTO.class, HttpStatus.OK);
    }

    private List<ConfirmationMail> confirmationMails() {
        ArgumentCaptor<MailRecipientDTO> recipients = ArgumentCaptor.forClass(MailRecipientDTO.class);
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(mailSendingService, atLeast(0)).buildAndSendAsync(recipients.capture(), anyString(), eq(CONFIRMATION_TEMPLATE), variables.capture());
        List<ConfirmationMail> mails = new ArrayList<>();
        for (int i = 0; i < recipients.getAllValues().size(); i++) {
            Map<String, Object> mailVariables = variables.getAllValues().get(i);
            mails.add(new ConfirmationMail(recipients.getAllValues().get(i).email(), (String) mailVariables.get("keyId"), (String) mailVariables.get("keySecret")));
        }
        return mails;
    }

    private ConfirmationMail mailTo(String email) {
        return confirmationMails().reversed().stream().filter(mail -> mail.recipientEmail().equals(email)).findFirst().orElseThrow();
    }

    private EmailChangeConfirmationResultDTO confirm(ConfirmationMail mail) throws Exception {
        return request.postWithResponseBody(CONFIRM_PATH, new EmailChangeKeyDTO(mail.keyId(), mail.keySecret()), EmailChangeConfirmationResultDTO.class, HttpStatus.OK);
    }

    /**
     * Confirms with the given secret and expects the refusal with the given error key. The key is the translation the client shows, which the server sends in a header.
     */
    private void confirmFailing(ConfirmationMail mail, String secret, String errorKey) throws Exception {
        request.postWithResponseBody(CONFIRM_PATH, new EmailChangeKeyDTO(mail.keyId(), secret), Map.class, HttpStatus.BAD_REQUEST, new HttpHeaders(), expectedError(errorKey),
                null);
    }

    private static Map<String, String> expectedError(String errorKey) {
        return Map.of("X-artemisApp-error", "error." + errorKey);
    }

    private void putFailing(UserDTO update, String errorKey) throws Exception {
        request.putWithResponseBody(BASIC_INFORMATION_PATH, update, Map.class, HttpStatus.BAD_REQUEST, expectedError(errorKey));
    }

    private String emailOfAccount() {
        return userTestRepository.findOneByLogin(LOGIN).orElseThrow().getEmail();
    }

    private Optional<PendingEmailChange> storedPendingChange() {
        return userRecoveryKeyService.findPendingEmailChange(user.getId());
    }

    /**
     * Makes the stored request look as if it had been made that long ago, which is how the validity and the pause between requests are tested without waiting.
     */
    private void ageRequestBy(Duration age) {
        PendingEmailChange pending = storedPendingChange().orElseThrow();
        var aged = new PendingEmailChange(pending.getNewEmail(), pending.getCurrentKeyId(), pending.getCurrentKeyHash(), pending.getNewKeyId(), pending.getNewKeyHash(),
                pending.getRequestedAt().minus(age), pending.getCurrentConfirmedAt());
        aged.setNewConfirmedAt(pending.getNewConfirmedAt());
        userRecoveryKeyService.storePendingEmailChange(user.getId(), aged);
    }

    /**
     * Issuing a reset key must not write back what the row held when it was read. A cancellation that happens right after that read ends the request, and the reset key
     * is stored next to it without bringing the request or its confirmations back.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void issuingAResetKeyDoesNotRestoreAnEmailChangeThatWasCancelledMeanwhile() throws Exception {
        requestChangeTo(NEW_EMAIL);
        confirm(mailTo(originalEmail));
        assertThat(storedPendingChange()).isPresent();

        AtomicBoolean cancelled = new AtomicBoolean(false);
        UserRecoveryKeyRepository racingRepository = mock(UserRecoveryKeyRepository.class, delegatesTo(userRecoveryKeyRepository));
        Answer<Object> readThenCancel = invocation -> {
            Object result = invocation.getMethod().invoke(userRecoveryKeyRepository, invocation.getArguments());
            if (cancelled.compareAndSet(false, true)) {
                userRecoveryKeyService.clearPendingEmailChange(user.getId());
            }
            return result;
        };
        doAnswer(readThenCancel).when(racingRepository).findByUserId(anyLong());
        doAnswer(readThenCancel).when(racingRepository).existsById(anyLong());

        new UserRecoveryKeyService(racingRepository).storeResetKey(user.getId(), "racingresetkey", "hash", Instant.now());

        assertThat(cancelled).as("the cancellation ran between the first read and the write").isTrue();
        assertThat(storedPendingChange()).as("the cancelled request, and its confirmation of the current address, stay gone").isEmpty();
        assertThat(userRecoveryKeyService.findResetKeyId(user.getId())).isEqualTo("racingresetkey");
    }

    @Test
    @WithMockUser(username = LOGIN)
    void storingKeysOfDifferentKindsLeavesTheOthersAsTheyAre() throws Exception {
        requestChangeTo(NEW_EMAIL);
        PendingEmailChange pending = storedPendingChange().orElseThrow();

        userRecoveryKeyService.storeActivationKey(user.getId(), "activationkey");
        userRecoveryKeyService.storeResetKey(user.getId(), "resetkeyid", "resetkeyhash", Instant.now());

        assertThat(userRecoveryKeyService.findActivationKey(user.getId())).isEqualTo("activationkey");
        assertThat(userRecoveryKeyService.findResetKeyId(user.getId())).isEqualTo("resetkeyid");
        assertThat(storedPendingChange().orElseThrow().getCurrentKeyId()).isEqualTo(pending.getCurrentKeyId());

        userRecoveryKeyService.clearActivationKey(user.getId());
        userRecoveryKeyService.clearResetKey(user.getId());

        assertThat(userRecoveryKeyService.findActivationKey(user.getId())).isNull();
        assertThat(userRecoveryKeyService.findResetKeyId(user.getId())).isNull();
        assertThat(storedPendingChange()).as("clearing the keys leaves the request, and the row that holds it").isPresent();

        userRecoveryKeyService.clearPendingEmailChange(user.getId());

        assertThat(userRecoveryKeyRepository.findByUserId(user.getId())).as("nothing is outstanding, so the row is gone").isEmpty();
    }

    /**
     * A settings save of an account that confirms address changes never writes the address, so an address that a confirmation applied after the save read the account is
     * not replaced by the one it read.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aSettingsSaveLeavesTheAddressOfTheAccountAlone() {
        user.setEmail(NEW_EMAIL);
        userTestRepository.save(user);

        userCreationService.updateBasicInformationOfCurrentUser("Renamed", "Account", null, user.getLangKey(), null);

        User saved = userTestRepository.findOneByLogin(LOGIN).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(NEW_EMAIL);
        assertThat(saved.getFirstName()).isEqualTo("Renamed");
        assertThat(saved.getLastName()).isEqualTo("Account");
    }

    @Test
    @WithMockUser(username = LOGIN)
    void requestingAChangeStoresItAndLeavesTheAddressUnchanged() throws Exception {
        UserDTO update = updateWithEmail(NEW_EMAIL);
        update.setFirstName("Renamed");

        BasicInformationUpdateResultDTO result = request.putWithResponseBody(BASIC_INFORMATION_PATH, update, BasicInformationUpdateResultDTO.class, HttpStatus.OK);

        PendingEmailChangeDTO pending = result.pendingEmailChange();
        assertThat(pending).isNotNull();
        assertThat(pending.newEmail()).isEqualTo(NEW_EMAIL);
        assertThat(pending.currentAddressConfirmed()).isFalse();
        assertThat(pending.newAddressConfirmed()).isFalse();
        assertThat(pending.expiresAt()).isEqualTo(pending.requestedAt().plus(EmailChangeService.VALIDITY));
        // The address is only applied once both addresses have confirmed. What else was saved takes effect at once.
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
        assertThat(userTestRepository.findOneByLogin(LOGIN).orElseThrow().getFirstName()).isEqualTo("Renamed");
    }

    @Test
    @WithMockUser(username = LOGIN)
    void eachAddressGetsItsOwnKey() throws Exception {
        requestChangeTo(NEW_EMAIL);

        List<ConfirmationMail> mails = confirmationMails();
        assertThat(mails).hasSize(2);
        assertThat(mails).extracting(ConfirmationMail::recipientEmail).containsExactlyInAnyOrder(originalEmail, NEW_EMAIL);
        assertThat(mailTo(originalEmail).keyId()).isNotEqualTo(mailTo(NEW_EMAIL).keyId());
        assertThat(mailTo(originalEmail).keySecret()).isNotEqualTo(mailTo(NEW_EMAIL).keySecret());
        // Only hashes are stored, so reading the table does not reveal a key that could still be used.
        PendingEmailChange stored = storedPendingChange().orElseThrow();
        assertThat(stored.getCurrentKeyHash()).isNotEqualTo(mailTo(originalEmail).keySecret());
        assertThat(stored.getNewKeyHash()).isNotEqualTo(mailTo(NEW_EMAIL).keySecret());
    }

    @Test
    @WithMockUser(username = LOGIN)
    void theAddressChangesOnlyAfterBothAddressesConfirmed() throws Exception {
        requestChangeTo(NEW_EMAIL);

        EmailChangeConfirmationResultDTO fromCurrent = confirm(mailTo(originalEmail));
        assertThat(fromCurrent.completed()).isFalse();
        assertThat(fromCurrent.address()).isEqualTo(EmailChangeAddress.CURRENT);
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
        PendingEmailChangeDTO afterFirst = request.get(PENDING_PATH, HttpStatus.OK, PendingEmailChangeDTO.class);
        assertThat(afterFirst.currentAddressConfirmed()).isTrue();
        assertThat(afterFirst.newAddressConfirmed()).isFalse();

        EmailChangeConfirmationResultDTO fromNew = confirm(mailTo(NEW_EMAIL));
        assertThat(fromNew.completed()).isTrue();
        assertThat(fromNew.address()).isEqualTo(EmailChangeAddress.NEW);
        assertThat(emailOfAccount()).isEqualTo(NEW_EMAIL);
        assertThat(storedPendingChange()).isEmpty();
        assertThat(request.get(PENDING_PATH, HttpStatus.NO_CONTENT, PendingEmailChangeDTO.class)).isNull();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void theOrderInWhichTheAddressesConfirmDoesNotMatter() throws Exception {
        requestChangeTo(NEW_EMAIL);

        assertThat(confirm(mailTo(NEW_EMAIL)).completed()).isFalse();
        assertThat(confirm(mailTo(originalEmail)).completed()).isTrue();

        assertThat(emailOfAccount()).isEqualTo(NEW_EMAIL);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void theFormerAddressIsToldThatTheAddressChanged() throws Exception {
        requestChangeTo(NEW_EMAIL);
        confirm(mailTo(originalEmail));
        confirm(mailTo(NEW_EMAIL));

        // The previous address is the one channel that still reaches the owner if neither confirmation was theirs.
        ArgumentCaptor<MailRecipientDTO> recipients = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailSendingService, atLeast(1)).buildAndSendAsync(recipients.capture(), anyString(), eq("mail/notification/emailChangedEmail"), anyMap());
        boolean noticeSentToThePreviousAddress = recipients.getAllValues().stream().anyMatch(recipient -> originalEmail.equals(recipient.email()));
        assertThat(noticeSentToThePreviousAddress).isTrue();
    }

    /**
     * Regression test for the takeover through a stolen session: whoever holds a session can name an address of their own and
     * confirm it from there, so confirming the new address must change nothing until the owner of the current one confirms too.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void confirmingOnlyTheNewAddressDoesNotChangeTheAddress() throws Exception {
        requestChangeTo(NEW_EMAIL);

        EmailChangeConfirmationResultDTO result = confirm(mailTo(NEW_EMAIL));
        // Following the same link again changes nothing either.
        confirm(mailTo(NEW_EMAIL));

        assertThat(result.completed()).isFalse();
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
        assertThat(storedPendingChange()).isPresent();
    }

    /**
     * Regression test: changing the password is how the owner reacts to a suspected intrusion, and has to cancel what the intruder
     * asked for, or the intruder could carry on from the request they had already made.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void changingThePasswordCancelsAPendingChange() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail toCurrent = mailTo(originalEmail);
        ConfirmationMail toNew = mailTo(NEW_EMAIL);

        request.postWithoutLocation("/api/account/change-password", new PasswordChangeDTO(UserFactory.USER_PASSWORD, "12345678", null), HttpStatus.OK, null);

        assertThat(storedPendingChange()).isEmpty();
        confirmFailing(toCurrent, toCurrent.keySecret(), "invalidEmailChangeKey");
        confirmFailing(toNew, toNew.keySecret(), "invalidEmailChangeKey");
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aCompletedPasswordResetCancelsAPendingChange() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail toNew = mailTo(NEW_EMAIL);
        PasswordResetKeyDTO resetKey = userService.prepareUserForPasswordReset(user).orElseThrow();

        userService.completePasswordReset("a-new-password-12", resetKey.id(), resetKey.secret(), new CredentialRevocationChoiceDTO(false, false, false)).orElseThrow();

        assertThat(storedPendingChange()).isEmpty();
        confirmFailing(toNew, toNew.keySecret(), "invalidEmailChangeKey");
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void deactivatingTheAccountCancelsAPendingChange() throws Exception {
        requestChangeTo(NEW_EMAIL);

        userCreationService.deactivateUser(userTestRepository.findOneByLogin(LOGIN).orElseThrow());

        assertThat(storedPendingChange()).isEmpty();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void anExpiredRequestCannotBeConfirmed() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail toCurrent = mailTo(originalEmail);
        ageRequestBy(EmailChangeService.VALIDITY.plusMinutes(1));

        confirmFailing(toCurrent, toCurrent.keySecret(), "invalidEmailChangeKey");
        // It no longer counts as pending, and is cleaned up by the attempt to use it.
        assertThat(storedPendingChange()).isEmpty();
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void anExpiredRequestIsNotReportedAsPending() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ageRequestBy(EmailChangeService.VALIDITY.plusMinutes(1));

        assertThat(request.get(PENDING_PATH, HttpStatus.NO_CONTENT, PendingEmailChangeDTO.class)).isNull();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aWrongSecretIsRefusedAndConfirmsNothing() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail toCurrent = mailTo(originalEmail);

        confirmFailing(toCurrent, "x".repeat(20), "invalidEmailChangeKey");

        PendingEmailChangeDTO pending = request.get(PENDING_PATH, HttpStatus.OK, PendingEmailChangeDTO.class);
        assertThat(pending.currentAddressConfirmed()).isFalse();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aKeyThatIsTooShortIsRefused() throws Exception {
        request.postWithResponseBody(CONFIRM_PATH, new EmailChangeKeyDTO("short", "short"), Map.class, HttpStatus.BAD_REQUEST, new HttpHeaders(),
                expectedError("invalidEmailChangeKey"), null);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aChangeIsRefusedWhenNoMailIsConfigured() throws Exception {
        doReturn(false).when(mailSendingService).isMailConfigured();

        // Nothing could be confirmed without mail, so applying the change unconfirmed would be the hole this closes.
        putFailing(updateWithEmail(NEW_EMAIL), "emailChangeUnavailable");

        assertThat(emailOfAccount()).isEqualTo(originalEmail);
        assertThat(storedPendingChange()).isEmpty();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void anAddressThatDoesNotMatchTheAllowedPatternIsRefused() throws Throwable {
        ConfigUtil.testWithChangedConfig(emailChangeService, "allowedEmailPattern", Optional.of(Pattern.compile("[a-z\\-]+@allowed\\.de")), () -> {
            putFailing(updateWithEmail(NEW_EMAIL), "emailInvalid");

            assertThat(storedPendingChange()).isEmpty();
        });
    }

    @Test
    @WithMockUser(username = LOGIN)
    void anAddressOfAnotherAccountIsRefused() throws Exception {
        User other = userUtilService.createAndSaveUser("emailchangeother");

        request.put(BASIC_INFORMATION_PATH, updateWithEmail(other.getEmail()), HttpStatus.BAD_REQUEST);

        assertThat(storedPendingChange()).isEmpty();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void theSameRequestTwiceMailsOnlyOnce() throws Exception {
        BasicInformationUpdateResultDTO first = requestChangeTo(NEW_EMAIL);
        BasicInformationUpdateResultDTO second = requestChangeTo(NEW_EMAIL);

        assertThat(confirmationMails()).hasSize(2);
        // The first answer carries the time as it was taken, the second what the database kept, which is rounded to milliseconds.
        assertThat(second.pendingEmailChange().requestedAt()).isCloseTo(first.pendingEmailChange().requestedAt(), within(2, ChronoUnit.MILLIS));
    }

    /**
     * Every request mails an address the requester chooses, from a mail account the whole installation shares.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aDifferentAddressRightAfterwardsIsRefused() throws Exception {
        requestChangeTo(NEW_EMAIL);

        putFailing(updateWithEmail("another-address@test.de"), "emailChangeRequestedTooRecently");

        assertThat(confirmationMails()).hasSize(2);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aDifferentAddressAfterAMinuteReplacesTheRequest() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail firstToCurrent = mailTo(originalEmail);
        waitOutTheCooldown();

        BasicInformationUpdateResultDTO result = requestChangeTo("another-address@test.de");

        assertThat(result.pendingEmailChange().newEmail()).isEqualTo("another-address@test.de");
        // The key of the replaced request stops working.
        confirmFailing(firstToCurrent, firstToCurrent.keySecret(), "invalidEmailChangeKey");
    }

    /**
     * The settings form sends the address back whenever any other field is saved, which must not look like withdrawing the request.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void savingTheFormWithTheUnchangedAddressKeepsThePendingChange() throws Exception {
        requestChangeTo(NEW_EMAIL);
        UserDTO update = updateWithEmail(originalEmail);
        update.setFirstName("AnotherName");

        BasicInformationUpdateResultDTO result = request.putWithResponseBody(BASIC_INFORMATION_PATH, update, BasicInformationUpdateResultDTO.class, HttpStatus.OK);

        assertThat(result.pendingEmailChange()).isNotNull();
        assertThat(result.pendingEmailChange().newEmail()).isEqualTo(NEW_EMAIL);
        assertThat(storedPendingChange()).isPresent();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void resendingTooSoonIsRefusedAndLaterRenewsBothKeys() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail oldToCurrent = mailTo(originalEmail);

        request.postWithResponseBody(PENDING_PATH + "/resend", null, Map.class, HttpStatus.BAD_REQUEST, new HttpHeaders(), expectedError("emailChangeRequestedTooRecently"), null);

        confirm(oldToCurrent);
        waitOutTheCooldown();
        PendingEmailChangeDTO renewed = request.postWithResponseBody(PENDING_PATH + "/resend", null, PendingEmailChangeDTO.class, HttpStatus.OK);

        assertThat(confirmationMails()).hasSize(4);
        // Earlier confirmations are dropped, because the owner of an address confirms the link they have in front of them.
        assertThat(renewed.currentAddressConfirmed()).isFalse();
        confirmFailing(oldToCurrent, oldToCurrent.keySecret(), "invalidEmailChangeKey");
        assertThat(confirm(mailTo(originalEmail)).completed()).isFalse();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void resendingWithoutAPendingChangeIsRefused() throws Exception {
        request.postWithResponseBody(PENDING_PATH + "/resend", null, Map.class, HttpStatus.BAD_REQUEST, new HttpHeaders(), expectedError("noPendingEmailChange"), null);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void cancellingWithdrawsTheRequestAndIsIdempotent() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail toNew = mailTo(NEW_EMAIL);

        request.delete(PENDING_PATH, HttpStatus.OK);
        request.delete(PENDING_PATH, HttpStatus.OK);

        assertThat(storedPendingChange()).isEmpty();
        confirmFailing(toNew, toNew.keySecret(), "invalidEmailChangeKey");
    }

    /**
     * The pause must not go away with the request, or alternating a request and a cancellation would mail two addresses every time
     * without any limit.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void cancellingARequestDoesNotEndThePauseBeforeTheNextOne() throws Exception {
        requestChangeTo(NEW_EMAIL);
        request.delete(PENDING_PATH, HttpStatus.OK);

        putFailing(updateWithEmail(NEW_EMAIL), "emailChangeRequestedTooRecently");
        putFailing(updateWithEmail("another-address@test.de"), "emailChangeRequestedTooRecently");

        assertThat(confirmationMails()).hasSize(2);
        assertThat(storedPendingChange()).isEmpty();
        waitOutTheCooldown();
        assertThat(requestChangeTo(NEW_EMAIL).pendingEmailChange()).isNotNull();
    }

    /**
     * A confirmation that was already past the check of its secret when the request was cancelled must not bring the request back:
     * recording it is a statement that matches only a request that still exists, not a save of the row that was read earlier.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aConfirmationThatArrivesAfterTheCancellationDoesNotBringTheRequestBack() throws Exception {
        requestChangeTo(NEW_EMAIL);
        ConfirmationMail toCurrent = mailTo(originalEmail);
        request.delete(PENDING_PATH, HttpStatus.OK);

        userRecoveryKeyService.confirmEmailChangeAddress(toCurrent.keyId(), true);
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo(NEW_EMAIL).keyId(), false);

        assertThat(storedPendingChange()).isEmpty();
    }

    /**
     * The address is applied by one statement that matches only while the request exists and has been confirmed from both
     * addresses, so a copy of the request that was read before it was cancelled, replaced or its account recovered cannot apply it.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aChangeIsAppliedOnlyWhileTheConfirmedRequestStillExists() throws Exception {
        requestChangeTo(NEW_EMAIL);
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo(originalEmail).keyId(), true);
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo(NEW_EMAIL).keyId(), false);
        PendingEmailChange confirmed = storedPendingChange().orElseThrow();
        long userId = user.getId();

        // Cancelled after it was read: nothing is applied.
        request.delete(PENDING_PATH, HttpStatus.OK);
        assertThat(userTestRepository.applyConfirmedEmailChange(userId, NEW_EMAIL, confirmed.getCurrentKeyId(), confirmed.getNewKeyId())).isZero();
        assertThat(emailOfAccount()).isEqualTo(originalEmail);

        // Replaced by another request: the keys of the old one match nothing.
        waitOutTheCooldown();
        requestChangeTo("another-address@test.de");
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo(originalEmail).keyId(), true);
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo("another-address@test.de").keyId(), false);
        assertThat(userTestRepository.applyConfirmedEmailChange(userId, NEW_EMAIL, confirmed.getCurrentKeyId(), confirmed.getNewKeyId())).isZero();
        assertThat(emailOfAccount()).isEqualTo(originalEmail);

        // The request that exists and is confirmed from both addresses is applied, and only once.
        PendingEmailChange current = storedPendingChange().orElseThrow();
        assertThat(userTestRepository.applyConfirmedEmailChange(userId, "another-address@test.de", current.getCurrentKeyId(), current.getNewKeyId())).isOne();
        assertThat(emailOfAccount()).isEqualTo("another-address@test.de");
        assertThat(userTestRepository.applyConfirmedEmailChange(userId, "another-address@test.de", current.getCurrentKeyId(), current.getNewKeyId())).isZero();
    }

    /**
     * The cleanup after a confirmation, an expiry or a failed check names the request it belongs to, so a request that replaced it in the
     * meantime is not deleted together with it.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void cleaningUpARequestLeavesAReplacementAlone() throws Exception {
        requestChangeTo(NEW_EMAIL);
        PendingEmailChange first = storedPendingChange().orElseThrow();
        waitOutTheCooldown();
        requestChangeTo("another-address@test.de");

        userRecoveryKeyService.clearPendingEmailChangeOfRequest(user.getId(), first);

        assertThat(storedPendingChange()).isPresent();
        assertThat(storedPendingChange().orElseThrow().getNewEmail()).isEqualTo("another-address@test.de");

        PendingEmailChange second = storedPendingChange().orElseThrow();
        userRecoveryKeyService.clearPendingEmailChangeOfRequest(user.getId(), second);
        assertThat(storedPendingChange()).isEmpty();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aChangeConfirmedFromOnlyOneAddressIsNotApplied() throws Exception {
        requestChangeTo(NEW_EMAIL);
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo(NEW_EMAIL).keyId(), false);
        PendingEmailChange pending = storedPendingChange().orElseThrow();

        assertThat(userTestRepository.applyConfirmedEmailChange(user.getId(), NEW_EMAIL, pending.getCurrentKeyId(), pending.getNewKeyId())).isZero();
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aConfirmedChangeIsNotAppliedToADeactivatedAccount() throws Exception {
        requestChangeTo(NEW_EMAIL);
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo(originalEmail).keyId(), true);
        userRecoveryKeyService.confirmEmailChangeAddress(mailTo(NEW_EMAIL).keyId(), false);
        PendingEmailChange confirmed = storedPendingChange().orElseThrow();
        User deactivated = userTestRepository.findOneByLogin(LOGIN).orElseThrow();
        deactivated.setActivated(false);
        userTestRepository.save(deactivated);

        assertThat(userTestRepository.applyConfirmedEmailChange(user.getId(), NEW_EMAIL, confirmed.getCurrentKeyId(), confirmed.getNewKeyId())).isZero();
        assertThat(emailOfAccount()).isEqualTo(originalEmail);
    }

    /**
     * Regression test: after the owner took the account back with a password change, the session the intruder still holds must not be able to
     * file a new request, which the owner could otherwise only keep cancelling. It is the way in for an account that has no address yet, where
     * the new address alone completes the change.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aSessionThatStartedBeforeTheCredentialsChangedCannotRequestOrResendAnEmailChange() throws Exception {
        requestChangeTo(NEW_EMAIL);
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().minus(Duration.ofHours(1)));
        waitOutTheCooldown();

        request.put(BASIC_INFORMATION_PATH, updateWithEmail("another-address@test.de"), HttpStatus.UNAUTHORIZED);
        request.postWithResponseBody(PENDING_PATH + "/resend", null, Map.class, HttpStatus.UNAUTHORIZED);

        assertThat(confirmationMails()).hasSize(2);
        // Withdrawing a request stays possible for such a session: it only ever protects the account.
        request.delete(PENDING_PATH, HttpStatus.OK);
        assertThat(storedPendingChange()).isEmpty();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void anAccountWithoutAnAddressNeedsOnlyTheNewAddressToConfirm() throws Exception {
        User withoutAddress = userTestRepository.findOneByLogin(LOGIN).orElseThrow();
        withoutAddress.setEmail(null);
        userTestRepository.save(withoutAddress);
        UserDTO update = new UserDTO(withoutAddress);
        update.setEmail(NEW_EMAIL);

        BasicInformationUpdateResultDTO result = request.putWithResponseBody(BASIC_INFORMATION_PATH, update, BasicInformationUpdateResultDTO.class, HttpStatus.OK);

        // There is no current address whose owner could confirm, so that side starts out confirmed and only one mail goes out.
        assertThat(result.pendingEmailChange().currentAddressConfirmed()).isTrue();
        assertThat(confirmationMails()).hasSize(1);
        assertThat(confirm(mailTo(NEW_EMAIL)).completed()).isTrue();
        assertThat(emailOfAccount()).isEqualTo(NEW_EMAIL);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void anExternalAccountKeepsChangingItsAddressDirectly() throws Exception {
        User external = userTestRepository.findOneByLogin(LOGIN).orElseThrow();
        external.setInternal(false);
        userTestRepository.save(external);

        BasicInformationUpdateResultDTO result = request.putWithResponseBody(BASIC_INFORMATION_PATH, updateWithEmail(NEW_EMAIL), BasicInformationUpdateResultDTO.class,
                HttpStatus.OK);

        assertThat(result.pendingEmailChange()).isNull();
        assertThat(emailOfAccount()).isEqualTo(NEW_EMAIL);
        assertThat(confirmationMails()).isEmpty();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void theAddressIsCheckedAgainWhenTheChangeIsApplied() throws Exception {
        requestChangeTo(NEW_EMAIL);
        // Someone else registers the address after the request was made.
        userUtilService.createAndSaveUserWithEmail("emailchangetaker", NEW_EMAIL);
        confirm(mailTo(originalEmail));

        request.postWithResponseBody(CONFIRM_PATH, new EmailChangeKeyDTO(mailTo(NEW_EMAIL).keyId(), mailTo(NEW_EMAIL).keySecret()), Map.class, HttpStatus.BAD_REQUEST,
                new HttpHeaders(), expectedError("emailExists"), null);

        assertThat(emailOfAccount()).isEqualTo(originalEmail);
        // The request is dropped, so it cannot be retried into an address that is no longer free.
        assertThat(storedPendingChange()).isEmpty();
    }

    @Test
    void theConfirmationMailRendersTheLinkAndEscapesTheAddress() throws Exception {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMAN)) {
            for (boolean forCurrentAddress : List.of(true, false)) {
                Context context = new Context(locale);
                context.setVariable("user", new MailRecipientDTO("owner@test.de", locale.getLanguage(), LOGIN, "First", "Last"));
                context.setVariable("baseUrl", URI.create("https://artemis.example.org").toURL());
                context.setVariable("forCurrentAddress", forCurrentAddress);
                context.setVariable("newEmail", "<script>alert(1)</script>@test.de");
                context.setVariable("keyId", "KEYIDKEYID123");
                context.setVariable("keySecret", "SECRETSECRET123");
                context.setVariable("validityInHours", 24L);

                String html = templateEngine.process(CONFIRMATION_TEMPLATE, context);

                assertThat(html).contains("https://artemis.example.org/account/confirm-email-change?keyId=KEYIDKEYID123").contains("keySecret=SECRETSECRET123");
                // The address is user-supplied, so it must reach the message as text and never as markup.
                assertThat(html).doesNotContain("<script>alert(1)");
                if (forCurrentAddress) {
                    assertThat(html).contains("&lt;script&gt;");
                }
            }
        }
    }
}
