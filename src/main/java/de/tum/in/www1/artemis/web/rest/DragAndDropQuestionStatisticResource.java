package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic;
import de.tum.in.www1.artemis.repository.DragAndDropQuestionStatisticRepository;
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
 * REST controller for managing DragAndDropQuestionStatistic.
 */
@RestController
@RequestMapping("/api")
public class DragAndDropQuestionStatisticResource {

    private final Logger log = LoggerFactory.getLogger(DragAndDropQuestionStatisticResource.class);

    private static final String ENTITY_NAME = "dragAndDropQuestionStatistic";

    private DragAndDropQuestionStatisticRepository dragAndDropQuestionStatisticRepository;

    public DragAndDropQuestionStatisticResource(DragAndDropQuestionStatisticRepository dragAndDropQuestionStatisticRepository) {
        this.dragAndDropQuestionStatisticRepository = dragAndDropQuestionStatisticRepository;
    }

    /**
     * POST  /drag-and-drop-question-statistics : Create a new dragAndDropQuestionStatistic.
     *
     * @param dragAndDropQuestionStatistic the dragAndDropQuestionStatistic to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dragAndDropQuestionStatistic, or with status 400 (Bad Request) if the dragAndDropQuestionStatistic has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drag-and-drop-question-statistics")
    @Timed
    public ResponseEntity<DragAndDropQuestionStatistic> createDragAndDropQuestionStatistic(@RequestBody DragAndDropQuestionStatistic dragAndDropQuestionStatistic) throws URISyntaxException {
        log.debug("REST request to save DragAndDropQuestionStatistic : {}", dragAndDropQuestionStatistic);
        if (dragAndDropQuestionStatistic.getId() != null) {
            throw new BadRequestAlertException("A new dragAndDropQuestionStatistic cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DragAndDropQuestionStatistic result = dragAndDropQuestionStatisticRepository.save(dragAndDropQuestionStatistic);
        return ResponseEntity.created(new URI("/api/drag-and-drop-question-statistics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drag-and-drop-question-statistics : Updates an existing dragAndDropQuestionStatistic.
     *
     * @param dragAndDropQuestionStatistic the dragAndDropQuestionStatistic to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dragAndDropQuestionStatistic,
     * or with status 400 (Bad Request) if the dragAndDropQuestionStatistic is not valid,
     * or with status 500 (Internal Server Error) if the dragAndDropQuestionStatistic couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drag-and-drop-question-statistics")
    @Timed
    public ResponseEntity<DragAndDropQuestionStatistic> updateDragAndDropQuestionStatistic(@RequestBody DragAndDropQuestionStatistic dragAndDropQuestionStatistic) throws URISyntaxException {
        log.debug("REST request to update DragAndDropQuestionStatistic : {}", dragAndDropQuestionStatistic);
        if (dragAndDropQuestionStatistic.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        DragAndDropQuestionStatistic result = dragAndDropQuestionStatisticRepository.save(dragAndDropQuestionStatistic);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dragAndDropQuestionStatistic.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drag-and-drop-question-statistics : get all the dragAndDropQuestionStatistics.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dragAndDropQuestionStatistics in body
     */
    @GetMapping("/drag-and-drop-question-statistics")
    @Timed
    public List<DragAndDropQuestionStatistic> getAllDragAndDropQuestionStatistics() {
        log.debug("REST request to get all DragAndDropQuestionStatistics");
        return dragAndDropQuestionStatisticRepository.findAll();
    }

    /**
     * GET  /drag-and-drop-question-statistics/:id : get the "id" dragAndDropQuestionStatistic.
     *
     * @param id the id of the dragAndDropQuestionStatistic to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dragAndDropQuestionStatistic, or with status 404 (Not Found)
     */
    @GetMapping("/drag-and-drop-question-statistics/{id}")
    @Timed
    public ResponseEntity<DragAndDropQuestionStatistic> getDragAndDropQuestionStatistic(@PathVariable Long id) {
        log.debug("REST request to get DragAndDropQuestionStatistic : {}", id);
        Optional<DragAndDropQuestionStatistic> dragAndDropQuestionStatistic = dragAndDropQuestionStatisticRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(dragAndDropQuestionStatistic);
    }

    /**
     * DELETE  /drag-and-drop-question-statistics/:id : delete the "id" dragAndDropQuestionStatistic.
     *
     * @param id the id of the dragAndDropQuestionStatistic to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drag-and-drop-question-statistics/{id}")
    @Timed
    public ResponseEntity<Void> deleteDragAndDropQuestionStatistic(@PathVariable Long id) {
        log.debug("REST request to delete DragAndDropQuestionStatistic : {}", id);

        dragAndDropQuestionStatisticRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
