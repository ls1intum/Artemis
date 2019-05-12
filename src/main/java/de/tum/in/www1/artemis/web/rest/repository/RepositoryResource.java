package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.FileMove;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Created by Josias Montag on 14.10.16.
 */
@RestController
@RequestMapping({ "/api", "/api_basic" })
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
public class RepositoryResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<GitService> gitService;

    private final UserService userService;

    private final RepositoryService repositoryService;

    public RepositoryResource(UserService userService, ParticipationService participationService, AuthorizationCheckService authCheckService, Optional<GitService> gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, RepositoryService repositoryService) {
        this.userService = userService;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.repositoryService = repositoryService;
    }

    /**
     * GET /repository/{participationId}/files: Map of all file and folders of the repository. Each entry states if it is a file or a folder.
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/repository/{participationId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HashMap<String, FileType>> getFiles(@PathVariable Long participationId) throws IOException, InterruptedException {
        log.debug("REST request to files for Participation : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        try {
            Repository repository = repositoryService.checkoutRepositoryByParticipation(participation);
            HashMap<String, FileType> fileList = repositoryService.getFiles(repository);
            return new ResponseEntity<>(fileList, HttpStatus.OK);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
    }

    /**
     * GET /repository/{participationId}/file: Get the content of a file
     *
     * @param participationId Participation ID
     * @param filename
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> getFile(@PathVariable Long participationId, @RequestParam("file") String filename) throws IOException, InterruptedException {
        log.debug("REST request to file {} for Participation : {}", filename, participationId);

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        try {
            byte[] out = repositoryService.getFile(repository, filename);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity(out, responseHeaders, HttpStatus.OK);
        }
        catch (FileNotFoundException ex) {
            return notFound();
        }
    }

    /**
     * POST /repository/{participationId}/file: Create new file
     *
     * @param participationId Participation ID
     * @param filename
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createFile(@PathVariable Long participationId, @RequestParam("file") String filename, HttpServletRequest request)
            throws IOException, InterruptedException {
        log.debug("REST request to create file {} for Participation : {}", filename, participationId);

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }

        InputStream inputStream = request.getInputStream();

        try {
            repositoryService.createFile(repository, filename, inputStream);
        }
        catch (FileAlreadyExistsException ex) {
            // File already existing. Conflict.
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        catch (IllegalArgumentException ex) {
            // Invalid file
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityCreationAlert("file", filename)).build();
    }

    /**
     * POST /repository/{participationId}/folder: Create new folder
     *
     * @param participationId Participation ID
     * @param folderName
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping(value = "/repository/{participationId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createFolder(@PathVariable Long participationId, @RequestParam("folder") String folderName, HttpServletRequest request)
            throws IOException, InterruptedException {
        log.debug("REST request to create file {} for Participation : {}", folderName, participationId);

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }

        InputStream inputStream = request.getInputStream();

        try {
            repositoryService.createFolder(repository, folderName, inputStream);
        }
        catch (FileAlreadyExistsException ex) {
            // File already existing. Conflict.
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        catch (IllegalArgumentException ex) {
            // Invalid file
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityCreationAlert("folder", folderName)).build();
    }

    /**
     * Change the name of a file.
     * 
     * @param participationId id of the participation the git repository belongs to.
     * @param fileMove        defines current and new path in git repository.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @PostMapping(value = "/repository/{participationId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> renameFile(@PathVariable Long participationId, @RequestBody FileMove fileMove) throws IOException, InterruptedException {

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }

        try {
            repositoryService.renameFile(repository, fileMove);
        }
        catch (FileNotFoundException ex) {
            return notFound();
        }
        catch (FileAlreadyExistsException ex) {
            // File already existing. Conflict.
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        catch (IllegalArgumentException ex) {
            // Invalid file
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert("file", fileMove.getNewFilename())).build();
    }

    /**
     * DELETE /repository/{participationId}/file: Delete the file or the folder specified. If the path is a folder, all files in it will be deleted, too.
     * 
     * @param participationId Participation ID
     * @param filename        path of file or folder to delete.
     * @return
     * @throws IOException
     */
    @DeleteMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteFile(@PathVariable Long participationId, @RequestParam("file") String filename) throws IOException, InterruptedException {
        log.debug("REST request to delete file {} for Participation : {}", filename, participationId);

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        try {
            repositoryService.deleteFile(repository, filename);
        }
        catch (FileNotFoundException ex) {
            return notFound();
        }
        catch (IllegalArgumentException ex) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("file", filename)).build();
    }

    /**
     * GET /repository/{participationId}/pull: Pull into the participation repository
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/repository/{participationId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> pullChanges(@PathVariable Long participationId) throws IOException, InterruptedException {
        log.debug("REST request to commit Repository for Participation : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        repositoryService.pullChanges(repository);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * POST /repository/{participationId}/commit: Commit into the participation repository
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @PostMapping(value = "/repository/{participationId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> commitChanges(@PathVariable Long participationId) throws IOException, InterruptedException {
        log.debug("REST request to commit Repository for Participation : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        try {
            repositoryService.commitChanges(repository);
        }
        catch (GitAPIException ex) {
            // TODO: Properly catch specific git errors
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * GET /repository/{participationId}: Get the "clean" status of the repository. Clean = No uncommitted changes.
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @GetMapping(value = "/repository/{participationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long participationId) throws IOException, GitAPIException, InterruptedException {
        log.debug("REST request to get clean status for Repository for Participation : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        RepositoryStatusDTO status = repositoryService.getStatus(repository);

        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    /**
     * GET /repository/:participationId/buildlogs : get the build log from Bamboo for the "participationId" repository.
     *
     * @param participationId the participationId of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/repository/{participationId}/buildlogs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getResultDetails(@PathVariable Long participationId) {
        log.debug("REST request to get build log : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        boolean hasPermissions = repositoryService.canAccessParticipation(participation);
        if (!hasPermissions)
            return forbidden();

        List<BuildLogEntry> logs = continuousIntegrationService.get().getLatestBuildLogs(participation);
        return new ResponseEntity<>(logs, HttpStatus.OK);
    }
}
