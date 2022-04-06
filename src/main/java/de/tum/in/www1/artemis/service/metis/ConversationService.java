package de.tum.in.www1.artemis.service.metis;

import java.util.HashSet;
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
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.CrudAction;

@Service
public class ConversationService {

    private static final String CONVERSATION_ENTITY_NAME = "messages.conversation";

    private static final String CONVERSATION_DETAILS_ENTITY_NAME = "messages.conversationParticipant";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ConversationRepository conversationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public ConversationService(UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            ConversationParticipantRepository conversationParticipantRepository, ConversationRepository conversationRepository, SimpMessageSendingOperations messagingTemplate) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Persists given conversation
     *
     * @param courseId      id of course the conversation belongs to
     * @param conversation  conversation to be persisted
     * @return              persisted conversation
     */
    public Conversation createConversation(Long courseId, Conversation conversation) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        if (conversation.getId() != null) {
            throw new BadRequestAlertException("A new conversation cannot already have an ID", CONVERSATION_ENTITY_NAME, "idexists");
        }

        if (conversation.getConversationParticipants().isEmpty()) {
            throw new BadRequestAlertException("A new conversation must have conversationParticipants", CONVERSATION_DETAILS_ENTITY_NAME, "NotNull");
        }

        final Course course = preCheckUserAndCourse(user, courseId);
        conversation.setCourse(course);

        Conversation savedConversation = conversationRepository.save(conversation);

        ConversationParticipant conversationParticipantOfCurrentUser = new ConversationParticipant();
        conversationParticipantOfCurrentUser.setUser(user);
        conversation.getConversationParticipants().add(conversationParticipantOfCurrentUser);

        conversation.getConversationParticipants()
                .forEach(conversationParticipant -> conversationParticipant = createConversationParticipant(conversationParticipant, savedConversation));

        // informs involved users about a new conversation
        broadcastForConversation(new ConversationDTO(savedConversation, CrudAction.CREATE));

        return savedConversation;
    }

    public Conversation getConversationById(Long conversationId) {
        Optional<Conversation> conversation = conversationRepository.findById(conversationId);
        conversation.ifPresent(conversation1 -> conversation1
                .setConversationParticipants(new HashSet<>(conversationParticipantRepository.findConversationParticipantByConversationId(conversationId))));

        return conversation.get();
    }

    /**
     * fetch conversations from database by userId and courseId
     *
     * @param courseId  id of course the conversations belongs to
     * @return          fetched conversations
     */
    public List<Conversation> getConversationsOfUser(Long courseId) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        List<Conversation> conversations = conversationRepository.getConversationsOfUser(courseId, user.getId());

        conversations.forEach(conversation -> {
            List<ConversationParticipant> conversationParticipants = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId());
            conversationParticipants.forEach(conversationParticipant -> {
                if (!conversationParticipant.getUser().getId().equals(user.getId())) {
                    conversationParticipant.filterSensitiveInformation();
                }
            });
            conversation.setConversationParticipants(new HashSet<>(conversationParticipants));
        });

        return conversations;
    }

    /**
     * Broadcasts a conversation event in a course under a specific topic via websockets
     *
     * @param conversationDTO object including the affected conversation as well as the action
     */
    private void broadcastForConversation(ConversationDTO conversationDTO) {
        String courseTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + conversationDTO.getConversation().getCourse().getId();
        String conversationParticipantTopicName = courseTopicName + "/conversations/user/";

        conversationDTO.getConversation().getConversationParticipants().forEach(
                conversationParticipant -> messagingTemplate.convertAndSend(conversationParticipantTopicName + conversationParticipant.getUser().getId(), conversationDTO));
    }

    /**
     * Helper method that persists a conversationParticipant
     * @param conversationParticipant   conversationParticipant to be persisted
     * @param conversation              conversation in association with the conversationParticipant
     * @return                          persisted conversationParticipant
     */
    private ConversationParticipant createConversationParticipant(ConversationParticipant conversationParticipant, Conversation conversation) {
        conversationParticipant.setConversation(conversation);
        conversationParticipant.setClosed(false);
        conversationParticipant.setLastRead(conversation.getLastMessageDate());
        return conversationParticipantRepository.save(conversationParticipant);
    }

    Course preCheckUserAndCourse(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to be at least student in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // check if course has posts enabled
        if (!course.getPostsEnabled()) {
            throw new BadRequestAlertException("Postings are not enabled for this course", getEntityName(), "400", true);
        }
        return course;
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     *
     * @return conversation entity name
     */
    public String getEntityName() {
        return CONVERSATION_ENTITY_NAME;
    }
}
