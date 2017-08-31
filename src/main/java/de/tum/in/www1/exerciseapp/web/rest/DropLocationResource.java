package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.DropLocation;

import de.tum.in.www1.exerciseapp.repository.DropLocationRepository;
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
 * REST controller for managing DropLocation.
 */
@RestController
@RequestMapping("/api")
public class DropLocationResource {

    private final Logger log = LoggerFactory.getLogger(DropLocationResource.class);

    private static final String ENTITY_NAME = "dropLocation";

    private final DropLocationRepository dropLocationRepository;
    public DropLocationResource(DropLocationRepository dropLocationRepository) {
        this.dropLocationRepository = dropLocationRepository;
    }

    /**
     * POST  /drop-locations : Create a new dropLocation.
     *
     * @param dropLocation the dropLocation to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dropLocation, or with status 400 (Bad Request) if the dropLocation has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drop-locations")
    @Timed
    public ResponseEntity<DropLocation> createDropLocation(@RequestBody DropLocation dropLocation) throws URISyntaxException {
        log.debug("REST request to save DropLocation : {}", dropLocation);
        if (dropLocation.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new dropLocation cannot already have an ID")).body(null);
        }
        DropLocation result = dropLocationRepository.save(dropLocation);
        return ResponseEntity.created(new URI("/api/drop-locations/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drop-locations : Updates an existing dropLocation.
     *
     * @param dropLocation the dropLocation to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dropLocation,
     * or with status 400 (Bad Request) if the dropLocation is not valid,
     * or with status 500 (Internal Server Error) if the dropLocation couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drop-locations")
    @Timed
    public ResponseEntity<DropLocation> updateDropLocation(@RequestBody DropLocation dropLocation) throws URISyntaxException {
        log.debug("REST request to update DropLocation : {}", dropLocation);
        if (dropLocation.getId() == null) {
            return createDropLocation(dropLocation);
        }
        DropLocation result = dropLocationRepository.save(dropLocation);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dropLocation.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drop-locations : get all the dropLocations.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dropLocations in body
     */
    @GetMapping("/drop-locations")
    @Timed
    public List<DropLocation> getAllDropLocations() {
        log.debug("REST request to get all DropLocations");
        return dropLocationRepository.findAll();
        }

    /**
     * GET  /drop-locations/:id : get the "id" dropLocation.
     *
     * @param id the id of the dropLocation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dropLocation, or with status 404 (Not Found)
     */
    @GetMapping("/drop-locations/{id}")
    @Timed
    public ResponseEntity<DropLocation> getDropLocation(@PathVariable Long id) {
        log.debug("REST request to get DropLocation : {}", id);
        DropLocation dropLocation = dropLocationRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(dropLocation));
    }

    /**
     * DELETE  /drop-locations/:id : delete the "id" dropLocation.
     *
     * @param id the id of the dropLocation to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drop-locations/{id}")
    @Timed
    public ResponseEntity<Void> deleteDropLocation(@PathVariable Long id) {
        log.debug("REST request to delete DropLocation : {}", id);
        dropLocationRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
