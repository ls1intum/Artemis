package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;

public abstract class ConversationDTO {

    /**
     * Determines the type of the conversation, either "channel" , "groupChat" or "oneToOneChat"  depending on the subclass
     */
    private String type;

    private Long id;

    private ZonedDateTime creationDate;

    private ZonedDateTime lastMessageDate;

    private ConversationUserDTO creator;

    // property not taken from entity
    private ZonedDateTime lastReadDate;

    // property not taken from entity
    private Boolean isFavorite;

    // property not taken from entity
    private Boolean isHidden;

    // property not taken from entity
    private Boolean isCreator;

    // property not taken from entity
    private Boolean isMember;

    // property not taken from entity
    private Integer numberOfMembers;

    // ToDo: Maybe add property hasUnreadMessages and unreadMessagesCount?? How does slack do it?

    public ConversationDTO(Conversation conversation, String type) {
        this.id = conversation.getId();
        this.creationDate = conversation.getCreationDate();
        this.lastMessageDate = conversation.getLastMessageDate();
        if (conversation.getCreator() != null) {
            this.creator = new ConversationUserDTO(conversation.getCreator());
        }
        this.type = type;
    }

    public ConversationDTO(String type) {
        this.type = type;
    }

    public ConversationDTO() {
        // default constructor
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
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

    public ConversationUserDTO getCreator() {
        return creator;
    }

    public void setCreator(ConversationUserDTO creator) {
        this.creator = creator;
    }

    public Boolean getIsCreator() {
        return isCreator;
    }

    public void setIsCreator(Boolean creator) {
        isCreator = creator;
    }

    public Boolean getIsFavorite() {
        return isFavorite;
    }

    public void setIsFavorite(Boolean favorite) {
        isFavorite = favorite;
    }

    public Boolean getIsHidden() {
        return isHidden;
    }

    public void setIsHidden(Boolean hidden) {
        isHidden = hidden;
    }

    public ZonedDateTime getLastReadDate() {
        return lastReadDate;
    }

    public void setLastReadDate(ZonedDateTime lastReadDate) {
        this.lastReadDate = lastReadDate;
    }

    @Override
    public String toString() {
        return "ConversationDTO{" + "type='" + type + '\'' + ", id=" + id + ", creationDate=" + creationDate + ", lastMessageDate=" + lastMessageDate + ", lastReadDate="
                + lastReadDate + ", isMember=" + isMember + ", isFavorite=" + isFavorite + ", isHidden=" + isHidden + ", isCreator=" + isCreator + ", numberOfMembers="
                + numberOfMembers + ", creator=" + (creator != null ? creator.getName() : "") + '}';
    }
}
