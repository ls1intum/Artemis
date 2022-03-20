package de.tum.in.www1.artemis.service.metis;

import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ChatSession;
import de.tum.in.www1.artemis.domain.metis.UserChatSession;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ChatSessionRepository;
import de.tum.in.www1.artemis.repository.metis.UserChatSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ChatSessionDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.CrudAction;

@Service
public class ChatService {

    private static final String METIS_CHAT_SESSION_ENTITY_NAME = "metis.chatSession";

    private static final String METIS_USER_CHAT_SESSION_ENTITY_NAME = "metis.userChatSession";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserChatSessionRepository userChatSessionRepository;

    private final ChatSessionRepository chatSessionRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public ChatService(UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            UserChatSessionRepository userChatSessionRepository, ChatSessionRepository chatSessionRepository, SimpMessageSendingOperations messagingTemplate) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userChatSessionRepository = userChatSessionRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Persists given chatSession
     *
     * @param courseId      id of course the chatSessions belongs to
     * @param chatSession   chatSession to be persisted
     * @return              persisted chatSession
     */
    public ChatSession createChatSession(Long courseId, ChatSession chatSession) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        if (chatSession.getId() != null) {
            throw new BadRequestAlertException("A new chat session cannot already have an ID", METIS_CHAT_SESSION_ENTITY_NAME, "idexists");
        }

        if (chatSession.getUserChatSessions().isEmpty()) {
            throw new BadRequestAlertException("A new chat session must have userChatSession", METIS_USER_CHAT_SESSION_ENTITY_NAME, "NotNull");
        }

        final Course course = preCheckUserAndCourse(user, courseId);
        chatSession.setCourse(course);

        UserChatSession userChatSessionOfCurrentUser = new UserChatSession();
        userChatSessionOfCurrentUser.setUser(user);
        chatSession.getUserChatSessions().add(userChatSessionOfCurrentUser);

        chatSession.getUserChatSessions().forEach(userChatSession -> userChatSession = createUserChatSession(userChatSession, chatSession));

        ChatSession savedChatSession = chatSessionRepository.save(chatSession);

        // informs involved users about a new chat session
        broadcastForChatSession(new ChatSessionDTO(savedChatSession, CrudAction.CREATE));

        return savedChatSession;
    }

    /**
     * Retrieve chat sessions from database by userId and courseId
     *
     * @param courseId id of course the chatSessions belongs to
     * @return retrieved chat sessions
     */
    public List<ChatSession> getChatSessions(Long courseId) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        return chatSessionRepository.getChatSessionsOfUser(courseId, user.getId());
    }

    /**
     * Broadcasts a session related event in a course under a specific topic via websockets
     *
     * @param chatSessionDTO object including the affected chatSession as well as the action
     */
    private void broadcastForChatSession(ChatSessionDTO chatSessionDTO) {
        String courseTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + chatSessionDTO.getChatSession().getCourse().getId();
        String userChatSessionTopicName = courseTopicName + "/chatSessions/user/";

        chatSessionDTO.getChatSession().getUserChatSessions()
                .forEach(userChatSession -> messagingTemplate.convertAndSend(userChatSessionTopicName + userChatSession.getUser().getId(), chatSessionDTO));
    }

    /**
     * Helper method that persists a userChatSession
     * @param userChatSession   userChatSession to be persisted
     * @param chatSession       chatSession in association with userChatSession
     * @return                  persisted userChatSession
     */
    private UserChatSession createUserChatSession(UserChatSession userChatSession, ChatSession chatSession) {
        userChatSession.setLastRead(chatSession.getLastMessageDate());
        return userChatSessionRepository.save(userChatSession);
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
     */
    public String getEntityName() {
        return METIS_CHAT_SESSION_ENTITY_NAME;
    }
}
