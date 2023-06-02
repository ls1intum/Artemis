package de.tum.in.www1.artemis.service.scheduled;

import java.io.IOException;
import java.time.*;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.service.ProfileService;

@Service
@Profile("scheduling")
public class DataExportScheduleService {

    private final ProfileService profileService;

    private final TaskScheduler scheduler;

    private final DataExportRepository dataExportRepository;

    private final DataExportService dataExportService;

    private final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

    public DataExportScheduleService(ProfileService profileService, TaskScheduler scheduler, DataExportRepository dataExportRepository, DataExportService dataExportService) {
        this.profileService = profileService;
        this.scheduler = scheduler;
        this.dataExportRepository = dataExportRepository;
        this.dataExportService = dataExportService;
    }

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

            ZoneOffset zoneOffset = ZonedDateTime.now().getZone().getRules().getOffset(Instant.now());
            for (var dataExport : dataExportsToBeScheduled) {

            }
            // For local testing
            // scheduler.scheduleAtFixedRate(scheduleEmailSummaries(), ZonedDateTime.now().toLocalDateTime().toInstant(zoneOffset), Duration.ofMinutes(3));

            log.info("Scheduled data exports on start up.");
        }
        catch (Exception exception) {
            log.error("Failed to start DataExportScheduleService", exception);
        }
    }

    Runnable scheduleDataExport(DataExport dataExport) {
        return () -> {
            checkSecurityUtils();
            try {
                dataExportService.createDataExport(dataExport);
            }
            catch (IOException e) {
                handleCreationFailure(dataExport);
            }
        };
    }

    private void handleCreationFailure(DataExport dataExport) {
        dataExport.setDataExportState(DataExportState.FAILED);
        dataExportRepository.save(dataExport);

    }

    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }

    public void scheduleDataExportCreation(DataExport dataExport) {
        LocalDate currentDate = LocalDate.now();
        LocalTime startTime = LocalTime.of(4, 0);
        LocalDate nextDay = currentDate.plusDays(1);
        Instant scheduledTime = nextDay.atTime(startTime).atZone(ZoneId.systemDefault()).toInstant();
        scheduler.schedule(scheduleDataExport(dataExport), scheduledTime);

    }
}
