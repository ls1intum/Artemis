package de.tum.in.www1.artemis.service;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import tech.jhipster.config.JHipsterProperties;

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

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final JHipsterProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    private final TimeService timeService;

    // notification related variables

    private static final String NOTIFICATION = "notification";

    private static final String NOTIFICATION_SUBJECT = "notificationSubject";

    private static final String NOTIFICATION_URL = "notificationUrl";

    // time related variables
    private static final String TIME_SERVICE = "timeService";

    public MailService(JHipsterProperties jHipsterProperties, JavaMailSender javaMailSender, MessageSource messageSource, SpringTemplateEngine templateEngine,
            TimeService timeService) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.timeService = timeService;
    }

    /**
     * Sends an e-mail to the specified sender
     *
     * @param user who should be contacted.
     * @param subject The mail subject
     * @param content The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml Whether the mail should support HTML tags
     */
    @Async
    public void sendEmail(User user, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}", isMultipart, isHtml, user, subject, content);

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(user.getEmail());
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, isHtml);
            javaMailSender.send(mimeMessage);
            log.info("Sent email with subject '{}' to User '{}'", subject, user);
        }
        catch (MailException | MessagingException e) {
            log.warn("Email could not be sent to user '{}'", user, e);
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
        context.setVariable(BASE_URL, artemisServerUrl);
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, context.getLocale());
        sendEmail(user, subject, content, false, true);
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
     * Sends a notification based Email to one user
     * @param notification which properties are used to create the email
     * @param user who should be contacted
     * @param notificationSubject that is used to provide further information (e.g. exercise, attachment, post, etc.)
     */
    @Async
    public void sendNotificationEmail(Notification notification, User user, Object notificationSubject) {
        NotificationType notificationType = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        log.debug("Sending \"{}\" notification email to '{}'", notificationType.name(), user.getEmail());

        Locale locale = Locale.forLanguageTag(user.getLangKey());

        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(NOTIFICATION, notification);
        context.setVariable(NOTIFICATION_SUBJECT, notificationSubject);

        context.setVariable(TIME_SERVICE, this.timeService);

        // replace with (e.g.) "http://localhost:9000" for local testing
        context.setVariable(NOTIFICATION_URL, NotificationTarget.extractNotificationUrl(notification, artemisServerUrl.toString()));
        context.setVariable(BASE_URL, artemisServerUrl);

        String content = createContentForNotificationEmailByType(notificationType, context);
        String subject = notification.getTitle();

        sendEmail(user, subject, content, false, true);
    }

    /**
     * Creates content for a notification email based on its type
     * @param notificationType which is used to find the corresponding html template
     * @param context which is needed for creating the content via the templateEngine
     * @return created content based on notification type
     */
    private String createContentForNotificationEmailByType(NotificationType notificationType, Context context) {
        return switch (notificationType) {
            case ATTACHMENT_CHANGE -> templateEngine.process("mail/notification/attachmentChangedEmail", context);
            case EXERCISE_CREATED -> templateEngine.process("mail/notification/exerciseReleasedEmail", context);
            case EXERCISE_PRACTICE -> templateEngine.process("mail/notification/exerciseOpenForPracticeEmail", context);
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        };
    }

    @Async
    public void sendNotificationEmailForMultipleUsers(GroupNotification notification, List<User> users, Object notificationSubject) {
        users.forEach(user -> sendNotificationEmail(notification, user, notificationSubject));
    }
}
