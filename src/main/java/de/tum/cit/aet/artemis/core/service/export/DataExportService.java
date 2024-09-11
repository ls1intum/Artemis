package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.DataExportState;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.repository.DataExportRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.web.rest.dto.DataExportDTO;
import de.tum.cit.aet.artemis.web.rest.dto.RequestDataExportDTO;

/**
 * Service Implementation for managing the data export in accordance with Art. 15 GDPR.
 * This service is responsible for downloading, deleting data exports and checking if a data export can be requested.
 * For creating data exports, see {@link DataExportCreationService}.
 */
@Profile(PROFILE_CORE)
@Service
public class DataExportService {

    private final int DAYS_BETWEEN_DATA_EXPORTS;

    private final UserRepository userRepository;

    private final DataExportRepository dataExportRepository;

    private final FileService fileService;

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);

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
    public RequestDataExportDTO requestDataExport() {
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        User user = userRepository.getUser();
        dataExport.setUser(user);
        dataExport = dataExportRepository.save(dataExport);
        return new RequestDataExportDTO(dataExport.getId(), dataExport.getDataExportState(), dataExport.getCreatedDate().atZone(ZoneId.systemDefault()));
    }

    /**
     * Request a data export for another user (not yourself) as admin.
     *
     * @param login the login of the user to create the data export for, not the login of the requesting admin user
     * @return a RequestDataExportDTO containing the id of the data export and the state of the data export
     */
    public RequestDataExportDTO requestDataExportForUserAsAdmin(String login) {
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        User user = userRepository.getUserByLoginElseThrow(login);
        dataExport.setUser(user);
        dataExport = dataExportRepository.save(dataExport);
        return new RequestDataExportDTO(dataExport.getId(), dataExport.getDataExportState(), dataExport.getCreatedDate().atZone(ZoneId.systemDefault()));
    }

    /**
     * Download the data export for the given data export id.
     *
     * @param dataExport the data export to download
     * @return the file path where the data export is stored
     * @throws EntityNotFoundException  if the data export or the user could not be found
     * @throws AccessForbiddenException if the user is not allowed to download the data export
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
        var dataExportsFromUser = dataExportRepository.findAllDataExportsByUserIdOrderByRequestDateDesc(user.getId());
        if (dataExportsFromUser.isEmpty()) {
            return noDataExport;
        }
        for (var dataExport : dataExportsFromUser) {
            if (dataExport.getDataExportState().isDownloadable()) {
                ZonedDateTime nextRequestDate = retrieveNextRequestDate(dataExport);
                return new DataExportDTO(dataExport.getId(), dataExport.getDataExportState(), dataExport.getCreatedDate().atZone(ZoneId.systemDefault()), nextRequestDate);
            }
        }
        var latestDataExport = dataExportsFromUser.getFirst();
        return new DataExportDTO(null, latestDataExport.getDataExportState(), latestDataExport.getCreatedDate().atZone(ZoneId.systemDefault()),
                retrieveNextRequestDate(latestDataExport));
    }

    /**
     * Calculates the next date when the user can request a data export.
     * <p>
     * This is the date when the last data export was requested (stored in the createdDate) + the constant DAYS_BETWEEN_DATA_EXPORTS.
     * By default, DAYS_BETWEEN_DATA_EXPORTS is set to 14 days.
     * This can be changed by setting the property artemis.data-export.days-between-data-exports in the application.yml file.
     *
     * @param dataExport the data export for which the next request date should be calculated
     * @return the next date when the user can request a data export
     */
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
        fileService.schedulePathForDeletion(Path.of(dataExport.getFilePath()), 2);
        if (dataExport.getDataExportState().hasBeenDownloaded()) {
            dataExport.setDataExportState(DataExportState.DOWNLOADED_DELETED);
        }
        else {
            dataExport.setDataExportState(DataExportState.DELETED);
        }
        dataExportRepository.save(dataExport);
    }

    /**
     * Checks if the data export can be downloaded.
     * <p>
     * The data export can be downloaded if its state is either EMAIL_SENT or DOWNLOADED.
     *
     * @param dataExport the data export to check
     * @throws AccessForbiddenException if the data export is not in a downloadable state
     */
    public void checkDataExportCanBeDownloadedElseThrow(DataExport dataExport) {
        if (!dataExport.getDataExportState().isDownloadable()) {
            throw new AccessForbiddenException("Data export has either not been created or already been deleted");
        }
    }

}
