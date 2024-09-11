package de.tum.cit.aet.artemis.service.tutorialgroups;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static jakarta.persistence.Persistence.getPersistenceUtil;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;
import de.tum.cit.aet.artemis.service.metis.conversation.ConversationService;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;

/**
 * Service for managing the channel connected to a tutorial group.
 */
@Profile(PROFILE_CORE)
@Service
public class TutorialGroupChannelManagementService {

    private final ChannelService channelService;

    private final ConversationService conversationService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final ChannelRepository channelRepository;

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupChannelManagementService.class);

    public TutorialGroupChannelManagementService(ChannelService channelService, ConversationService conversationService, TutorialGroupRepository tutorialGroupRepository,
            TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository, ChannelRepository channelRepository) {
        this.channelService = channelService;
        this.conversationService = conversationService;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.channelRepository = channelRepository;
    }

    /**
     * Sets up the channels for all tutorial groups of the given course.
     *
     * @param course the course for which the channels should be set up
     * @return the set of channels that were set up
     */
    public Set<Channel> createTutorialGroupsChannelsForAllTutorialGroupsOfCourse(Course course) {
        log.debug("Set up tutorial group channels for course with id {}", course.getId());
        return tutorialGroupRepository.findAllByCourseIdWithChannel(course.getId()).stream().map(this::createChannelForTutorialGroup).collect(Collectors.toSet());
    }

    /**
     * Removes the channels for all tutorial groups of the given course.
     *
     * @param course the course for which the channels should be removed
     */
    public void removeTutorialGroupChannelsForCourse(Course course) {
        log.debug("Remove tutorial group channels for course with id {}", course.getId());
        tutorialGroupRepository.findAllByCourseIdWithChannel(course.getId()).forEach(this::deleteTutorialGroupChannel);
    }

    /**
     * Perform setup of channel for the given tutorial group
     * <p>
     * - Create channel if it does not exist
     * - Add all students of tutorial group to channel
     * - Add teaching assistant to channel and make him/her moderator
     *
     * @param tutorialGroupToSetUp the tutorial group for which the channel should be set up
     * @return the created or existing channel
     */
    public Channel createChannelForTutorialGroup(TutorialGroup tutorialGroupToSetUp) {
        var channel = getTutorialGroupChannel(tutorialGroupToSetUp).orElseGet(() -> createTutorialGroupChannel(tutorialGroupToSetUp));
        addAllStudentsOfTutorialGroupToChannel(tutorialGroupToSetUp);
        addTeachingAssistantToTutorialGroupChannel(tutorialGroupToSetUp);
        log.debug("Setup channel for tutorial group with id {} with channel id {}", tutorialGroupToSetUp.getId(), channel.getId());
        return channel;
    }

    /**
     * Updates the name of the tutorial group channel to the current title of the tutorial group.
     *
     * @param tutorialGroup the tutorial group for which the channel should be updated
     */
    public void updateNameOfTutorialGroupChannel(TutorialGroup tutorialGroup) {
        getTutorialGroupChannel(tutorialGroup).ifPresentOrElse(channel -> {
            channel.setName(determineUniqueTutorialGroupChannelName(tutorialGroup));
            channelRepository.save(channel);
            log.debug("Updated title of channel with id {} of tutorial group with id {}", channel.getId(), tutorialGroup.getId());
        }, () -> log.debug("Channel for tutorial group with id {} does not exist, cannot update title", tutorialGroup.getId()));
    }

    /**
     * Delete the tutorial group channel of the given tutorial group.
     *
     * @param tutorialGroup the tutorial group of the channel to be deleted
     */
    public void deleteTutorialGroupChannel(TutorialGroup tutorialGroup) {
        tutorialGroupRepository.getTutorialGroupWithChannel(tutorialGroup.getId()).ifPresentOrElse(tg -> {
            if (tg.getTutorialGroupChannel() != null) {
                var channel = tg.getTutorialGroupChannel();
                tg.setTutorialGroupChannel(null);
                tutorialGroupRepository.save(tg);
                conversationService.deleteConversation(channel);
            }
            {
                log.debug("Tutorial group with id {} does not have a channel, cannot delete channel", tutorialGroup.getId());
            }
        }, () -> log.debug("Tutorial with id {} does not exist, cannot delete channel", tutorialGroup.getId()));
    }

    /**
     * Registers the teaching assistant of the given tutorial group to the channel of the tutorial group.
     * <p>
     * In addition, grants the teaching assistant the moderator role for the channel.
     *
     * @param tutorialGroup the tutorial group for which the teaching assistant should be registered to the channel
     */
    public void addTeachingAssistantToTutorialGroupChannel(TutorialGroup tutorialGroup) {
        var teachingAssistantOptional = getTeachingAssistant(tutorialGroup);
        var tutorialGroupChannelOptional = getTutorialGroupChannel(tutorialGroup);

        if (Stream.of(teachingAssistantOptional, tutorialGroupChannelOptional).allMatch(Optional::isPresent)) {
            var teachingAssistant = teachingAssistantOptional.get();
            var channel = tutorialGroupChannelOptional.get();
            conversationService.registerUsersToConversation(tutorialGroup.getCourse(), Set.of(teachingAssistant), channel, Optional.empty());
            log.debug("Added teaching assistant with id {} to the channel with id {} of the tutorial group with id {}", teachingAssistant.getId(), channel.getId(),
                    tutorialGroup.getId());
            channelService.grantChannelModeratorRole(channel, Set.of(teachingAssistant));
            log.debug("Granted teaching assistant with id {} moderator role for the channel with id {} of the tutorial group with id {}", teachingAssistant.getId(),
                    channel.getId(), tutorialGroup.getId());
        }
        else {
            tutorialGroupChannelOptional.ifPresent(
                    channel -> log.debug("Could not add teaching assistant to channel with id {} of tutorial group with id {} because the teaching assistant is not present",
                            channel.getId(), tutorialGroup.getId()));
            teachingAssistantOptional.ifPresent(
                    teachingAssistant -> log.debug("Could not add teaching assistant with id {} to channel of tutorial group with id {} because the channel is not present",
                            teachingAssistant.getId(), tutorialGroup.getId()));
        }
    }

    /**
     * Grants the given users the moderator role for the channel of the given tutorial group.
     *
     * @param tutorialGroup the tutorial group of the channel for which the users should be granted the moderator role
     * @param users         the users for which the moderator role should be granted
     */
    public void grantUsersModeratorRoleToTutorialGroupChannel(TutorialGroup tutorialGroup, Set<User> users) {
        getTutorialGroupChannel(tutorialGroup).ifPresentOrElse(channel -> {
            channelService.grantChannelModeratorRole(channel, users);
            log.debug("Granted users with ids {} moderator role for the channel with id {} of the tutorial group with id {}",
                    users.stream().map(User::getId).collect(Collectors.toSet()), channel.getId(), tutorialGroup.getId());
        }, () -> log.debug("Channel for tutorial group with id {} does not exist, cannot grant users moderator role for channel", tutorialGroup.getId()));
    }

    /**
     * Add the given users to the tutorial group channel of the given tutorial group.
     *
     * @param tutorialGroup the tutorial group of the channel to which the users should be added
     * @param usersToAdd    the users to be added to the channel
     */
    public void addUsersToTutorialGroupChannel(TutorialGroup tutorialGroup, Set<User> usersToAdd) {
        getTutorialGroupChannel(tutorialGroup).ifPresentOrElse(channel -> {
            conversationService.registerUsersToConversation(tutorialGroup.getCourse(), usersToAdd, channel, Optional.empty());
            log.debug("Added users with ids {} to the channel with id {} of the tutorial group with id {}", usersToAdd.stream().map(User::getId).collect(Collectors.toSet()),
                    channel.getId(), tutorialGroup.getId());
        }, () -> log.debug("Channel for tutorial group with id {} does not exist, cannot add users to channel", tutorialGroup.getId()));
    }

    /**
     * Remove the given users from the tutorial group channel of the given tutorial group.
     *
     * @param tutorialGroup the tutorial group of the channel from which the users should be removed
     * @param usersToRemove the users to be removed from the channel
     */
    public void removeUsersFromTutorialGroupChannel(TutorialGroup tutorialGroup, Set<User> usersToRemove) {
        getTutorialGroupChannel(tutorialGroup).ifPresentOrElse(channel -> {
            conversationService.deregisterUsersFromAConversation(tutorialGroup.getCourse(), usersToRemove, channel);
            log.debug("Removed users with ids {} from the channel with id {} of the tutorial group with id {}", usersToRemove.stream().map(User::getId).collect(Collectors.toSet()),
                    channel.getId(), tutorialGroup.getId());
        }, () -> log.debug("Channel for tutorial group with id {} does not exist, cannot remove users from channel", tutorialGroup.getId()));
    }

    /**
     * Removes the given users from all tutorial group channels of the given course.
     *
     * @param course the course of the tutorial groups
     * @param users  the users to be removed from the channels
     */
    public void removeUsersFromAllTutorialGroupChannelsInCourse(Course course, Set<User> users) {
        var tutorialGroups = tutorialGroupRepository.findAllByCourseId(course.getId());
        tutorialGroups.forEach(tutorialGroup -> removeUsersFromTutorialGroupChannel(tutorialGroup, users));
    }

    /**
     * Add all students of the given tutorial group to the channel of the tutorial group.
     *
     * @param tutorialGroup the tutorial group for which all students should be added to the channel
     */
    private void addAllStudentsOfTutorialGroupToChannel(TutorialGroup tutorialGroup) {
        var studentsToAdd = tutorialGroupRegistrationRepository.findAllByTutorialGroup(tutorialGroup).stream().map(TutorialGroupRegistration::getStudent)
                .collect(Collectors.toSet());
        addUsersToTutorialGroupChannel(tutorialGroup, studentsToAdd);
    }

    /**
     * Creates a channel for the given tutorial group in the database.
     *
     * @param tutorialGroup the tutorial group for which the channel should be created
     * @return the created channel
     */
    private Channel createTutorialGroupChannel(TutorialGroup tutorialGroup) {
        var tutorialGroupChannel = new Channel();
        tutorialGroupChannel.setName(determineUniqueTutorialGroupChannelName(tutorialGroup));
        tutorialGroupChannel.setIsPublic(true); // TODO: make this configurable if desired requirement
        tutorialGroupChannel.setIsAnnouncementChannel(false);
        var persistedChannel = channelService.createChannel(tutorialGroup.getCourse(), tutorialGroupChannel, Optional.empty());
        log.debug("Created channel with id {} for tutorial group with id {}", persistedChannel.getId(), tutorialGroup.getId());
        tutorialGroup.setTutorialGroupChannel(persistedChannel);
        tutorialGroupRepository.saveAndFlush(tutorialGroup);
        log.debug("Added channel with id {} to tutorial group with id {}", persistedChannel.getId(), tutorialGroup.getId());

        return persistedChannel;
    }

    /**
     * Determines a unique channel name for the given tutorial group based on the tutorial group title.
     *
     * @param tutorialGroup the tutorial group for which the channel name should be determined
     * @return a unique channel name for the given tutorial group
     * @throws IllegalStateException if a unique channel name could not be determined
     */
    private String determineUniqueTutorialGroupChannelName(TutorialGroup tutorialGroup) {
        Course course = tutorialGroup.getCourse();
        String cleanedGroupTitle = tutorialGroup.getTitle().replaceAll("\\s", "-").toLowerCase();
        String channelName = "tutorgroup-" + cleanedGroupTitle.substring(0, Math.min(cleanedGroupTitle.length(), 18));

        if (!channelRepository.existsChannelByNameAndCourseId(channelName, course.getId())) {
            // No channel with this name exists in the course yet, so it can be used.
            return channelName;
        }

        // try to make it unique by adding a random number to the end of the channel name
        // if already max length remove the last 3 characters to get some space to try to make it unique
        if (channelName.length() >= 30) {
            channelName = channelName.substring(0, 27);
        }

        do {
            channelName += ThreadLocalRandom.current().nextInt(0, 10);
        }
        while (channelRepository.existsChannelByNameAndCourseId(channelName, course.getId()) && channelName.length() <= 30);

        if (channelName.length() > 30) {
            // very unlikely to happen
            throw new IllegalStateException("Could not create a unique channel name for tutorial group with id " + tutorialGroup.getId());
        }

        return channelName;
    }

    /**
     * Get the tutorial group belonging to the given channel.
     *
     * @param channel the channel for which the tutorial group should be fetched
     * @return the tutorial group belonging to the channel, if it exists
     */
    public Optional<TutorialGroup> getTutorialGroupBelongingToChannel(Channel channel) {
        return tutorialGroupRepository.findByTutorialGroupChannelId(channel.getId());
    }

    /**
     * Get the channel of the given tutorial group.
     *
     * @param tutorialGroup the tutorial group for which the channel should be fetched
     * @return the channel of the tutorial group, if it exists
     */
    public Optional<Channel> getTutorialGroupChannel(TutorialGroup tutorialGroup) {
        return tutorialGroupRepository.getTutorialGroupChannel(tutorialGroup.getId());
    }

    /**
     * Get the teaching assistant of the given tutorial group. If the teaching assistant is not loaded, it will be fetched from the database.
     *
     * @param tutorialGroup the tutorial group for which the teaching assistant should be fetched
     * @return the teaching assistant of the tutorial group, if it exists
     */
    private Optional<User> getTeachingAssistant(TutorialGroup tutorialGroup) {
        if (getPersistenceUtil().isLoaded(tutorialGroup, "teachingAssistant")) {
            return Optional.ofNullable(tutorialGroup.getTeachingAssistant());
        }
        else {
            return Optional.ofNullable(tutorialGroupRepository.findByIdWithTeachingAssistantAndCourseElseThrow(tutorialGroup.getId()).getTeachingAssistant());
        }
    }

    /**
     * Changes the channel mode for all tutorial group channels of the given course.
     *
     * @param course                      the course for which the channel mode should be changed
     * @param tutorialGroupChannelsPublic the new channel mode
     */
    public void changeChannelModeForCourse(Course course, Boolean tutorialGroupChannelsPublic) {
        var channels = tutorialGroupRepository.findAllByCourseIdWithChannel(course.getId()).stream().map(this::createChannelForTutorialGroup).collect(Collectors.toSet());
        channels.forEach(channel -> channel.setIsPublic(tutorialGroupChannelsPublic));
        channelRepository.saveAll(channels);
        log.debug("Changed public for all tutorial group channels of course with id {} to {}", course.getId(), tutorialGroupChannelsPublic);
    }
}
