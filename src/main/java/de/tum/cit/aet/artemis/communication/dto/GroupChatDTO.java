package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GroupChatDTO(
        // Fields from ConversationDTO (duplicated)
        String type, Long id, ZonedDateTime creationDate, ZonedDateTime lastMessageDate, ConversationUserDTO creator, ZonedDateTime lastReadDate, Long unreadMessagesCount,
        Boolean isFavorite, Boolean isHidden, Boolean isMuted, Boolean isCreator, Boolean isMember, Integer numberOfMembers,

        // Specific GroupChatDTO fields
        String name, Set<ConversationUserDTO> members) implements ConversationDTO {

    /**
     * Constructor from GroupChat entity
     */
    public GroupChatDTO(GroupChat groupChat) {
        this("groupChat", groupChat.getId(), groupChat.getCreationDate(), groupChat.getLastMessageDate(),
                groupChat.getCreator() != null ? new ConversationUserDTO(groupChat.getCreator()) : null, null, // lastReadDate - not from entity
                null, // unreadMessagesCount - not from entity
                null, // isFavorite - not from entity
                null, // isHidden - not from entity
                null, // isMuted - not from entity
                null, // isCreator - not from entity
                null, // isMember - not from entity
                null, // numberOfMembers - not from entity
                groupChat.getName(), null  // members - typically populated separately
        );
    }

    public GroupChatDTO(GroupChat groupChat, Set<ConversationUserDTO> members) {
        this("groupChat", groupChat.getId(), groupChat.getCreationDate(), groupChat.getLastMessageDate(),
                groupChat.getCreator() != null ? new ConversationUserDTO(groupChat.getCreator()) : null, null, // lastReadDate - not from entity
                null, // unreadMessagesCount - not from entity
                null, // isFavorite - not from entity
                null, // isHidden - not from entity
                null, // isMuted - not from entity
                null, // isCreator - not from entity
                null, // isMember - not from entity
                null, // numberOfMembers - not from entity
                groupChat.getName(), members  // members - typically populated separately
        );
    }

    /**
     * Default constructor
     */
    public GroupChatDTO() {
        this("groupChat", null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Implementation of copyWith from ConversationDTO interface
     * Creates a new instance with the specified fields changed.
     * If a parameter is null, the current value is retained.
     */
    @Override
    public GroupChatDTO copyWith(Long id, ZonedDateTime creationDate, ZonedDateTime lastMessageDate, ConversationUserDTO creator, ZonedDateTime lastReadDate,
            Long unreadMessagesCount, Boolean isFavorite, Boolean isHidden, Boolean isMuted, Boolean isCreator, Boolean isMember, Integer numberOfMembers) {
        return new GroupChatDTO(this.type(), // type should remain constant for GroupChat
                id != null ? id : this.id(), creationDate != null ? creationDate : this.creationDate(), lastMessageDate != null ? lastMessageDate : this.lastMessageDate(),
                creator != null ? creator : this.creator(), lastReadDate != null ? lastReadDate : this.lastReadDate(),
                unreadMessagesCount != null ? unreadMessagesCount : this.unreadMessagesCount(), isFavorite != null ? isFavorite : this.isFavorite(),
                isHidden != null ? isHidden : this.isHidden(), isMuted != null ? isMuted : this.isMuted(), isCreator != null ? isCreator : this.isCreator(),
                isMember != null ? isMember : this.isMember(), numberOfMembers != null ? numberOfMembers : this.numberOfMembers(), this.name(), // preserve name in base copyWith
                this.members() // preserve members in base copyWith
        );
    }

    /**
     * Creates a new GroupChatDTO with updated members
     *
     * @param members the new set of members
     * @return a new GroupChatDTO instance with updated members
     */
    public GroupChatDTO withMembers(Set<ConversationUserDTO> members) {
        return new GroupChatDTO(this.type(), this.id(), this.creationDate(), this.lastMessageDate(), this.creator(), this.lastReadDate(), this.unreadMessagesCount(),
                this.isFavorite(), this.isHidden(), this.isMuted(), this.isCreator(), this.isMember(), this.numberOfMembers(), this.name(), members);
    }

    public GroupChatDTO withNumberOfMembers(Integer numberOfMembers) {
        return new GroupChatDTO(this.type(), this.id(), this.creationDate(), this.lastMessageDate(), this.creator(), this.lastReadDate(), this.unreadMessagesCount(),
                this.isFavorite(), this.isHidden(), this.isMuted(), this.isCreator(), this.isMember(), numberOfMembers, this.name(), this.members());
    }

    public GroupChatDTO withName(String name) {
        return new GroupChatDTO(this.type(), this.id(), this.creationDate(), this.lastMessageDate(), this.creator(), this.lastReadDate(), this.unreadMessagesCount(),
                this.isFavorite(), this.isHidden(), this.isMuted(), this.isCreator(), this.isMember(), this.numberOfMembers(), name, this.members());
    }

    public GroupChatDTO withIsCreator(Boolean isCreator) {
        return new GroupChatDTO(this.type(), this.id(), this.creationDate(), this.lastMessageDate(), this.creator(), this.lastReadDate(), this.unreadMessagesCount(),
                this.isFavorite(), this.isHidden(), this.isMuted(), isCreator, this.isMember(), this.numberOfMembers(), this.name(), this.members());
    }

    // Convenience getter methods to match original naming convention
    public Set<ConversationUserDTO> getMembers() {
        return members;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "GroupChatDTO{" + "name='" + name + '\'' + "members=" + members + "} " + "ConversationDTO{" + "type='" + type + '\'' + ", id=" + id + ", creationDate="
                + creationDate + ", lastMessageDate=" + lastMessageDate + ", unreadMessageCount=" + unreadMessagesCount + ", lastReadDate=" + lastReadDate + ", isMember="
                + isMember + ", isFavorite=" + isFavorite + ", isHidden=" + isHidden + ", isCreator=" + isCreator + ", numberOfMembers=" + numberOfMembers + ", creator="
                + (creator != null ? creator.publicInfo().name() : "") + '}';
    }
}
