package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.MultipleChoiceSubmittedAnswer;
import de.tum.in.www1.artemis.repository.MultipleChoiceSubmittedAnswerRepository;
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
 * REST controller for managing MultipleChoiceSubmittedAnswer.
 */
@RestController
@RequestMapping("/api")
public class MultipleChoiceSubmittedAnswerResource {

    private final Logger log = LoggerFactory.getLogger(MultipleChoiceSubmittedAnswerResource.class);

    private static final String ENTITY_NAME = "multipleChoiceSubmittedAnswer";

    private final MultipleChoiceSubmittedAnswerRepository multipleChoiceSubmittedAnswerRepository;

    public MultipleChoiceSubmittedAnswerResource(MultipleChoiceSubmittedAnswerRepository multipleChoiceSubmittedAnswerRepository) {
        this.multipleChoiceSubmittedAnswerRepository = multipleChoiceSubmittedAnswerRepository;
    }

    /**
     * POST  /multiple-choice-submitted-answers : Create a new multipleChoiceSubmittedAnswer.
     *
     * @param multipleChoiceSubmittedAnswer the multipleChoiceSubmittedAnswer to create
     * @return the ResponseEntity with status 201 (Created) and with body the new multipleChoiceSubmittedAnswer, or with status 400 (Bad Request) if the multipleChoiceSubmittedAnswer has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/multiple-choice-submitted-answers")
    @Timed
    public ResponseEntity<MultipleChoiceSubmittedAnswer> createMultipleChoiceSubmittedAnswer(@RequestBody MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) throws URISyntaxException {
        log.debug("REST request to save MultipleChoiceSubmittedAnswer : {}", multipleChoiceSubmittedAnswer);
        if (multipleChoiceSubmittedAnswer.getId() != null) {
            throw new BadRequestAlertException("A new multipleChoiceSubmittedAnswer cannot already have an ID", ENTITY_NAME, "idexists");
        }
        MultipleChoiceSubmittedAnswer result = multipleChoiceSubmittedAnswerRepository.save(multipleChoiceSubmittedAnswer);
        return ResponseEntity.created(new URI("/api/multiple-choice-submitted-answers/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /multiple-choice-submitted-answers : Updates an existing multipleChoiceSubmittedAnswer.
     *
     * @param multipleChoiceSubmittedAnswer the multipleChoiceSubmittedAnswer to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated multipleChoiceSubmittedAnswer,
     * or with status 400 (Bad Request) if the multipleChoiceSubmittedAnswer is not valid,
     * or with status 500 (Internal Server Error) if the multipleChoiceSubmittedAnswer couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/multiple-choice-submitted-answers")
    @Timed
    public ResponseEntity<MultipleChoiceSubmittedAnswer> updateMultipleChoiceSubmittedAnswer(@RequestBody MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) throws URISyntaxException {
        log.debug("REST request to update MultipleChoiceSubmittedAnswer : {}", multipleChoiceSubmittedAnswer);
        if (multipleChoiceSubmittedAnswer.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        MultipleChoiceSubmittedAnswer result = multipleChoiceSubmittedAnswerRepository.save(multipleChoiceSubmittedAnswer);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, multipleChoiceSubmittedAnswer.getId().toString()))
            .body(result);
    }

    /**
     * GET  /multiple-choice-submitted-answers : get all the multipleChoiceSubmittedAnswers.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many)
     * @return the ResponseEntity with status 200 (OK) and the list of multipleChoiceSubmittedAnswers in body
     */
    @GetMapping("/multiple-choice-submitted-answers")
    @Timed
    public List<MultipleChoiceSubmittedAnswer> getAllMultipleChoiceSubmittedAnswers(@RequestParam(required = false, defaultValue = "false") boolean eagerload) {
        log.debug("REST request to get all MultipleChoiceSubmittedAnswers");
        return multipleChoiceSubmittedAnswerRepository.findAllWithEagerRelationships();
    }

    /**
     * GET  /multiple-choice-submitted-answers/:id : get the "id" multipleChoiceSubmittedAnswer.
     *
     * @param id the id of the multipleChoiceSubmittedAnswer to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the multipleChoiceSubmittedAnswer, or with status 404 (Not Found)
     */
    @GetMapping("/multiple-choice-submitted-answers/{id}")
    @Timed
    public ResponseEntity<MultipleChoiceSubmittedAnswer> getMultipleChoiceSubmittedAnswer(@PathVariable Long id) {
        log.debug("REST request to get MultipleChoiceSubmittedAnswer : {}", id);
        Optional<MultipleChoiceSubmittedAnswer> multipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswerRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(multipleChoiceSubmittedAnswer);
    }

    /**
     * DELETE  /multiple-choice-submitted-answers/:id : delete the "id" multipleChoiceSubmittedAnswer.
     *
     * @param id the id of the multipleChoiceSubmittedAnswer to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/multiple-choice-submitted-answers/{id}")
    @Timed
    public ResponseEntity<Void> deleteMultipleChoiceSubmittedAnswer(@PathVariable Long id) {
        log.debug("REST request to delete MultipleChoiceSubmittedAnswer : {}", id);

        multipleChoiceSubmittedAnswerRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
