package de.tum.in.www1.artemis.domain.metis.conversation;

public class GeneralConversationInfo {

    private final long conversationId;

    private int numberOfParticipants;

    public GeneralConversationInfo(long conversationId, long numberOfParticipants) {
        this.conversationId = conversationId;
        this.numberOfParticipants = Long.valueOf(numberOfParticipants).intValue();
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
