package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.DragAndDropMapping;

import de.tum.in.www1.exerciseapp.repository.DragAndDropMappingRepository;
import de.tum.in.www1.exerciseapp.web.rest.errors.BadRequestAlertException;
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
 * REST controller for managing DragAndDropMapping.
 */
@RestController
@RequestMapping("/api")
public class DragAndDropMappingResource {

    private final Logger log = LoggerFactory.getLogger(DragAndDropMappingResource.class);

    private static final String ENTITY_NAME = "dragAndDropMapping";

    private final DragAndDropMappingRepository dragAndDropMappingRepository;

    public DragAndDropMappingResource(DragAndDropMappingRepository dragAndDropMappingRepository) {
        this.dragAndDropMappingRepository = dragAndDropMappingRepository;
    }

    /**
     * POST  /drag-and-drop-mappings : Create a new dragAndDropMapping.
     *
     * @param dragAndDropMapping the dragAndDropMapping to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dragAndDropMapping, or with status 400 (Bad Request) if the dragAndDropMapping has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drag-and-drop-mappings")
    @Timed
    public ResponseEntity<DragAndDropMapping> createDragAndDropMapping(@RequestBody DragAndDropMapping dragAndDropMapping) throws URISyntaxException {
        log.debug("REST request to save DragAndDropMapping : {}", dragAndDropMapping);
        if (dragAndDropMapping.getId() != null) {
            throw new BadRequestAlertException("A new dragAndDropMapping cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DragAndDropMapping result = dragAndDropMappingRepository.save(dragAndDropMapping);
        return ResponseEntity.created(new URI("/api/drag-and-drop-mappings/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drag-and-drop-mappings : Updates an existing dragAndDropMapping.
     *
     * @param dragAndDropMapping the dragAndDropMapping to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dragAndDropMapping,
     * or with status 400 (Bad Request) if the dragAndDropMapping is not valid,
     * or with status 500 (Internal Server Error) if the dragAndDropMapping couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drag-and-drop-mappings")
    @Timed
    public ResponseEntity<DragAndDropMapping> updateDragAndDropMapping(@RequestBody DragAndDropMapping dragAndDropMapping) throws URISyntaxException {
        log.debug("REST request to update DragAndDropMapping : {}", dragAndDropMapping);
        if (dragAndDropMapping.getId() == null) {
            return createDragAndDropMapping(dragAndDropMapping);
        }
        DragAndDropMapping result = dragAndDropMappingRepository.save(dragAndDropMapping);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dragAndDropMapping.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drag-and-drop-mappings : get all the dragAndDropMappings.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dragAndDropMappings in body
     */
    @GetMapping("/drag-and-drop-mappings")
    @Timed
    public List<DragAndDropMapping> getAllDragAndDropMappings() {
        log.debug("REST request to get all DragAndDropMappings");
        return dragAndDropMappingRepository.findAll();
        }

    /**
     * GET  /drag-and-drop-mappings/:id : get the "id" dragAndDropMapping.
     *
     * @param id the id of the dragAndDropMapping to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dragAndDropMapping, or with status 404 (Not Found)
     */
    @GetMapping("/drag-and-drop-mappings/{id}")
    @Timed
    public ResponseEntity<DragAndDropMapping> getDragAndDropMapping(@PathVariable Long id) {
        log.debug("REST request to get DragAndDropMapping : {}", id);
        DragAndDropMapping dragAndDropMapping = dragAndDropMappingRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(dragAndDropMapping));
    }

    /**
     * DELETE  /drag-and-drop-mappings/:id : delete the "id" dragAndDropMapping.
     *
     * @param id the id of the dragAndDropMapping to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drag-and-drop-mappings/{id}")
    @Timed
    public ResponseEntity<Void> deleteDragAndDropMapping(@PathVariable Long id) {
        log.debug("REST request to delete DragAndDropMapping : {}", id);
        dragAndDropMappingRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
