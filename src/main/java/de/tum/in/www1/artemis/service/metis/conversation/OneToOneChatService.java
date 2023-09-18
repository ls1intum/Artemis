package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class OneToOneChatService {

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final OneToOneChatRepository oneToOneChatRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public OneToOneChatService(ConversationParticipantRepository conversationParticipantRepository, OneToOneChatRepository oneToOneChatRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.oneToOneChatRepository = oneToOneChatRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Creates a new OneToOneChat between two users
     * <p>
     * Note: if a OneToOneChat between the two users already exists, it will be returned instead of a new one
     *
     * @param course the course the OneToOneChat is in
     * @param userA  the first user
     * @param userB  the second user
     * @return the newly created OneToOneChat or the existing one
     */
    public OneToOneChat startOneToOneChat(Course course, User userA, User userB) {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var existingChatBetweenUsers = oneToOneChatRepository.findBetweenUsersWithParticipantsAndUserGroups(course.getId(), userA.getId(), userB.getId());
        if (existingChatBetweenUsers.isPresent()) {
            return existingChatBetweenUsers.get();
        }

        // Check that both users are a member of the course
        if (!authorizationCheckService.isAtLeastStudentInCourse(course, userA) || !authorizationCheckService.isAtLeastStudentInCourse(course, userB)) {
            throw new BadRequestAlertException("A one-to-one chat can only be started with two members of the course", "OneToOneChat", "invalidUserLogin");
        }

        var oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        oneToOneChat.setCreator(requestingUser);
        var savedChat = oneToOneChatRepository.save(oneToOneChat);

        ConversationParticipant participationOfUserA = ConversationParticipant.createWithDefaultValues(userA, oneToOneChat);
        ConversationParticipant participationOfUserB = ConversationParticipant.createWithDefaultValues(userB, oneToOneChat);
        conversationParticipantRepository.saveAll(List.of(participationOfUserA, participationOfUserB));
        return oneToOneChatRepository.save(savedChat);
    }
}
