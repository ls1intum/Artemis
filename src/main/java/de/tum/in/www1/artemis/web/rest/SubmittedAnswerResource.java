package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.SubmittedAnswer;
import de.tum.in.www1.artemis.repository.SubmittedAnswerRepository;
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
 * REST controller for managing SubmittedAnswer.
 */
@RestController
@RequestMapping("/api")
public class SubmittedAnswerResource {

    private final Logger log = LoggerFactory.getLogger(SubmittedAnswerResource.class);

    private static final String ENTITY_NAME = "submittedAnswer";

    private SubmittedAnswerRepository submittedAnswerRepository;

    public SubmittedAnswerResource(SubmittedAnswerRepository submittedAnswerRepository) {
        this.submittedAnswerRepository = submittedAnswerRepository;
    }

    /**
     * POST  /submitted-answers : Create a new submittedAnswer.
     *
     * @param submittedAnswer the submittedAnswer to create
     * @return the ResponseEntity with status 201 (Created) and with body the new submittedAnswer, or with status 400 (Bad Request) if the submittedAnswer has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/submitted-answers")
    @Timed
    public ResponseEntity<SubmittedAnswer> createSubmittedAnswer(@RequestBody SubmittedAnswer submittedAnswer) throws URISyntaxException {
        log.debug("REST request to save SubmittedAnswer : {}", submittedAnswer);
        if (submittedAnswer.getId() != null) {
            throw new BadRequestAlertException("A new submittedAnswer cannot already have an ID", ENTITY_NAME, "idexists");
        }
        SubmittedAnswer result = submittedAnswerRepository.save(submittedAnswer);
        return ResponseEntity.created(new URI("/api/submitted-answers/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /submitted-answers : Updates an existing submittedAnswer.
     *
     * @param submittedAnswer the submittedAnswer to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated submittedAnswer,
     * or with status 400 (Bad Request) if the submittedAnswer is not valid,
     * or with status 500 (Internal Server Error) if the submittedAnswer couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/submitted-answers")
    @Timed
    public ResponseEntity<SubmittedAnswer> updateSubmittedAnswer(@RequestBody SubmittedAnswer submittedAnswer) throws URISyntaxException {
        log.debug("REST request to update SubmittedAnswer : {}", submittedAnswer);
        if (submittedAnswer.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        SubmittedAnswer result = submittedAnswerRepository.save(submittedAnswer);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, submittedAnswer.getId().toString()))
            .body(result);
    }

    /**
     * GET  /submitted-answers : get all the submittedAnswers.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of submittedAnswers in body
     */
    @GetMapping("/submitted-answers")
    @Timed
    public List<SubmittedAnswer> getAllSubmittedAnswers() {
        log.debug("REST request to get all SubmittedAnswers");
        return submittedAnswerRepository.findAll();
    }

    /**
     * GET  /submitted-answers/:id : get the "id" submittedAnswer.
     *
     * @param id the id of the submittedAnswer to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the submittedAnswer, or with status 404 (Not Found)
     */
    @GetMapping("/submitted-answers/{id}")
    @Timed
    public ResponseEntity<SubmittedAnswer> getSubmittedAnswer(@PathVariable Long id) {
        log.debug("REST request to get SubmittedAnswer : {}", id);
        Optional<SubmittedAnswer> submittedAnswer = submittedAnswerRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(submittedAnswer);
    }

    /**
     * DELETE  /submitted-answers/:id : delete the "id" submittedAnswer.
     *
     * @param id the id of the submittedAnswer to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/submitted-answers/{id}")
    @Timed
    public ResponseEntity<Void> deleteSubmittedAnswer(@PathVariable Long id) {
        log.debug("REST request to delete SubmittedAnswer : {}", id);

        submittedAnswerRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
