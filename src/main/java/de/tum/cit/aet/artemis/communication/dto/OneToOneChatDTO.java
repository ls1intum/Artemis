package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OneToOneChatDTO(
        // Fields from ConversationDTO (duplicated)
        String type, Long id, ZonedDateTime creationDate, ZonedDateTime lastMessageDate, ConversationUserDTO creator, ZonedDateTime lastReadDate, Long unreadMessagesCount,
        Boolean isFavorite, Boolean isHidden, Boolean isMuted, Boolean isCreator, Boolean isMember, Integer numberOfMembers,

        // Specific OneToOneChatDTO field
        Set<ConversationUserDTO> members) implements ConversationDTO {

    /**
     * Constructor from Conversation entity
     */
    public OneToOneChatDTO(Conversation conversation) {
        this("oneToOneChat", conversation.getId(), conversation.getCreationDate(), conversation.getLastMessageDate(),
                conversation.getCreator() != null ? new ConversationUserDTO(conversation.getCreator()) : null, null, // lastReadDate - not from entity
                null, // unreadMessagesCount - not from entity
                null, // isFavorite - not from entity
                null, // isHidden - not from entity
                null, // isMuted - not from entity
                null, // isCreator - not from entity
                null, // isMember - not from entity
                null, // numberOfMembers - not from entity
                null  // members - typically populated separately
        );
    }

    /**
     * Default constructor
     */
    public OneToOneChatDTO() {
        this("oneToOneChat", null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Implementation of copyWith from ConversationDTO interface
     * Creates a new instance with the specified fields changed.
     * If a parameter is null, the current value is retained.
     */
    @Override
    public OneToOneChatDTO copyWith(Long id, ZonedDateTime creationDate, ZonedDateTime lastMessageDate, ConversationUserDTO creator, ZonedDateTime lastReadDate,
            Long unreadMessagesCount, Boolean isFavorite, Boolean isHidden, Boolean isMuted, Boolean isCreator, Boolean isMember, Integer numberOfMembers) {
        return new OneToOneChatDTO(this.type(), // type should remain constant for OneToOneChat
                id != null ? id : this.id(), creationDate != null ? creationDate : this.creationDate(), lastMessageDate != null ? lastMessageDate : this.lastMessageDate(),
                creator != null ? creator : this.creator(), lastReadDate != null ? lastReadDate : this.lastReadDate(),
                unreadMessagesCount != null ? unreadMessagesCount : this.unreadMessagesCount(), isFavorite != null ? isFavorite : this.isFavorite(),
                isHidden != null ? isHidden : this.isHidden(), isMuted != null ? isMuted : this.isMuted(), isCreator != null ? isCreator : this.isCreator(),
                isMember != null ? isMember : this.isMember(), numberOfMembers != null ? numberOfMembers : this.numberOfMembers(), this.members() // preserve members in base
                                                                                                                                                  // copyWith
        );
    }

    /**
     * Creates a new OneToOneChatDTO with updated members
     *
     * @param members the new set of members
     * @return a new OneToOneChatDTO instance with updated members
     */
    public OneToOneChatDTO withMembers(Set<ConversationUserDTO> members) {
        return new OneToOneChatDTO(this.type(), this.id(), this.creationDate(), this.lastMessageDate(), this.creator(), this.lastReadDate(), this.unreadMessagesCount(),
                this.isFavorite(), this.isHidden(), this.isMuted(), this.isCreator(), this.isMember(), this.numberOfMembers(), members);
    }

    public OneToOneChatDTO withNumberOfMembers(Integer numberOfMembers) {
        return new OneToOneChatDTO(this.type(), this.id(), this.creationDate(), this.lastMessageDate(), this.creator(), this.lastReadDate(), this.unreadMessagesCount(),
                this.isFavorite(), this.isHidden(), this.isMuted(), this.isCreator(), this.isMember(), numberOfMembers, this.members());
    }

    public OneToOneChatDTO withIsCreator(Boolean isCreator) {
        return new OneToOneChatDTO(this.type(), this.id(), this.creationDate(), this.lastMessageDate(), this.creator(), this.lastReadDate(), this.unreadMessagesCount(),
                this.isFavorite(), this.isHidden(), this.isMuted(), isCreator, this.isMember(), this.numberOfMembers(), this.members());
    }

    // Convenience getter method to match original naming convention
    public Set<ConversationUserDTO> getMembers() {
        return members;
    }

    @Override
    public String toString() {
        return "OneToOneChatDTO{" + "members=" + members + "} " + "ConversationDTO{" + "type='" + type + '\'' + ", id=" + id + ", creationDate=" + creationDate
                + ", lastMessageDate=" + lastMessageDate + ", unreadMessageCount=" + unreadMessagesCount + ", lastReadDate=" + lastReadDate + ", isMember=" + isMember
                + ", isFavorite=" + isFavorite + ", isHidden=" + isHidden + ", isCreator=" + isCreator + ", numberOfMembers=" + numberOfMembers + ", creator="
                + (creator != null ? creator.publicInfo().name() : "") + '}';
    }
}
