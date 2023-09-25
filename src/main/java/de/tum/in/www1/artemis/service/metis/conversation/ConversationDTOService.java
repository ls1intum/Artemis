package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Persistence;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipantSettingsView;
import de.tum.in.www1.artemis.domain.metis.conversation.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.*;

@Service
public class ConversationDTOService {

    private final UserRepository userRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    public ConversationDTOService(UserRepository userRepository, ConversationParticipantRepository conversationParticipantRepository,
            ChannelAuthorizationService channelAuthorizationService, TutorialGroupRepository tutorialGroupRepository, CourseRepository courseRepository) {
        this.userRepository = userRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelAuthorizationService = channelAuthorizationService;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.courseRepository = courseRepository;
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
     *
     * @param summary        the conversation summary to create the DTO from
     * @param requestingUser the user requesting the DTO
     * @return the created ConversationDTO
     */
    public ConversationDTO convertToDTO(ConversationSummary summary, User requestingUser) {
        if (summary.conversation() instanceof Channel channel) {
            return convertChannelToDto(requestingUser, channel, summary);
        }
        if (summary.conversation() instanceof OneToOneChat oneToOneChat) {
            return convertOneToOneChatToDto(requestingUser, oneToOneChat, summary);
        }
        if (summary.conversation() instanceof GroupChat groupChat) {
            return convertGroupChatToDto(requestingUser, groupChat, summary);
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
    public ConversationDTO convertToDTOWithNoExtraDBCalls(Conversation conversation) {
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
        channelDTO.setIsChannelModerator(channelAuthorizationService.isChannelModerator(channel.getId(), requestingUser.getId()));
        channelDTO.setHasChannelModerationRights(channelAuthorizationService.hasChannelModerationRights(channel.getId(), requestingUser));
        var participantOptional = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), requestingUser.getId());
        setDTOPropertiesBasedOnParticipant(channelDTO, participantOptional);
        channelDTO.setIsMember(channelDTO.getIsMember() || channel.getIsCourseWide());
        setDTOCreatorProperty(requestingUser, channel, channelDTO);
        channelDTO.setNumberOfMembers(channel.getIsCourseWide() ? courseRepository.countCourseMembers(channel.getCourse().getId())
                : conversationParticipantRepository.countByConversationId(channel.getId()));
        var tutorialGroup = tutorialGroupRepository.findByTutorialGroupChannelId(channel.getId());
        tutorialGroup.ifPresent(tg -> {
            channelDTO.setTutorialGroupId(tg.getId());
            channelDTO.setTutorialGroupTitle(tg.getTitle());
        });
        return channelDTO;
    }

    /**
     * Creates a ChannelDTO from a Channel
     *
     * @param requestingUser the user requesting the DTO
     * @param channel        the channel to create the DTO from
     * @param channelSummary additional data about the channel
     * @return the created ChannelDTO
     */
    @NotNull
    private ChannelDTO convertChannelToDto(User requestingUser, Channel channel, ConversationSummary channelSummary) {
        var channelDTO = new ChannelDTO(channel);
        this.fillGeneralConversationDtoFields(channelDTO, requestingUser, channelSummary);

        // channel-DTO specific fields
        var participantOptional = Optional.ofNullable(channelSummary.userConversationInfo().getConversationParticipantSettingsView());
        channelDTO.setIsChannelModerator(participantOptional.map(ConversationParticipantSettingsView::isModerator).orElse(false));

        channelDTO.setIsMember(channelAuthorizationService.isMember(channel, participantOptional));
        channelDTO.setHasChannelModerationRights(channelAuthorizationService.hasChannelModerationRights(channel, requestingUser, participantOptional));

        var tutorialGroup = tutorialGroupRepository.findByTutorialGroupChannelId(channel.getId());
        tutorialGroup.ifPresent(tg -> {
            channelDTO.setTutorialGroupId(tg.getId());
            channelDTO.setTutorialGroupTitle(tg.getTitle());
        });

        return channelDTO;
    }

    /**
     * Creates a OneToOneChatDTO from a OneToOneChat
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
        setDTOPropertiesBasedOnParticipant(oneToOneChatDTO, participantOfRequestingUser);
        setDTOCreatorProperty(requestingUser, oneToOneChat, oneToOneChatDTO);
        oneToOneChatDTO.setMembers(chatParticipants);
        oneToOneChatDTO.setNumberOfMembers(conversationParticipants.size());
        return oneToOneChatDTO;
    }

    /**
     * Creates a OneToOneChatDTO from a OneToOneChat
     *
     * @param requestingUser      the user requesting the DTO
     * @param oneToOneChat        the one to one chat to create the DTO from
     * @param oneToOneChatSummary additional data about the oneToOneChat
     * @return the created OneToOneChatDTO
     */
    @NotNull
    private OneToOneChatDTO convertOneToOneChatToDto(User requestingUser, OneToOneChat oneToOneChat, ConversationSummary oneToOneChatSummary) {
        var oneToOneChatDTO = new OneToOneChatDTO(oneToOneChat);
        this.fillGeneralConversationDtoFields(oneToOneChatDTO, requestingUser, oneToOneChatSummary);

        // oneToOneChat-DTO specific fields
        Set<ConversationUserDTO> chatParticipants = getChatParticipantDTOs(requestingUser, oneToOneChat.getCourse(), getConversationParticipants(oneToOneChat));
        oneToOneChatDTO.setMembers(chatParticipants);
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
        setDTOPropertiesBasedOnParticipant(groupChatDTO, participantOfRequestingUser);
        setDTOCreatorProperty(requestingUser, groupChat, groupChatDTO);
        groupChatDTO.setMembers(chatParticipants);
        groupChatDTO.setNumberOfMembers(conversationParticipants.size());
        return groupChatDTO;
    }

    /**
     * Creates a GroupChatDTO from a GroupChat
     *
     * @param requestingUser   the user requesting the DTO
     * @param groupChat        the group chat to create the DTO from
     * @param groupChatSummary additional data about the groupChat
     * @return the created GroupChatDTO
     */
    @NotNull
    private GroupChatDTO convertGroupChatToDto(User requestingUser, GroupChat groupChat, ConversationSummary groupChatSummary) {
        var groupChatDTO = new GroupChatDTO(groupChat);
        this.fillGeneralConversationDtoFields(groupChatDTO, requestingUser, groupChatSummary);

        // groupChat-DTO specific fields
        Set<ConversationUserDTO> chatParticipants = getChatParticipantDTOs(requestingUser, groupChat.getCourse(), getConversationParticipants(groupChat));
        groupChatDTO.setMembers(chatParticipants);
        return groupChatDTO;
    }

    @NotNull
    private Set<ConversationParticipant> getConversationParticipants(Conversation conversation) {
        Set<ConversationParticipant> conversationParticipants;
        var participantsInitialized = Persistence.getPersistenceUtil().isLoaded(conversation, "conversationParticipants") && conversation.getConversationParticipants() != null
                && conversation.getConversationParticipants().size() > 0;
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
            userDTO.setIsChannelModerator(null); // not needed for one to one chats
            var userWithGroups = user;
            var groupsInitialized = Persistence.getPersistenceUtil().isLoaded(user, "groups") && user.getGroups() != null;
            if (!groupsInitialized) {
                userWithGroups = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(user.getId());
            }
            UserPublicInfoDTO.assignRoleProperties(course, userWithGroups, userDTO);
            return userDTO;
        }).collect(Collectors.toSet());
    }

    private void setDTOPropertiesBasedOnParticipant(ConversationDTO conversationDTO, Optional<ConversationParticipant> participantOptional) {
        conversationDTO.setIsMember(participantOptional.isPresent());
        participantOptional.ifPresent(participant -> {
            conversationDTO.setLastReadDate(participant.getLastRead());
            conversationDTO.setUnreadMessagesCount(participant.getUnreadMessagesCount());
        });
        conversationDTO.setIsFavorite(participantOptional.map(ConversationParticipant::getIsFavorite).orElse(false));
        conversationDTO.setIsHidden(participantOptional.map(ConversationParticipant::getIsHidden).orElse(false));
    }

    private void setDTOCreatorProperty(User requestingUser, Conversation conversation, ConversationDTO conversationDTO) {
        if (conversation.getCreator() != null) {
            conversationDTO.setIsCreator(conversation.getCreator().getId().equals(requestingUser.getId()));
        }
        else {
            // Is the case for conversations created by the system such as tutorial group channels
            conversationDTO.setIsCreator(false);
        }
    }

    private void fillGeneralConversationDtoFields(ConversationDTO conversationDTO, User requestingUser, ConversationSummary conversationSummary) {
        var participantOptional = Optional.ofNullable(conversationSummary.userConversationInfo().getConversationParticipantSettingsView());

        conversationDTO.setIsMember(participantOptional.isPresent());
        conversationDTO.setIsFavorite(participantOptional.map(ConversationParticipantSettingsView::isFavorite).orElse(false));
        conversationDTO.setIsHidden(participantOptional.map(ConversationParticipantSettingsView::isHidden).orElse(false));
        participantOptional.ifPresent(participant -> conversationDTO.setLastReadDate(participant.lastRead()));

        conversationDTO.setUnreadMessagesCount(conversationSummary.userConversationInfo().getUnreadMessagesCount());
        conversationDTO.setNumberOfMembers(conversationSummary.generalConversationInfo().getNumberOfParticipants());

        setDTOCreatorProperty(requestingUser, conversationSummary.conversation(), conversationDTO);
    }
}
