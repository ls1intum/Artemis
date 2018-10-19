package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.DropLocationCounter;
import de.tum.in.www1.artemis.repository.DropLocationCounterRepository;
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
 * REST controller for managing DropLocationCounter.
 */
@RestController
@RequestMapping("/api")
public class DropLocationCounterResource {

    private final Logger log = LoggerFactory.getLogger(DropLocationCounterResource.class);

    private static final String ENTITY_NAME = "dropLocationCounter";

    private DropLocationCounterRepository dropLocationCounterRepository;

    public DropLocationCounterResource(DropLocationCounterRepository dropLocationCounterRepository) {
        this.dropLocationCounterRepository = dropLocationCounterRepository;
    }

    /**
     * POST  /drop-location-counters : Create a new dropLocationCounter.
     *
     * @param dropLocationCounter the dropLocationCounter to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dropLocationCounter, or with status 400 (Bad Request) if the dropLocationCounter has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drop-location-counters")
    @Timed
    public ResponseEntity<DropLocationCounter> createDropLocationCounter(@RequestBody DropLocationCounter dropLocationCounter) throws URISyntaxException {
        log.debug("REST request to save DropLocationCounter : {}", dropLocationCounter);
        if (dropLocationCounter.getId() != null) {
            throw new BadRequestAlertException("A new dropLocationCounter cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DropLocationCounter result = dropLocationCounterRepository.save(dropLocationCounter);
        return ResponseEntity.created(new URI("/api/drop-location-counters/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drop-location-counters : Updates an existing dropLocationCounter.
     *
     * @param dropLocationCounter the dropLocationCounter to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dropLocationCounter,
     * or with status 400 (Bad Request) if the dropLocationCounter is not valid,
     * or with status 500 (Internal Server Error) if the dropLocationCounter couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drop-location-counters")
    @Timed
    public ResponseEntity<DropLocationCounter> updateDropLocationCounter(@RequestBody DropLocationCounter dropLocationCounter) throws URISyntaxException {
        log.debug("REST request to update DropLocationCounter : {}", dropLocationCounter);
        if (dropLocationCounter.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        DropLocationCounter result = dropLocationCounterRepository.save(dropLocationCounter);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dropLocationCounter.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drop-location-counters : get all the dropLocationCounters.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dropLocationCounters in body
     */
    @GetMapping("/drop-location-counters")
    @Timed
    public List<DropLocationCounter> getAllDropLocationCounters() {
        log.debug("REST request to get all DropLocationCounters");
        return dropLocationCounterRepository.findAll();
    }

    /**
     * GET  /drop-location-counters/:id : get the "id" dropLocationCounter.
     *
     * @param id the id of the dropLocationCounter to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dropLocationCounter, or with status 404 (Not Found)
     */
    @GetMapping("/drop-location-counters/{id}")
    @Timed
    public ResponseEntity<DropLocationCounter> getDropLocationCounter(@PathVariable Long id) {
        log.debug("REST request to get DropLocationCounter : {}", id);
        Optional<DropLocationCounter> dropLocationCounter = dropLocationCounterRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(dropLocationCounter);
    }

    /**
     * DELETE  /drop-location-counters/:id : delete the "id" dropLocationCounter.
     *
     * @param id the id of the dropLocationCounter to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drop-location-counters/{id}")
    @Timed
    public ResponseEntity<Void> deleteDropLocationCounter(@PathVariable Long id) {
        log.debug("REST request to delete DropLocationCounter : {}", id);

        dropLocationCounterRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
