package de.tum.in.www1.artemis.service.scheduled;

import java.time.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
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

    private final TaskScheduler scheduler;

    private final DataExportRepository dataExportRepository;

    private final DataExportCreationService dataExportCreationService;

    private final DataExportService dataExportService;

    private final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

    public DataExportScheduleService(@Qualifier("taskScheduler") TaskScheduler scheduler, DataExportRepository dataExportRepository,
            DataExportCreationService dataExportCreationService, DataExportService dataExportService) {
        this.scheduler = scheduler;
        this.dataExportRepository = dataExportRepository;
        this.dataExportCreationService = dataExportCreationService;
        this.dataExportService = dataExportService;
    }

    /**
     * Schedule data exports on startup. All data export that are either in the state REQUESTED or IN_CREATION will be scheduled.
     */
    @Scheduled(cron = "0 0 4 * * *") // execute this every night at 4:00:00 am
    public void createDataExports() {
        log.info("Creating data exports");
        var dataExports = dataExportRepository.findAllThatNeedToBeScheduled();
        dataExports.forEach(this::createDataExport);
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
        scheduleDataExportDeletion(dataExport);
    }

    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }

    /**
     * Schedule the deletion of a single data export
     *
     * @param dataExport the data export to be deleted
     */
    private void scheduleDataExportDeletion(DataExport dataExport) {
        scheduler.schedule(deleteDataExport(dataExport), ZonedDateTime.now().plusDays(7).toInstant());
    }

    private Runnable deleteDataExport(DataExport dataExport) {
        return () -> {
            checkSecurityUtils();
            dataExportService.deleteDataExportAndSetDataExportState(dataExport);
        };
    }

}
