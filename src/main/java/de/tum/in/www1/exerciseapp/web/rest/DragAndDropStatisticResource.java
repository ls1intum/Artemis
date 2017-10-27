package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.DragAndDropStatistic;

import de.tum.in.www1.exerciseapp.repository.DragAndDropStatisticRepository;
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
 * REST controller for managing DragAndDropStatistic.
 */
@RestController
@RequestMapping("/api")
public class DragAndDropStatisticResource {

    private final Logger log = LoggerFactory.getLogger(DragAndDropStatisticResource.class);

    private static final String ENTITY_NAME = "dragAndDropStatistic";

    private final DragAndDropStatisticRepository dragAndDropStatisticRepository;

    public DragAndDropStatisticResource(DragAndDropStatisticRepository dragAndDropStatisticRepository) {
        this.dragAndDropStatisticRepository = dragAndDropStatisticRepository;
    }

    /**
     * POST  /drag-and-drop-statistics : Create a new dragAndDropStatistic.
     *
     * @param dragAndDropStatistic the dragAndDropStatistic to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dragAndDropStatistic, or with status 400 (Bad Request) if the dragAndDropStatistic has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drag-and-drop-statistics")
    @Timed
    public ResponseEntity<DragAndDropStatistic> createDragAndDropStatistic(@RequestBody DragAndDropStatistic dragAndDropStatistic) throws URISyntaxException {
        log.debug("REST request to save DragAndDropStatistic : {}", dragAndDropStatistic);
        if (dragAndDropStatistic.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new dragAndDropStatistic cannot already have an ID")).body(null);
        }
        DragAndDropStatistic result = dragAndDropStatisticRepository.save(dragAndDropStatistic);
        return ResponseEntity.created(new URI("/api/drag-and-drop-statistics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drag-and-drop-statistics : Updates an existing dragAndDropStatistic.
     *
     * @param dragAndDropStatistic the dragAndDropStatistic to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dragAndDropStatistic,
     * or with status 400 (Bad Request) if the dragAndDropStatistic is not valid,
     * or with status 500 (Internal Server Error) if the dragAndDropStatistic couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drag-and-drop-statistics")
    @Timed
    public ResponseEntity<DragAndDropStatistic> updateDragAndDropStatistic(@RequestBody DragAndDropStatistic dragAndDropStatistic) throws URISyntaxException {
        log.debug("REST request to update DragAndDropStatistic : {}", dragAndDropStatistic);
        if (dragAndDropStatistic.getId() == null) {
            return createDragAndDropStatistic(dragAndDropStatistic);
        }
        DragAndDropStatistic result = dragAndDropStatisticRepository.save(dragAndDropStatistic);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dragAndDropStatistic.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drag-and-drop-statistics : get all the dragAndDropStatistics.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dragAndDropStatistics in body
     */
    @GetMapping("/drag-and-drop-statistics")
    @Timed
    public List<DragAndDropStatistic> getAllDragAndDropStatistics() {
        log.debug("REST request to get all DragAndDropStatistics");
        return dragAndDropStatisticRepository.findAll();
    }

    /**
     * GET  /drag-and-drop-statistics/:id : get the "id" dragAndDropStatistic.
     *
     * @param id the id of the dragAndDropStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dragAndDropStatistic, or with status 404 (Not Found)
     */
    @GetMapping("/drag-and-drop-statistics/{id}")
    @Timed
    public ResponseEntity<DragAndDropStatistic> getDragAndDropStatistic(@PathVariable Long id) {
        log.debug("REST request to get DragAndDropStatistic : {}", id);
        DragAndDropStatistic dragAndDropStatistic = dragAndDropStatisticRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(dragAndDropStatistic));
    }

    /**
     * DELETE  /drag-and-drop-statistics/:id : delete the "id" dragAndDropStatistic.
     *
     * @param id the id of the dragAndDropStatistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drag-and-drop-statistics/{id}")
    @Timed
    public ResponseEntity<Void> deleteDragAndDropStatistic(@PathVariable Long id) {
        log.debug("REST request to delete DragAndDropStatistic : {}", id);
        dragAndDropStatisticRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
