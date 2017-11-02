package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.StatisticCounter;

import de.tum.in.www1.exerciseapp.repository.StatisticCounterRepository;
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
 * REST controller for managing StatisticCounter.
 */
@RestController
@RequestMapping("/api")
public class StatisticCounterResource {

    private final Logger log = LoggerFactory.getLogger(StatisticCounterResource.class);

    private static final String ENTITY_NAME = "statisticCounter";

    private final StatisticCounterRepository statisticCounterRepository;

    public StatisticCounterResource(StatisticCounterRepository statisticCounterRepository) {
        this.statisticCounterRepository = statisticCounterRepository;
    }

    /**
     * POST  /statistic-counters : Create a new statisticCounter.
     *
     * @param statisticCounter the statisticCounter to create
     * @return the ResponseEntity with status 201 (Created) and with body the new statisticCounter, or with status 400 (Bad Request) if the statisticCounter has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/statistic-counters")
    @Timed
    public ResponseEntity<StatisticCounter> createStatisticCounter(@RequestBody StatisticCounter statisticCounter) throws URISyntaxException {
        log.debug("REST request to save StatisticCounter : {}", statisticCounter);
        if (statisticCounter.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new statisticCounter cannot already have an ID")).body(null);
        }
        StatisticCounter result = statisticCounterRepository.save(statisticCounter);
        return ResponseEntity.created(new URI("/api/statistic-counters/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /statistic-counters : Updates an existing statisticCounter.
     *
     * @param statisticCounter the statisticCounter to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated statisticCounter,
     * or with status 400 (Bad Request) if the statisticCounter is not valid,
     * or with status 500 (Internal Server Error) if the statisticCounter couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/statistic-counters")
    @Timed
    public ResponseEntity<StatisticCounter> updateStatisticCounter(@RequestBody StatisticCounter statisticCounter) throws URISyntaxException {
        log.debug("REST request to update StatisticCounter : {}", statisticCounter);
        if (statisticCounter.getId() == null) {
            return createStatisticCounter(statisticCounter);
        }
        StatisticCounter result = statisticCounterRepository.save(statisticCounter);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, statisticCounter.getId().toString()))
            .body(result);
    }

    /**
     * GET  /statistic-counters : get all the statisticCounters.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of statisticCounters in body
     */
    @GetMapping("/statistic-counters")
    @Timed
    public List<StatisticCounter> getAllStatisticCounters() {
        log.debug("REST request to get all StatisticCounters");
        return statisticCounterRepository.findAll();
    }

    /**
     * GET  /statistic-counters/:id : get the "id" statisticCounter.
     *
     * @param id the id of the statisticCounter to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the statisticCounter, or with status 404 (Not Found)
     */
    @GetMapping("/statistic-counters/{id}")
    @Timed
    public ResponseEntity<StatisticCounter> getStatisticCounter(@PathVariable Long id) {
        log.debug("REST request to get StatisticCounter : {}", id);
        StatisticCounter statisticCounter = statisticCounterRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(statisticCounter));
    }

    /**
     * DELETE  /statistic-counters/:id : delete the "id" statisticCounter.
     *
     * @param id the id of the statisticCounter to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/statistic-counters/{id}")
    @Timed
    public ResponseEntity<Void> deleteStatisticCounter(@PathVariable Long id) {
        log.debug("REST request to delete StatisticCounter : {}", id);
        statisticCounterRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
