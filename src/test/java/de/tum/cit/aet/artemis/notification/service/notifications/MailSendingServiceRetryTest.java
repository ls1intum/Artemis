package de.tum.cit.aet.artemis.notification.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.notification.config.MailDeliveryProperties;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.TestAccountEmailService;
import de.tum.cit.aet.artemis.notification.service.notifications.MailRetryQueueService.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * What happens to a mail that the SMTP server does not accept: it is kept for another attempt, ordered by urgency, paced so
 * that a backlog does not use up the quota again, and dropped when it is no longer worth sending. The mail sender is a mock
 * that throws the exceptions the real one throws, including the reply of the LRZ that took production's mail down on
 * 2026-10-07.
 */
class MailSendingServiceRetryTest {

    private static final String QUOTA_REPLY = "450 4.7.0 <DATA>: Data command rejected: Daily mail quota exceeded";

    private MailDeliveryProperties properties;

    private SimpleMeterRegistry meterRegistry;

    private MailRetryQueueService retryQueue;

    private JavaMailSender javaMailSender;

    private TestAccountEmailService testAccountEmailPolicy;

    private MailSendingService mailSendingService;

    @BeforeEach
    void setUp() throws Exception {
        properties = new MailDeliveryProperties();
        meterRegistry = new SimpleMeterRegistry();
        retryQueue = new MailRetryQueueService(properties, meterRegistry);

        javaMailSender = mock(JavaMailSender.class);
        // A new message per call, so that the subject of every message that was sent can be read afterwards.
        when(javaMailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));

        testAccountEmailPolicy = mock(TestAccountEmailService.class);

        var artemisProperties = new ArtemisProperties();
        artemisProperties.getMail().setFrom("artemis@xcit.test");

        var messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Subject of the mail");
        var templateEngine = mock(SpringTemplateEngine.class);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("Body of the mail");

        mailSendingService = new MailSendingService(artemisProperties, javaMailSender, messageSource, templateEngine, retryQueue, testAccountEmailPolicy);
        ReflectionTestUtils.setField(mailSendingService, "artemisServerUrl", URI.create("http://localhost:9000").toURL());
    }

    private static MailRecipientDTO recipient(String login) {
        return new MailRecipientDTO(login + "@example.org", "en", login, "First", "Last");
    }

    private static MailRecipientDTO testUser(String login) {
        return new MailRecipientDTO(login + "@example.org", "en", login, "First", "Last", null, null, true);
    }

    private static MailSendException quotaFailure() {
        return new MailSendException(Map.of(new Object(), new SMTPSendFailedException("DATA", 450, QUOTA_REPLY, null, null, null, null)));
    }

    private static MailSendException smtpFailure(int replyCode, String reply) {
        return new MailSendException(Map.of(new Object(), new SMTPSendFailedException("DATA", replyCode, reply, null, null, null, null)));
    }

    private double counted(Outcome outcome) {
        return meterRegistry.get("artemis.mail.outcome").tag("outcome", outcome.name().toLowerCase(Locale.ROOT)).counter().count();
    }

    /**
     * The subjects of the messages that were handed to the mail sender, in the order they were handed over.
     */
    private List<String> sentSubjects() {
        var messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeast(0)).send(messages.capture());
        return messages.getAllValues().stream().map(message -> {
            try {
                return message.getSubject();
            }
            catch (MessagingException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    @Test
    void shouldSendAMailTheServerAcceptsAndKeepNothing() {
        boolean accepted = mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        assertThat(accepted).isTrue();
        verify(javaMailSender).send(any(MimeMessage.class));
        assertThat(retryQueue.size()).isZero();
        assertThat(counted(Outcome.SENT)).isEqualTo(1);
    }

    @Test
    void shouldKeepAMailAndPauseTheNodeWhenTheQuotaIsExhausted() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));

        boolean accepted = mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        assertThat(accepted).as("a mail that waits for another attempt is accepted for delivery").isTrue();
        assertThat(retryQueue.size()).isEqualTo(1);
        assertThat(retryQueue.isPaused(Instant.now())).isTrue();
        assertThat(counted(Outcome.QUEUED_FOR_RETRY)).isEqualTo(1);
        assertThat(counted(Outcome.SENT)).isZero();
    }

    @Test
    void shouldNotContactTheServerForMailsThatArriveWhilePaused() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "First", "Body", false, true, MailPriority.TRANSACTIONAL);

        // These are the announcements of a whole course: every one of them would only collect the same refusal.
        for (int i = 2; i <= 50; i++) {
            assertThat(mailSendingService.sendEmailSync(recipient("student" + i), "Mail " + i, "Body", false, true, MailPriority.BULK)).isTrue();
        }

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        assertThat(retryQueue.size()).isEqualTo(50);
    }

    @Test
    void shouldSendTheQueuedMailsOnceThePauseHasEnded() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "First", "Body", false, true, MailPriority.TRANSACTIONAL);
        mailSendingService.sendEmailSync(recipient("student2"), "Second", "Body", false, true, MailPriority.TRANSACTIONAL);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        Instant afterThePause = Instant.now().plus(properties.getRetry().getQuotaPause()).plusSeconds(1);
        mailSendingService.sendQueuedMails(afterThePause);

        assertThat(retryQueue.size()).isZero();
        assertThat(counted(Outcome.SENT_AFTER_RETRY)).isEqualTo(2);
        // The first entry is the attempt that met the quota; the second mail was queued without an attempt, because the node was paused.
        assertThat(sentSubjects()).containsExactly("First", "First", "Second");
    }

    @Test
    void shouldNotSendAnythingBeforeThePauseHasEnded() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "First", "Body", false, true, MailPriority.TRANSACTIONAL);

        mailSendingService.sendQueuedMails(Instant.now().plus(properties.getRetry().getQuotaPause()).minusSeconds(30));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        assertThat(retryQueue.size()).isEqualTo(1);
    }

    @Test
    void shouldStopTheRunAndPauseAgainWhenTheQuotaIsStillExhausted() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        for (int i = 1; i <= 5; i++) {
            mailSendingService.sendEmailSync(recipient("student" + i), "Mail " + i, "Body", false, true, MailPriority.TRANSACTIONAL);
        }
        Instant afterThePause = Instant.now().plus(properties.getRetry().getQuotaPause()).plusSeconds(1);

        mailSendingService.sendQueuedMails(afterThePause);

        // One probe, which failed. The other four were not tried, and all five are still waiting.
        verify(javaMailSender, times(2)).send(any(MimeMessage.class));
        assertThat(retryQueue.size()).isEqualTo(5);
        assertThat(retryQueue.isPaused(afterThePause)).isTrue();
    }

    @Test
    void shouldSendAtMostOneBatchPerRun() {
        properties.getRetry().setDrainBatchSize(2);
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        for (int i = 1; i <= 5; i++) {
            mailSendingService.sendEmailSync(recipient("student" + i), "Mail " + i, "Body", false, true, MailPriority.TRANSACTIONAL);
        }
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        mailSendingService.sendQueuedMails(Instant.now().plus(properties.getRetry().getQuotaPause()).plusSeconds(1));

        assertThat(counted(Outcome.SENT_AFTER_RETRY)).isEqualTo(2);
        assertThat(retryQueue.size()).isEqualTo(3);
    }

    @Test
    void shouldSendTransactionalMailsBeforeBulkMailsThatWereQueuedEarlier() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "Announcement 1", "Body", false, true, MailPriority.BULK);
        mailSendingService.sendEmailSync(recipient("student2"), "Announcement 2", "Body", false, true, MailPriority.BULK);
        mailSendingService.sendEmailSync(recipient("student3"), "Password reset", "Body", false, true, MailPriority.TRANSACTIONAL);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        mailSendingService.sendQueuedMails(Instant.now().plus(properties.getRetry().getQuotaPause()).plusSeconds(1));

        // The first entry is the attempt that met the quota, the others were queued while the node was paused. What follows is the order in which the queue was worked off.
        assertThat(sentSubjects().subList(1, 4)).containsExactly("Password reset", "Announcement 1", "Announcement 2");
    }

    @Test
    void shouldNotKeepAMailTheServerRefusedForGood() {
        doThrow(smtpFailure(550, "550 5.1.1 The email account that you tried to reach does not exist")).when(javaMailSender).send(any(MimeMessage.class));

        boolean accepted = mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        assertThat(accepted).isFalse();
        assertThat(retryQueue.size()).isZero();
        assertThat(retryQueue.isPaused(Instant.now())).as("one address that does not exist is no reason to stop sending").isFalse();
        assertThat(counted(Outcome.REJECTED)).isEqualTo(1);
    }

    @Test
    void shouldRetryAMailThatFailedForItsOwnReasonAfterABackoffWithoutPausingTheNode() {
        doThrow(smtpFailure(451, "451 4.3.0 Local error in processing")).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);
        assertThat(retryQueue.isPaused(Instant.now())).isFalse();
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        mailSendingService.sendQueuedMails(Instant.now().plusSeconds(30));
        assertThat(retryQueue.size()).as("the first backoff is a minute").isEqualTo(1);

        mailSendingService.sendQueuedMails(Instant.now().plusSeconds(90));
        assertThat(retryQueue.size()).isZero();
        assertThat(counted(Outcome.SENT_AFTER_RETRY)).isEqualTo(1);
    }

    @Test
    void shouldDoubleTheBackoffWhenTheRetryFailsAgain() {
        doThrow(smtpFailure(451, "451 4.3.0 Local error in processing")).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        Instant firstRetry = Instant.now().plusSeconds(90);
        mailSendingService.sendQueuedMails(firstRetry);
        verify(javaMailSender, times(2)).send(any(MimeMessage.class));
        assertThat(retryQueue.size()).isEqualTo(1);

        // The second wait is two minutes, so a minute later nothing is due yet...
        mailSendingService.sendQueuedMails(firstRetry.plusSeconds(60));
        verify(javaMailSender, times(2)).send(any(MimeMessage.class));

        // ...and after two minutes it is.
        mailSendingService.sendQueuedMails(firstRetry.plusSeconds(130));
        verify(javaMailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void shouldDropARetryThatTheServerNowRefusesForGood() {
        doThrow(smtpFailure(451, "451 4.3.0 Local error in processing")).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);
        doThrow(smtpFailure(550, "550 5.1.1 No such user")).when(javaMailSender).send(any(MimeMessage.class));

        mailSendingService.sendQueuedMails(Instant.now().plusSeconds(90));

        assertThat(retryQueue.size()).isZero();
        assertThat(counted(Outcome.REJECTED)).isEqualTo(1);
    }

    @Test
    void shouldDropAMailThatIsTooOldInsteadOfSendingIt() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "Password reset", "Body", false, true, MailPriority.TRANSACTIONAL);
        mailSendingService.sendEmailSync(recipient("student2"), "Announcement", "Body", false, true, MailPriority.BULK);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        // Past the age of a transactional mail, before the age of a bulk mail: a password reset link of yesterday is worthless, the announcement is not.
        Instant later = Instant.now().plus(properties.getRetry().getTransactionalMaxAge()).plusSeconds(60);
        mailSendingService.sendQueuedMails(later);

        assertThat(counted(Outcome.EXPIRED)).isEqualTo(1);
        assertThat(counted(Outcome.SENT_AFTER_RETRY)).isEqualTo(1);
        assertThat(sentSubjects()).as("the attempt that met the quota, then the announcement; the expired password reset was not sent again").containsExactly("Password reset",
                "Announcement");
    }

    @Test
    void shouldPauseTheNodeForAShorterTimeWhenTheServerRefusesTheCredentials() {
        doThrow(new MailAuthenticationException("Authentication failed")).when(javaMailSender).send(any(MimeMessage.class));

        mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        assertThat(retryQueue.size()).isEqualTo(1);
        assertThat(retryQueue.isPaused(Instant.now())).isTrue();
        assertThat(retryQueue.isPaused(Instant.now().plus(properties.getRetry().getUnavailablePause()).plusSeconds(5))).isFalse();
    }

    @Test
    void shouldNeverQueueTheMailOfAFlowThatNeedsToKnowWhetherItWasDelivered() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));

        boolean delivered = mailSendingService.buildAndSendSyncReporting(recipient("student1"), "email.key", List.of(), "mail/template", Map.of());

        // A queued mail is not a delivered one: the caller would advance a "warned" state for a warning nobody received.
        assertThat(delivered).isFalse();
        assertThat(retryQueue.size()).isZero();
        assertThat(counted(Outcome.REJECTED)).isEqualTo(1);
    }

    @Test
    void shouldNeverQueueTheMailOfAnAdministratorWhoAsksWhetherItWasSent() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));

        boolean sent = mailSendingService.sendEmailSync(recipient("student1"), "Digest", "Body", false, true);

        assertThat(sent).isFalse();
        assertThat(retryQueue.size()).isZero();
    }

    @Test
    void shouldNotSendAMailTwiceWhenOnlyClosingTheConnectionFailedAfterTheServerAcceptedIt() {
        // Spring reports a failed close after a successful send as a MailSendException without any failed message.
        doThrow(new MailSendException("Failed to close server connection after message sending", new SocketException("Broken pipe"))).when(javaMailSender)
                .send(any(MimeMessage.class));

        boolean accepted = mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        assertThat(accepted).isTrue();
        assertThat(retryQueue.size()).isZero();
        assertThat(retryQueue.isPaused(Instant.now())).isFalse();
        assertThat(counted(Outcome.SENT)).isEqualTo(1);
    }

    @Test
    void shouldAttemptTheMailOfAReportingFlowEvenWhileTheNodeIsPaused() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));
        mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        boolean delivered = mailSendingService.buildAndSendSyncReporting(recipient("student2"), "email.key", List.of(), "mail/template", Map.of());

        // The flow measures whether mail reaches people, so it has to ask the server instead of assuming the answer.
        assertThat(delivered).isTrue();
        verify(javaMailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void shouldQueueTheMailOfAnAsynchronousBuildAndSend() {
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));

        mailSendingService.buildAndSendAsync(recipient("student1"), "email.key", "mail/template", Map.of());

        assertThat(retryQueue.size()).isEqualTo(1);
    }

    @Test
    void shouldNotKeepAnythingWhenRetryIsDisabled() {
        properties.getRetry().setEnabled(false);
        doThrow(quotaFailure()).when(javaMailSender).send(any(MimeMessage.class));

        boolean accepted = mailSendingService.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        assertThat(accepted).isFalse();
        assertThat(retryQueue.size()).isZero();
        assertThat(retryQueue.isPaused(Instant.now())).isFalse();
        // Nothing drains a disabled queue either, so a run is a no-op.
        mailSendingService.sendQueuedMails(Instant.now().plusSeconds(3600));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotSendToATestUserThatHasNotOptedIn() {
        when(testAccountEmailPolicy.suppressesEmailTo("test_user_1", true)).thenReturn(true);

        boolean accepted = mailSendingService.sendEmailSync(testUser("test_user_1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL);

        assertThat(accepted).isFalse();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(retryQueue.size()).isZero();
        assertThat(counted(Outcome.SUPPRESSED)).isEqualTo(1);
    }

    @Test
    void shouldNotSendAMailThatWasBuiltForATestUserThatHasNotOptedIn() {
        when(testAccountEmailPolicy.suppressesEmailTo("test_user_1", true)).thenReturn(true);

        mailSendingService.buildAndSendAsync(testUser("test_user_1"), "email.key", "mail/template", Map.of());
        boolean reported = mailSendingService.buildAndSendSyncReporting(testUser("test_user_1"), "email.key", List.of(), "mail/template", Map.of());

        assertThat(reported).isFalse();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldSendToATestUserThatHasOptedIn() {
        when(testAccountEmailPolicy.suppressesEmailTo("test_user_1", true)).thenReturn(false);

        assertThat(mailSendingService.sendEmailSync(testUser("test_user_1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL)).isTrue();
        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotTryToSendWhenNoMailServerIsConfigured() {
        var unconfigured = new ArtemisProperties();
        unconfigured.getMail().setFrom("artemis@localhost");
        var service = new MailSendingService(unconfigured, javaMailSender, mock(MessageSource.class), mock(SpringTemplateEngine.class), retryQueue, testAccountEmailPolicy);

        assertThat(service.sendEmailSync(recipient("student1"), "Subject", "Body", false, true, MailPriority.TRANSACTIONAL)).isFalse();
        service.sendQueuedMails(Instant.now());

        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(retryQueue.size()).isZero();
    }

    @Test
    void shouldFinishARunOfAnEmptyQueueWithoutContactingTheServer() {
        mailSendingService.sendQueuedMails(Instant.now().plus(Duration.ofDays(1)));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }
}
