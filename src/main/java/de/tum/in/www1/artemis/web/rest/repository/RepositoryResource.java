package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.FileMove;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

/**
 * Abstract class that can be extended to make repository endpoints available that retrieve the repository based on the implemented method getRepository. This way the retrieval of
 * the repository and the permission checks can be outsourced to child classes. The domain could be any ID needed to make API calls (exercise, participation, etc.).
 */
public abstract class RepositoryResource {

    protected final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    protected final AuthorizationCheckService authCheckService;

    protected final Optional<ContinuousIntegrationService> continuousIntegrationService;

    protected final Optional<GitService> gitService;

    protected final UserService userService;

    protected final RepositoryService repositoryService;

    public RepositoryResource(UserService userService, AuthorizationCheckService authCheckService, Optional<GitService> gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, RepositoryService repositoryService) {
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.repositoryService = repositoryService;
    }

    /**
     * Override this method to define how a repository can be retrieved.
     * 
     * @param domainId
     * @return
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InterruptedException
     */
    abstract Repository getRepository(Long domainId) throws IOException, IllegalArgumentException, IllegalAccessException, InterruptedException;

    public ResponseEntity<HashMap<String, FileType>> getFiles(Long domainId) throws IOException, InterruptedException {
        log.debug("REST request to files for domainId : {}", domainId);

        try {
            Repository repository = getRepository(domainId);
            HashMap<String, FileType> fileList = repositoryService.getFiles(repository);
            return new ResponseEntity<>(fileList, HttpStatus.OK);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
    }

    /**
     * Get the content of a file.
     *
     * @param domainId
     * @param filename
     * @return
     * @throws IOException
     */
    public ResponseEntity<String> getFile(Long domainId, String filename) throws IOException, InterruptedException {
        log.debug("REST request to file {} for domainId : {}", filename, domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
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
     * Create new file.
     *
     * @param domainId
     * @param filename
     * @param request
     * @return
     * @throws IOException
     */
    public ResponseEntity<Void> createFile(Long domainId, String filename, HttpServletRequest request) throws IOException, InterruptedException {
        log.debug("REST request to create file {} for domainId : {}", filename, domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
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

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Create new folder
     *
     * @param domainId
     * @param folderName
     * @param request
     * @return
     * @throws IOException
     */
    public ResponseEntity<Void> createFolder(Long domainId, String folderName, HttpServletRequest request) throws IOException, InterruptedException {
        log.debug("REST request to create file {} for domainId : {}", folderName, domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
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

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Change the name of a file.
     *
     * @param domainId id of the participation the git repository belongs to.
     * @param fileMove defines current and new path in git repository.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public ResponseEntity<Void> renameFile(Long domainId, FileMove fileMove) throws IOException, InterruptedException {
        log.debug("REST request to rename file {} to {} for domainId : {}", fileMove.getCurrentFilePath(), fileMove.getNewFilename(), domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
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

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Delete the file or the folder specified. If the path is a folder, all files in it will be deleted, too.
     * 
     * @param domainId
     * @param filename path of file or folder to delete.
     * @return
     * @throws IOException
     */
    public ResponseEntity<Void> deleteFile(Long domainId, String filename) throws IOException, InterruptedException {
        log.debug("REST request to delete file {} for domainId : {}", filename, domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
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
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Pull into the participation repository.
     *
     * @param domainId
     * @return
     * @throws IOException
     */
    public ResponseEntity<Void> pullChanges(Long domainId) throws IOException, InterruptedException {
        log.debug("REST request to commit Repository for domainId : {}", domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        repositoryService.pullChanges(repository);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Commit into the participation repository.
     *
     * @param domainId
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public ResponseEntity<Void> commitChanges(Long domainId) throws IOException, InterruptedException {
        log.debug("REST request to commit Repository for domainId : {}", domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
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
     * Get the "clean" status of the repository. Clean = No uncommitted changes.
     *
     * @param domainId
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public ResponseEntity<RepositoryStatusDTO> getStatus(Long domainId) throws IOException, GitAPIException, InterruptedException {
        log.debug("REST request to get clean status for Repository for domainId : {}", domainId);

        Repository repository;
        try {
            repository = getRepository(domainId);
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        RepositoryStatusDTO status = repositoryService.getStatus(repository);

        return new ResponseEntity<>(status, HttpStatus.OK);
    }
}
