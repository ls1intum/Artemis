package de.tum.cit.aet.artemis.notification.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import javax.net.ssl.SSLException;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.MailPreparationException;

class MailFailureBoundaryTest {

    @ParameterizedTest
    @CsvSource({ "399, TRANSIENT, TRANSIENT", "400, QUOTA_EXCEEDED, TRANSIENT", "499, QUOTA_EXCEEDED, TRANSIENT", "500, PERMANENT, PERMANENT", "599, PERMANENT, PERMANENT",
            "600, TRANSIENT, TRANSIENT", "421, SERVER_UNAVAILABLE, TRANSIENT" })
    void senderQuotaAndRecipientMailboxFailuresHaveDifferentScopes(int code, MailFailureKind senderKind, MailFailureKind recipientKind) throws Exception {
        var address = new InternetAddress("student@example.org");
        assertThat(MailFailureClassifier.classify(new SMTPSenderFailedException(address, "MAIL FROM", code, "Quota exceeded"))).isEqualTo(senderKind);
        assertThat(MailFailureClassifier.classify(new SMTPAddressFailedException(address, "RCPT TO", code, "Quota exceeded"))).isEqualTo(recipientKind);
    }

    @Test
    void nullRepliesAndEmptyInvalidAddressListsRemainRetryable() {
        assertThat(MailFailureClassifier.classify(new SMTPSendFailedException("DATA", 450, null, null, null, null, null))).isEqualTo(MailFailureKind.TRANSIENT);
        assertThat(MailFailureClassifier.classify(new SendFailedException("unknown", null, null, null, new jakarta.mail.Address[0]))).isEqualTo(MailFailureKind.TRANSIENT);
        assertThat(MailFailureClassifier.classify(null)).isEqualTo(MailFailureKind.TRANSIENT);
    }

    @Test
    void classifiesLibraryFailuresWithoutDependingOnASpringWrapper() {
        assertThat(MailFailureClassifier.classify(new AddressException("invalid address"))).isEqualTo(MailFailureKind.PERMANENT);
        assertThat(MailFailureClassifier.classify(new MailPreparationException("invalid template"))).isEqualTo(MailFailureKind.PERMANENT);
        assertThat(MailFailureClassifier.classify(new AuthenticationFailedException("rejected password"))).isEqualTo(MailFailureKind.SERVER_UNAVAILABLE);
        assertThat(MailFailureClassifier.classify(new SSLException("certificate rejected"))).isEqualTo(MailFailureKind.SERVER_UNAVAILABLE);
    }
}
