package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.MultipleChoiceStatistic;

import de.tum.in.www1.exerciseapp.repository.MultipleChoiceStatisticRepository;
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
 * REST controller for managing MultipleChoiceStatistic.
 */
@RestController
@RequestMapping("/api")
public class MultipleChoiceStatisticResource {

    private final Logger log = LoggerFactory.getLogger(MultipleChoiceStatisticResource.class);

    private static final String ENTITY_NAME = "multipleChoiceStatistic";

    private final MultipleChoiceStatisticRepository multipleChoiceStatisticRepository;

    public MultipleChoiceStatisticResource(MultipleChoiceStatisticRepository multipleChoiceStatisticRepository) {
        this.multipleChoiceStatisticRepository = multipleChoiceStatisticRepository;
    }

    /**
     * POST  /multiple-choice-statistics : Create a new multipleChoiceStatistic.
     *
     * @param multipleChoiceStatistic the multipleChoiceStatistic to create
     * @return the ResponseEntity with status 201 (Created) and with body the new multipleChoiceStatistic, or with status 400 (Bad Request) if the multipleChoiceStatistic has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/multiple-choice-statistics")
    @Timed
    public ResponseEntity<MultipleChoiceStatistic> createMultipleChoiceStatistic(@RequestBody MultipleChoiceStatistic multipleChoiceStatistic) throws URISyntaxException {
        log.debug("REST request to save MultipleChoiceStatistic : {}", multipleChoiceStatistic);
        if (multipleChoiceStatistic.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new multipleChoiceStatistic cannot already have an ID")).body(null);
        }
        MultipleChoiceStatistic result = multipleChoiceStatisticRepository.save(multipleChoiceStatistic);
        return ResponseEntity.created(new URI("/api/multiple-choice-statistics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /multiple-choice-statistics : Updates an existing multipleChoiceStatistic.
     *
     * @param multipleChoiceStatistic the multipleChoiceStatistic to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated multipleChoiceStatistic,
     * or with status 400 (Bad Request) if the multipleChoiceStatistic is not valid,
     * or with status 500 (Internal Server Error) if the multipleChoiceStatistic couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/multiple-choice-statistics")
    @Timed
    public ResponseEntity<MultipleChoiceStatistic> updateMultipleChoiceStatistic(@RequestBody MultipleChoiceStatistic multipleChoiceStatistic) throws URISyntaxException {
        log.debug("REST request to update MultipleChoiceStatistic : {}", multipleChoiceStatistic);
        if (multipleChoiceStatistic.getId() == null) {
            return createMultipleChoiceStatistic(multipleChoiceStatistic);
        }
        MultipleChoiceStatistic result = multipleChoiceStatisticRepository.save(multipleChoiceStatistic);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, multipleChoiceStatistic.getId().toString()))
            .body(result);
    }

    /**
     * GET  /multiple-choice-statistics : get all the multipleChoiceStatistics.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of multipleChoiceStatistics in body
     */
    @GetMapping("/multiple-choice-statistics")
    @Timed
    public List<MultipleChoiceStatistic> getAllMultipleChoiceStatistics() {
        log.debug("REST request to get all MultipleChoiceStatistics");
        return multipleChoiceStatisticRepository.findAll();
    }

    /**
     * GET  /multiple-choice-statistics/:id : get the "id" multipleChoiceStatistic.
     *
     * @param id the id of the multipleChoiceStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the multipleChoiceStatistic, or with status 404 (Not Found)
     */
    @GetMapping("/multiple-choice-statistics/{id}")
    @Timed
    public ResponseEntity<MultipleChoiceStatistic> getMultipleChoiceStatistic(@PathVariable Long id) {
        log.debug("REST request to get MultipleChoiceStatistic : {}", id);
        MultipleChoiceStatistic multipleChoiceStatistic = multipleChoiceStatisticRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(multipleChoiceStatistic));
    }

    /**
     * DELETE  /multiple-choice-statistics/:id : delete the "id" multipleChoiceStatistic.
     *
     * @param id the id of the multipleChoiceStatistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/multiple-choice-statistics/{id}")
    @Timed
    public ResponseEntity<Void> deleteMultipleChoiceStatistic(@PathVariable Long id) {
        log.debug("REST request to delete MultipleChoiceStatistic : {}", id);
        multipleChoiceStatisticRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
