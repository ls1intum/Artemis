package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.List;

import org.springframework.stereotype.Service;

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

    public List<String> getNamesOfOtherMembers(GroupChat oneToOneChat, User requestingUser) {
        return oneToOneChat.getConversationParticipants().stream().map(ConversationParticipant::getUser).filter(user -> !user.getId().equals(requestingUser.getId()))
                .map(User::getName).toList();
    }

    // public GroupChat createNewGroupChat(Course course, OneToOneChat newOneToOneChat) {
    // var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();
    //
    // if (newOneToOneChat.getId() != null) {
    // throw new BadRequestAlertException("A new group chat cannot already have an ID", GROUP_CHAT_ENTITY_NAME, "idexists");
    // }
    //
    // var hasParticipants = newOneToOneChat.getConversationParticipants() != null && !newOneToOneChat.getConversationParticipants().isEmpty() && newOneToOneChat
    // .getConversationParticipants().stream().anyMatch(conversationParticipant -> !conversationParticipant.getUser().getId().equals(requestingUser.getId()));
    //
    // if (!hasParticipants) {
    // throw new BadRequestAlertException("A new group chat must have other conversation participants than just the requesting user", GROUP_CHAT_ENTITY_NAME,
    // "invalidconversationparticipants");
    // }
    // // filter out requesting user from participants if sent -> we will create it below
    // var participants = new HashSet<>(newOneToOneChat.getConversationParticipants());
    // participants.removeIf(participant -> participant.getUser().getId().equals(requestingUser.getId()));
    // newOneToOneChat.setConversationParticipants(participants);
    //
    // var existingGroupChatsOfUser = oneToOneChatRepository.findActiveOneToOneChatsOfUser(course.getId(), requestingUser.getId());
    //
    // // find out if there already exists a group chat with the exact same participants (other than the requesting user)
    // var existingGroupChatOptional = existingGroupChatsOfUser.stream().filter(groupChat -> {
    // var groupChatParticipantsWithoutRequestingUser = groupChat.getConversationParticipants();
    // groupChatParticipantsWithoutRequestingUser.removeIf(participant -> participant.getUser().getId().equals(requestingUser.getId()));
    // if (groupChatParticipantsWithoutRequestingUser.size() != newOneToOneChat.getConversationParticipants().size()) {
    // return false;
    // }
    // return groupChatParticipantsWithoutRequestingUser.stream().map(ConversationParticipant::getUser)
    // .allMatch(user -> newOneToOneChat.getConversationParticipants().stream().map(ConversationParticipant::getUser).anyMatch(user::equals));
    // }).findFirst();
    //
    // if (existingGroupChatOptional.isEmpty()) {
    // newOneToOneChat.setCourse(course);
    // var savedGroupChat = oneToOneChatRepository.save(newOneToOneChat);
    // var conversationParticipantOfRequestingUser = new ConversationParticipant();
    // conversationParticipantOfRequestingUser.setUser(requestingUser);
    // conversationParticipantOfRequestingUser.setConversation(savedGroupChat);
    // var conversationsOfOtherUsers = newOneToOneChat.getConversationParticipants();
    // conversationsOfOtherUsers.forEach(conversationParticipant -> conversationParticipant.setConversation(savedGroupChat));
    //
    // var allConversationParticipants = new ArrayList<>(List.of(conversationParticipantOfRequestingUser));
    // allConversationParticipants.addAll(conversationsOfOtherUsers);
    //
    // var savedParticipants = new HashSet<>(conversationParticipantRepository.saveAll(allConversationParticipants));
    // savedGroupChat.setConversationParticipants(savedParticipants);
    // savedGroupChat.setCreator(requestingUser);
    // return oneToOneChatRepository.save(savedGroupChat);
    // } else {
    // return existingGroupChatOptional.get();
    // }
    // }

}
