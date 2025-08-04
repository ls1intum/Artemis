package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = OneToOneChatDTO.class, name = "oneToOneChat"), @JsonSubTypes.Type(value = GroupChatDTO.class, name = "groupChat"),
        @JsonSubTypes.Type(value = ChannelDTO.class, name = "channel"), })
public interface ConversationDTO {

    String type();

    Long id();

    ZonedDateTime creationDate();

    ZonedDateTime lastMessageDate();

    ConversationUserDTO creator();

    ZonedDateTime lastReadDate();

    Long unreadMessagesCount();

    Boolean isFavorite();

    Boolean isHidden();

    Boolean isMuted();

    Boolean isCreator();

    Boolean isMember();

    Integer numberOfMembers();

    /**
     * Creates a copy of this ConversationDTO with the specified fields changed.
     * Use null to keep the existing value.
     *
     * @param id                  the new id
     * @param creationDate        the new creation date
     * @param lastMessageDate     the new last message date
     * @param creator             the new creator
     * @param lastReadDate        the new last read date
     * @param unreadMessagesCount the new unread messages count
     * @param isFavorite          the new favorite status
     * @param isHidden            the new hidden status
     * @param isMuted             the new muted status
     * @param isCreator           the new creator status
     * @param isMember            the new member status
     * @param numberOfMembers     the new number of members
     * @return a new ConversationDTO instance with the updated fields
     */
    ConversationDTO copyWith(Long id, ZonedDateTime creationDate, ZonedDateTime lastMessageDate, ConversationUserDTO creator, ZonedDateTime lastReadDate, Long unreadMessagesCount,
            Boolean isFavorite, Boolean isHidden, Boolean isMuted, Boolean isCreator, Boolean isMember, Integer numberOfMembers);
}
