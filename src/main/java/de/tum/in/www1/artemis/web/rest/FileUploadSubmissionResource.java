package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing FileUploadSubmission.
 */
@RestController
@RequestMapping("/api")
public class FileUploadSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionResource.class);

    private static final String ENTITY_NAME = "fileUploadSubmission";

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    public FileUploadSubmissionResource(FileUploadSubmissionRepository fileUploadSubmissionRepository) {
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    /**
     * POST  /file-upload-submissions : Create a new fileUploadSubmission.
     *
     * @param fileUploadSubmission the fileUploadSubmission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadSubmission, or with status 400 (Bad Request) if the fileUploadSubmission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/file-upload-submissions")
    public ResponseEntity<FileUploadSubmission> createFileUploadSubmission(@RequestBody FileUploadSubmission fileUploadSubmission) throws URISyntaxException {
        log.debug("REST request to save FileUploadSubmission : {}", fileUploadSubmission);
        if (fileUploadSubmission.getId() != null) {
            throw new BadRequestAlertException("A new fileUploadSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        FileUploadSubmission result = fileUploadSubmissionRepository.save(fileUploadSubmission);
        return ResponseEntity.created(new URI("/api/file-upload-submissions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /file-upload-submissions : Updates an existing fileUploadSubmission.
     *
     * @param fileUploadSubmission the fileUploadSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadSubmission,
     * or with status 400 (Bad Request) if the fileUploadSubmission is not valid,
     * or with status 500 (Internal Server Error) if the fileUploadSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/file-upload-submissions")
    public ResponseEntity<FileUploadSubmission> updateFileUploadSubmission(@RequestBody FileUploadSubmission fileUploadSubmission) throws URISyntaxException {
        log.debug("REST request to update FileUploadSubmission : {}", fileUploadSubmission);
        if (fileUploadSubmission.getId() == null) {
            return createFileUploadSubmission(fileUploadSubmission);
        }
        FileUploadSubmission result = fileUploadSubmissionRepository.save(fileUploadSubmission);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, fileUploadSubmission.getId().toString()))
            .body(result);
    }

    /**
     * GET  /file-upload-submissions : get all the fileUploadSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of fileUploadSubmissions in body
     */
    @GetMapping("/file-upload-submissions")
    public List<FileUploadSubmission> getAllFileUploadSubmissions() {
        log.debug("REST request to get all FileUploadSubmissions");
        return fileUploadSubmissionRepository.findAll();
        }

    /**
     * GET  /file-upload-submissions/:id : get the "id" fileUploadSubmission.
     *
     * @param id the id of the fileUploadSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-submissions/{id}")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmission(@PathVariable Long id) {
        log.debug("REST request to get FileUploadSubmission : {}", id);
        Optional<FileUploadSubmission> fileUploadSubmission = fileUploadSubmissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(fileUploadSubmission);
    }

    /**
     * DELETE  /file-upload-submissions/:id : delete the "id" fileUploadSubmission.
     *
     * @param id the id of the fileUploadSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/file-upload-submissions/{id}")
    public ResponseEntity<Void> deleteFileUploadSubmission(@PathVariable Long id) {
        log.debug("REST request to delete FileUploadSubmission : {}", id);
        fileUploadSubmissionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
