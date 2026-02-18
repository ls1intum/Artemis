package de.tum.cit.aet.artemis.communication.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;
import de.tum.cit.aet.artemis.core.dto.ComponentVulnerabilitiesDTO;
import de.tum.cit.aet.artemis.core.dto.ComponentWithVulnerabilitiesDTO;
import de.tum.cit.aet.artemis.core.dto.VulnerabilityDTO;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import tech.jhipster.config.JHipsterProperties;

/**
 * Integration tests for emails sent via {@link MailService} and {@link MailSendingService}.
 * Uses GreenMail to verify template rendering, i18n resolution, and SMTP delivery
 * for account-related emails and security notification emails.
 */
@Execution(ExecutionMode.SAME_THREAD)
class MailServiceEmailIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final int EMAIL_TIMEOUT_MS = 5000;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Autowired
    private SpringTemplateEngine templateEngine;

    private MailSendingService testMailSendingService;

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
        var mailEnabledProperties = new JHipsterProperties();
        mailEnabledProperties.getMail().setFrom("test@greenmail.test");

        testMailSendingService = new MailSendingService(mailEnabledProperties, greenMailSender, mainMessageSource, testTemplateEngine);
        ReflectionTestUtils.setField(testMailSendingService, "artemisServerUrl", new URL("http://localhost:9000"));

        testMailService = new MailService(mainMessageSource, testTemplateEngine, testMailSendingService);
        ReflectionTestUtils.setField(testMailService, "artemisServerUrl", new URL("http://localhost:9000"));

        recipient = new User();
        recipient.setEmail("user@greenmail.test");
        recipient.setLangKey("en");
        recipient.setLogin("testuser");
        recipient.setFirstName("Jane");
        recipient.setLastName("Doe");
    }

    // -- Activation email --

    @Test
    void activationEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        recipient.setActivationKey("abc123-activation-key");

        testMailService.sendActivationEmail(recipient);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("testuser");
        assertThat(body).contains("abc123-activation-key");
        assertThat(body).contains("account/activate");
    }

    @Test
    void activationEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        recipient.setActivationKey("de-activation-key-456");

        testMailService.sendActivationEmail(recipient);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("de-activation-key-456");
        assertThat(body).contains("account/activate");
    }

    // -- Password reset email --

    @Test
    void passwordResetEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        recipient.setResetKey("reset-key-789");

        testMailService.sendPasswordResetMail(recipient);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("reset-key-789");
        assertThat(body).contains("account/reset/finish");
    }

    @Test
    void passwordResetEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        recipient.setResetKey("de-reset-key-012");

        testMailService.sendPasswordResetMail(recipient);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("de-reset-key-012");
        assertThat(body).contains("account/reset/finish");
    }

    // -- SAML2 set password email --

    @Test
    void saml2SetPasswordEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        recipient.setResetKey("saml-reset-key-345");

        testMailService.sendSAML2SetPasswordMail(recipient);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("saml-reset-key-345");
    }

    @Test
    void saml2SetPasswordEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        recipient.setResetKey("de-saml-key-678");

        testMailService.sendSAML2SetPasswordMail(recipient);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("de-saml-key-678");
    }

    // -- New login notification email --

    @Test
    void newLoginEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var contextVariables = createLoginEmailContext("Password", "17.02.2026", "10:30:00 (Europe/Berlin)", "Web Browser", "http://localhost:9000/account/password");

        testMailSendingService.buildAndSendSync(recipient, "email.notification.login.title", "mail/notification/newLoginEmail", contextVariables);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Password");
        assertThat(body).contains("17.02.2026");
        assertThat(body).contains("10:30:00");
        assertThat(body).contains("Web Browser");
        assertThat(body).contains("account/password");
    }

    @Test
    void newLoginEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");
        var contextVariables = createLoginEmailContext("Passwort", "17.02.2026", "10:30:00 (Europe/Berlin)", "Webbrowser", "http://localhost:9000/account/password");

        testMailSendingService.buildAndSendSync(recipient, "email.notification.login.title", "mail/notification/newLoginEmail", contextVariables);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Passwort");
        assertThat(body).contains("17.02.2026");
        assertThat(body).contains("Webbrowser");
    }

    // -- New passkey notification email --

    @Test
    void newPasskeyEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailSendingService.buildAndSendSync(recipient, "email.notification.newPasskey.title", "mail/notification/newPasskeyEmail", new HashMap<>());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("user-settings/passkeys");
        assertThat(body).contains("Jane Doe");
    }

    @Test
    void newPasskeyEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailSendingService.buildAndSendSync(recipient, "email.notification.newPasskey.title", "mail/notification/newPasskeyEmail", new HashMap<>());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("user-settings/passkeys");
        assertThat(body).contains("Jane Doe");
    }

    // -- VCS access token expired notification email --

    @Test
    void vcsAccessTokenExpiredEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailSendingService.buildAndSendSync(recipient, "email.notification.vcsAccessTokenExpiry.title", "mail/notification/vcsAccessTokenExpiredEmail", new HashMap<>());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("user-settings/vcs-token");
        assertThat(body).contains("Jane Doe");
    }

    @Test
    void vcsAccessTokenExpiredEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailSendingService.buildAndSendSync(recipient, "email.notification.vcsAccessTokenExpiry.title", "mail/notification/vcsAccessTokenExpiredEmail", new HashMap<>());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("user-settings/vcs-token");
        assertThat(body).contains("Jane Doe");
    }

    // -- SSH key expired notification email --

    @Test
    void sshKeyExpiredEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var sshKey = new UserSshPublicKey();
        sshKey.setLabel("My Laptop Key");
        sshKey.setKeyHash("SHA256:abc123hash");

        var contextVariables = new HashMap<String, Object>();
        contextVariables.put("sshKey", sshKey);
        contextVariables.put("expiryDate", "17.02.2026 - 10:30:00");

        testMailSendingService.buildAndSendSync(recipient, "email.notification.sshKeyExpiry.sshKeysHasExpiredWarning", "mail/notification/sshKeyHasExpiredEmail", contextVariables);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("My Laptop Key");
        assertThat(body).contains("SHA256:abc123hash");
        assertThat(body).contains("17.02.2026 - 10:30:00");
        assertThat(body).contains("user-settings/ssh");
    }

    @Test
    void sshKeyExpiredEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var sshKey = new UserSshPublicKey();
        sshKey.setLabel("Mein Laptop Key");
        sshKey.setKeyHash("SHA256:de456hash");

        var contextVariables = new HashMap<String, Object>();
        contextVariables.put("sshKey", sshKey);
        contextVariables.put("expiryDate", "17.02.2026 - 10:30:00");

        testMailSendingService.buildAndSendSync(recipient, "email.notification.sshKeyExpiry.sshKeysHasExpiredWarning", "mail/notification/sshKeyHasExpiredEmail", contextVariables);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Mein Laptop Key");
        assertThat(body).contains("SHA256:de456hash");
        assertThat(body).contains("17.02.2026 - 10:30:00");
        assertThat(body).contains("user-settings/ssh");
    }

    // -- Data export created email --

    @Test
    void dataExportCreatedEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var dataExport = new DataExport();
        dataExport.setId(42L);

        testMailService.sendDataExportCreatedEmail(recipient, dataExport);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("privacy/data-exports/42");
    }

    @Test
    void dataExportCreatedEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var dataExport = new DataExport();
        dataExport.setId(99L);

        testMailService.sendDataExportCreatedEmail(recipient, dataExport);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("privacy/data-exports/99");
    }

    // -- Data export failed admin email --

    @Test
    void dataExportFailedAdminEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var exportUser = new User();
        exportUser.setLogin("faileduser");

        var dataExport = new DataExport();
        dataExport.setUser(exportUser);

        testMailService.sendDataExportFailedEmailToAdmin(recipient, dataExport, new RuntimeException("Disk full"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("faileduser");
        assertThat(body).contains("Disk full");
        assertThat(body).contains("admin/data-exports");
    }

    @Test
    void dataExportFailedAdminEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var exportUser = new User();
        exportUser.setLogin("fehlbenutzer");

        var dataExport = new DataExport();
        dataExport.setUser(exportUser);

        testMailService.sendDataExportFailedEmailToAdmin(recipient, dataExport, new RuntimeException("Festplatte voll"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("fehlbenutzer");
        assertThat(body).contains("Festplatte voll");
        assertThat(body).contains("admin/data-exports");
    }

    // -- Data export email failed admin email --

    @Test
    void dataExportEmailFailedAdminEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var exportUser = new User();
        exportUser.setLogin("emailfailuser");

        var dataExport = new DataExport();
        dataExport.setUser(exportUser);

        testMailService.sendDataExportEmailFailedEmailToAdmin(recipient, dataExport, new RuntimeException("SMTP connection refused"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("emailfailuser");
        assertThat(body).contains("SMTP connection refused");
        assertThat(body).contains("admin/data-exports");
    }

    @Test
    void dataExportEmailFailedAdminEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var exportUser = new User();
        exportUser.setLogin("emailfehlnutzer");

        var dataExport = new DataExport();
        dataExport.setUser(exportUser);

        testMailService.sendDataExportEmailFailedEmailToAdmin(recipient, dataExport, new RuntimeException("SMTP Verbindung abgelehnt"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("emailfehlnutzer");
        assertThat(body).contains("SMTP Verbindung abgelehnt");
        assertThat(body).contains("admin/data-exports");
    }

    // -- Successful data exports admin email --

    @Test
    void successfulDataExportsAdminEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var user1 = new User();
        user1.setLogin("exportuser1");
        var export1 = new DataExport();
        export1.setUser(user1);

        var user2 = new User();
        user2.setLogin("exportuser2");
        var export2 = new DataExport();
        export2.setUser(user2);

        var dataExports = new LinkedHashSet<DataExport>();
        dataExports.add(export1);
        dataExports.add(export2);

        testMailService.sendSuccessfulDataExportsEmailToAdmin(recipient, dataExports);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("exportuser1");
        assertThat(body).contains("exportuser2");
    }

    @Test
    void successfulDataExportsAdminEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var user1 = new User();
        user1.setLogin("exportnutzer1");
        var export1 = new DataExport();
        export1.setUser(user1);

        var dataExports = new LinkedHashSet<DataExport>();
        dataExports.add(export1);

        testMailService.sendSuccessfulDataExportsEmailToAdmin(recipient, dataExports);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("exportnutzer1");
    }

    // -- Build agent self-paused admin email --

    @Test
    void buildAgentSelfPausedEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailService.sendBuildAgentSelfPausedEmailToAdmin(recipient, "build-agent-01", 5);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("build-agent-01");
        assertThat(body).contains("5");
    }

    @Test
    void buildAgentSelfPausedEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailService.sendBuildAgentSelfPausedEmailToAdmin(recipient, "build-agent-02", 10);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("build-agent-02");
        assertThat(body).contains("10");
    }

    // -- Vulnerability scan result email --

    @Test
    void vulnerabilityScanResultEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var vuln = new VulnerabilityDTO("CVE-2025-1234", "Test vulnerability summary", "details", "HIGH", 7.5, List.of(), List.of("2.0.0"), List.of());
        var component = new ComponentWithVulnerabilitiesDTO("pkg:maven/com.example/test@1.0.0", List.of(vuln));
        var vulnerabilities = new ComponentVulnerabilitiesDTO(List.of(component), 1, 0, 1, 0, 0, "2026-02-17T10:00:00Z");
        var versionInfo = new ArtemisVersionDTO("7.8.0", "7.9.0", true, "https://github.com/ls1intum/Artemis/releases/tag/7.9.0", null, "2026-02-17");

        testMailService.sendVulnerabilityScanResultEmail(recipient, vulnerabilities, versionInfo, true);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("CVE-2025-1234");
        assertThat(body).contains("Test vulnerability summary");
        assertThat(body).contains("pkg:maven/com.example/test@1.0.0");
        assertThat(body).contains("7.8.0");
        assertThat(body).contains("7.9.0");
        assertThat(body).contains("2.0.0");
        assertThat(body).contains("admin/dependencies");
    }

    @Test
    void vulnerabilityScanResultEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var vulnerabilities = new ComponentVulnerabilitiesDTO(List.of(), 0, 0, 0, 0, 0, "2026-02-17T10:00:00Z");
        var versionInfo = new ArtemisVersionDTO("7.8.0", null, false, null, null, "2026-02-17");

        testMailService.sendVulnerabilityScanResultEmail(recipient, vulnerabilities, versionInfo, false);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("7.8.0");
        assertThat(body).contains("admin/dependencies");
    }

    private Map<String, Object> createLoginEmailContext(String authMethod, String loginDate, String loginTime, String requestOrigin, String resetLink) {
        var contextVariables = new HashMap<String, Object>();
        contextVariables.put("authenticationMethod", authMethod);
        contextVariables.put("loginDate", loginDate);
        contextVariables.put("loginTime", loginTime);
        contextVariables.put("requestOrigin", requestOrigin);
        contextVariables.put("resetLink", resetLink);
        return contextVariables;
    }

    private String getDeliveredEmailBody() throws Exception {
        assertThat(greenMail.waitForIncomingEmail(EMAIL_TIMEOUT_MS, 1)).isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0].getContent().toString();
    }
}
