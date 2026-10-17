package de.tum.cit.aet.artemis.notification.service.notifications;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;

import javax.net.ssl.SSLException;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

/**
 * Decides from the exception of a failed send whether the mail is worth trying again.
 * <p>
 * The reply of an SMTP server carries the answer: a code in the 5xx range is a final refusal and a code in the 4xx range
 * asks the sender to come back later (RFC 5321, section 4.2.1). Spring wraps the exception of the server in a
 * {@link MailSendException} that keeps the exception of every failed message in a map, so both the cause chain and that map
 * have to be read. What is not understood counts as {@link MailFailureKind#TRANSIENT}: a mail that is tried once too often
 * until it expires is a smaller harm than a mail that is dropped because of an unfamiliar message.
 */
public final class MailFailureClassifier {

    /**
     * Phrases with which a server says that a limit of the account is used up. The server of the LRZ answers
     * {@code 450 4.7.0 <DATA>: Data command rejected: Daily mail quota exceeded}.
     */
    private static final List<String> QUOTA_PHRASES = List.of("quota", "rate limit", "limit exceeded", "too many");

    /**
     * The reply code with which an SMTP server announces that it is shutting down or overloaded (RFC 5321, section 4.2.2).
     */
    private static final int SERVICE_NOT_AVAILABLE = 421;

    private MailFailureClassifier() {
    }

    /**
     * Classifies a failure of handing a mail to the SMTP server.
     *
     * @param failure the exception the mail sender threw
     * @return what the failure says about trying again
     */
    public static MailFailureKind classify(Throwable failure) {
        for (Throwable candidate : candidates(failure)) {
            MailFailureKind kind = classifySingle(candidate);
            if (kind != null) {
                return kind;
            }
        }
        return MailFailureKind.TRANSIENT;
    }

    /**
     * The exception itself, its causes, and the exception of every failed message that a {@link MailSendException}
     * carries together with their causes. The most specific exception is the one that holds the reply code, and the
     * wrapping ones come first, so a definite answer further down is found even when the wrapper itself says nothing.
     */
    private static List<Throwable> candidates(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Throwable> result = new ArrayList<>();
        collect(failure, seen, result);
        return result;
    }

    private static void collect(Throwable throwable, Set<Throwable> seen, List<Throwable> result) {
        Throwable current = throwable;
        while (current != null && seen.add(current)) {
            result.add(current);
            if (current instanceof MailSendException mailSendException) {
                for (Exception messageFailure : mailSendException.getFailedMessages().values()) {
                    collect(messageFailure, seen, result);
                }
            }
            current = current.getCause();
        }
    }

    /**
     * @return the kind this exception alone determines, or null if it says nothing about trying again
     */
    private static MailFailureKind classifySingle(Throwable throwable) {
        if (throwable instanceof SMTPSendFailedException smtp) {
            return classifyReply(smtp.getReturnCode(), smtp.getMessage());
        }
        if (throwable instanceof SMTPAddressFailedException smtp) {
            // A refusal of one recipient says something about that address only, whatever its text says about a quota (a full
            // mailbox of someone else), so it must never make this node stop sending.
            return classifyRecipientReply(smtp.getReturnCode());
        }
        if (throwable instanceof SMTPSenderFailedException smtp) {
            return classifyReply(smtp.getReturnCode(), smtp.getMessage());
        }
        if (throwable instanceof SendFailedException sendFailed && sendFailed.getInvalidAddresses() != null && sendFailed.getInvalidAddresses().length > 0) {
            // The server refused an address outright. This is the "Invalid Addresses" that a mailbox which does not exist produces.
            return MailFailureKind.PERMANENT;
        }
        if (throwable instanceof MailParseException || throwable instanceof MailPreparationException || throwable instanceof AddressException) {
            return MailFailureKind.PERMANENT;
        }
        if (throwable instanceof MailAuthenticationException || throwable instanceof AuthenticationFailedException) {
            return MailFailureKind.SERVER_UNAVAILABLE;
        }
        if (throwable instanceof SocketException || throwable instanceof SocketTimeoutException || throwable instanceof UnknownHostException || throwable instanceof SSLException) {
            return MailFailureKind.SERVER_UNAVAILABLE;
        }
        return null;
    }

    private static MailFailureKind classifyReply(int replyCode, String message) {
        if (replyCode >= 500 && replyCode < 600) {
            return MailFailureKind.PERMANENT;
        }
        if (replyCode >= 400 && replyCode < 500) {
            if (replyCode == SERVICE_NOT_AVAILABLE) {
                return MailFailureKind.SERVER_UNAVAILABLE;
            }
            return mentionsQuota(message) ? MailFailureKind.QUOTA_EXCEEDED : MailFailureKind.TRANSIENT;
        }
        return null;
    }

    private static MailFailureKind classifyRecipientReply(int replyCode) {
        if (replyCode >= 500 && replyCode < 600) {
            return MailFailureKind.PERMANENT;
        }
        return replyCode >= 400 && replyCode < 500 ? MailFailureKind.TRANSIENT : null;
    }

    private static boolean mentionsQuota(String message) {
        if (message == null) {
            return false;
        }
        String lowerCaseMessage = message.toLowerCase(Locale.ROOT);
        return QUOTA_PHRASES.stream().anyMatch(lowerCaseMessage::contains);
    }
}
