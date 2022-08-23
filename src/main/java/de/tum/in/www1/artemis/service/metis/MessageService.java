package de.tum.in.www1.artemis.service.metis;

import java.util.Objects;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.MessageRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class MessageService extends PostingService {

    private final UserRepository userRepository;

    private final ConversationService conversationService;

    private final MessageRepository messageRepository;

    protected MessageService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository, MessageRepository messageRepository,
            AuthorizationCheckService authorizationCheckService, SimpMessageSendingOperations messagingTemplate, UserRepository userRepository,
            ConversationService conversationService) {
        super(courseRepository, exerciseRepository, lectureRepository, authorizationCheckService, messagingTemplate);
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
    }

    /**
     * Checks course, user and post message validity,
     * determines the post's author, persists the post,
     * and sends a notification to affected user groups
     *
     * @param courseId    id of the course the post belongs to
     * @param messagePost message post to create
     * @return created message post that was persisted
     */
    public Post createMessage(Long courseId, Post messagePost) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (messagePost.getId() != null) {
            throw new BadRequestAlertException("A new message post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }
        final Course course = preCheckUserAndCourse(user, courseId);

        // set author to current user
        messagePost.setAuthor(user);
        // set default value display priority -> NONE
        messagePost.setDisplayPriority(DisplayPriority.NONE);

        if (messagePost.getConversation() != null) {
            if (messagePost.getConversation().getId() == null) {
                // persist conversation for post if provided
                messagePost.setConversation(conversationService.createConversation(courseId, messagePost.getConversation()));
            }
            conversationService.mayInteractWithConversationElseThrow(messagePost.getConversation().getId(), user);
        }

        Post savedMessage = messageRepository.save(messagePost);
        broadcastForPost(new PostDTO(savedMessage, MetisCrudAction.CREATE), course);

        return savedMessage;
    }

    /**
     * fetch posts from database by conversationId
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch posts
     * @return page of posts that match the given context
     */
    public Page<Post> getMessages(Pageable pageable, @Valid PostContextFilter postContextFilter) {
        Page<Post> conversationPosts;
        if (postContextFilter.getConversationId() != null) {

            final User user = userRepository.getUserWithGroupsAndAuthorities();
            Conversation conversation = conversationService.getConversationById(postContextFilter.getConversationId());

            conversationService.mayInteractWithConversationElseThrow(conversation.getId(), user);

            conversationPosts = messageRepository.findMessages(postContextFilter, pageable);

            // protect sample solution, grading instructions, etc.
            conversationPosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);
        }
        else {
            throw new BadRequestAlertException("A new message post cannot be associated with more than one context", METIS_POST_ENTITY_NAME, "ambiguousContext");
        }

        return conversationPosts;
    }

    /**
     * Checks course, user and post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId    id of the course the post belongs to
     * @param postId      id of the post to update
     * @param messagePost post to update
     * @return updated post that was persisted
     */
    public Post updateMessage(Long courseId, Long postId, Post messagePost) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        // check
        if (messagePost.getId() == null || !Objects.equals(messagePost.getId(), postId)) {
            throw new BadRequestAlertException("Invalid id", METIS_POST_ENTITY_NAME, "idnull");
        }
        final Course course = preCheckUserAndCourse(user, courseId);

        Post existingPost = messageRepository.findMessagePostByIdElseThrow(postId);
        mayUpdateOrDeleteMessageElseThrow(existingPost, user);

        // update: allow overwriting of values only for depicted fields
        existingPost.setContent(messagePost.getContent());

        Post updatedPost = messageRepository.save(existingPost);

        // emit a post update via websocket
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course);

        return updatedPost;
    }

    /**
     * Checks course, user and post validity,
     * determines authority to delete post and deletes the post
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the message post to delete
     */
    public void deleteMessageById(Long courseId, Long postId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        Post post = messageRepository.findMessagePostByIdElseThrow(postId);
        mayUpdateOrDeleteMessageElseThrow(post, user);

        // delete
        messageRepository.deleteById(postId);
        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course);
    }

    // TODO share code snippet for answerPost check
    private void mayUpdateOrDeleteMessageElseThrow(Post existingMessagePost, User user) {
        // non-message posts should not be manipulated from this endpoint and only the author of a message post should edit or delete the entity
        if (existingMessagePost.getConversation() == null || !existingMessagePost.getAuthor().getId().equals(user.getId())) {
            throw new AccessForbiddenException("Post", existingMessagePost.getId());
        }
    }

    @Override
    String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }
}
