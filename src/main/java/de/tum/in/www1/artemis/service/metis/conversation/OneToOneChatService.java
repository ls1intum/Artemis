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
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.OneToOneChatDTO;

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

    public Optional<OneToOneChat> findOneToOneChatWithSameMembers(Long courseId, Long userAId, Long userBId) {
        return oneToOneChatRepository.findWithSameMembers(courseId, userAId, userBId);
    }

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
        var participants = conversationParticipantRepository.saveAll(List.of(participationOfUserA, participationOfUserB));
        savedChat.getConversationParticipants().addAll(participants);
        return oneToOneChatRepository.save(savedChat);
    }

    public OneToOneChatDTO convertToDTO(OneToOneChat oneToOneChat, User requestingUser) {
        var oneToOneChatFromDB = oneToOneChatRepository.findByIdWithConversationParticipantsAndGroupsElseThrow(oneToOneChat.getId());
        var oneToOneChatDTO = new OneToOneChatDTO(oneToOneChatFromDB);
        oneToOneChatDTO.setIsMember(oneToOneChatFromDB.getConversationParticipants().stream().anyMatch(participant -> participant.getUser().equals(requestingUser)));
        oneToOneChatDTO.setNumberOfMembers(oneToOneChatFromDB.getConversationParticipants().size());
        return oneToOneChatDTO;
    }

    @NotNull
    private ConversationParticipant createChatParticipant(User user, OneToOneChat oneToOneChat) {
        var participant = new ConversationParticipant();
        participant.setUser(user);
        participant.setConversation(oneToOneChat);
        return participant;
    }
}
