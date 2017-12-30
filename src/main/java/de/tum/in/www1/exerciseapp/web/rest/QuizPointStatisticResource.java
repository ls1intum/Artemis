package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.QuizPointStatistic;
import de.tum.in.www1.exerciseapp.repository.QuizPointStatisticRepository;
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
 * REST controller for managing QuizPointStatistic.
 */
@RestController
@RequestMapping("/api")
public class QuizPointStatisticResource {

    private final Logger log = LoggerFactory.getLogger(QuizPointStatisticResource.class);

    private static final String ENTITY_NAME = "quizPointStatistic";

    private final QuizPointStatisticRepository quizPointStatisticRepository;

    public QuizPointStatisticResource(QuizPointStatisticRepository quizPointStatisticRepository) {
        this.quizPointStatisticRepository = quizPointStatisticRepository;
    }

    /**
     * POST  /quiz-point-statistics : Create a new quizPointStatistic.
     *
     * @param quizPointStatistic the quizPointStatistic to create
     * @return the ResponseEntity with status 201 (Created) and with body the new quizPointStatistic, or with status 400 (Bad Request) if the quizPointStatistic has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/quiz-point-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<QuizPointStatistic> createQuizPointStatistic(@RequestBody QuizPointStatistic quizPointStatistic) throws URISyntaxException {
        log.debug("REST request to save QuizPointStatistic : {}", quizPointStatistic);
        if (quizPointStatistic.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizPointStatistic cannot already have an ID")).body(null);
        }
        QuizPointStatistic result = quizPointStatisticRepository.save(quizPointStatistic);
        return ResponseEntity.created(new URI("/api/quiz-point-statistics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /quiz-point-statistics : Updates an existing quizPointStatistic.
     *
     * @param quizPointStatistic the quizPointStatistic to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated quizPointStatistic,
     * or with status 400 (Bad Request) if the quizPointStatistic is not valid,
     * or with status 500 (Internal Server Error) if the quizPointStatistic couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/quiz-point-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<QuizPointStatistic> updateQuizPointStatistic(@RequestBody QuizPointStatistic quizPointStatistic) throws URISyntaxException {
        log.debug("REST request to update QuizPointStatistic : {}", quizPointStatistic);
        if (quizPointStatistic.getId() == null) {
            return createQuizPointStatistic(quizPointStatistic);
        }
        QuizPointStatistic result = quizPointStatisticRepository.save(quizPointStatistic);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, quizPointStatistic.getId().toString()))
            .body(result);
    }

    /**
     * GET  /quiz-point-statistics : get all the quizPointStatistics.
     *
     * @param filter the filter of the request
     * @return the ResponseEntity with status 200 (OK) and the list of quizPointStatistics in body
     */
    @GetMapping("/quiz-point-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public List<QuizPointStatistic> getAllQuizPointStatistics(@RequestParam(required = false) String filter) {
        if ("quiz-is-null".equals(filter)) {
            log.debug("REST request to get all QuizPointStatistics where quiz is null");
            return StreamSupport
                .stream(quizPointStatisticRepository.findAll().spliterator(), false)
                .filter(quizPointStatistic -> quizPointStatistic.getQuiz() == null)
                .collect(Collectors.toList());
        }
        log.debug("REST request to get all QuizPointStatistics");
        return quizPointStatisticRepository.findAll();
    }

    /**
     * GET  /quiz-point-statistics/:id : get the "id" quizPointStatistic.
     *
     * @param id the id of the quizPointStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizPointStatistic, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-point-statistics/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<QuizPointStatistic> getQuizPointStatistic(@PathVariable Long id) {
        log.debug("REST request to get QuizPointStatistic : {}", id);
        QuizPointStatistic quizPointStatistic = quizPointStatisticRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizPointStatistic));
    }

    /**
     * GET  /quiz-point-statistic/:id : get the "id" quizPointStatistic.
     *
     * @param id the id of the quizPointStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-point-statistic/{id}/for-student")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizPointStatistic> getQuizExerciseForStudent(@PathVariable Long id) {
        log.debug("REST request to get QuizPointStatistic : {}", id);
        QuizPointStatistic quizPointStatistic = quizPointStatisticRepository.findOne(id);

        // filter out the statistic information if the statistic is not released
        if(!quizPointStatistic.isReleased()) {
            // filter out all Information about the Statistic except if it is released (so students can't get the pointCounters before the Statistic is released)
            quizPointStatistic.setQuiz(null);
            quizPointStatistic.setPointCounters(null);
            quizPointStatistic.setParticipantsRated(null);
            quizPointStatistic.setParticipantsUnrated(null);
        }

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizPointStatistic));
    }

    /**
     * DELETE  /quiz-point-statistics/:id : delete the "id" quizPointStatistic.
     *
     * @param id the id of the quizPointStatistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-point-statistics/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<Void> deleteQuizPointStatistic(@PathVariable Long id) {
        log.debug("REST request to delete QuizPointStatistic : {}", id);
        quizPointStatisticRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
