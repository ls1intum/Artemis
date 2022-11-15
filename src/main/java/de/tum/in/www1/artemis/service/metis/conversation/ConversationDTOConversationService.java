package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Persistence;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationUserDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.OneToOneChatDTO;

@Service
public class ConversationDTOConversationService {

    private final UserRepository userRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    public ConversationDTOConversationService(UserRepository userRepository, ConversationParticipantRepository conversationParticipantRepository,
            ChannelAuthorizationService channelAuthorizationService) {
        this.userRepository = userRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelAuthorizationService = channelAuthorizationService;
    }

    public ConversationDTO convertToDto(Conversation conversation, User requestingUser) {
        if (conversation instanceof Channel channel) {
            return convertChannelToDto(requestingUser, channel);
        }
        if (conversation instanceof OneToOneChat oneToOneChat) {
            return convertOneToOneChatToDto(requestingUser, oneToOneChat);
        }
        throw new IllegalArgumentException("Conversation type not supported");
    }

    @NotNull
    private ConversationDTO convertChannelToDto(User requestingUser, Channel channel) {
        var channelDTO = new ChannelDTO(channel);
        channelDTO.setIsChannelAdmin(channelAuthorizationService.isChannelAdmin(channel.getId(), requestingUser.getId()));
        channelDTO.setHasChannelAdminRights(channelAuthorizationService.hasChannelAdminRights(channel.getId(), requestingUser));
        channelDTO.setIsMember(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), requestingUser.getId()).isPresent());
        channelDTO.setIsCreator(channel.getCreator().getId().equals(requestingUser.getId()));
        channelDTO.setNumberOfMembers(conversationParticipantRepository.countByConversationId(channel.getId()));
        return channelDTO;
    }

    @NotNull
    private OneToOneChatDTO convertOneToOneChatToDto(User requestingUser, OneToOneChat oneToOneChat) {
        var course = oneToOneChat.getCourse();
        Set<ConversationParticipant> conversationParticipants = getConversationParticipants(oneToOneChat);
        Set<ConversationUserDTO> chatParticipants = getChatParticipantDTOs(requestingUser, course, conversationParticipants);
        var oneToOneChatDTO = new OneToOneChatDTO(oneToOneChat);
        oneToOneChatDTO
                .setIsMember(conversationParticipants.stream().anyMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(requestingUser.getId())));
        oneToOneChatDTO.setMembers(chatParticipants);
        oneToOneChatDTO.setIsCreator(oneToOneChat.getCreator().getId().equals(requestingUser.getId()));
        oneToOneChatDTO.setNumberOfMembers(conversationParticipants.size());
        return oneToOneChatDTO;
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
