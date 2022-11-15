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
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

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

    public void registerUsersToChannel(Course course, Set<User> usersToRegister, Channel channel) {
        var existingParticipants = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToRegister.stream().map(User::getId).collect(Collectors.toSet()));
        var usersToRegisterWithoutExistingParticipants = usersToRegister.stream()
                .filter(user -> existingParticipants.stream().noneMatch(participant -> participant.getUser().getId().equals(user.getId()))).collect(Collectors.toSet());
        Set<ConversationParticipant> newConversationParticipants = new HashSet<>();
        for (User user : usersToRegisterWithoutExistingParticipants) {
            ConversationParticipant conversationParticipant = new ConversationParticipant();
            conversationParticipant.setUser(user);
            conversationParticipant.setConversation(channel);
            conversationParticipant.setIsAdmin(false);
            newConversationParticipants.add(conversationParticipant);
        }
        conversationParticipantRepository.saveAll(newConversationParticipants);

        conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, channel, usersToRegisterWithoutExistingParticipants);
        notifyChannelMembersAboutUpdate(channel);
    }

    public void deregisterUsersFromChannel(Course course, Set<User> usersToDeregister, Channel channel) {
        var participantsToRemove = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToDeregister.stream().map(User::getId).collect(Collectors.toSet()));
        var usersWithExistingParticipants = usersToDeregister.stream()
                .filter(user -> participantsToRemove.stream().anyMatch(participant -> participant.getUser().getId().equals(user.getId()))).collect(Collectors.toSet());
        conversationParticipantRepository.deleteAll(participantsToRemove);

        conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.DELETE, channel, usersWithExistingParticipants);
        notifyChannelMembersAboutUpdate(channel);
    }

    public Channel getChannelOrThrow(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BadRequestAlertException("Channel with id " + channelId + " does not exist", CHANNEL_ENTITY_NAME, "idnotfound"));
    }

    public void grantChannelAdmin(Channel channel, Set<User> usersToGrantChannelAdmin) {
        var matchingParticipants = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToGrantChannelAdmin.stream().map(User::getId).collect(Collectors.toSet()));
        for (ConversationParticipant conversationParticipant : matchingParticipants) {
            conversationParticipant.setIsAdmin(true);
        }
        conversationParticipantRepository.saveAll(matchingParticipants);
        notifyChannelMembersAboutUpdate(channel);
    }

    public void revokeChannelAdmin(Channel channel, Set<User> usersToRevokeChannelAdmin) {
        var matchingParticipants = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToRevokeChannelAdmin.stream().map(User::getId).collect(Collectors.toSet()));
        for (ConversationParticipant conversationParticipant : matchingParticipants) {
            conversationParticipant.setIsAdmin(false);
        }
        conversationParticipantRepository.saveAll(matchingParticipants);
        notifyChannelMembersAboutUpdate(channel);
    }

    public List<Channel> getChannels(Long courseId) {
        return channelRepository.findChannelsByCourseId(courseId);
    }

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
        notifyChannelMembersAboutUpdate(channel);
        return updatedChannel;
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
        var channel = getChannelOrThrow(channelId);
        if (channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(true);
        var updatedChannel = channelRepository.save(channel);
        notifyChannelMembersAboutUpdate(updatedChannel);
    }

    public void unarchiveChannel(Long channelId) {
        var channel = getChannelOrThrow(channelId);
        if (!channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(false);
        var updatedChannel = channelRepository.save(channel);
        notifyChannelMembersAboutUpdate(updatedChannel);
    }

    private void notifyChannelMembersAboutUpdate(Channel channel) {
        var usersToContact = conversationParticipantRepository.findConversationParticipantByConversationId(channel.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        conversationService.broadcastOnConversationMembershipChannel(channel.getCourse(), MetisCrudAction.UPDATE, channel, usersToContact);
    }

    public void deleteChannel(Channel channel) {
        this.conversationService.deleteConversation(channel);
    }

}
