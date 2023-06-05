package de.tum.in.www1.artemis.service.metis.conversation;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DefaultChannelType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.metis.conversation.errors.ChannelNameDuplicateException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

@Service
public class ChannelService {

    public static final String CHANNEL_ENTITY_NAME = "messages.channel";

    private static final String CHANNEL_NAME_REGEX = "^[a-z0-9$][a-z0-9-]{0,30}$";

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelRepository channelRepository;

    private final ConversationService conversationService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public ChannelService(ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository, ConversationService conversationService,
            CourseRepository courseRepository, UserRepository userRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelRepository = channelRepository;
        this.conversationService = conversationService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Grants the channel moderator role to the given user for the given channel
     *
     * @param channel      the channel
     * @param usersToGrant the users to grant the channel moderator role
     */
    public void grantChannelModeratorRole(Channel channel, Set<User> usersToGrant) {
        var matchingParticipants = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToGrant.stream().map(User::getId).collect(Collectors.toSet()));
        for (ConversationParticipant conversationParticipant : matchingParticipants) {
            conversationParticipant.setIsModerator(true);
        }
        conversationParticipantRepository.saveAll(matchingParticipants);
        conversationService.notifyAllConversationMembersAboutUpdate(channel);
    }

    /**
     * Revokes the channel moderator role from a user for the given channel
     *
     * @param channel       the channel
     * @param usersToRevoke the users to revoke channel moderator role from
     */
    public void revokeChannelModeratorRole(Channel channel, Set<User> usersToRevoke) {
        var matchingParticipants = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(channel.getId(),
                usersToRevoke.stream().map(User::getId).collect(Collectors.toSet()));
        for (ConversationParticipant conversationParticipant : matchingParticipants) {
            conversationParticipant.setIsModerator(false);
        }
        conversationParticipantRepository.saveAll(matchingParticipants);
        conversationService.notifyAllConversationMembersAboutUpdate(channel);
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
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (channelDTO.getName() != null && !channelDTO.getName().equals(channel.getName())) {
            channel.setName(channelDTO.getName().trim());
        }
        if (channelDTO.getDescription() != null && !channelDTO.getDescription().equals(channel.getDescription())) {
            channel.setDescription(channelDTO.getDescription().trim());
        }
        if (channelDTO.getTopic() != null && !channelDTO.getTopic().equals(channel.getTopic())) {
            channel.setTopic(channelDTO.getTopic().trim());
        }
        this.channelIsValidOrThrow(courseId, channel);

        var updatedChannel = channelRepository.save(channel);
        conversationService.notifyAllConversationMembersAboutUpdate(updatedChannel);
        return updatedChannel;
    }

    /**
     * Creates a new channel for the given course
     *
     * @param course  the course to create the channel for
     * @param channel the channel to create
     * @param creator the creator of the channel, if set a participant will be created for the creator
     * @return the created channel
     */
    public Channel createChannel(Course course, Channel channel, Optional<User> creator) {
        if (StringUtils.hasText(channel.getName())) {
            channel.setName(StringUtils.trimAllWhitespace(channel.getName().toLowerCase()));
        }
        channel.setCreator(creator.orElse(null));
        channel.setCourse(course);
        channel.setIsArchived(false);
        this.channelIsValidOrThrow(course.getId(), channel);
        var savedChannel = channelRepository.save(channel);

        if (creator.isPresent()) {
            var conversationParticipantOfRequestingUser = new ConversationParticipant();
            // set the last reading time of a participant in the past when creating conversation for the first time!
            conversationParticipantOfRequestingUser.setLastRead(ZonedDateTime.now().minusYears(2));
            conversationParticipantOfRequestingUser.setUnreadMessagesCount(0L);
            conversationParticipantOfRequestingUser.setUser(creator.get());
            conversationParticipantOfRequestingUser.setConversation(savedChannel);
            // Creator is a moderator. Special case, because creator is the only moderator that can not be revoked the role
            conversationParticipantOfRequestingUser.setIsModerator(true);
            conversationParticipantOfRequestingUser = conversationParticipantRepository.save(conversationParticipantOfRequestingUser);
            savedChannel.getConversationParticipants().add(conversationParticipantOfRequestingUser);
            savedChannel = channelRepository.save(savedChannel);
            conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, savedChannel, Set.of(creator.get()));
        }
        return savedChannel;
    }

    /**
     * Register users to the newly created channel
     *
     * @param addAllStudents        if true, all students of the course will be added to the channel
     * @param addAllTutors          if true, all tutors of the course will be added to the channel
     * @param addAllInstructors     if true, all instructors of the course will be added to the channel
     * @param usersLoginsToRegister the logins of the users to register to the channel
     * @param course                the course to create the channel for
     * @param channel               the channel to create
     * @return all users that were registered to the channel
     */
    public Set<User> registerUsersToChannel(boolean addAllStudents, boolean addAllTutors, boolean addAllInstructors, List<String> usersLoginsToRegister, Course course,
            Channel channel) {
        Set<User> usersToRegister = new HashSet<>();
        usersToRegister.addAll(conversationService.findUsersInDatabase(course, addAllStudents, addAllTutors, addAllInstructors));
        usersToRegister.addAll(conversationService.findUsersInDatabase(usersLoginsToRegister));
        conversationService.registerUsersToConversation(course, usersToRegister, channel, Optional.empty());
        return usersToRegister;
    }

    /**
     * Add user to default channels of courses with the same group asynchronously. This is used when a user is added to a group.
     *
     * @param userToAddToGroup the user to be added
     * @param group            the group of the user
     * @param role             the role of the user
     */
    @Async
    public void registerUserToDefaultChannels(User userToAddToGroup, String group, Role role) {
        final Set<String> channelNames = Arrays.stream(DefaultChannelType.values()).map(DefaultChannelType::getName).collect(Collectors.toSet());

        List<Course> courses = switch (role) {
            case STUDENT -> courseRepository.findCoursesByStudentGroupName(group);
            case TEACHING_ASSISTANT -> courseRepository.findCoursesByTeachingAssistantGroupName(group);
            case INSTRUCTOR -> courseRepository.findCoursesByInstructorGroupName(group);
            default -> List.of();
        };

        for (Course c : courses) {
            // set the security context because the async methods use multiple threads
            SecurityUtils.setAuthorizationObject();
            channelRepository.findChannelsByCourseId(c.getId()).stream().filter(channel -> channelNames.contains(channel.getName())).forEach(channel -> {
                conversationService.registerUsersToConversation(c, Set.of(userToAddToGroup), channel, Optional.empty());
            });
        }
    }

    /**
     * Checks if the given channel is valid for the given course or throws an exception
     *
     * @param courseId the id of the course
     * @param channel  the channel to check
     */
    public void channelIsValidOrThrow(Long courseId, @Valid Channel channel) {
        if (channel.getName() != null && !channel.getName().matches(CHANNEL_NAME_REGEX)) {
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
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(true);
        var updatedChannel = channelRepository.save(channel);
        conversationService.notifyAllConversationMembersAboutUpdate(updatedChannel);
    }

    /**
     * Unarchive the channel with the given id
     *
     * @param channelId the id of the archived channel to unarchive
     */
    public void unarchiveChannel(Long channelId) {
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (!channel.getIsArchived()) {
            return;
        }
        channel.setIsArchived(false);
        var updatedChannel = channelRepository.save(channel);
        conversationService.notifyAllConversationMembersAboutUpdate(updatedChannel);
    }

    /**
     * Create a channel for a lecture
     *
     * @param lecture     the lecture to create the channel for
     * @param channelName the name of the channel
     * @return the created channel
     */
    public Channel createLectureChannel(Lecture lecture, @NotNull String channelName) {
        Channel channelToCreate = new Channel();
        channelToCreate.setName(channelName);
        channelToCreate.setIsPublic(true);
        channelToCreate.setIsAnnouncementChannel(false);
        channelToCreate.setIsArchived(false);
        channelToCreate.setLecture(lecture);
        return createChannel(lecture.getCourse(), channelToCreate, Optional.of(userRepository.getUserWithGroupsAndAuthorities()));
    }

    /**
     * Creates a channel for a course exercise and sets the channel name of the exercise accordingly
     *
     * @param exercise    the exercise to create the channel for
     * @param channelName the name of the channel
     * @return the created channel
     */
    public Channel createExerciseChannel(Exercise exercise, @NotNull String channelName) {
        if (!exercise.isCourseExercise()) {
            return null;
        }

        Channel channelToCreate = new Channel();
        channelToCreate.setName(channelName);
        channelToCreate.setIsPublic(true);
        channelToCreate.setIsAnnouncementChannel(false);
        channelToCreate.setIsArchived(false);
        channelToCreate.setExercise(exercise);
        return createChannel(exercise.getCourseViaExerciseGroupOrCourseMember(), channelToCreate, Optional.of(userRepository.getUserWithGroupsAndAuthorities()));
    }

    /**
     * Create a channel for an exam
     *
     * @param exam        the exam to create the channel for
     * @param channelName the name of the channel
     * @return the created channel
     */
    public Channel createExamChannel(Exam exam, @NotNull String channelName) {
        if (exam.isTestExam()) {
            return null;
        }
        Channel channelToCreate = new Channel();
        channelToCreate.setName(channelName);
        channelToCreate.setIsPublic(false);
        channelToCreate.setIsAnnouncementChannel(false);
        channelToCreate.setIsArchived(false);
        channelToCreate.setExam(exam);
        return createChannel(exam.getCourse(), channelToCreate, Optional.of(userRepository.getUserWithGroupsAndAuthorities()));
    }

    /**
     * Update the channel of a lecture
     *
     * @param originalLecture the original lecture
     * @param channelName     the new channel name
     * @return the updated channel
     */
    public Channel updateLectureChannel(Lecture originalLecture, String channelName) {
        if (channelName == null) {
            return null;
        }
        Channel channel = channelRepository.findChannelByLectureId(originalLecture.getId());
        return updateChannelName(channel, channelName);
    }

    /**
     * Update the channel of an exercise
     *
     * @param originalExercise the original exercise
     * @param updatedExercise  the updated exercise
     * @return the updated channel
     */
    public Channel updateExerciseChannel(Exercise originalExercise, Exercise updatedExercise) {
        if (updatedExercise.getChannelName() == null) {
            return null;
        }
        Channel channel = channelRepository.findChannelByExerciseId(originalExercise.getId());
        return updateChannelName(channel, updatedExercise.getChannelName());
    }

    /**
     * Update the channel of an exam
     *
     * @param originalExam the original exam
     * @param updatedExam  the updated exam
     * @return the updated channel
     */
    public Channel updateExamChannel(Exam originalExam, Exam updatedExam) {
        if (updatedExam.getChannelName() == null) {
            return null;
        }
        Channel channel = channelRepository.findChannelByExamId(originalExam.getId());
        return updateChannelName(channel, updatedExam.getChannelName());
    }

    /**
     * Update the channel name
     *
     * @param channel        the channel to update
     * @param newChannelName the new channel name
     * @return the updated channel
     */
    private Channel updateChannelName(Channel channel, String newChannelName) {

        // Update channel name if necessary
        if (!newChannelName.equals(channel.getName())) {
            channel.setName(newChannelName);
            this.channelIsValidOrThrow(channel.getCourse().getId(), channel);
            return channelRepository.save(channel);
        }
        else {
            return channel;
        }
    }
}
