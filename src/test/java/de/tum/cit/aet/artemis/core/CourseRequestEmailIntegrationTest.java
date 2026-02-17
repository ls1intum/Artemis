package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
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
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import tech.jhipster.config.JHipsterProperties;

/**
 * Integration tests that verify course request emails are actually delivered with correct content.
 * Uses GreenMail (in-process SMTP server) to intercept and inspect emails sent by the application.
 * <p>
 * These tests catch issues that mock-based tests cannot:
 * <ul>
 * <li>Thymeleaf template rendering failures (missing variables, type mismatches, record accessor issues)</li>
 * <li>i18n/localization errors (missing message keys)</li>
 * <li>Actual SMTP delivery problems</li>
 * </ul>
 * <p>
 * A standalone {@link MailSendingService} is constructed per test with a real {@link JavaMailSenderImpl}
 * pointing to GreenMail. This avoids interference from the {@code @MockitoSpyBean} proxies in the
 * base test class and provides a clean, isolated test of template rendering and email delivery.
 */
@Execution(ExecutionMode.SAME_THREAD)
class CourseRequestEmailIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final int EMAIL_TIMEOUT_MS = 5000;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private SpringTemplateEngine templateEngine;

    private MailSendingService testMailService;

    private User recipient;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.reset();

        // Create a standalone MailSendingService with a real JavaMailSenderImpl pointing to GreenMail.
        // This bypasses the @MockitoSpyBean proxy chain and tests actual template rendering + SMTP delivery.
        var greenMailSender = new JavaMailSenderImpl();
        greenMailSender.setHost("127.0.0.1");
        greenMailSender.setPort(greenMail.getSmtp().getPort());

        // Use a MessageSource that reads directly from src/main/resources, because the test classpath
        // has a minimal messages_en.properties (src/test/resources/i18n/) that shadows the production one.
        var mainMessageSource = new ReloadableResourceBundleMessageSource();
        mainMessageSource.setBasename("file:src/main/resources/i18n/messages");
        mainMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

        // Create a dedicated template engine that uses the production MessageSource for #{...} expressions.
        var testTemplateEngine = new SpringTemplateEngine();
        templateEngine.getTemplateResolvers().forEach(testTemplateEngine::addTemplateResolver);
        testTemplateEngine.setMessageSource(mainMessageSource);

        // Explicitly configure a non-default mail.from to ensure MailSendingService's mailConfigured guard
        // allows sending. The default "artemis@localhost" would cause emails to be silently skipped.
        var mailEnabledProperties = new JHipsterProperties();
        mailEnabledProperties.getMail().setFrom("test@greenmail.test");

        testMailService = new MailSendingService(mailEnabledProperties, greenMailSender, mainMessageSource, testTemplateEngine);
        ReflectionTestUtils.setField(testMailService, "artemisServerUrl", new URL("http://localhost:9000"));

        recipient = new User();
        recipient.setEmail("requester@greenmail.test");
        recipient.setLangKey("en");
        recipient.setFirstName("Jane");
        recipient.setLastName("Doe");
    }

    @Test
    void receivedEmailTemplate_shouldRenderRecordFieldsAndDeliver() throws Exception {
        var courseRequestData = new CourseRequestEmailData("Introduction to Testing", "INTTEST", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false,
                "Need this course for our testing department.", null);

        testMailService.buildAndSendSync(recipient, "email.courseRequest.received.title", "mail/courseRequestReceivedEmail", Map.of("courseRequest", courseRequestData));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Introduction to Testing");
        assertThat(body).contains("INTTEST");
        assertThat(body).contains("WS2025");
        assertThat(body).contains("Need this course for our testing department.");
    }

    @Test
    void acceptedEmailTemplate_shouldRenderRecordFieldsAndDeliver() throws Exception {
        var courseRequestData = new CourseRequestEmailData("Accepted Course", "ACPTCRS", null, null, null, false, null, null);

        var course = new Course();
        course.setId(42L);

        testMailService.buildAndSendSync(recipient, "email.courseRequest.accepted.title", "mail/courseRequestAcceptedEmail",
                Map.of("courseRequest", courseRequestData, "course", course));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Accepted Course");
        assertThat(body).contains("course-management/42");
    }

    @Test
    void rejectedEmailTemplate_shouldRenderRecordFieldsIncludingDecisionReason() throws Exception {
        var courseRequestData = new CourseRequestEmailData("Rejected Course", "REJCRS", null, null, null, false, null, "Not enough justification provided.");

        testMailService.buildAndSendSync(recipient, "email.courseRequest.rejected.title", "mail/courseRequestRejectedEmail", Map.of("courseRequest", courseRequestData));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Rejected Course");
        assertThat(body).contains("Not enough justification provided.");
    }

    @Test
    void receivedEmailTemplate_shouldRenderCorrectlyInGerman() throws Exception {
        recipient.setLangKey("de");
        var courseRequestData = new CourseRequestEmailData("Einführung in Tests", "EINFTEST", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), true,
                "Kurs wird für die Abteilung benötigt.", null);

        testMailService.buildAndSendSync(recipient, "email.courseRequest.received.title", "mail/courseRequestReceivedEmail", Map.of("courseRequest", courseRequestData));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("EINFTEST");
        assertThat(body).contains("Kurs wird für die Abteilung benötigt.");
    }

    @Test
    void contactEmailTemplate_shouldRenderRecordFieldsAndDeliver() throws Exception {
        var contactData = new ContactEmailData("New Course Request", "NEWCRS", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false,
                "We need this course urgently.", "Jane Doe", "jane@example.com");

        testMailService.buildAndSendSync(recipient, "email.courseRequest.contact.title", "mail/courseRequestContactEmail", Map.of("courseRequest", contactData));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("New Course Request");
        assertThat(body).contains("NEWCRS");
        assertThat(body).contains("WS2025");
        assertThat(body).contains("We need this course urgently.");
        assertThat(body).contains("Jane Doe");
        assertThat(body).contains("jane@example.com");
    }

    @Test
    void contactEmailTemplate_shouldRenderCorrectlyInGerman() throws Exception {
        recipient.setLangKey("de");
        var contactData = new ContactEmailData("Neuer Kurs", "NEUKRS", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), true, "Dringend benötigt.",
                "Max Mustermann", "max@example.com");

        testMailService.buildAndSendSync(recipient, "email.courseRequest.contact.title", "mail/courseRequestContactEmail", Map.of("courseRequest", contactData));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("NEUKRS");
        assertThat(body).contains("Max Mustermann");
        assertThat(body).contains("Dringend benötigt.");
    }

    @Test
    void acceptedEmailTemplate_shouldRenderCorrectlyInGerman() throws Exception {
        recipient.setLangKey("de");
        var courseRequestData = new CourseRequestEmailData("Akzeptierter Kurs", "AKZKRS", null, null, null, false, null, null);

        var course = new Course();
        course.setId(99L);

        testMailService.buildAndSendSync(recipient, "email.courseRequest.accepted.title", "mail/courseRequestAcceptedEmail",
                Map.of("courseRequest", courseRequestData, "course", course));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Akzeptierter Kurs");
        assertThat(body).contains("course-management/99");
    }

    @Test
    void rejectedEmailTemplate_shouldRenderCorrectlyInGerman() throws Exception {
        recipient.setLangKey("de");
        var courseRequestData = new CourseRequestEmailData("Abgelehnter Kurs", "ABLKRS", null, null, null, false, null, "Keine ausreichende Begründung.");

        testMailService.buildAndSendSync(recipient, "email.courseRequest.rejected.title", "mail/courseRequestRejectedEmail", Map.of("courseRequest", courseRequestData));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Abgelehnter Kurs");
        assertThat(body).contains("Keine ausreichende Begründung.");
    }

    /**
     * Waits for exactly one email to arrive at GreenMail and returns its decoded body content.
     * Uses {@link MimeMessage#getContent()} to properly decode quoted-printable encoded characters (e.g., UTF-8 umlauts).
     */
    private String getDeliveredEmailBody() throws Exception {
        assertThat(greenMail.waitForIncomingEmail(EMAIL_TIMEOUT_MS, 1)).isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0].getContent().toString();
    }

    /**
     * DTO that mirrors the {@code CourseRequestEmailData} record in {@code CourseRequestService}.
     * Used to verify that Thymeleaf/SpEL can resolve record accessor methods (e.g., {@code title()})
     * for template expressions like {@code ${courseRequest.title}}.
     */
    record CourseRequestEmailData(String title, String shortName, String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse, String reason,
            String decisionReason) {
    }

    /**
     * DTO that mirrors the {@code ContactEmailData} record in {@code CourseRequestService}.
     * Includes requester information needed by the contact email template.
     */
    record ContactEmailData(String title, String shortName, String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse, String reason,
            String requesterName, String requesterEmail) {
    }
}
