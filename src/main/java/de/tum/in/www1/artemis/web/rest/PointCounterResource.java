package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.PointCounter;
import de.tum.in.www1.artemis.repository.PointCounterRepository;
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
 * REST controller for managing PointCounter.
 */
@RestController
@RequestMapping("/api")
public class PointCounterResource {

    private final Logger log = LoggerFactory.getLogger(PointCounterResource.class);

    private static final String ENTITY_NAME = "pointCounter";

    private PointCounterRepository pointCounterRepository;

    public PointCounterResource(PointCounterRepository pointCounterRepository) {
        this.pointCounterRepository = pointCounterRepository;
    }

    /**
     * POST  /point-counters : Create a new pointCounter.
     *
     * @param pointCounter the pointCounter to create
     * @return the ResponseEntity with status 201 (Created) and with body the new pointCounter, or with status 400 (Bad Request) if the pointCounter has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/point-counters")
    @Timed
    public ResponseEntity<PointCounter> createPointCounter(@RequestBody PointCounter pointCounter) throws URISyntaxException {
        log.debug("REST request to save PointCounter : {}", pointCounter);
        if (pointCounter.getId() != null) {
            throw new BadRequestAlertException("A new pointCounter cannot already have an ID", ENTITY_NAME, "idexists");
        }
        PointCounter result = pointCounterRepository.save(pointCounter);
        return ResponseEntity.created(new URI("/api/point-counters/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /point-counters : Updates an existing pointCounter.
     *
     * @param pointCounter the pointCounter to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated pointCounter,
     * or with status 400 (Bad Request) if the pointCounter is not valid,
     * or with status 500 (Internal Server Error) if the pointCounter couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/point-counters")
    @Timed
    public ResponseEntity<PointCounter> updatePointCounter(@RequestBody PointCounter pointCounter) throws URISyntaxException {
        log.debug("REST request to update PointCounter : {}", pointCounter);
        if (pointCounter.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        PointCounter result = pointCounterRepository.save(pointCounter);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, pointCounter.getId().toString()))
            .body(result);
    }

    /**
     * GET  /point-counters : get all the pointCounters.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of pointCounters in body
     */
    @GetMapping("/point-counters")
    @Timed
    public List<PointCounter> getAllPointCounters() {
        log.debug("REST request to get all PointCounters");
        return pointCounterRepository.findAll();
    }

    /**
     * GET  /point-counters/:id : get the "id" pointCounter.
     *
     * @param id the id of the pointCounter to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the pointCounter, or with status 404 (Not Found)
     */
    @GetMapping("/point-counters/{id}")
    @Timed
    public ResponseEntity<PointCounter> getPointCounter(@PathVariable Long id) {
        log.debug("REST request to get PointCounter : {}", id);
        Optional<PointCounter> pointCounter = pointCounterRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(pointCounter);
    }

    /**
     * DELETE  /point-counters/:id : delete the "id" pointCounter.
     *
     * @param id the id of the pointCounter to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/point-counters/{id}")
    @Timed
    public ResponseEntity<Void> deletePointCounter(@PathVariable Long id) {
        log.debug("REST request to delete PointCounter : {}", id);

        pointCounterRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
