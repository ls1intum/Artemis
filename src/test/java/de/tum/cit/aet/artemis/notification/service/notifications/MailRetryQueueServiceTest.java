package de.tum.cit.aet.artemis.notification.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.notification.config.MailDeliveryProperties;
import de.tum.cit.aet.artemis.notification.service.notifications.MailRetryQueueService.Outcome;
import de.tum.cit.aet.artemis.notification.service.notifications.MailRetryQueueService.PendingMail;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MailRetryQueueServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private MailDeliveryProperties properties;

    private SimpleMeterRegistry meterRegistry;

    private MailRetryQueueService queue;

    @BeforeEach
    void setUp() {
        properties = new MailDeliveryProperties();
        properties.getRetry().setMaxQueueSize(3);
        meterRegistry = new SimpleMeterRegistry();
        queue = new MailRetryQueueService(properties, meterRegistry);
    }

    private PendingMail mail(String login, MailPriority priority) {
        return queue.newPendingMail(login + "@example.org", "subject of " + login, "body of " + login, false, true, priority, NOW);
    }

    private boolean offer(PendingMail mail) {
        return queue.offer(mail, NOW);
    }

    private double counted(Outcome outcome) {
        return meterRegistry.get("artemis.mail.outcome").tag("outcome", outcome.name().toLowerCase(Locale.ROOT)).counter().count();
    }

    @Test
    void shouldReturnTransactionalMailsBeforeBulkMailsThatWereQueuedEarlier() {
        offer(mail("bulk1", MailPriority.BULK));
        offer(mail("bulk2", MailPriority.BULK));
        offer(mail("reset", MailPriority.TRANSACTIONAL));

        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).isEqualTo("reset@example.org");
        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).isEqualTo("bulk1@example.org");
        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).isEqualTo("bulk2@example.org");
        assertThat(queue.pollDue(NOW)).isEmpty();
    }

    @Test
    void shouldKeepTheOrderInWhichMailsOfOnePriorityWereQueued() {
        offer(mail("first", MailPriority.BULK));
        offer(mail("second", MailPriority.BULK));

        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).isEqualTo("first@example.org");
        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).isEqualTo("second@example.org");
    }

    @Test
    void shouldNotReturnAMailBeforeItsTimeButSkipToTheNextOneThatIsDue() {
        var later = mail("later", MailPriority.BULK).afterFailedAttempt(NOW.plusSeconds(60));
        offer(later);
        offer(mail("due", MailPriority.BULK));

        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).isEqualTo("due@example.org");
        assertThat(queue.pollDue(NOW)).isEmpty();
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.pollDue(NOW.plusSeconds(60))).get().extracting(PendingMail::recipientEmail).isEqualTo("later@example.org");
    }

    @Test
    void shouldDropANewBulkMailWhenTheQueueIsFull() {
        offer(mail("bulk1", MailPriority.BULK));
        offer(mail("bulk2", MailPriority.BULK));
        offer(mail("bulk3", MailPriority.BULK));

        assertThat(offer(mail("bulk4", MailPriority.BULK))).isFalse();

        assertThat(queue.size()).isEqualTo(3);
        assertThat(counted(Outcome.DROPPED_QUEUE_FULL)).isEqualTo(1);
    }

    @Test
    void shouldMakeRoomForATransactionalMailByEvictingTheOldestBulkMail() {
        offer(mail("bulk1", MailPriority.BULK));
        offer(mail("bulk2", MailPriority.BULK));
        offer(mail("bulk3", MailPriority.BULK));

        assertThat(offer(mail("reset", MailPriority.TRANSACTIONAL))).isTrue();

        assertThat(queue.size()).isEqualTo(3);
        assertThat(counted(Outcome.DROPPED_QUEUE_FULL)).isEqualTo(1);
        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).isEqualTo("reset@example.org");
        assertThat(queue.pollDue(NOW)).get().extracting(PendingMail::recipientEmail).as("bulk1 was evicted").isEqualTo("bulk2@example.org");
    }

    @Test
    void shouldDropATransactionalMailWhenTheQueueHoldsOnlyTransactionalMails() {
        offer(mail("reset1", MailPriority.TRANSACTIONAL));
        offer(mail("reset2", MailPriority.TRANSACTIONAL));
        offer(mail("reset3", MailPriority.TRANSACTIONAL));

        assertThat(offer(mail("reset4", MailPriority.TRANSACTIONAL))).isFalse();
        assertThat(queue.size()).isEqualTo(3);
    }

    @Test
    void shouldPurgeExpiredMailsBeforeDecidingThatTheQueueIsFull() {
        offer(mail("old1", MailPriority.BULK));
        offer(mail("old2", MailPriority.BULK));
        offer(mail("old3", MailPriority.TRANSACTIONAL));
        // The node is paused, so nothing is worked off, and all three mails are past the age that their priority allows.
        queue.pauseAfter(MailFailureKind.QUOTA_EXCEEDED, NOW);
        Instant later = NOW.plus(properties.getRetry().getBulkMaxAge()).plusSeconds(1);
        var fresh = queue.newPendingMail("fresh@example.org", "subject of fresh", "body of fresh", false, true, MailPriority.BULK, later);

        assertThat(queue.offer(fresh, later)).as("a fresh mail is not dropped because of mails that can no longer be sent").isTrue();

        assertThat(queue.size()).isEqualTo(1);
        assertThat(counted(Outcome.EXPIRED)).isEqualTo(3);
        assertThat(counted(Outcome.DROPPED_QUEUE_FULL)).isZero();
        assertThat(queue.pollDue(later)).get().extracting(PendingMail::recipientEmail).isEqualTo("fresh@example.org");
    }

    @Test
    void shouldNotEvictAMailThatCanStillBeSentWhileExpiredMailsTakeRoom() {
        offer(mail("expiring", MailPriority.TRANSACTIONAL));
        offer(mail("bulk1", MailPriority.BULK));
        offer(mail("bulk2", MailPriority.BULK));
        // Past the age of a transactional mail, before the age of a bulk mail.
        Instant later = NOW.plus(properties.getRetry().getTransactionalMaxAge()).plusSeconds(1);
        var reset = queue.newPendingMail("reset@example.org", "subject of reset", "body of reset", false, true, MailPriority.TRANSACTIONAL, later);

        assertThat(queue.offer(reset, later)).isTrue();

        assertThat(counted(Outcome.EXPIRED)).as("the expired mail made room").isEqualTo(1);
        assertThat(counted(Outcome.DROPPED_QUEUE_FULL)).as("so the oldest bulk mail did not have to go").isZero();
        assertThat(queue.size()).isEqualTo(3);
    }

    @Test
    void shouldRemoveOnlyTheExpiredMailsWhenPurging() {
        offer(mail("reset", MailPriority.TRANSACTIONAL));
        offer(mail("announcement", MailPriority.BULK));

        assertThat(queue.purgeExpired(NOW.plus(properties.getRetry().getTransactionalMaxAge()).plusSeconds(1))).isEqualTo(1);

        assertThat(queue.size()).isEqualTo(1);
        assertThat(counted(Outcome.EXPIRED)).isEqualTo(1);
        assertThat(queue.pollDue(NOW.plus(properties.getRetry().getTransactionalMaxAge()).plusSeconds(1))).get().extracting(PendingMail::recipientEmail)
                .isEqualTo("announcement@example.org");
        assertThat(queue.purgeExpired(NOW)).as("nothing is purged twice").isZero();
    }

    @Test
    void shouldLetAMailExpireAccordingToItsPriority() {
        var transactional = mail("reset", MailPriority.TRANSACTIONAL);
        var bulk = mail("announcement", MailPriority.BULK);

        assertThat(transactional.isExpired(NOW.plus(properties.getRetry().getTransactionalMaxAge()).minusSeconds(1))).isFalse();
        assertThat(transactional.isExpired(NOW.plus(properties.getRetry().getTransactionalMaxAge()))).isTrue();
        assertThat(bulk.isExpired(NOW.plus(properties.getRetry().getTransactionalMaxAge()))).as("bulk mail is worth more patience").isFalse();
        assertThat(bulk.isExpired(NOW.plus(properties.getRetry().getBulkMaxAge()))).isTrue();
    }

    @Test
    void shouldDoubleTheWaitWithEveryFailedAttemptUpToTheMaximum() {
        properties.getRetry().setInitialDelay(Duration.ofMinutes(1));
        properties.getRetry().setMaxDelay(Duration.ofMinutes(10));

        assertThat(queue.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(queue.backoff(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(queue.backoff(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(queue.backoff(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(queue.backoff(5)).as("capped").isEqualTo(Duration.ofMinutes(10));
        assertThat(queue.backoff(60)).as("and stays capped without overflowing").isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldScheduleAMailsOwnFailureWithABackoffAndLeaveSendingRunning() {
        Instant next = queue.scheduleAfterFailure(MailFailureKind.TRANSIENT, 2, NOW);

        assertThat(next).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(queue.isPaused(NOW)).isFalse();
    }

    @Test
    void shouldPauseTheNodeAfterAQuotaFailureAndMakeTheMailDueWhenThePauseEnds() {
        Instant next = queue.scheduleAfterFailure(MailFailureKind.QUOTA_EXCEEDED, 1, NOW);

        assertThat(next).isEqualTo(NOW.plus(properties.getRetry().getQuotaPause()));
        assertThat(queue.isPaused(NOW)).isTrue();
        assertThat(queue.isPaused(next.minusSeconds(1))).isTrue();
        assertThat(queue.isPaused(next)).isFalse();
    }

    @Test
    void shouldPauseForTheShorterTimeWhenTheServerIsUnreachable() {
        Instant next = queue.scheduleAfterFailure(MailFailureKind.SERVER_UNAVAILABLE, 1, NOW);

        assertThat(next).isEqualTo(NOW.plus(properties.getRetry().getUnavailablePause()));
    }

    @Test
    void shouldNeverShortenAPauseThatIsAlreadyRunning() {
        Instant quotaPauseEnd = queue.pauseAfter(MailFailureKind.QUOTA_EXCEEDED, NOW);

        Instant afterUnreachable = queue.pauseAfter(MailFailureKind.SERVER_UNAVAILABLE, NOW.plusSeconds(1));

        assertThat(afterUnreachable).isEqualTo(quotaPauseEnd);
        assertThat(queue.isPaused(quotaPauseEnd.minusSeconds(1))).isTrue();
    }

    @Test
    void shouldCountWhatBecameOfTheMailsAndReportTheQueueAndThePause() {
        queue.record(Outcome.SENT);
        queue.record(Outcome.SENT);
        queue.record(Outcome.SUPPRESSED);
        offer(mail("reset", MailPriority.TRANSACTIONAL));
        offer(mail("announcement", MailPriority.BULK));
        offer(mail("announcement2", MailPriority.BULK));

        assertThat(counted(Outcome.SENT)).isEqualTo(2);
        assertThat(counted(Outcome.SUPPRESSED)).isEqualTo(1);
        assertThat(counted(Outcome.REJECTED)).isZero();
        assertThat(meterRegistry.get("artemis.mail.retry.queue.size").tag("priority", "transactional").gauge().value()).isEqualTo(1);
        assertThat(meterRegistry.get("artemis.mail.retry.queue.size").tag("priority", "bulk").gauge().value()).isEqualTo(2);
        assertThat(meterRegistry.get("artemis.mail.delivery.paused").gauge().value()).isZero();
    }

    @Test
    void shouldNotWriteTheBodyOfAMailIntoALog() {
        var text = mail("someone", MailPriority.BULK).toString();

        assertThat(text).contains("someone").contains("subject of someone").doesNotContain("body of someone");
    }

    @Test
    void shouldCountAFailedAttemptOnTheCopyOnly() {
        var original = mail("someone", MailPriority.BULK);

        var copy = original.afterFailedAttempt(NOW.plusSeconds(5));

        assertThat(original.attempts()).isZero();
        assertThat(copy.attempts()).isEqualTo(1);
        assertThat(copy.nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(copy.expiresAt()).as("the age is counted from the first attempt, not from the last").isEqualTo(original.expiresAt());
    }
}
