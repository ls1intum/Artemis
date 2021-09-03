package de.tum.in.www1.artemis.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.repository.UserRepository;
import io.github.jhipster.config.JHipsterProperties;

/**
 * Service for sending emails.
 * <p>
 * We use the @Async annotation to send emails asynchronously.
 */
@Service
public class MailService {

    private final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String USER = "user";

    private static final String BASE_URL = "baseUrl";

    private static final String NOTIFICATION_TITLE = "notificationTitle";

    private static final String NOTIFICATION_TEXT = "notificationText";

    private final JHipsterProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    private final UserRepository userRepository;

    public MailService(JHipsterProperties jHipsterProperties, JavaMailSender javaMailSender, MessageSource messageSource, SpringTemplateEngine templateEngine,
            UserRepository userRepository) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;

        this.userRepository = userRepository;
    }

    /**
     * Sends an e-mail to the specified sender
     *
     * @param to The receiver address
     * @param subject The mail subject
     * @param content The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml Whether the mail should support HTML tags
     */
    @Async
    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}", isMultipart, isHtml, to, subject, content);

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, isHtml);
            javaMailSender.send(mimeMessage);
            log.info("Sent email with subject '{}' to User '{}'", subject, to);
        }
        catch (MailException | MessagingException e) {
            log.warn("Email could not be sent to user '{}'", to, e);
        }
    }

    /**
     * Sends a predefined mail based on a template
     *
     * @param user The receiver of the mail
     * @param templateName The name of the template
     * @param titleKey The key mapping the title for the subject of the mail
     */
    @Async
    public void sendEmailFromTemplate(User user, String templateName, String titleKey) {
        Context context = this.prepareContext(user);
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, context.getLocale());
        sendEmail(user.getEmail(), subject, content, false, true);
    }

    private Context prepareContext(User user) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, jHipsterProperties.getMail().getBaseUrl());
        return context;
    }

    @Async
    public void sendActivationEmail(User user) {
        log.debug("Sending activation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/activationEmail", "email.activation.title");
    }

    @Async
    public void sendCreationEmail(User user) {
        log.debug("Sending creation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/creationEmail", "email.activation.title");
    }

    @Async
    public void sendPasswordResetMail(User user) {
        log.debug("Sending password reset email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/passwordResetEmail", "email.reset.title");
    }

    @Async
    public void sendSAML2SetPasswordMail(User user) {
        log.debug("Sending SAML2 set password email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/samlSetPasswordEmail", "email.saml.title");
    }

    // notification related
    @Async
    public void sendSingleUserNotificationEmail(SingleUserNotification notification) {
        User user = notification.getRecipient();
        log.debug("Sending notification email to '{}'", user.getEmail());
        String templateName = "mail/notifications/notificationEmailTest";
        Context context = this.prepareContext(user);
        context.setVariable(NOTIFICATION_TITLE, notification.getTitle());
        context.setVariable(NOTIFICATION_TEXT, notification.getText());
        String content = templateEngine.process(templateName, context);
        // String subject = messageSource.getMessage("email.notification.dummy", null, locale);
        // String subject = messageSource.getMessage(notification.getTitle(), null, locale);
        String subject = notification.getTitle();
        sendEmail(user.getEmail(), subject, content, false, true);
    }

    @Async
    public void sendGroupNotificationEmail(Notification notification, List<User> userList) {
        log.debug("Sending group notification email");
        // TODO change templateName to group notification

        Locale localeTest = Locale.forLanguageTag(userList.get(0).getLangKey());

        String templateName = "mail/notifications/notificationEmailTest";
        // Context context = this.prepareContext(user);
        Locale locale = Locale.forLanguageTag("en");
        Context context = new Context(locale);
        // context.setVariable(USER, user);
        context.setVariable(BASE_URL, jHipsterProperties.getMail().getBaseUrl());
        context.setVariable(NOTIFICATION_TITLE, notification.getTitle());
        context.setVariable(NOTIFICATION_TEXT, notification.getText());
        String content = templateEngine.process(templateName, context);
        // String subject = messageSource.getMessage("email.notification.dummy", null, locale);
        // String subject = messageSource.getMessage(notification.getTitle(), null, locale);
        String subject = notification.getTitle();

        // TODO add filter by settings here

        String[] bcc = userList.stream().map(User::getEmail).toArray(String[]::new);

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            // message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            // message.setText(content, isHtml);
            message.setText(content, true);

            message.setBcc(bcc);

            javaMailSender.send(mimeMessage);
            log.info("Sent email with subject '{}'", subject);
        }
        catch (MailException | MessagingException e) {
            log.warn("Email could not be sent", e);
        }
    }
}
