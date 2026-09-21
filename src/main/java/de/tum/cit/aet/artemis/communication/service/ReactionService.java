package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.ReactionDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.ReactionRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismPostApi;
import de.tum.cit.aet.artemis.plagiarism.exception.PlagiarismApiNotPresentException;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ReactionService {

    private static final String METIS_REACTION_ENTITY_NAME = "posting reaction";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ReactionRepository reactionRepository;

    private final Optional<PlagiarismPostApi> plagiarismPostApi;

    private final ConversationService conversationService;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    public ReactionService(UserRepository userRepository, CourseRepository courseRepository, ReactionRepository reactionRepository, Optional<PlagiarismPostApi> plagiarismPostApi,
            ConversationService conversationService, PostRepository postRepository, AnswerPostRepository answerPostRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.reactionRepository = reactionRepository;
        this.plagiarismPostApi = plagiarismPostApi;
        this.conversationService = conversationService;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
    }

    /**
     * Checks reaction validity, determines the reaction's user,
     * retrieves the associated posting and persists the mutual association
     *
     * @param courseId    id of the course the corresponding posting belongs to
     * @param reactionDTO reaction to create
     * @return created reaction that was persisted
     */
    public Reaction createReaction(Long courseId, ReactionDTO reactionDTO) {
        if (reactionDTO.id() != null) {
            throw new BadRequestAlertException("A new reaction cannot already have an ID", METIS_REACTION_ENTITY_NAME, "idExists");
        }

        if (reactionDTO.emojiId() == null || reactionDTO.emojiId().isBlank()) {
            throw new BadRequestAlertException("emojiId must be set", METIS_REACTION_ENTITY_NAME, "emojiIdMissing");
        }

        final Course course = courseRepository.findByIdElseThrow(courseId);
        final User user = this.userRepository.getUserWithAuthorities();
        final long targetId = reactionDTO.relatedPostId();

        Reaction reaction = new Reaction();
        reaction.setEmojiId(reactionDTO.emojiId());
        reaction.setUser(user);

        if (reactionDTO.postingType() == PostingType.ANSWER) {
            return reactToAnswerPost(reaction, answerPostRepository.findByIdElseThrow(targetId), user, course);
        }
        if (reactionDTO.postingType() == PostingType.POST) {
            return reactToPost(reaction, postRepository.findByIdElseThrow(targetId), user, course);
        }

        // No type given, so the id has to be resolved on its own. Posts and answer posts are numbered independently, which means the same value
        // regularly denotes one of each; the course tells them apart in every case except the one where both happen to live in it.
        Optional<AnswerPost> answerPostById = answerPostRepository.findById(targetId);
        Optional<Post> postById = postRepository.findById(targetId);

        Optional<AnswerPost> answerPost = answerPostById.filter(candidate -> belongsToCourse(candidate.getCoursePostingBelongsTo(), courseId));
        Optional<Post> post = postById.filter(candidate -> belongsToCourse(candidate.getCoursePostingBelongsTo(), courseId));

        if (answerPost.isPresent() && post.isPresent()) {
            throw new BadRequestAlertException("The id " + targetId + " denotes both a post and an answer post in this course, so postingType is required",
                    METIS_REACTION_ENTITY_NAME, "ambiguousPostingId");
        }
        if (answerPost.isPresent()) {
            return reactToAnswerPost(reaction, answerPost.get(), user, course);
        }
        if (post.isPresent()) {
            return reactToPost(reaction, post.get(), user, course);
        }

        // Nothing in this course carries the id, so the request fails either way. Which way matters to the caller: an id that denotes no posting
        // at all is a 404, while one that denotes a posting in another course is the same wrongCourse rejection the typed paths give.
        if (answerPostById.isEmpty() && postById.isEmpty()) {
            throw new EntityNotFoundException("Posting", targetId);
        }
        throw new BadRequestAlertException("Reaction does not belong to the given course", METIS_REACTION_ENTITY_NAME, "wrongCourse");
    }

    private boolean belongsToCourse(Course postingCourse, Long courseId) {
        return postingCourse != null && Objects.equals(postingCourse.getId(), courseId);
    }

    private Reaction reactToPost(Reaction reaction, Post post, User user, Course course) {
        checkThatCourseHasCourseIdElseThrow(course.getId(), post.getCoursePostingBelongsTo());
        reaction.setPost(post);
        return createReactionForPost(reaction, post, user, course);
    }

    private Reaction reactToAnswerPost(Reaction reaction, AnswerPost answerPost, User user, Course course) {
        checkThatCourseHasCourseIdElseThrow(course.getId(), answerPost.getCoursePostingBelongsTo());
        reaction.setAnswerPost(answerPost);
        return createReactionForAnswer(reaction, answerPost, user, course);
    }

    /**
     * Determines authority to delete reaction and deletes the reaction
     *
     * @param reactionId id of the reaction to delete
     * @param courseId   id of the course the according posting belongs to
     */
    public void deleteReactionByIdIfAllowedElseThrow(Long reactionId, Long courseId) {
        final User user = userRepository.getUserWithAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);
        Reaction reaction = reactionRepository.findByIdElseThrow(reactionId);

        Course reactionCourse = getReactionCourseElseThrow(reaction);
        checkThatCourseHasCourseIdElseThrow(courseId, reactionCourse);

        // check if user that wants to delete reaction is user that created the reaction
        if (!user.equals(reaction.getUser())) {
            throw new AccessForbiddenException("Reaction", reaction.getId());
        }

        // get affected post that will be sent as payload in according websocket message
        Post updatedPost;
        if (reaction.getPost() != null) {
            updatedPost = reaction.getPost();
            mayInteractWithConversationElseThrow(user, updatedPost, course);
            // remove reaction and persist post
            updatedPost.removeReaction(reaction);
            postRepository.save(updatedPost);
        }
        else {
            AnswerPost updatedAnswerPost = reaction.getAnswerPost();
            mayInteractWithConversationElseThrow(user, updatedAnswerPost.getPost(), course);
            updatedAnswerPost.removeReaction(reaction);
            updatedPost = updatedAnswerPost.getPost();
            // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
            // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
            updatedPost.removeAnswerPost(updatedAnswerPost);
            updatedPost.addAnswerPost(updatedAnswerPost);
        }

        PlagiarismPostApi api = plagiarismPostApi.orElseThrow(() -> new PlagiarismApiNotPresentException(PlagiarismPostApi.class));
        api.preparePostForBroadcast(updatedPost);
        api.broadcastForPost(updatedPost, MetisCrudAction.UPDATE, course.getId(), null);
        reactionRepository.deleteById(reactionId);
    }

    private void mayInteractWithConversationElseThrow(User user, Post post, Course course) {
        if (post.getConversation() != null) {
            PlagiarismPostApi api = plagiarismPostApi.orElseThrow(() -> new PlagiarismApiNotPresentException(PlagiarismPostApi.class));
            conversationService.isMemberOrCreateForCourseWideElseThrow(post.getConversation().getId(), user, Optional.empty());
            api.preCheckUserAndCourseForCommunicationOrMessaging(user, course);
        }
    }

    /**
     * Adds the given reaction to the answer
     *
     * @param reaction reaction to add
     * @param posting  answer to add the reaction to
     * @param user     user who reacted
     * @param course   course the post belongs to
     * @return saved reaction
     */
    private Reaction createReactionForAnswer(Reaction reaction, AnswerPost posting, User user, Course course) {
        PlagiarismPostApi api = plagiarismPostApi.orElseThrow(() -> new PlagiarismApiNotPresentException(PlagiarismPostApi.class));
        Reaction savedReaction;
        AnswerPost answerPost = api.findAnswerPostOrAnswerMessageById(posting.getId());
        mayInteractWithConversationElseThrow(user, answerPost.getPost(), course);
        reaction.setAnswerPost(answerPost);
        // save reaction
        savedReaction = reactionRepository.save(reaction);
        answerPost.addReaction(savedReaction);

        // save answer post
        AnswerPost updatedAnswerPost = answerPostRepository.save(answerPost);
        updatedAnswerPost.getPost().setConversation(answerPost.getPost().getConversation());

        api.preparePostAndBroadcast(answerPost, course);
        return savedReaction;
    }

    /**
     * Adds the given reaction to the post
     *
     * @param reaction reaction to add
     * @param posting  post to add the reaction to
     * @param user     user who reacted
     * @param course   course the post belongs to
     * @return saved reaction
     */
    private Reaction createReactionForPost(Reaction reaction, Post posting, User user, Course course) {
        PlagiarismPostApi api = plagiarismPostApi.orElseThrow(() -> new PlagiarismApiNotPresentException(PlagiarismPostApi.class));

        Reaction savedReaction;
        Post post = api.findPostOrMessagePostById(posting.getId());
        mayInteractWithConversationElseThrow(user, post, course);
        reaction.setPost(post);
        // save reaction
        savedReaction = reactionRepository.save(reaction);
        post.addReaction(reaction);
        Post updatedPost = postRepository.save(post);
        updatedPost.setConversation(post.getConversation());

        api.preparePostForBroadcast(post);
        api.broadcastForPost(post, MetisCrudAction.UPDATE, course.getId(), null);
        return savedReaction;
    }

    /**
     * Returns the course for the given reaction, throws if not found.
     *
     * @param reaction the reaction entity
     * @return the associated course
     * @throws BadRequestAlertException if no course can be found
     */
    private Course getReactionCourseElseThrow(Reaction reaction) {
        if (reaction.getPost() != null) {
            return reaction.getPost().getCoursePostingBelongsTo();
        }
        if (reaction.getAnswerPost() != null) {
            return reaction.getAnswerPost().getCoursePostingBelongsTo();
        }
        throw new BadRequestAlertException("Reaction could not be found", METIS_REACTION_ENTITY_NAME, "reactionNotFound");
    }

    /**
     * Ensures that the given course matches the expected course ID.
     *
     * @param expectedCourseId the ID of the course that is expected
     * @param actual           the actual course to verify
     * @throws BadRequestAlertException if the actual course is null or does not match the expected course ID
     */
    private void checkThatCourseHasCourseIdElseThrow(Long expectedCourseId, Course actual) {
        if (actual == null || !Objects.equals(actual.getId(), expectedCourseId)) {
            throw new BadRequestAlertException("Reaction does not belong to the given course", METIS_REACTION_ENTITY_NAME, "wrongCourse");
        }
    }
}
