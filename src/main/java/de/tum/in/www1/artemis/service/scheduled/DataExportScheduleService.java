package de.tum.in.www1.artemis.service.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.service.ProfileService;

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

    private final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

    public DataExportScheduleService(DataExportRepository dataExportRepository, DataExportCreationService dataExportCreationService, DataExportService dataExportService,
            ProfileService profileService) {
        this.dataExportRepository = dataExportRepository;
        this.dataExportCreationService = dataExportCreationService;
        this.dataExportService = dataExportService;
        this.profileService = profileService;
    }

    /**
     * Schedule data export creation and deletion.
     * Created will be all data exports that are in the state REQUESTED OR IN_CREATION
     * Deleted will be all data exports that have a creation date older than seven days
     */
    // TODO change the cron expression again once the testing is done.
    // @Scheduled(cron = "0 0 4 * * *") // execute this every night at 4:00:00 am
    @Scheduled(cron = "0 0/2 * * * *") // execute this every 2 minutes
    public void createDataExportsAndDeleteOldOnes() {
        if (profileService.isDev()) {
            // do not execute this in a development environment
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }

        checkSecurityUtils();
        log.info("Creating data exports and deleting old ones");
        var dataExportsToBeCreated = dataExportRepository.findAllToBeCreated();
        dataExportsToBeCreated.forEach(this::createDataExport);
        var dataExportsToBeDeleted = dataExportRepository.findAllToBeDeleted();
        dataExportsToBeDeleted.forEach(this::deleteDataExport);
    }

    /**
     * Create a single data export
     *
     * @param dataExport the data export to be created
     */
    private void createDataExport(DataExport dataExport) {
        checkSecurityUtils();
        log.info("Creating data export for {}", dataExport.getUser().getLogin());
        dataExportCreationService.createDataExport(dataExport);
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
        checkSecurityUtils();
        dataExportService.deleteDataExportAndSetDataExportState(dataExport);
    }

}
