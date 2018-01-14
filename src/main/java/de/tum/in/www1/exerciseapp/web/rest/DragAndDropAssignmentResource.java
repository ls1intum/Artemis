package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.DragAndDropAssignment;

import de.tum.in.www1.exerciseapp.repository.DragAndDropAssignmentRepository;
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
 * REST controller for managing DragAndDropAssignment.
 */
@RestController
@RequestMapping("/api")
public class DragAndDropAssignmentResource {

    private final Logger log = LoggerFactory.getLogger(DragAndDropAssignmentResource.class);

    private static final String ENTITY_NAME = "dragAndDropAssignment";

    private final DragAndDropAssignmentRepository dragAndDropAssignmentRepository;

    public DragAndDropAssignmentResource(DragAndDropAssignmentRepository dragAndDropAssignmentRepository) {
        this.dragAndDropAssignmentRepository = dragAndDropAssignmentRepository;
    }

    /**
     * POST  /drag-and-drop-assignments : Create a new dragAndDropAssignment.
     *
     * @param dragAndDropAssignment the dragAndDropAssignment to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dragAndDropAssignment, or with status 400 (Bad Request) if the dragAndDropAssignment has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drag-and-drop-assignments")
    @Timed
    public ResponseEntity<DragAndDropAssignment> createDragAndDropAssignment(@RequestBody DragAndDropAssignment dragAndDropAssignment) throws URISyntaxException {
        log.debug("REST request to save DragAndDropAssignment : {}", dragAndDropAssignment);
        if (dragAndDropAssignment.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new dragAndDropAssignment cannot already have an ID")).body(null);
        }
        DragAndDropAssignment result = dragAndDropAssignmentRepository.save(dragAndDropAssignment);
        return ResponseEntity.created(new URI("/api/drag-and-drop-assignments/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drag-and-drop-assignments : Updates an existing dragAndDropAssignment.
     *
     * @param dragAndDropAssignment the dragAndDropAssignment to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dragAndDropAssignment,
     * or with status 400 (Bad Request) if the dragAndDropAssignment is not valid,
     * or with status 500 (Internal Server Error) if the dragAndDropAssignment couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drag-and-drop-assignments")
    @Timed
    public ResponseEntity<DragAndDropAssignment> updateDragAndDropAssignment(@RequestBody DragAndDropAssignment dragAndDropAssignment) throws URISyntaxException {
        log.debug("REST request to update DragAndDropAssignment : {}", dragAndDropAssignment);
        if (dragAndDropAssignment.getId() == null) {
            return createDragAndDropAssignment(dragAndDropAssignment);
        }
        DragAndDropAssignment result = dragAndDropAssignmentRepository.save(dragAndDropAssignment);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dragAndDropAssignment.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drag-and-drop-assignments : get all the dragAndDropAssignments.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dragAndDropAssignments in body
     */
    @GetMapping("/drag-and-drop-assignments")
    @Timed
    public List<DragAndDropAssignment> getAllDragAndDropAssignments() {
        log.debug("REST request to get all DragAndDropAssignments");
        return dragAndDropAssignmentRepository.findAll();
    }

    /**
     * GET  /drag-and-drop-assignments/:id : get the "id" dragAndDropAssignment.
     *
     * @param id the id of the dragAndDropAssignment to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dragAndDropAssignment, or with status 404 (Not Found)
     */
    @GetMapping("/drag-and-drop-assignments/{id}")
    @Timed
    public ResponseEntity<DragAndDropAssignment> getDragAndDropAssignment(@PathVariable Long id) {
        log.debug("REST request to get DragAndDropAssignment : {}", id);
        DragAndDropAssignment dragAndDropAssignment = dragAndDropAssignmentRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(dragAndDropAssignment));
    }

    /**
     * DELETE  /drag-and-drop-assignments/:id : delete the "id" dragAndDropAssignment.
     *
     * @param id the id of the dragAndDropAssignment to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drag-and-drop-assignments/{id}")
    @Timed
    public ResponseEntity<Void> deleteDragAndDropAssignment(@PathVariable Long id) {
        log.debug("REST request to delete DragAndDropAssignment : {}", id);
        dragAndDropAssignmentRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
