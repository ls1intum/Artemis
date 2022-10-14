package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.EXERCISE_SUBMISSION_ASSESSED;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.extractNotificationUrl;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.exception.ArtemisMailException;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
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

    private static final String EXERCISE_TYPE = "exerciseType";

    private static final String PLAGIARISM_VERDICT = "plagiarismVerdict";

    private static final String ASSESSED_SCORE = "assessedScore";

    private static final String RELATIVE_SCORE = "relativeScore";

    private static final String NOTIFICATION_TYPE = "notificationType";

    // Translation that can not be done via i18n Resource Bundle (for Thymeleaf) but has to be set in this service via Java
    private final String newAnnouncementEN = "New announcement \"%s\" in course \"%s\"";

    private final String newAnnouncementDE = "Neue Ankündigung \"%s\" im Kurs \"%s\"";

    // time related variables
    private static final String TIME_SERVICE = "timeService";

    // weekly summary related variables

    private static final String WEEKLY_SUMMARY_NEW_EXERCISES = "weeklySummaryNewExercises";

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
     * @param recipient who should be contacted.
     * @param subject The mail subject
     * @param content The content of the mail. Can be enriched with HTML tags
     * @param isMultipart Whether to create a multipart that supports alternative texts, inline elements
     * @param isHtml Whether the mail should support HTML tags
     */
    @Async
    public void sendEmail(User recipient, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}", isMultipart, isHtml, recipient, subject, content);

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
            throw new ArtemisMailException("Email could not be sent to user", e);
        }
    }

    /**
     * Sends a predefined mail based on a template
     *
     * @param user The receiver of the mail
     * @param templateName The name of the template
     * @param titleKey The key mapping the title for the subject of the mail
     */
    public void sendEmailFromTemplate(User user, String templateName, String titleKey) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, artemisServerUrl);
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, context.getLocale());
        sendEmail(user, subject, content, false, true);
    }

    public void sendActivationEmail(User user) {
        log.debug("Sending activation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/activationEmail", "email.activation.title");
    }

    public void sendPasswordResetMail(User user) {
        log.debug("Sending password reset email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/passwordResetEmail", "email.reset.title");
    }

    public void sendSAML2SetPasswordMail(User user) {
        log.debug("Sending SAML2 set password email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/samlSetPasswordEmail", "email.saml.title");
    }

    // notification related

    /**
     * Sets the context and subject for the case that the notificationSubject is a Post
     * @param context that is modified
     * @param notificationSubject which has to be a Post
     * @param locale used for translations
     * @return the modified subject of the email
     */
    private String setPostContextAndSubject(Context context, Object notificationSubject, Locale locale) {
        // For Announcement Posts
        String newAnnouncementString = locale.toString().equals("en") ? newAnnouncementEN : newAnnouncementDE;
        String postTitle = ((Post) notificationSubject).getTitle();
        String courseTitle = ((Post) notificationSubject).getCourse().getTitle();

        return String.format(newAnnouncementString, postTitle, courseTitle);
    }

    /**
     * Sends a notification based email to one user
     * @param notification which properties are used to create the email
     * @param user who should be contacted
     * @param notificationSubject that is used to provide further information (e.g. exercise, attachment, post, etc.)
     */
    public void sendNotificationEmail(Notification notification, User user, Object notificationSubject) {
        NotificationType notificationType = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        log.debug("Sending \"{}\" notification email to '{}'", notificationType.name(), user.getEmail());

        String localeKey = user.getLangKey();
        if (localeKey == null) {
            throw new IllegalArgumentException(
                    "The user object has no language key defined. This can happen if you do not load the user object from the database but take it straight from the client");
        }

        Locale locale = Locale.forLanguageTag(localeKey);

        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(NOTIFICATION, notification);
        context.setVariable(NOTIFICATION_SUBJECT, notificationSubject);

        context.setVariable(TIME_SERVICE, this.timeService);
        String subject = notification.getTitle();

        if (notificationSubject instanceof Exercise exercise) {
            context.setVariable(EXERCISE_TYPE, exercise.getExerciseType());
            checkAndPrepareExerciseSubmissionAssessedCase(notificationType, context, exercise, user);
        }
        if (notificationSubject instanceof PlagiarismCase plagiarismCase) {
            context.setVariable(PLAGIARISM_VERDICT, plagiarismCase.getVerdict());
        }

        if (notificationSubject instanceof SingleUserNotificationService.TutorialGroupNotificationSubject tutorialGroupNotificationSubject) {
            setContextForTutorialGroupNotifications(context, notificationType, tutorialGroupNotificationSubject);
        }

        if (notificationSubject instanceof Post post) {
            // posts use a different mechanism for the url
            context.setVariable(NOTIFICATION_URL, extractNotificationUrl(post, artemisServerUrl.toString()));
            subject = setPostContextAndSubject(context, notificationSubject, locale);
        }
        else {
            context.setVariable(NOTIFICATION_URL, extractNotificationUrl(notification, artemisServerUrl.toString()));
        }
        context.setVariable(BASE_URL, artemisServerUrl);

        String content = createContentForNotificationEmailByType(notificationType, context);

        sendEmail(user, subject, content, false, true);
    }

    private void setContextForTutorialGroupNotifications(Context context, NotificationType notificationType,
            SingleUserNotificationService.TutorialGroupNotificationSubject notificationSubject) {

        if (NotificationType.TUTORIAL_GROUP_REGISTRATION_STUDENT.equals(notificationType)) {
            context.setVariable(NOTIFICATION_TYPE, "studentRegistration");
            context.setVariable("student", notificationSubject.users().stream().findFirst().orElse(null));
        }
        if (NotificationType.TUTORIAL_GROUP_DEREGISTRATION_STUDENT.equals(notificationType)) {
            context.setVariable(NOTIFICATION_TYPE, "studentDeregistration");
            context.setVariable("student", notificationSubject.users().stream().findFirst().orElse(null));
        }
        if (NotificationType.TUTORIAL_GROUP_REGISTRATION_TUTOR.equals(notificationType)) {
            context.setVariable(NOTIFICATION_TYPE, "tutorRegistration");
            context.setVariable("student", notificationSubject.users().stream().findFirst().orElse(null));
        }
        if (NotificationType.TUTORIAL_GROUP_DEREGISTRATION_TUTOR.equals(notificationType)) {
            context.setVariable(NOTIFICATION_TYPE, "tutorDeregistration");
            context.setVariable("student", notificationSubject.users().stream().findFirst().orElse(null));
        }
        if (NotificationType.TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR.equals(notificationType)) {
            context.setVariable(NOTIFICATION_TYPE, "tutorRegistrationMultiple");
            context.setVariable("numberOfStudents", notificationSubject.users().size());
        }
        if (NotificationType.TUTORIAL_GROUP_ASSIGNED.equals(notificationType)) {
            context.setVariable(NOTIFICATION_TYPE, "tutorialGroupAssigned");
        }
        if (NotificationType.TUTORIAL_GROUP_UNASSIGNED.equals(notificationType)) {
            context.setVariable(NOTIFICATION_TYPE, "tutorialGroupUnassigned");
        }
    }

    /**
     * Creates content for a notification email based on its type
     *
     * @param notificationType which is used to find the corresponding html template
     * @param context          which is needed for creating the content via the templateEngine
     * @return created content based on notification type
     */
    private String createContentForNotificationEmailByType(NotificationType notificationType, Context context) {
        return switch (notificationType) {
            case ATTACHMENT_CHANGE -> templateEngine.process("mail/notification/attachmentChangedEmail", context);
            case EXERCISE_RELEASED -> templateEngine.process("mail/notification/exerciseReleasedEmail", context);
            case EXERCISE_PRACTICE -> templateEngine.process("mail/notification/exerciseOpenForPracticeEmail", context);
            case NEW_ANNOUNCEMENT_POST -> templateEngine.process("mail/notification/announcementPostEmail", context);
            case FILE_SUBMISSION_SUCCESSFUL -> templateEngine.process("mail/notification/fileSubmissionSuccessfulEmail", context);
            case EXERCISE_SUBMISSION_ASSESSED -> templateEngine.process("mail/notification/exerciseSubmissionAssessedEmail", context);
            case DUPLICATE_TEST_CASE -> templateEngine.process("mail/notification/duplicateTestCasesEmail", context);
            case NEW_PLAGIARISM_CASE_STUDENT -> templateEngine.process("mail/notification/plagiarismCaseEmail", context);
            case PLAGIARISM_CASE_VERDICT_STUDENT -> templateEngine.process("mail/notification/plagiarismVerdictEmail", context);
            case TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_UNASSIGNED -> templateEngine
                    .process("mail/notification/tutorialGroupBasicEmail", context);
            case TUTORIAL_GROUP_DELETED -> templateEngine.process("mail/notification/tutorialGroupDeletedEmail", context);
            case TUTORIAL_GROUP_UPDATED -> templateEngine.process("mail/notification/tutorialGroupUpdatedEmail", context);
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        };
    }

    /**
     * Auxiliary method for EXERCISE_SUBMISSION_ASSESSED case to load the needed score property to use it in the template.
     *
     * @param notificationType that needs to be EXERCISE_SUBMISSION_ASSESSED
     * @param context that should be updated with the score property
     * @param exercise that holds the needed information: exercise -> studentParticipation -> results (this information was loaded in previous steps)
     * @param recipientStudent who will receive the email
     * @return the updated context
     */
    private void checkAndPrepareExerciseSubmissionAssessedCase(NotificationType notificationType, Context context, Exercise exercise, User recipientStudent) {
        if (notificationType.equals(EXERCISE_SUBMISSION_ASSESSED)) {
            StudentParticipation studentParticipation = exercise.getStudentParticipations().stream()
                    .filter(participation -> participation.getStudent().orElseThrow().equals(recipientStudent)).findFirst().orElseThrow();
            Double score = studentParticipation.findLatestResult().getScore();
            context.setVariable(ASSESSED_SCORE, score);
            context.setVariable(RELATIVE_SCORE, exercise.getMaxPoints() / score);
        }
    }

    @Async
    public void sendNotificationEmailForMultipleUsers(Notification notification, List<User> users, Object notificationSubject) {
        users.forEach(user -> sendNotificationEmail(notification, user, notificationSubject));
    }

    /// Weekly Summary Email

    /**
     * Sends an email based on a weekly summary
     *
     * @param user who is the recipient
     * @param exercises that will be used in the weekly summary
     */
    @Async
    public void sendWeeklySummaryEmail(User user, Set<Exercise> exercises) {
        log.debug("Sending weekly summary email to '{}'", user.getEmail());

        Locale locale = Locale.forLanguageTag(user.getLangKey());

        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(WEEKLY_SUMMARY_NEW_EXERCISES, exercises);

        context.setVariable(TIME_SERVICE, this.timeService);
        String subject = "Weekly Summary";

        context.setVariable(BASE_URL, artemisServerUrl);

        String content = templateEngine.process("mail/weeklySummary", context);

        sendEmail(user, subject, content, false, true);
    }
}
