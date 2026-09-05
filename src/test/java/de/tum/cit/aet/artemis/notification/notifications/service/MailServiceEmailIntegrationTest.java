package de.tum.cit.aet.artemis.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.dto.ComponentVulnerabilitiesDTO;
import de.tum.cit.aet.artemis.admin.dto.ComponentWithVulnerabilitiesDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageModuleSummaryDTO;
import de.tum.cit.aet.artemis.admin.dto.VulnerabilityDTO;
import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;
import de.tum.cit.aet.artemis.notification.dto.DataExportEmailDTO;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

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
        var mailEnabledProperties = new ArtemisProperties();
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

        testMailService.sendActivationEmail(MailRecipientDTO.withRecoveryKey(recipient, "abc123-activation-key", null));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("testuser");
        assertThat(body).contains("abc123-activation-key");
        assertThat(body).contains("account/activate");
    }

    @Test
    void activationEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailService.sendActivationEmail(MailRecipientDTO.withRecoveryKey(recipient, "de-activation-key-456", null));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("de-activation-key-456");
        assertThat(body).contains("account/activate");
    }

    // -- Password reset email --

    @Test
    void passwordResetEmail_shouldRenderAndDeliverInEnglish() throws Exception {

        testMailService.sendPasswordResetMail(MailRecipientDTO.withRecoveryKey(recipient, null, "reset-key-789"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("reset-key-789");
        assertThat(body).contains("account/reset/finish");
    }

    @Test
    void passwordResetEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailService.sendPasswordResetMail(MailRecipientDTO.withRecoveryKey(recipient, null, "de-reset-key-012"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("de-reset-key-012");
        assertThat(body).contains("account/reset/finish");
    }

    @Test
    void passwordResetEmail_shouldUseTheSharedArtemisLayout() throws Exception {

        testMailService.sendPasswordResetMail(MailRecipientDTO.withRecoveryKey(recipient, null, "styled-reset-key-345"));

        assertUsesSharedArtemisLayout(getDeliveredEmailBody());
    }

    @Test
    void activationEmail_shouldUseTheSharedArtemisLayout() throws Exception {

        testMailService.sendActivationEmail(MailRecipientDTO.withRecoveryKey(recipient, "styled-activation-key-123", null));

        assertUsesSharedArtemisLayout(getDeliveredEmailBody());
    }

    @Test
    void saml2SetPasswordEmail_shouldUseTheSharedArtemisLayout() throws Exception {

        testMailService.sendSAML2SetPasswordMail(MailRecipientDTO.withRecoveryKey(recipient, null, "styled-saml-key-567"));

        assertUsesSharedArtemisLayout(getDeliveredEmailBody());
    }

    /**
     * Asserts that a mail carries the shared Artemis chrome.
     * <p>
     * The three account mails that contain a link are the ones a user is most likely to distrust, because each can
     * arrive unprompted: anyone can type someone else's address into the reset form. Looking like every other Artemis
     * mail is what makes them credible rather than suspicious, and all three used to render as unstyled documents in
     * the mail client's default serif font.
     * <p>
     * The absence of the footer is asserted too. That footer links to the notification settings, and none of these
     * three can be switched off there, so the link would point at a setting that does not exist for them.
     *
     * @param body the rendered mail body
     */
    private static void assertUsesSharedArtemisLayout(String body) {
        assertThat(body).as("the Artemis header with the logo").contains("<header>").contains("id=\"logo\"");
        assertThat(body).as("the shared stylesheet, which sets the sans-serif font").contains("<style>").contains("font-family");
        assertThat(body).as("the message body wrapper the shared css styles").contains("id=\"message-body\"");
        assertThat(body).as("the favicon, at the path it is actually served from").contains("/logo/favicon.svg");
        assertThat(body).as("no notification-settings footer on a mail that cannot be switched off").doesNotContain("<footer>");
        // The logo has to come from this installation. It is a documented customization point, and pointing at the TUM
        // deployment would both ignore a custom logo and make every recipient's mail client fetch an image from there.
        assertThat(body).as("the logo, served by this installation so a custom one is used").contains("src=\"http://localhost:9000/public/images/logo.png\"");
        assertThat(body).as("no request to the TUM deployment").doesNotContain("artemis.tum.de");
    }

    // -- SAML2 set password email --

    @Test
    void saml2SetPasswordEmail_shouldRenderAndDeliverInEnglish() throws Exception {

        testMailService.sendSAML2SetPasswordMail(MailRecipientDTO.withRecoveryKey(recipient, null, "saml-reset-key-345"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("saml-reset-key-345");
    }

    @Test
    void saml2SetPasswordEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailService.sendSAML2SetPasswordMail(MailRecipientDTO.withRecoveryKey(recipient, null, "de-saml-key-678"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("de-saml-key-678");
    }

    // -- New login notification email --

    @Test
    void newLoginEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var contextVariables = createLoginEmailContext("Password", "17.02.2026", "10:30:00 (Europe/Berlin)", "Web Browser", "http://localhost:9000/account/password");

        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.login.title", "mail/notification/newLoginEmail", contextVariables);

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

        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.login.title", "mail/notification/newLoginEmail", contextVariables);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Passwort");
        assertThat(body).contains("17.02.2026");
        assertThat(body).contains("Webbrowser");
    }

    // -- New passkey notification email --

    @Test
    void newPasskeyEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.newPasskey.title", "mail/notification/newPasskeyEmail", new HashMap<>());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("user-settings/passkeys");
        assertThat(body).contains("Jane Doe");
    }

    @Test
    void newPasskeyEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.newPasskey.title", "mail/notification/newPasskeyEmail", new HashMap<>());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("user-settings/passkeys");
        assertThat(body).contains("Jane Doe");
    }

    // -- E-mail changed notification email --

    @Test
    void emailChangedEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.emailChanged.title", "mail/notification/emailChangedEmail",
                new HashMap<>(Map.of("emailRemoved", false, "newEmail", "new-address@tum.de")));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("new-address@tum.de");
        assertThat(body).contains("Jane Doe");
        assertThat(body).contains("was changed");
    }

    @Test
    void emailChangedEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.emailChanged.title", "mail/notification/emailChangedEmail",
                new HashMap<>(Map.of("emailRemoved", false, "newEmail", "new-address@tum.de")));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("new-address@tum.de");
        assertThat(body).contains("geändert");
    }

    @Test
    void emailChangedEmail_shouldEscapeTheAddressRatherThanRenderItAsMarkup() throws Exception {
        // The address reaches the template from user input, so it has to be escaped. The template uses th:text for
        // exactly this reason; th:utext would turn a crafted address into live markup in the recipient's client.
        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.emailChanged.title", "mail/notification/emailChangedEmail",
                new HashMap<>(Map.of("emailRemoved", false, "newEmail", "<script>alert(1)</script>@tum.de")));

        String body = getDeliveredEmailBody();
        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).contains("&lt;script&gt;");
    }

    // -- VCS access token expired notification email --

    @Test
    void vcsAccessTokenExpiredEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.vcsAccessTokenExpiry.title", "mail/notification/vcsAccessTokenExpiredEmail",
                new HashMap<>());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("user-settings/vcs-token");
        assertThat(body).contains("Jane Doe");
    }

    @Test
    void vcsAccessTokenExpiredEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.vcsAccessTokenExpiry.title", "mail/notification/vcsAccessTokenExpiredEmail",
                new HashMap<>());

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

        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.sshKeyExpiry.sshKeysHasExpiredWarning",
                "mail/notification/sshKeyHasExpiredEmail", contextVariables);

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

        testMailSendingService.buildAndSendSync(MailRecipientDTO.from(recipient), "email.notification.sshKeyExpiry.sshKeysHasExpiredWarning",
                "mail/notification/sshKeyHasExpiredEmail", contextVariables);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Mein Laptop Key");
        assertThat(body).contains("SHA256:de456hash");
        assertThat(body).contains("17.02.2026 - 10:30:00");
        assertThat(body).contains("user-settings/ssh");
    }

    // -- Data export created email --

    @Test
    void dataExportCreatedEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var dataExport = new DataExportEmailDTO(42L, recipient.getLogin());

        testMailService.sendDataExportCreatedEmail(MailRecipientDTO.from(recipient), dataExport);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("privacy/data-exports/42");
    }

    @Test
    void dataExportCreatedEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var dataExport = new DataExportEmailDTO(99L, recipient.getLogin());

        testMailService.sendDataExportCreatedEmail(MailRecipientDTO.from(recipient), dataExport);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("privacy/data-exports/99");
    }

    // -- Data export failed admin email --

    @Test
    void dataExportFailedAdminEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var dataExport = new DataExportEmailDTO(1L, "faileduser");

        testMailService.sendDataExportFailedEmailToAdmin(MailRecipientDTO.from(recipient), dataExport, new RuntimeException("Disk full"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("faileduser");
        assertThat(body).contains("Disk full");
        assertThat(body).contains("admin/data-exports");
    }

    @Test
    void dataExportFailedAdminEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var dataExport = new DataExportEmailDTO(2L, "fehlbenutzer");

        testMailService.sendDataExportFailedEmailToAdmin(MailRecipientDTO.from(recipient), dataExport, new RuntimeException("Festplatte voll"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("fehlbenutzer");
        assertThat(body).contains("Festplatte voll");
        assertThat(body).contains("admin/data-exports");
    }

    // -- Data export email failed admin email --

    @Test
    void dataExportEmailFailedAdminEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var dataExport = new DataExportEmailDTO(3L, "emailfailuser");

        testMailService.sendDataExportEmailFailedEmailToAdmin(MailRecipientDTO.from(recipient), dataExport, new RuntimeException("SMTP connection refused"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("emailfailuser");
        assertThat(body).contains("SMTP connection refused");
        assertThat(body).contains("admin/data-exports");
    }

    @Test
    void dataExportEmailFailedAdminEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var dataExport = new DataExportEmailDTO(4L, "emailfehlnutzer");

        testMailService.sendDataExportEmailFailedEmailToAdmin(MailRecipientDTO.from(recipient), dataExport, new RuntimeException("SMTP Verbindung abgelehnt"));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("emailfehlnutzer");
        assertThat(body).contains("SMTP Verbindung abgelehnt");
        assertThat(body).contains("admin/data-exports");
    }

    // -- Successful data exports admin email --

    @Test
    void successfulDataExportsAdminEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        var dataExports = new LinkedHashSet<DataExportEmailDTO>();
        dataExports.add(new DataExportEmailDTO(11L, "exportuser1"));
        dataExports.add(new DataExportEmailDTO(12L, "exportuser2"));

        testMailService.sendSuccessfulDataExportsEmailToAdmin(MailRecipientDTO.from(recipient), dataExports);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("exportuser1");
        assertThat(body).contains("exportuser2");
    }

    @Test
    void successfulDataExportsAdminEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        var dataExports = new LinkedHashSet<DataExportEmailDTO>();
        dataExports.add(new DataExportEmailDTO(21L, "exportnutzer1"));

        testMailService.sendSuccessfulDataExportsEmailToAdmin(MailRecipientDTO.from(recipient), dataExports);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("exportnutzer1");
    }

    // -- Build agent self-paused admin email --

    @Test
    void buildAgentSelfPausedEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailService.sendBuildAgentSelfPausedEmailToAdmin(MailRecipientDTO.from(recipient), "build-agent-01", 5);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("build-agent-01");
        assertThat(body).contains("5");
    }

    @Test
    void buildAgentSelfPausedEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailService.sendBuildAgentSelfPausedEmailToAdmin(MailRecipientDTO.from(recipient), "build-agent-02", 10);

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

        testMailService.sendVulnerabilityScanResultEmail(MailRecipientDTO.from(recipient), vulnerabilities, versionInfo, true);

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

        testMailService.sendVulnerabilityScanResultEmail(MailRecipientDTO.from(recipient), vulnerabilities, versionInfo, false);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("7.8.0");
        assertThat(body).contains("admin/dependencies");
    }

    // -- Weekly feature usage digest --

    @Test
    void featureUsageDigestEmail_shouldRenderAndDeliverInEnglish() throws Exception {
        testMailService.sendFeatureUsageDigestEmail(MailRecipientDTO.from(recipient), featureUsageDigest());

        String body = getDeliveredEmailBody();
        assertThat(body).contains("programming");
        assertThat(body).contains("1500");
        // the previous window was smaller, so the change has to read as a rise
        assertThat(body).contains("+50%");
        // a module nobody touched is named rather than shown as a row of zeros
        assertThat(body).contains("lecture");
        // the whole point of the mail is to get someone onto the page
        assertThat(body).contains("admin/feature-usage");
    }

    @Test
    void featureUsageDigestEmail_shouldRenderAndDeliverInGerman() throws Exception {
        recipient.setLangKey("de");

        testMailService.sendFeatureUsageDigestEmail(MailRecipientDTO.from(recipient), featureUsageDigest());

        String body = getDeliveredEmailBody();
        // proves the German message keys exist too; a missing key would render as ??key??
        assertThat(body).doesNotContain("??");
        assertThat(body).contains("Nutzung pro Modul");
        assertThat(body).contains("admin/feature-usage");
    }

    @Test
    void featureUsageDigestEmail_shouldSayWhenNothingWasRecorded() throws Exception {
        var empty = new FeatureUsageDigestDTO(7, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 16), 0, 0, 0, 0, 0, 0, null, List.of(), List.of());

        testMailService.sendFeatureUsageDigestEmail(MailRecipientDTO.from(recipient), empty);

        String body = getDeliveredEmailBody();
        // an empty deployment must not receive a table of zeros presented as a finding
        assertThat(body).contains("No usage at all was recorded");
        assertThat(body).doesNotContain("Usage per module");
    }

    @Test
    void featureUsageDigestEmail_shouldOmitTheChangeWhenThereIsNoPreviousData() throws Exception {
        var module = new FeatureUsageModuleSummaryDTO("programming", 1500, 0, 3, 40, 60, 20);
        var digest = new FeatureUsageDigestDTO(7, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 16), 1500, 0, 60, 40, 20, 2, null, List.of(module), List.of());

        testMailService.sendFeatureUsageDigestEmail(MailRecipientDTO.from(recipient), digest);

        String body = getDeliveredEmailBody();
        assertThat(body).contains("n/a");
        assertThat(body).doesNotContain("%</span>");
    }

    private static FeatureUsageDigestDTO featureUsageDigest() {
        var programming = new FeatureUsageModuleSummaryDTO("programming", 1500, 1000, 3, 40, 60, 20);
        var exam = new FeatureUsageModuleSummaryDTO("exam", 200, 400, 0, 10, 12, 2);
        return new FeatureUsageDigestDTO(7, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 16), 1700, 1400, 72, 50, 22, 2, Instant.parse("2026-01-01T00:00:00Z"),
                List.of(programming, exam), List.of("lecture", "quiz"));
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
