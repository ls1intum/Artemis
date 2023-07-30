package de.tum.in.www1.artemis.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.DataExportDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service Implementation for managing the data export in accordance with Art. 15 GDPR.
 */
@Service
public class DataExportService {

    private final int DAYS_BETWEEN_DATA_EXPORTS;

    private final UserRepository userRepository;

    private final DataExportRepository dataExportRepository;

    private final FileService fileService;

    private final Logger log = LoggerFactory.getLogger(DataExportService.class);

    public DataExportService(@Value("${artemis.data-export.days-between-data-exports:14}") int daysBetweenDataExports, UserRepository userRepository,
            DataExportRepository dataExportRepository, FileService fileService) {
        this.DAYS_BETWEEN_DATA_EXPORTS = daysBetweenDataExports;
        this.userRepository = userRepository;
        this.dataExportRepository = dataExportRepository;
        this.fileService = fileService;
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
        User user = userRepository.getUser();
        dataExport.setUser(user);
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;
    }

    /**
     * Download the data export for the given data export id.
     *
     * @param dataExport the data export to download
     * @return the file path where the data export is stored
     * @throws de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException  if the data export or the user could not be found
     * @throws de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException if the user is not allowed to download the data export
     */
    public Resource downloadDataExport(DataExport dataExport) {
        dataExport.setDownloadDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport = dataExportRepository.save(dataExport);
        var filePath = Path.of(dataExport.getFilePath());
        var finalZipFile = filePath.toFile();
        try {
            return new InputStreamResource(new FileInputStream(finalZipFile));
        }
        catch (FileNotFoundException e) {
            log.error("Could not find data export file", e);
            throw new InternalServerErrorException("Could not find data export file");
        }
    }

    /**
     * Checks if the user can download any data export.
     *
     * @return a DataExportDTO containing the id of the data export to download or null if no data export can be downloaded
     */
    public DataExportDTO canDownloadAnyDataExport() {
        var noDataExport = new DataExportDTO(null, null, null, null);
        var user = userRepository.getUser();
        var dataExportsFromUser = dataExportRepository.findAllDataExportsByUserId(user.getId());
        Optional<DataExport> latestDataExport = dataExportsFromUser.stream().max(Comparator.comparing(DataExport::getCreatedDate));
        if (dataExportsFromUser.isEmpty()) {
            return noDataExport;
        }
        for (var dataExport : dataExportsFromUser) {
            if (dataExport.getDataExportState().isDownloadable()) {
                ZonedDateTime nextRequestDate;
                nextRequestDate = retrieveNextRequestDate(dataExport);
                return new DataExportDTO(dataExport.getId(), dataExport.getDataExportState(), dataExport.getCreatedDate().atZone(ZoneId.systemDefault()), nextRequestDate);
            }
        }
        return new DataExportDTO(null, latestDataExport.get().getDataExportState(), latestDataExport.get().getCreatedDate().atZone(ZoneId.systemDefault()),
                retrieveNextRequestDate(latestDataExport.get()));
    }

    @NotNull
    private ZonedDateTime retrieveNextRequestDate(DataExport dataExport) {
        return dataExport.getCreatedDate().atZone(ZoneId.systemDefault()).plusDays(DAYS_BETWEEN_DATA_EXPORTS);
    }

    /**
     * Deletes the given data export and sets the state to DELETED or DOWNLOADED_DELETED depending on whether the export has been downloaded or not.
     *
     * @param dataExport the data export to delete
     */
    public void deleteDataExportAndSetDataExportState(DataExport dataExport) {
        if (dataExport.getFilePath() == null) {
            return;
        }
        fileService.scheduleForDirectoryDeletion(Path.of(dataExport.getFilePath()), 2);
        if (dataExport.getDataExportState().hasBeenDownloaded()) {
            dataExport.setDataExportState(DataExportState.DOWNLOADED_DELETED);
        }
        else {
            dataExport.setDataExportState(DataExportState.DELETED);
        }
        dataExportRepository.save(dataExport);
    }

}
