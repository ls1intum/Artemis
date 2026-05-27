package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

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
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardAlertChatModeDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardAlertDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for Iris dashboard emails sent via {@link MailService}.
 * Uses GreenMail to verify template rendering, i18n resolution, and SMTP delivery
 * for the digest and alert email templates.
 */
@Execution(ExecutionMode.SAME_THREAD)
class IrisDashboardEmailIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final int EMAIL_TIMEOUT_MS = 5000;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Autowired
    private SpringTemplateEngine templateEngine;

    private MailService testMailService;

    private User recipient;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.reset();

        var greenMailSender = new JavaMailSenderImpl();
        greenMailSender.setHost("127.0.0.1");
        greenMailSender.setPort(greenMail.getSmtp().getPort());

        // Use production MessageSource to avoid test classpath shadowing
        var mainMessageSource = new ReloadableResourceBundleMessageSource();
        mainMessageSource.setBasename("file:src/main/resources/i18n/messages");
        mainMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

        var testTemplateEngine = new SpringTemplateEngine();
        templateEngine.getTemplateResolvers().forEach(testTemplateEngine::addTemplateResolver);
        testTemplateEngine.setMessageSource(mainMessageSource);

        // Explicitly enable mail sending (bypass the "artemis@localhost" guard)
        var mailEnabledProperties = new ArtemisProperties();
        mailEnabledProperties.getMail().setFrom("test@greenmail.test");

        var testMailSendingService = new MailSendingService(mailEnabledProperties, greenMailSender, mainMessageSource, testTemplateEngine);
        ReflectionTestUtils.setField(testMailSendingService, "artemisServerUrl", new URL("http://localhost:9000"));

        testMailService = new MailService(mainMessageSource, testTemplateEngine, testMailSendingService);
        ReflectionTestUtils.setField(testMailService, "artemisServerUrl", new URL("http://localhost:9000"));

        recipient = new User();
        recipient.setEmail("admin@greenmail.test");
        recipient.setLangKey("en");
        recipient.setLogin("iris-dashboard-recipient");
        recipient.setFirstName("Administrator");
    }

    @Test
    void digestEmail_shouldRenderAndDeliver() throws Exception {
        var digest = new IrisDashboardDigestDTO(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"), Instant.parse("2026-05-26T23:55:00Z"), 100, 50, 50.0,
                200, 20, 12.5, 25, 12, 80.0, 20.0, 60.0, 15.0, 40, 10, 35, 8, 5.5, 4.0, 12.0, 150.75, List.of(), List.of(), "/admin/iris-dashboard");

        testMailService.sendIrisDashboardDigestEmail(recipient, digest);

        assertThat(greenMail.waitForIncomingEmail(EMAIL_TIMEOUT_MS, 1)).isTrue();
        var messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        String body = messages[0].getContent().toString();
        assertThat(body).contains("12.5%");
        assertThat(body).contains("iris-dashboard");
    }

    @Test
    void alertEmail_shouldRenderAndDeliver() throws Exception {
        var alert = new IrisDashboardAlertDTO(Instant.parse("2026-05-27T11:00:00Z"), Instant.parse("2026-05-27T11:55:00Z"), 15.5, 10.0, 20, 3, 100,
                List.of(new IrisDashboardAlertChatModeDTO("COURSE_CHAT", 15, 2, 13.3)), "/admin/iris-dashboard");

        testMailService.sendIrisDashboardAlertEmail(recipient, alert);

        assertThat(greenMail.waitForIncomingEmail(EMAIL_TIMEOUT_MS, 1)).isTrue();
        var messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        String body = messages[0].getContent().toString();
        assertThat(body).contains("15.5%");
        assertThat(body).contains("COURSE_CHAT");
    }
}
