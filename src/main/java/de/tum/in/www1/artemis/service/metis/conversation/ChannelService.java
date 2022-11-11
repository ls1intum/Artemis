package de.tum.in.www1.artemis.service.metis.conversation;

import static javax.validation.Validation.buildDefaultValidatorFactory;

import java.util.List;

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

@Service
public class ChannelService {

    public static final String CHANNEL_ENTITY_NAME = "messages.channel";

    private static final String CHANNEL_NAME_REGEX = "^[a-z0-9-]{1}[a-z0-9-]{0,20}$";

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelRepository channelRepository;

    private final UserRepository userRepository;

    public ChannelService(ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository, UserRepository userRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
    }

    public Channel getChannelOrThrow(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BadRequestAlertException("Channel with id " + channelId + " does not exist", CHANNEL_ENTITY_NAME, "idnotfound"));
    }

    public List<Channel> getChannels(Long courseId) {
        return channelRepository.findChannelsByCourseId(courseId);
    }

    public Channel createChannel(Course course, Channel channel) {
        if (channel.getId() != null) {
            throw new BadRequestAlertException("A new channel cannot already have an ID", "channel", "idexists");
        }
        if (StringUtils.hasText(channel.getName())) {
            channel.setName(StringUtils.trimAllWhitespace(channel.getName().toLowerCase()));
        }
        this.channelIsValidOrThrow(course.getId(), channel);
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();
        channel.setCreator(user);
        channel.setCourse(course);
        var savedChannel = channelRepository.save(channel);
        var conversationParticipantOfRequestingUser = new ConversationParticipant();
        conversationParticipantOfRequestingUser.setUser(user);
        conversationParticipantOfRequestingUser.setConversation(savedChannel);
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
        channelRepository.findChannelByCourseIdAndName(courseId, channel.getName()).ifPresent(existingChannel -> {
            throw new ChannelNameDuplicateException(existingChannel.getName());
        });
    }

}
