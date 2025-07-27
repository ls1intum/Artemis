package de.tum.cit.aet.artemis.communication.dto;

/**
 * Stores general information about a conversation that is not related to a user
 */
public record GeneralConversationInfo(long conversationId, int numberOfParticipants) {

    public GeneralConversationInfo(long conversationId, long numberOfParticipants) {
        this(conversationId, (int) numberOfParticipants);
    }

    public GeneralConversationInfo withNumberOfParticipants(int newNumberOfParticipants) {
        return new GeneralConversationInfo(this.conversationId, newNumberOfParticipants);
    }
}
