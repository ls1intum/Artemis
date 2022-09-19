package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTOType.*;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTOType;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.repository.util.RepositoryExecutor;

/**
 * Abstract class that can be extended to make repository endpoints available that retrieve the repository based on the implemented method getRepository. This way the retrieval of
 * the repository and the permission checks can be outsourced to child classes. The domain could be any ID needed to make API calls (exercise, participation, etc.).
 */
public abstract class RepositoryResource {

    protected final Logger log = LoggerFactory.getLogger(RepositoryResource.class);

    protected final AuthorizationCheckService authCheckService;

    protected final Optional<ContinuousIntegrationService> continuousIntegrationService;

    protected final GitService gitService;

    protected final UserRepository userRepository;

    protected final RepositoryService repositoryService;

    protected final ProgrammingExerciseRepository programmingExerciseRepository;

    protected final Optional<VersionControlService> versionControlService;

    public RepositoryResource(UserRepository userRepository, AuthorizationCheckService authCheckService, GitService gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, RepositoryService repositoryService, Optional<VersionControlService> versionControlService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.repositoryService = repositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.versionControlService = versionControlService;
    }

    /**
     * Override this method to define how a repository can be retrieved.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return the repository if available.
     * @throws IOException            if the repository folder can't be accessed.
     * @throws IllegalAccessException if the user is not allowed to access the repository.
     * @throws GitAPIException        if the repository can't be checked out.
     */
    abstract Repository getRepository(Long domainId, RepositoryActionType repositoryAction, boolean pullOnCheckout)
            throws IOException, IllegalAccessException, IllegalArgumentException, GitAPIException;

    /**
     * Get the url for a repository.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return the repositoryUrl.
     */
    abstract VcsRepositoryUrl getRepositoryUrl(Long domainId);

    /**
     * Check if the current user can access the given repository.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return true if the user can access the repository.
     */
    abstract boolean canAccessRepository(Long domainId);

    /**
     * Gets or retrieves the default branch from the domain object
     * @param domainID the id of the domain object
     * @return the name of the default branch of that domain object
     */
    abstract String getOrRetrieveBranchOfDomainObject(Long domainID);

    /**
     * Get a map of files for the given domainId.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return the map of files with an indicator if the file is a file or a folder.
     */
    public ResponseEntity<Map<String, FileType>> getFiles(Long domainId) {
        log.debug("REST request to files for domainId : {}", domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.READ, true);
            Map<String, FileType> fileList = repositoryService.getFiles(repository);
            return new ResponseEntity<>(fileList, HttpStatus.OK);
        });
    }

    /**
     * Get the content of a file.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @param filename of the file to retrieve.
     * @return the file if available.
     */
    public ResponseEntity<byte[]> getFile(Long domainId, String filename) {
        log.debug("REST request to file {} for domainId : {}", filename, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.READ, true);
            byte[] out = repositoryService.getFile(repository, filename);
            HttpHeaders responseHeaders = new HttpHeaders();
            var contentType = repositoryService.getFileType(repository, filename);
            responseHeaders.add("Content-Type", contentType);
            return new ResponseEntity<>(out, responseHeaders, HttpStatus.OK);
        });
    }

    /**
     * Create new file.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @param filename of the file to create.
     * @param request  to retrieve input stream from.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    public ResponseEntity<Void> createFile(Long domainId, String filename, HttpServletRequest request) {
        log.debug("REST request to create file {} for domainId : {}", filename, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.WRITE, true);
            InputStream inputStream = request.getInputStream();
            repositoryService.createFile(repository, filename, inputStream);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Create new folder.
     *
     * @param domainId   that serves as an abstract identifier for retrieving the repository.
     * @param folderName of the folder to create.
     * @param request    to retrieve inputStream from.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    public ResponseEntity<Void> createFolder(Long domainId, String folderName, HttpServletRequest request) {
        log.debug("REST request to create file {} for domainId : {}", folderName, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.WRITE, true);
            InputStream inputStream = request.getInputStream();
            repositoryService.createFolder(repository, folderName, inputStream);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Change the name of a file.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @param fileMove defines current and new path in git repository.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    public ResponseEntity<Void> renameFile(Long domainId, FileMove fileMove) {
        log.debug("REST request to rename file {} to {} for domainId : {}", fileMove.currentFilePath(), fileMove.newFilename(), domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.WRITE, true);
            repositoryService.renameFile(repository, fileMove);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Delete the file or the folder specified. If the path is a folder, all files in it will be deleted, too.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @param filename path of file or folder to delete.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    public ResponseEntity<Void> deleteFile(Long domainId, String filename) {
        log.debug("REST request to delete file {} for domainId : {}", filename, domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.WRITE, true);
            repositoryService.deleteFile(repository, filename);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Pull into the participation repository.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    public ResponseEntity<Void> pullChanges(Long domainId) {
        log.debug("REST request to commit Repository for domainId : {}", domainId);

        return executeAndCheckForExceptions(() -> {
            try (Repository repository = getRepository(domainId, RepositoryActionType.READ, true)) {
                repositoryService.pullChanges(repository);

                return new ResponseEntity<>(HttpStatus.OK);
            }
        });
    }

    /**
     * Commit into the participation repository.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    public ResponseEntity<Void> commitChanges(Long domainId) {
        User user = userRepository.getUser();
        log.debug("REST request to commit Repository for domainId : {}", domainId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.WRITE, true);
            repositoryService.commitChanges(repository, user);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Reset a repository to the last commit. This will remove all staged / unstaged changes. Use with care as lost data can't be retrieved!
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    public ResponseEntity<Void> resetToLastCommit(Long domainId) {
        return executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(domainId, RepositoryActionType.RESET, false);
            gitService.resetToOriginHead(repository);
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Get the "clean" status of the repository. Clean = No uncommitted changes.
     *
     * @param domainId that serves as an abstract identifier for retrieving the repository.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     * @throws GitAPIException if the repository can't be checked out to retrieve the status.
     */
    public ResponseEntity<RepositoryStatusDTO> getStatus(Long domainId) throws GitAPIException {
        log.debug("REST request to get clean status for Repository for domainId : {}", domainId);

        if (!canAccessRepository(domainId)) {
            throw new AccessForbiddenException();
        }

        RepositoryStatusDTOType repositoryStatus;
        VcsRepositoryUrl repositoryUrl = getRepositoryUrl(domainId);

        try {
            boolean isClean;
            // This check reduces the amount of REST-calls that retrieve the default branch of a repository.
            // Retrieving the default branch is not necessary if the repository is already cached.
            if (gitService.isRepositoryCached(repositoryUrl)) {
                isClean = repositoryService.isClean(repositoryUrl);
            }
            else {
                String branch = getOrRetrieveBranchOfDomainObject(domainId);
                isClean = repositoryService.isClean(repositoryUrl, branch);
            }
            repositoryStatus = isClean ? CLEAN : UNCOMMITTED_CHANGES;
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            repositoryStatus = CONFLICT;
        }

        return new ResponseEntity<>(new RepositoryStatusDTO(repositoryStatus), HttpStatus.OK);
    }

    /**
     * This method is used to check the executed statements for exceptions. Will return an appropriate ResponseEntity for every kind of possible exception.
     *
     * @param executor lambda function to execute.
     * @return ResponseEntity with appropriate status (e.g. ok or forbidden).
     */
    protected <T> ResponseEntity<T> executeAndCheckForExceptions(RepositoryExecutor<T> executor) {
        ResponseEntity<T> responseEntitySuccess;
        try {
            responseEntitySuccess = executor.exec();
        }
        catch (IllegalArgumentException | FileAlreadyExistsException ex) {
            throw new BadRequestAlertException("Illegal argument during operation or file already exists", "Repository", "illegalArgumentFileAlreadyExists");
        }
        catch (IllegalAccessException ex) {
            throw new AccessForbiddenException();
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        catch (FileNotFoundException ex) {
            throw new EntityNotFoundException("File not found");
        }
        catch (GitAPIException | IOException ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseEntitySuccess;
    }

    /**
     * Iterate through the file submissions and try to save each one. Will continue iterating when an error is encountered on updating a file and store its error in the resulting
     * Map.
     *
     * @param submissions the file submissions (changes) that should be saved in the repository
     * @param repository  the git repository in which the file changes should be saved
     * @return a map of <filename, error | null>
     */
    protected Map<String, String> saveFileSubmissions(List<FileSubmission> submissions, Repository repository) {
        // If updating the file fails due to an IOException, we send an error message for the specific file and try to update the rest
        Map<String, String> fileSaveResult = new HashMap<>();
        submissions.forEach(submission -> {
            try {
                fetchAndUpdateFile(submission, repository);
                fileSaveResult.put(submission.getFileName(), null);
            }
            catch (IOException ex) {
                fileSaveResult.put(submission.getFileName(), ex.getMessage());
            }
        });
        return fileSaveResult;
    }

    /**
     * Retrieve the file from repository and update its content with the submission's content. Throws exceptions if the user doesn't have permissions, the file can't be retrieved
     * or it can't be updated.
     *
     * @param submission information about file update
     * @param repository repository in which to fetch and update the file
     * @throws IOException exception when the file in the file submission parameter is empty
     */
    private void fetchAndUpdateFile(FileSubmission submission, Repository repository) throws IOException {
        Optional<File> file = gitService.getFileByName(repository, submission.getFileName());

        if (file.isEmpty()) {
            throw new IOException("File could not be found.");
        }

        InputStream inputStream = new ByteArrayInputStream(submission.getFileContent().getBytes(StandardCharsets.UTF_8));
        Files.copy(inputStream, file.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();
    }
}
