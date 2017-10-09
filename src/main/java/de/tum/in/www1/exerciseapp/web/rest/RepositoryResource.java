package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.domain.BuildLogEntry;
import de.tum.in.www1.exerciseapp.domain.File;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Repository;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.service.ContinuousIntegrationService;
import de.tum.in.www1.exerciseapp.service.GitService;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import de.tum.in.www1.exerciseapp.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Created by Josias Montag on 14.10.16.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class RepositoryResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final Optional<GitService> gitService;

    private final ParticipationService participationService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final GrantedAuthority adminAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN);
    private final GrantedAuthority taAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.TEACHING_ASSISTANT);

    public RepositoryResource(ParticipationService participationService, Optional<GitService> gitService, Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.participationService = participationService;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * GET /repository/{id}/files: List all file names of the repository
     *
     * @param id Participation ID
     * @param authentication
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @GetMapping(value = "/repository/{id}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'read')")
    public ResponseEntity<Collection<String>> getFiles(@PathVariable Long id, AbstractAuthenticationToken authentication) throws IOException, GitAPIException {
        log.debug("REST request to files for Participation : {}", id);
        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }


        Repository repository = gitService.get().getOrCheckoutRepository(participation);
        Iterator<File> itr = gitService.get().listFiles(repository).iterator();

        Collection<String> fileList = new LinkedList<>();

        while (itr.hasNext()) {
            fileList.add(itr.next().toString());
        }

        return new ResponseEntity<>(
            fileList,
            HttpStatus.OK);
    }


    /**
     * GET /repository/{id}/file: Get the content of a file
     *
     * @param id Participation ID
     * @param filename
     * @param authentication
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @GetMapping(value = "/repository/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'read')")
    public ResponseEntity<String> getFile(@PathVariable Long id, @RequestParam("file")  String filename, AbstractAuthenticationToken authentication) throws IOException, GitAPIException {
        log.debug("REST request to file {} for Participation : {}", filename, id);
        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }



        Repository repository = gitService.get().getOrCheckoutRepository(participation);

        Optional<File> file = gitService.get().getFileByName(repository, filename);

        if(!file.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        InputStream inputStream = new FileInputStream(file.get());

        byte[]out=org.apache.commons.io.IOUtils.toByteArray(inputStream);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity(out, responseHeaders,HttpStatus.OK);
    }


    /**
     * POST /repository/{id}/file: Create new file
     *
     * @param id Participation ID
     * @param filename
     * @param request
     * @param authentication
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @PostMapping(value = "/repository/{id}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'update')")
    public ResponseEntity<Void> createFile(@PathVariable Long id, @RequestParam("file")  String filename, HttpServletRequest request, AbstractAuthenticationToken authentication) throws IOException, GitAPIException {
        log.debug("REST request to create file {} for Participation : {}", filename, id);
        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

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
     * PUT /repository/{id}/file: Update the file content
     *
     * @param id Participation ID
     * @param filename
     * @param request
     * @param authentication
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @PutMapping(value = "/repository/{id}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'update')")
    public ResponseEntity<Void> updateFile(@PathVariable Long id, @RequestParam("file")  String filename, HttpServletRequest request, AbstractAuthenticationToken authentication) throws IOException, GitAPIException {
        log.debug("REST request to update file {} for Participation : {}", filename, id);
        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Repository repository = gitService.get().getOrCheckoutRepository(participation);

        Optional<File> file = gitService.get().getFileByName(repository, filename);

        if(!file.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        InputStream inputStream = request.getInputStream();

        Files.copy(inputStream, file.get().toPath(), StandardCopyOption.REPLACE_EXISTING);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert("file", filename)).build();
    }



    /**
     * DELETE /repository/{id}/file: Delete the file
     *
     * @param id Participation ID
     * @param filename
     * @param request
     * @param authentication
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @DeleteMapping(value = "/repository/{id}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'update')")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id, @RequestParam("file")  String filename, HttpServletRequest request, AbstractAuthenticationToken authentication) throws IOException, GitAPIException {
        log.debug("REST request to delete file {} for Participation : {}", filename, id);
        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Repository repository = gitService.get().getOrCheckoutRepository(participation);

        Optional<File> file = gitService.get().getFileByName(repository, filename);

        if(!file.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Files.delete(file.get().toPath());

        repository.setFiles(null); // invalidate cache

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("file", filename)).build();
    }




    /**
     * POST /repository/{id}/commit: Commit into the participation repository
     *
     * @param id Participation ID
     * @param request
     * @param authentication
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @PostMapping(value = "/repository/{id}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'commit')")
    public ResponseEntity<Void> updateFile(@PathVariable Long id, HttpServletRequest request, AbstractAuthenticationToken authentication) throws IOException, GitAPIException {
        log.debug("REST request to commit Repository for Participation : {}", id);
        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Repository repository = gitService.get().getOrCheckoutRepository(participation);

        gitService.get().stageAllChanges(repository);
        gitService.get().commitAndPush(repository, "Changes by Online Editor");

        return new ResponseEntity<>(HttpStatus.OK);
    }


    /**
     * GET /repository/{id}: Get the "clean" status of the repository. Clean = No uncommitted changes.
     *
     * @param id Participation ID
     * @param request
     * @param authentication
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @GetMapping(value = "/repository/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'read')")
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long id, HttpServletRequest request, AbstractAuthenticationToken authentication) throws IOException, GitAPIException {
        log.debug("REST request to get clean status for Repository for Participation : {}", id);
        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Repository repository = gitService.get().getOrCheckoutRepository(participation);

        RepositoryStatusDTO status = new RepositoryStatusDTO();

        status.isClean = gitService.get().isClean(repository);

        if(status.isClean) {
            gitService.get().pull(repository);
        }

        return new ResponseEntity<>(status, HttpStatus.OK);
    }


    /**
     * GET  /repository/:id/buildlogs : get the build log from Bamboo for the "id" repository.
     *
     * @param id the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/repository/{id}/buildlogs", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(#id, 'Repository', 'read')")
    public ResponseEntity<?> getResultDetails(@PathVariable Long id, @RequestParam(required = false) String username, AbstractAuthenticationToken authentication) {
        log.debug("REST request to get build log : {}", id);

        Participation participation = participationService.findOne(id);

        if (!Optional.ofNullable(participation).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        List<BuildLogEntry> logs = continuousIntegrationService.get().getLatestBuildLogs(participation);

        return new ResponseEntity<>(logs, HttpStatus.OK);
    }
}
