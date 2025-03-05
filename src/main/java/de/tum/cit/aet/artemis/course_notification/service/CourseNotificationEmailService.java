package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.course_notification.dto.CourseNotificationDTO;

/**
 * Service responsible for sending course notifications via email to recipients.
 *
 * <p>
 * This implementation of {@link CourseNotificationBroadcastService} handles email-based notifications
 * by generating localized email content from templates and sending them to the appropriate users.
 * Each email is processed asynchronously to improve performance when sending to multiple recipients.
 * </p>
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationEmailService extends CourseNotificationBroadcastService {

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    private final MailSendingService mailSendingService;

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationEmailService.class);

    private static final String SERVER_URL_KEY = "serverUrl";

    private static final String TYPE_KEY = "notificationType";

    private static final String RECIPIENT_KEY = "recipient";

    private static final String COURSE_ID_KEY = "courseId";

    private static final String PARAMETERS_KEY = "parameters";

    private static final String CREATION_DATE_KEY = "creationDate";

    private static final String CATEGORY_KEY = "category";

    public CourseNotificationEmailService(MessageSource messageSource, SpringTemplateEngine templateEngine, MailSendingService mailSendingService) {
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Sends course notifications via email to all recipients. The emails are sent asynchronously.
     *
     * <p>
     * This method creates a separate process for each email that needs to be sent.
     * It uses the recipient's language preference to localize the content and subject
     * of the email.
     * </p>
     *
     * @param courseNotification The notification data to be sent
     * @param recipients         The list of users who should receive the notification
     */
    @Override
    protected void sendCourseNotification(CourseNotificationDTO courseNotification, List<User> recipients) {
        recipients.forEach(recipient -> {
            String localeKey = recipient.getLangKey();
            if (localeKey == null) {
                localeKey = "en";
            }
            Locale locale = Locale.forLanguageTag(localeKey);
            Context context = new Context(locale);
            context.setVariable(SERVER_URL_KEY, artemisServerUrl);
            context.setVariable(TYPE_KEY, courseNotification.notificationType());
            context.setVariable(RECIPIENT_KEY, recipient);
            context.setVariable(COURSE_ID_KEY, courseNotification.courseId());
            context.setVariable(PARAMETERS_KEY, courseNotification.parameters());
            context.setVariable(CREATION_DATE_KEY, courseNotification.creationDate());
            context.setVariable(CATEGORY_KEY, courseNotification.category());
            String subject;
            String content;
            try {
                subject = messageSource.getMessage(getLocalePrefix(courseNotification) + ".title", null, context.getLocale());
            }
            catch (NoSuchMessageException e) {
                log.error("Subject for e-mail could not be generated. Make sure to create the locale key {}.title.", getLocalePrefix(courseNotification), e);
                return;
            }
            try {
                content = templateEngine.process(getMailTemplateDirectory(courseNotification), context);
            }
            catch (TemplateProcessingException e) {
                log.error("Content for e-mail could not be generated. Make sure to create the template file {}.", getMailTemplateDirectory(courseNotification), e);
                return;
            }

            mailSendingService.sendEmail(recipient, subject, content, false, true);
        });
    }

    /**
     * Gets the locale prefix for the given notification type.
     *
     * @param courseNotification The notification to get the locale prefix for
     * @return The locale prefix string for message lookups
     */
    private String getLocalePrefix(CourseNotificationDTO courseNotification) {
        return "email.notification." + courseNotification.notificationType();
    }

    /**
     * Gets the mail template directory path for the given notification type.
     *
     * @param courseNotification The notification to get the template directory for
     * @return The path to the email template file
     */
    private String getMailTemplateDirectory(CourseNotificationDTO courseNotification) {
        return "mail/notification/" + courseNotification.notificationType();
    }
}
