package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.service.metis.AnswerMessageService;

@RestController
@RequestMapping("/api")
public class AnswerMessageResource {

    private final AnswerMessageService answerMessageService;

    public AnswerMessageResource(AnswerMessageService answerMessageService) {
        this.answerMessageService = answerMessageService;
    }

    /**
     * POST /courses/{courseId}/answer-messages : Create a new answer message
     *
     * @param courseId   id of the course the post belongs to
     * @param answerMessage answer post to create
     * @return ResponseEntity with status 201 (Created) containing the created answer message in the response body,
     * or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PostMapping("courses/{courseId}/answer-messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AnswerPost> createAnswerPost(@PathVariable Long courseId, @RequestBody AnswerPost answerMessage) throws URISyntaxException {
        AnswerPost createdAnswerMessage = answerMessageService.createAnswerMessage(courseId, answerMessage);

        // creation of answerMessage should not trigger alert
        return ResponseEntity.created(new URI("/api/courses" + courseId + "/answer-messages/" + createdAnswerMessage.getId())).body(createdAnswerMessage);
    }

    /**
     * PUT /courses/{courseId}/answer-messages/{answerPostId} : Update an existing answer message with given id
     *
     * @param courseId      id of the course the answer post belongs to
     * @param answerMessageId  id of the answer post to update
     * @param answerMessage    answer post to update
     * @return ResponseEntity with status 200 (OK) containing the updated answer message in the response body,
     * or with status 400 (Bad Request) if the checks on user, course or associated post validity fail
     */
    @PutMapping("courses/{courseId}/answer-messages/{answerMessageId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AnswerPost> updateAnswerPost(@PathVariable Long courseId, @PathVariable Long answerMessageId, @RequestBody AnswerPost answerMessage) {
        AnswerPost updatedAnswerMessage = answerMessageService.updateAnswerMessage(courseId, answerMessageId, answerMessage);
        return new ResponseEntity<>(updatedAnswerMessage, null, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/answer-messages/{id} : Delete an answer message by its id
     *
     * @param courseId          id of the course the message belongs to
     * @param answerMessageId   id of the answer message to delete
     * @return ResponseEntity with status 200 (OK),
     * or 400 (Bad Request) if the checks on user or course validity fail
     */
    @DeleteMapping("courses/{courseId}/answer-messages/{answerMessageId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteAnswerPost(@PathVariable Long courseId, @PathVariable Long answerMessageId) {
        answerMessageService.deleteAnswerMessageById(courseId, answerMessageId);

        // deletion of answerMessages should not trigger alert
        return ResponseEntity.ok().build();
    }
}
