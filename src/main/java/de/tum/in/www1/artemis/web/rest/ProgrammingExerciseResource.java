package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
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
 * REST controller for managing ProgrammingExercise.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private ProgrammingExerciseRepository programmingExerciseRepository;

    public ProgrammingExerciseResource(ProgrammingExerciseRepository programmingExerciseRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * POST  /programming-exercises : Create a new programmingExercise.
     *
     * @param programmingExercise the programmingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the programmingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/programming-exercises")
    @Timed
    public ResponseEntity<ProgrammingExercise> createProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) throws URISyntaxException {
        log.debug("REST request to save ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() != null) {
            throw new BadRequestAlertException("A new programmingExercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ProgrammingExercise result = programmingExerciseRepository.save(programmingExercise);
        return ResponseEntity.created(new URI("/api/programming-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /programming-exercises : Updates an existing programmingExercise.
     *
     * @param programmingExercise the programmingExercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated programmingExercise,
     * or with status 400 (Bad Request) if the programmingExercise is not valid,
     * or with status 500 (Internal Server Error) if the programmingExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/programming-exercises")
    @Timed
    public ResponseEntity<ProgrammingExercise> updateProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) throws URISyntaxException {
        log.debug("REST request to update ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        ProgrammingExercise result = programmingExerciseRepository.save(programmingExercise);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, programmingExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /programming-exercises : get all the programmingExercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping("/programming-exercises")
    @Timed
    public List<ProgrammingExercise> getAllProgrammingExercises() {
        log.debug("REST request to get all ProgrammingExercises");
        return programmingExerciseRepository.findAll();
    }

    /**
     * GET  /programming-exercises/:id : get the "id" programmingExercise.
     *
     * @param id the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping("/programming-exercises/{id}")
    @Timed
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable Long id) {
        log.debug("REST request to get ProgrammingExercise : {}", id);
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(programmingExercise);
    }

    /**
     * DELETE  /programming-exercises/:id : delete the "id" programmingExercise.
     *
     * @param id the id of the programmingExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/programming-exercises/{id}")
    @Timed
    public ResponseEntity<Void> deleteProgrammingExercise(@PathVariable Long id) {
        log.debug("REST request to delete ProgrammingExercise : {}", id);

        programmingExerciseRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
