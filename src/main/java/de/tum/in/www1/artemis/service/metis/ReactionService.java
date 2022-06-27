package de.tum.in.www1.artemis.service.metis;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.MetisPostAction;
import de.tum.in.www1.artemis.web.websocket.dto.MetisPostDTO;

@Service
public class ReactionService {

    private static final String METIS_REACTION_ENTITY_NAME = "posting reaction";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ReactionRepository reactionRepository;

    private final PostService postService;

    private final AnswerPostService answerPostService;

    public ReactionService(UserRepository userRepository, CourseRepository courseRepository, ReactionRepository reactionRepository, PostService postService,
            AnswerPostService answerPostService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.reactionRepository = reactionRepository;
        this.postService = postService;
        this.answerPostService = answerPostService;
    }

    /**
     * Checks reaction validity, determines the reaction's user,
     * retrieves the associated posting and persists the mutual association
     *
     * @param courseId if of course the according posting belongs to
     * @param reaction reaction to create
     * @return created reaction that was persisted
     */
    public Reaction createReaction(Long courseId, Reaction reaction) {
        Posting posting = reaction.getPost() == null ? reaction.getAnswerPost() : reaction.getPost();

        // checks
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (reaction.getId() != null) {
            throw new BadRequestAlertException("A new reaction cannot already have an ID", METIS_REACTION_ENTITY_NAME, "idExists");
        }

        // set user to current user
        reaction.setUser(user);

        // we query the repository dependent on the type of posting and update this posting
        Reaction savedReaction;
        if (posting instanceof Post) {
            Post post = postService.findById(posting.getId());
            reaction.setPost(post);
            // save reaction
            savedReaction = reactionRepository.save(reaction);
            // save post
            postService.updateWithReaction(post, reaction, courseId);
        }
        else {
            AnswerPost answerPost = answerPostService.findById(posting.getId());
            reaction.setAnswerPost(answerPost);
            // save reaction
            savedReaction = reactionRepository.save(reaction);
            // save answer post
            answerPostService.updateWithReaction(answerPost, reaction, courseId);
        }
        return savedReaction;
    }

    /**
     * Determines authority to delete reaction and deletes the reaction
     *
     * @param reactionId    id of the reaction to delete
     * @param courseId      id of the course the according posting belongs to
     */
    public void deleteReactionById(Long reactionId, Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);
        Reaction reaction = reactionRepository.findByIdElseThrow(reactionId);

        // check if user that wants to delete reaction is user that created the reaction
        if (!user.equals(reaction.getUser())) {
            throw new AccessForbiddenException("Reaction", reaction.getId());
        }

        // get affected post that will be sent as payload in according websocket message
        Post updatedPost;
        if (reaction.getPost() != null) {
            updatedPost = reaction.getPost();
            updatedPost.removeReaction(reaction);
        }
        else {
            AnswerPost updatedAnswerPost = reaction.getAnswerPost();
            updatedAnswerPost.removeReaction(reaction);
            updatedPost = updatedAnswerPost.getPost();
            // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
            // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
            updatedPost.removeAnswerPost(updatedAnswerPost);
            updatedPost.addAnswerPost(updatedAnswerPost);
        }
        postService.broadcastForPost(new MetisPostDTO(updatedPost, MetisPostAction.UPDATE_POST), course);
        reactionRepository.deleteById(reactionId);
    }
}
