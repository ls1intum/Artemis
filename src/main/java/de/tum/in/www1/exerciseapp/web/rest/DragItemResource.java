package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.DragItem;
import de.tum.in.www1.exerciseapp.repository.DragItemRepository;
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
 * REST controller for managing DragItem.
 */
@RestController
@RequestMapping("/api")
public class DragItemResource {

    private final Logger log = LoggerFactory.getLogger(DragItemResource.class);

    private static final String ENTITY_NAME = "dragItem";

    private final DragItemRepository dragItemRepository;
    public DragItemResource(DragItemRepository dragItemRepository) {
        this.dragItemRepository = dragItemRepository;
    }

    /**
     * POST  /drag-items : Create a new dragItem.
     *
     * @param dragItem the dragItem to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dragItem, or with status 400 (Bad Request) if the dragItem has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drag-items")
    @Timed
    public ResponseEntity<DragItem> createDragItem(@RequestBody DragItem dragItem) throws URISyntaxException {
        log.debug("REST request to save DragItem : {}", dragItem);
        if (dragItem.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new dragItem cannot already have an ID")).body(null);
        }
        DragItem result = dragItemRepository.save(dragItem);
        return ResponseEntity.created(new URI("/api/drag-items/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drag-items : Updates an existing dragItem.
     *
     * @param dragItem the dragItem to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dragItem,
     * or with status 400 (Bad Request) if the dragItem is not valid,
     * or with status 500 (Internal Server Error) if the dragItem couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drag-items")
    @Timed
    public ResponseEntity<DragItem> updateDragItem(@RequestBody DragItem dragItem) throws URISyntaxException {
        log.debug("REST request to update DragItem : {}", dragItem);
        if (dragItem.getId() == null) {
            return createDragItem(dragItem);
        }
        DragItem result = dragItemRepository.save(dragItem);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dragItem.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drag-items : get all the dragItems.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dragItems in body
     */
    @GetMapping("/drag-items")
    @Timed
    public List<DragItem> getAllDragItems() {
        log.debug("REST request to get all DragItems");
        return dragItemRepository.findAll();
        }

    /**
     * GET  /drag-items/:id : get the "id" dragItem.
     *
     * @param id the id of the dragItem to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dragItem, or with status 404 (Not Found)
     */
    @GetMapping("/drag-items/{id}")
    @Timed
    public ResponseEntity<DragItem> getDragItem(@PathVariable Long id) {
        log.debug("REST request to get DragItem : {}", id);
        DragItem dragItem = dragItemRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(dragItem));
    }

    /**
     * DELETE  /drag-items/:id : delete the "id" dragItem.
     *
     * @param id the id of the dragItem to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drag-items/{id}")
    @Timed
    public ResponseEntity<Void> deleteDragItem(@PathVariable Long id) {
        log.debug("REST request to delete DragItem : {}", id);
        dragItemRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
