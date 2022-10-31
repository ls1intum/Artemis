package de.tum.in.www1.artemis.service.metis;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationDTO;

@Service
public class ConversationService {

    private static final String CONVERSATION_ENTITY_NAME = "messages.conversation";

    private static final String CONVERSATION_DETAILS_ENTITY_NAME = "messages.conversationParticipant";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ConversationRepository conversationRepository;

    private final ChannelRepository channelRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public ConversationService(UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            ChannelRepository channelRepository, ConversationParticipantRepository conversationParticipantRepository, ConversationRepository conversationRepository,
            SimpMessageSendingOperations messagingTemplate) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.channelRepository = channelRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Persists given conversation
     *
     * @param courseId     id of course the conversation belongs to
     * @param conversation conversation to be persisted
     * @return persisted conversation
     */
    public Conversation createDirectConversation(Long courseId, Conversation conversation) {
        if (!conversation.isDirectConversation()) {
            throw new IllegalArgumentException("Only direct conversations can be created with this method");
        }

        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        if (conversation.getId() != null) {
            throw new BadRequestAlertException("A new conversation cannot already have an ID", CONVERSATION_ENTITY_NAME, "idexists");
        }

        if (conversation.getConversationParticipants().isEmpty()
                || conversation.getConversationParticipants().stream().anyMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()))) {
            throw new BadRequestAlertException("A new conversation must have other conversation participants", CONVERSATION_DETAILS_ENTITY_NAME, "invalidconversationparticipants");
        }

        final Course course = checkUserAndCourse(user, courseId);

        List<Conversation> existingConversations = conversationRepository.findConversationsOfUserWithConversationParticipants(course.getId(), user.getId());
        Optional<Conversation> existingConversation = existingConversations.stream().filter((conversation1) -> conversation1.getConversationParticipants().stream()
                .anyMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(
                        conversation.getConversationParticipants().toArray(new ConversationParticipant[conversation.getConversationParticipants().size()])[0].getUser().getId())))
                .findAny();

        if (!existingConversation.isPresent()) {
            conversation.setCourse(course);

            Conversation savedConversation = conversationRepository.save(conversation);

            ConversationParticipant conversationParticipantOfCurrentUser = new ConversationParticipant();
            conversationParticipantOfCurrentUser.setUser(user);
            conversation.getConversationParticipants().add(conversationParticipantOfCurrentUser);

            conversation.getConversationParticipants().forEach(conversationParticipant -> conversationParticipantToCreate(conversationParticipant, savedConversation));
            conversationParticipantRepository.saveAll(conversation.getConversationParticipants());
            savedConversation.setConversationParticipants(conversation.getConversationParticipants());

            return savedConversation;
        }
        else {
            return existingConversation.get();
        }
    }

    /**
     * fetch conversation from database by conversationId
     *
     * @param conversationId id of the conversation to fetch
     * @return fetched conversation
     */
    public Conversation getConversationById(Long conversationId) {
        return conversationRepository.findConversationByIdWithConversationParticipants(conversationId);
    }

    /**
     * fetch conversations from database by userId and courseId
     *
     * @param courseId id of course the conversations belongs to
     * @return fetched conversations
     */
    public List<Conversation> getConversationsOfUser(Long courseId) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        List<Conversation> conversations = conversationRepository.findActiveConversationsOfUserWithConversationParticipants(courseId, user.getId());
        List<Conversation> channels = channelRepository.findChannelsOfUserWithConversationParticipants(courseId, user.getId());
        var allConversations = new ArrayList<Conversation>();
        allConversations.addAll(conversations);
        allConversations.addAll(channels);
        allConversations.forEach(conversation -> filterSensitiveInformation(conversation, user));

        return allConversations;
    }

    /**
     * used to update the lastMessageDate property of a conversation
     *
     * @param conversation
     */
    public void updateConversation(Conversation conversation) {
        conversationRepository.save(conversation);
    }

    /**
     * Broadcasts a conversation event in a course under a specific topic via websockets
     *
     * @param conversationDTO object including the affected conversation as well as the action
     * @param user            if not null, the user the message is specifically targeted to
     */
    public void broadcastForConversation(ConversationDTO conversationDTO, User user) {
        String courseTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + conversationDTO.getConversation().getCourse().getId();
        String conversationParticipantTopicName = courseTopicName + "/conversations/user/";

        if (user == null) {
            conversationDTO.getConversation().getConversationParticipants()
                    .forEach(conversationParticipant -> messagingTemplate.convertAndSendToUser(conversationParticipant.getUser().getLogin(),
                            conversationParticipantTopicName + conversationParticipant.getUser().getId(), conversationDTO));
        }
        else {
            messagingTemplate.convertAndSendToUser(user.getLogin(), conversationParticipantTopicName + user.getId(), conversationDTO);
        }
    }

    Conversation mayInteractWithConversationElseThrow(Long conversationId, User user) {
        // use object fetched from database
        Conversation conversation = conversationRepository.findConversationByIdWithConversationParticipants(conversationId);
        if (conversation == null
                || conversation.getConversationParticipants().stream().noneMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()))) {
            throw new AccessForbiddenException("User not allowed to access this conversation!");
        }

        return conversation;
    }

    /**
     * Helper method that prepares a conversationParticipant that will later be persisted
     *
     * @param conversationParticipant conversationParticipant to be created
     * @param conversation            conversation in association with the conversationParticipant
     * @return returned conversationParticipant ready to be persisted
     */
    private ConversationParticipant conversationParticipantToCreate(ConversationParticipant conversationParticipant, Conversation conversation) {
        conversationParticipant.setConversation(conversation);
        conversationParticipant.setLastRead(conversation.getLastMessageDate());
        return conversationParticipant;
    }

    Course checkUserAndCourse(User user, Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to at least have student role in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        return course;
    }

    /**
     * filters sensitive information such as last read times of other users
     *
     * @param user          user whose sensitive information will be preserved
     * @param conversation  object to be filtered for sensitive information
     */
    static void filterSensitiveInformation(Conversation conversation, User user) {
        conversation.getConversationParticipants().forEach(conversationParticipant -> {
            if (!conversationParticipant.getUser().getId().equals(user.getId())) {
                conversationParticipant.filterSensitiveInformation();
            }
        });
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     *
     * @return conversation entity name
     */
    public String getEntityName() {
        return CONVERSATION_ENTITY_NAME;
    }

    ZonedDateTime auditConversationReadTimeOfUser(Conversation conversation, User user) {
        // update the last time user has read the conversation
        ConversationParticipant readingParticipant = conversation.getConversationParticipants().stream()
                .filter(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId())).findAny().get();
        readingParticipant.setLastRead(ZonedDateTime.now());
        conversationParticipantRepository.save(readingParticipant);
        return readingParticipant.getLastRead();
    }
}
