package de.tum.cit.aet.artemis.communication.web.conversation;

import static de.tum.cit.aet.artemis.communication.service.conversation.ChannelService.CHANNEL_ENTITY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.AddedToChannelNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ChannelDeletedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.RemovedFromChannelNotification;
import de.tum.cit.aet.artemis.communication.dto.ChannelDTO;
import de.tum.cit.aet.artemis.communication.dto.ChannelIdAndNameDTO;
import de.tum.cit.aet.artemis.communication.dto.FeedbackChannelRequestDTO;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationDTOService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.service.conversation.auth.ChannelAuthorizationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupChannelManagementApi;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/communication/courses/")
public class ChannelResource extends ConversationManagementResource {

    private static final Logger log = LoggerFactory.getLogger(ChannelResource.class);

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ConversationDTOService conversationDTOService;

    private final UserRepository userRepository;

    private final ConversationService conversationService;

    private final Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final CourseNotificationService courseNotificationService;

    public ChannelResource(ConversationParticipantRepository conversationParticipantRepository, ChannelService channelService, ChannelRepository channelRepository,
            ChannelAuthorizationService channelAuthorizationService, AuthorizationCheckService authorizationCheckService, ConversationDTOService conversationDTOService,
            CourseRepository courseRepository, UserRepository userRepository, ConversationService conversationService,
            Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi, CourseNotificationService courseNotificationService) {
        super(courseRepository);
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.conversationDTOService = conversationDTOService;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.tutorialGroupChannelManagementApi = tutorialGroupChannelManagementApi;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.courseNotificationService = courseNotificationService;
    }

    /**
     * GET courses/:courseId/channels/overview: Returns an overview of all channels in a course
     *
     * @param courseId the id of the course
     * @return ResponseEntity with status 200 (OK) and with body containing the list of channels the user is authorized to see
     */
    @GetMapping("{courseId}/channels/overview")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ChannelDTO>> getCourseChannelsOverview(@PathVariable Long courseId) {
        log.debug("REST request to all channels of course: {}", courseId);
        checkCommunicationEnabledElseThrow(courseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(course, requestingUser);
        var isOnlyStudent = authorizationCheckService.isOnlyStudentInCourse(course, requestingUser);
        var channels = channelRepository.findChannelsByCourseId(courseId).stream();

        var filteredChannels = isOnlyStudent ? conversationService.filterVisibleChannelsForStudents(channels) : channels;
        var channelDTOs = filteredChannels.map(channel -> conversationDTOService.convertChannelToDTO(requestingUser, channel));

        // only instructors / system admins can see all channels
        if (!isAtLeastInstructor) {
            channelDTOs = filterVisibleChannelsForNonInstructors(channelDTOs);
        }

        return ResponseEntity.ok(channelDTOs.sorted(Comparator.comparing(ChannelDTO::getName)).toList());
    }

    /**
     * GET courses/:courseId/channels/public-overview: Returns a list of channels in a course that are visible to every course member
     *
     * @param courseId the id of the course
     * @return ResponseEntity with status 200 (OK) and with body containing the list of channels visible to all course members
     */
    @GetMapping("{courseId}/channels/public-overview")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ChannelIdAndNameDTO>> getCoursePublicChannelsOverview(@PathVariable Long courseId) {
        log.debug("REST request to get all public channels of course: {}", courseId);
        checkCommunicationEnabledElseThrow(courseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        var channels = channelRepository.findChannelsByCourseId(courseId).stream();

        // Filter channels that are either course-wide or public and, if associated with a lecture/exercise/exam,
        // ensure it's visible to students
        var filteredChannelSummaries = conversationService.filterVisibleChannelsForStudents(channels).filter(summary -> summary.getIsCourseWide() || summary.getIsPublic());
        var channelDTOs = filteredChannelSummaries.map(summary -> new ChannelIdAndNameDTO(summary.getId(), summary.getName()));

        return ResponseEntity.ok(channelDTOs.sorted(Comparator.comparing(ChannelIdAndNameDTO::name)).toList());
    }

    /**
     * GET courses/:courseId/exercises/:exerciseId/channel Returns the channel by exercise id
     *
     * @param courseId   the id of the course
     * @param exerciseId the id of the channel
     * @return ResponseEntity with status 200 (OK) and with body containing the channel
     */
    @GetMapping("{courseId}/exercises/{exerciseId}/channel")
    @EnforceAtLeastStudent
    public ResponseEntity<ChannelDTO> getExerciseChannel(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        log.debug("REST request to get channel of exercise: {}", exerciseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        var channel = channelRepository.findChannelByExerciseId(exerciseId);

        if (channel == null) {
            return ResponseEntity.ok(null);
        }
        checkChannelMembership(channel, requestingUser);

        return ResponseEntity.ok(conversationDTOService.convertChannelToDTO(requestingUser, channel));
    }

    /**
     * GET courses/:courseId/lectures/:lectureId/channel Returns the channel by lecture id
     *
     * @param courseId  the id of the course
     * @param lectureId the id of the channel
     * @return ResponseEntity with status 200 (OK) and with body containing the channel
     */
    @GetMapping("{courseId}/lectures/{lectureId}/channel")
    @EnforceAtLeastStudent
    public ResponseEntity<ChannelDTO> getLectureChannel(@PathVariable Long courseId, @PathVariable Long lectureId) {
        log.debug("REST request to get channel of lecture: {}", lectureId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        var channel = channelRepository.findChannelByLectureId(lectureId);

        if (channel == null) {
            return ResponseEntity.ok(null);

        }
        checkChannelMembership(channel, requestingUser);

        return ResponseEntity.ok(conversationDTOService.convertChannelToDTO(requestingUser, channel));
    }

    /**
     * POST courses/:courseId/channels/: Creates a new channel in a course
     *
     * @param courseId   the id of the course
     * @param channelDTO the dto containing the properties of the channel to be created
     * @return ResponseEntity with status 201 (Created) and with body containing the created channel
     */
    @PostMapping("{courseId}/channels")
    @EnforceAtLeastStudent
    public ResponseEntity<ChannelDTO> createChannel(@PathVariable Long courseId, @RequestBody ChannelDTO channelDTO) throws URISyntaxException {
        log.debug("REST request to create channel in course {} with properties : {}", courseId, channelDTO);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);
        channelAuthorizationService.isAllowedToCreateChannel(course, requestingUser);

        var channelToCreate = new Channel();
        channelToCreate.setName(channelDTO.getName());
        channelToCreate.setIsPublic(channelDTO.getIsPublic());
        channelToCreate.setIsAnnouncementChannel(channelDTO.getIsAnnouncementChannel());
        channelToCreate.setIsArchived(false);
        channelToCreate.setDescription(channelDTO.getDescription());
        channelToCreate.setIsCourseWide(channelDTO.getIsCourseWide());

        if (channelDTO.getName() != null && channelDTO.getName().trim().startsWith("$")) {
            throw new BadRequestAlertException("User generated channels cannot start with $", "channel", "channelNameInvalid");
        }

        var createdChannel = channelService.createChannel(course, channelDTO.toChannel(), Optional.of(userRepository.getUserWithGroupsAndAuthorities()));

        if (createdChannel.getIsCourseWide()) {
            var addedToChannelNotification = new AddedToChannelNotification(courseId, course.getTitle(), course.getCourseIcon(), requestingUser.getName(), createdChannel.getName(),
                    createdChannel.getId());
            // NOTE: we cannot use Set.of(), because the group names might be identical and then the ImmutableCollections$SetN would throw an exception
            Set<String> groupNames = new HashSet<>();
            groupNames.add(course.getStudentGroupName());
            groupNames.add(course.getTeachingAssistantGroupName());
            groupNames.add(course.getEditorGroupName());
            groupNames.add(course.getInstructorGroupName());
            var recipients = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(groupNames);

            courseNotificationService.sendCourseNotification(addedToChannelNotification,
                    recipients.stream().filter(user -> !Objects.equals(user.getId(), requestingUser.getId())).toList());
        }

        return ResponseEntity.created(new URI("/api/channels/" + createdChannel.getId())).body(conversationDTOService.convertChannelToDTO(requestingUser, createdChannel));
    }

    /**
     * PUT courses/:courseId/channels/:channelId: Updates a channel in a course
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel to be updated
     * @param channelDTO the dto containing the properties of the channel to be updated
     * @return ResponseEntity with status 200 (Ok) and with body containing the updated channel
     */
    @PutMapping("{courseId}/channels/{channelId}")
    @EnforceAtLeastStudent
    public ResponseEntity<ChannelDTO> updateChannel(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody ChannelDTO channelDTO) {
        log.debug("REST request to update channel {} with properties : {}", channelId, channelDTO);
        checkCommunicationEnabledElseThrow(courseId);

        var originalChannel = channelRepository.findByIdElseThrow(channelId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        if (!originalChannel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToUpdateChannel(originalChannel, requestingUser);

        if (channelDTO.getName() != null && channelDTO.getName().trim().startsWith("$")) {
            throw new BadRequestAlertException("User generated channels cannot start with $", "channel", "channelNameInvalid");
        }

        var updatedChannel = channelService.updateChannel(originalChannel.getId(), courseId, channelDTO);
        return ResponseEntity.ok().body(conversationDTOService.convertChannelToDTO(requestingUser, updatedChannel));
    }

    /**
     * DELETE courses/:courseId/channels/:channelId: Deletes a channel in a course
     *
     * @param courseId  the id of the course
     * @param channelId the id of the channel to be deleted
     * @return ResponseEntity with status 200 (Ok)
     */
    @DeleteMapping("{courseId}/channels/{channelId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteChannel(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to delete channel {}", channelId);
        checkCommunicationEnabledElseThrow(courseId);
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        channelAuthorizationService.isAllowedToDeleteChannel(channel, requestingUser);

        tutorialGroupChannelManagementApi.flatMap(groupChannelManagementApi -> groupChannelManagementApi.getTutorialGroupBelongingToChannel(channel)).ifPresent(tutorialGroup -> {
            throw new BadRequestAlertException("The channel belongs to tutorial group " + tutorialGroup.getTitle(), CHANNEL_ENTITY_NAME, "channel.tutorialGroup.mismatch");
        });

        var usersToNotify = conversationParticipantRepository.findConversationParticipantsByConversationId(channel.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        conversationService.deleteConversation(channel);

        var course = channel.getCourse();
        var channelDeletedNotification = new ChannelDeletedNotification(courseId, course.getTitle(), course.getCourseIcon(), requestingUser.getName(), channel.getName());

        courseNotificationService.sendCourseNotification(channelDeletedNotification,
                usersToNotify.stream().filter((user) -> !Objects.equals(user.getId(), requestingUser.getId())).toList());
        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/:channelId/archive : Archives a channel in a course
     *
     * @param courseId  the id of the course
     * @param channelId the id of the channel to be archived
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/channels/{channelId}/archive")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> archiveChannel(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to archive channel : {}", channelId);
        checkCommunicationEnabledElseThrow(courseId);
        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        channelAuthorizationService.isAllowedToArchiveChannel(channelFromDatabase, userRepository.getUserWithGroupsAndAuthorities());
        channelService.archiveChannel(channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/:channelId/unarchive : Unarchives an archived channel in a course
     *
     * @param courseId  the id of the course
     * @param channelId the id of the archived channel to be unarchived
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/channels/{channelId}/unarchive")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> unArchiveChannel(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to unarchive channel : {}", channelId);
        checkCommunicationEnabledElseThrow(courseId);
        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        channelAuthorizationService.isAllowedToUnArchiveChannel(channelFromDatabase, userRepository.getUserWithGroupsAndAuthorities());
        channelService.unarchiveChannel(channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/:channelId/grant-channel-moderator : Grants members of a channel the channel moderator role
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the channel members to be granted the channel moderator role
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/channels/{channelId}/grant-channel-moderator")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> grantChannelModeratorRole(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        log.debug("REST request to grant channel moderator role to users {} in channel {}", userLogins.toString(), channelId);
        checkCommunicationEnabledElseThrow(courseId);
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToGrantChannelModeratorRole(channel, userRepository.getUserWithGroupsAndAuthorities());
        var usersToGrant = conversationService.findUsersInDatabase(userLogins);
        channelService.grantChannelModeratorRole(channel, usersToGrant);
        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/:channelId/revoke-channel-moderator : Revokes the channel moderator role
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the channel members to be revoked the channel moderator role
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/channels/{channelId}/revoke-channel-moderator")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> revokeChannelModeratorRole(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        log.debug("REST request to revoke channel moderator role from users {} in channel {}", userLogins.toString(), channelId);
        checkCommunicationEnabledElseThrow(courseId);
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToRevokeChannelModeratorRole(channel, userRepository.getUserWithGroupsAndAuthorities());
        var usersToGrantChannelModeratorRole = conversationService.findUsersInDatabase(userLogins);
        if (usersToGrantChannelModeratorRole.contains(channel.getCreator())) {
            throw new BadRequestAlertException("The creator of the channel cannot lose the channel moderator role", CHANNEL_ENTITY_NAME, "channel.creator.revoke");
        }

        channelService.revokeChannelModeratorRole(channel, usersToGrantChannelModeratorRole);
        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/:channelId/register : Registers users to a channel of a course
     *
     * @param courseId          the id of the course
     * @param channelId         the id of the channel
     * @param userLogins        the logins of the course users to be registered for a channel
     * @param addAllStudents    true if all course students should be added
     * @param addAllTutors      true if all course tutors and editors should be added
     * @param addAllInstructors true if all course instructors should be added
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/channels/{channelId}/register")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> registerUsersToChannel(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody(required = false) List<String> userLogins,
            @RequestParam(defaultValue = "false") Boolean addAllStudents, @RequestParam(defaultValue = "false") Boolean addAllTutors,
            @RequestParam(defaultValue = "false") Boolean addAllInstructors) {
        checkCommunicationEnabledElseThrow(courseId);
        List<String> usersLoginsToRegister = new ArrayList<>();
        if (userLogins != null) {
            usersLoginsToRegister.addAll(userLogins);
        }
        usersLoginsToRegister = usersLoginsToRegister.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()).stream().toList();

        if (!usersLoginsToRegister.isEmpty()) {
            log.debug("REST request to register {} users to channel : {}", usersLoginsToRegister.size(), channelId);
        }
        if (addAllStudents || addAllTutors || addAllInstructors) {
            var registerAllString = "addAllStudents: " + addAllStudents + ", addAllTutors: " + addAllTutors + ", addAllInstructors: " + addAllInstructors;
            log.debug("REST request to register {} to channel : {}", registerAllString, channelId);
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        channelAuthorizationService.isAllowedToRegisterUsersToChannel(channelFromDatabase, usersLoginsToRegister, requestingUser);
        Set<User> registeredUsers = channelService.registerUsersToChannel(addAllStudents, addAllTutors, addAllInstructors, usersLoginsToRegister, course, channelFromDatabase);

        var addedToChannelNotification = new AddedToChannelNotification(courseId, course.getTitle(), course.getCourseIcon(), requestingUser.getName(),
                channelFromDatabase.getName(), channelFromDatabase.getId());

        courseNotificationService.sendCourseNotification(addedToChannelNotification, registeredUsers.stream().toList());

        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/:channelId/deregister : Deregisters users from a channel of a course
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the course users to be deregistered from a channel
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/channels/{channelId}/deregister")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deregisterUsers(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        checkCommunicationEnabledElseThrow(courseId);
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", CHANNEL_ENTITY_NAME, "userLoginsEmpty");
        }
        // ToDo: maybe limit how many users can be deregistered at once?
        log.debug("REST request to deregister {} users from the channel : {}", userLogins.size(), channelId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        channelAuthorizationService.isAllowedToDeregisterUsersFromChannel(channelFromDatabase, userLogins, requestingUser);
        var usersToDeRegister = conversationService.findUsersInDatabase(userLogins);
        // you are not allowed to deregister the creator of a channel
        var creator = channelFromDatabase.getCreator();
        if (usersToDeRegister.contains(creator)) {
            throw new BadRequestAlertException("You are not allowed to deregister the creator of a channel", "conversation", "creatorDeregistration");
        }

        conversationService.deregisterUsersFromAConversation(course, usersToDeRegister, channelFromDatabase);

        var removedFromChannelNotification = new RemovedFromChannelNotification(courseId, course.getTitle(), course.getCourseIcon(), requestingUser.getName(),
                channelFromDatabase.getName(), channelFromDatabase.getId());

        courseNotificationService.sendCourseNotification(removedFromChannelNotification, usersToDeRegister.stream().toList());
        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/: Creates a new feedback-specific channel in a course.
     *
     * @param courseId               where the channel is being created.
     * @param exerciseId             for which the feedback channel is being created.
     * @param feedbackChannelRequest containing a DTO with the properties of the channel (e.g., name, description, visibility)
     *                                   and the feedback detail text used to determine the affected students to be added to the channel.
     * @return ResponseEntity with status 201 (Created) and the body containing the details of the created channel.
     * @throws URISyntaxException       if the URI for the created resource cannot be constructed.
     * @throws BadRequestAlertException if the channel name starts with an invalid prefix (e.g., "$").
     */
    @PostMapping("{courseId}/{exerciseId}/feedback-channel")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ChannelDTO> createFeedbackChannel(@PathVariable Long courseId, @PathVariable Long exerciseId,
            @RequestBody FeedbackChannelRequestDTO feedbackChannelRequest) throws URISyntaxException {
        log.debug("REST request to create feedback channel for course {} and exercise {} with properties: {}", courseId, exerciseId, feedbackChannelRequest);

        ChannelDTO channelDTO = feedbackChannelRequest.channel();
        List<String> feedbackDetailTexts = feedbackChannelRequest.feedbackDetailTexts();
        String testCaseName = feedbackChannelRequest.testCaseName();

        User requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);
        Channel createdChannel = channelService.createFeedbackChannel(course, exerciseId, channelDTO, feedbackDetailTexts, testCaseName, requestingUser);
        return ResponseEntity.created(new URI("/api/channels/" + createdChannel.getId())).body(conversationDTOService.convertChannelToDTO(requestingUser, createdChannel));
    }

    /**
     * POST courses/:courseId/channels/mark-as-read: Marks all channels of a course as read for the current user.
     *
     * @param courseId the id of the course.
     * @return ResponseEntity with status 200 (Ok).
     */
    @PostMapping("{courseId}/channels/mark-as-read")
    @EnforceAtLeastStudent
    public ResponseEntity<ChannelDTO> markAllChannelsOfCourseAsRead(@PathVariable Long courseId) {
        log.debug("REST request to mark all channels of course {} as read", courseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        conversationService.markAllConversationOfAUserAsRead(course.getId(), requestingUser);
        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/channels/:channelId/toggle-privacy
     * <p>
     * Toggles the privacy status of a channel: If the channel is public, it becomes private;
     * if it is private, it becomes public.
     *
     * @param courseId  The ID of the course to which the channel belongs
     * @param channelId The ID of the channel whose privacy status will be changed
     * @return The updated channel's DTO
     */
    @PostMapping("{courseId}/channels/{channelId}/toggle-privacy")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<ChannelDTO> toggleChannelPrivacy(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to toggle privacy for channel : {}", channelId);
        checkCommunicationEnabledElseThrow(courseId);

        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        if (!channelFromDatabase.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        channelAuthorizationService.isAllowedToUpdateChannel(channelFromDatabase, requestingUser);
        channelFromDatabase.setIsPublic(!channelFromDatabase.getIsPublic());
        var updatedChannel = channelRepository.save(channelFromDatabase);
        return ResponseEntity.ok(conversationDTOService.convertChannelToDTO(requestingUser, updatedChannel));
    }

    private void checkEntityIdMatchesPathIds(Channel channel, Optional<Long> courseId, Optional<Long> conversationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!channel.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the channel", CHANNEL_ENTITY_NAME, "courseIdMismatch");
            }
        });
        conversationId.ifPresent(conversationIdValue -> {
            if (!channel.getId().equals(conversationIdValue)) {
                throw new BadRequestAlertException("The conversationId in the path does not match the channelId in the channel", CHANNEL_ENTITY_NAME, "channelIdMismatch");
            }
        });
    }

    private Stream<ChannelDTO> filterVisibleChannelsForNonInstructors(Stream<ChannelDTO> channelDTOs) {
        return channelDTOs.filter(channelDTO -> channelDTO.getIsPublic() || channelDTO.getIsMember());
    }

    private void checkChannelMembership(Channel channel, @NotNull User user) {
        if (channel == null || channel.getIsCourseWide() || conversationService.isMember(channel.getId(), user.getId())) {
            return;
        }

        // suppress error alert with skipAlert: true so that the client can display a custom error message
        throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "You don't have access to this channel, but you could join it.", "channel", "noAccessButCouldJoin",
                true);
    }
}
