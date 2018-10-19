package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.QuizPointStatistic;
import de.tum.in.www1.artemis.repository.QuizPointStatisticRepository;
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

    private QuizPointStatisticRepository quizPointStatisticRepository;

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
    @Timed
    public ResponseEntity<QuizPointStatistic> createQuizPointStatistic(@RequestBody QuizPointStatistic quizPointStatistic) throws URISyntaxException {
        log.debug("REST request to save QuizPointStatistic : {}", quizPointStatistic);
        if (quizPointStatistic.getId() != null) {
            throw new BadRequestAlertException("A new quizPointStatistic cannot already have an ID", ENTITY_NAME, "idexists");
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
    @Timed
    public ResponseEntity<QuizPointStatistic> updateQuizPointStatistic(@RequestBody QuizPointStatistic quizPointStatistic) throws URISyntaxException {
        log.debug("REST request to update QuizPointStatistic : {}", quizPointStatistic);
        if (quizPointStatistic.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
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
    @Timed
    public ResponseEntity<QuizPointStatistic> getQuizPointStatistic(@PathVariable Long id) {
        log.debug("REST request to get QuizPointStatistic : {}", id);
        Optional<QuizPointStatistic> quizPointStatistic = quizPointStatisticRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(quizPointStatistic);
    }

    /**
     * DELETE  /quiz-point-statistics/:id : delete the "id" quizPointStatistic.
     *
     * @param id the id of the quizPointStatistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-point-statistics/{id}")
    @Timed
    public ResponseEntity<Void> deleteQuizPointStatistic(@PathVariable Long id) {
        log.debug("REST request to delete QuizPointStatistic : {}", id);

        quizPointStatisticRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
