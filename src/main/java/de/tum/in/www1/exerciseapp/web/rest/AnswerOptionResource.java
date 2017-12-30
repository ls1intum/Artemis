package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.AnswerOption;
import de.tum.in.www1.exerciseapp.repository.AnswerOptionRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
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
 * REST controller for managing AnswerOption.
 */
@RestController
@RequestMapping("/api")
public class AnswerOptionResource {

    private final Logger log = LoggerFactory.getLogger(AnswerOptionResource.class);

    private static final String ENTITY_NAME = "answerOption";

    private final AnswerOptionRepository answerOptionRepository;

    public AnswerOptionResource(AnswerOptionRepository answerOptionRepository) {
        this.answerOptionRepository = answerOptionRepository;
    }

    /**
     * POST  /answer-options : Create a new answerOption.
     *
     * @param answerOption the answerOption to create
     * @return the ResponseEntity with status 201 (Created) and with body the new answerOption, or with status 400 (Bad Request) if the answerOption has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/answer-options")
    @Timed
    public ResponseEntity<AnswerOption> createAnswerOption(@RequestBody AnswerOption answerOption) throws URISyntaxException {
        log.debug("REST request to save AnswerOption : {}", answerOption);
        if (answerOption.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new answerOption cannot already have an ID")).body(null);
        }
        AnswerOption result = answerOptionRepository.save(answerOption);
        return ResponseEntity.created(new URI("/api/answer-options/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /answer-options : Updates an existing answerOption.
     *
     * @param answerOption the answerOption to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated answerOption,
     * or with status 400 (Bad Request) if the answerOption is not valid,
     * or with status 500 (Internal Server Error) if the answerOption couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/answer-options")
    @Timed
    public ResponseEntity<AnswerOption> updateAnswerOption(@RequestBody AnswerOption answerOption) throws URISyntaxException {
        log.debug("REST request to update AnswerOption : {}", answerOption);
        if (answerOption.getId() == null) {
            return createAnswerOption(answerOption);
        }
        AnswerOption result = answerOptionRepository.save(answerOption);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, answerOption.getId().toString()))
            .body(result);
    }

    /**
     * GET  /answer-options : get all the answerOptions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of answerOptions in body
     */
    @GetMapping("/answer-options")
    @Timed
    public List<AnswerOption> getAllAnswerOptions() {
        log.debug("REST request to get all AnswerOptions");
        return answerOptionRepository.findAll();
    }

    /**
     * GET  /answer-options/:id : get the "id" answerOption.
     *
     * @param id the id of the answerOption to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the answerOption, or with status 404 (Not Found)
     */
    @GetMapping("/answer-options/{id}")
    @Timed
    public ResponseEntity<AnswerOption> getAnswerOption(@PathVariable Long id) {
        log.debug("REST request to get AnswerOption : {}", id);
        AnswerOption answerOption = answerOptionRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(answerOption));
    }

    /**
     * DELETE  /answer-options/:id : delete the "id" answerOption.
     *
     * @param id the id of the answerOption to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/answer-options/{id}")
    @Timed
    public ResponseEntity<Void> deleteAnswerOption(@PathVariable Long id) {
        log.debug("REST request to delete AnswerOption : {}", id);
        answerOptionRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
