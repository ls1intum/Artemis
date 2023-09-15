package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
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

    private final WebsocketMessagingService websocketMessagingService;

    private final OneToOneChatRepository oneToOneChatRepository;

    private final PostRepository postRepository;

    private final GroupChatRepository groupChatRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public ConversationService(ConversationDTOService conversationDTOService, UserRepository userRepository, ChannelRepository channelRepository,
            ConversationParticipantRepository conversationParticipantRepository, ConversationRepository conversationRepository, WebsocketMessagingService websocketMessagingService,
            OneToOneChatRepository oneToOneChatRepository, PostRepository postRepository, GroupChatRepository groupChatRepository,
            AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.conversationDTOService = conversationDTOService;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.websocketMessagingService = websocketMessagingService;
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
     * Checks if a user is a member of a conversation and therefore can access it else throws an exception
     *
     * @param conversationId the id of the conversation
     * @param userId         the id of the user
     */
    public void isMemberElseThrow(Long conversationId, Long userId) {
        if (!isMember(conversationId, userId)) {
            throw new AccessForbiddenException("User not allowed to access this conversation!");
        }
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

        var conversationsOfUser = new ArrayList<Conversation>();
        conversationsOfUser.addAll(oneToOneChatsOfUser);
        conversationsOfUser.addAll(groupChatsOfUser);
        Course course = courseRepository.findByIdElseThrow(courseId);
        // if the user is only a student in the course, we filter out all channels that are not yet open
        var isOnlyStudent = authorizationCheckService.isOnlyStudentInCourse(course, requestingUser);
        var filteredChannels = isOnlyStudent ? filterVisibleChannelsForStudents(channelsOfUser.stream()).toList() : channelsOfUser;
        conversationsOfUser.addAll(filteredChannels);

        var conversationIds = conversationsOfUser.stream().map(Conversation::getId).toList();
        var userConversationInfos = conversationRepository.getUserInformationForConversations(conversationIds, requestingUser.getId()).stream()
                .collect(Collectors.toMap(UserConversationInfo::getConversationId, Function.identity()));
        var generalConversationInfos = conversationRepository.getGeneralInformationForConversations(conversationIds).stream()
                .collect(Collectors.toMap(GeneralConversationInfo::getConversationId, Function.identity()));

        Integer numberOfCourseMembers = null;
        for (Channel channel : filteredChannels) {
            if (channel.getIsCourseWide()) {
                if (numberOfCourseMembers == null) {
                    numberOfCourseMembers = courseRepository.countCourseMembers(courseId);
                }
                generalConversationInfos.get(channel.getId()).setNumberOfParticipants(numberOfCourseMembers);
            }
        }

        Stream<ConversationSummary> conversationSummaries = conversationsOfUser.stream()
                .map(conversation -> new ConversationSummary(conversation, userConversationInfos.get(conversation.getId()), generalConversationInfos.get(conversation.getId())));

        return conversationSummaries.map(summary -> conversationDTOService.convertToDTO(summary, requestingUser)).toList();
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
            ConversationParticipant conversationParticipant = ConversationParticipant.createWithDefaultValues(user, conversation);
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
     * @param course       the course in which the conversation takes place
     * @param conversation conversation which members to notify about the new message (except the author)
     * @param recipients   users to which the notification should be sent
     */
    public void notifyAllConversationMembersAboutNewMessage(Course course, Conversation conversation, Set<User> recipients) {
        broadcastOnConversationMembershipChannel(course, MetisCrudAction.NEW_MESSAGE, conversation, recipients);
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
    public void deleteConversation(Conversation conversation) {
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
     * @param recipients      the users to be messaged
     */
    public void broadcastOnConversationMembershipChannel(Course course, MetisCrudAction metisCrudAction, Conversation conversation, Set<User> recipients) {
        String conversationParticipantTopicName = getConversationParticipantTopicName(course.getId());
        recipients.forEach(user -> sendToConversationMembershipChannel(metisCrudAction, conversation, user, conversationParticipantTopicName));
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
        websocketMessagingService.sendMessageToUser(user.getLogin(), conversationParticipantTopicName + user.getId(), websocketDTO);
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
            if (conversation instanceof Channel && ((Channel) conversation).getIsCourseWide()) {
                return userRepository.searchAllByLoginOrNameInCourse(pageable, searchTerm, course.getId());
            }
            return userRepository.searchAllByLoginOrNameInConversation(pageable, searchTerm, conversation.getId());
        }
        else {
            var groups = new HashSet<String>();
            switch (filter.get()) {
                case INSTRUCTOR -> groups.add(course.getInstructorGroupName());
                case TUTOR -> {
                    groups.add(course.getTeachingAssistantGroupName());
                    // searching for tutors also searches for editors
                    groups.add(course.getEditorGroupName());
                }
                case STUDENT -> groups.add(course.getStudentGroupName());
                case CHANNEL_MODERATOR -> {
                    assert conversation instanceof Channel : "The filter CHANNEL_MODERATOR is only allowed for channels!";
                    return userRepository.searchChannelModeratorsByLoginOrNameInConversation(pageable, searchTerm, conversation.getId());
                }
                default -> throw new IllegalArgumentException("The filter is not supported.");
            }

            if (conversation instanceof Channel && ((Channel) conversation).getIsCourseWide()) {
                return userRepository.searchAllByLoginOrNameInGroups(pageable, searchTerm, groups);
            }

            return userRepository.searchAllByLoginOrNameInConversationWithCourseGroups(pageable, searchTerm, conversation.getId(), groups);
        }

    }

    /**
     * Switch the favorite status of a conversation for a user
     *
     * @param conversationId the id of the conversation
     * @param requestingUser the user that wants to switch the favorite status
     * @param favoriteStatus the new favorite status
     */
    public void switchFavoriteStatus(Long conversationId, User requestingUser, Boolean favoriteStatus) {
        ConversationParticipant conversationParticipant = getOrCreateConversationParticipant(conversationId, requestingUser);
        conversationParticipant.setIsFavorite(favoriteStatus);
        conversationParticipantRepository.save(conversationParticipant);
    }

    /**
     * Switch the hidden status of a conversation for a user
     *
     * @param conversationId the id of the conversation
     * @param requestingUser the user that wants to switch the hidden status
     * @param hiddenStatus   the new hidden status
     */
    public void switchHiddenStatus(Long conversationId, User requestingUser, Boolean hiddenStatus) {
        ConversationParticipant conversationParticipant = getOrCreateConversationParticipant(conversationId, requestingUser);
        conversationParticipant.setIsHidden(hiddenStatus);
        conversationParticipantRepository.save(conversationParticipant);
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
     * Filter all channels where the attached lecture/exercise has been released
     *
     * @param channels a stream of channels
     * @return a stream of channels without channels belonging to unreleased lectures/exercises/exams
     */
    public Stream<Channel> filterVisibleChannelsForStudents(Stream<Channel> channels) {
        return channels.filter(this::isChannelVisibleToStudents);
    }

    /**
     * Determines whether the provided channel is visible to students.
     * <p>
     * If the channel is not associated with a lecture/exam/exercise, then this method returns true.
     * If it is connected to a lecture/exam/exercise, then the
     * channel visibility depends on the visible date of the lecture/exam/exercise.
     *
     * @param channel the channel under consideration
     * @return true if the channel is visible to students
     */
    public boolean isChannelVisibleToStudents(@NotNull Channel channel) {
        if (channel.getLecture() != null) {
            return channel.getLecture().isVisibleToStudents();
        }
        else if (channel.getExercise() != null) {
            return channel.getExercise().isVisibleToStudents();
        }
        else if (channel.getExam() != null) {
            return channel.getExam().isVisibleToStudents();
        }
        return true;
    }

    private ConversationParticipant getOrCreateConversationParticipant(Long conversationId, User requestingUser) {
        var participation = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, requestingUser.getId());

        if (participation.isEmpty()) {
            Conversation conversation = conversationRepository.findByIdElseThrow(conversationId);

            if (conversation instanceof Channel channel && channel.getIsCourseWide()) {
                return ConversationParticipant.createWithDefaultValues(requestingUser, channel);
            }
            else {
                throw new AccessForbiddenException("User not allowed to access this conversation!");
            }
        }
        else {
            return participation.get();
        }
    }
}
