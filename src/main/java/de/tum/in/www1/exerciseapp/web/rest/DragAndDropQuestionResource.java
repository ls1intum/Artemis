package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.DragAndDropQuestion;

import de.tum.in.www1.exerciseapp.repository.DragAndDropQuestionRepository;
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
 * REST controller for managing DragAndDropQuestion.
 */
@RestController
@RequestMapping("/api")
public class DragAndDropQuestionResource {

    private final Logger log = LoggerFactory.getLogger(DragAndDropQuestionResource.class);

    private static final String ENTITY_NAME = "dragAndDropQuestion";

    private final DragAndDropQuestionRepository dragAndDropQuestionRepository;

    public DragAndDropQuestionResource(DragAndDropQuestionRepository dragAndDropQuestionRepository) {
        this.dragAndDropQuestionRepository = dragAndDropQuestionRepository;
    }

    /**
     * POST  /drag-and-drop-questions : Create a new dragAndDropQuestion.
     *
     * @param dragAndDropQuestion the dragAndDropQuestion to create
     * @return the ResponseEntity with status 201 (Created) and with body the new dragAndDropQuestion, or with status 400 (Bad Request) if the dragAndDropQuestion has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/drag-and-drop-questions")
    @Timed
    public ResponseEntity<DragAndDropQuestion> createDragAndDropQuestion(@RequestBody DragAndDropQuestion dragAndDropQuestion) throws URISyntaxException {
        log.debug("REST request to save DragAndDropQuestion : {}", dragAndDropQuestion);
        if (dragAndDropQuestion.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new dragAndDropQuestion cannot already have an ID")).body(null);
        }
        DragAndDropQuestion result = dragAndDropQuestionRepository.save(dragAndDropQuestion);
        return ResponseEntity.created(new URI("/api/drag-and-drop-questions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /drag-and-drop-questions : Updates an existing dragAndDropQuestion.
     *
     * @param dragAndDropQuestion the dragAndDropQuestion to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated dragAndDropQuestion,
     * or with status 400 (Bad Request) if the dragAndDropQuestion is not valid,
     * or with status 500 (Internal Server Error) if the dragAndDropQuestion couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/drag-and-drop-questions")
    @Timed
    public ResponseEntity<DragAndDropQuestion> updateDragAndDropQuestion(@RequestBody DragAndDropQuestion dragAndDropQuestion) throws URISyntaxException {
        log.debug("REST request to update DragAndDropQuestion : {}", dragAndDropQuestion);
        if (dragAndDropQuestion.getId() == null) {
            return createDragAndDropQuestion(dragAndDropQuestion);
        }
        DragAndDropQuestion result = dragAndDropQuestionRepository.save(dragAndDropQuestion);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, dragAndDropQuestion.getId().toString()))
            .body(result);
    }

    /**
     * GET  /drag-and-drop-questions : get all the dragAndDropQuestions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of dragAndDropQuestions in body
     */
    @GetMapping("/drag-and-drop-questions")
    @Timed
    public List<DragAndDropQuestion> getAllDragAndDropQuestions() {
        log.debug("REST request to get all DragAndDropQuestions");
        return dragAndDropQuestionRepository.findAll();
    }

    /**
     * GET  /drag-and-drop-questions/:id : get the "id" dragAndDropQuestion.
     *
     * @param id the id of the dragAndDropQuestion to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the dragAndDropQuestion, or with status 404 (Not Found)
     */
    @GetMapping("/drag-and-drop-questions/{id}")
    @Timed
    public ResponseEntity<DragAndDropQuestion> getDragAndDropQuestion(@PathVariable Long id) {
        log.debug("REST request to get DragAndDropQuestion : {}", id);
        DragAndDropQuestion dragAndDropQuestion = dragAndDropQuestionRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(dragAndDropQuestion));
    }

    /**
     * DELETE  /drag-and-drop-questions/:id : delete the "id" dragAndDropQuestion.
     *
     * @param id the id of the dragAndDropQuestion to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/drag-and-drop-questions/{id}")
    @Timed
    public ResponseEntity<Void> deleteDragAndDropQuestion(@PathVariable Long id) {
        log.debug("REST request to delete DragAndDropQuestion : {}", id);
        dragAndDropQuestionRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
