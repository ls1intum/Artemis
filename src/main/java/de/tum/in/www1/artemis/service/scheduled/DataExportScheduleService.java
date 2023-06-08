package de.tum.in.www1.artemis.service.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.DataExportService;

/**
 * Service responsible for scheduling data exports.
 */
@Service
@Profile("scheduling")
public class DataExportScheduleService {

    private final DataExportRepository dataExportRepository;

    private final DataExportCreationService dataExportCreationService;

    private final DataExportService dataExportService;

    private final Environment env;

    private final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

    public DataExportScheduleService(DataExportRepository dataExportRepository, DataExportCreationService dataExportCreationService, DataExportService dataExportService,
            Environment env) {
        this.dataExportRepository = dataExportRepository;
        this.dataExportCreationService = dataExportCreationService;
        this.dataExportService = dataExportService;
        this.env = env;
    }

    /**
     * Schedule data export creation and deletion.
     * Created will be all data exports that are in the state REQUESTED OR IN_CREATION
     * Deleted will be all data exports that have a creation date older than seven days
     */
    @Scheduled(cron = "0 0 4 * * *") // execute this every night at 4:00:00 am
    public void createDataExportsAndDeleteOldOnes() {
        checkSecurityUtils();
        log.info("Creating data exports and deleting old ones");
        var dataExportsToBeCreated = dataExportRepository.findAllToBeCreated();
        dataExportsToBeCreated.forEach(this::createDataExport);
        var dataExportsToBeDeleted = dataExportRepository.findAllToBeDeleted();
        dataExportsToBeDeleted.forEach(this::deleteDataExport);
    }

    /**
     * Create a single data export and schedule its deletion one week after creation.
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

    private void deleteDataExport(DataExport dataExport) {
        checkSecurityUtils();
        dataExportService.deleteDataExportAndSetDataExportState(dataExport);
    }

}
