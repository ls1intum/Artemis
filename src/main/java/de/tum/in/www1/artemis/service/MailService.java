package de.tum.in.www1.artemis.service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTarget;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
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

    private final JHipsterProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    // notification related variables

    private static final String NOTIFICATION = "notification";

    private static final String NOTIFICATION_TYPE_FROM_TITLE = "notificationTypeFromTitle";

    private static final String NOTIFICATION_SUBJECT = "notificationSubject";

    private static final String NOTIFICATION_URL = "notificationUrl";

    private static final String IS_GROUP_NOTIFICATION = "isGroupNotification";

    public MailService(JHipsterProperties jHipsterProperties, JavaMailSender javaMailSender, MessageSource messageSource, SpringTemplateEngine templateEngine) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
    }

    /**
     * Sends an e-mail to the specified sender
     *
     * @param isGroupEmail indicates if the email will be send to an individual or a group
     * @param users who should be contacted.
     * @param subject The mail subject
     * @param content The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml Whether the mail should support HTML tags
     */
    @Async
    public void sendEmail(boolean isGroupEmail, List<User> users, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}", isMultipart, isHtml, users, subject, content);

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            if (!isGroupEmail) {
                message.setTo(users.get(0).getEmail());
            }
            else {
                String[] bcc = users.stream().map(User::getEmail).toArray(String[]::new);
                message.setBcc(bcc);
            }
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, isHtml);
            javaMailSender.send(mimeMessage);
            log.info("Sent email with subject '{}' to User '{}'", subject, users);
        }
        catch (MailException | MessagingException e) {
            log.warn("Email could not be sent to user '{}'", users, e);
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
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, jHipsterProperties.getMail().getBaseUrl());

        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, context.getLocale());
        sendEmail(false, Collections.singletonList(user), subject, content, false, true);
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

    /**
     * Sends a notification based Email to one user or to multiple via BCC (i.e. only one email is created)
     * @param notification which properties are used to create the email
     * @param users that should be contacted (might be only one user)
     */
    @Async
    public void sendNotificationEmail(Notification notification, List<User> users) {
        boolean isGroup = notification instanceof GroupNotification;
        User user = users.get(0);
        log.debug(isGroup ? "Sending group notification email" : "Sending notification email to '{}'", user.getEmail());

        Locale locale = Locale.forLanguageTag(isGroup ? "en" : user.getLangKey());
        Context context = new Context(locale);

        if (!isGroup) {
            context.setVariable(USER, user);
        }
        context.setVariable(NOTIFICATION, notification);
        NotificationType notificationType = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        context.setVariable(NOTIFICATION_TYPE_FROM_TITLE, notificationType.toString());
        context.setVariable(NOTIFICATION_SUBJECT, findNotificationSubject(notification));
        // replace with (e.g.) "http://localhost:9000" for local testing
        context.setVariable(NOTIFICATION_URL, NotificationTarget.extractNotificationUrl(notification, jHipsterProperties.getMail().getBaseUrl()));
        context.setVariable(IS_GROUP_NOTIFICATION, isGroup);
        context.setVariable(BASE_URL, jHipsterProperties.getMail().getBaseUrl());

        String content = templateEngine.process("mail/notificationEmail", context);
        String subject = notification.getTitle();

        sendEmail(isGroup, users, subject, content, false, true);
    }

    /**
     * Finds the most important part (the "subject") of the notification text property
     * E.g. notification (original type = EXERCISE_CREATED) -> "subject" = name of the exercise (this information is part of the text property)
     * @param notification which "subject" should be extracted
     * @return the "subject" of the notification (text property)
     */
    private String findNotificationSubject(Notification notification) {
        String text = notification.getText();
        // some notification texts can be customized (e.g. by an instructor) -> usually no [..."subject"...] structure anymore
        boolean isCustomSubject = text.indexOf('"') == -1;
        if (isCustomSubject) {
            return text;
        }
        return text.substring(text.indexOf('"') + 1, text.lastIndexOf('"'));
    }
}
