package de.tum.in.www1.artemis.service.metis.conversation;

import static javax.validation.Validation.buildDefaultValidatorFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.service.metis.conversation.errors.ChannelNameDuplicateException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;

@Service
public class ChannelService {

    public static final String CHANNEL_ENTITY_NAME = "messages.channel";

    private static final String CHANNEL_NAME_REGEX = "^[a-z0-9-]{1}[a-z0-9-]{0,20}$";

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelRepository channelRepository;

    private final UserRepository userRepository;

    private final ConversationService conversationService;

    private final ChannelAuthorizationService channelAuthorizationService;

    public ChannelService(ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository, UserRepository userRepository,
            ConversationService conversationService, ChannelAuthorizationService channelAuthorizationService) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.channelAuthorizationService = channelAuthorizationService;
    }

    public ChannelDTO convertToDTO(Channel channel, @Nullable User requestingUser) {
        if (requestingUser == null) {
            requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        }
        var dto = new ChannelDTO(channel);
        dto.setIsMember(conversationService.isMember(channel.getId(), requestingUser.getId()));
        if (channel.getCreator() != null) {
            dto.setIsCreator(channel.getCreator().getId().equals(channel.getId()));
        }
        else {
            dto.setIsCreator(false);
        }
        dto.setNumberOfMembers(conversationService.getMemberCount(channel.getId()));
        dto.setIsChannelAdmin(channelAuthorizationService.isChannelAdmin(channel.getId(), requestingUser.getId()));
        dto.setHasChannelAdminRights(channelAuthorizationService.hasChannelAdminRights(channel.getId(), requestingUser));
        return dto;
    }

    public Channel getChannelOrThrow(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BadRequestAlertException("Channel with id " + channelId + " does not exist", CHANNEL_ENTITY_NAME, "idnotfound"));
    }

    public Channel getChannelWithParticipantsOrThrow(Long channelId) {
        return channelRepository.findChannelByIdWithEagerParticipants(channelId)
                .orElseThrow(() -> new BadRequestAlertException("Channel with id " + channelId + " does not exist", CHANNEL_ENTITY_NAME, "idnotfound"));
    }

    public void grantChannelAdmin(Long channelId, Set<User> usersToGrantChannelAdmin) {
        var channelOptional = channelRepository.findChannelByIdWithEagerParticipants(channelId);
        if (channelOptional.isEmpty()) {
            throw new BadRequestAlertException("Channel with id " + channelId + " does not exist", CHANNEL_ENTITY_NAME, "idnotfound");
        }
        var channel = channelOptional.get();
        channel.getConversationParticipants().forEach(participant -> {
            if (usersToGrantChannelAdmin.contains(participant.getUser()) && (participant.getIsAdmin() != null && !participant.getIsAdmin())) {
                participant.setIsAdmin(true);
            }
        });
        channelRepository.save(channel);
    }

    public void revokeChannelAdmin(Long channelId, Set<User> usersToRevokeChannelAdmin) {
        var channelOptional = channelRepository.findChannelByIdWithEagerParticipants(channelId);
        if (channelOptional.isEmpty()) {
            throw new BadRequestAlertException("Channel with id " + channelId + " does not exist", CHANNEL_ENTITY_NAME, "idnotfound");
        }
        var channel = channelOptional.get();
        channel.getConversationParticipants().forEach(participant -> {
            if (usersToRevokeChannelAdmin.contains(participant.getUser()) && (participant.getIsAdmin() != null && participant.getIsAdmin())) {
                participant.setIsAdmin(false);
            }
        });
        channelRepository.save(channel);
    }

    public List<Channel> getChannels(Long courseId) {
        return channelRepository.findChannelsByCourseId(courseId);
    }

    public Channel updateChannel(Long channelId, Long courseId, ChannelDTO channelDTO) {
        var channel = getChannelWithParticipantsOrThrow(channelId);
        if (channelDTO.getName() != null && !channelDTO.getName().equals(channel.getName())) {
            channel.setName(channelDTO.getName().trim().isBlank() ? null : channelDTO.getName().trim());
        }
        if (channelDTO.getDescription() != null && !channelDTO.getDescription().equals(channel.getDescription())) {
            channel.setDescription(channelDTO.getDescription().trim().isBlank() ? null : channelDTO.getDescription().trim());
        }
        if (channelDTO.getTopic() != null && !channelDTO.getTopic().equals(channel.getTopic())) {
            channel.setTopic(channelDTO.getTopic().trim().isBlank() ? null : channelDTO.getTopic().trim());
        }
        this.channelIsValidOrThrow(courseId, channel);

        return (Channel) conversationService.updateConversation(channel);
    }

    public Channel createChannel(Course course, Channel channel) {
        if (channel.getId() != null) {
            throw new BadRequestAlertException("A new channel cannot already have an ID", "channel", "idexists");
        }
        if (StringUtils.hasText(channel.getName())) {
            channel.setName(StringUtils.trimAllWhitespace(channel.getName().toLowerCase()));
        }
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();
        channel.setCreator(user);
        channel.setCourse(course);
        channel.setIsArchived(false);
        this.channelIsValidOrThrow(course.getId(), channel);
        var savedChannel = channelRepository.save(channel);
        var conversationParticipantOfRequestingUser = new ConversationParticipant();
        conversationParticipantOfRequestingUser.setUser(user);
        conversationParticipantOfRequestingUser.setConversation(savedChannel);
        conversationParticipantOfRequestingUser.setIsAdmin(true); // creator is of course admin. Special case, because creator is the only admin that can not be removed
        conversationParticipantOfRequestingUser = conversationParticipantRepository.save(conversationParticipantOfRequestingUser);
        savedChannel.getConversationParticipants().add(conversationParticipantOfRequestingUser);
        savedChannel = channelRepository.save(savedChannel);
        return savedChannel;
    }

    public void channelIsValidOrThrow(Long courseId, Channel channel) {
        var validator = buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(channel);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        if (!StringUtils.hasText(channel.getName())) {
            throw new BadRequestAlertException("A channel needs to have a name", CHANNEL_ENTITY_NAME, "nameMissing");
        }
        if (!channel.getName().matches(CHANNEL_NAME_REGEX)) {
            throw new BadRequestAlertException("Channel names can only contain lowercase letters, numbers, and dashes.", CHANNEL_ENTITY_NAME, "namePatternInvalid");
        }
        Optional<Channel> channelWithSameName;
        if (channel.getId() != null) {
            channelWithSameName = channelRepository.findChannelByCourseIdAndNameAndIdNot(courseId, channel.getName(), channel.getId());
        }
        else {
            channelWithSameName = channelRepository.findChannelByCourseIdAndName(courseId, channel.getName());
        }
        channelWithSameName.ifPresent(existingChannel -> {
            throw new ChannelNameDuplicateException(existingChannel.getName());
        });
    }

    public void archiveChannel(Long channelId) {
        var channel = getChannelWithParticipantsOrThrow(channelId);
        if (channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(true);
        conversationService.updateConversation(channel);
    }

    public void unarchiveChannel(Long channelId) {
        var channel = getChannelWithParticipantsOrThrow(channelId);
        if (!channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(false);
        conversationService.updateConversation(channel);
    }

    public void deleteChannel(Long channelId) {
        this.conversationService.deleteConversation(channelId);
    }

}
