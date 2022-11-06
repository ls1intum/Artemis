package de.tum.in.www1.artemis.service.metis.conversation;

import java.time.ZonedDateTime;
import java.util.*;

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

    public ConversationService(UserRepository userRepository, ChannelRepository channelRepository, ConversationParticipantRepository conversationParticipantRepository,
            ConversationRepository conversationRepository, SimpMessageSendingOperations messagingTemplate, GroupChatRepository groupChatRepository,
            GroupChatService groupChatService) {
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
        this.groupChatRepository = groupChatRepository;
        this.groupChatService = groupChatService;
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

        var websocketDTO = new ConversationWebsocketDTO(convertConversationToDTO(conversationFromDatabase), MetisCrudAction.CREATE);
        broadcastOnConversationMembershipChannel(course, websocketDTO, usersToRegister);
    }

    public void deregisterUsers(Course course, Set<User> usersToDeregister, Conversation conversation) {
        var conversationFromDatabase = conversationRepository.findByIdWithConversationParticipantsElseThrow(conversation.getId());

        var participantsToRemove = new HashSet<ConversationParticipant>();

        for (User user : usersToDeregister) {
            var participant = conversationFromDatabase.getConversationParticipants().stream()
                    .filter(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId())).findFirst();
            participant.ifPresent(participantsToRemove::add);
        }

        conversationFromDatabase.getConversationParticipants().removeAll(participantsToRemove);
        conversationRepository.save(conversationFromDatabase);
        conversationParticipantRepository.deleteAll(participantsToRemove);
        var websocketDTO = new ConversationWebsocketDTO(convertConversationToDTO(conversationFromDatabase), MetisCrudAction.DELETE);
        broadcastOnConversationMembershipChannel(course, websocketDTO, usersToDeregister);

    }

    public ConversationDTO convertConversationToDTO(Conversation conversation) {
        var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();
        ConversationDTO dto = null;
        if (conversation instanceof Channel channel) {
            dto = new ChannelDTO(channel);
        }
        else if (conversation instanceof GroupChat groupChat) {
            dto = new GroupChatDTO(groupChat);
            ((GroupChatDTO) dto).setNamesOfOtherMembers(groupChatService.getNamesOfOtherMembers(groupChat, requestingUser));
        }
        if (dto != null) {
            dto.setIsMember(isMember(conversation.getId(), requestingUser.getId()));
            dto.setNumberOfMembers(getMemberCount(conversation.getId()));
        }
        return dto;
    }

    public Conversation getConversationById(Long conversationId) {
        return conversationRepository.findByIdElseThrow(conversationId);
    }

    public boolean isMember(Long conversationId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, userId).isPresent();
    }

    public Integer getMemberCount(Long channelId) {
        return conversationParticipantRepository.countByConversationId(channelId);
    }

    public List<ConversationDTO> getConversationsOfUser(Long courseId, User user) {

        var activeGroupChatsOfUser = groupChatRepository.findActiveGroupChatsOfUserWithConversationParticipants(courseId, user.getId()).stream().map(groupChat -> {
            var dto = new GroupChatDTO(groupChat);
            dto.setIsMember(true);
            dto.setNumberOfMembers(groupChat.getConversationParticipants().size());
            dto.setNamesOfOtherMembers(groupChatService.getNamesOfOtherMembers(groupChat, user));
            return dto;
        }).toList();

        var channelsOfUser = channelRepository.findChannelsOfUser(courseId, user.getId()).stream().map(channel -> {
            var dto = new ChannelDTO(channel);
            dto.setIsMember(true);
            dto.setNumberOfMembers(getMemberCount(channel.getId()));
            return dto;
        }).toList();

        var allConversations = new ArrayList<ConversationDTO>();
        allConversations.addAll(activeGroupChatsOfUser);
        allConversations.addAll(channelsOfUser);

        return allConversations;
    }

    public Conversation updateConversation(Conversation conversation) {
        return conversationRepository.save(conversation);
    }

    public void broadcastOnConversationMembershipChannel(Course course, ConversationWebsocketDTO conversationWebsocketDTO, Set<User> usersToMessage) {
        String courseTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + course.getId();
        String conversationParticipantTopicName = courseTopicName + "/conversations/user/";
        usersToMessage.forEach(user -> sendToConversationMembershipChannel(conversationWebsocketDTO, user, conversationParticipantTopicName));
    }

    private void sendToConversationMembershipChannel(ConversationWebsocketDTO conversationWebsocketDTO, User user, String conversationParticipantTopicName) {
        messagingTemplate.convertAndSendToUser(user.getLogin(), conversationParticipantTopicName + user.getId(), conversationWebsocketDTO);
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
