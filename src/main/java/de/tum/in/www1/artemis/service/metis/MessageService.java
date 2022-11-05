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
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.MessageRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.GroupChatService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationWebsocketDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class MessageService extends PostingService {

    private final GroupChatService groupChatService;

    private final ConversationService conversationService;

    private final MessageRepository messageRepository;

    protected MessageService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository, MessageRepository messageRepository,
            AuthorizationCheckService authorizationCheckService, SimpMessageSendingOperations messagingTemplate, UserRepository userRepository, GroupChatService groupChatService,
            ConversationService conversationService, ConversationParticipantRepository conversationParticipantRepository) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, messagingTemplate, conversationParticipantRepository);
        this.groupChatService = groupChatService;
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
        if (messagePost.getConversation() != null) {

            final User user = this.userRepository.getUserWithGroupsAndAuthorities();
            Conversation conversation;

            // checks
            if (messagePost.getId() != null) {
                throw new BadRequestAlertException("A new message post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
            }
            final Course course = preCheckUserAndCourse(user, courseId);

            // set author to current user
            messagePost.setAuthor(user);
            // set default value display priority -> NONE
            messagePost.setDisplayPriority(DisplayPriority.NONE);

            if (messagePost.getConversation().getId() == null && messagePost.getConversation() instanceof GroupChat) {
                // persist conversation for post if it is new
                messagePost.setConversation(groupChatService.createNewGroupChat(course, (GroupChat) messagePost.getConversation()));
            }
            conversation = conversationService.mayInteractWithConversationElseThrow(messagePost.getConversation().getId(), user);
            conversation.setLastMessageDate(conversationService.auditConversationReadTimeOfUser(conversation, user));

            Post savedMessage = messageRepository.save(messagePost);
            savedMessage.setConversation(conversation);

            conversationService.updateConversation(conversation);

            broadcastForPost(new PostDTO(savedMessage, MetisCrudAction.CREATE), course);

            // ToDo: Investigate if this means we really send one message for every channel participant and if we can optimize this
            conversationService.broadcastForConversation(course, new ConversationWebsocketDTO(getConversationDTO(user, conversation), MetisCrudAction.UPDATE), null);

            return savedMessage;
        }

        // conversation object must be provided in all cases. we do not throw an exception here in order to not leak implementation details
        return null;
    }

    private ConversationDTO getConversationDTO(User user, Conversation conversation) {
        if (conversation instanceof Channel c) {
            var channelDto = new ChannelDTO(c);
            channelDto.setIsMember(true);
            channelDto.setNumberOfMembers(conversationService.getMemberCount(c.getId()));
            return channelDto;
        }
        else if (conversation instanceof GroupChat g) {
            var groupChat = groupChatService.findByIdWithConversationParticipantsElseThrow(g.getId());
            var groupDto = new GroupChatDTO(groupChat);
            groupDto.setIsMember(true);
            groupDto.setNumberOfMembers(groupChat.getConversationParticipants().size());
            groupDto.setNamesOfOtherMembers(groupChatService.getNamesOfOtherMembers(groupChat, user));
            return groupDto;
        }
        return null;
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

            conversationPosts = messageRepository.findMessages(postContextFilter, pageable);

            // protect sample solution, grading instructions, etc.
            conversationPosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

            setAuthorRoleOfPostings(conversationPosts.getContent());

            conversationService.auditConversationReadTimeOfUser(conversation, user);

            conversationService.broadcastForConversation(conversation.getCourse(),
                    new ConversationWebsocketDTO(getConversationDTO(user, conversation), MetisCrudAction.READ_CONVERSATION), user);
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

        Post existingMessage = messageRepository.findMessagePostByIdElseThrow(postId);
        Conversation conversation = mayUpdateOrDeleteMessageElseThrow(existingMessage, user);

        // update: allow overwriting of values only for depicted fields
        existingMessage.setContent(messagePost.getContent());

        Post updatedPost = messageRepository.save(existingMessage);
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
        Post post = messageRepository.findMessagePostByIdElseThrow(postId);
        post.setConversation(mayUpdateOrDeleteMessageElseThrow(post, user));

        // delete
        messageRepository.deleteById(postId);
        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course);
    }

    private Conversation mayUpdateOrDeleteMessageElseThrow(Post existingMessagePost, User user) {
        // non-message posts should not be manipulated from this endpoint and only the author of a message post should edit or delete the entity
        if (existingMessagePost.getConversation() == null || !existingMessagePost.getAuthor().getId().equals(user.getId())) {
            throw new AccessForbiddenException("Post", existingMessagePost.getId());
        }
        else {
            return conversationService.getConversationById(existingMessagePost.getConversation().getId());
        }
    }

    @Override
    String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }
}
