package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.scheduled.DataExportScheduleService;
import de.tum.in.www1.artemis.web.rest.dto.DataExportDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service Implementation for managing the data export in accordance with Art. 15 GDPR.
 */
@Service
public class DataExportService {

    private static final int DAYS_BETWEEN_DATA_EXPORTS = 14;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final DataExportRepository dataExportRepository;

    private final DataExportScheduleService dataExportScheduleService;

    public DataExportService(UserRepository userRepository, AuthorizationCheckService authorizationCheckService, DataExportRepository dataExportRepository,
            DataExportScheduleService dataExportScheduleService) {
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.dataExportRepository = dataExportRepository;
        this.dataExportScheduleService = dataExportScheduleService;
    }

    /**
     * Requests a data export for the given user.
     * This will create a new DataExport object in the database and start the creation of the data export.
     *
     * @return the created DataExport object
     */
    public DataExport requestDataExport() throws IOException {
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        dataExport.setUser(user);
        dataExport.setRequestDate(ZonedDateTime.now());
        dataExport = dataExportRepository.save(dataExport);
        dataExportScheduleService.scheduleDataExportCreation(dataExport);
        return dataExport;
    }

    /**
     * Download the data export for the given data export id.
     *
     * @param dataExportId the id of the data export to download
     * @return the file path where the data export is stored
     * @throws de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException  if the data export or the user could not be found
     * @throws de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException if the user is not allowed to download the data export
     */
    public Path downloadDataExport(long dataExportId) {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        authorizationCheckService.currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        checkDataExportCanBeDownloaded(dataExport);

        dataExport.setDownloadDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport = dataExportRepository.save(dataExport);
        return Path.of(dataExport.getFilePath());
    }

    private void checkDataExportCanBeDownloaded(DataExport dataExport) {
        if (!dataExport.getDataExportState().isDownloadable()) {
            throw new AccessForbiddenException("Data export has either not been created or already been deleted");
        }
    }

    public boolean canRequestDataExport() {
        var user = userRepository.getUserWithDataExports();
        if (user.getDataExports().isEmpty()) {
            return true;
        }
        var latestDataExport = user.getDataExports().stream().max(Comparator.comparing(DataExport::getRequestDate));
        if (latestDataExport.isEmpty()) {
            return true;
        }
        var latestDataExportCreationDate = latestDataExport.get().getRequestDate();
        // allow requesting a new data export if the latest data export is older than 14 days or its creation has failed
        return Duration.between(latestDataExportCreationDate, ZonedDateTime.now()).toDays() >= DAYS_BETWEEN_DATA_EXPORTS || latestDataExport.get().getDataExportState().hasFailed();
    }

    public DataExportDTO canDownloadAnyDataExport() {
        var cannotDownload = new DataExportDTO(null);
        var user = userRepository.getUserWithDataExports();
        if (user.getDataExports().isEmpty()) {
            return cannotDownload;
        }
        var latestDataExportOptional = user.getDataExports().stream().max(Comparator.comparing(DataExport::getRequestDate));
        if (latestDataExportOptional.isEmpty()) {
            return cannotDownload;
        }
        var latestDataExport = latestDataExportOptional.get();
        // either the latest data export is downloadable or none of the data exports are
        if (latestDataExport.getDataExportState().isDownloadable()) {
            return new DataExportDTO(latestDataExport.getId());
        }
        else {
            return cannotDownload;
        }
    }

    public boolean canDownloadSpecificDataExport(long dataExportId) {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        authorizationCheckService.currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        return dataExport.getDataExportState().isDownloadable();
    }

}
