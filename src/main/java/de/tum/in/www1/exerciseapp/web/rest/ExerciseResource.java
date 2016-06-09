package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
import de.tum.in.www1.exerciseapp.repository.search.ExerciseSearchRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import de.tum.in.www1.exerciseapp.web.rest.util.PaginationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.*;

/**
 * REST controller for managing Exercise.
 */
@RestController
@RequestMapping("/api")
public class ExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseResource.class);
        
    @Inject
    private ExerciseRepository exerciseRepository;
    
    @Inject
    private ExerciseSearchRepository exerciseSearchRepository;
    
    /**
     * POST  /exercises : Create a new exercise.
     *
     * @param exercise the exercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new exercise, or with status 400 (Bad Request) if the exercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/exercises",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Exercise> createExercise(@RequestBody Exercise exercise) throws URISyntaxException {
        log.debug("REST request to save Exercise : {}", exercise);
        if (exercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("exercise", "idexists", "A new exercise cannot already have an ID")).body(null);
        }
        Exercise result = exerciseRepository.save(exercise);
        exerciseSearchRepository.save(result);
        return ResponseEntity.created(new URI("/api/exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert("exercise", result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /exercises : Updates an existing exercise.
     *
     * @param exercise the exercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated exercise,
     * or with status 400 (Bad Request) if the exercise is not valid,
     * or with status 500 (Internal Server Error) if the exercise couldnt be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/exercises",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Exercise> updateExercise(@RequestBody Exercise exercise) throws URISyntaxException {
        log.debug("REST request to update Exercise : {}", exercise);
        if (exercise.getId() == null) {
            return createExercise(exercise);
        }
        Exercise result = exerciseRepository.save(exercise);
        exerciseSearchRepository.save(result);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert("exercise", exercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /exercises : get all the exercises.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of exercises in body
     * @throws URISyntaxException if there is an error to generate the pagination HTTP headers
     */
    @RequestMapping(value = "/exercises",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Exercise>> getAllExercises(Pageable pageable)
        throws URISyntaxException {
        log.debug("REST request to get a page of Exercises");
        Page<Exercise> page = exerciseRepository.findAll(pageable); 
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/exercises");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET  /exercises/:id : get the "id" exercise.
     *
     * @param id the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/exercises/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Exercise> getExercise(@PathVariable Long id) {
        log.debug("REST request to get Exercise : {}", id);
        Exercise exercise = exerciseRepository.findOne(id);
        return Optional.ofNullable(exercise)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /exercises/:id : delete the "id" exercise.
     *
     * @param id the id of the exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @RequestMapping(value = "/exercises/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> deleteExercise(@PathVariable Long id) {
        log.debug("REST request to delete Exercise : {}", id);
        exerciseRepository.delete(id);
        exerciseSearchRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("exercise", id.toString())).build();
    }

    /**
     * SEARCH  /_search/exercises?query=:query : search for the exercise corresponding
     * to the query.
     *
     * @param query the query of the exercise search
     * @return the result of the search
     */
    @RequestMapping(value = "/_search/exercises",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Exercise>> searchExercises(@RequestParam String query, Pageable pageable)
        throws URISyntaxException {
        log.debug("REST request to search for a page of Exercises for query {}", query);
        Page<Exercise> page = exerciseSearchRepository.search(queryStringQuery(query), pageable);
        HttpHeaders headers = PaginationUtil.generateSearchPaginationHttpHeaders(query, page, "/api/_search/exercises");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

}
