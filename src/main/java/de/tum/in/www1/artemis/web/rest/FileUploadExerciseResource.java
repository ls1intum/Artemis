package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
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
 * REST controller for managing FileUploadExercise.
 */
@RestController
@RequestMapping("/api")
public class FileUploadExerciseResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseResource.class);

    private static final String ENTITY_NAME = "fileUploadExercise";

    private FileUploadExerciseRepository fileUploadExerciseRepository;

    public FileUploadExerciseResource(FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
    }

    /**
     * POST  /file-upload-exercises : Create a new fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/file-upload-exercises")
    @Timed
    public ResponseEntity<FileUploadExercise> createFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to save FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() != null) {
            throw new BadRequestAlertException("A new fileUploadExercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);
        return ResponseEntity.created(new URI("/api/file-upload-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /file-upload-exercises : Updates an existing fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadExercise,
     * or with status 400 (Bad Request) if the fileUploadExercise is not valid,
     * or with status 500 (Internal Server Error) if the fileUploadExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/file-upload-exercises")
    @Timed
    public ResponseEntity<FileUploadExercise> updateFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to update FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, fileUploadExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /file-upload-exercises : get all the fileUploadExercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of fileUploadExercises in body
     */
    @GetMapping("/file-upload-exercises")
    @Timed
    public List<FileUploadExercise> getAllFileUploadExercises() {
        log.debug("REST request to get all FileUploadExercises");
        return fileUploadExerciseRepository.findAll();
    }

    /**
     * GET  /file-upload-exercises/:id : get the "id" fileUploadExercise.
     *
     * @param id the id of the fileUploadExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadExercise, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-exercises/{id}")
    @Timed
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long id) {
        log.debug("REST request to get FileUploadExercise : {}", id);
        Optional<FileUploadExercise> fileUploadExercise = fileUploadExerciseRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(fileUploadExercise);
    }

    /**
     * DELETE  /file-upload-exercises/:id : delete the "id" fileUploadExercise.
     *
     * @param id the id of the fileUploadExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/file-upload-exercises/{id}")
    @Timed
    public ResponseEntity<Void> deleteFileUploadExercise(@PathVariable Long id) {
        log.debug("REST request to delete FileUploadExercise : {}", id);

        fileUploadExerciseRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
