package de.tum.in.www1.artemis.service.metis;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class ReactionService {

    private static final String METIS_REACTION_ENTITY_NAME = "posting reaction";

    // constant must be same as it is in the client (metis.util.ts#28)
    private static final String VOTE_EMOJI_ID = "heavy_plus_sign";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ReactionRepository reactionRepository;

    private final PostService postService;

    private final AnswerPostService answerPostService;

    private final ConversationService conversationService;

    public ReactionService(UserRepository userRepository, CourseRepository courseRepository, ReactionRepository reactionRepository, PostService postService,
            AnswerPostService answerPostService, ConversationService conversationService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.reactionRepository = reactionRepository;
        this.postService = postService;
        this.answerPostService = answerPostService;
        this.conversationService = conversationService;
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
            Post post = postService.findPostOrMessagePostById(posting.getId());
            post.setConversation(mayInteractWithConversationIfConversationMessage(user, post));
            reaction.setPost(post);
            // save reaction
            savedReaction = reactionRepository.save(reaction);

            if (VOTE_EMOJI_ID.equals(reaction.getEmojiId())) {
                // increase voteCount of post needed for sorting
                post.setVoteCount(post.getVoteCount() + 1);
            }

            // save post
            postService.addReaction(post, reaction, courseId);
        }
        else {
            AnswerPost answerPost = answerPostService.findAnswerPostOrAnswerMessageById(posting.getId());
            answerPost.getPost().setConversation(mayInteractWithConversationIfConversationMessage(user, answerPost.getPost()));
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
     * @param reactionId id of the reaction to delete
     * @param courseId   id of the course the according posting belongs to
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
            updatedPost.setConversation(mayInteractWithConversationIfConversationMessage(user, updatedPost));

            if (VOTE_EMOJI_ID.equals(reaction.getEmojiId())) {
                // decrease voteCount of post needed for sorting
                updatedPost.setVoteCount(updatedPost.getVoteCount() - 1);
            }

            // remove reaction and persist post
            postService.removeReaction(updatedPost, reaction, courseId);
        }
        else {
            AnswerPost updatedAnswerPost = reaction.getAnswerPost();
            updatedAnswerPost.getPost().setConversation(mayInteractWithConversationIfConversationMessage(user, updatedAnswerPost.getPost()));
            updatedAnswerPost.removeReaction(reaction);
            updatedPost = updatedAnswerPost.getPost();
            // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
            // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
            updatedPost.removeAnswerPost(updatedAnswerPost);
            updatedPost.addAnswerPost(updatedAnswerPost);
        }
        postService.broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course);
        reactionRepository.deleteById(reactionId);
    }

    private Conversation mayInteractWithConversationIfConversationMessage(User user, Post post) {
        if (post.getConversation() != null) {
            return conversationService.mayInteractWithConversationElseThrow(post.getConversation().getId(), user);
        }
        else {
            return null;
        }
    }
}
