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

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;
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
     * @param user         The receiver of the mail
     * @param templateName The name of the template
     * @param titleKey     The key mapping the title for the subject of the mail
     */
    public void sendEmailFromTemplate(User user, String templateName, String titleKey) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = createBaseContext(user, locale);
        prepareTemplateAndSendEmail(user, templateName, titleKey, context);
    }

    /**
     * Sends an email to a user (the internal admin user) about a failed data export creation.
     *
     * @param admin        the admin user
     * @param templateName the name of the email template
     * @param titleKey     the subject of the email
     * @param dataExport   the data export that failed
     * @param reason       the exception that caused the data export to fail
     */
    public void sendDataExportFailedEmailForAdmin(User admin, String templateName, String titleKey, DataExport dataExport, Exception reason) {
        Locale locale = Locale.forLanguageTag(admin.getLangKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(DATA_EXPORT, dataExport);
        context.setVariable(REASON, reason.getMessage());
        prepareTemplateAndSendEmailWithArgumentInSubject(admin, templateName, titleKey, dataExport.getUser().getLogin(), context);
    }

    public void sendSuccessfulDataExportsEmailToAdmin(User admin, String templateName, String titleKey, Set<DataExport> dataExports) {
        Locale locale = Locale.forLanguageTag(admin.getLangKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(DATA_EXPORTS, dataExports);
        prepareTemplateAndSendEmail(admin, templateName, titleKey, context);
    }

    private void prepareTemplateAndSendEmail(User admin, String templateName, String titleKey, Context context) {
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, context.getLocale());
        mailSendingService.sendEmail(admin, subject, content, false, true);
    }

    private void prepareTemplateAndSendEmailWithArgumentInSubject(User admin, String templateName, String titleKey, String argument, Context context) {
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, new Object[] { argument }, context.getLocale());
        mailSendingService.sendEmail(admin, subject, content, false, true);
    }

    private Context createBaseContext(User admin, Locale locale) {
        Context context = new Context(locale);
        context.setVariable(USER, admin);
        context.setVariable(BASE_URL, artemisServerUrl);
        return context;
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

    public void sendDataExportFailedEmailToAdmin(User admin, DataExport dataExport, Exception reason) {
        log.debug("Sending data export failed email to admin email address '{}'", admin.getEmail());
        sendDataExportFailedEmailForAdmin(admin, "mail/dataExportFailedAdminEmail", "email.dataExportFailedAdmin.title", dataExport, reason);
    }

    public void sendSuccessfulDataExportsEmailToAdmin(User admin, Set<DataExport> dataExports) {
        log.debug("Sending successful creation of data exports email to admin email address '{}'", admin.getEmail());
        sendSuccessfulDataExportsEmailToAdmin(admin, "mail/successfulDataExportsAdminEmail", "email.successfulDataExportCreationsAdmin.title", dataExports);
    }

    /**
     * Sends an email to admin users about a build agent paused itself.
     *
     * @param admin                    the admin user to notify
     * @param buildAgentName           the name of the build agent that was paused
     * @param consecutiveBuildFailures the number of consecutive build failures on the build agent
     */
    public void sendBuildAgentSelfPausedEmailToAdmin(User admin, String buildAgentName, int consecutiveBuildFailures) {
        log.debug("Sending build agent self paused email to admin email address '{}'", admin.getEmail());
        Locale locale = Locale.forLanguageTag(admin.getLangKey());
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
    public void sendVulnerabilityScanResultEmail(User admin, ComponentVulnerabilitiesDTO vulnerabilities, ArtemisVersionDTO versionInfo, boolean shouldRecommendUpgrade) {
        log.debug("Sending vulnerability scan result email to admin email address '{}'", admin.getEmail());
        Locale locale = Locale.forLanguageTag(admin.getLangKey());
        Context context = createBaseContext(admin, locale);
        context.setVariable(VULNERABILITIES, vulnerabilities);
        context.setVariable(VERSION_INFO, versionInfo);
        context.setVariable(SHOULD_RECOMMEND_UPGRADE, shouldRecommendUpgrade);
        prepareTemplateAndSendEmail(admin, "mail/vulnerabilityScanResultEmail", "email.vulnerabilityScan.title", context);
    }
}
