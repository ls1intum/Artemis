package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.ExerciseResult;
import de.tum.in.www1.artemis.repository.ExerciseResultRepository;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * REST controller for managing ExerciseResult.
 */
@RestController
@RequestMapping("/api")
public class ExerciseResultResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseResultResource.class);

    private static final String ENTITY_NAME = "exerciseResult";

    private ExerciseResultRepository exerciseResultRepository;

    public ExerciseResultResource(ExerciseResultRepository exerciseResultRepository) {
        this.exerciseResultRepository = exerciseResultRepository;
    }

    /**
     * POST  /exercise-results : Create a new exerciseResult.
     *
     * @param exerciseResult the exerciseResult to create
     * @return the ResponseEntity with status 201 (Created) and with body the new exerciseResult, or with status 400 (Bad Request) if the exerciseResult has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/exercise-results")
    @Timed
    public ResponseEntity<ExerciseResult> createExerciseResult(@RequestBody ExerciseResult exerciseResult) throws URISyntaxException {
        log.debug("REST request to save ExerciseResult : {}", exerciseResult);
        if (exerciseResult.getId() != null) {
            throw new BadRequestAlertException("A new exerciseResult cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ExerciseResult result = exerciseResultRepository.save(exerciseResult);
        return ResponseEntity.created(new URI("/api/exercise-results/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /exercise-results : Updates an existing exerciseResult.
     *
     * @param exerciseResult the exerciseResult to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated exerciseResult,
     * or with status 400 (Bad Request) if the exerciseResult is not valid,
     * or with status 500 (Internal Server Error) if the exerciseResult couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/exercise-results")
    @Timed
    public ResponseEntity<ExerciseResult> updateExerciseResult(@RequestBody ExerciseResult exerciseResult) throws URISyntaxException {
        log.debug("REST request to update ExerciseResult : {}", exerciseResult);
        if (exerciseResult.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        ExerciseResult result = exerciseResultRepository.save(exerciseResult);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, exerciseResult.getId().toString()))
            .body(result);
    }

    /**
     * GET  /exercise-results : get all the exerciseResults.
     *
     * @param filter the filter of the request
     * @return the ResponseEntity with status 200 (OK) and the list of exerciseResults in body
     */
    @GetMapping("/exercise-results")
    @Timed
    public List<ExerciseResult> getAllExerciseResults(@RequestParam(required = false) String filter) {
        if ("submission-is-null".equals(filter)) {
            log.debug("REST request to get all ExerciseResults where submission is null");
            return StreamSupport
                .stream(exerciseResultRepository.findAll().spliterator(), false)
                .filter(exerciseResult -> exerciseResult.getSubmission() == null)
                .collect(Collectors.toList());
        }
        log.debug("REST request to get all ExerciseResults");
        return exerciseResultRepository.findAll();
    }

    /**
     * GET  /exercise-results/:id : get the "id" exerciseResult.
     *
     * @param id the id of the exerciseResult to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exerciseResult, or with status 404 (Not Found)
     */
    @GetMapping("/exercise-results/{id}")
    @Timed
    public ResponseEntity<ExerciseResult> getExerciseResult(@PathVariable Long id) {
        log.debug("REST request to get ExerciseResult : {}", id);
        Optional<ExerciseResult> exerciseResult = exerciseResultRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(exerciseResult);
    }

    /**
     * DELETE  /exercise-results/:id : delete the "id" exerciseResult.
     *
     * @param id the id of the exerciseResult to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/exercise-results/{id}")
    @Timed
    public ResponseEntity<Void> deleteExerciseResult(@PathVariable Long id) {
        log.debug("REST request to delete ExerciseResult : {}", id);

        exerciseResultRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
