package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;

@Service
public class GroupChatService {

    public static final String GROUP_CHAT_ENTITY_NAME = "messages.groupchat";

    private final UserRepository userRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final GroupChatRepository groupChatRepository;

    public GroupChatService(UserRepository userRepository, ConversationParticipantRepository conversationParticipantRepository, GroupChatRepository groupChatRepository) {
        this.userRepository = userRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.groupChatRepository = groupChatRepository;
    }

    public GroupChat startGroupChat(Course course, Set<User> startingMembers) {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var groupChat = new GroupChat();
        groupChat.setCourse(course);
        groupChat.setCreator(requestingUser);
        var savedGroupChat = groupChatRepository.save(groupChat);
        var participantsToCreate = startingMembers.stream().map(user -> createChatParticipant(user, groupChat)).toList();
        conversationParticipantRepository.saveAll(participantsToCreate);
        return savedGroupChat;
    }

    @NotNull
    private ConversationParticipant createChatParticipant(User user, GroupChat groupChat) {
        var participant = new ConversationParticipant();
        participant.setUser(user);
        participant.setConversation(groupChat);
        return participant;
    }

}
