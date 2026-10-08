package de.tum.cit.aet.artemis.notification.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.TestAccountEmailService;
import de.tum.cit.aet.artemis.notification.service.notifications.MailRetryQueueService.Outcome;
import de.tum.cit.aet.artemis.notification.service.notifications.MailRetryQueueService.PendingMail;

/**
 * Service for sending emails asynchronously.
 * <p>
 * A mail that the SMTP server does not accept for a temporary reason is not lost: it is kept in a {@link MailRetryQueueService}
 * and tried again by a scheduled job, unless the caller needs to know about the delivery right now (see
 * {@link #buildAndSendSyncReporting}). A mail to a test account that has not opted in to e-mail is not sent at all
 * (see {@link TestAccountEmailService}).
 */
@Lazy
@Service
@Profile(PROFILE_CORE)
public class MailSendingService {

    private static final Logger log = LoggerFactory.getLogger(MailSendingService.class);

    /**
     * The default mail sender address used by JHipster when no custom configuration is provided.
     * When this default is detected, email sending is skipped to avoid connection errors in
     * development and E2E test environments where no mail server is configured.
     */
    private static final String DEFAULT_MAIL_FROM = "artemis@localhost";

    /**
     * What became of a request to send one mail.
     */
    private enum Delivery {

        /**
         * The SMTP server accepted the mail.
         */
        SENT,
        /**
         * The SMTP server did not accept the mail for now, so it waits in the queue for another attempt.
         */
        QUEUED,
        /**
         * The recipient is a test account that has not opted in to e-mail.
         */
        SUPPRESSED,
        /**
         * No mail server is configured for this deployment.
         */
        NOT_CONFIGURED,
        /**
         * The mail was not sent and will not be tried again.
         */
        FAILED;

        /**
         * @return whether the mail was delivered or is kept for delivery
         */
        boolean isAcceptedForDelivery() {
            return this == SENT || this == QUEUED;
        }
    }

    /**
     * The result of one attempt to hand a mail to the SMTP server.
     *
     * @param failure   why the attempt failed, or null if the server accepted the mail
     * @param exception the exception behind the failure, or null if there was none
     * @param messageId the id the mail carries, which a bounce message refers to, if it is known
     */
    private record SendAttempt(@Nullable MailFailureKind failure, @Nullable Exception exception, @Nullable String messageId) {

        static SendAttempt accepted(@Nullable String messageId) {
            return new SendAttempt(null, null, messageId);
        }

        static SendAttempt failed(MailFailureKind failure, Exception exception) {
            return new SendAttempt(failure, exception, null);
        }

        boolean isAccepted() {
            return failure == null;
        }
    }

    private final ArtemisProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    private final boolean mailConfigured;

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    private final MailRetryQueueService retryQueue;

    private final TestAccountEmailService testAccountEmailPolicy;

    /**
     * Set while the queue is worked off, so that a run that is still sending when the next one is due is not joined by it.
     */
    private final AtomicBoolean draining = new AtomicBoolean();

    public MailSendingService(ArtemisProperties jHipsterProperties, JavaMailSender javaMailSender, MessageSource messageSource, SpringTemplateEngine templateEngine,
            MailRetryQueueService retryQueue, TestAccountEmailService testAccountEmailPolicy) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.retryQueue = retryQueue;
        this.testAccountEmailPolicy = testAccountEmailPolicy;

        // Check if mail is properly configured (not using the default placeholder)
        String mailFrom = jHipsterProperties.getMail().getFrom();
        this.mailConfigured = mailFrom != null && !mailFrom.isBlank() && !mailFrom.equalsIgnoreCase(DEFAULT_MAIL_FROM);

        if (!this.mailConfigured) {
            log.warn("Email sending is disabled. To enable, configure 'jhipster.mail.from' in application.yml");
        }
    }

    /**
     * Returns whether mail sending is configured.
     *
     * @return {@code true} if mail sending is properly configured, {@code false} otherwise
     */
    public boolean isMailConfigured() {
        return mailConfigured;
    }

    /**
     * Sends an e-mail to the specified recipient asynchronously
     *
     * @param recipient   who should be contacted.
     * @param subject     The mail subject
     * @param content     The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml      Whether the mail should support HTML tags
     */
    @Async("mailTaskExecutor")
    public void sendEmail(MailRecipientDTO recipient, String subject, String content, boolean isMultipart, boolean isHtml) {
        deliver(recipient.email(), recipient.login(), subject, content, isMultipart, isHtml, MailPriority.TRANSACTIONAL, true);
    }

    /**
     * Sends an e-mail to the specified recipient synchronously
     *
     * @param recipient   who should be contacted.
     * @param subject     The mail subject
     * @param content     The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml      Whether the mail should support HTML tags
     * @return true if the SMTP server accepted the mail, false if mail is not configured for this deployment, the recipient is a
     *         test account that did not opt in, or the mail was not accepted. Reported rather than discarded because a caller
     *         that measures whether its channel works has no other way to find out. A mail that is not accepted is never queued
     *         here: a queued mail is not a delivered one.
     */
    public boolean sendEmailSync(MailRecipientDTO recipient, String subject, String content, boolean isMultipart, boolean isHtml) {
        return deliver(recipient.email(), recipient.login(), subject, content, isMultipart, isHtml, MailPriority.TRANSACTIONAL, false) == Delivery.SENT;
    }

    /**
     * Sends an e-mail to the specified recipient synchronously and says how urgent it is, which decides its place if it has
     * to wait in the retry queue.
     *
     * @param recipient   who should be contacted.
     * @param subject     The mail subject
     * @param content     The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml      Whether the mail should support HTML tags
     * @param priority    How urgently the mail has to be delivered
     * @return true if the mail was handed to the transport or is kept to be handed over later, false otherwise
     */
    public boolean sendEmailSync(MailRecipientDTO recipient, String subject, String content, boolean isMultipart, boolean isHtml, MailPriority priority) {
        return deliver(recipient.email(), recipient.login(), subject, content, isMultipart, isHtml, priority, true).isAcceptedForDelivery();
    }

    /**
     * Builds and sends an e-mail to the specified recipient synchronously
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     */
    public void buildAndSendSync(@NonNull MailRecipientDTO recipient, @NonNull String subjectKey, @NonNull String contentTemplate,
            @NonNull Map<String, Object> additionalContextVariables) {
        buildAndSend(recipient, subjectKey, List.of(), contentTemplate, additionalContextVariables, true);
    }

    /**
     * Builds and sends an e-mail to the specified recipient asynchronously
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     */
    @Async("mailTaskExecutor")
    public void buildAndSendAsync(@NonNull MailRecipientDTO recipient, @NonNull String subjectKey, @NonNull String contentTemplate,
            @NonNull Map<String, Object> additionalContextVariables) {
        buildAndSend(recipient, subjectKey, List.of(), contentTemplate, additionalContextVariables, true);
    }

    /**
     * Builds and sends an e-mail to the specified recipient asynchronously with subject arguments
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param subjectArgs                The arguments to be substituted in the subject message (e.g., for {0}, {1} placeholders)
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     */
    @Async("mailTaskExecutor")
    public void buildAndSendAsync(@NonNull MailRecipientDTO recipient, @NonNull String subjectKey, @NonNull List<String> subjectArgs, @NonNull String contentTemplate,
            @NonNull Map<String, Object> additionalContextVariables) {
        buildAndSend(recipient, subjectKey, subjectArgs, contentTemplate, additionalContextVariables, true);
    }

    /**
     * Builds and sends an e-mail to the specified recipient synchronously and reports whether it was actually sent.
     * Unlike {@link #buildAndSendAsync}, this blocks until the send completes and returns {@code false} if mail is not
     * configured, the template/subject could not be rendered, or the SMTP server rejected the message. Use this for the
     * GDPR "warn before erase" flows, where an account/course must only be advanced to the "warned" state once a warning
     * was really delivered, so that an SMTP outage warns nobody (and therefore erases nobody) rather than silently
     * scheduling deletions for warnings that never arrived.
     * <p>
     * A mail the SMTP server does not accept is never queued here: a queued mail is not a delivered one, and the caller
     * has its own way to try again on its next run.
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param subjectArgs                The arguments to be substituted in the subject message (e.g., for {0}, {1} placeholders)
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     * @return {@code true} if the e-mail was successfully handed to the SMTP server, {@code false} otherwise
     */
    public boolean buildAndSendSyncReporting(@NonNull MailRecipientDTO recipient, @NonNull String subjectKey, @NonNull List<String> subjectArgs, @NonNull String contentTemplate,
            @NonNull Map<String, Object> additionalContextVariables) {
        return buildAndSend(recipient, subjectKey, subjectArgs, contentTemplate, additionalContextVariables, false) == Delivery.SENT;
    }

    /**
     * Builds and sends an e-mail to the specified recipient
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param subjectArgs                The arguments to be substituted in the subject message
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     * @param mayBeQueued                Whether the mail may wait in the retry queue if the SMTP server does not accept it now
     */
    private Delivery buildAndSend(@NonNull MailRecipientDTO recipient, @NonNull String subjectKey, @NonNull List<String> subjectArgs, @NonNull String contentTemplate,
            @NonNull Map<String, Object> additionalContextVariables, boolean mayBeQueued) {
        String localeKey = recipient.langKey();
        if (localeKey == null) {
            localeKey = "en";
        }
        Locale locale = Locale.forLanguageTag(localeKey);
        Context context = new Context(locale);
        context.setVariable("user", recipient);
        context.setVariable("baseUrl", artemisServerUrl);

        additionalContextVariables.forEach(context::setVariable);

        String subject;
        String content;
        try {
            Object[] argsArray = subjectArgs.isEmpty() ? null : subjectArgs.toArray();
            subject = messageSource.getMessage(subjectKey, argsArray, context.getLocale());
            content = templateEngine.process(contentTemplate, context);
        }
        catch (NoSuchMessageException | TemplateProcessingException ex) {
            log.error("Failed to build email with subject key '{}' and template '{}': {}", subjectKey, contentTemplate, ex.getMessage(), ex);
            return Delivery.FAILED;
        }

        return deliver(recipient.email(), recipient.login(), subject, content, false, true, MailPriority.TRANSACTIONAL, mayBeQueued);
    }

    /**
     * Hands an e-mail to the SMTP server, or keeps it for later if the server does not accept it for now.
     *
     * @param recipientEmail the e-mail address to send to
     * @param recipientLogin the recipient's login (used for logging and for the test-account check)
     * @param subject        The mail subject
     * @param content        The content of the mail. Can be enriched with HTML tags
     * @param isMultipart    Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml         Whether the mail should support HTML tags
     * @param priority       How urgently the mail has to be delivered
     * @param mayBeQueued    Whether the mail may wait in the retry queue if the SMTP server does not accept it now
     */
    private Delivery deliver(String recipientEmail, @Nullable String recipientLogin, String subject, String content, boolean isMultipart, boolean isHtml, MailPriority priority,
            boolean mayBeQueued) {
        if (!mailConfigured) {
            log.debug("Skipping email to '{}' - mail not configured", recipientEmail);
            return Delivery.NOT_CONFIGURED;
        }
        if (testAccountEmailPolicy.suppressesEmailTo(recipientLogin)) {
            log.debug("Skipping email with subject '{}' to test account '{}' - it has not opted in to e-mail", subject, recipientLogin);
            retryQueue.record(Outcome.SUPPRESSED);
            return Delivery.SUPPRESSED;
        }

        Instant now = Instant.now();
        PendingMail mail = retryQueue.newPendingMail(recipientEmail, recipientLogin, subject, content, isMultipart, isHtml, priority, now);
        boolean canBeQueued = mayBeQueued && retryQueue.isEnabled();

        if (canBeQueued && retryQueue.isPaused(now)) {
            // The SMTP server asked this node to stop sending. Trying each mail again would only collect the same refusal.
            log.debug("Queued email with subject '{}' to user '{}' because sending is paused", subject, recipientLogin);
            if (!retryQueue.offer(mail)) {
                return Delivery.FAILED;
            }
            retryQueue.record(Outcome.QUEUED_FOR_RETRY);
            return Delivery.QUEUED;
        }

        SendAttempt attempt = trySend(mail);
        if (attempt.isAccepted()) {
            retryQueue.record(Outcome.SENT);
            log.info("Sent email with subject '{}' to user '{}' (message id {})", subject, recipientLogin, attempt.messageId());
            return Delivery.SENT;
        }
        // Read the clock again: a connection that timed out has used up part of the pause the failure is about to start.
        return handleFailure(mail, attempt, Instant.now(), canBeQueued, false);
    }

    /**
     * Decides what becomes of a mail whose attempt to be sent failed: it is dropped, or it waits for the next attempt.
     *
     * @return {@link Delivery#QUEUED} if the mail waits, {@link Delivery#FAILED} if it is gone
     */
    private Delivery handleFailure(PendingMail mail, SendAttempt attempt, Instant now, boolean canBeQueued, boolean wasQueued) {
        MailFailureKind failure = attempt.failure();
        Exception exception = attempt.exception();
        if (failure == MailFailureKind.PERMANENT || !canBeQueued) {
            retryQueue.record(Outcome.REJECTED);
            // Note: we should not rethrow the exception here, as this would prevent sending out other emails in case multiple users are affected
            log.error("Email could not be sent to user '{}' (subject '{}', reason {}) and will not be tried again", mail.recipientLogin(), mail.subject(), failure, exception);
            return Delivery.FAILED;
        }

        boolean wasPaused = retryQueue.isPaused(now);
        Instant nextAttemptAt = retryQueue.scheduleAfterFailure(failure, mail.attempts() + 1, now);
        PendingMail waiting = mail.afterFailedAttempt(nextAttemptAt);
        if (!retryQueue.offer(waiting)) {
            return Delivery.FAILED;
        }
        if (!wasQueued) {
            retryQueue.record(Outcome.QUEUED_FOR_RETRY);
        }
        String reason = exception == null ? "unknown" : NestedExceptionUtils.getMostSpecificCause(exception).getMessage();
        if (failure.pausesDelivery()) {
            if (!wasPaused) {
                log.warn("The SMTP server does not accept mail ({}: {}). This node stops sending until {} and keeps the e-mails that arrive meanwhile ({} queued).", failure,
                        reason, nextAttemptAt, retryQueue.size());
            }
        }
        else {
            log.warn("Email could not be sent to user '{}' (subject '{}', reason {}: {}), will try again at {}", mail.recipientLogin(), mail.subject(), failure, reason,
                    nextAttemptAt);
        }
        return Delivery.QUEUED;
    }

    /**
     * Builds the message of a mail and hands it to the SMTP server.
     */
    private SendAttempt trySend(PendingMail mail) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}'", mail.multipart(), mail.html(), mail.recipientLogin(), mail.subject());

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, mail.multipart(), StandardCharsets.UTF_8.name());
            message.setTo(mail.recipientEmail());
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(mail.subject());
            message.setText(mail.content(), mail.html());
            javaMailSender.send(mimeMessage);
            return SendAttempt.accepted(messageIdOf(mimeMessage));
        }
        catch (MailSendException e) {
            if (e.getFailedMessages().isEmpty()) {
                // Spring reports no failed message only if the server accepted the mail and closing the connection failed afterwards.
                // Trying again would deliver the mail twice and stop this node from sending for no reason.
                log.warn("Email with subject '{}' to user '{}' was accepted, but closing the connection to the SMTP server failed: {}", mail.subject(), mail.recipientLogin(),
                        e.getMessage());
                return SendAttempt.accepted(messageIdOf(mimeMessage));
            }
            return SendAttempt.failed(MailFailureClassifier.classify(e), e);
        }
        catch (MailException | MessagingException e) {
            return SendAttempt.failed(MailFailureClassifier.classify(e), e);
        }
    }

    /**
     * The id that the mail carries after it was sent, which a bounce message of a mail server refers to. Not every
     * sender assigns one, so it may be missing.
     */
    private static @Nullable String messageIdOf(MimeMessage mimeMessage) {
        try {
            return mimeMessage.getMessageID();
        }
        catch (MessagingException e) {
            return null;
        }
    }

    /**
     * Works off the queue of mails that the SMTP server did not accept: a mail whose time has come is sent again, a mail
     * that is too old is dropped. Runs on every node, because every node queues the mails it could not send itself.
     * <p>
     * A run stops when the SMTP server reports that the quota is used up or cannot be reached, and after a limited number
     * of mails, so that a backlog is not sent in one burst that uses up the quota again.
     */
    @Async("mailTaskExecutor")
    @Scheduled(fixedDelayString = "${artemis.mail.retry.drain-interval:PT30S}", initialDelayString = "${artemis.mail.retry.drain-interval:PT30S}")
    public void sendQueuedMails() {
        sendQueuedMails(Instant.now());
    }

    void sendQueuedMails(Instant startedAt) {
        if (!retryQueue.isEnabled() || !mailConfigured) {
            return;
        }
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            drainQueue(startedAt);
        }
        finally {
            draining.set(false);
        }
    }

    private void drainQueue(Instant startedAt) {
        Instant clockStart = Instant.now();
        int attempted = 0;
        int sent = 0;
        while (attempted < retryQueue.drainBatchSize()) {
            // Sending takes time, so the moment of each mail is read anew instead of taken from the start of the run.
            Instant now = startedAt.plus(Duration.between(clockStart, Instant.now()));
            if (retryQueue.isPaused(now)) {
                break;
            }
            Optional<PendingMail> due = retryQueue.pollDue(now);
            if (due.isEmpty()) {
                break;
            }
            attempted++;
            PendingMail mail = due.get();
            if (mail.isExpired(now)) {
                retryQueue.record(Outcome.EXPIRED);
                log.warn("Email with subject '{}' to user '{}' was dropped after {} failed attempts, because it is too old to be sent", mail.subject(), mail.recipientLogin(),
                        mail.attempts());
                continue;
            }
            SendAttempt attempt = trySend(mail);
            if (attempt.isAccepted()) {
                sent++;
                retryQueue.record(Outcome.SENT_AFTER_RETRY);
                log.info("Sent email with subject '{}' to user '{}' (message id {}) after {} failed attempts", mail.subject(), mail.recipientLogin(), attempt.messageId(),
                        mail.attempts());
            }
            else {
                handleFailure(mail, attempt, now, true, true);
            }
        }
        if (attempted > 0) {
            log.info("Worked off the e-mail queue: {} of {} mails sent, {} still waiting", sent, attempted, retryQueue.size());
        }
    }
}
