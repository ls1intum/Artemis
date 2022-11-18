package de.tum.in.www1.artemis.service.metis;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class ConversationMessagingService extends PostingService {

    private final ConversationService conversationService;

    private final ConversationMessageRepository conversationMessageRepository;

    protected ConversationMessagingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            ConversationMessageRepository conversationMessageRepository, AuthorizationCheckService authorizationCheckService, SimpMessageSendingOperations messagingTemplate,
            UserRepository userRepository, ConversationService conversationService, ConversationParticipantRepository conversationParticipantRepository) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, messagingTemplate, conversationParticipantRepository);
        this.conversationService = conversationService;
        this.conversationMessageRepository = conversationMessageRepository;
    }

    public Post createMessage(Long courseId, Post messagePost) {
        if (messagePost.getId() != null) {
            throw new BadRequestAlertException("A new message post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }
        if (messagePost.getConversation() == null || messagePost.getConversation().getId() == null) {
            throw new BadRequestAlertException("A new message post must have a conversation", METIS_POST_ENTITY_NAME, "conversationnotset");
        }

        var author = this.userRepository.getUserWithGroupsAndAuthorities();
        var course = preCheckUserAndCourse(author, courseId);
        messagePost.setAuthor(author);
        messagePost.setDisplayPriority(DisplayPriority.NONE);
        var conversation = conversationService.mayInteractWithConversationElseThrow(messagePost.getConversation().getId(), author);

        if (conversation instanceof Channel channel) {
            if (channel.getIsArchived()) {
                throw new BadRequestAlertException("A message cannot be created in an archived channel", METIS_POST_ENTITY_NAME, "channelarchived");
            }
        }

        // update last message date and conversation read time of user at the same time
        conversation.setLastMessageDate(conversationService.auditConversationReadTimeOfUser(conversation, author));
        var savedMessage = conversationMessageRepository.save(messagePost);
        savedMessage.setConversation(conversation);
        conversation = conversationService.updateConversation(conversation);
        broadcastForPost(new PostDTO(savedMessage, MetisCrudAction.CREATE), course);

        if (conversation instanceof OneToOneChat || conversation instanceof GroupChat) {
            var getNumberOfPosts = conversationMessageRepository.countByConversationId(conversation.getId());
            if (getNumberOfPosts == 1) { // first message in one to one message chat or group chat --> notify all participants that a conversation with them has been created
                var participants = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId()).stream()
                        .map(ConversationParticipant::getUser).filter(Objects::nonNull).collect(Collectors.toSet());
                conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, conversation, participants);
            }
        }
        // send conversation with updated last message date to participants. This is necessary to show the unread messages badge in the client
        conversationService.notifyConversationMembersAboutUpdate(conversation);
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

            Conversation conversation = conversationService.mayInteractWithConversationElseThrow(postContextFilter.getConversationId(), user);

            conversationPosts = conversationMessageRepository.findMessages(postContextFilter, pageable);

            // protect sample solution, grading instructions, etc.
            conversationPosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

            setAuthorRoleOfPostings(conversationPosts.getContent());

            conversationService.auditConversationReadTimeOfUser(conversation, user);

            conversationService.broadcastOnConversationMembershipChannel(conversation.getCourse(), MetisCrudAction.READ_CONVERSATION, conversation, Set.of(user));
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

        Post existingMessage = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        Conversation conversation = mayUpdateOrDeleteMessageElseThrow(existingMessage, user);

        // update: allow overwriting of values only for depicted fields
        existingMessage.setContent(messagePost.getContent());

        Post updatedPost = conversationMessageRepository.save(existingMessage);
        updatedPost.setConversation(conversation);

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
        Post post = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        post.setConversation(mayUpdateOrDeleteMessageElseThrow(post, user));

        // delete
        conversationMessageRepository.deleteById(postId);
        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course);
    }

    private Conversation mayUpdateOrDeleteMessageElseThrow(Post existingMessagePost, User user) {
        // non-message posts should not be manipulated from this endpoint and only the author of a message post should edit or delete the entity
        if (existingMessagePost.getConversation() == null || !existingMessagePost.getAuthor().getId().equals(user.getId())) {
            throw new AccessForbiddenException("Post", existingMessagePost.getId());
        }
        else {
            var conversation = conversationService.getConversationById(existingMessagePost.getConversation().getId());
            if (conversation instanceof Channel channel) {
                if (channel.getIsArchived()) {
                    throw new BadRequestAlertException("A message cannot be created in an archived channel", METIS_POST_ENTITY_NAME, "channelarchived");
                }
            }
            return conversation;
        }
    }

    @Override
    String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }
}
