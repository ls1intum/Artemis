package de.tum.in.www1.artemis.service.metis.conversation;

import static javax.validation.Validation.buildDefaultValidatorFactory;

import java.util.Set;

import javax.validation.ConstraintViolationException;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;

@Service
public class GroupChatService {

    public static final String GROUP_CHAT_ENTITY_NAME = "messages.groupchat";

    private static final String GROUP_NAME_REGEX = "^[a-z0-9-]{1}[a-z0-9-]{0,20}$";

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

    public GroupChat getGroupChatOrThrow(Long groupChatId) {
        return groupChatRepository.findById(groupChatId)
                .orElseThrow(() -> new BadRequestAlertException("GroupChat with id " + groupChatId + " does not exist", GROUP_CHAT_ENTITY_NAME, "idnotfound"));
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

    public GroupChat updateGroupChat(Long groupChatId, GroupChatDTO groupChatDTO) {
        var groupChat = getGroupChatOrThrow(groupChatId);
        if (groupChatDTO.getName() != null && !groupChatDTO.getName().equals(groupChat.getName())) {
            groupChat.setName(groupChatDTO.getName().trim().isBlank() ? null : groupChatDTO.getName().trim());
        }
        this.groupChatIsValidOrThrow(groupChat);
        var updatedGroupChat = groupChatRepository.save(groupChat);
        conversationService.notifyConversationMembersAboutUpdate(updatedGroupChat);
        return updatedGroupChat;
    }

    public void groupChatIsValidOrThrow(GroupChat groupChat) {
        var validator = buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(groupChat);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        if (!groupChat.getName().matches(GROUP_NAME_REGEX)) {
            throw new BadRequestAlertException("Group names can only contain lowercase letters, numbers, and dashes.", GROUP_CHAT_ENTITY_NAME, "namePatternInvalid");
        }
    }

}
