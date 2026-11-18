package de.tum.cit.aet.artemis.communication.service.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

@Profile(PROFILE_CORE)
@Lazy
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
     * <p>
     * Starting a chat is idempotent also when several requests start it at the same time, for example both users writing to each other for the first time, or a double
     * click. The chat row carries the pair of users, and the database allows one such row per course, so exactly one request creates it. Every request then makes sure
     * both participants exist, which a request does not leave half done when it fails: the next one completes it.
     *
     * @param course the course the OneToOneChat is in
     * @param userA  the first user
     * @param userB  the second user
     * @return the newly created OneToOneChat or the existing one
     */
    public OneToOneChat startOneToOneChat(Course course, User userA, User userB) {
        var requestingUser = userRepository.getUserWithAuthorities();
        var existingChatBetweenUsers = oneToOneChatRepository.findWithParticipantsAndUserCourseRolesInCourseBetweenUsers(course.getId(), userA.getId(), userB.getId());
        if (existingChatBetweenUsers.isPresent()) {
            return claimIfNotStarted(existingChatBetweenUsers.get(), requestingUser);
        }

        String pairKey = OneToOneChat.pairKeyOf(userA.getId(), userB.getId());
        OneToOneChat chat = oneToOneChatRepository.findByCourseIdAndParticipantPairKey(course.getId(), pairKey).orElseGet(() -> createChat(course, requestingUser, pairKey));
        var now = ZonedDateTime.now();
        for (User user : List.of(userA, userB)) {
            // As for a new conversation: the user has not read anything yet, which is why the last read date lies in the past
            conversationParticipantRepository.createIfAbsent(chat.getId(), user.getId(), false, now.minusYears(2), 0L);
        }
        // The participants of the chat are not loaded, so whoever builds the response reads both of them
        return claimIfNotStarted(chat, requestingUser);
    }

    /**
     * Inserts the chat of a pair. If a concurrent request inserted it first, the database refuses this insert and the chat of that request is the result.
     */
    private OneToOneChat createChat(Course course, User requestingUser, String pairKey) {
        var oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        oneToOneChat.setCreator(requestingUser);
        oneToOneChat.setParticipantPairKey(pairKey);
        try {
            return oneToOneChatRepository.save(oneToOneChat);
        }
        catch (DataIntegrityViolationException e) {
            return oneToOneChatRepository.findByCourseIdAndParticipantPairKey(course.getId(), pairKey).orElseThrow(() -> e);
        }
    }

    /**
     * A chat nobody has written in belongs to the user who starts it now, because that is the user it is shown to.
     */
    private OneToOneChat claimIfNotStarted(OneToOneChat chat, User requestingUser) {
        if (chat.getLastMessageDate() == null && !requestingUser.getId().equals(chat.getCreator().getId())) {
            chat.setCreator(requestingUser);
            return oneToOneChatRepository.save(chat);
        }
        return chat;
    }
}
