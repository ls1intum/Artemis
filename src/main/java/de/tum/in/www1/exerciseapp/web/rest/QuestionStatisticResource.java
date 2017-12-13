package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.QuestionStatistic;

import de.tum.in.www1.exerciseapp.repository.QuestionStatisticRepository;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * REST controller for managing QuestionStatistic.
 */
@RestController
@RequestMapping("/api")
public class QuestionStatisticResource {

    private final Logger log = LoggerFactory.getLogger(QuestionStatisticResource.class);

    private static final String ENTITY_NAME = "questionStatistic";

    private final QuestionStatisticRepository questionStatisticRepository;

    public QuestionStatisticResource(QuestionStatisticRepository questionStatisticRepository) {
        this.questionStatisticRepository = questionStatisticRepository;
    }

    /**
     * POST  /question-statistics : Create a new questionStatistic.
     *
     * @param questionStatistic the questionStatistic to create
     * @return the ResponseEntity with status 201 (Created) and with body the new questionStatistic, or with status 400 (Bad Request) if the questionStatistic has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/question-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<QuestionStatistic> createQuestionStatistic(@RequestBody QuestionStatistic questionStatistic) throws URISyntaxException {
        log.debug("REST request to save QuestionStatistic : {}", questionStatistic);
        if (questionStatistic.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new questionStatistic cannot already have an ID")).body(null);
        }
        QuestionStatistic result = questionStatisticRepository.save(questionStatistic);
        return ResponseEntity.created(new URI("/api/question-statistics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /question-statistics : Updates an existing questionStatistic.
     *
     * @param questionStatistic the questionStatistic to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated questionStatistic,
     * or with status 400 (Bad Request) if the questionStatistic is not valid,
     * or with status 500 (Internal Server Error) if the questionStatistic couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/question-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<QuestionStatistic> updateQuestionStatistic(@RequestBody QuestionStatistic questionStatistic) throws URISyntaxException {
        log.debug("REST request to update QuestionStatistic : {}", questionStatistic);
        if (questionStatistic.getId() == null) {
            return createQuestionStatistic(questionStatistic);
        }
        QuestionStatistic result = questionStatisticRepository.save(questionStatistic);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, questionStatistic.getId().toString()))
            .body(result);
    }

    /**
     * GET  /question-statistics : get all the questionStatistics.
     *
     * @param filter the filter of the request
     * @return the ResponseEntity with status 200 (OK) and the list of questionStatistics in body
     */
    @GetMapping("/question-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public List<QuestionStatistic> getAllQuestionStatistics(@RequestParam(required = false) String filter) {
        if ("question-is-null".equals(filter)) {
            log.debug("REST request to get all QuestionStatistics where question is null");
            return StreamSupport
                .stream(questionStatisticRepository.findAll().spliterator(), false)
                .filter(questionStatistic -> questionStatistic.getQuestion() == null)
                .collect(Collectors.toList());
        }
        log.debug("REST request to get all QuestionStatistics");
        return questionStatisticRepository.findAll();
    }

    /**
     * GET  /question-statistics/:id : get the "id" questionStatistic.
     *
     * @param id the id of the questionStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the questionStatistic, or with status 404 (Not Found)
     */
    @GetMapping("/question-statistics/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<QuestionStatistic> getQuestionStatistic(@PathVariable Long id) {
        log.debug("REST request to get QuestionStatistic : {}", id);
        QuestionStatistic questionStatistic = questionStatisticRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(questionStatistic));
    }

    /**
     * DELETE  /question-statistics/:id : delete the "id" questionStatistic.
     *
     * @param id the id of the questionStatistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/question-statistics/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<Void> deleteQuestionStatistic(@PathVariable Long id) {
        log.debug("REST request to delete QuestionStatistic : {}", id);
        questionStatisticRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
