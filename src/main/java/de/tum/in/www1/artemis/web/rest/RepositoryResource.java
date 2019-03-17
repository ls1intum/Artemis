package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

/**
 * Created by Josias Montag on 14.10.16.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
public class RepositoryResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final ParticipationService participationService;
    private final AuthorizationCheckService authCheckService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<GitService> gitService;
    private final UserService userService;

    public RepositoryResource(UserService userService, ParticipationService participationService, AuthorizationCheckService authCheckService,
                              Optional<GitService> gitService, Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.userService = userService;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * GET /repository/{participationId}/files: List all file names of the repository
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/repository/{participationId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Collection<String>> getFiles(@PathVariable Long participationId) throws IOException, InterruptedException {
        log.debug("REST request to files for Participation : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        ResponseEntity<Collection<String>> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        Iterator<File> itr = gitService.get().listFiles(repository).iterator();

        Collection<String> fileList = new LinkedList<>();

        while (itr.hasNext()) {
            fileList.add(itr.next().toString());
        }

        return new ResponseEntity<>(fileList, HttpStatus.OK);
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
        ResponseEntity<String> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        Optional<File> file = gitService.get().getFileByName(repository, filename);
        if(!file.isPresent()) { return notFound(); }

        InputStream inputStream = new FileInputStream(file.get());

        byte[]out=org.apache.commons.io.IOUtils.toByteArray(inputStream);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity(out, responseHeaders, HttpStatus.OK);
    }

    @Nullable
    private <X> ResponseEntity<X> checkParticipation(Participation participation) {
        if (!userHasPermissions(participation)) return forbidden();

        if (!Optional.ofNullable(participation).isPresent()) {
            return notFound();
        }
        return null;
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
    public ResponseEntity<Void> createFile(@PathVariable Long participationId, @RequestParam("file") String filename, HttpServletRequest request) throws IOException, InterruptedException {
        log.debug("REST request to create file {} for Participation : {}", filename, participationId);

        Participation participation = participationService.findOne(participationId);
        ResponseEntity<Void> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        if(gitService.get().getFileByName(repository, filename).isPresent()) {
            // File already existing. Conflict.
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        File file = new File(new java.io.File(repository.getLocalPath() + File.separator + filename), repository);
        if(!repository.isValidFile(file)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        file.getParentFile().mkdirs();

        InputStream inputStream = request.getInputStream();
        Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        repository.setFiles(null); // invalidate cache

        return ResponseEntity.ok().headers(HeaderUtil.createEntityCreationAlert("file", filename)).build();
    }


    /**
     * PUT /repository/{participationId}/file: Update the file content
     *
     * @param participationId Participation ID
     * @param filename
     * @param request
     * @return
     * @throws IOException
     */
    @PutMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateFile(@PathVariable Long participationId, @RequestParam("file")  String filename, HttpServletRequest request) throws IOException, InterruptedException {
        log.debug("REST request to update file {} for Participation : {}", filename, participationId);

        Participation participation = participationService.findOne(participationId);
        ResponseEntity<Void> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        Optional<File> file = gitService.get().getFileByName(repository, filename);
        if(!file.isPresent()) { return notFound(); }

        InputStream inputStream = request.getInputStream();
        Files.copy(inputStream, file.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert("file", filename)).build();
    }

    /**
     * DELETE /repository/{participationId}/file: Delete the file
     *
     * @param participationId Participation ID
     * @param filename
     * @return
     * @throws IOException
     */
    @DeleteMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteFile(@PathVariable Long participationId, @RequestParam("file")  String filename) throws IOException, InterruptedException {
        log.debug("REST request to delete file {} for Participation : {}", filename, participationId);

        Participation participation = participationService.findOne(participationId);
        ResponseEntity<Void> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        Optional<File> file = gitService.get().getFileByName(repository, filename);
        if(!file.isPresent()) { return notFound(); }

        Files.delete(file.get().toPath());
        repository.setFiles(null); // invalidate cache
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
        ResponseEntity<Void> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        gitService.get().pull(repository);
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
    public ResponseEntity<Void> commitChanges(@PathVariable Long participationId) throws IOException, GitAPIException, InterruptedException {
        log.debug("REST request to commit Repository for Participation : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        ResponseEntity<Void> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        gitService.get().stageAllChanges(repository);
        gitService.get().commitAndPush(repository, "Changes by Online Editor");
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
        ResponseEntity<RepositoryStatusDTO> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        RepositoryStatusDTO status = new RepositoryStatusDTO();
        status.isClean = gitService.get().isClean(repository);

        if(status.isClean) {
            gitService.get().pull(repository);
        }

        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    /**
     * GET  /repository/:participationId/buildlogs : get the build log from Bamboo for the "participationId" repository.
     *
     * @param participationId the participationId of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/repository/{participationId}/buildlogs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getResultDetails(@PathVariable Long participationId) {
        log.debug("REST request to get build log : {}", participationId);

        Participation participation = participationService.findOne(participationId);
        ResponseEntity<Void> failureResponse = checkParticipation(participation);
        if (failureResponse != null) return failureResponse;

        List<BuildLogEntry> logs = continuousIntegrationService.get().getLatestBuildLogs(participation);
        return new ResponseEntity<>(logs, HttpStatus.OK);
    }

    private boolean userHasPermissions(Participation participation) {
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            //if the user is not the owner of the participation, the user can only see it in case he is
            //a teaching assistant or an instructor of the course, or in case he is admin
            User user = userService.getUserWithGroupsAndAuthorities();
            Course course = participation.getExercise().getCourse();
            return authCheckService.isTeachingAssistantInCourse(course, user) ||
                authCheckService.isInstructorInCourse(course, user) ||
                authCheckService.isAdmin();
        }
        return true;
    }
}
