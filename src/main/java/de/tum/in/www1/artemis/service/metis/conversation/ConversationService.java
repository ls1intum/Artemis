package de.tum.in.www1.artemis.service.metis.conversation;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationDTO;

@Service
public class ConversationService {

    private static final String CONVERSATION_ENTITY_NAME = "messages.conversation";

    private static final String CONVERSATION_DETAILS_ENTITY_NAME = "messages.conversationParticipant";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ConversationRepository conversationRepository;

    private final ChannelRepository channelRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final GroupChatRepository groupChatRepository;

    private final ChannelService channelService;

    public ConversationService(UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            ChannelRepository channelRepository, ConversationParticipantRepository conversationParticipantRepository, ConversationRepository conversationRepository,
            SimpMessageSendingOperations messagingTemplate, GroupChatRepository groupChatRepository, ChannelService channelService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.channelRepository = channelRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
        this.groupChatRepository = groupChatRepository;
        this.channelService = channelService;
    }

    public void registerUsers(Set<User> usersToRegister, Conversation conversation) {
        var userThatNeedToBeRegistered = new HashSet<User>();

        for (User user : usersToRegister) {
            var isRegistered = conversation.getConversationParticipants().stream()
                    .anyMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()));
            if (!isRegistered) {
                userThatNeedToBeRegistered.add(user);
            }
        }

        List<ConversationParticipant> newConversationParticipants = new ArrayList<>();
        for (User user : userThatNeedToBeRegistered) {
            ConversationParticipant conversationParticipant = new ConversationParticipant();
            conversationParticipant.setUser(user);
            conversationParticipant.setConversation(conversation);
            newConversationParticipants.add(conversationParticipant);
        }
        newConversationParticipants = conversationParticipantRepository.saveAll(newConversationParticipants);
        conversation.getConversationParticipants().addAll(newConversationParticipants);
        conversationRepository.save(conversation);
    }

    public void deregisterUsers(Set<User> usersToDeregister, Conversation conversation) {
        var participantsToRemove = new HashSet<ConversationParticipant>();

        for (User user : usersToDeregister) {
            var participant = conversation.getConversationParticipants().stream().filter(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()))
                    .findFirst();
            participant.ifPresent(participantsToRemove::add);
        }

        conversation.getConversationParticipants().removeAll(participantsToRemove);
        conversationRepository.save(conversation);
        conversationParticipantRepository.deleteAll(participantsToRemove);
    }

    /**
     * fetch conversation from database by conversationId
     *
     * @param conversationId id of the conversation to fetch
     * @return fetched conversation
     */
    public Conversation getConversationByIdWithConversationParticipants(Long conversationId) {
        return conversationRepository.findConversationByIdWithConversationParticipants(conversationId);
    }

    public Conversation getConversationById(Long conversationId) {
        return conversationRepository.findByIdElseThrow(conversationId);
    }

    public boolean isMember(Long conversationId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, userId).isPresent();
    }

    public List<Conversation> getConversationsOfUser(Long courseId, User user) {

        // Note: Includes participants of group chats
        var activeGroupChatsOfUser = groupChatRepository.findActiveGroupChatsOfUserWithConversationParticipants(courseId, user.getId());
        activeGroupChatsOfUser.forEach(groupChat -> groupChat.setNumberOfMembers(groupChat.getConversationParticipants().size()));
        activeGroupChatsOfUser.forEach(conversation -> filterSensitiveInformation(conversation, user));

        // Note: Does NOT include participants of channels
        var channelsOfUser = channelRepository.findChannelsOfUser(courseId, user.getId());
        channelsOfUser.forEach(channel -> channel.setNumberOfMembers(channelService.getMemberCount(channel.getId())));

        var allConversations = new ArrayList<Conversation>();
        allConversations.addAll(activeGroupChatsOfUser);
        allConversations.addAll(channelsOfUser);

        return allConversations;
    }

    public void updateConversation(Conversation conversation) {
        conversationRepository.save(conversation);
    }

    /**
     * Broadcasts a conversation event in a course under a specific topic via websockets
     *
     * @param conversationDTO object including the affected conversation as well as the action
     * @param user            if not null, the user the message is specifically targeted to
     */
    public void broadcastForConversation(ConversationDTO conversationDTO, User user) {
        String courseTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + conversationDTO.getConversation().getCourse().getId();
        String conversationParticipantTopicName = courseTopicName + "/conversations/user/";

        if (user == null) {
            conversationDTO.getConversation().getConversationParticipants()
                    .forEach(conversationParticipant -> messagingTemplate.convertAndSendToUser(conversationParticipant.getUser().getLogin(),
                            conversationParticipantTopicName + conversationParticipant.getUser().getId(), conversationDTO));
        }
        else {
            messagingTemplate.convertAndSendToUser(user.getLogin(), conversationParticipantTopicName + user.getId(), conversationDTO);
        }
    }

    public Conversation mayInteractWithConversationElseThrow(Long conversationId, User user) {
        // use object fetched from database
        Conversation conversation = conversationRepository.findConversationByIdWithConversationParticipants(conversationId);
        if (conversation == null
                || conversation.getConversationParticipants().stream().noneMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()))) {
            throw new AccessForbiddenException("User not allowed to access this conversation!");
        }

        return conversation;
    }

    /**
     * filters sensitive information such as last read times of other users
     *
     * @param user         user whose sensitive information will be preserved
     * @param conversation object to be filtered for sensitive information
     */
    static void filterSensitiveInformation(Conversation conversation, User user) {
        conversation.getConversationParticipants().forEach(conversationParticipant -> {
            if (!conversationParticipant.getUser().getId().equals(user.getId())) {
                conversationParticipant.filterSensitiveInformation();
            }
        });
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     *
     * @return conversation entity name
     */
    public String getEntityName() {
        return CONVERSATION_ENTITY_NAME;
    }

    public ZonedDateTime auditConversationReadTimeOfUser(Conversation conversation, User user) {
        // update the last time user has read the conversation
        ConversationParticipant readingParticipant = conversation.getConversationParticipants().stream()
                .filter(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId())).findAny().get();
        readingParticipant.setLastRead(ZonedDateTime.now());
        conversationParticipantRepository.save(readingParticipant);
        return readingParticipant.getLastRead();
    }

    public Page<User> searchMembersOfConversation(Pageable pageable, Long conversationId, String searchTerm) {
        return userRepository.searchAllByLoginOrNameInConversation(pageable, searchTerm, conversationId);
    }
}
