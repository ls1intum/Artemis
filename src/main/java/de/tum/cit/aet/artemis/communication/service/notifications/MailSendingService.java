package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.core.domain.User;
import tech.jhipster.config.JHipsterProperties;

/**
 * Service for sending emails asynchronously.
 */
@Service
@Profile(PROFILE_CORE)
public class MailSendingService {

    private static final Logger log = LoggerFactory.getLogger(MailSendingService.class);

    private final JHipsterProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    public MailSendingService(JHipsterProperties jHipsterProperties, JavaMailSender javaMailSender, MessageSource messageSource, SpringTemplateEngine templateEngine) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
    }

    /**
     * Sends an e-mail to the specified sender asynchronously
     *
     * @param recipient   who should be contacted.
     * @param subject     The mail subject
     * @param content     The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml      Whether the mail should support HTML tags
     */
    @Async
    public void sendEmail(User recipient, String subject, String content, boolean isMultipart, boolean isHtml) {
        executeSend(recipient, subject, content, isMultipart, isHtml);
    }

    /**
     * Sends an e-mail to the specified sender synchronously
     *
     * @param recipient   who should be contacted.
     * @param subject     The mail subject
     * @param content     The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml      Whether the mail should support HTML tags
     */
    public void sendEmailSync(User recipient, String subject, String content, boolean isMultipart, boolean isHtml) {
        executeSend(recipient, subject, content, isMultipart, isHtml);
    }

    /**
     * Builds and sends an e-mail to the specified sender synchronously
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     *
     * @return true if mail was sent, false if not
     */
    public boolean buildAndSendSync(User recipient, String subjectKey, String contentTemplate, Map<String, Object> additionalContextVariables) {
        return buildAndSend(recipient, subjectKey, contentTemplate, additionalContextVariables);
    }

    /**
     * Builds and sends an e-mail to the specified sender synchronously
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     */
    @Async
    public void buildAndSendAsync(User recipient, String subjectKey, String contentTemplate, Map<String, Object> additionalContextVariables) {
        buildAndSend(recipient, subjectKey, contentTemplate, additionalContextVariables);
    }

    /**
     * Builds and sends an e-mail to the specified sender
     *
     * @param recipient                  who should be contacted.
     * @param subjectKey                 The locale key of the subject
     * @param contentTemplate            The thymeleaf .html file path to render
     * @param additionalContextVariables The context variables for the template aside from the baseUrl and user
     *
     * @return true if mail was sent, false if not
     */
    private boolean buildAndSend(User recipient, String subjectKey, String contentTemplate, Map<String, Object> additionalContextVariables) {
        String localeKey = recipient.getLangKey();
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
            subject = messageSource.getMessage(subjectKey, null, context.getLocale());
            content = templateEngine.process(contentTemplate, context);
        }
        catch (NoSuchMessageException | TemplateProcessingException ex) {
            return false;
        }

        executeSend(recipient, subject, content, false, true);

        return true;
    }

    /**
     * Executes sending an e-mail to the specified sender
     *
     * @param recipient   who should be contacted.
     * @param subject     The mail subject
     * @param content     The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml      Whether the mail should support HTML tags
     */
    private void executeSend(User recipient, String subject, String content, boolean isMultipart, boolean isHtml) {
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
