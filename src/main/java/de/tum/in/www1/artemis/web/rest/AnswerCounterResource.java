package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.AnswerCounter;
import de.tum.in.www1.artemis.repository.AnswerCounterRepository;
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
 * REST controller for managing AnswerCounter.
 */
@RestController
@RequestMapping("/api")
public class AnswerCounterResource {

    private final Logger log = LoggerFactory.getLogger(AnswerCounterResource.class);

    private static final String ENTITY_NAME = "answerCounter";

    private AnswerCounterRepository answerCounterRepository;

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
    @Timed
    public ResponseEntity<AnswerCounter> createAnswerCounter(@RequestBody AnswerCounter answerCounter) throws URISyntaxException {
        log.debug("REST request to save AnswerCounter : {}", answerCounter);
        if (answerCounter.getId() != null) {
            throw new BadRequestAlertException("A new answerCounter cannot already have an ID", ENTITY_NAME, "idexists");
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
    @Timed
    public ResponseEntity<AnswerCounter> updateAnswerCounter(@RequestBody AnswerCounter answerCounter) throws URISyntaxException {
        log.debug("REST request to update AnswerCounter : {}", answerCounter);
        if (answerCounter.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
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
    @Timed
    public ResponseEntity<AnswerCounter> getAnswerCounter(@PathVariable Long id) {
        log.debug("REST request to get AnswerCounter : {}", id);
        Optional<AnswerCounter> answerCounter = answerCounterRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(answerCounter);
    }

    /**
     * DELETE  /answer-counters/:id : delete the "id" answerCounter.
     *
     * @param id the id of the answerCounter to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/answer-counters/{id}")
    @Timed
    public ResponseEntity<Void> deleteAnswerCounter(@PathVariable Long id) {
        log.debug("REST request to delete AnswerCounter : {}", id);

        answerCounterRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
