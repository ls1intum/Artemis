package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.ExerciseOpenForPracticePayloadDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.NewAnnouncementPayloadDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownCustomLinkRendererService;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownCustomReferenceRendererService;

class CourseNotificationEmailServiceTest {

    private static final ZonedDateTime FIXED_CREATION_DATE = ZonedDateTime.parse("2025-01-15T10:00:00+01:00");

    private CourseNotificationEmailService courseNotificationEmailService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    MarkdownCustomLinkRendererService markdownCustomLinkRendererService;

    @Mock
    MarkdownCustomReferenceRendererService markdownCustomReferenceRendererService;

    @Captor
    private ArgumentCaptor<Context> contextCaptor;

    private URL serverUrl;

    @BeforeEach
    void setUp() throws MalformedURLException, URISyntaxException {
        MockitoAnnotations.openMocks(this);
        courseNotificationEmailService = new CourseNotificationEmailService(messageSource, templateEngine, mailSendingService, markdownCustomLinkRendererService,
                markdownCustomReferenceRendererService);
        serverUrl = new URI("https://example.org").toURL();

        ReflectionTestUtils.setField(courseNotificationEmailService, "artemisServerUrl", serverUrl);
        // sendEmailSync now reports whether the mail went out, and a boolean-returning mock answers false by default,
        // which would make every test here see a failed delivery. Lenient because several of these never reach the send.
        lenient().when(mailSendingService.sendEmailSync(any(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(true);
    }

    @Test
    void shouldSendNotificationToSingleRecipientWhenLocaleIsEnglish() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("ANNOUNCEMENT", 123L);

        when(messageSource.getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/ANNOUNCEMENT"), any(Context.class))).thenReturn("Test Content");

        courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("en")));
            verify(templateEngine).process(eq("mail/course_notification/ANNOUNCEMENT"), contextCaptor.capture());
            verify(mailSendingService).sendEmailSync(eq(expectedMailRecipient(recipient)), eq("Test Subject"), eq("Test Content"), eq(false), eq(true));

            Context capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getVariable("serverUrl")).isEqualTo(serverUrl);
            assertThat(capturedContext.getVariable("notificationType")).isEqualTo("ANNOUNCEMENT");
            assertThat(capturedContext.getVariable("recipient")).isEqualTo(CourseNotificationRecipientDTO.from(recipient));
            assertThat(capturedContext.getVariable("courseId")).isEqualTo(123L);
        });
    }

    @Test
    void shouldSendNotificationToMultipleRecipientsWhenTheyHaveDifferentLanguages() {
        var englishUser = createUser("english", "en");
        var germanUser = createUser("german", "de");

        CourseNotificationDTO notification = createNotification("ASSIGNMENT_RELEASED", 456L);

        when(messageSource.getMessage(eq("email.courseNotification.ASSIGNMENT_RELEASED.title"), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/ASSIGNMENT_RELEASED"), any(Context.class))).thenReturn("Test Content");

        courseNotificationEmailService.sendCourseNotification(notification,
                List.of(CourseNotificationRecipientDTO.from(englishUser), CourseNotificationRecipientDTO.from(germanUser)));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource, times(1)).getMessage(eq("email.courseNotification.ASSIGNMENT_RELEASED.title"), any(), eq(Locale.forLanguageTag("en")));
            verify(messageSource, times(1)).getMessage(eq("email.courseNotification.ASSIGNMENT_RELEASED.title"), any(), eq(Locale.forLanguageTag("de")));
            verify(templateEngine, times(2)).process(eq("mail/course_notification/ASSIGNMENT_RELEASED"), any(Context.class));
            verify(mailSendingService).sendEmailSync(eq(expectedMailRecipient(englishUser)), anyString(), anyString(), eq(false), eq(true));
            verify(mailSendingService).sendEmailSync(eq(expectedMailRecipient(germanUser)), anyString(), anyString(), eq(false), eq(true));
        });
    }

    @Test
    void shouldNotSendNotificationWhenRecipientListIsEmpty() {
        CourseNotificationDTO notification = createNotification("EXERCISE_RELEASED", 789L);

        courseNotificationEmailService.sendCourseNotification(notification, List.of());

        Awaitility.await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource, never()).getMessage(anyString(), any(), any(Locale.class));
            verify(templateEngine, never()).process(anyString(), any(Context.class));
            verify(mailSendingService, never()).sendEmailSync(any(), anyString(), anyString(), anyBoolean(), anyBoolean());
        });
    }

    /**
     * A recipient whose subject or template cannot be rendered is skipped so the others still get their mail, which is
     * the right behaviour but not a success. The returned future is what the feature usage analysis reads, so completing
     * it normally reported the e-mail channel as healthy while a missing locale key or template was silently dropping
     * mail, and the error rate would never have shown it.
     */
    /**
     * A send that reports it did not go out, because mail is not configured for this deployment or the message could not
     * be built or sent, is a delivery failure of this channel. It used to be invisible: the outcome was computed inside
     * MailSendingService and then discarded at its void boundary, so the channel reported success whatever SMTP did.
     */
    @Test
    void shouldCompleteExceptionallyWhenTheMailCouldNotBeSent() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("ANNOUNCEMENT", 123L);
        when(messageSource.getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/ANNOUNCEMENT"), any(Context.class))).thenReturn("Test Content");
        when(mailSendingService.sendEmailSync(any(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(false);

        var delivery = courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        assertThat(delivery).isCompletedExceptionally();
    }

    @Test
    void shouldCompleteExceptionallyWhenARecipientCouldNotBeRendered() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("UNKNOWN_TYPE", 123L);
        when(messageSource.getMessage(eq("email.courseNotification.UNKNOWN_TYPE.title"), any(), any(Locale.class))).thenThrow(new NoSuchMessageException("Message code not found"));

        var delivery = courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        assertThat(delivery).isCompletedExceptionally();
    }

    @Test
    void shouldCompleteNormallyWhenEveryRecipientWasRendered() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("ANNOUNCEMENT", 123L);
        // Rendering has to succeed for the send to be reached at all, and the send itself has to be stubbed: anyString()
        // does not match null, so an unrendered subject would silently miss the stub and report a failed delivery.
        when(messageSource.getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/ANNOUNCEMENT"), any(Context.class))).thenReturn("Test Content");

        var delivery = courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        // otherwise the flag is simply always set and the error rate is wrong in the other direction
        assertThat(delivery).isCompletedWithValue(null);
    }

    @Test
    void shouldNotSendEmailWhenSubjectTranslationIsMissing() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("UNKNOWN_TYPE", 123L);

        when(messageSource.getMessage(eq("email.courseNotification.UNKNOWN_TYPE.title"), any(), any(Locale.class))).thenThrow(new NoSuchMessageException("Message code not found"));

        courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.UNKNOWN_TYPE.title"), any(), any(Locale.class));
            verify(templateEngine, never()).process(anyString(), any(Context.class));
            verify(mailSendingService, never()).sendEmailSync(any(), anyString(), anyString(), anyBoolean(), anyBoolean());
        });
    }

    @Test
    void shouldNotSendEmailWhenTemplateProcessingFails() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("VALID_TYPE", 123L);

        when(messageSource.getMessage(eq("email.courseNotification.VALID_TYPE.title"), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/VALID_TYPE"), any(Context.class))).thenThrow(new TemplateProcessingException("Template not found"));

        courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.VALID_TYPE.title"), any(), any(Locale.class));
            verify(templateEngine).process(eq("mail/course_notification/VALID_TYPE"), any(Context.class));
            verify(mailSendingService, never()).sendEmailSync(any(), anyString(), anyString(), anyBoolean(), anyBoolean());
        });
    }

    @Test
    void shouldContinueProcessingOtherRecipientsWhenOneFails() {
        User user1 = createUser("user1", "en");
        User user2 = createUser("user2", "de");
        CourseNotificationDTO notification = createNotification("ANNOUNCEMENT", 123L);

        when(messageSource.getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("en"))))
                .thenThrow(new NoSuchMessageException("Message code not found"));

        when(messageSource.getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("de")))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/ANNOUNCEMENT"), any(Context.class))).thenReturn("Test Content");

        courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(user1), CourseNotificationRecipientDTO.from(user2)));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("en")));
            verify(messageSource).getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("de")));
            verify(templateEngine).process(eq("mail/course_notification/ANNOUNCEMENT"), any(Context.class));
            verify(mailSendingService, never()).sendEmailSync(eq(expectedMailRecipient(user1)), anyString(), anyString(), anyBoolean(), anyBoolean());
            verify(mailSendingService).sendEmailSync(eq(expectedMailRecipient(user2)), eq("Test Subject"), eq("Test Content"), eq(false), eq(true));
        });
    }

    @Test
    void shouldSetAllExpectedVariablesInTemplateContext() {
        var recipient = createUser("user1", "en");
        var creationDate = ZonedDateTime.now();
        var category = CourseNotificationCategory.COMMUNICATION;

        CourseNotificationDTO notification = new CourseNotificationDTO("DETAILED_NOTIFICATION", 1L, 123L, creationDate, category, "Test Course", null,
                new ExerciseOpenForPracticePayloadDTO(1L, "Test Exercise"), "/");

        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("Test Content");

        courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(templateEngine).process(anyString(), contextCaptor.capture());

            Context capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getVariable("serverUrl")).isEqualTo(serverUrl);
            assertThat(capturedContext.getVariable("notificationType")).isEqualTo("DETAILED_NOTIFICATION");
            assertThat(capturedContext.getVariable("recipient")).isEqualTo(CourseNotificationRecipientDTO.from(recipient));
            assertThat(capturedContext.getVariable("courseId")).isEqualTo(123L);
            // The template reads the values by name, so the payload is flattened into the context together with the
            // values every notification carries.
            @SuppressWarnings("unchecked")
            var contextParameters = (Map<String, Object>) capturedContext.getVariable("parameters");
            assertThat(contextParameters).containsEntry("exerciseTitle", "Test Exercise").containsEntry("exerciseId", 1L).containsEntry("courseTitle", "Test Course");
            assertThat(capturedContext.getVariable("creationDate")).isEqualTo(creationDate);
            assertThat(capturedContext.getVariable("category")).isEqualTo(category);
        });
    }

    /**
     * A single newline is a soft break in Markdown, and rendering it as a plain newline lets every mail client collapse
     * it into a space, so an announcement written over several lines arrived as one run-on line. The e-mail has to show
     * the break the same way the web client does, while a blank line still has to start a new paragraph rather than
     * turning into a second break.
     */
    @Test
    void shouldRenderSingleLineBreaksInMarkdownAsHtmlLineBreaks() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = new CourseNotificationDTO("newAnnouncementNotification", 1L, 123L, FIXED_CREATION_DATE, CourseNotificationCategory.COMMUNICATION,
                "Test Course", null, new NewAnnouncementPayloadDTO(1L, "Test Announcement", "first line\nsecond line\n\nnext paragraph", "Test Author", null, 2L, 3L), "/");

        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("Test Content");
        when(markdownCustomLinkRendererService.render(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(markdownCustomReferenceRendererService.render(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(templateEngine).process(anyString(), contextCaptor.capture());

            @SuppressWarnings("unchecked")
            var renderedParameters = (Map<String, Object>) contextCaptor.getValue().getVariable("parameters");
            assertThat((String) renderedParameters.get("postMarkdownContent")).isEqualToIgnoringWhitespace("<p>first line<br>second line</p><p>next paragraph</p>");
        });
    }

    @ParameterizedTest
    @CsvSource({ "QUIZ_RELEASED, email.courseNotification.QUIZ_RELEASED.title, mail/course_notification/QUIZ_RELEASED",
            "EXERCISE_DUE_SOON, email.courseNotification.EXERCISE_DUE_SOON.title, mail/course_notification/EXERCISE_DUE_SOON",
            "SUBMISSION_ASSESSED, email.courseNotification.SUBMISSION_ASSESSED.title, mail/course_notification/SUBMISSION_ASSESSED" })
    void shouldUseCorrectTemplatePathsBasedOnNotificationType(String notificationType, String expectedLocalePrefix, String expectedTemplatePath) {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification(notificationType, 123L);

        when(messageSource.getMessage(eq(expectedLocalePrefix), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq(expectedTemplatePath), any(Context.class))).thenReturn("Test Content");

        courseNotificationEmailService.sendCourseNotification(notification, List.of(CourseNotificationRecipientDTO.from(recipient)));

        // Assert
        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq(expectedLocalePrefix), any(), any(Locale.class));
            verify(templateEngine).process(eq(expectedTemplatePath), any(Context.class));
            verify(mailSendingService).sendEmailSync(eq(expectedMailRecipient(recipient)), eq("Test Subject"), eq("Test Content"), eq(false), eq(true));
        });
    }

    private User createUser(String login, String langKey) {
        User user = new User();
        user.setLogin(login);
        user.setLangKey(langKey);
        return user;
    }

    /**
     * Mirrors the {@code CourseNotificationRecipientDTO -> MailRecipientDTO} conversion performed by
     * {@code CourseNotificationEmailService}: course-notification emails never carry an activation or reset key, so those
     * are always {@code null}. Building the expected DTO this way keeps the assertions independent of whether the
     * {@link User} fixture happens to set an activation/reset key.
     */
    private static MailRecipientDTO expectedMailRecipient(User user) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), null, null);
    }

    private CourseNotificationDTO createNotification(String notificationType, Long courseId) {
        return new CourseNotificationDTO(notificationType, 1L, courseId, ZonedDateTime.now(), CourseNotificationCategory.COMMUNICATION, "Test Course", null,
                new ExerciseOpenForPracticePayloadDTO(1L, "testValue"), "/");
    }
}
