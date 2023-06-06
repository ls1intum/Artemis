package de.tum.in.www1.artemis.service.metis.conversation;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationWebsocketDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

@Service
public class ConversationService {

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final ConversationDTOService conversationDTOService;

    private final UserRepository userRepository;

    private final ConversationRepository conversationRepository;

    private final ChannelRepository channelRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final OneToOneChatRepository oneToOneChatRepository;

    private final PostRepository postRepository;

    private final GroupChatRepository groupChatRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public ConversationService(ConversationDTOService conversationDTOService, UserRepository userRepository, ChannelRepository channelRepository,
            ConversationParticipantRepository conversationParticipantRepository, ConversationRepository conversationRepository, SimpMessageSendingOperations messagingTemplate,
            OneToOneChatRepository oneToOneChatRepository, PostRepository postRepository, GroupChatRepository groupChatRepository,
            AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.conversationDTOService = conversationDTOService;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
        this.oneToOneChatRepository = oneToOneChatRepository;
        this.postRepository = postRepository;
        this.groupChatRepository = groupChatRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * Gets the conversation with the given id
     *
     * @param conversationId the id of the conversation
     * @return the conversation with the given id
     */
    public Conversation getConversationById(Long conversationId) {
        return conversationRepository.findByIdElseThrow(conversationId);
    }

    /**
     * Checks whether the user is a member of the conversation
     *
     * @param conversationId the id of the conversation
     * @param userId         the id of the user
     * @return true if the user is a member of the conversation, false otherwise
     */
    public boolean isMember(Long conversationId, Long userId) {
        return conversationParticipantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    /**
     * Gets the conversation in a course for which the user is a member
     *
     * @param courseId       the id of the course
     * @param requestingUser the user for which the conversations are requested
     * @return the conversation in the course for which the user is a member
     */
    public List<ConversationDTO> getConversationsOfUser(Long courseId, User requestingUser) {
        var oneToOneChatsOfUser = oneToOneChatRepository.findActiveOneToOneChatsOfUserWithParticipantsAndUserGroups(courseId, requestingUser.getId());
        var channelsOfUser = channelRepository.findChannelsOfUser(courseId, requestingUser.getId());
        var groupChatsOfUser = groupChatRepository.findGroupChatsOfUserWithParticipantsAndUserGroups(courseId, requestingUser.getId());

        var conversations = new ArrayList<Conversation>();
        conversations.addAll(oneToOneChatsOfUser);
        conversations.addAll(groupChatsOfUser);
        Course course = courseRepository.findByIdElseThrow(courseId);
        // if the user is only a student in the course, we filter out all channels that are not yet open
        var isOnlyStudent = authorizationCheckService.isOnlyStudentInCourse(course, requestingUser);
        var filteredChannels = isOnlyStudent ? filterVisibleChannelsForStudents(channelsOfUser.stream()).toList() : channelsOfUser;
        conversations.addAll(filteredChannels);

        return conversations.stream().map(conversation -> conversationDTOService.convertToDTO(conversation, requestingUser)).toList();
    }

    /**
     * Determines if the user has unread messages in that course
     *
     * @param courseId       the id of the course
     * @param requestingUser the user for which the conversations should be checked
     * @return true if the user has unread messages in that course, false otherwise
     */
    public boolean userHasUnreadMessages(Long courseId, User requestingUser) {
        return conversationRepository.userHasUnreadMessageInCourse(courseId, requestingUser.getId());
    }

    /**
     * Updates a conversation
     *
     * @param conversation the conversation to be updated
     * @return the updated conversation
     */
    public Conversation updateConversation(Conversation conversation) {
        return conversationRepository.save(conversation);
    }

    /**
     * Registers users as a participant of a conversation
     *
     * @param course       the course in which the conversation is located
     * @param users        the users to be registered
     * @param conversation the conversation in which the users are registered
     * @param memberLimit  the maximum number of members in the conversation
     */
    public void registerUsersToConversation(Course course, Set<User> users, Conversation conversation, Optional<Integer> memberLimit) {
        var existingUsers = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        var usersToBeRegistered = users.stream().filter(user -> !existingUsers.contains(user)).collect(Collectors.toSet());

        if (memberLimit.isPresent()) {
            var currentMemberCount = conversationParticipantRepository.countByConversationId(conversation.getId());
            if (currentMemberCount + usersToBeRegistered.size() > memberLimit.get()) {
                throw new BadRequestAlertException("The maximum number of members has been reached", "conversation", "memberLimitReached");
            }
        }
        Set<ConversationParticipant> newConversationParticipants = new HashSet<>();
        for (User user : usersToBeRegistered) {
            ConversationParticipant conversationParticipant = new ConversationParticipant();
            conversationParticipant.setUser(user);
            conversationParticipant.setConversation(conversation);
            conversationParticipant.setIsModerator(false);
            conversationParticipant.setIsHidden(false);
            conversationParticipant.setIsFavorite(false);
            // set the last reading time of a participant in the past when creating conversation for the first time!
            conversationParticipant.setLastRead(ZonedDateTime.now().minusYears(2));
            conversationParticipant.setUnreadMessagesCount(0L);
            newConversationParticipants.add(conversationParticipant);
        }
        if (!newConversationParticipants.isEmpty()) {
            conversationParticipantRepository.saveAll(newConversationParticipants);
            broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, conversation, usersToBeRegistered);
            broadcastOnConversationMembershipChannel(course, MetisCrudAction.UPDATE, conversation, existingUsers);
        }
    }

    /**
     * Notify all members of a conversation about an update to the conversation
     *
     * @param conversation conversation which members to notify
     */
    public void notifyAllConversationMembersAboutUpdate(Conversation conversation) {
        var usersToContact = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        broadcastOnConversationMembershipChannel(conversation.getCourse(), MetisCrudAction.UPDATE, conversation, usersToContact);
    }

    /**
     * Notify all members of a conversation about a new message in the conversation
     *
     * @param conversation conversation which members to notify about the new message (except the author)
     * @param author       author of the new message to filter out
     */
    public void notifyAllConversationMembersAboutNewMessage(Conversation conversation, User author) {
        var usersToContact = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        // filter out the author of the message
        usersToContact.remove(author);
        broadcastOnConversationMembershipChannel(conversation.getCourse(), MetisCrudAction.NEW_MESSAGE, conversation, usersToContact);
    }

    /**
     * Removes users from a conversation
     *
     * @param course       the course in which the conversation is located
     * @param users        the users to be removed
     * @param conversation the conversation from which the users are removed
     */
    public void deregisterUsersFromAConversation(Course course, Set<User> users, Conversation conversation) {
        var existingUsers = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        var usersToBeDeregistered = users.stream().filter(existingUsers::contains).collect(Collectors.toSet());
        var remainingUsers = existingUsers.stream().filter(user -> !usersToBeDeregistered.contains(user)).collect(Collectors.toSet());
        var participantsToRemove = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(conversation.getId(),
                usersToBeDeregistered.stream().map(User::getId).collect(Collectors.toSet()));
        if (participantsToRemove.size() > 0) {
            conversationParticipantRepository.deleteAll(participantsToRemove);
            broadcastOnConversationMembershipChannel(course, MetisCrudAction.DELETE, conversation, usersToBeDeregistered);
            broadcastOnConversationMembershipChannel(course, MetisCrudAction.UPDATE, conversation, remainingUsers);
        }
    }

    /**
     * Delete a conversation
     *
     * @param conversation the conversation to be deleted
     */
    @Transactional // ok because of delete
    public void deleteConversation(Conversation conversation) {
        var usersToMessage = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        broadcastOnConversationMembershipChannel(conversation.getCourse(), MetisCrudAction.DELETE, conversation, usersToMessage);
        this.postRepository.deleteAllByConversationId(conversation.getId());
        this.conversationParticipantRepository.deleteAllByConversationId(conversation.getId());
        this.conversationRepository.deleteById(conversation.getId());
    }

    /**
     * Broadcasts a message on the conversation membership channel of users
     *
     * @param course          the course in which the conversation is located
     * @param metisCrudAction the action that was performed
     * @param conversation    the conversation that was affected
     * @param usersToMessage  the users to be messaged
     */
    public void broadcastOnConversationMembershipChannel(Course course, MetisCrudAction metisCrudAction, Conversation conversation, Set<User> usersToMessage) {
        String conversationParticipantTopicName = getConversationParticipantTopicName(course.getId());
        usersToMessage.forEach(user -> sendToConversationMembershipChannel(metisCrudAction, conversation, user, conversationParticipantTopicName));
    }

    /**
     * Deregister all clients from the exercise channel of the given exercise
     *
     * @param exercise the exercise that is being deleted
     */
    public void deregisterAllClientsFromChannel(Exercise exercise) {
        // deregister all clients from the channel
        Channel originalChannel = channelRepository.findChannelByExerciseId(exercise.getId());
        if (exercise.isCourseExercise() && originalChannel != null) {
            Set<ConversationParticipant> channelParticipants = conversationParticipantRepository.findConversationParticipantByConversationId(originalChannel.getId());
            Set<User> usersToBeDeregistered = channelParticipants.stream().map(ConversationParticipant::getUser).collect(Collectors.toSet());
            broadcastOnConversationMembershipChannel(originalChannel.getCourse(), MetisCrudAction.DELETE, originalChannel, usersToBeDeregistered);
        }
    }

    /**
     * Deregister all clients from the lecture channel of the given exercise
     *
     * @param lecture the lecture that is being deleted
     */
    public void deregisterAllClientsFromChannel(Lecture lecture) {
        // deregister all clients from the channel
        Channel originalChannel = channelRepository.findChannelByLectureId(lecture.getId());
        if (originalChannel != null) {
            Set<ConversationParticipant> channelParticipants = conversationParticipantRepository.findConversationParticipantByConversationId(originalChannel.getId());
            Set<User> usersToBeDeregistered = channelParticipants.stream().map(ConversationParticipant::getUser).collect(Collectors.toSet());
            broadcastOnConversationMembershipChannel(lecture.getCourse(), MetisCrudAction.DELETE, originalChannel, usersToBeDeregistered);
        }
    }

    @NotNull
    public static String getConversationParticipantTopicName(Long courseId) {
        return METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + courseId + "/conversations/user/";
    }

    private void sendToConversationMembershipChannel(MetisCrudAction metisCrudAction, Conversation conversation, User user, String conversationParticipantTopicName) {
        ConversationDTO dto;
        if (metisCrudAction.equals(MetisCrudAction.NEW_MESSAGE)) {
            // we do not want to recalculate the whole dto for a new message, just the information needed for updating the unread messages
            dto = conversationDTOService.convertToDTOWithNoExtraDBCalls(conversation);
        }
        else {
            dto = conversationDTOService.convertToDTO(conversation, user);
        }

        var websocketDTO = new ConversationWebsocketDTO(dto, metisCrudAction);
        messagingTemplate.convertAndSendToUser(user.getLogin(), conversationParticipantTopicName + user.getId(), websocketDTO);
    }

    /**
     * Checks if a user is a member of a conversation and therefore can access it else throws an exception
     *
     * @param conversationId the id of the conversation
     * @param user           the user to check
     * @return conversation if the user is a member
     */
    public Conversation mayInteractWithConversationElseThrow(Long conversationId, User user) {
        Optional<Conversation> conversation = conversationRepository.findById(conversationId);
        if (conversation.isEmpty() || !isMember(conversationId, user.getId())) {
            throw new AccessForbiddenException("User not allowed to access this conversation!");
        }
        return conversation.get();
    }

    /**
     * Search for members of a conversation
     *
     * @param course       the course in which the conversation is located
     * @param conversation the conversation
     * @param pageable     the pagination information
     * @param searchTerm   the search term to search name or login for
     * @param filter       additional filter to filter by role
     * @return the list of found users that match the criteria
     */
    public Page<User> searchMembersOfConversation(Course course, Conversation conversation, Pageable pageable, String searchTerm,
            Optional<ConversationMemberSearchFilters> filter) {
        if (filter.isEmpty()) {
            return userRepository.searchAllByLoginOrNameInConversation(pageable, searchTerm, conversation.getId());
        }
        else {
            switch (filter.get()) {
                case INSTRUCTOR -> {
                    return userRepository.searchAllByLoginOrNameInConversationWithCourseGroup(pageable, searchTerm, conversation.getId(), course.getInstructorGroupName());
                }
                case TUTOR -> {
                    // searches for both tutors and editors
                    return userRepository.searchAllByLoginOrNameInConversationWithEitherCourseGroup(pageable, searchTerm, conversation.getId(), course.getEditorGroupName(),
                            course.getTeachingAssistantGroupName());
                }
                case STUDENT -> {
                    return userRepository.searchAllByLoginOrNameInConversationWithCourseGroup(pageable, searchTerm, conversation.getId(), course.getStudentGroupName());
                }
                case CHANNEL_MODERATOR -> {
                    assert conversation instanceof Channel : "The filter CHANNEL_MODERATOR is only allowed for channels!";
                    return userRepository.searchChannelModeratorsByLoginOrNameInConversation(pageable, searchTerm, conversation.getId());
                }
                default -> throw new IllegalArgumentException("The filter is not supported.");
            }
        }

    }

    /**
     * Switch the favorite status of a conversation for a user
     *
     * @param conversationId the id of the conversation
     * @param requestingUser the user that wants to switch the favorite status
     * @param isFavorite     the new favorite status
     */
    public void switchFavoriteStatus(Long conversationId, User requestingUser, Boolean isFavorite) {
        var participation = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserIdElseThrow(conversationId, requestingUser.getId());
        participation.setIsFavorite(isFavorite);
        conversationParticipantRepository.save(participation);
    }

    /**
     * Switch the hidden status of a conversation for a user
     *
     * @param conversationId the id of the conversation
     * @param requestingUser the user that wants to switch the hidden status
     * @param hiddenStatus   the new hidden status
     */
    public void switchHiddenStatus(Long conversationId, User requestingUser, Boolean hiddenStatus) {
        var participation = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserIdElseThrow(conversationId, requestingUser.getId());
        participation.setIsHidden(hiddenStatus);
        conversationParticipantRepository.save(participation);
    }

    /**
     * The user can select one of these roles to filter the conversation members by role
     */
    public enum ConversationMemberSearchFilters {
        INSTRUCTOR, EDITOR, TUTOR, STUDENT, CHANNEL_MODERATOR // this is a special role that is only used for channels
    }

    /**
     * Find users with a certain role in a course
     *
     * @param course             the course
     * @param findAllStudents    if true, result includes all users with the student role in the course
     * @param findAllTutors      if true, result includes all users with the tutor role in the course
     * @param findAllInstructors if true, result includes all users with the instructor role in the course
     * @return the list of users found
     */
    public Set<User> findUsersInDatabase(Course course, boolean findAllStudents, boolean findAllTutors, boolean findAllInstructors) {
        Set<User> users = new HashSet<>();
        if (findAllStudents) {
            users.addAll(userRepository.findAllInGroupWithAuthorities(course.getStudentGroupName()));
        }
        if (findAllTutors) {
            users.addAll(userRepository.findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName()));
            users.addAll(userRepository.findAllInGroupWithAuthorities(course.getEditorGroupName()));
        }
        if (findAllInstructors) {
            users.addAll(userRepository.findAllInGroupWithAuthorities(course.getInstructorGroupName()));
        }
        return users;
    }

    /**
     * Find users in database by their login
     *
     * @param userLogins the logins to search users by
     * @return set of users with the given logins
     */
    public Set<User> findUsersInDatabase(@RequestBody List<String> userLogins) {
        Set<User> users = new HashSet<>();
        for (String userLogin : userLogins) {
            if (userLogin == null || userLogin.isEmpty()) {
                continue;
            }
            var userToRegister = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
            userToRegister.ifPresent(users::add);
        }
        return users;
    }

    /**
     * Find all conversations for which the given user should be able to receive notifications.
     *
     * @param user                    The user for which to find the courses.
     * @param unreadConversationsOnly Whether to only return conversations that have unread messages.
     * @return A list of conversations for which the user should receive notifications.
     */
    public List<Conversation> findAllConversationsForNotifications(User user, boolean unreadConversationsOnly) {
        if (unreadConversationsOnly) {
            return conversationRepository.findAllUnreadConversationsWhereUserIsParticipant(user.getId());
        }
        return conversationRepository.findAllWhereUserIsParticipant(user.getId());
    }

    /**
     * Filter all channels where the attached lecture/exercise has been released
     *
     * @param channels A stream of channels
     * @return A stream of channels for lectures/exercises that have been released
     */
    public Stream<Channel> filterVisibleChannelsForStudents(Stream<Channel> channels) {
        return channels.filter(channel -> {
            if (channel.getExercise() != null) {
                return channel.getExercise().isReleased();
            }
            else if (channel.getExam() != null) {
                return channel.getExam().isVisibleToStudents();
            }
            else {
                return true;
            }
        });
    }
}
