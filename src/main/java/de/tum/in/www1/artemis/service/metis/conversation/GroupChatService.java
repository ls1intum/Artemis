package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

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

    public GroupChat findByIdWithConversationParticipantsElseThrow(Long groupChatId) {
        return groupChatRepository.findByIdWithConversationParticipantsElseThrow(groupChatId);
    }

    public GroupChat createNewGroupChat(Course course, GroupChat newGroupChat) {
        var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();

        if (newGroupChat.getId() != null) {
            throw new BadRequestAlertException("A new group chat cannot already have an ID", GROUP_CHAT_ENTITY_NAME, "idexists");
        }

        var hasParticipants = newGroupChat.getConversationParticipants() != null && !newGroupChat.getConversationParticipants().isEmpty() && newGroupChat
                .getConversationParticipants().stream().anyMatch(conversationParticipant -> !conversationParticipant.getUser().getId().equals(requestingUser.getId()));

        if (!hasParticipants) {
            throw new BadRequestAlertException("A new group chat must have other conversation participants than just the requesting user", GROUP_CHAT_ENTITY_NAME,
                    "invalidconversationparticipants");
        }
        // filter out requesting user from participants if sent -> we will create it below
        var participants = new HashSet<>(newGroupChat.getConversationParticipants());
        participants.removeIf(participant -> participant.getUser().getId().equals(requestingUser.getId()));
        newGroupChat.setConversationParticipants(participants);

        var existingGroupChatsOfUser = groupChatRepository.findActiveGroupChatsOfUserWithConversationParticipants(course.getId(), requestingUser.getId());

        // find out if there already exists a group chat with the exact same participants (other than the requesting user)
        var existingGroupChatOptional = existingGroupChatsOfUser.stream().filter(groupChat -> {
            var groupChatParticipantsWithoutRequestingUser = groupChat.getConversationParticipants();
            groupChatParticipantsWithoutRequestingUser.removeIf(participant -> participant.getUser().getId().equals(requestingUser.getId()));
            if (groupChatParticipantsWithoutRequestingUser.size() != newGroupChat.getConversationParticipants().size()) {
                return false;
            }
            return groupChatParticipantsWithoutRequestingUser.stream().map(ConversationParticipant::getUser)
                    .allMatch(user -> newGroupChat.getConversationParticipants().stream().map(ConversationParticipant::getUser).anyMatch(user::equals));
        }).findFirst();

        if (existingGroupChatOptional.isEmpty()) {
            newGroupChat.setCourse(course);
            var savedGroupChat = groupChatRepository.save(newGroupChat);
            var conversationParticipantOfRequestingUser = new ConversationParticipant();
            conversationParticipantOfRequestingUser.setUser(requestingUser);
            conversationParticipantOfRequestingUser.setConversation(savedGroupChat);
            var conversationsOfOtherUsers = newGroupChat.getConversationParticipants();
            conversationsOfOtherUsers.forEach(conversationParticipant -> conversationParticipant.setConversation(savedGroupChat));

            var allConversationParticipants = new ArrayList<>(List.of(conversationParticipantOfRequestingUser));
            allConversationParticipants.addAll(conversationsOfOtherUsers);

            var savedParticipants = new HashSet<>(conversationParticipantRepository.saveAll(allConversationParticipants));
            savedGroupChat.setConversationParticipants(savedParticipants);
            return groupChatRepository.save(savedGroupChat);
        }
        else {
            return existingGroupChatOptional.get();
        }
    }

    public List<String> getNamesOfOtherMembers(GroupChat groupChat, User requestingUser) {
        return groupChat.getConversationParticipants().stream().map(ConversationParticipant::getUser).filter(user -> !user.getId().equals(requestingUser.getId()))
                .map(User::getName).toList();
    }
}
