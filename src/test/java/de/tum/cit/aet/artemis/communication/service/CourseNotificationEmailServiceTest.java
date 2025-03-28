package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Collections;
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

import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownCustomLinkRendererService;
import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownCustomReferenceRendererService;
import de.tum.cit.aet.artemis.core.domain.User;

class CourseNotificationEmailServiceTest {

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
    void setUp() throws MalformedURLException {
        MockitoAnnotations.openMocks(this);
        courseNotificationEmailService = new CourseNotificationEmailService(messageSource, templateEngine, mailSendingService, markdownCustomLinkRendererService,
                markdownCustomReferenceRendererService);
        serverUrl = new URL("https://example.org");

        ReflectionTestUtils.setField(courseNotificationEmailService, "artemisServerUrl", serverUrl);
    }

    @Test
    void shouldSendNotificationToSingleRecipientWhenLocaleIsEnglish() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("ANNOUNCEMENT", 123L);

        when(messageSource.getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/ANNOUNCEMENT"), any(Context.class))).thenReturn("Test Content");

        courseNotificationEmailService.sendCourseNotification(notification, List.of(recipient));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("en")));
            verify(templateEngine).process(eq("mail/course_notification/ANNOUNCEMENT"), contextCaptor.capture());
            verify(mailSendingService).sendEmail(eq(recipient), eq("Test Subject"), eq("Test Content"), eq(false), eq(true));

            Context capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getVariable("serverUrl")).isEqualTo(serverUrl);
            assertThat(capturedContext.getVariable("notificationType")).isEqualTo("ANNOUNCEMENT");
            assertThat(capturedContext.getVariable("recipient")).isEqualTo(recipient);
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

        courseNotificationEmailService.sendCourseNotification(notification, List.of(englishUser, germanUser));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource, times(1)).getMessage(eq("email.courseNotification.ASSIGNMENT_RELEASED.title"), any(), eq(Locale.forLanguageTag("en")));
            verify(messageSource, times(1)).getMessage(eq("email.courseNotification.ASSIGNMENT_RELEASED.title"), any(), eq(Locale.forLanguageTag("de")));
            verify(templateEngine, times(2)).process(eq("mail/course_notification/ASSIGNMENT_RELEASED"), any(Context.class));
            verify(mailSendingService).sendEmail(eq(englishUser), anyString(), anyString(), eq(false), eq(true));
            verify(mailSendingService).sendEmail(eq(germanUser), anyString(), anyString(), eq(false), eq(true));
        });
    }

    @Test
    void shouldNotSendNotificationWhenRecipientListIsEmpty() {
        CourseNotificationDTO notification = createNotification("EXERCISE_RELEASED", 789L);

        courseNotificationEmailService.sendCourseNotification(notification, Collections.emptyList());

        Awaitility.await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource, never()).getMessage(anyString(), any(), any(Locale.class));
            verify(templateEngine, never()).process(anyString(), any(Context.class));
            verify(mailSendingService, never()).sendEmail(any(), anyString(), anyString(), anyBoolean(), anyBoolean());
        });
    }

    @Test
    void shouldNotSendEmailWhenSubjectTranslationIsMissing() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("UNKNOWN_TYPE", 123L);

        when(messageSource.getMessage(eq("email.courseNotification.UNKNOWN_TYPE.title"), any(), any(Locale.class))).thenThrow(new NoSuchMessageException("Message code not found"));

        courseNotificationEmailService.sendCourseNotification(notification, List.of(recipient));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.UNKNOWN_TYPE.title"), any(), any(Locale.class));
            verify(templateEngine, never()).process(anyString(), any(Context.class));
            verify(mailSendingService, never()).sendEmail(any(), anyString(), anyString(), anyBoolean(), anyBoolean());
        });
    }

    @Test
    void shouldNotSendEmailWhenTemplateProcessingFails() {
        User recipient = createUser("user1", "en");
        CourseNotificationDTO notification = createNotification("VALID_TYPE", 123L);

        when(messageSource.getMessage(eq("email.courseNotification.VALID_TYPE.title"), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(eq("mail/course_notification/VALID_TYPE"), any(Context.class))).thenThrow(new TemplateProcessingException("Template not found"));

        courseNotificationEmailService.sendCourseNotification(notification, List.of(recipient));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.VALID_TYPE.title"), any(), any(Locale.class));
            verify(templateEngine).process(eq("mail/course_notification/VALID_TYPE"), any(Context.class));
            verify(mailSendingService, never()).sendEmail(any(), anyString(), anyString(), anyBoolean(), anyBoolean());
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

        courseNotificationEmailService.sendCourseNotification(notification, List.of(user1, user2));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("en")));
            verify(messageSource).getMessage(eq("email.courseNotification.ANNOUNCEMENT.title"), any(), eq(Locale.forLanguageTag("de")));
            verify(templateEngine).process(eq("mail/course_notification/ANNOUNCEMENT"), any(Context.class));
            verify(mailSendingService, never()).sendEmail(eq(user1), anyString(), anyString(), anyBoolean(), anyBoolean());
            verify(mailSendingService).sendEmail(eq(user2), eq("Test Subject"), eq("Test Content"), eq(false), eq(true));
        });
    }

    @Test
    void shouldSetAllExpectedVariablesInTemplateContext() {
        var recipient = createUser("user1", "en");
        Map<String, Object> parameters = Map.of("param1", "value1", "param2", "value2");
        var creationDate = ZonedDateTime.now();
        var category = CourseNotificationCategory.COMMUNICATION;

        CourseNotificationDTO notification = new CourseNotificationDTO("DETAILED_NOTIFICATION", 1L, 123L, creationDate, category, parameters, "/");

        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Test Subject");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("Test Content");

        courseNotificationEmailService.sendCourseNotification(notification, List.of(recipient));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(templateEngine).process(anyString(), contextCaptor.capture());

            Context capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getVariable("serverUrl")).isEqualTo(serverUrl);
            assertThat(capturedContext.getVariable("notificationType")).isEqualTo("DETAILED_NOTIFICATION");
            assertThat(capturedContext.getVariable("recipient")).isEqualTo(recipient);
            assertThat(capturedContext.getVariable("courseId")).isEqualTo(123L);
            assertThat(capturedContext.getVariable("parameters")).isEqualTo(parameters);
            assertThat(capturedContext.getVariable("creationDate")).isEqualTo(creationDate);
            assertThat(capturedContext.getVariable("category")).isEqualTo(category);
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

        courseNotificationEmailService.sendCourseNotification(notification, List.of(recipient));

        // Assert
        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(messageSource).getMessage(eq(expectedLocalePrefix), any(), any(Locale.class));
            verify(templateEngine).process(eq(expectedTemplatePath), any(Context.class));
            verify(mailSendingService).sendEmail(eq(recipient), eq("Test Subject"), eq("Test Content"), eq(false), eq(true));
        });
    }

    private User createUser(String login, String langKey) {
        User user = new User();
        user.setLogin(login);
        user.setLangKey(langKey);
        return user;
    }

    private CourseNotificationDTO createNotification(String notificationType, Long courseId) {
        return new CourseNotificationDTO(notificationType, 1L, courseId, ZonedDateTime.now(), CourseNotificationCategory.COMMUNICATION, Map.of("testParam", "testValue"), "/");
    }
}
