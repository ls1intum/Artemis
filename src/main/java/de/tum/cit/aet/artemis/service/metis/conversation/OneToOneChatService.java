package de.tum.cit.aet.artemis.service.metis.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.metis.ConversationParticipant;
import de.tum.cit.aet.artemis.domain.metis.conversation.OneToOneChat;

@Profile(PROFILE_CORE)
@Service
public class OneToOneChatService {

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final OneToOneChatRepository oneToOneChatRepository;

    private final UserRepository userRepository;

    public OneToOneChatService(ConversationParticipantRepository conversationParticipantRepository, OneToOneChatRepository oneToOneChatRepository, UserRepository userRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.oneToOneChatRepository = oneToOneChatRepository;
        this.userRepository = userRepository;
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
        var existingChatBetweenUsers = oneToOneChatRepository.findWithParticipantsAndUserGroupsInCourseBetweenUsers(course.getId(), userA.getId(), userB.getId());
        if (existingChatBetweenUsers.isPresent()) {
            OneToOneChat chat = existingChatBetweenUsers.get();
            if (chat.getLastMessageDate() == null && !requestingUser.getId().equals(chat.getCreator().getId())) {
                chat.setCreator(requestingUser);
                return oneToOneChatRepository.save(existingChatBetweenUsers.get());
            }
            return existingChatBetweenUsers.get();
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
