package de.tum.cit.aet.artemis.service.notifications;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.User;
import tech.jhipster.config.JHipsterProperties;

/**
 * Service for sending emails asynchronously.
 */
@Service
@Profile(PROFILE_CORE)
class MailSendingService {

    private static final Logger log = LoggerFactory.getLogger(MailSendingService.class);

    private final JHipsterProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    MailSendingService(JHipsterProperties jHipsterProperties, JavaMailSender javaMailSender) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
    }

    /**
     * Sends an e-mail to the specified sender
     *
     * @param recipient   who should be contacted.
     * @param subject     The mail subject
     * @param content     The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml      Whether the mail should support HTML tags
     */
    @Async
    void sendEmail(User recipient, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}'", isMultipart, isHtml, recipient, subject);

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(recipient.getEmail());
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, isHtml);
            javaMailSender.send(mimeMessage);
            log.info("Sent email with subject '{}' to User '{}'", subject, recipient);
        }
        catch (MailException | MessagingException e) {
            log.error("Email could not be sent to user '{}'", recipient, e);
            // Note: we should not rethrow the exception here, as this would prevent sending out other emails in case multiple users are affected
        }
    }

}
