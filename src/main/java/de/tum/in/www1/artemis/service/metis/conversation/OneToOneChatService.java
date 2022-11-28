package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;

@Service
public class OneToOneChatService {

    public static final String ONE_TO_ONE_CHAT_ENTITY_NAME = "messages.oneToOneChat";

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final OneToOneChatRepository oneToOneChatRepository;

    private final UserRepository userRepository;

    public OneToOneChatService(ConversationParticipantRepository conversationParticipantRepository, OneToOneChatRepository oneToOneChatRepository, UserRepository userRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.oneToOneChatRepository = oneToOneChatRepository;
        this.userRepository = userRepository;
    }

    /**
     * Tries to find a OneToOneChat between two users
     *
     * @param courseId the course the OneToOneChat is in
     * @param userAId  the id of the first user
     * @param userBId  the id of the second user
     * @return the OneToOneChat if it exists, otherwise empty optional
     */
    public Optional<OneToOneChat> findOneToOneChatWithSameMembers(Long courseId, Long userAId, Long userBId) {
        return oneToOneChatRepository.findBetweenUsersWithParticipantsAndUserGroups(courseId, userAId, userBId);
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
        var existingChatBetweenUsers = findOneToOneChatWithSameMembers(course.getId(), userA.getId(), userB.getId());
        if (existingChatBetweenUsers.isPresent()) {
            return existingChatBetweenUsers.get();
        }
        var oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        oneToOneChat.setCreator(requestingUser);
        var savedChat = oneToOneChatRepository.save(oneToOneChat);

        ConversationParticipant participationOfUserA = createChatParticipant(userA, oneToOneChat);
        ConversationParticipant participationOfUserB = createChatParticipant(userB, oneToOneChat);
        conversationParticipantRepository.saveAll(List.of(participationOfUserA, participationOfUserB));
        return oneToOneChatRepository.save(savedChat);
    }

    @NotNull
    private ConversationParticipant createChatParticipant(User user, OneToOneChat oneToOneChat) {
        var participant = new ConversationParticipant();
        participant.setUser(user);
        participant.setConversation(oneToOneChat);
        return participant;
    }
}
