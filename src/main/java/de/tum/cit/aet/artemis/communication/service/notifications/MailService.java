package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.communication.dto.DataExportMailDTO;
import de.tum.cit.aet.artemis.communication.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;
import de.tum.cit.aet.artemis.core.dto.ComponentVulnerabilitiesDTO;

/**
 * Service for preparing and sending emails.
 * <p>
 * We use the MailSendingService to send emails asynchronously.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String USER = "user";

    private static final String BASE_URL = "baseUrl";

    private static final String DATA_EXPORT = "dataExport";

    private static final String DATA_EXPORTS = "dataExports";

    private static final String REASON = "reason";

    private static final String BUILD_AGENT_NAME = "buildAgentName";

    private static final String CONSECUTIVE_BUILD_FAILURES = "consecutiveBuildFailures";

    private static final String VULNERABILITIES = "vulnerabilities";

    private static final String VERSION_INFO = "versionInfo";

    private static final String SHOULD_RECOMMEND_UPGRADE = "shouldRecommendUpgrade";

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    private final MailSendingService mailSendingService;

    public MailService(MessageSource messageSource, SpringTemplateEngine templateEngine, MailSendingService mailSendingService) {
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Sends a predefined mail based on a template
     *
     * @param recipient    The receiver of the mail
     * @param templateName The name of the template
     * @param titleKey     The key mapping the title for the subject of the mail
     */
    public void sendEmailFromTemplate(MailRecipientDTO recipient, String templateName, String titleKey) {
        Locale locale = Locale.forLanguageTag(recipient.langKey());
        Context context = createBaseContext(recipient, locale);
        prepareTemplateAndSendEmail(recipient, templateName, titleKey, context);
    }

    public void sendSuccessfulDataExportsEmailToAdmin(MailRecipientDTO admin, String templateName, String titleKey, Set<DataExportMailDTO> dataExports) {
        Locale locale = Locale.forLanguageTag(admin.langKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(DATA_EXPORTS, dataExports);
        prepareTemplateAndSendEmail(admin, templateName, titleKey, context);
    }

    private void prepareTemplateAndSendEmail(MailRecipientDTO recipient, String templateName, String titleKey, Context context) {
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, context.getLocale());
        mailSendingService.sendEmail(recipient.email(), subject, content, false, true);
    }

    private void prepareTemplateAndSendEmailWithArgumentInSubject(MailRecipientDTO recipient, String templateName, String titleKey, String argument, Context context) {
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, new Object[] { argument }, context.getLocale());
        mailSendingService.sendEmail(recipient.email(), subject, content, false, true);
    }

    private Context createBaseContext(MailRecipientDTO recipient, Locale locale) {
        Context context = new Context(locale);
        context.setVariable(USER, recipient);
        context.setVariable(BASE_URL, artemisServerUrl);
        return context;
    }

    public void sendActivationEmail(MailRecipientDTO recipient) {
        log.debug("Sending activation email to '{}'", recipient.email());
        sendEmailFromTemplate(recipient, "mail/activationEmail", "email.activation.title");
    }

    public void sendPasswordResetMail(MailRecipientDTO recipient) {
        log.debug("Sending password reset email to '{}'", recipient.email());
        sendEmailFromTemplate(recipient, "mail/passwordResetEmail", "email.reset.title");
    }

    public void sendSAML2SetPasswordMail(MailRecipientDTO recipient) {
        log.debug("Sending SAML2 set password email to '{}'", recipient.email());
        sendEmailFromTemplate(recipient, "mail/samlSetPasswordEmail", "email.saml.title");
    }

    /**
     * Sends an email to admin users informing them that a data export has failed.
     *
     * @param admin      the admin user to notify
     * @param dataExport the data export that failed
     * @param reason     the exception that caused the failure
     */
    public void sendDataExportFailedEmailToAdmin(MailRecipientDTO admin, DataExportMailDTO dataExport, Exception reason) {
        log.debug("Sending data export failed email to admin email address '{}'", admin.email());
        Locale locale = Locale.forLanguageTag(admin.langKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(DATA_EXPORT, dataExport);
        context.setVariable(REASON, reason.getMessage());
        prepareTemplateAndSendEmailWithArgumentInSubject(admin, "mail/dataExportFailedAdminEmail", "email.dataExportFailedAdmin.title", dataExport.user().login(), context);
    }

    /**
     * Sends an email to admin users informing them that a data export notification email has failed.
     *
     * @param admin      the admin user to notify
     * @param dataExport the data export that failed
     * @param reason     the exception that caused the failure
     */
    public void sendDataExportEmailFailedEmailToAdmin(MailRecipientDTO admin, DataExportMailDTO dataExport, Exception reason) {
        log.debug("Sending data export email failed email to admin email address '{}'", admin.email());
        Locale locale = Locale.forLanguageTag(admin.langKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(DATA_EXPORT, dataExport);
        context.setVariable(REASON, reason.getMessage());
        prepareTemplateAndSendEmailWithArgumentInSubject(admin, "mail/dataExportEmailFailedAdminEmail", "email.dataExportEmailFailedAdmin.title", dataExport.user().login(),
                context);
    }

    /**
     * Sends an email to a user informing them that their data export has been successfully created.
     *
     * @param recipient  the user to send the email to
     * @param dataExport the data export that was created
     */
    public void sendDataExportCreatedEmail(MailRecipientDTO recipient, DataExportMailDTO dataExport) {
        log.debug("Sending data export created email to '{}'", recipient.email());
        Locale locale = Locale.forLanguageTag(recipient.langKey());
        Context context = createBaseContext(recipient, locale);
        context.setVariable(DATA_EXPORT, dataExport);
        prepareTemplateAndSendEmail(recipient, "mail/dataExportCreatedEmail", "email.dataExportCreated.title", context);
    }

    public void sendSuccessfulDataExportsEmailToAdmin(MailRecipientDTO admin, Set<DataExportMailDTO> dataExports) {
        log.debug("Sending successful creation of data exports email to admin email address '{}'", admin.email());
        sendSuccessfulDataExportsEmailToAdmin(admin, "mail/successfulDataExportsAdminEmail", "email.successfulDataExportCreationsAdmin.title", dataExports);
    }

    /**
     * Sends an email to admin users about a build agent paused itself.
     *
     * @param admin                    the admin user to notify
     * @param buildAgentName           the name of the build agent that was paused
     * @param consecutiveBuildFailures the number of consecutive build failures on the build agent
     */
    public void sendBuildAgentSelfPausedEmailToAdmin(MailRecipientDTO admin, String buildAgentName, int consecutiveBuildFailures) {
        log.debug("Sending build agent self paused email to admin email address '{}'", admin.email());
        Locale locale = Locale.forLanguageTag(admin.langKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(BUILD_AGENT_NAME, buildAgentName);
        context.setVariable(CONSECUTIVE_BUILD_FAILURES, consecutiveBuildFailures);
        prepareTemplateAndSendEmail(admin, "mail/buildAgentSelfPausedEmail", "email.buildAgent.SelfPaused.title", context);
    }

    /**
     * Sends an email to admin users with the results of the weekly vulnerability scan.
     *
     * @param admin                  the admin user to notify
     * @param vulnerabilities        the vulnerability scan results
     * @param versionInfo            the Artemis version information
     * @param shouldRecommendUpgrade whether to recommend upgrading due to high/critical vulnerabilities and available update
     */
    public void sendVulnerabilityScanResultEmail(MailRecipientDTO admin, ComponentVulnerabilitiesDTO vulnerabilities, ArtemisVersionDTO versionInfo,
            boolean shouldRecommendUpgrade) {
        log.debug("Sending vulnerability scan result email to admin email address '{}'", admin.email());
        Locale locale = Locale.forLanguageTag(admin.langKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(VULNERABILITIES, vulnerabilities);
        context.setVariable(VERSION_INFO, versionInfo);
        context.setVariable(SHOULD_RECOMMEND_UPGRADE, shouldRecommendUpgrade);
        prepareTemplateAndSendEmail(admin, "mail/vulnerabilityScanResultEmail", "email.vulnerabilityScan.title", context);
    }
}
