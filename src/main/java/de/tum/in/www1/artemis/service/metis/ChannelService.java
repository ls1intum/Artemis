package de.tum.in.www1.artemis.service.metis;

import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.Persistence;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.errors.ChannelNameDuplicateException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class ChannelService {

    private static final String CHANNEL_NAME_REGEX = "^[a-z0-9-]{1}[a-z0-9-]{0,20}$";

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelRepository channelRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public ChannelService(ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    public record ChannelOverviewDTO(Long channelId, String channelName, String channelDescription, Boolean isPublic, boolean isMember, int noOfMembers) {
    }

    public List<Conversation> getChannels(Long courseId) {
        return channelRepository.findChannelsWithConversationParticipantsByCourseId(courseId);
    }

    public Conversation getChannelElseThrow(Long channelId) {
        return channelRepository.findChannelWithConversationParticipantsByIdElseThrow(channelId);
    }

    public void isAllowedToCreateChannelElseThrow(@NotNull Course course, @Nullable User user) {
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (user == null || !persistenceUtil.isLoaded(user, "authorities") || !persistenceUtil.isLoaded(user, "groups") || user.getGroups() == null
                || user.getAuthorities() == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        // ToDo: Discuss who else should be allowed to create channels
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    public Conversation createChannel(Long courseId, Conversation channel) {
        if (!channel.isChannel()) {
            throw new IllegalArgumentException("Only channels can be created with this method");
        }
        if (StringUtils.hasText(channel.getName())) {
            channel.setName(StringUtils.trimAllWhitespace(channel.getName().toLowerCase()));
        }
        this.channelIsValidOrThrow(courseId, channel);

        final User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (channel.getId() != null) {
            throw new BadRequestAlertException("A new channel cannot already have an ID", "channel", "idexists");
        }

        final Course course = checkUserAndCourse(user, courseId);
        channel.setCourse(course);

        var savedChannel = channelRepository.save(channel);
        var conversationParticipant = new ConversationParticipant();
        conversationParticipant.setUser(user);
        conversationParticipant.setConversation(savedChannel);
        conversationParticipant = conversationParticipantRepository.save(conversationParticipant);
        savedChannel.getConversationParticipants().add(conversationParticipant);
        savedChannel = channelRepository.save(savedChannel);
        return savedChannel;
    }

    Course checkUserAndCourse(User user, Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to at least have student role in the course
        isAllowedToCreateChannelElseThrow(course, user);

        return course;
    }

    public void channelIsValidOrThrow(Long courseId, Conversation channel) {
        if (!channel.isChannel()) {
            throw new IllegalArgumentException("Only channels can be checked with this method");
        }
        if (!StringUtils.hasText(channel.getName())) {
            throw new BadRequestAlertException("A channel needs to have a name", "channel", "namemissing");
        }
        if (!channel.getName().matches(CHANNEL_NAME_REGEX)) {
            throw new BadRequestAlertException("Channel names can only contain lowercase letters, numbers, and dashes.", "channel", "nameinvalid");
        }
        channelRepository.findChannelByCourseIdAndName(courseId, channel.getName()).ifPresent(existingChannel -> {
            throw new ChannelNameDuplicateException(existingChannel.getName());
        });
    }

}
