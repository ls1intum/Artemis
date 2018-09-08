package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ResultRepository;
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
 * REST controller for managing Result.
 */
@RestController
@RequestMapping("/api")
public class ResultResource {

    private final Logger log = LoggerFactory.getLogger(ResultResource.class);

    private static final String ENTITY_NAME = "result";

    private final ResultRepository resultRepository;

    public ResultResource(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /**
     * POST  /results : Create a new result.
     *
     * @param result the result to create
     * @return the ResponseEntity with status 201 (Created) and with body the new result, or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/results")
    @Timed
    public ResponseEntity<Result> createResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to save Result : {}", result);
        if (result.getId() != null) {
            throw new BadRequestAlertException("A new result cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Result result = resultRepository.save(result);
        return ResponseEntity.created(new URI("/api/results/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /results : Updates an existing result.
     *
     * @param result the result to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated result,
     * or with status 400 (Bad Request) if the result is not valid,
     * or with status 500 (Internal Server Error) if the result couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/results")
    @Timed
    public ResponseEntity<Result> updateResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to update Result : {}", result);
        if (result.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Result result = resultRepository.save(result);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * GET  /results : get all the results.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping("/results")
    @Timed
    public List<Result> getAllResults() {
        log.debug("REST request to get all Results");
        return resultRepository.findAll();
    }

    /**
     * GET  /results/:id : get the "id" result.
     *
     * @param id the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("/results/{id}")
    @Timed
    public ResponseEntity<Result> getResult(@PathVariable Long id) {
        log.debug("REST request to get Result : {}", id);
        Optional<Result> result = resultRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(result);
    }

    /**
     * DELETE  /results/:id : delete the "id" result.
     *
     * @param id the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/results/{id}")
    @Timed
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        log.debug("REST request to delete Result : {}", id);

        resultRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
