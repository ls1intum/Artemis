package de.tum.cit.aet.artemis.communication.service.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.DefaultChannelType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.ChannelDTO;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.errors.ChannelNameDuplicateException;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ChannelService {

    public static final String CHANNEL_ENTITY_NAME = "messages.channel";

    private static final String CHANNEL_NAME_REGEX = "^[a-z0-9$][a-z0-9:\\-]{0,30}$";

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelRepository channelRepository;

    private final ConversationService conversationService;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public ChannelService(ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository, ConversationService conversationService,
            UserRepository userRepository, StudentParticipationRepository studentParticipationRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelRepository = channelRepository;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
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
        // If the channel is course-wide, there might not be a participant entry for
        // some users yet. They are created here.
        if (channel.getIsCourseWide()) {
            var matchingParticipantIds = matchingParticipants.stream().map(participant -> participant.getUser().getId()).collect(Collectors.toSet());
            var missingUsers = usersToGrant.stream().filter(user -> !matchingParticipantIds.contains(user.getId()));
            missingUsers.forEach(user -> {
                ConversationParticipant conversationParticipant = ConversationParticipant.createWithDefaultValues(user, channel);
                conversationParticipant.setIsModerator(true);
                matchingParticipants.add(conversationParticipant);
            });
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
     * @param creator the creator of the channel, if set a participant will be
     *                    created for the creator
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
            var conversationParticipantOfRequestingUser = ConversationParticipant.createWithDefaultValues(creator.get(), savedChannel);
            // Creator is a moderator. Special case, because creator is the only moderator
            // that can not be revoked the role
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
     * @param addAllStudents        if true, all students of the course will be
     *                                  added to the channel
     * @param addAllTutors          if true, all tutors of the course will be added
     *                                  to the channel
     * @param addAllInstructors     if true, all instructors of the course will be
     *                                  added to the channel
     * @param usersLoginsToRegister the logins of the users to register to the
     *                                  channel
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
     * Deletes the channel if it exists
     *
     * @param channel the channel to delete
     */
    public void deleteChannel(@Nullable Channel channel) {
        if (channel != null) {
            conversationService.deleteConversation(channel.getId());
        }
    }

    /**
     * Checks if the given channel is valid for the given course or throws an
     * exception
     *
     * @param courseId the id of the course
     * @param channel  the channel to check
     */
    public void channelIsValidOrThrow(Long courseId, @Valid Channel channel) {
        if (channel.getName() != null && !channel.getName().matches(CHANNEL_NAME_REGEX)) {
            throw new BadRequestAlertException("Channel names can only contain lowercase letters, numbers, colons and dashes.", CHANNEL_ENTITY_NAME, "namePatternInvalid");
        }

        if (this.allowDuplicateChannelName(channel)) {
            return;
        }

        Set<Channel> channelsWithSameName;
        if (channel.getId() != null) {
            channelsWithSameName = channelRepository.findChannelByCourseIdAndNameAndIdNot(courseId, channel.getName(), channel.getId());
        }
        else {
            channelsWithSameName = channelRepository.findChannelByCourseIdAndName(courseId, channel.getName());
        }
        if (!channelsWithSameName.isEmpty()) {
            throw new ChannelNameDuplicateException(channel.getName());
        }
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
     * Creates and persists channels for the given lectures within a course.
     * Assumes that unique channel names can be derived from the lectures titles.
     * Will produce duplicate channel names otherwise that need to be corrected.
     * Assigns the specified user as the creator and moderator.
     *
     * @param lectures the list of lectures for which channels should be created
     * @param course   the course to which the lectures (and channels) belong
     * @param creator  the user who will be set as the creator and moderator of each
     *                     channel
     * @throws IllegalArgumentException if for any lecture a channel name is derived
     *                                      that does not follow the required format
     */
    public void createChannelsForLectures(List<Lecture> lectures, Course course, User creator) {
        Set<Channel> channelsToCreate = new HashSet<>();
        Set<ConversationParticipant> conversationParticipants = new HashSet<>();
        for (Lecture lecture : lectures) {
            Channel channelToCreate = createDefaultChannel(Optional.empty(), "lecture-", lecture.getTitle());
            channelToCreate.setLecture(lecture);
            channelToCreate.setCreator(creator);
            channelToCreate.setCourse(course);
            channelToCreate.setIsArchived(false);
            if (!channelToCreate.getName().matches(CHANNEL_NAME_REGEX)) {
                throw new IllegalArgumentException("A channel name that was derived from a lecture title did not satisfy the channel name format");
            }
            channelsToCreate.add(channelToCreate);

            ConversationParticipant conversationParticipant = ConversationParticipant.createWithDefaultValues(creator, channelToCreate);
            conversationParticipant.setIsModerator(true);
            conversationParticipants.add(conversationParticipant);
            channelToCreate.getConversationParticipants().add(conversationParticipant);
        }
        channelRepository.saveAll(channelsToCreate);
        conversationParticipantRepository.saveAll(conversationParticipants);
        channelsToCreate.forEach(channel -> conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, channel, Set.of(creator)));
    }

    /**
     * Creates a channel for a lecture and sets the channel name of the lecture
     * accordingly.
     *
     * @param lecture     the lecture to create the channel for
     * @param channelName the name of the channel (optional), will be generated from the lecture title if not provided
     *
     * @return the created channel name
     */
    public String createLectureChannel(Lecture lecture, Optional<String> channelName) {
        Channel channelToCreate = createDefaultChannel(channelName, "lecture-", lecture.getTitle());
        channelToCreate.setLecture(lecture);
        Channel createdChannel = createChannel(lecture.getCourse(), channelToCreate, Optional.of(userRepository.getUser()));
        return createdChannel.getName();
    }

    /**
     * Creates a channel for a course exercise and sets the channel name of the
     * exercise accordingly.
     *
     * @param exercise    the exercise to create the channel for
     * @param channelName the name of the channel
     * @return the created channel
     */
    public Channel createExerciseChannel(Exercise exercise, Optional<String> channelName) {
        if (!exercise.isCourseExercise()) {
            return null;
        }
        Channel channelToCreate = createDefaultChannel(channelName, "exercise-", exercise.getTitle());
        channelToCreate.setExercise(exercise);
        return createChannel(exercise.getCourseViaExerciseGroupOrCourseMember(), channelToCreate, Optional.of(userRepository.getUser()));
    }

    /**
     * Creates a channel for a real exam and sets the channel name of the exam
     * accordingly.
     *
     * @param exam        the exam to create the channel for
     * @param channelName the name of the channel
     */
    public void createExamChannel(Exam exam, Optional<String> channelName) {
        Channel channelToCreate = createDefaultChannel(channelName, "exam-", exam.getTitle());
        channelToCreate.setExam(exam);
        Channel createdChannel = createChannel(exam.getCourse(), channelToCreate, Optional.of(userRepository.getUser()));
        exam.setChannelName(createdChannel.getName());
    }

    /**
     * Update the channel of a lecture
     *
     * @param originalLecture the original lecture
     * @param channelName     the new channel name
     */
    public void updateLectureChannel(Lecture originalLecture, String channelName) {
        if (channelName == null) {
            return;
        }
        Channel channel = channelRepository.findChannelByLectureId(originalLecture.getId());
        if (channel == null) {
            return;
        }
        updateChannelName(channel, channelName);
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
        if (channel == null) {
            return null;
        }
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
        if (channel == null) {
            return null;
        }
        return updateChannelName(channel, updatedExam.getChannelName());
    }

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

    /**
     * Creates a channel object with the provided name.
     * The resulting channel is public, not an announcement channel and not
     * archived.
     *
     * @param channelNameOptional the desired name of the channel wrapped in an
     *                                Optional
     * @param prefix              the prefix for the channel name
     * @param backupTitle         used as a basis for the resulting channel name if
     *                                the provided channel name is empty
     * @return a default channel with the given name
     */
    private static Channel createDefaultChannel(Optional<String> channelNameOptional, @NonNull String prefix, String backupTitle) {
        String channelName = channelNameOptional.filter(s -> !s.isEmpty()).orElse(generateChannelNameFromTitle(prefix, Optional.ofNullable(backupTitle)));
        Channel defaultChannel = new Channel();
        defaultChannel.setName(channelName);
        defaultChannel.setIsPublic(true);
        defaultChannel.setIsCourseWide(true);
        defaultChannel.setIsAnnouncementChannel(false);
        defaultChannel.setIsArchived(false);
        return defaultChannel;
    }

    /**
     * Determines whether duplicate channel names are allowed for the given channel
     *
     * @return true if the channel does belong to a lecture/exercise/exam
     */
    private boolean allowDuplicateChannelName(Channel channel) {
        return channel.getExercise() != null || channel.getLecture() != null || channel.getExam() != null;
    }

    /**
     * Generates the channel name based on the associated lecture/exercise/exam
     * title and a corresponding prefix.
     * The resulting name only contains lower case letters, digits and hyphens and
     * has a maximum length of 30 characters.
     * Upper case letters are transformed to lower case and special characters are
     * replaced with a hyphen, while avoiding
     * consecutive hyphens, e.g. "Example(%)name" becomes "example-name".
     *
     * @param prefix prefix for the channel
     * @param title  title of the lecture/exercise/exam to derive the channel name
     *                   from
     * @return the generated channel name
     */
    private static String generateChannelNameFromTitle(@NonNull String prefix, Optional<String> title) {
        String channelName = prefix + title.orElse("");
        // [^a-z0-9]+ matches all occurrences of single or consecutive characters that
        // are no digits and letters
        String specialCharacters = "[^a-z0-9]+";
        // -+$ matches a trailing hyphen at the end of a string
        String leadingTrailingHyphens = "-$";
        channelName = channelName.toLowerCase().replaceAll(specialCharacters, "-").replaceFirst(leadingTrailingHyphens, "");
        if (channelName.length() > 30) {
            channelName = channelName.substring(0, 30);
        }
        return channelName;
    }

    /**
     * Creates a feedback-specific channel for an exercise within a course.
     *
     * @param course              in which the channel is being created.
     * @param exerciseId          of the exercise associated with the feedback
     *                                channel.
     * @param channelDTO          containing the properties of the channel to be
     *                                created, such as name, description, and
     *                                visibility.
     * @param feedbackDetailTexts used to identify the students affected by the
     *                                feedback.
     * @param requestingUser      initiating the channel creation request.
     * @param testCaseName        to filter student submissions according to a
     *                                specific feedback
     * @return the created {@link Channel} object with its properties.
     * @throws BadRequestAlertException if the channel name starts with an invalid
     *                                      prefix (e.g., "$").
     */
    public Channel createFeedbackChannel(Course course, Long exerciseId, ChannelDTO channelDTO, List<String> feedbackDetailTexts, String testCaseName, User requestingUser) {
        if (channelDTO.getName() != null && channelDTO.getName().trim().startsWith("$")) {
            throw new BadRequestAlertException("User generated channels cannot start with $", "channel", "channelNameInvalid");
        }

        Channel createdChannel = createChannel(course, channelDTO.toChannel(), Optional.of(requestingUser));

        List<String> userLogins = studentParticipationRepository.findAffectedLoginsByFeedbackDetailText(exerciseId, feedbackDetailTexts, testCaseName);

        if (userLogins != null && !userLogins.isEmpty()) {
            registerUsersToChannel(false, false, false, userLogins, course, createdChannel);
        }

        return createdChannel;
    }

    public void deleteChannelForExerciseId(long exerciseId) {
        Long exerciseChannelId = channelRepository.findChannelIdByExerciseId(exerciseId);
        if (exerciseChannelId != null) {
            conversationService.deleteConversation(exerciseChannelId);
        }
    }

    /**
     * Creates a default channel with the given name. The channel is course-wide so all course members are automatically participants.
     *
     * @param course      the course, where the channel should be created
     * @param channelType the default channel type
     */
    public void createDefaultChannel(Course course, DefaultChannelType channelType) {
        Channel channelToCreate = new Channel();
        channelToCreate.setName(channelType.getName());
        channelToCreate.setIsPublic(true);
        channelToCreate.setIsCourseWide(true);
        channelToCreate.setIsAnnouncementChannel(channelType.equals(DefaultChannelType.ANNOUNCEMENT));
        channelToCreate.setIsArchived(false);
        channelToCreate.setDescription(null);
        createChannel(course, channelToCreate, Optional.empty());
    }

    /**
     * Creates all default communication channels for the given course.
     * <p>
     * See {@link de.tum.cit.aet.artemis.communication.domain.DefaultChannelType}
     * for the list of channel types that will be created.
     *
     * @param course the course for which the default channels should be created
     */
    public void createDefaultChannels(Course course) {
        Arrays.stream(DefaultChannelType.values()).forEach(channelType -> createDefaultChannel(course, channelType));
    }
}
