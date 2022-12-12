package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Persistence;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.*;

@Service
public class ConversationDTOService {

    private final Logger log = LoggerFactory.getLogger(ConversationDTOService.class);

    private final UserRepository userRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    public ConversationDTOService(UserRepository userRepository, ConversationParticipantRepository conversationParticipantRepository,
            ChannelAuthorizationService channelAuthorizationService) {
        this.userRepository = userRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelAuthorizationService = channelAuthorizationService;
    }

    /**
     * Creates a ConversationDTO from a Conversation
     *
     * @param conversation   the conversation to create the DTO from
     * @param requestingUser the user requesting the DTO
     * @return the created ConversationDTO
     */
    public ConversationDTO convertToDTO(Conversation conversation, User requestingUser) {
        if (conversation instanceof Channel channel) {
            return convertChannelToDto(requestingUser, channel);
        }
        if (conversation instanceof OneToOneChat oneToOneChat) {
            return convertOneToOneChatToDto(requestingUser, oneToOneChat);
        }
        if (conversation instanceof GroupChat groupChat) {
            return convertGroupChatToDto(requestingUser, groupChat);
        }
        throw new IllegalArgumentException("Conversation type not supported");
    }

    /**
     * Creates a ConversationDTO from a Conversation
     * <p>
     * Does not set transient properties that require extra database queries
     *
     * @param conversation the conversation to create the DTO from
     * @return the created ConversationDTO
     */
    public ConversationDTO convertToDTOWithoutExtraDBCalls(Conversation conversation) {
        if (conversation instanceof Channel channel) {
            return new ChannelDTO(channel);
        }
        if (conversation instanceof OneToOneChat oneToOneChat) {
            return new OneToOneChatDTO(oneToOneChat);
        }
        if (conversation instanceof GroupChat groupChat) {
            return new GroupChatDTO(groupChat);
        }
        throw new IllegalArgumentException("Conversation type not supported");
    }

    /**
     * Creates a ChannelDTO from a Channel
     *
     * @param requestingUser the user requesting the DTO
     * @param channel        the channel to create the DTO from
     * @return the created ChannelDTO
     */
    @NotNull
    public ChannelDTO convertChannelToDto(User requestingUser, Channel channel) {
        var channelDTO = new ChannelDTO(channel);
        channelDTO.setIsChannelAdmin(channelAuthorizationService.isChannelAdmin(channel.getId(), requestingUser.getId()));
        channelDTO.setHasChannelAdminRights(channelAuthorizationService.hasChannelAdminRights(channel.getId(), requestingUser));
        var participantOptional = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), requestingUser.getId());
        channelDTO.setIsMember(participantOptional.isPresent());
        participantOptional.ifPresent(conversationParticipant -> channelDTO.setLastReadDate(conversationParticipant.getLastRead()));
        channelDTO.setIsFavorite(participantOptional.map(ConversationParticipant::getIsFavorite).orElse(false));
        channelDTO.setIsHidden(participantOptional.map(ConversationParticipant::getIsHidden).orElse(false));
        if (channel.getCreator() != null) {
            channelDTO.setIsCreator(channel.getCreator().getId().equals(requestingUser.getId()));
        }
        else {
            log.error("Unexpected Behaviour: Channel {} has no creator", channel.getId());
            channelDTO.setIsCreator(false);
        }
        channelDTO.setNumberOfMembers(conversationParticipantRepository.countByConversationId(channel.getId()));
        return channelDTO;
    }

    /**
     * Creates a OneToOneChatDTO from a OneToOneChat
     * w
     *
     * @param requestingUser the user requesting the DTO
     * @param oneToOneChat   the one to one chat to create the DTO from
     * @return the created OneToOneChatDTO
     */
    @NotNull
    public OneToOneChatDTO convertOneToOneChatToDto(User requestingUser, OneToOneChat oneToOneChat) {
        var course = oneToOneChat.getCourse();
        Set<ConversationParticipant> conversationParticipants = getConversationParticipants(oneToOneChat);
        var participantOfRequestingUser = conversationParticipants.stream()
                .filter(conversationParticipant -> conversationParticipant.getUser().getId().equals(requestingUser.getId())).findFirst();
        Set<ConversationUserDTO> chatParticipants = getChatParticipantDTOs(requestingUser, course, conversationParticipants);
        var oneToOneChatDTO = new OneToOneChatDTO(oneToOneChat);
        oneToOneChatDTO.setIsMember(participantOfRequestingUser.isPresent());
        participantOfRequestingUser.ifPresent(conversationParticipant -> oneToOneChatDTO.setLastReadDate(conversationParticipant.getLastRead()));
        participantOfRequestingUser.ifPresent(conversationParticipant -> oneToOneChatDTO.setIsFavorite(conversationParticipant.getIsFavorite()));
        participantOfRequestingUser.ifPresent(conversationParticipant -> oneToOneChatDTO.setIsHidden(conversationParticipant.getIsHidden()));
        oneToOneChatDTO.setMembers(chatParticipants);
        if (oneToOneChat.getCreator() != null) {
            oneToOneChatDTO.setIsCreator(oneToOneChat.getCreator().getId().equals(requestingUser.getId()));
        }
        else {
            log.warn("Unexpected Behaviour: OneToOneChat {} has no creator. Can happen with db entries before December 2022", oneToOneChat.getId());
            oneToOneChatDTO.setIsCreator(false);
        }
        oneToOneChatDTO.setNumberOfMembers(conversationParticipants.size());
        return oneToOneChatDTO;
    }

    /**
     * Creates a GroupChatDTO from a GroupChat
     *
     * @param requestingUser the user requesting the DTO
     * @param groupChat      the group chat to create the DTO from
     * @return the created GroupChatDTO
     */
    @NotNull
    public GroupChatDTO convertGroupChatToDto(User requestingUser, GroupChat groupChat) {
        var course = groupChat.getCourse();
        Set<ConversationParticipant> conversationParticipants = getConversationParticipants(groupChat);
        var participantOfRequestingUser = conversationParticipants.stream()
                .filter(conversationParticipant -> conversationParticipant.getUser().getId().equals(requestingUser.getId())).findFirst();
        Set<ConversationUserDTO> chatParticipants = getChatParticipantDTOs(requestingUser, course, conversationParticipants);
        var groupChatDTO = new GroupChatDTO(groupChat);
        groupChatDTO.setIsMember(participantOfRequestingUser.isPresent());
        participantOfRequestingUser.ifPresent(conversationParticipant -> groupChatDTO.setLastReadDate(conversationParticipant.getLastRead()));
        participantOfRequestingUser.ifPresent(conversationParticipant -> groupChatDTO.setIsFavorite(conversationParticipant.getIsFavorite()));
        participantOfRequestingUser.ifPresent(conversationParticipant -> groupChatDTO.setIsHidden(conversationParticipant.getIsHidden()));
        groupChatDTO.setMembers(chatParticipants);
        if (groupChat.getCreator() != null) {
            groupChatDTO.setIsCreator(groupChat.getCreator().getId().equals(requestingUser.getId()));
        }
        else {
            log.error("Unexpected Behaviour: GroupChat {} has no creator", groupChat.getId());
            groupChatDTO.setIsCreator(false);
        }
        groupChatDTO.setNumberOfMembers(conversationParticipants.size());
        return groupChatDTO;
    }

    @NotNull
    private Set<ConversationParticipant> getConversationParticipants(Conversation conversation) {
        Set<ConversationParticipant> conversationParticipants;
        var participantsInitialized = Persistence.getPersistenceUtil().isLoaded(conversation, "conversationParticipants") && conversation.getConversationParticipants() != null;
        if (participantsInitialized) {
            conversationParticipants = conversation.getConversationParticipants();
        }
        else {
            conversationParticipants = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId());
        }
        return conversationParticipants;
    }

    @NotNull
    private Set<ConversationUserDTO> getChatParticipantDTOs(User requestingUser, Course course, Set<ConversationParticipant> conversationParticipants) {
        return conversationParticipants.stream().map(ConversationParticipant::getUser).map(user -> {
            var userDTO = new ConversationUserDTO(user);
            userDTO.setIsRequestingUser(user.getId().equals(requestingUser.getId()));
            userDTO.setIsChannelAdmin(null); // not needed for one to one chats
            var userWithGroups = user;
            var groupsInitialized = Persistence.getPersistenceUtil().isLoaded(user, "groups") && user.getGroups() != null;
            if (!groupsInitialized) {
                userWithGroups = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(user.getId());
            }
            UserPublicInfoDTO.assignRoleProperties(course, userWithGroups, userDTO);
            return userDTO;
        }).collect(Collectors.toSet());
    }
}
