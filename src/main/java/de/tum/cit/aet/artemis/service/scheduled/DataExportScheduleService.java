package de.tum.cit.aet.artemis.service.scheduled;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.DataExportRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.export.DataExportCreationService;
import de.tum.cit.aet.artemis.service.export.DataExportService;
import de.tum.cit.aet.artemis.service.notifications.MailService;
import de.tum.cit.aet.artemis.service.user.UserService;

/**
 * Service responsible for scheduling data exports.
 */
@Service
@Profile(PROFILE_SCHEDULING)
public class DataExportScheduleService {

    private final DataExportRepository dataExportRepository;

    private final DataExportCreationService dataExportCreationService;

    private final DataExportService dataExportService;

    private final ProfileService profileService;

    private final MailService mailService;

    private final UserService userService;

    private static final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

    public DataExportScheduleService(DataExportRepository dataExportRepository, DataExportCreationService dataExportCreationService, DataExportService dataExportService,
            ProfileService profileService, MailService mailService, UserService userService) {
        this.dataExportRepository = dataExportRepository;
        this.dataExportCreationService = dataExportCreationService;
        this.dataExportService = dataExportService;
        this.profileService = profileService;
        this.mailService = mailService;
        this.userService = userService;
    }

    /**
     * Schedule data export creation and deletion.
     * Created will be all data exports that are in the state REQUESTED OR IN_CREATION
     * Deleted will be all data exports that have a creation date older than seven days
     */
    @Scheduled(cron = "${artemis.scheduling.data-export-creation-time: 0 0 4 * * *}")
    public void createDataExportsAndDeleteOldOnes() throws InterruptedException {
        if (profileService.isDevActive()) {
            // do not execute this in a development environment
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }
        checkSecurityUtils();
        log.info("Creating data exports and deleting old ones");
        Set<DataExport> successfulDataExports = Collections.synchronizedSet(new HashSet<>());
        var dataExportsToBeCreated = dataExportRepository.findAllToBeCreated();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        dataExportsToBeCreated.forEach(dataExport -> executor.execute(() -> createDataExport(dataExport, successfulDataExports)));
        executor.shutdown();
        ZonedDateTime thresholdDate = ZonedDateTime.now().minusDays(7);
        var dataExportsToBeDeleted = dataExportRepository.findAllToBeDeleted(thresholdDate);
        dataExportsToBeDeleted.forEach(this::deleteDataExport);
        Optional<User> admin = userService.findInternalAdminUser();
        if (admin.isEmpty()) {
            log.warn("No internal admin user found. Cannot send email to admin about successful creation of data exports.");
            return;
        }
        // This job runs at 4 am by default and the next scheduled job runs at 5 am, so we should allow 60 minutes for the creation.
        // If the creation doesn't finish within 60 minutes, all pending exports will be picked up when the job runs the next time.
        if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.MINUTES)) {
            log.info("Not all pending data exports could be created within 60 minutes.");
            executor.shutdownNow();
        }
        if (!successfulDataExports.isEmpty()) {
            mailService.sendSuccessfulDataExportsEmailToAdmin(admin.get(), successfulDataExports);
        }
    }

    /**
     * Create a single data export
     *
     * @param dataExport the data export to be created
     */
    private void createDataExport(DataExport dataExport, Set<DataExport> successfulDataExports) {
        checkSecurityUtils();
        log.info("Creating data export for {}", dataExport.getUser().getLogin());
        var successful = dataExportCreationService.createDataExport(dataExport);
        if (successful) {
            successfulDataExports.add(dataExport);
        }
    }

    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }

    /**
     * Delete a single data export
     *
     * @param dataExport the data export to be deleted
     */
    private void deleteDataExport(DataExport dataExport) {
        log.info("Deleting data export for {}", dataExport.getUser().getLogin());
        dataExportService.deleteDataExportAndSetDataExportState(dataExport);
    }

}
