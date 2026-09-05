package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.dto.DuplicateUserEmailReportDTO;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;

/**
 * Sends a weekly, privacy-minimized warning to the main administrator while installations prepare for unique user emails.
 */
@Lazy
@Service
@Profile(PROFILE_CORE)
public class DuplicateUserEmailScheduleService {

    static final int MAX_ACCOUNT_IDS_IN_EMAIL = 100;

    private static final Logger log = LoggerFactory.getLogger(DuplicateUserEmailScheduleService.class);

    @Value("${info.contact:}")
    private String adminEmail;

    @Value("${info.testServer:false}")
    private boolean isTestServer;

    private final UserRepository userRepository;

    private final MailService mailService;

    private final ProfileService profileService;

    public DuplicateUserEmailScheduleService(UserRepository userRepository, MailService mailService, ProfileService profileService) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.profileService = profileService;
    }

    /**
     * Checks for duplicate user emails every Monday at 8:00 AM by default. Only the scheduling production node sends the report.
     */
    @Scheduled(cron = "${artemis.scheduling.duplicate-user-email-report-time:0 0 8 * * MON}")
    public void checkForDuplicatedUserEmailsAndNotifyAdmin() {
        if (!profileService.isSchedulingActive() || profileService.isDevActive() || isTestServer) {
            return;
        }
        sendDuplicateUserEmailReport();
    }

    /**
     * Sends a report if duplicate emails exist and the main administrator address is configured.
     *
     * @return whether a report was sent
     */
    public boolean sendDuplicateUserEmailReport() {
        if (!StringUtils.hasText(adminEmail)) {
            log.warn("Admin email (info.contact) is not configured. Cannot send duplicate user email report.");
            return false;
        }

        try {
            List<Long> affectedAccountIds = userRepository.findUserIdsWithDuplicatedEmail();
            if (affectedAccountIds.isEmpty()) {
                return false;
            }

            List<Long> includedAccountIds = affectedAccountIds.stream().limit(MAX_ACCOUNT_IDS_IN_EMAIL).toList();
            DuplicateUserEmailReportDTO report = new DuplicateUserEmailReportDTO(affectedAccountIds.size(), includedAccountIds);
            MailRecipientDTO recipient = new MailRecipientDTO(adminEmail, "en", "duplicate-user-email-report-recipient", "Administrator", null, null, null);
            mailService.sendDuplicateUserEmailReportEmail(recipient, report);
            log.warn("Found {} accounts with duplicated email addresses. Sent a remediation report to the configured administrator.", affectedAccountIds.size());
            return true;
        }
        catch (Exception e) {
            log.error("Failed to check for duplicated user emails or send the administrator report", e);
            return false;
        }
    }
}
