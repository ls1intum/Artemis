package de.tum.in.www1.artemis.service.metis;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class ReactionService {

    private static final String METIS_REACTION_ENTITY_NAME = "posting reaction";

    private final UserRepository userRepository;

    private final ReactionRepository reactionRepository;

    private final PostService postService;

    private final AnswerPostService answerPostService;

    public ReactionService(UserRepository userRepository, ReactionRepository reactionRepository, PostService postService, AnswerPostService answerPostService) {
        this.userRepository = userRepository;
        this.reactionRepository = reactionRepository;
        this.postService = postService;
        this.answerPostService = answerPostService;
    }

    /**
     * Checks reaction validity, determines the reaction's user,
     * retrieves the associated posting and persists the mutual association
     *
     * @param reaction reaction to create
     * @return created reaction that was persisted
     */
    public Reaction createReaction(Reaction reaction) {
        Posting posting = reaction.getPost() == null ? reaction.getAnswerPost() : reaction.getPost();

        // checks
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (reaction.getId() != null) {
            throw new BadRequestAlertException("A new reaction cannot already have an ID", METIS_REACTION_ENTITY_NAME, "idexists");
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
            postService.updateWithReaction(post, reaction);
        }
        else {
            AnswerPost answerPost = answerPostService.findById(posting.getId());
            reaction.setAnswerPost(answerPost);
            // save reaction
            savedReaction = reactionRepository.save(reaction);
            // save answer post
            answerPostService.updateWithReaction(answerPost, reaction);
        }
        return savedReaction;
    }

    /**
     * Determines authority to delete reaction and deletes the reaction
     *
     * @param reactionId id of the reaction to delete
     */
    public void deleteReactionById(Long reactionId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Reaction reaction = reactionRepository.findByIdElseThrow(reactionId);

        // check if user that wants to delete reaction is user that created the reaction
        if (!user.equals(reaction.getUser())) {
            throw new AccessForbiddenException("Reaction", reaction.getId());
        }
        reactionRepository.deleteById(reactionId);
    }
}
