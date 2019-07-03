package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.io.IOException;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.CheckoutConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
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
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

/**
 * Executes repository actions on repositories related to the participation id transmitted. Available to the owner of the participation, TAs/Instructors of the exercise and Admins.
 */
@RestController
@RequestMapping({ "/api", "/api_basic" })
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
public class RepositoryParticipationResource extends RepositoryResource {

    private final ParticipationService participationService;

    public RepositoryParticipationResource(UserService userService, AuthorizationCheckService authCheckService, Optional<GitService> gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, RepositoryService repositoryService, ParticipationService participationService,
            SimpMessageSendingOperations messagingTemplate) {
        super(userService, authCheckService, gitService, continuousIntegrationService, repositoryService, messagingTemplate);
        this.participationService = participationService;
    }

    @Override
    Repository getRepository(Long participationId) throws IOException, IllegalAccessException, InterruptedException {
        Participation participation = participationService.findOne(participationId);
        try {
            return repositoryService.checkoutRepositoryByParticipation(participation);
        }
        catch (CheckoutConflictException ex) {
            messagingTemplate.convertAndSendToUser(userService.getUser().getLogin(), "/topic/repository-state/" + participationId + "update", "CHECKOUT_CONFLICT");
            throw new IOException();
        }
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
        return super.getFiles(participationId);
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
        return super.getFile(participationId, filename);
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
        return super.createFile(participationId, filename, request);
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
        return super.createFolder(participationId, folderName, request);
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
        return super.renameFile(participationId, fileMove);
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
        return super.deleteFile(participationId, filename);
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
        return super.pullChanges(participationId);
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
        return super.commitChanges(participationId);
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
        return super.getStatus(participationId);
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
        if (!participationService.canAccessParticipation(participation))
            return forbidden();

        List<BuildLogEntry> logs = continuousIntegrationService.get().getLatestBuildLogs(participation);

        return new ResponseEntity<>(logs, HttpStatus.OK);
    }
}
