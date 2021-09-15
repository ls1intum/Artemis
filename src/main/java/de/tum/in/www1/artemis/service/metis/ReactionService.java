package de.tum.in.www1.artemis.service.metis;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

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

@Service
public class ReactionService {

    private static final String METIS_REACTION_ENTITY_NAME = "posting reaction";

    private final UserRepository userRepository;

    private final ReactionRepository reactionRepository;

    private final CourseRepository courseRepository;

    private final PostService postService;

    private final AnswerPostService answerPostService;

    public ReactionService(UserRepository userRepository, ReactionRepository reactionRepository, CourseRepository courseRepository, PostService postService,
            AnswerPostService answerPostService) {
        this.userRepository = userRepository;
        this.reactionRepository = reactionRepository;
        this.courseRepository = courseRepository;
        this.postService = postService;
        this.answerPostService = answerPostService;
    }

    /**
     * Checks reaction validity, determines the reaction's user,
     * retrieves the associated posting and persists the mutual association
     *
     * @param courseId id of the course the post belongs to
     * @param reaction reaction to create
     * @return created reaction that was persisted
     */
    public Reaction createReaction(Long courseId, Reaction reaction) {
        Posting posting = reaction.getPost() == null ? reaction.getAnswerPost() : reaction.getPost();

        // checks
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (reaction.getId() != null) {
            throw new BadRequestAlertException("A new reaction cannot already have an ID", METIS_REACTION_ENTITY_NAME, "idexists");
        }

        // set user to current user
        reaction.setUser(user);

        // set creation date to now
        reaction.setCreationDate(ZonedDateTime.now());

        // we query the repository dependent on the type of posting and update this posting
        Reaction savedReaction;
        if (posting instanceof Post) {
            postService.preCheckUserAndCourse(user, courseId);
            Post post = postService.findById(posting.getId());
            reaction.setPost(post);
            reaction.setUser(user);
            // check if the user already reacted with the reaction to add (-> avoid duplicated reactions) before saving
            if (reactionRepository.findReactionsByPostIdAndEmojiIdAndUserId(post.getId(), reaction.getEmojiId(), user.getId()).size() > 0) {
                throw new BadRequestAlertException("You can only react once with a certain emoji", METIS_REACTION_ENTITY_NAME, "duplicatedReaction");
            }
            // save reaction
            savedReaction = reactionRepository.save(reaction);
            // save post
            postService.updateWithReaction(post, reaction);
            // protect sample solution, grading instructions
            if (post.getExercise() != null) {
                post.getExercise().filterSensitiveInformation();
            }
        }
        else {
            answerPostService.preCheckUserAndCourse(user, courseId);
            AnswerPost answerPost = answerPostService.findById(posting.getId());
            reaction.setAnswerPost(answerPost);
            reaction.setUser(user);
            // check if the user already reacted with the reaction to add (-> avoid duplicated reactions) before saving
            if (reactionRepository.findReactionsByAnswerPostIdAndEmojiIdAndUserId(answerPost.getId(), reaction.getEmojiId(), user.getId()).size() > 0) {
                throw new BadRequestAlertException("You can only react once with a certain emoji", METIS_REACTION_ENTITY_NAME, "duplicatedReaction");
            }
            // save reaction
            savedReaction = reactionRepository.save(reaction);
            // save answer post
            answerPostService.updateWithReaction(answerPost, reaction);
            // protect sample solution, grading instructions, etc.
            if (answerPost.getPost().getExercise() != null) {
                answerPost.getPost().getExercise().filterSensitiveInformation();
            }
        }
        return savedReaction;
    }

    /**
     * Determines authority to delete reaction and deletes the reaction
     *
     * @param courseId   id of the course the reaction belongs to
     * @param reactionId id of the reaction to delete
     */
    public void deleteReactionById(Long courseId, Long reactionId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        courseRepository.findByIdElseThrow(courseId);
        Reaction reaction = reactionRepository.findByIdElseThrow(reactionId);

        // check if user that wants to delete reaction is user that created the reaction
        if (!user.equals(reaction.getUser())) {
            throw new AccessForbiddenException("Reaction", reaction.getId());
        }
        reactionRepository.deleteById(reactionId);

    }
}
