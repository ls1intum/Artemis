package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.MultipleChoiceQuestionStatistic;

import de.tum.in.www1.exerciseapp.domain.QuizPointStatistic;
import de.tum.in.www1.exerciseapp.repository.MultipleChoiceQuestionStatisticRepository;
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
 * REST controller for managing MultipleChoiceQuestionStatistic.
 */
@RestController
@RequestMapping("/api")
public class MultipleChoiceQuestionStatisticResource {

    private final Logger log = LoggerFactory.getLogger(MultipleChoiceQuestionStatisticResource.class);

    private static final String ENTITY_NAME = "multipleChoiceQuestionStatistic";

    private final MultipleChoiceQuestionStatisticRepository multipleChoiceQuestionStatisticRepository;

    public MultipleChoiceQuestionStatisticResource(MultipleChoiceQuestionStatisticRepository multipleChoiceQuestionStatisticRepository) {
        this.multipleChoiceQuestionStatisticRepository = multipleChoiceQuestionStatisticRepository;
    }

    /**
     * POST  /multiple-choice-question-statistics : Create a new multipleChoiceQuestionStatistic.
     *
     * @param multipleChoiceQuestionStatistic the multipleChoiceQuestionStatistic to create
     * @return the ResponseEntity with status 201 (Created) and with body the new multipleChoiceQuestionStatistic, or with status 400 (Bad Request) if the multipleChoiceQuestionStatistic has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/multiple-choice-question-statistics")
    @Timed
    public ResponseEntity<MultipleChoiceQuestionStatistic> createMultipleChoiceQuestionStatistic(@RequestBody MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) throws URISyntaxException {
        log.debug("REST request to save MultipleChoiceQuestionStatistic : {}", multipleChoiceQuestionStatistic);
        if (multipleChoiceQuestionStatistic.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new multipleChoiceQuestionStatistic cannot already have an ID")).body(null);
        }
        MultipleChoiceQuestionStatistic result = multipleChoiceQuestionStatisticRepository.save(multipleChoiceQuestionStatistic);
        return ResponseEntity.created(new URI("/api/multiple-choice-question-statistics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /multiple-choice-question-statistics : Updates an existing multipleChoiceQuestionStatistic.
     *
     * @param multipleChoiceQuestionStatistic the multipleChoiceQuestionStatistic to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated multipleChoiceQuestionStatistic,
     * or with status 400 (Bad Request) if the multipleChoiceQuestionStatistic is not valid,
     * or with status 500 (Internal Server Error) if the multipleChoiceQuestionStatistic couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/multiple-choice-question-statistics")
    @Timed
    public ResponseEntity<MultipleChoiceQuestionStatistic> updateMultipleChoiceQuestionStatistic(@RequestBody MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) throws URISyntaxException {
        log.debug("REST request to update MultipleChoiceQuestionStatistic : {}", multipleChoiceQuestionStatistic);
        if (multipleChoiceQuestionStatistic.getId() == null) {
            return createMultipleChoiceQuestionStatistic(multipleChoiceQuestionStatistic);
        }
        MultipleChoiceQuestionStatistic result = multipleChoiceQuestionStatisticRepository.save(multipleChoiceQuestionStatistic);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, multipleChoiceQuestionStatistic.getId().toString()))
            .body(result);
    }

    /**
     * GET  /multiple-choice-question-statistics : get all the multipleChoiceQuestionStatistics.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of multipleChoiceQuestionStatistics in body
     */
    @GetMapping("/multiple-choice-question-statistics")
    @Timed
    public List<MultipleChoiceQuestionStatistic> getAllMultipleChoiceQuestionStatistics() {
        log.debug("REST request to get all MultipleChoiceQuestionStatistics");
        return multipleChoiceQuestionStatisticRepository.findAll();
    }

    /**
     * GET  /multiple-choice-question-statistics/:id : get the "id" multipleChoiceQuestionStatistic.
     *
     * @param id the id of the multipleChoiceQuestionStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the multipleChoiceQuestionStatistic, or with status 404 (Not Found)
     */
    @GetMapping("/multiple-choice-question-statistics/{id}")
    @Timed
    public ResponseEntity<MultipleChoiceQuestionStatistic> getMultipleChoiceQuestionStatistic(@PathVariable Long id) {
        log.debug("REST request to get MultipleChoiceQuestionStatistic : {}", id);
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic = multipleChoiceQuestionStatisticRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(multipleChoiceQuestionStatistic));
    }

    /**
     * GET  /multiple-choice-question-statistic/:id : get the "id" multipleChoiceQuestionStatistic.
     *
     * @param id the id of the multipleChoiceQuestionStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the multipleChoiceQuestionStatistic, or with status 404 (Not Found)
     */
    @GetMapping("/multiple-choice-question-statistic/{id}/for-student")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<MultipleChoiceQuestionStatistic> getQuizExerciseForStudent(@PathVariable Long id) {
        log.debug("REST request to get QuizPointStatistic : {}", id);
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic = multipleChoiceQuestionStatisticRepository.findOne(id);

        // only filter out the information if the statistic is not released
        if(!multipleChoiceQuestionStatistic.isReleased()) {
            // filter out all Information about the Statistic except if it is released (so students can't get the information before the Statistic is released)
            multipleChoiceQuestionStatistic.setQuestion(null);
            multipleChoiceQuestionStatistic.setAnswerCounters(null);
            multipleChoiceQuestionStatistic.setRatedCorrectCounter(null);
            multipleChoiceQuestionStatistic.setUnRatedCorrectCounter(null);
            multipleChoiceQuestionStatistic.setParticipantsRated(null);
            multipleChoiceQuestionStatistic.setParticipantsUnrated(null);
        }

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(multipleChoiceQuestionStatistic));
    }


    /**
     * DELETE  /multiple-choice-question-statistics/:id : delete the "id" multipleChoiceQuestionStatistic.
     *
     * @param id the id of the multipleChoiceQuestionStatistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/multiple-choice-question-statistics/{id}")
    @Timed
    public ResponseEntity<Void> deleteMultipleChoiceQuestionStatistic(@PathVariable Long id) {
        log.debug("REST request to delete MultipleChoiceQuestionStatistic : {}", id);
        multipleChoiceQuestionStatisticRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
