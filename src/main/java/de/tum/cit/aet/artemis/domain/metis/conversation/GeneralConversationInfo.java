package de.tum.cit.aet.artemis.domain.metis.conversation;

/**
 * Stores general information about a conversation that is not related to a user
 */
public class GeneralConversationInfo {

    private final long conversationId;

    private int numberOfParticipants;

    public GeneralConversationInfo(long conversationId, long numberOfParticipants) {
        this.conversationId = conversationId;
        this.numberOfParticipants = (int) numberOfParticipants;
    }

    public long getConversationId() {
        return conversationId;
    }

    public int getNumberOfParticipants() {
        return numberOfParticipants;
    }

    public void setNumberOfParticipants(int numberOfParticipants) {
        this.numberOfParticipants = numberOfParticipants;
    }
}
