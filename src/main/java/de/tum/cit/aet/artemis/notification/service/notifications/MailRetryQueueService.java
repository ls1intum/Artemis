package de.tum.cit.aet.artemis.notification.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PreDestroy;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.notification.config.MailDeliveryProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Holds the mails that the SMTP server did not accept, in the memory of this node, until the node tries them again.
 * <p>
 * Without it a mail that hits a temporary refusal, above all an exhausted daily quota, was logged and lost: nobody learned
 * that an announcement or a password reset had not arrived. The queue only keeps and orders the mails and remembers that
 * the server asked the node to stop sending for a while; {@link MailSendingService} decides when to send.
 * <p>
 * The queue is deliberately behind a small interface of its own so that a store that survives a restart can replace the
 * memory later without touching the senders. It is bounded, because every entry holds a rendered body.
 * <p>
 * The same class counts what happened to the mails ({@link Outcome}), because "was the mail sent" has no other answer than
 * the sum of these counts: the SMTP server confirms that it accepted a message and nothing more.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MailRetryQueueService {

    private static final Logger log = LoggerFactory.getLogger(MailRetryQueueService.class);

    /**
     * What became of a mail, as counted by the meter {@code artemis.mail.outcome}.
     */
    public enum Outcome {

        /**
         * The SMTP server accepted the mail at the first attempt.
         */
        SENT,
        /**
         * The SMTP server accepted the mail at a later attempt, after it had been queued.
         */
        SENT_AFTER_RETRY,
        /**
         * The SMTP server did not accept the mail for a temporary reason, so it waits in the queue.
         */
        QUEUED_FOR_RETRY,
        /**
         * The mail was not delivered and is not tried again: the SMTP server refused it for good, it could not be built, or the
         * caller does not allow it to wait.
         */
        REJECTED,
        /**
         * The mail waited longer than its priority allows and was dropped without a further attempt.
         */
        EXPIRED,
        /**
         * The queue was full, so the mail was dropped.
         */
        DROPPED_QUEUE_FULL,
        /**
         * The recipient is a test account that has not opted in to e-mail, so nothing was sent.
         */
        SUPPRESSED
    }

    /**
     * A mail waiting for another attempt. It holds the rendered message, so that nothing has to be rebuilt, and the
     * attempts it already had.
     *
     * @param recipientEmail the address to send to
     * @param recipientLogin the login of the recipient, for logs and for the test-account check
     * @param subject        the rendered subject
     * @param content        the rendered body
     * @param multipart      whether the message is multipart
     * @param html           whether the body is HTML
     * @param priority       how urgent the mail is
     * @param expiresAt      when the mail stops being worth sending
     * @param attempts       how many attempts to send it failed so far
     * @param nextAttemptAt  the earliest time of the next attempt
     */
    public record PendingMail(String recipientEmail, @Nullable String recipientLogin, String subject, String content, boolean multipart, boolean html, MailPriority priority,
            Instant expiresAt, int attempts, Instant nextAttemptAt) {

        /**
         * The same mail after one more failed attempt.
         *
         * @param nextAttempt the earliest time of the next attempt
         * @return a copy that counts the failed attempt
         */
        public PendingMail afterFailedAttempt(Instant nextAttempt) {
            return new PendingMail(recipientEmail, recipientLogin, subject, content, multipart, html, priority, expiresAt, attempts + 1, nextAttempt);
        }

        /**
         * @param now the current time
         * @return whether the mail waited longer than its priority allows
         */
        public boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }

        /**
         * Leaves the rendered body out, so that a mail that ends up in a log does not write its content there.
         */
        @Override
        public String toString() {
            return "PendingMail[recipientLogin=" + recipientLogin + ", subject=" + subject + ", priority=" + priority + ", attempts=" + attempts + ", nextAttemptAt="
                    + nextAttemptAt + ']';
        }
    }

    private final MailDeliveryProperties.Retry retryProperties;

    private final Object lock = new Object();

    private final Deque<PendingMail> transactionalMails = new ArrayDeque<>();

    private final Deque<PendingMail> bulkMails = new ArrayDeque<>();

    private final Map<Outcome, Counter> outcomeCounters = new EnumMap<>(Outcome.class);

    private volatile Instant pausedUntil = Instant.EPOCH;

    public MailRetryQueueService(MailDeliveryProperties properties, MeterRegistry meterRegistry) {
        this.retryProperties = properties.getRetry();
        for (Outcome outcome : Outcome.values()) {
            outcomeCounters.put(outcome, Counter.builder("artemis.mail.outcome").description("What became of the mails Artemis tried to send")
                    .tag("outcome", outcome.name().toLowerCase(Locale.ROOT)).register(meterRegistry));
        }
        for (MailPriority priority : MailPriority.values()) {
            Gauge.builder("artemis.mail.retry.queue.size", this, queue -> queue.size(priority)).description("Mails waiting for another attempt to send them")
                    .tag("priority", priority.name().toLowerCase(Locale.ROOT)).register(meterRegistry);
        }
        Gauge.builder("artemis.mail.delivery.paused", this, queue -> queue.isPaused(Instant.now()) ? 1 : 0)
                .description("1 while this node does not contact the SMTP server because it reported a quota or was unreachable").register(meterRegistry);
    }

    /**
     * @return whether mails that the SMTP server did not accept are kept for another attempt
     */
    public boolean isEnabled() {
        return retryProperties.isEnabled();
    }

    /**
     * @return the most mails the drain job sends in one run
     */
    public int drainBatchSize() {
        return retryProperties.getDrainBatchSize();
    }

    /**
     * Counts what became of a mail.
     *
     * @param outcome what happened
     */
    public void record(Outcome outcome) {
        outcomeCounters.get(outcome).increment();
    }

    /**
     * Builds the entry for a mail that was just rejected for the first time.
     *
     * @param recipientEmail the address to send to
     * @param recipientLogin the login of the recipient
     * @param subject        the rendered subject
     * @param content        the rendered body
     * @param multipart      whether the message is multipart
     * @param html           whether the body is HTML
     * @param priority       how urgent the mail is
     * @param now            the current time
     * @return the mail, due immediately and expiring according to its priority
     */
    public PendingMail newPendingMail(String recipientEmail, @Nullable String recipientLogin, String subject, String content, boolean multipart, boolean html,
            MailPriority priority, Instant now) {
        Duration maxAge = priority == MailPriority.TRANSACTIONAL ? retryProperties.getTransactionalMaxAge() : retryProperties.getBulkMaxAge();
        return new PendingMail(recipientEmail, recipientLogin, subject, content, multipart, html, priority, now.plus(maxAge), 0, now);
    }

    /**
     * Queues a mail for another attempt. A full queue drops the mail, except that a transactional mail takes the place of the
     * oldest bulk mail: a password reset must not wait behind or be lost to a backlog of announcements.
     *
     * @param mail the mail to keep
     * @return whether the mail was queued
     */
    public boolean offer(PendingMail mail) {
        synchronized (lock) {
            if (transactionalMails.size() + bulkMails.size() >= retryProperties.getMaxQueueSize()) {
                if (mail.priority() == MailPriority.TRANSACTIONAL && !bulkMails.isEmpty()) {
                    PendingMail evicted = bulkMails.pollFirst();
                    record(Outcome.DROPPED_QUEUE_FULL);
                    log.warn("Mail queue is full, dropped the oldest bulk mail {} to make room for {}", evicted, mail);
                }
                else {
                    record(Outcome.DROPPED_QUEUE_FULL);
                    log.error("Mail queue is full ({} mails), dropped {}", retryProperties.getMaxQueueSize(), mail);
                    return false;
                }
            }
            (mail.priority() == MailPriority.TRANSACTIONAL ? transactionalMails : bulkMails).addLast(mail);
        }
        return true;
    }

    /**
     * Removes and returns the next mail whose time has come. Transactional mails come first.
     *
     * @param now the current time
     * @return the mail, or empty if none is due
     */
    public Optional<PendingMail> pollDue(Instant now) {
        synchronized (lock) {
            Optional<PendingMail> due = removeFirstDue(transactionalMails, now);
            return due.isPresent() ? due : removeFirstDue(bulkMails, now);
        }
    }

    private static Optional<PendingMail> removeFirstDue(Deque<PendingMail> mails, Instant now) {
        Iterator<PendingMail> iterator = mails.iterator();
        while (iterator.hasNext()) {
            PendingMail mail = iterator.next();
            if (!mail.nextAttemptAt().isAfter(now)) {
                iterator.remove();
                return Optional.of(mail);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the number of mails waiting for another attempt
     */
    public int size() {
        synchronized (lock) {
            return transactionalMails.size() + bulkMails.size();
        }
    }

    private int size(MailPriority priority) {
        synchronized (lock) {
            return (priority == MailPriority.TRANSACTIONAL ? transactionalMails : bulkMails).size();
        }
    }

    /**
     * The wait before the next attempt of a mail that failed for a reason of its own. It starts at the configured initial
     * delay and doubles with every failed attempt up to the configured maximum.
     *
     * @param failedAttempts how many attempts failed so far, at least one
     * @return how long to wait
     */
    Duration backoff(int failedAttempts) {
        Duration maxDelay = retryProperties.getMaxDelay();
        Duration delay = retryProperties.getInitialDelay();
        for (int i = 1; i < failedAttempts && delay.compareTo(maxDelay) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    /**
     * Stops this node from contacting the SMTP server for a while, because the server reported a failure that every mail
     * would run into. A pause never gets shorter by a later, shorter one.
     *
     * @param kind the failure that makes the node stop
     * @param now  the current time
     * @return when sending may be tried again
     */
    Instant pauseAfter(MailFailureKind kind, Instant now) {
        Duration pause = kind == MailFailureKind.QUOTA_EXCEEDED ? retryProperties.getQuotaPause() : retryProperties.getUnavailablePause();
        Instant until = now.plus(pause);
        synchronized (lock) {
            if (until.isAfter(pausedUntil)) {
                pausedUntil = until;
            }
            return pausedUntil;
        }
    }

    /**
     * @param now the current time
     * @return whether this node currently does not contact the SMTP server
     */
    public boolean isPaused(Instant now) {
        return now.isBefore(pausedUntil);
    }

    /**
     * Decides when a mail whose attempt just failed is tried again. A failure that every mail would run into pauses the whole
     * node, and the mail is due when the pause ends, so that the first attempt after the pause is not delayed by a backoff
     * the mail collected before. Any other failure is the mail's own and delays only that mail, by a backoff that grows with
     * every failed attempt.
     *
     * @param failure        what the failed attempt says about trying again
     * @param failedAttempts how many attempts of this mail failed so far, including the one that just failed
     * @param now            the current time
     * @return the earliest time of the next attempt
     */
    public Instant scheduleAfterFailure(MailFailureKind failure, int failedAttempts, Instant now) {
        return failure.pausesDelivery() ? pauseAfter(failure, now) : now.plus(backoff(failedAttempts));
    }

    /**
     * Drops what is still queued when the node shuts down. A queue in memory cannot outlive the node, which is why the
     * number of lost mails is worth a line in the log.
     */
    @PreDestroy
    void logMailsLostOnShutdown() {
        int lost = size();
        if (lost > 0) {
            log.warn("Shutting down with {} e-mails that could not be delivered yet. They are lost.", lost);
        }
    }
}
