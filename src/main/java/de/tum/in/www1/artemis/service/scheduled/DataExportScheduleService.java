package de.tum.in.www1.artemis.service.scheduled;

import java.time.*;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.ProfileService;

/**
 * Service responsible for scheduling data exports.
 */
@Service
@Profile("scheduling")
public class DataExportScheduleService {

    private final ProfileService profileService;

    private final TaskScheduler scheduler;

    private final DataExportRepository dataExportRepository;

    private final DataExportCreationService dataExportCreationService;

    private final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

    public DataExportScheduleService(ProfileService profileService, @Qualifier("taskScheduler") TaskScheduler scheduler, DataExportRepository dataExportRepository,
            DataExportCreationService dataExportCreationService) {
        this.profileService = profileService;
        this.scheduler = scheduler;
        this.dataExportRepository = dataExportRepository;
        this.dataExportCreationService = dataExportCreationService;

    }

    /**
     * Schedule data exports on startup. All data export that are either in the state REQUESTED or IN_CREATION will be scheduled.
     */
    @PostConstruct
    public void scheduleDataExportOnStartup() {
        try {
            if (profileService.isDev()) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }
            SecurityUtils.setAuthorizationObject();
            var dataExportsToBeScheduled = dataExportRepository.findAllThatNeedToBeScheduled();

            for (var dataExport : dataExportsToBeScheduled) {
                scheduleDataExportCreation(dataExport);
            }

            log.info("Scheduled data exports on start up.");
        }
        catch (Exception exception) {
            log.error("Failed to start DataExportScheduleService", exception);
        }
    }

    private Runnable createDataExport(DataExport dataExport) {
        return () -> {
            checkSecurityUtils();
            dataExportCreationService.createDataExport(dataExport);
            scheduleDataExportDeletion(dataExport);
        };
    }

    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }

    /**
     * Schedule the creation of a single data export
     *
     * @param dataExport the data export to be created
     */
    public void scheduleDataExportCreation(DataExport dataExport) {
        log.info("Scheduling data export for {}", dataExport.getUser().getLogin());
        scheduler.schedule(createDataExport(dataExport), retrieveScheduledDateTime());

    }

    /**
     * Schedule the deletion of a single data export
     *
     * @param dataExport the data export to be deleted
     */
    public void scheduleDataExportDeletion(DataExport dataExport) {
        scheduler.schedule(deleteDataExport(dataExport), ZonedDateTime.now().plusDays(7).toInstant());
    }

    private Runnable deleteDataExport(DataExport dataExport) {
        return () -> {
            checkSecurityUtils();
            dataExportCreationService.deleteDataExportAndSetDataExportState(dataExport);
        };
    }

    private Instant retrieveScheduledDateTime() {
        LocalDate currentDate = LocalDate.now();
        LocalTime startTime = LocalTime.of(4, 0);
        LocalDate day = currentDate;
        // only add one day if the data export is scheduled after 4 am and before midnight
        if (LocalTime.now().isAfter(startTime)) {
            day = currentDate.plusDays(1);
        }

        return day.atTime(startTime).atZone(ZoneId.systemDefault()).toInstant();
    }

}
