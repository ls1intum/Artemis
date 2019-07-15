package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ExerciseHint;
import de.tum.in.www1.artemis.service.ExerciseHintService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import io.github.jhipster.web.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.ExerciseHint}.
 */
@RestController
@RequestMapping("/api")
public class ExerciseHintResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseHintResource.class);

    private static final String ENTITY_NAME = "exerciseHint";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseHintService exerciseHintService;

    public ExerciseHintResource(ExerciseHintService exerciseHintService) {
        this.exerciseHintService = exerciseHintService;
    }

    /**
     * {@code POST  /exercise-hints} : Create a new exerciseHint.
     *
     * @param exerciseHint the exerciseHint to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new exerciseHint, or with status {@code 400 (Bad Request)} if the exerciseHint has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("/exercise-hints")
    public ResponseEntity<ExerciseHint> createExerciseHint(@RequestBody ExerciseHint exerciseHint) throws URISyntaxException {
        log.debug("REST request to save ExerciseHint : {}", exerciseHint);
        if (exerciseHint.getId() != null) {
            throw new BadRequestAlertException("A new exerciseHint cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ExerciseHint result = exerciseHintService.save(exerciseHint);
        return ResponseEntity.created(new URI("/api/exercise-hints/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * {@code PUT  /exercise-hints} : Updates an existing exerciseHint.
     *
     * @param exerciseHint the exerciseHint to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is not valid,
     * or with status {@code 500 (Internal Server Error)} if the exerciseHint couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/exercise-hints")
    public ResponseEntity<ExerciseHint> updateExerciseHint(@RequestBody ExerciseHint exerciseHint) throws URISyntaxException {
        log.debug("REST request to update ExerciseHint : {}", exerciseHint);
        if (exerciseHint.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        ExerciseHint result = exerciseHintService.save(exerciseHint);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseHint.getId().toString())).body(result);
    }

    /**
     * {@code GET  /exercise-hints} : get all the exerciseHints.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of exerciseHints in body.
     */
    @GetMapping("/exercise-hints")
    public List<ExerciseHint> getAllExerciseHints() {
        log.debug("REST request to get all ExerciseHints");
        return exerciseHintService.findAll();
    }

    /**
     * {@code GET  /exercise-hints/:id} : get the "id" exerciseHint.
     *
     * @param id the id of the exerciseHint to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/exercise-hints/{id}")
    public ResponseEntity<ExerciseHint> getExerciseHint(@PathVariable Long id) {
        log.debug("REST request to get ExerciseHint : {}", id);
        Optional<ExerciseHint> exerciseHint = exerciseHintService.findOne(id);
        return ResponseUtil.wrapOrNotFound(exerciseHint);
    }

    /**
     * {@code DELETE  /exercise-hints/:id} : delete the "id" exerciseHint.
     *
     * @param id the id of the exerciseHint to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/exercise-hints/{id}")
    public ResponseEntity<Void> deleteExerciseHint(@PathVariable Long id) {
        log.debug("REST request to delete ExerciseHint : {}", id);
        exerciseHintService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
