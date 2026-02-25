package de.tum.cit.aet.artemis.communication.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import tech.jhipster.config.JHipsterProperties;

/**
 * Integration tests for course notification email templates.
 * <p>
 * Replicates the context setup of {@code CourseNotificationEmailService.sendCourseNotification()}
 * to verify that each template renders correctly with proper i18n resolution and SMTP delivery.
 */
@Execution(ExecutionMode.SAME_THREAD)
class CourseNotificationEmailIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final int EMAIL_TIMEOUT_MS = 5000;

    private static final long COURSE_ID = 42L;

    private static final URL SERVER_URL;

    static {
        try {
            SERVER_URL = new URL("http://localhost:9000");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Autowired
    private SpringTemplateEngine templateEngine;

    private MailSendingService testMailSendingService;

    private SpringTemplateEngine testTemplateEngine;

    private ReloadableResourceBundleMessageSource mainMessageSource;

    private User recipient;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.reset();

        var greenMailSender = new JavaMailSenderImpl();
        greenMailSender.setHost("127.0.0.1");
        greenMailSender.setPort(greenMail.getSmtp().getPort());

        mainMessageSource = new ReloadableResourceBundleMessageSource();
        mainMessageSource.setBasename("file:src/main/resources/i18n/messages");
        mainMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

        testTemplateEngine = new SpringTemplateEngine();
        templateEngine.getTemplateResolvers().forEach(testTemplateEngine::addTemplateResolver);
        testTemplateEngine.setMessageSource(mainMessageSource);

        var mailEnabledProperties = new JHipsterProperties();
        mailEnabledProperties.getMail().setFrom("test@greenmail.test");

        testMailSendingService = new MailSendingService(mailEnabledProperties, greenMailSender, mainMessageSource, testTemplateEngine);
        ReflectionTestUtils.setField(testMailSendingService, "artemisServerUrl", SERVER_URL);

        recipient = new User();
        recipient.setEmail("user@greenmail.test");
        recipient.setLangKey("en");
        recipient.setLogin("testuser");
        recipient.setFirstName("Jane");
        recipient.setLastName("Doe");
    }

    // -- New exercise notification --

    @Test
    void newExerciseNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("exerciseTitle", (Object) "Sorting Algorithms", "courseTitle", "Intro to CS", "difficulty", "MEDIUM", "numberOfPoints", "100");

        sendCourseNotificationEmail("newExerciseNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Sorting Algorithms");
        assertThat(body).contains("Intro to CS");
        assertThat(body).contains("100");
    }

    @Test
    void newExerciseNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("exerciseTitle", (Object) "Sortieralgorithmen", "courseTitle", "Einf. in die Informatik", "difficulty", "HARD", "numberOfPoints", "50");

        sendCourseNotificationEmail("newExerciseNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Sortieralgorithmen");
        assertThat(body).contains("Einf. in die Informatik");
    }

    // -- New announcement notification --

    @Test
    void newAnnouncementNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("postTitle", (Object) "Important Update", "courseTitle", "Software Engineering", "postMarkdownContent", "Please read the updated guidelines.");

        sendCourseNotificationEmail("newAnnouncementNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Important Update");
        assertThat(body).contains("Software Engineering");
        assertThat(body).contains("Please read the updated guidelines.");
    }

    @Test
    void newAnnouncementNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("postTitle", (Object) "Wichtige Mitteilung", "courseTitle", "Softwaretechnik", "postMarkdownContent", "Bitte lest die aktualisierten Richtlinien.");

        sendCourseNotificationEmail("newAnnouncementNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Wichtige Mitteilung");
        assertThat(body).contains("Softwaretechnik");
    }

    // -- Exercise assessed notification --

    @Test
    void exerciseAssessedNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("exerciseTitle", (Object) "Binary Search", "courseTitle", "Algorithms", "numberOfPoints", "85", "score", "85%");

        sendCourseNotificationEmail("exerciseAssessedNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Binary Search");
        assertThat(body).contains("Algorithms");
        assertThat(body).contains("85");
    }

    @Test
    void exerciseAssessedNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("exerciseTitle", (Object) "Binärsuche", "courseTitle", "Algorithmen", "numberOfPoints", "90", "score", "90%");

        sendCourseNotificationEmail("exerciseAssessedNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Binärsuche");
        assertThat(body).contains("Algorithmen");
    }

    // -- Exercise open for practice notification --

    @Test
    void exerciseOpenForPracticeNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("exerciseTitle", (Object) "Graph Traversal", "courseTitle", "Data Structures");

        sendCourseNotificationEmail("exerciseOpenForPracticeNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Graph Traversal");
        assertThat(body).contains("Data Structures");
    }

    @Test
    void exerciseOpenForPracticeNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("exerciseTitle", (Object) "Graphtraversierung", "courseTitle", "Datenstrukturen");

        sendCourseNotificationEmail("exerciseOpenForPracticeNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Graphtraversierung");
        assertThat(body).contains("Datenstrukturen");
    }

    // -- Duplicate test case notification --

    @Test
    void duplicateTestCaseNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("exerciseTitle", (Object) "Merge Sort", "courseTitle", "Algorithms II");

        sendCourseNotificationEmail("duplicateTestCaseNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Merge Sort");
        assertThat(body).contains("Algorithms II");
    }

    @Test
    void duplicateTestCaseNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("exerciseTitle", (Object) "Mergesort", "courseTitle", "Algorithmen II");

        sendCourseNotificationEmail("duplicateTestCaseNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Mergesort");
        assertThat(body).contains("Algorithmen II");
    }

    // -- New mention notification --

    @Test
    void newMentionNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("courseTitle", (Object) "Operating Systems", "postMarkdownContent", "Hey @testuser, can you check this?");

        sendCourseNotificationEmail("newMentionNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Operating Systems");
        assertThat(body).contains("Hey @testuser, can you check this?");
    }

    @Test
    void newMentionNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("courseTitle", (Object) "Betriebssysteme", "postMarkdownContent", "Hallo @testuser, kannst du das prüfen?");

        sendCourseNotificationEmail("newMentionNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Betriebssysteme");
    }

    // -- New plagiarism case notification --

    @Test
    void newPlagiarismCaseNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("exerciseTitle", (Object) "Hash Tables", "postMarkdownContent", "Suspicious similarity detected.");

        sendCourseNotificationEmail("newPlagiarismCaseNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Hash Tables");
        assertThat(body).contains("Suspicious similarity detected.");
    }

    @Test
    void newPlagiarismCaseNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("exerciseTitle", (Object) "Hashtabellen", "postMarkdownContent", "Verdächtige Ähnlichkeit festgestellt.");

        sendCourseNotificationEmail("newPlagiarismCaseNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Hashtabellen");
    }

    // -- New CPC plagiarism case notification --

    @Test
    void newCpcPlagiarismCaseNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("exerciseTitle", (Object) "Linked Lists", "postMarkdownContent", "CPC plagiarism detected.");

        sendCourseNotificationEmail("newCpcPlagiarismCaseNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Linked Lists");
        assertThat(body).contains("CPC plagiarism detected.");
    }

    @Test
    void newCpcPlagiarismCaseNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("exerciseTitle", (Object) "Verkettete Listen", "postMarkdownContent", "CPC Plagiat festgestellt.");

        sendCourseNotificationEmail("newCpcPlagiarismCaseNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Verkettete Listen");
    }

    // -- Plagiarism case verdict notification --

    @Test
    void plagiarismCaseVerdictNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("exerciseTitle", (Object) "Stacks and Queues", "verdict", "PLAGIARISM");

        sendCourseNotificationEmail("plagiarismCaseVerdictNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Stacks and Queues");
    }

    @Test
    void plagiarismCaseVerdictNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("exerciseTitle", (Object) "Stapel und Warteschlangen", "verdict", "WARNING");

        sendCourseNotificationEmail("plagiarismCaseVerdictNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Stapel und Warteschlangen");
    }

    // -- Registered to tutorial group notification --

    @Test
    void registeredToTutorialGroupNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("groupTitle", (Object) "Tutorial Group A", "courseTitle", "Linear Algebra");

        sendCourseNotificationEmail("registeredToTutorialGroupNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Tutorial Group A");
        assertThat(body).contains("Linear Algebra");
    }

    @Test
    void registeredToTutorialGroupNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("groupTitle", (Object) "Übungsgruppe A", "courseTitle", "Lineare Algebra");

        sendCourseNotificationEmail("registeredToTutorialGroupNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Übungsgruppe A");
        assertThat(body).contains("Lineare Algebra");
    }

    // -- Deregistered from tutorial group notification --

    @Test
    void deregisteredFromTutorialGroupNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("groupTitle", (Object) "Tutorial Group B", "courseTitle", "Calculus");

        sendCourseNotificationEmail("deregisteredFromTutorialGroupNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Tutorial Group B");
        assertThat(body).contains("Calculus");
    }

    @Test
    void deregisteredFromTutorialGroupNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("groupTitle", (Object) "Übungsgruppe B", "courseTitle", "Analysis");

        sendCourseNotificationEmail("deregisteredFromTutorialGroupNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Übungsgruppe B");
        assertThat(body).contains("Analysis");
    }

    // -- Tutorial group assigned notification --

    @Test
    void tutorialGroupAssignedNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("groupTitle", (Object) "Tutorial Group C", "courseTitle", "Discrete Math");

        sendCourseNotificationEmail("tutorialGroupAssignedNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Tutorial Group C");
        assertThat(body).contains("Discrete Math");
    }

    @Test
    void tutorialGroupAssignedNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("groupTitle", (Object) "Übungsgruppe C", "courseTitle", "Diskrete Mathematik");

        sendCourseNotificationEmail("tutorialGroupAssignedNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Übungsgruppe C");
        assertThat(body).contains("Diskrete Mathematik");
    }

    // -- Tutorial group unassigned notification --

    @Test
    void tutorialGroupUnassignedNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("groupTitle", (Object) "Tutorial Group D", "courseTitle", "Statistics");

        sendCourseNotificationEmail("tutorialGroupUnassignedNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Tutorial Group D");
        assertThat(body).contains("Statistics");
    }

    @Test
    void tutorialGroupUnassignedNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("groupTitle", (Object) "Übungsgruppe D", "courseTitle", "Statistik");

        sendCourseNotificationEmail("tutorialGroupUnassignedNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Übungsgruppe D");
        assertThat(body).contains("Statistik");
    }

    // -- Tutorial group deleted notification --

    @Test
    void tutorialGroupDeletedNotification_shouldRenderAndDeliverInEnglish() throws Exception {
        var params = Map.of("groupTitle", (Object) "Tutorial Group E", "courseTitle", "Physics");

        sendCourseNotificationEmail("tutorialGroupDeletedNotification", params, "en");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Tutorial Group E");
        assertThat(body).contains("Physics");
    }

    @Test
    void tutorialGroupDeletedNotification_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var params = Map.of("groupTitle", (Object) "Übungsgruppe E", "courseTitle", "Physik");

        sendCourseNotificationEmail("tutorialGroupDeletedNotification", params, "de");

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Übungsgruppe E");
        assertThat(body).contains("Physik");
    }

    // -- Helper methods --

    /**
     * Replicates the context setup and email sending logic from {@code CourseNotificationEmailService.sendCourseNotification()}.
     * Sets up all required Thymeleaf context variables, processes the template, resolves the subject, and sends via SMTP.
     */
    private void sendCourseNotificationEmail(String notificationType, Map<String, Object> parameters, String langKey) {
        Locale locale = Locale.forLanguageTag(langKey);
        Context context = new Context(locale);
        context.setVariable("serverUrl", SERVER_URL);
        context.setVariable("notificationType", notificationType);
        context.setVariable("recipient", recipient);
        context.setVariable("courseId", COURSE_ID);
        context.setVariable("parameters", new HashMap<>(parameters));
        context.setVariable("creationDate", ZonedDateTime.now());
        context.setVariable("category", CourseNotificationCategory.GENERAL);
        context.setVariable("notificationUrl", SERVER_URL + "/courses/" + COURSE_ID);

        String content = testTemplateEngine.process("mail/course_notification/" + notificationType, context);
        String subject = mainMessageSource.getMessage("email.courseNotification." + notificationType + ".title", null, locale);
        testMailSendingService.sendEmailSync(recipient, subject, content, false, true);
    }

    private String getDeliveredEmailBody() throws Exception {
        assertThat(greenMail.waitForIncomingEmail(EMAIL_TIMEOUT_MS, 1)).isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0].getContent().toString();
    }
}
