package de.tum.in.www1.artemis.service.metis.conversation;

import static javax.validation.Validation.buildDefaultValidatorFactory;

import java.util.*;
import java.util.stream.Collectors;

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

    public ChannelService(ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository, UserRepository userRepository,
            ConversationService conversationService) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
    }

    /**
     * Gets the channel with the given id or throws a not found exception
     *
     * @param channelId the id of the channel
     * @return the channel with the given id
     */
    public Channel getChannelOrThrow(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BadRequestAlertException("Channel with id " + channelId + " does not exist", CHANNEL_ENTITY_NAME, "idnotfound"));
    }

    /**
     * Grans the channel admin role to the given user for the given channel
     *
     * @param channel                  the channel
     * @param usersToGrantChannelAdmin the users to grant channel admin
     */
    public void grantChannelAdmin(Channel channel, Set<User> usersToGrantChannelAdmin) {
        var matchingParticipants = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToGrantChannelAdmin.stream().map(User::getId).collect(Collectors.toSet()));
        for (ConversationParticipant conversationParticipant : matchingParticipants) {
            conversationParticipant.setIsAdmin(true);
        }
        conversationParticipantRepository.saveAll(matchingParticipants);
        conversationService.notifyConversationMembersAboutUpdate(channel);
    }

    /**
     * Removes the channel admin role from a user for the given channel
     *
     * @param channel                   the channel to remove the channel admin role from
     * @param usersToRevokeChannelAdmin the users to revoke channel admin
     */
    public void revokeChannelAdmin(Channel channel, Set<User> usersToRevokeChannelAdmin) {
        var matchingParticipants = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToRevokeChannelAdmin.stream().map(User::getId).collect(Collectors.toSet()));
        for (ConversationParticipant conversationParticipant : matchingParticipants) {
            conversationParticipant.setIsAdmin(false);
        }
        conversationParticipantRepository.saveAll(matchingParticipants);
        conversationService.notifyConversationMembersAboutUpdate(channel);
    }

    /**
     * Get all channels for the given course
     *
     * @param courseId the id of the course
     * @return a list of channels for the given course
     */
    public List<Channel> getChannels(Long courseId) {
        return channelRepository.findChannelsByCourseId(courseId);
    }

    /**
     * Updates the given channel
     *
     * @param channelId  the id of the channel to update
     * @param courseId   the id of the course the channel belongs to
     * @param channelDTO the dto containing the new channel data
     * @return the updated channel
     */
    public Channel updateChannel(Long channelId, Long courseId, ChannelDTO channelDTO) {
        var channel = getChannelOrThrow(channelId);
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

        var updatedChannel = channelRepository.save(channel);
        conversationService.notifyConversationMembersAboutUpdate(updatedChannel);
        return updatedChannel;
    }

    /**
     * Creates a new channel for the given course
     *
     * @param course  the course to create the channel for
     * @param channel the channel to create
     * @return the created channel
     */
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

    /**
     * Checks if the given channel is valid for the given course or throws an exception
     *
     * @param courseId the id of the course
     * @param channel  the channel to check
     */
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

    /**
     * Archive the channel with the given id
     *
     * @param channelId the id of the channel to archive
     */
    public void archiveChannel(Long channelId) {
        var channel = getChannelOrThrow(channelId);
        if (channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(true);
        var updatedChannel = channelRepository.save(channel);
        conversationService.notifyConversationMembersAboutUpdate(updatedChannel);
    }

    /**
     * Unarchive the channel with the given id
     *
     * @param channelId the id of the archived channel to unarchive
     */
    public void unarchiveChannel(Long channelId) {
        var channel = getChannelOrThrow(channelId);
        if (!channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(false);
        var updatedChannel = channelRepository.save(channel);
        conversationService.notifyConversationMembersAboutUpdate(updatedChannel);
    }

    /**
     * Delete the channel with the given id
     *
     * @param channel the channel to delete
     */
    public void deleteChannel(Channel channel) {
        this.conversationService.deleteConversation(channel);
    }

}
