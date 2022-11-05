package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;

public abstract class ConversationDTO {

    /**
     * Determines the type of the conversation, either "channel" or "groupChat" depending on the subclass
     */
    private final String type;

    private Long id;

    private ZonedDateTime creationDate;

    private ZonedDateTime lastMessageDate;

    // property not taken from entity
    private Boolean isMember;

    // property not taken from entity
    private Integer numberOfMembers;

    // ToDo: Maybe add property hasUnreadMessages and unreadMessagesCount?? How does slack do it?

    public ConversationDTO(Conversation conversation, String type) {
        this.id = conversation.getId();
        this.creationDate = conversation.getCreationDate();
        this.lastMessageDate = conversation.getLastMessageDate();
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public ZonedDateTime getLastMessageDate() {
        return lastMessageDate;
    }

    public void setLastMessageDate(ZonedDateTime lastMessageDate) {
        this.lastMessageDate = lastMessageDate;
    }

    public Boolean getIsMember() {
        return isMember;
    }

    public void setIsMember(Boolean member) {
        isMember = member;
    }

    public Integer getNumberOfMembers() {
        return numberOfMembers;
    }

    public void setNumberOfMembers(Integer numberOfMembers) {
        this.numberOfMembers = numberOfMembers;
    }

    public String getType() {
        return type;
    }
}
