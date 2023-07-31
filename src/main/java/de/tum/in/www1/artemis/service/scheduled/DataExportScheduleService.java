package de.tum.in.www1.artemis.service.scheduled;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.dataexport.DataExportCreationService;
import de.tum.in.www1.artemis.service.dataexport.DataExportService;
import de.tum.in.www1.artemis.service.notifications.MailService;
import de.tum.in.www1.artemis.service.user.UserService;

/**
 * Service responsible for scheduling data exports.
 */
@Service
@Profile("scheduling")
public class DataExportScheduleService {

    private final DataExportRepository dataExportRepository;

    private final DataExportCreationService dataExportCreationService;

    private final DataExportService dataExportService;

    private final ProfileService profileService;

    private final MailService mailService;

    private final UserService userService;

    private final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

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
    public void createDataExportsAndDeleteOldOnes() {
        if (profileService.isDev()) {
            // do not execute this in a development environment
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }

        checkSecurityUtils();
        log.info("Creating data exports and deleting old ones");
        Set<DataExport> successfulDataExports = new HashSet<>();
        var dataExportsToBeCreated = dataExportRepository.findAllToBeCreated();
        dataExportsToBeCreated.forEach(dataExport -> createDataExport(dataExport, successfulDataExports));
        var dataExportsToBeDeleted = dataExportRepository.findAllToBeDeleted();
        dataExportsToBeDeleted.forEach(this::deleteDataExport);
        Optional<User> admin = userService.findInternalAdminUser();
        if (admin.isEmpty()) {
            log.warn("No internal admin user found. Cannot send email to admin about successful creation of data exports.");
            return;
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
