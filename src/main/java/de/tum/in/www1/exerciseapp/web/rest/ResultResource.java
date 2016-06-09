package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.repository.search.ResultSearchRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * REST controller for managing Result.
 */
@RestController
@RequestMapping("/api")
public class ResultResource {

    private final Logger log = LoggerFactory.getLogger(ResultResource.class);

    @Inject
    private ResultRepository resultRepository;

    @Inject
    private ResultSearchRepository resultSearchRepository;

    /**
     * POST  /results : Create a new result.
     *
     * @param result the result to create
     * @return the ResponseEntity with status 201 (Created) and with body the new result, or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/results",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Result> createResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to save Result : {}", result);
        if (result.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("result", "idexists", "A new result cannot already have an ID")).body(null);
        }
        Result savedResult = resultRepository.save(result);
        resultSearchRepository.save(savedResult);
        return ResponseEntity.created(new URI("/api/results/" + savedResult.getId()))
            .headers(HeaderUtil.createEntityCreationAlert("result", savedResult.getId().toString()))
            .body(savedResult);
    }

    /**
     * PUT  /results : Updates an existing result.
     *
     * @param result the result to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated result,
     * or with status 400 (Bad Request) if the result is not valid,
     * or with status 500 (Internal Server Error) if the result couldnt be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/results",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Result> updateResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to update Result : {}", result);
        if (result.getId() == null) {
            return createResult(result);
        }
        Result savedResult = resultRepository.save(result);
        resultSearchRepository.save(savedResult);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert("result", savedResult.getId().toString()))
            .body(savedResult);
    }

    /**
     * GET  /results : get all the results.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @RequestMapping(value = "/results",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<Result> getAllResults() {
        log.debug("REST request to get all Results");
        List<Result> results = resultRepository.findAll();
        return results;
    }

    /**
     * GET  /results/:id : get the "id" result.
     *
     * @param id the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/results/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Result> getResult(@PathVariable Long id) {
        log.debug("REST request to get Result : {}", id);
        Result result = resultRepository.findOne(id);
        return Optional.ofNullable(result)
            .map(foundResult -> new ResponseEntity<>(
                foundResult,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /results/:id : delete the "id" result.
     *
     * @param id the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @RequestMapping(value = "/results/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        log.debug("REST request to delete Result : {}", id);
        resultRepository.delete(id);
        resultSearchRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("result", id.toString())).build();
    }

    /**
     * SEARCH  /_search/results?query=:query : search for the result corresponding
     * to the query.
     *
     * @param query the query of the result search
     * @return the result of the search
     */
    @RequestMapping(value = "/_search/results",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<Result> searchResults(@RequestParam String query) {
        log.debug("REST request to search Results for query {}", query);
        return StreamSupport
            .stream(resultSearchRepository.search(queryStringQuery(query)).spliterator(), false)
            .collect(Collectors.toList());
    }

}
