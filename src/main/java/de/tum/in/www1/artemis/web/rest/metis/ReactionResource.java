package de.tum.in.www1.artemis.web.rest.metis;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.net.URI;
import java.net.URISyntaxException;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for Reaction on Postings.
 */
@RestController
@RequestMapping("/api")
public class ReactionResource {

    private static final String ENTITY_NAME = "metis.reaction";

    private final Logger log = LoggerFactory.getLogger(ReactionResource.class);

    private final ReactionRepository reactionRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    public ReactionResource(ReactionRepository reactionRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository) {
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /courses/{courseId}/posts/{id}/reactions : Create a reaction on a posting.
     *
     * @param courseId course the posting that is reacted on belongs to
     * @param reaction the reaction to create
     * @return the ResponseEntity with status 201 (Created) and with body the new reaction, or with status 400 (Bad Request) if the reaction is associated with non-existing postings
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/postings/reactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Reaction> createReaction(@PathVariable Long courseId, @Valid @RequestBody Reaction reaction) throws URISyntaxException {
        Posting posting = reaction.getPost() == null ? reaction.getAnswerPost() : reaction.getPost();

        if (!posting.getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesn't match the courseId of the sent Post in Body");
        }
        log.debug("REST request to save Reaction : {}", reaction);
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (reaction.getId() != null) {
            throw new BadRequestAlertException("A new reaction cannot already have an ID", ENTITY_NAME, "idexists");
        }
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // set user to current user
        reaction.setUser(user);

        // we query the repository dependent on the type of posting
        Reaction savedReaction;
        if (posting instanceof Post) {
            Post post = postRepository.findByIdElseThrow(posting.getId());
            reaction.setPost(post);
            savedReaction = reactionRepository.save(reaction);
            post.addReaction(reaction);
            postRepository.save(post);

            // Protect Sample Solution, Grading Instructions, etc.
            post.getExercise().filterSensitiveInformation();
        }
        else {
            AnswerPost answerPost = answerPostRepository.findByIdElseThrow(posting.getId());
            reaction.setAnswerPost(answerPost);
            savedReaction = reactionRepository.save(reaction);
            answerPost.addReaction(reaction);
            answerPostRepository.save(answerPost);

            // Protect Sample Solution, Grading Instructions, etc.
            answerPost.getPost().getExercise().filterSensitiveInformation();
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/postings/reactions/" + savedReaction.getId())).body(savedReaction);
    }

    /**
     * DELETE /courses/{courseId}/posts/{id}/reactions/{reactionId} : delete the reaction with {id}.
     *
     * @param courseId   course the posting that is reacted on belongs to
     * @param reactionId the id of the reaction to delete
     * @return the ResponseEntity with status 200 (OK) or 400 (Bad Request) if the data is inconsistent
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @DeleteMapping("courses/{courseId}/postings/reactions/{reactionId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteReaction(@PathVariable Long courseId, @PathVariable Long reactionId) throws URISyntaxException {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        courseRepository.findByIdElseThrow(courseId);
        Reaction reaction = reactionRepository.findByIdElseThrow(reactionId);

        // check if user that wants to delete reaction is user that created the reaction
        if (!user.equals(reaction.getUser())) {
            throw new AccessForbiddenException("Reaction", reaction.getId());
        }

        String entity = "";
        if (reaction.getPost() != null) {
            entity = "post with id: " + reaction.getPost().getId();
        }
        else if (reaction.getAnswerPost() != null) {
            entity = "answer post with id: " + reaction.getAnswerPost().getId();
        }
        log.debug("Reaction deleted by " + user.getLogin() + ". Reaction: " + reaction.getEmojiId() + " for " + entity);
        reactionRepository.deleteById(reactionId);

        return ResponseEntity.ok().build();
    }
}
