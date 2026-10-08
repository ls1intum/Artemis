package de.tum.cit.aet.artemis.notification.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

/**
 * The classification decides whether a mail is kept for another attempt, so it is tested with the exceptions the mail
 * library and Spring really produce, including the one that took production's mail down on 2026-10-07.
 */
class MailFailureClassifierTest {

    /**
     * The exact reply of the SMTP server of the LRZ once the quota of the account was used up.
     */
    private static final String QUOTA_REPLY = "450 4.7.0 <DATA>: Data command rejected: Daily mail quota exceeded";

    private static SMTPSendFailedException smtpFailure(String command, int replyCode, String reply) {
        return new SMTPSendFailedException(command, replyCode, reply, null, null, null, null);
    }

    /**
     * What the mail sender throws: Spring wraps the exception of the server in a {@code MailSendException} that carries it
     * once as the cause and once in the map of failed messages.
     */
    private static MailSendException wrapped(Exception serverFailure) {
        return new MailSendException(Map.of(new Object(), serverFailure));
    }

    @Test
    void shouldClassifyTheQuotaReplyOfTheLrzAsQuotaExceeded() {
        assertThat(MailFailureClassifier.classify(wrapped(smtpFailure("DATA", 450, QUOTA_REPLY)))).isEqualTo(MailFailureKind.QUOTA_EXCEEDED);
    }

    @Test
    void shouldRecognizeTheQuotaWhateverTheCase() {
        assertThat(MailFailureClassifier.classify(smtpFailure("DATA", 452, "452 4.2.2 DAILY MAIL QUOTA EXCEEDED"))).isEqualTo(MailFailureKind.QUOTA_EXCEEDED);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            450 | 450 4.2.1 Mailbox busy, try again later
            451 | 451 4.3.0 Local error in processing
            452 | 452 4.3.1 Insufficient system storage
            """)
    void shouldClassifyOtherTemporaryRefusalsAsTransient(int replyCode, String reply) {
        assertThat(MailFailureClassifier.classify(smtpFailure("DATA", replyCode, reply))).isEqualTo(MailFailureKind.TRANSIENT);
    }

    @Test
    void shouldClassifyServiceNotAvailableAsServerUnavailable() {
        assertThat(MailFailureClassifier.classify(smtpFailure("MAIL", 421, "421 4.3.2 Service not available, closing transmission channel")))
                .isEqualTo(MailFailureKind.SERVER_UNAVAILABLE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            550 | 550 5.1.1 The email account that you tried to reach does not exist
            552 | 552 5.2.2 Mailbox full
            553 | 553 5.1.3 The address is not valid
            554 | 554 5.7.1 Message rejected as spam
            """)
    void shouldClassifyFinalRefusalsAsPermanent(int replyCode, String reply) {
        assertThat(MailFailureClassifier.classify(wrapped(smtpFailure("RCPT", replyCode, reply)))).isEqualTo(MailFailureKind.PERMANENT);
    }

    @Test
    void shouldNotMistakeAPermanentRefusalThatMentionsAQuotaForTheQuotaOfTheAccount() {
        // A mailbox that is full is the recipient's problem and will not be solved by waiting for the quota of the sender.
        assertThat(MailFailureClassifier.classify(smtpFailure("RCPT", 552, "552 5.2.2 Quota exceeded, the mailbox is full"))).isEqualTo(MailFailureKind.PERMANENT);
    }

    @Test
    void shouldClassifyInvalidAddressesAsPermanent() throws Exception {
        // The failure seen on staging for the users of the end-to-end tests: "MailSendException: Failed messages: SendFailedException: Invalid Addresses".
        var invalid = new Address[] { new InternetAddress("artemis_test_user_1@artemis.local") };
        var failure = new SendFailedException("Invalid Addresses", new MessagingException("no such mailbox"), null, null, invalid);

        assertThat(MailFailureClassifier.classify(wrapped(failure))).isEqualTo(MailFailureKind.PERMANENT);
    }

    @Test
    void shouldReadTheReplyCodeOfAnAddressFailureBehindTheInvalidAddressesException() throws Exception {
        var address = new InternetAddress("someone@example.org");
        var addressFailure = new SMTPAddressFailedException(address, "RCPT TO", 450, "450 4.2.0 Greylisted, try again later");
        // No invalid address is listed: the server only asked to try again later, so this is not a final refusal.
        var failure = new SendFailedException("Invalid Addresses", addressFailure, null, new Address[] { address }, null);

        assertThat(MailFailureClassifier.classify(wrapped(failure))).isEqualTo(MailFailureKind.TRANSIENT);
    }

    @Test
    void shouldClassifyAMessageThatCannotBeBuiltAsPermanent() {
        assertThat(MailFailureClassifier.classify(new MailParseException("Could not parse mail", new MessagingException("bad header")))).isEqualTo(MailFailureKind.PERMANENT);
    }

    @Test
    void shouldClassifyRefusedCredentialsAsServerUnavailable() {
        assertThat(MailFailureClassifier.classify(new MailAuthenticationException("Authentication failed", new AuthenticationFailedException("535 5.7.8 Bad credentials"))))
                .isEqualTo(MailFailureKind.SERVER_UNAVAILABLE);
    }

    @Test
    void shouldClassifyAnUnreachableServerAsServerUnavailable() {
        var failure = new MailSendException("Mail server connection failed", new MessagingException("Could not connect to SMTP host", new ConnectException("Connection refused")));

        assertThat(MailFailureClassifier.classify(failure)).isEqualTo(MailFailureKind.SERVER_UNAVAILABLE);
        assertThat(MailFailureClassifier.classify(new MessagingException("Could not connect", new UnknownHostException("postout.lrz.de"))))
                .isEqualTo(MailFailureKind.SERVER_UNAVAILABLE);
        assertThat(MailFailureClassifier.classify(new MessagingException("timeout", new SocketTimeoutException("Read timed out")))).isEqualTo(MailFailureKind.SERVER_UNAVAILABLE);
    }

    @Test
    void shouldFindTheReplyCodeFurtherDownTheCauseChain() {
        assertThat(MailFailureClassifier.classify(new IllegalStateException("could not send", new RuntimeException(smtpFailure("DATA", 450, QUOTA_REPLY)))))
                .isEqualTo(MailFailureKind.QUOTA_EXCEEDED);
    }

    @Test
    void shouldTreatWhatItDoesNotUnderstandAsTransient() {
        assertThat(MailFailureClassifier.classify(new MailSendException("something unfamiliar"))).isEqualTo(MailFailureKind.TRANSIENT);
        assertThat(MailFailureClassifier.classify(new RuntimeException("unexpected"))).isEqualTo(MailFailureKind.TRANSIENT);
    }

    @Test
    void shouldNotLoopOnACyclicCauseChain() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(MailFailureClassifier.classify(first)).isEqualTo(MailFailureKind.TRANSIENT);
    }

    @Test
    void shouldPauseDeliveryOnlyForFailuresThatEveryMailWouldRunInto() {
        assertThat(MailFailureKind.QUOTA_EXCEEDED.pausesDelivery()).isTrue();
        assertThat(MailFailureKind.SERVER_UNAVAILABLE.pausesDelivery()).isTrue();
        assertThat(MailFailureKind.TRANSIENT.pausesDelivery()).isFalse();
        assertThat(MailFailureKind.PERMANENT.pausesDelivery()).isFalse();
    }
}
