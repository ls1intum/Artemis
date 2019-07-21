package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
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
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTOType;
import de.tum.in.www1.artemis.web.rest.repository.util.RepositoryExecutor;

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
    abstract Repository getRepository(Long domainId, boolean pullOnCheckout)
            throws IOException, IllegalAccessException, IllegalArgumentException, InterruptedException, GitAPIException;

    abstract URL getRepositoryUrl(Long domainId);

    abstract boolean canAccessRepository(Long domainId);

    public ResponseEntity<HashMap<String, FileType>> getFiles(Long domainId) throws IOException, InterruptedException {
        log.debug("REST request to files for domainId : {}", domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            HashMap<String, FileType> fileList = repositoryService.getFiles(repository);
            return new ResponseEntity<>(fileList, HttpStatus.OK);
        });
    }

    /**
     * Get the content of a file.
     *
     * @param domainId
     * @param filename
     * @return
     * @throws IOException
     */
    public ResponseEntity<byte[]> getFile(Long domainId, String filename) throws IOException, InterruptedException {
        log.debug("REST request to file {} for domainId : {}", filename, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            byte[] out = repositoryService.getFile(repository, filename);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(out, responseHeaders, HttpStatus.OK);
        });
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
    public ResponseEntity<Void> createFile(Long domainId, String filename, HttpServletRequest request) {
        log.debug("REST request to create file {} for domainId : {}", filename, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            InputStream inputStream = request.getInputStream();
            repositoryService.createFile(repository, filename, inputStream);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Create new folder.
     *
     * @param domainId
     * @param folderName
     * @param request
     * @return
     */
    public ResponseEntity<Void> createFolder(Long domainId, String folderName, HttpServletRequest request) {
        log.debug("REST request to create file {} for domainId : {}", folderName, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            InputStream inputStream = request.getInputStream();
            repositoryService.createFolder(repository, folderName, inputStream);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Change the name of a file.
     *
     * @param domainId id of the participation the git repository belongs to.
     * @param fileMove defines current and new path in git repository.
     * @return
     */
    public ResponseEntity<Void> renameFile(Long domainId, FileMove fileMove) {
        log.debug("REST request to rename file {} to {} for domainId : {}", fileMove.getCurrentFilePath(), fileMove.getNewFilename(), domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            repositoryService.renameFile(repository, fileMove);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Delete the file or the folder specified. If the path is a folder, all files in it will be deleted, too.
     * 
     * @param domainId
     * @param filename path of file or folder to delete.
     * @return
     * @throws IOException
     */
    public ResponseEntity<Void> deleteFile(Long domainId, String filename) {
        log.debug("REST request to delete file {} for domainId : {}", filename, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            repositoryService.deleteFile(repository, filename);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Pull into the participation repository.
     *
     * @param domainId
     * @return
     * @throws IOException
     */
    public ResponseEntity<Void> pullChanges(Long domainId) {
        log.debug("REST request to commit Repository for domainId : {}", domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            repositoryService.pullChanges(repository);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Commit into the participation repository.
     *
     * @param domainId
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public ResponseEntity<Void> commitChanges(Long domainId) {
        log.debug("REST request to commit Repository for domainId : {}", domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, true);
            repositoryService.commitChanges(repository);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Reset a repository to the last commit. This will remove all staged / unstaged changes. Use with care as lost data can't be retrieved!
     *
     * @param domainId
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public ResponseEntity<Void> resetToLastCommit(Long domainId) {
        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, false);
            gitService.get().resetToOriginMaster(repository);
            return new ResponseEntity<>(HttpStatus.OK);
        });
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

        boolean hasPermissions = canAccessRepository(domainId);

        if (!hasPermissions) {
            return forbidden();
        }

        RepositoryStatusDTO repositoryStatus = new RepositoryStatusDTO();
        URL repositoryUrl = getRepositoryUrl(domainId);

        try {
            boolean isClean = repositoryService.isClean(repositoryUrl);
            repositoryStatus.setRepositoryStatus(isClean ? RepositoryStatusDTOType.CLEAN : RepositoryStatusDTOType.UNCOMMITTED_CHANGES);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            repositoryStatus.setRepositoryStatus(RepositoryStatusDTOType.CONFLICT);
        }

        return new ResponseEntity<>(repositoryStatus, HttpStatus.OK);
    }

    /**
     * This method is used to check the executed statements for exceptions. Will return an appropriate ResponseEntity for every kind of possible exception.
     * 
     * @param executor
     * @return the appropriate ResponseEntity (OK if the executor was successful or another error status code fitting the Exception).
     */
    private <T> ResponseEntity<T> executeAndCheckForExceptions(RepositoryExecutor<T> executor) {
        ResponseEntity<T> responseEntitySuccess;
        try {
            responseEntitySuccess = executor.exec();
        }
        catch (IllegalArgumentException ex) {
            return badRequest();
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        catch (FileAlreadyExistsException | CheckoutConflictException | WrongRepositoryStateException ex) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        catch (FileNotFoundException ex) {
            return notFound();
        }
        catch (GitAPIException | IOException | InterruptedException ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseEntitySuccess;
    }
}
