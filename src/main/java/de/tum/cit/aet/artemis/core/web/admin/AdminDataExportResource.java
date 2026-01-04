package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.DataExportState;
import de.tum.cit.aet.artemis.core.dto.DataExportAdminDTO;
import de.tum.cit.aet.artemis.core.dto.RequestDataExportDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.DataExportRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.export.DataExportCreationService;
import de.tum.cit.aet.artemis.core.service.export.DataExportService;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing data exports as admin.
 * Provides endpoints for listing, requesting, and downloading data exports for any user.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/core/admin/")
public class AdminDataExportResource {

    private static final Logger log = LoggerFactory.getLogger(AdminDataExportResource.class);

    private final DataExportService dataExportService;

    private final DataExportCreationService dataExportCreationService;

    private final DataExportRepository dataExportRepository;

    public AdminDataExportResource(DataExportService dataExportService, DataExportCreationService dataExportCreationService, DataExportRepository dataExportRepository) {
        this.dataExportService = dataExportService;
        this.dataExportCreationService = dataExportCreationService;
        this.dataExportRepository = dataExportRepository;
    }

    /**
     * GET /data-exports: Get all data exports with user information, with pagination support.
     *
     * @param pageable the pagination information (page, size, sort)
     * @return the ResponseEntity with status 200 (OK) and with body a list of data exports,
     *         and pagination headers (X-Total-Count, Link)
     */
    @GetMapping("data-exports")
    public ResponseEntity<List<DataExportAdminDTO>> getAllDataExports(Pageable pageable) {
        log.debug("REST request to get data exports as admin, pageable={}", pageable);
        Page<DataExport> dataExports = dataExportRepository.findAllWithUserOrderByCreatedDateDesc(pageable);
        List<DataExportAdminDTO> dtos = dataExports.getContent().stream().map(DataExportAdminDTO::of).toList();
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), dataExports);
        return new ResponseEntity<>(dtos, headers, HttpStatus.OK);
    }

    /**
     * POST /data-exports/{login}: Request a data export for the given user as admin.
     * Optionally execute the export immediately instead of scheduling it.
     *
     * @param login      the login of the user to request the data export for
     * @param executeNow if true, execute the export immediately instead of scheduling it (optional, defaults to false)
     * @return the ResponseEntity with status 200 (OK) and with body a DTO containing the id, the state and the request date of the data export
     */
    @PostMapping("data-exports/{login}")
    public ResponseEntity<RequestDataExportDTO> requestDataExportForUser(@PathVariable String login, @RequestParam(defaultValue = "false") boolean executeNow) {
        log.debug("REST request to create data export for user {} with executeNow={}", login, executeNow);
        RequestDataExportDTO result = dataExportService.requestDataExportForUserAsAdmin(login);

        if (executeNow) {
            DataExport dataExport = dataExportRepository.findByIdElseThrow(result.id());
            log.info("Executing data export immediately for user {}", login);
            boolean success = dataExportCreationService.createDataExport(dataExport);
            if (success) {
                // Reload the data export to get the updated state
                dataExport = dataExportRepository.findByIdElseThrow(result.id());
                result = new RequestDataExportDTO(dataExport.getId(), dataExport.getDataExportState(),
                        dataExport.getCreatedDate() != null ? dataExport.getCreatedDate().atZone(java.time.ZoneId.systemDefault()) : null);
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * GET /data-exports/{dataExportId}/download: Download a data export as admin.
     *
     * @param dataExportId the id of the data export to download
     * @return the ResponseEntity with status 200 (OK) and with body the resource containing the data export zip file
     * @throws EntityNotFoundException  if the data export could not be found
     * @throws AccessForbiddenException if the data export cannot be downloaded
     */
    @GetMapping("data-exports/{dataExportId}/download")
    public ResponseEntity<Resource> downloadDataExportAsAdmin(@PathVariable long dataExportId) {
        log.debug("REST request to download data export {} as admin", dataExportId);
        DataExport dataExport = dataExportRepository.findByIdWithUser(dataExportId).orElseThrow(() -> new EntityNotFoundException("DataExport", dataExportId));

        // Check if the export can be downloaded (has a file and is in the right state)
        if (dataExport.getFilePath() == null) {
            throw new AccessForbiddenException("Data export file is not available (may have been deleted or not yet created)");
        }
        dataExportService.checkDataExportCanBeDownloadedElseThrow(dataExport);

        Resource resource = dataExportService.downloadDataExport(dataExport);
        File finalZipFile = Path.of(dataExport.getFilePath()).toFile();
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment").filename(finalZipFile.getName()).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        return ResponseEntity.ok().contentLength(finalZipFile.length()).headers(headers).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", finalZipFile.getName())
                .body(resource);
    }

    /**
     * DELETE /data-exports/{dataExportId}: Cancel a pending data export as admin.
     * Only data exports in REQUESTED or IN_CREATION state can be cancelled.
     *
     * @param dataExportId the id of the data export to cancel
     * @return the ResponseEntity with status 200 (OK)
     * @throws EntityNotFoundException  if the data export could not be found
     * @throws BadRequestAlertException if the data export cannot be cancelled (wrong state)
     */
    @DeleteMapping("data-exports/{dataExportId}")
    public ResponseEntity<Void> cancelDataExport(@PathVariable long dataExportId) {
        log.debug("REST request to cancel data export {} as admin", dataExportId);
        DataExport dataExport = dataExportRepository.findByIdWithUser(dataExportId).orElseThrow(() -> new EntityNotFoundException("DataExport", dataExportId));

        // Only allow cancelling exports in REQUESTED or IN_CREATION state
        if (dataExport.getDataExportState() != DataExportState.REQUESTED && dataExport.getDataExportState() != DataExportState.IN_CREATION) {
            throw new BadRequestAlertException("Only pending data exports can be cancelled", "dataExport", "cannotCancel");
        }

        log.info("Admin cancelling data export {} for user {}", dataExportId, dataExport.getUser().getLogin());
        dataExportRepository.delete(dataExport);

        return ResponseEntity.ok().build();
    }
}
