package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Feedback;
import de.tum.in.www1.exerciseapp.repository.FeedbackRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.service.FeedbackService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * REST controller for managing Feedback.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class FeedbackResource {

    private final Logger log = LoggerFactory.getLogger(FeedbackResource.class);
    private static final String ENTITY_NAME = "feedback";

    private final FeedbackRepository feedbackRepository;
    private final ResultRepository resultRepository;
    private final FeedbackService feedbackService;

    public FeedbackResource(FeedbackRepository feedbackRepository, FeedbackService feedbackService, ResultRepository resultRepository) {
        this.feedbackRepository = feedbackRepository;
        this.resultRepository = resultRepository;
        this.feedbackService = feedbackService;
    }


    /**
     * POST  /feedbacks : Create a new feedback.
     *
     * @param feedback the feedback to create
     * @return the ResponseEntity with status 201 (Created) and with body the new feedback, or with status 400 (Bad Request) if the feedback has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/feedbacks")
    @Timed
    public ResponseEntity<Feedback> createFeedback(@RequestBody Feedback feedback) throws URISyntaxException {
        log.debug("REST request to save Feedback : {}", feedback);
        if (feedback.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new feedback cannot already have an ID")).body(null);
        }
        Feedback savedFeedback = feedbackService.save(feedback);
        return ResponseEntity.created(new URI("/api/feedbacks/" + savedFeedback.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, savedFeedback.getId().toString()))
            .body(savedFeedback);
    }

    /**
     * PUT  /feedbacks : Updates an existing feedback.
     *
     * @param feedback the feedback to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated feedback,
     * or with status 400 (Bad Request) if the feedback is not valid,
     * or with status 500 (Internal Server Error) if the feedback couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/feedbacks")
    @Timed
    public ResponseEntity<Feedback> updateFeedback(@RequestBody Feedback feedback) throws URISyntaxException {
        log.debug("REST request to update Feedback : {}", feedback);
        if (feedback.getId() == null) {
            return createFeedback(feedback);
        }
        Feedback result = feedbackRepository.save(feedback);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, feedback.getId().toString()))
            .body(result);
    }

    //Deactivated because it would load all (thousands) feedback objects and completely overload the server
    //TODO: activate this call again using the infinite scroll page mechanism
//    /**
//     * GET  /feedbacks : get all the feedbacks.
//     *
//     * @return the ResponseEntity with status 200 (OK) and the list of feedbacks in body
//     */
//    @GetMapping("/feedbacks")
//    @Timed
//    public List<Feedback> getAllFeedbacks() {
//        log.debug("REST request to get all Feedbacks");
//        return feedbackRepository.findAll();
//    }

    /**
     * GET  /feedbacks/:id : get the "id" feedback.
     *
     * @param id the id of the feedback to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the feedback, or with status 404 (Not Found)
     */
    @GetMapping("/feedbacks/{id}")
    @Timed
    public ResponseEntity<Feedback> getFeedback(@PathVariable Long id) {
        log.debug("REST request to get Feedback : {}", id);
        Feedback feedback = feedbackRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(feedback));
    }

    /**
     * DELETE  /feedbacks/:id : delete the "id" feedback.
     *
     * @param id the id of the feedback to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/feedbacks/{id}")
    @Timed
    public ResponseEntity<Void> deleteFeedback(@PathVariable Long id) {
        log.debug("REST request to delete Feedback : {}", id);
        feedbackRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
