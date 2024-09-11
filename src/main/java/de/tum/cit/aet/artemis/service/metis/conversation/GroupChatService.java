package de.tum.cit.aet.artemis.service.metis.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.GroupChatRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;

@Profile(PROFILE_CORE)
@Service
public class GroupChatService {

    public static final String GROUP_CHAT_ENTITY_NAME = "messages.groupchat";

    private static final String GROUP_NAME_REGEX = "^[a-z0-9-]{0,20}$";

    private final UserRepository userRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ConversationService conversationService;

    private final GroupChatRepository groupChatRepository;

    public GroupChatService(UserRepository userRepository, ConversationParticipantRepository conversationParticipantRepository, ConversationService conversationService,
            GroupChatRepository groupChatRepository) {
        this.userRepository = userRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationService = conversationService;
        this.groupChatRepository = groupChatRepository;
    }

    /**
     * Creates a new group chat
     *
     * @param course          the course the group chat is created in
     * @param startingMembers the users that are initially part of the group chat
     * @return the newly created group chat
     */
    public GroupChat startGroupChat(Course course, Set<User> startingMembers) {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var groupChat = new GroupChat();
        groupChat.setCourse(course);
        groupChat.setCreator(requestingUser);
        var savedGroupChat = groupChatRepository.save(groupChat);
        var participantsToCreate = startingMembers.stream().map(user -> ConversationParticipant.createWithDefaultValues(user, groupChat)).toList();
        conversationParticipantRepository.saveAll(participantsToCreate);
        return savedGroupChat;
    }

    /**
     * Updates the group chat with the given id
     *
     * @param groupChatId  the id of the group chat to update
     * @param groupChatDTO the DTO containing the new group chat data
     * @return the updated group chat
     */
    public GroupChat updateGroupChat(Long groupChatId, GroupChatDTO groupChatDTO) {
        var groupChat = groupChatRepository.findByIdElseThrow(groupChatId);
        if (groupChatDTO.getName() != null && !groupChatDTO.getName().equals(groupChat.getName())) {
            groupChat.setName(groupChatDTO.getName().trim().isBlank() ? "" : groupChatDTO.getName().trim());
        }
        this.groupChatIsValidOrThrow(groupChat);
        var updatedGroupChat = groupChatRepository.save(groupChat);
        conversationService.notifyAllConversationMembersAboutUpdate(updatedGroupChat);
        return updatedGroupChat;
    }

    /**
     * Checks if the group chat is valid and throws an exception if it is not
     *
     * @param groupChat the group chat to check
     */
    public void groupChatIsValidOrThrow(@Valid GroupChat groupChat) {
        if (groupChat.getName() != null && !groupChat.getName().matches(GROUP_NAME_REGEX)) {
            throw new BadRequestAlertException("Group names can only contain lowercase letters, numbers, and dashes.", GROUP_CHAT_ENTITY_NAME, "namePatternInvalid");
        }
    }

}
