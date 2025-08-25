package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.ConversationType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostingConversationDTO(Long id, String title, ConversationType type) {

    public PostingConversationDTO(Conversation conversation) {
        this(conversation.getId(), determineTitle(conversation), determineType(conversation));
    }

    private static String determineTitle(Conversation conversation) {
        if (conversation instanceof Channel) {
            return ((Channel) conversation).getName();
        }
        else if (conversation instanceof GroupChat) {
            return ((GroupChat) conversation).getName();
        }
        else {
            return "Chat";
        }
    }

    private static ConversationType determineType(Conversation conversation) {
        if (conversation instanceof Channel) {
            return ConversationType.CHANNEL;
        }
        else {
            return ConversationType.DIRECT;
        }
    }
}
