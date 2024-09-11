package de.tum.cit.aet.artemis.communication.web.conversation.dtos;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = OneToOneChatDTO.class, name = "oneToOneChat"),
    @JsonSubTypes.Type(value = GroupChatDTO.class, name = "groupChat"),
    @JsonSubTypes.Type(value = ChannelDTO.class, name = "channel"),
})
// @formatter:on
public class ConversationDTO {

    /**
     * Determines the type of the conversation, either "channel" , "groupChat" or "oneToOneChat" depending on the subclass
     */
    private String type;

    @SuppressWarnings("PMD.ShortVariable")
    private Long id;

    private ZonedDateTime creationDate;

    private ZonedDateTime lastMessageDate;

    private ConversationUserDTO creator;

    // property not taken from entity
    private ZonedDateTime lastReadDate;

    // property not taken from entity
    private Long unreadMessagesCount;

    // property not taken from entity
    private Boolean isFavorite;

    // property not taken from entity
    private Boolean isHidden;

    // property not taken from entity
    private Boolean isMuted;

    // property not taken from entity
    private Boolean isCreator;

    // property not taken from entity
    private Boolean isMember;

    // property not taken from entity
    private Integer numberOfMembers;

    protected ConversationDTO(Conversation conversation, String type) {
        this.id = conversation.getId();
        this.creationDate = conversation.getCreationDate();
        this.lastMessageDate = conversation.getLastMessageDate();
        if (conversation.getCreator() != null) {
            this.creator = new ConversationUserDTO(conversation.getCreator());
        }
        this.type = type;
    }

    protected ConversationDTO(String type) {
        this.type = type;
    }

    protected ConversationDTO() {
        // default constructor
    }

    // TODO: in json, this value is inserted twice, add @JsonIgnore
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @SuppressWarnings("PMD.ShortVariable")
    public Long getId() {
        return id;
    }

    @SuppressWarnings("PMD.ShortVariable")
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

    public Boolean getIsMuted() {
        return isMuted;
    }

    public void setIsMuted(Boolean isMuted) {
        this.isMuted = isMuted;
    }

    public ZonedDateTime getLastReadDate() {
        return lastReadDate;
    }

    public void setLastReadDate(ZonedDateTime lastReadDate) {
        this.lastReadDate = lastReadDate;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }

    public void setUnreadMessagesCount(Long unreadMessagesCount) {
        this.unreadMessagesCount = unreadMessagesCount;
    }

    @Override
    public String toString() {
        return "ConversationDTO{" + "type='" + type + '\'' + ", id=" + id + ", creationDate=" + creationDate + ", lastMessageDate=" + lastMessageDate + ", unreadMessageCount="
                + unreadMessagesCount + ", lastReadDate=" + lastReadDate + ", isMember=" + isMember + ", isFavorite=" + isFavorite + ", isHidden=" + isHidden + ", isCreator="
                + isCreator + ", numberOfMembers=" + numberOfMembers + ", creator=" + (creator != null ? creator.getName() : "") + '}';
    }

}
