package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_SUBMISSION_ASSESSED;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.extractNotificationUrl;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.activation_mail.ActivationMailRecipientDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_failed_mail.DataExportFailedContentDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_failed_mail.DataExportFailedMailAdminRecipientDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_successful_mail.DataExportSuccessfulContentsDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_successful_mail.DataExportSuccessfulMailAdminRecipientDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.notifications.NotificationMailRecipientDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.password_reset_mail.PasswordResetRecipientDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.saml2_set_password_mail.SAML2SetPasswordMailRecipientDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.weekly_summary_mail.WeeklySummaryMailContentDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.weekly_summary_mail.WeeklySummaryMailRecipientDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.TimeService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * Service for preparing and sending emails.
 * <p>
 * We use the MailSendingService to send emails asynchronously.
 */
@Profile(PROFILE_CORE)
@Service
public class MailService implements InstantNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String USER = "user";

    private static final String BASE_URL = "baseUrl";

    private static final String DATA_EXPORT_CONTENTS = "dataExportContents";

    private static final String DATA_EXPORT_FAILED_CONTENT = "dataExportFailedContent";

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    private final TimeService timeService;

    private final MailSendingService mailSendingService;

    private final List<MarkdownCustomRendererService> markdownCustomRendererServices;

    // notification related variables

    private static final String NOTIFICATION = "notification";

    private static final String NOTIFICATION_SUBJECT = "notificationSubject";

    private static final String NOTIFICATION_URL = "notificationUrl";

    private static final String EXERCISE_TYPE = "exerciseType";

    private static final String PLAGIARISM_VERDICT = "plagiarismVerdict";

    private static final String ASSESSED_SCORE = "assessedScore";

    private static final String RELATIVE_SCORE = "relativeScore";

    private static final String NOTIFICATION_TYPE = "notificationType";

    // time related variables
    private static final String TIME_SERVICE = "timeService";

    // weekly summary related variables
    private static final String WEEKLY_SUMMARY_CONTENT = "weeklySummaryContent";

    private final HashMap<Long, String> renderedPosts;

    public MailService(MessageSource messageSource, SpringTemplateEngine templateEngine, TimeService timeService, MailSendingService mailSendingService,
            MarkdownCustomLinkRendererService markdownCustomLinkRendererService, MarkdownCustomReferenceRendererService markdownCustomReferenceRendererService) {
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.timeService = timeService;
        this.mailSendingService = mailSendingService;
        markdownCustomRendererServices = List.of(markdownCustomLinkRendererService, markdownCustomReferenceRendererService);
        renderedPosts = new HashMap<>();
    }

    /**
     * Sends a predefined mail based on a template
     *
     * @param recipient    The receiver of the mail
     * @param templateName The name of the template
     * @param titleKey     The key mapping the title for the subject of the mail
     */
    private void sendEmailFromTemplate(IMailRecipientUserDTO recipient, String templateName, String titleKey) {
        Locale locale = Locale.forLanguageTag(recipient.langKey());
        Context context = createBaseContext(recipient, locale);
        prepareTemplateAndSendEmail(recipient, templateName, titleKey, context);
    }

    /**
     * Sends an email to a user (the internal admin user) about a failed data export creation.
     *
     * @param recipientAdmin        the admin user
     * @param templateName          the name of the email template
     * @param titleKey              the subject of the email
     * @param exportFailCaseContent the relevant information of the failed data export
     */
    private void sendDataExportFailedEmailForAdmin(IMailRecipientUserDTO recipientAdmin, String templateName, String titleKey, DataExportFailedContentDTO exportFailCaseContent) {
        Locale locale = Locale.forLanguageTag(recipientAdmin.langKey());
        Context context = createBaseContext(recipientAdmin, locale);
        context.setVariable(DATA_EXPORT_FAILED_CONTENT, exportFailCaseContent);
        prepareTemplateAndSendEmailWithArgumentInSubject(recipientAdmin, templateName, titleKey, exportFailCaseContent.exportUsername(), context);
    }

    private void sendSuccessfulDataExportsEmailToAdmin(IMailRecipientUserDTO recipientAdmin, String templateName, String titleKey,
            DataExportSuccessfulContentsDTO dataExportContents) {
        Locale locale = Locale.forLanguageTag(recipientAdmin.langKey());
        Context context = createBaseContext(recipientAdmin, locale);
        context.setVariable(DATA_EXPORT_CONTENTS, dataExportContents);
        prepareTemplateAndSendEmail(recipientAdmin, templateName, titleKey, context);
    }

    private void prepareTemplateAndSendEmail(IMailRecipientUserDTO recipient, String templateName, String titleKey, Context context) {
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, context.getLocale());
        mailSendingService.sendEmail(recipient, subject, content, false, true);
    }

    private void prepareTemplateAndSendEmailWithArgumentInSubject(IMailRecipientUserDTO recipient, String templateName, String titleKey, String argument, Context context) {
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, new Object[] { argument }, context.getLocale());
        mailSendingService.sendEmail(recipient, subject, content, false, true);
    }

    /**
     * Creates a base context for the email
     *
     * @param recipient DTO representing the user in a given use-case
     * @param locale    language preference of the user
     */
    private Context createBaseContext(IMailRecipientUserDTO recipient, Locale locale) {
        Context context = new Context(locale);
        context.setVariable(USER, recipient);
        context.setVariable(BASE_URL, artemisServerUrl);
        return context;
    }

    public void sendActivationEmail(User user) {
        log.debug("Sending activation email to '{}'", user.getEmail());
        sendEmailFromTemplate(ActivationMailRecipientDTO.of(user), "mail/activationEmail", "email.activation.title");
    }

    public void sendPasswordResetMail(User user) {
        log.debug("Sending password reset email to '{}'", user.getEmail());
        sendEmailFromTemplate(PasswordResetRecipientDTO.of(user), "mail/passwordResetEmail", "email.reset.title");
    }

    public void sendSAML2SetPasswordMail(User user) {
        log.debug("Sending SAML2 set password email to '{}'", user.getEmail());
        sendEmailFromTemplate(SAML2SetPasswordMailRecipientDTO.of(user), "mail/samlSetPasswordEmail", "email.saml.title");
    }

    public void sendDataExportFailedEmailToAdmin(User admin, DataExport dataExport, Exception reason) {
        log.debug("Sending data export failed email to admin email address '{}'", admin.getEmail());
        sendDataExportFailedEmailForAdmin(DataExportFailedMailAdminRecipientDTO.of(admin), "mail/dataExportFailedAdminEmail", "email.dataExportFailedAdmin.title",
                DataExportFailedContentDTO.of(reason, dataExport));
    }

    public void sendSuccessfulDataExportsEmailToAdmin(User admin, Set<DataExport> dataExports) {
        log.debug("Sending successful creation of data exports email to admin email address '{}'", admin.getEmail());
        sendSuccessfulDataExportsEmailToAdmin(DataExportSuccessfulMailAdminRecipientDTO.of(admin), "mail/successfulDataExportsAdminEmail",
                "email.successfulDataExportCreationsAdmin.title", DataExportSuccessfulContentsDTO.of(dataExports));
    }

    // notification related

    /**
     * Sets the context and subject for the case that the notificationSubject is a Post
     *
     * @param notificationSubject which has to be a Post
     * @param locale              used for translations
     * @return the modified subject of the email
     */
    private String createAnnouncementText(Object notificationSubject, Locale locale) {
        // Translation that can not be done via i18n Resource Bundle (for Thymeleaf) but has to be set in this service via Java
        String newAnnouncementString = locale.toString().equals("en") ? "New announcement \"%s\" in course \"%s\"" : "Neue Ankündigung \"%s\" im Kurs \"%s\"";
        String postTitle = ((Post) notificationSubject).getTitle();
        String courseTitle = ((Post) notificationSubject).getConversation().getCourse().getTitle();

        return String.format(newAnnouncementString, postTitle, courseTitle);
    }

    /**
     * Sends a notification based email to one user
     *
     * @param notification        which properties are used to create the email
     * @param users               who should be contacted
     * @param notificationSubject that is used to provide further information (e.g. exercise, attachment, post, etc.)
     */
    @Override
    @Async
    public void sendNotification(Notification notification, Set<User> users, Object notificationSubject) {
        // TODO: we should track how many emails could not be sent and notify the instructors in case of announcements or other important notifications
        users.forEach(user -> {
            try {
                sendNotification(notification, user, notificationSubject);
            }
            catch (Exception ex) {
                // Note: we should not rethrow the exception here, as this would prevent sending out other emails in case multiple users are affected
                log.error("Error while sending notification email to user '{}'", user.getLogin(), ex);
            }
        });
    }

    /**
     * Sends a notification based email to one user
     *
     * @param notification        which properties are used to create the email
     * @param user                who should be contacted
     * @param notificationSubject that is used to provide further information (e.g. exercise, attachment, post, etc.)
     */
    @Override
    public void sendNotification(Notification notification, User user, Object notificationSubject) {
        NotificationType notificationType = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());
        log.debug("Sending '{}' notification email to '{}'", notificationType.name(), user.getEmail());

        String localeKey = user.getLangKey();
        if (localeKey == null) {
            log.error("The user '{}' object has no language key defined. This can happen if you do not load the user object from the database but take it straight from the client",
                    user.getLogin());
            // use the default locale
            localeKey = "en";
        }

        Locale locale = Locale.forLanguageTag(localeKey);

        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(NOTIFICATION, notification);
        context.setVariable(NOTIFICATION_SUBJECT, notificationSubject);

        context.setVariable(TIME_SERVICE, this.timeService);
        String subject = messageSource.getMessage(notification.getTitle(), null, context.getLocale());

        if (notificationSubject instanceof Exercise exercise) {
            context.setVariable(EXERCISE_TYPE, exercise.getExerciseType());
            checkAndPrepareExerciseSubmissionAssessedCase(notificationType, context, exercise, user);
        }
        if (notificationSubject instanceof PlagiarismCase plagiarismCase) {
            subject = setPlagiarismContextAndSubject(context, notificationType, notification, plagiarismCase);
        }

        if (notificationSubject instanceof SingleUserNotificationService.TutorialGroupNotificationSubject tutorialGroupNotificationSubject) {
            setContextForTutorialGroupNotifications(context, notificationType, tutorialGroupNotificationSubject);
        }

        if (notificationSubject instanceof Post post) {
            // posts use a different mechanism for the url
            context.setVariable(NOTIFICATION_URL, extractNotificationUrl(post, artemisServerUrl.toString()));
            subject = createAnnouncementText(notificationSubject, locale);

            // Render markdown content of post to html
            try {
                String renderedPostContent;

                // To avoid having to re-render the same post multiple times we store it in a hash map
                if (renderedPosts.containsKey(post.getId())) {
                    renderedPostContent = renderedPosts.get(post.getId());
                }
                else {
                    Parser parser = Parser.builder().build();
                    HtmlRenderer renderer = HtmlRenderer.builder()
                            .attributeProviderFactory(attributeContext -> new MarkdownRelativeToAbsolutePathAttributeProvider(artemisServerUrl.toString()))
                            .nodeRendererFactory(new MarkdownImageBlockRendererFactory(artemisServerUrl.toString())).build();
                    String postContent = post.getContent();
                    renderedPostContent = markdownCustomRendererServices.stream().reduce(renderer.render(parser.parse(postContent)), (s, service) -> service.render(s),
                            (s1, s2) -> s2);
                    if (post.getId() != null) {
                        renderedPosts.put(post.getId(), renderedPostContent);
                    }
                }

                post.setContent(renderedPostContent);
            }
            catch (Exception e) {
                // In case something goes wrong, leave content of post as-is
                log.error("Error while parsing post content", e);
            }
        }
        else {
            context.setVariable(NOTIFICATION_URL, extractNotificationUrl(notification, artemisServerUrl.toString()));
        }
        context.setVariable(BASE_URL, artemisServerUrl);

        String content = createContentForNotificationEmailByType(notificationType, context);
        mailSendingService.sendEmail(NotificationMailRecipientDTO.of(user), subject, content, false, true);
    }

    /**
     * Sets the context and subject for the case that the notificationSubject is a PlagiarismCase
     *
     * @param context          the context of the email template
     * @param notificationType the notification type of which the email to be sent
     * @param notification     the object which contains the notification title
     * @param plagiarismCase   the plagiarism case for which the email to be sent
     * @return the modified subject of the email
     */
    private String setPlagiarismContextAndSubject(Context context, NotificationType notificationType, Notification notification, PlagiarismCase plagiarismCase) {
        if (notificationType == NotificationType.NEW_PLAGIARISM_CASE_STUDENT) {
            Exercise exercise = plagiarismCase.getExercise();
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            return messageSource.getMessage("email.plagiarism.title", new Object[] { exercise.getTitle(), course.getTitle() }, context.getLocale());
        }
        if (notificationType == NotificationType.NEW_CPC_PLAGIARISM_CASE_STUDENT) {
            Exercise exercise = plagiarismCase.getExercise();
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            return messageSource.getMessage("email.plagiarism.cpc.title", new Object[] { exercise.getTitle(), course.getTitle() }, context.getLocale());
        }
        if (notificationType == NotificationType.PLAGIARISM_CASE_VERDICT_STUDENT) {
            context.setVariable(PLAGIARISM_VERDICT, plagiarismCase.getVerdict());
            return messageSource.getMessage("artemisApp.singleUserNotification.title.plagiarismCaseVerdictStudent", new Object[] {}, context.getLocale());
        }
        return notification.getTitle();
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
            case NEW_PLAGIARISM_CASE_STUDENT, NEW_CPC_PLAGIARISM_CASE_STUDENT -> templateEngine.process("mail/notification/plagiarismCaseEmail", context);
            case PLAGIARISM_CASE_VERDICT_STUDENT -> templateEngine.process("mail/notification/plagiarismVerdictEmail", context);
            case TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR,
                    TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_UNASSIGNED ->
                templateEngine.process("mail/notification/tutorialGroupBasicEmail", context);
            case TUTORIAL_GROUP_DELETED -> templateEngine.process("mail/notification/tutorialGroupDeletedEmail", context);
            case TUTORIAL_GROUP_UPDATED -> templateEngine.process("mail/notification/tutorialGroupUpdatedEmail", context);
            case DATA_EXPORT_CREATED -> templateEngine.process("mail/notification/dataExportCreatedEmail", context);
            case DATA_EXPORT_FAILED -> templateEngine.process("mail/notification/dataExportFailedEmail", context);
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        };
    }

    /**
     * Auxiliary method for EXERCISE_SUBMISSION_ASSESSED case to load the needed score property to use it in the template.
     *
     * @param notificationType that needs to be EXERCISE_SUBMISSION_ASSESSED
     * @param context          that should be updated with the score property
     * @param exercise         that holds the needed information: exercise -> studentParticipation -> results (this information was loaded in previous steps)
     * @param recipientStudent who will receive the email
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

    /**
     * Sends an email based on a weekly summary
     *
     * @param user      who is the recipient
     * @param exercises that will be used in the weekly summary
     */
    public void sendWeeklySummaryEmail(User user, Set<Exercise> exercises) {
        log.debug("Sending weekly summary email to '{}'", user.getEmail());

        Locale locale = Locale.forLanguageTag(user.getLangKey());

        Context context = new Context(locale);
        context.setVariable(BASE_URL, artemisServerUrl);
        context.setVariable(USER, WeeklySummaryMailRecipientDTO.of(user));
        context.setVariable(WEEKLY_SUMMARY_CONTENT, WeeklySummaryMailContentDTO.of(exercises, timeService));

        String subject = "Weekly Summary";
        String content = templateEngine.process("mail/weeklySummary", context);
        mailSendingService.sendEmail(WeeklySummaryMailRecipientDTO.of(user), subject, content, false, true);
    }
}
