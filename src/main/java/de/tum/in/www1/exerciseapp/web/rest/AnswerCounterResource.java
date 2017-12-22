package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.AnswerCounter;

import de.tum.in.www1.exerciseapp.repository.AnswerCounterRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing AnswerCounter.
 */
@RestController
@RequestMapping("/api")
public class AnswerCounterResource {

    private final Logger log = LoggerFactory.getLogger(AnswerCounterResource.class);

    private static final String ENTITY_NAME = "answerCounter";

    private final AnswerCounterRepository answerCounterRepository;

    public AnswerCounterResource(AnswerCounterRepository answerCounterRepository) {
        this.answerCounterRepository = answerCounterRepository;
    }

    /**
     * POST  /answer-counters : Create a new answerCounter.
     *
     * @param answerCounter the answerCounter to create
     * @return the ResponseEntity with status 201 (Created) and with body the new answerCounter, or with status 400 (Bad Request) if the answerCounter has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/answer-counters")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<AnswerCounter> createAnswerCounter(@RequestBody AnswerCounter answerCounter) throws URISyntaxException {
        log.debug("REST request to save AnswerCounter : {}", answerCounter);
        if (answerCounter.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new answerCounter cannot already have an ID")).body(null);
        }
        AnswerCounter result = answerCounterRepository.save(answerCounter);
        return ResponseEntity.created(new URI("/api/answer-counters/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /answer-counters : Updates an existing answerCounter.
     *
     * @param answerCounter the answerCounter to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated answerCounter,
     * or with status 400 (Bad Request) if the answerCounter is not valid,
     * or with status 500 (Internal Server Error) if the answerCounter couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/answer-counters")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<AnswerCounter> updateAnswerCounter(@RequestBody AnswerCounter answerCounter) throws URISyntaxException {
        log.debug("REST request to update AnswerCounter : {}", answerCounter);
        if (answerCounter.getId() == null) {
            return createAnswerCounter(answerCounter);
        }
        AnswerCounter result = answerCounterRepository.save(answerCounter);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, answerCounter.getId().toString()))
            .body(result);
    }

    /**
     * GET  /answer-counters : get all the answerCounters.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of answerCounters in body
     */
    @GetMapping("/answer-counters")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public List<AnswerCounter> getAllAnswerCounters() {
        log.debug("REST request to get all AnswerCounters");
        return answerCounterRepository.findAll();
    }

    /**
     * GET  /answer-counters/:id : get the "id" answerCounter.
     *
     * @param id the id of the answerCounter to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the answerCounter, or with status 404 (Not Found)
     */
    @GetMapping("/answer-counters/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<AnswerCounter> getAnswerCounter(@PathVariable Long id) {
        log.debug("REST request to get AnswerCounter : {}", id);
        AnswerCounter answerCounter = answerCounterRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(answerCounter));
    }

    /**
     * DELETE  /answer-counters/:id : delete the "id" answerCounter.
     *
     * @param id the id of the answerCounter to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/answer-counters/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<Void> deleteAnswerCounter(@PathVariable Long id) {
        log.debug("REST request to delete AnswerCounter : {}", id);
        answerCounterRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
