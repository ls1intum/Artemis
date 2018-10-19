package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.Statistic;
import de.tum.in.www1.artemis.repository.StatisticRepository;
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
 * REST controller for managing Statistic.
 */
@RestController
@RequestMapping("/api")
public class StatisticResource {

    private final Logger log = LoggerFactory.getLogger(StatisticResource.class);

    private static final String ENTITY_NAME = "statistic";

    private StatisticRepository statisticRepository;

    public StatisticResource(StatisticRepository statisticRepository) {
        this.statisticRepository = statisticRepository;
    }

    /**
     * POST  /statistics : Create a new statistic.
     *
     * @param statistic the statistic to create
     * @return the ResponseEntity with status 201 (Created) and with body the new statistic, or with status 400 (Bad Request) if the statistic has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/statistics")
    @Timed
    public ResponseEntity<Statistic> createStatistic(@RequestBody Statistic statistic) throws URISyntaxException {
        log.debug("REST request to save Statistic : {}", statistic);
        if (statistic.getId() != null) {
            throw new BadRequestAlertException("A new statistic cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Statistic result = statisticRepository.save(statistic);
        return ResponseEntity.created(new URI("/api/statistics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /statistics : Updates an existing statistic.
     *
     * @param statistic the statistic to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated statistic,
     * or with status 400 (Bad Request) if the statistic is not valid,
     * or with status 500 (Internal Server Error) if the statistic couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/statistics")
    @Timed
    public ResponseEntity<Statistic> updateStatistic(@RequestBody Statistic statistic) throws URISyntaxException {
        log.debug("REST request to update Statistic : {}", statistic);
        if (statistic.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Statistic result = statisticRepository.save(statistic);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, statistic.getId().toString()))
            .body(result);
    }

    /**
     * GET  /statistics : get all the statistics.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of statistics in body
     */
    @GetMapping("/statistics")
    @Timed
    public List<Statistic> getAllStatistics() {
        log.debug("REST request to get all Statistics");
        return statisticRepository.findAll();
    }

    /**
     * GET  /statistics/:id : get the "id" statistic.
     *
     * @param id the id of the statistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the statistic, or with status 404 (Not Found)
     */
    @GetMapping("/statistics/{id}")
    @Timed
    public ResponseEntity<Statistic> getStatistic(@PathVariable Long id) {
        log.debug("REST request to get Statistic : {}", id);
        Optional<Statistic> statistic = statisticRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(statistic);
    }

    /**
     * DELETE  /statistics/:id : delete the "id" statistic.
     *
     * @param id the id of the statistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/statistics/{id}")
    @Timed
    public ResponseEntity<Void> deleteStatistic(@PathVariable Long id) {
        log.debug("REST request to delete Statistic : {}", id);

        statisticRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
