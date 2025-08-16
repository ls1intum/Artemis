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

import de.tum.cit.aet.artemis.communication.dto.MailTemplateDTO;
import de.tum.cit.aet.artemis.communication.dto.MailUserDTO;
import de.tum.cit.aet.artemis.core.domain.DataExport;

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
     * Sends predefined mail based on a template
     *
     * @param mailTemplateDTO the template containing the user and the template name
     */
    public void sendEmailFromTemplate(MailTemplateDTO mailTemplateDTO) {
        Context context = createBaseContext(mailTemplateDTO.userDTO());
        prepareTemplateAndSendEmail(mailTemplateDTO, context);
    }

    private void prepareTemplateAndSendEmail(MailTemplateDTO mailTemplateDTO, Context context) {
        String content = templateEngine.process(mailTemplateDTO.templateName(), context);
        String subject = messageSource.getMessage(mailTemplateDTO.titleKey(), null, context.getLocale());
        mailSendingService.sendEmail(mailTemplateDTO.userDTO(), subject, content, false, true);
    }

    private void prepareTemplateAndSendEmailWithArgumentInSubject(MailTemplateDTO mailTemplateDTO, String argument, Context context) {
        String content = templateEngine.process(mailTemplateDTO.templateName(), context);
        String subject = messageSource.getMessage(mailTemplateDTO.titleKey(), new Object[] { argument }, context.getLocale());
        mailSendingService.sendEmail(mailTemplateDTO.userDTO(), subject, content, false, true);
    }

    private Context createBaseContext(MailUserDTO userDTO) {
        Locale locale = Locale.forLanguageTag(userDTO.languageKey());
        Context context = new Context(locale);
        context.setVariable(USER, userDTO);
        context.setVariable(BASE_URL, artemisServerUrl);
        return context;
    }

    public void sendActivationEmail(MailUserDTO userDTO) {
        log.debug("Sending activation email to '{}'", userDTO.email());
        MailTemplateDTO mailTemplateDTO = new MailTemplateDTO("mail/activationEmail", "email.activation.title", userDTO);
        sendEmailFromTemplate(mailTemplateDTO);
    }

    public void sendPasswordResetMail(MailUserDTO userDTO) {
        log.debug("Sending password reset email to '{}'", userDTO.email());
        MailTemplateDTO mailTemplateDTO = new MailTemplateDTO("mail/passwordResetEmail", "email.reset.title", userDTO);
        sendEmailFromTemplate(mailTemplateDTO);
    }

    public void sendSAML2SetPasswordMail(MailUserDTO userDTO) {
        log.debug("Sending SAML2 set password email to '{}'", userDTO.email());
        MailTemplateDTO mailTemplateDTO = new MailTemplateDTO("mail/samlSetPasswordEmail", "email.saml.title", userDTO);
        sendEmailFromTemplate(mailTemplateDTO);
    }

    /**
     * Sends an email to a user (the internal admin user) about a failed data export
     * creation.
     *
     * @param adminDTO   the admin user
     * @param dataExport the data export that failed
     * @param reason     the exception that caused the data export to fail
     */
    public void sendDataExportFailedEmailToAdmin(MailUserDTO adminDTO, DataExport dataExport, Exception reason) {
        log.debug("Sending data export failed email to admin email address '{}'", adminDTO.email());
        MailTemplateDTO mailTemplateDTO = new MailTemplateDTO("mail/dataExportFailedAdminEmail", "email.dataExportFailedAdmin.title", adminDTO);
        Context context = createBaseContext(adminDTO);
        context.setVariable(DATA_EXPORT, dataExport);
        context.setVariable(REASON, reason.getMessage());
        prepareTemplateAndSendEmailWithArgumentInSubject(mailTemplateDTO, dataExport.getUser().getLogin(), context);
    }

    /**
     * Sends an email to admin users about successful data export creations.
     *
     * @param adminDTO    the admin user to notify
     * @param dataExports the set of data exports
     */
    public void sendSuccessfulDataExportsEmailToAdmin(MailUserDTO adminDTO, Set<DataExport> dataExports) {
        log.debug("Sending successful creation of data exports email to admin email address '{}'", adminDTO.email());
        MailTemplateDTO mailTemplateDTO = new MailTemplateDTO("mail/successfulDataExportsAdminEmail", "email.successfulDataExportCreationsAdmin.title", adminDTO);
        Context context = createBaseContext(adminDTO);
        context.setVariable(DATA_EXPORTS, dataExports);
        prepareTemplateAndSendEmail(mailTemplateDTO, context);
    }

    /**
     * Sends an email to admin users about a build agent paused itself.
     *
     * @param adminDTO                 the admin user to notify
     * @param buildAgentName           the name of the build agent that was paused
     * @param consecutiveBuildFailures the number of consecutive build failures on
     *                                     the build agent
     */
    public void sendBuildAgentSelfPausedEmailToAdmin(MailUserDTO adminDTO, String buildAgentName, int consecutiveBuildFailures) {
        log.debug("Sending build agent self paused email to admin email address '{}'", adminDTO.email());
        Context context = createBaseContext(adminDTO);
        context.setVariable(BUILD_AGENT_NAME, buildAgentName);
        context.setVariable(CONSECUTIVE_BUILD_FAILURES, consecutiveBuildFailures);
        MailTemplateDTO mailTemplateDTO = new MailTemplateDTO("mail/buildAgentSelfPausedEmail", "email.buildAgent.SelfPaused.title", adminDTO);
        prepareTemplateAndSendEmail(mailTemplateDTO, context);
    }
}
