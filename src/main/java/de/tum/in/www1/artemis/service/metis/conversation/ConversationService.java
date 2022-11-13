package de.tum.in.www1.artemis.service.metis.conversation;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationWebsocketDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

@Service
public class ConversationService {

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final UserRepository userRepository;

    private final ConversationRepository conversationRepository;

    private final ChannelRepository channelRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final GroupChatRepository groupChatRepository;

    private final GroupChatService groupChatService;

    private final ChannelAuthorizationService channelAuthorizationService;

    public ConversationService(UserRepository userRepository, ChannelRepository channelRepository, ConversationParticipantRepository conversationParticipantRepository,
            ConversationRepository conversationRepository, SimpMessageSendingOperations messagingTemplate, GroupChatRepository groupChatRepository,
            GroupChatService groupChatService, ChannelAuthorizationService channelAuthorizationService) {
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
        this.groupChatRepository = groupChatRepository;
        this.groupChatService = groupChatService;
        this.channelAuthorizationService = channelAuthorizationService;
    }

    public void registerUsers(Course course, Set<User> usersToRegister, Conversation conversation) {
        var conversationFromDatabase = conversationRepository.findByIdWithConversationParticipantsElseThrow(conversation.getId());
        var userThatNeedToBeRegistered = new HashSet<User>();

        for (User user : usersToRegister) {
            var isRegistered = conversationFromDatabase.getConversationParticipants().stream()
                    .anyMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()));
            if (!isRegistered) {
                userThatNeedToBeRegistered.add(user);
            }
        }

        List<ConversationParticipant> newConversationParticipants = new ArrayList<>();
        for (User user : userThatNeedToBeRegistered) {
            ConversationParticipant conversationParticipant = new ConversationParticipant();
            conversationParticipant.setUser(user);
            conversationParticipant.setConversation(conversationFromDatabase);
            newConversationParticipants.add(conversationParticipant);
        }
        newConversationParticipants = conversationParticipantRepository.saveAll(newConversationParticipants);
        conversationFromDatabase.getConversationParticipants().addAll(newConversationParticipants);
        conversationRepository.save(conversationFromDatabase);

        broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, conversationFromDatabase, userThatNeedToBeRegistered);
    }

    public void deregisterUsers(Course course, Set<User> usersToDeregister, Conversation conversation) {
        var conversationFromDatabase = conversationRepository.findByIdWithConversationParticipantsElseThrow(conversation.getId());

        if (conversation instanceof Channel channel) {
            var creator = channel.getCreator();
            if (usersToDeregister.contains(creator)) {
                throw new AccessForbiddenException("The creator of a channel cannot be deregistered. Even self deregistration is not possible.");
            }
        }

        var participantsToRemove = new HashSet<ConversationParticipant>();

        for (User user : usersToDeregister) {
            var participant = conversationFromDatabase.getConversationParticipants().stream()
                    .filter(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId())).findFirst();
            participant.ifPresent(participantsToRemove::add);
        }

        conversationFromDatabase.getConversationParticipants().removeAll(participantsToRemove);
        conversationRepository.save(conversationFromDatabase);
        conversationParticipantRepository.deleteAll(participantsToRemove);
        broadcastOnConversationMembershipChannel(course, MetisCrudAction.DELETE, conversationFromDatabase, usersToDeregister);

    }

    public ConversationDTO convertToDTOForUser(Conversation conversation, User user) {
        ConversationDTO dto = null;
        if (conversation instanceof Channel channel) {
            dto = new ChannelDTO(channel);
            ((ChannelDTO) dto).setIsChannelAdmin(channelAuthorizationService.isChannelAdmin(channel.getId(), user.getId()));
            ((ChannelDTO) dto).setHasChannelAdminRights(channelAuthorizationService.hasChannelAdminRights(channel.getId(), user));
        }
        if (conversation instanceof GroupChat groupChat) {
            dto = new GroupChatDTO(groupChat);
            ((GroupChatDTO) dto).setNamesOfOtherMembers(groupChatService.getNamesOfOtherMembers(groupChat, user));
        }
        if (dto == null) {
            throw new IllegalArgumentException("The conversation type is not supported.");
        }
        dto.setIsMember(isMember(conversation.getId(), user.getId()));
        dto.setIsCreator(isCreator(conversation.getId(), user.getId()));
        dto.setNumberOfMembers(getMemberCount(conversation.getId()));
        return dto;
    }

    public Conversation getConversationById(Long conversationId) {
        return conversationRepository.findByIdElseThrow(conversationId);
    }

    public boolean isMember(Long conversationId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, userId).isPresent();
    }

    public boolean isCreator(Long conversationId, Long userId) {
        var conversation = conversationRepository.findByIdElseThrow(conversationId);
        if (conversation.getCreator() != null) {
            return conversation.getCreator().getId().equals(userId);
        }
        else {
            return false;
        }
    }

    public Integer getMemberCount(Long channelId) {
        return conversationParticipantRepository.countByConversationId(channelId);
    }

    public List<ConversationDTO> getConversationsOfUser(Long courseId, User user) {
        var activeGroupChatsOfUser = groupChatRepository.findActiveGroupChatsOfUserWithConversationParticipants(courseId, user.getId());
        var channelsOfUser = channelRepository.findChannelsOfUser(courseId, user.getId());
        var conversations = new ArrayList<Conversation>();
        conversations.addAll(activeGroupChatsOfUser);
        conversations.addAll(channelsOfUser);
        var conversationDTOs = conversations.parallelStream().unordered().map(conversation -> convertToDTOForUser(conversation, user)).collect(Collectors.toList());
        return conversationDTOs;
    }

    public Conversation updateConversation(Conversation conversation) {
        return conversationRepository.save(conversation);
    }

    public void broadcastOnConversationMembershipChannel(Course course, MetisCrudAction metisCrudAction, Conversation conversation, Set<User> usersToMessage) {
        String courseTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + course.getId();
        String conversationParticipantTopicName = courseTopicName + "/conversations/user/";
        usersToMessage.forEach(user -> sendToConversationMembershipChannel(metisCrudAction, conversation, user, conversationParticipantTopicName));
    }

    private void sendToConversationMembershipChannel(MetisCrudAction metisCrudAction, Conversation conversation, User user, String conversationParticipantTopicName) {
        var dto = convertToDTOForUser(conversation, user);
        var websocketDTO = new ConversationWebsocketDTO(dto, metisCrudAction);
        messagingTemplate.convertAndSendToUser(user.getLogin(), conversationParticipantTopicName + user.getId(), websocketDTO);
    }

    public Conversation mayInteractWithConversationElseThrow(Long conversationId, User user) {
        Optional<Conversation> conversation = conversationRepository.findById(conversationId);
        if (conversation.isEmpty() || !isMember(conversationId, user.getId())) {
            throw new AccessForbiddenException("User not allowed to access this conversation!");
        }
        return conversation.get();
    }

    public ZonedDateTime auditConversationReadTimeOfUser(Conversation conversation, User user) {
        // update the last time user has read the conversation
        ConversationParticipant readingParticipant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversation.getId(), user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Conversation participant not found!"));
        readingParticipant.setLastRead(ZonedDateTime.now());
        conversationParticipantRepository.save(readingParticipant);
        return readingParticipant.getLastRead();
    }

    public Page<User> searchMembersOfConversation(Pageable pageable, Long conversationId, String searchTerm) {
        return userRepository.searchAllByLoginOrNameInConversation(pageable, searchTerm, conversationId);
    }
}
