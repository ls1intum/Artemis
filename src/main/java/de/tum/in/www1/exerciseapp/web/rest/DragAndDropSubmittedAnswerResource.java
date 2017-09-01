package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.DragAndDropSubmittedAnswer;
import de.tum.in.www1.exerciseapp.repository.DragAndDropSubmittedAnswerRepository;
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
 * REST controller for managing DragAndDropSubmittedAnswer.
 */
@RestController
@RequestMapping("/api")
public class DragAndDropSubmittedAnswerResource {

    private final Logger log = LoggerFactory.getLogger(DragAndDropSubmittedAnswerResource.class);

    private static final String ENTITY_NAME = "dragAndDropSubmittedAnswer";

    private final DragAndDropSubmittedAnswerRepository dragAndDropSubmittedAnswerRepository;
    public DragAndDropSubmittedAnswerResource(DragAndDropSubmittedAnswerRepository dragAndDropSubmittedAnswerRepository) {
        this.dragAndDropSubmittedAnswerRepository = dragAndDropSubmittedAnswerRepository;
    }

    /**
     * POST  /drag-and-drop-submitted-answers : Create a new dragAndDropSubmittedAnswer.
     *
     * @param dragAndDropSubmittedAnswer the dragAndDropSubmittedAnswer to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dragAndDropSubmittedAnswer, or with status 400 (Bad Request) if the dragAndDropSubmittedAnswer has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drag-and-drop-submitted-answers")
    @Timed
    public ResponseEntity<DragAndDropSubmittedAnswer> createDragAndDropSubmittedAnswer(@RequestBody DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) throws URISyntaxException {
        log.debug("REST request to save DragAndDropSubmittedAnswer : {}", dragAndDropSubmittedAnswer);
        if (dragAndDropSubmittedAnswer.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new dragAndDropSubmittedAnswer cannot already have an ID")).body(null);
        }
        DragAndDropSubmittedAnswer result = dragAndDropSubmittedAnswerRepository.save(dragAndDropSubmittedAnswer);
        return ResponseEntity.created(new URI("/api/drag-and-drop-submitted-answers/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drag-and-drop-submitted-answers : Updates an existing dragAndDropSubmittedAnswer.
     *
     * @param dragAndDropSubmittedAnswer the dragAndDropSubmittedAnswer to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dragAndDropSubmittedAnswer,
     * or with status 400 (Bad Request) if the dragAndDropSubmittedAnswer is not valid,
     * or with status 500 (Internal Server Error) if the dragAndDropSubmittedAnswer couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drag-and-drop-submitted-answers")
    @Timed
    public ResponseEntity<DragAndDropSubmittedAnswer> updateDragAndDropSubmittedAnswer(@RequestBody DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) throws URISyntaxException {
        log.debug("REST request to update DragAndDropSubmittedAnswer : {}", dragAndDropSubmittedAnswer);
        if (dragAndDropSubmittedAnswer.getId() == null) {
            return createDragAndDropSubmittedAnswer(dragAndDropSubmittedAnswer);
        }
        DragAndDropSubmittedAnswer result = dragAndDropSubmittedAnswerRepository.save(dragAndDropSubmittedAnswer);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dragAndDropSubmittedAnswer.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drag-and-drop-submitted-answers : get all the dragAndDropSubmittedAnswers.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dragAndDropSubmittedAnswers in body
     */
    @GetMapping("/drag-and-drop-submitted-answers")
    @Timed
    public List<DragAndDropSubmittedAnswer> getAllDragAndDropSubmittedAnswers() {
        log.debug("REST request to get all DragAndDropSubmittedAnswers");
        return dragAndDropSubmittedAnswerRepository.findAll();
        }

    /**
     * GET  /drag-and-drop-submitted-answers/:id : get the "id" dragAndDropSubmittedAnswer.
     *
     * @param id the id of the dragAndDropSubmittedAnswer to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dragAndDropSubmittedAnswer, or with status 404 (Not Found)
     */
    @GetMapping("/drag-and-drop-submitted-answers/{id}")
    @Timed
    public ResponseEntity<DragAndDropSubmittedAnswer> getDragAndDropSubmittedAnswer(@PathVariable Long id) {
        log.debug("REST request to get DragAndDropSubmittedAnswer : {}", id);
        DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer = dragAndDropSubmittedAnswerRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(dragAndDropSubmittedAnswer));
    }

    /**
     * DELETE  /drag-and-drop-submitted-answers/:id : delete the "id" dragAndDropSubmittedAnswer.
     *
     * @param id the id of the dragAndDropSubmittedAnswer to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drag-and-drop-submitted-answers/{id}")
    @Timed
    public ResponseEntity<Void> deleteDragAndDropSubmittedAnswer(@PathVariable Long id) {
        log.debug("REST request to delete DragAndDropSubmittedAnswer : {}", id);
        dragAndDropSubmittedAnswerRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
