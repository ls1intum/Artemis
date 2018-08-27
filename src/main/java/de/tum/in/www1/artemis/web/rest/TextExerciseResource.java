package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.TextExercise;

import de.tum.in.www1.artemis.repository.TextExerciseRepository;
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
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("/api")
public class TextExerciseResource {

    private final Logger log = LoggerFactory.getLogger(TextExerciseResource.class);

    private static final String ENTITY_NAME = "textExercise";

    private final TextExerciseRepository textExerciseRepository;

    public TextExerciseResource(TextExerciseRepository textExerciseRepository) {
        this.textExerciseRepository = textExerciseRepository;
    }

    /**
     * POST  /text-exercises : Create a new textExercise.
     *
     * @param textExercise the textExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new textExercise, or with status 400 (Bad Request) if the textExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/text-exercises")
    @Timed
    public ResponseEntity<TextExercise> createTextExercise(@RequestBody TextExercise textExercise) throws URISyntaxException {
        log.debug("REST request to save TextExercise : {}", textExercise);
        if (textExercise.getId() != null) {
            throw new BadRequestAlertException("A new textExercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        TextExercise result = textExerciseRepository.save(textExercise);
        return ResponseEntity.created(new URI("/api/text-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /text-exercises : Updates an existing textExercise.
     *
     * @param textExercise the textExercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textExercise,
     * or with status 400 (Bad Request) if the textExercise is not valid,
     * or with status 500 (Internal Server Error) if the textExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/text-exercises")
    @Timed
    public ResponseEntity<TextExercise> updateTextExercise(@RequestBody TextExercise textExercise) throws URISyntaxException {
        log.debug("REST request to update TextExercise : {}", textExercise);
        if (textExercise.getId() == null) {
            return createTextExercise(textExercise);
        }
        TextExercise result = textExerciseRepository.save(textExercise);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, textExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /text-exercises : get all the textExercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of textExercises in body
     */
    @GetMapping("/text-exercises")
    @Timed
    public List<TextExercise> getAllTextExercises() {
        log.debug("REST request to get all TextExercises");
        return textExerciseRepository.findAll();
        }

    /**
     * GET  /text-exercises/:id : get the "id" textExercise.
     *
     * @param id the id of the textExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textExercise, or with status 404 (Not Found)
     */
    @GetMapping("/text-exercises/{id}")
    @Timed
    public ResponseEntity<TextExercise> getTextExercise(@PathVariable Long id) {
        log.debug("REST request to get TextExercise : {}", id);
        TextExercise textExercise = textExerciseRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(textExercise));
    }

    /**
     * DELETE  /text-exercises/:id : delete the "id" textExercise.
     *
     * @param id the id of the textExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/text-exercises/{id}")
    @Timed
    public ResponseEntity<Void> deleteTextExercise(@PathVariable Long id) {
        log.debug("REST request to delete TextExercise : {}", id);
        textExerciseRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
