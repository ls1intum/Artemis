package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.ChannelSubType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChannelDTO(
        // Fields from ConversationDTO (duplicated)
        String type, Long id, ZonedDateTime creationDate, ZonedDateTime lastMessageDate, ConversationUserDTO creator, ZonedDateTime lastReadDate, Long unreadMessagesCount,
        Boolean isFavorite, Boolean isHidden, Boolean isMuted, Boolean isCreator, Boolean isMember, Integer numberOfMembers,

        // Specific ChannelDTO fields
        String name, String description, String topic, boolean isPublic, boolean isAnnouncementChannel, boolean isArchived, boolean isCourseWide,
        boolean hasChannelModerationRights, boolean isChannelModerator, Long tutorialGroupId, String tutorialGroupTitle, ChannelSubType subType, Long subTypeReferenceId)
        implements ConversationDTO {

    /**
     * Constructor from Channel entity
     */
    public ChannelDTO(Channel channel) {
        this("channel", channel.getId(), channel.getCreationDate(), channel.getLastMessageDate(),
                channel.getCreator() != null ? new ConversationUserDTO(channel.getCreator()) : null, null, // lastReadDate - not from entity
                null, // unreadMessagesCount - not from entity
                null, // isFavorite - not from entity
                null, // isHidden - not from entity
                null, // isMuted - not from entity
                null, // isCreator - not from entity
                null, // isMember - not from entity
                null, // numberOfMembers - not from entity
                channel.getName(), channel.getDescription(), channel.getTopic(), channel.getIsPublic(), channel.getIsAnnouncementChannel(), channel.getIsArchived(),
                channel.getIsCourseWide(), false, // hasChannelModerationRights - default value
                false, // isChannelModerator - default value
                null,  // tutorialGroupId - not from entity
                null,  // tutorialGroupTitle - not from entity
                determineSubType(channel), determineSubTypeReferenceId(channel));
    }

    /**
     * Default constructor with default values
     */
    public ChannelDTO() {
        this("channel", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, // isPublic default
                false, // isAnnouncementChannel default
                false, // isArchived default
                false, // isCourseWide default
                false, // hasChannelModerationRights default
                false, // isChannelModerator default
                null, null, null, null);
    }

    /**
     * Implementation of copyWith from ConversationDTO interface
     * Creates a new instance with the specified fields changed.
     * If a parameter is null, the current value is retained.
     */
    @Override
    public ChannelDTO copyWith(Long id, ZonedDateTime creationDate, ZonedDateTime lastMessageDate, ConversationUserDTO creator, ZonedDateTime lastReadDate,
            Long unreadMessagesCount, Boolean isFavorite, Boolean isHidden, Boolean isMuted, Boolean isCreator, Boolean isMember, Integer numberOfMembers) {
        return new ChannelDTO(this.type(), // type should remain constant for Channel
                id != null ? id : this.id(), creationDate != null ? creationDate : this.creationDate(), lastMessageDate != null ? lastMessageDate : this.lastMessageDate(),
                creator != null ? creator : this.creator(), lastReadDate != null ? lastReadDate : this.lastReadDate(),
                unreadMessagesCount != null ? unreadMessagesCount : this.unreadMessagesCount(), isFavorite != null ? isFavorite : this.isFavorite(),
                isHidden != null ? isHidden : this.isHidden(), isMuted != null ? isMuted : this.isMuted(), isCreator != null ? isCreator : this.isCreator(),
                isMember != null ? isMember : this.isMember(), numberOfMembers != null ? numberOfMembers : this.numberOfMembers(),
                // Preserve all channel-specific fields
                this.name(), this.description(), this.topic(), this.isPublic(), this.isAnnouncementChannel(), this.isArchived(), this.isCourseWide(),
                this.hasChannelModerationRights(), this.isChannelModerator(), this.tutorialGroupId(), this.tutorialGroupTitle(), this.subType(), this.subTypeReferenceId());
    }

    // Individual with methods for each ChannelDTO-specific field

    public ChannelDTO withName(String name) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name, description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withDescription(String description) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description, topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withTopic(String topic) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic, isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withNumberOfMembers(Integer numberOfMembers) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers, name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withIsMember(Boolean isMember) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember, numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withIsPublic(boolean isPublic) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic, isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withIsAnnouncementChannel(boolean isAnnouncementChannel) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel, isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withIsArchived(boolean isArchived) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived, isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withIsCourseWide(boolean isCourseWide) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide, hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withHasChannelModerationRights(boolean hasChannelModerationRights) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights,
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withIsChannelModerator(boolean isChannelModerator) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator, tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withTutorialGroupId(Long tutorialGroupId) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId, tutorialGroupTitle(), subType(), subTypeReferenceId());
    }

    public ChannelDTO withTutorialGroupTitle(String tutorialGroupTitle) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle, subType(), subTypeReferenceId());
    }

    public ChannelDTO withSubType(ChannelSubType subType) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType, subTypeReferenceId());
    }

    public ChannelDTO withSubTypeReferenceId(Long subTypeReferenceId) {
        return new ChannelDTO(type(), id(), creationDate(), lastMessageDate(), creator(), lastReadDate(), unreadMessagesCount(), isFavorite(), isHidden(), isMuted(), isCreator(),
                isMember(), numberOfMembers(), name(), description(), topic(), isPublic(), isAnnouncementChannel(), isArchived(), isCourseWide(), hasChannelModerationRights(),
                isChannelModerator(), tutorialGroupId(), tutorialGroupTitle(), subType(), subTypeReferenceId);
    }

    /**
     * Determines the subtype of the channel based on associated entities
     */
    private static ChannelSubType determineSubType(Channel channel) {
        if (channel.getExercise() != null) {
            return ChannelSubType.EXERCISE;
        }
        else if (channel.getLecture() != null) {
            return ChannelSubType.LECTURE;
        }
        else if (channel.getExam() != null) {
            return ChannelSubType.EXAM;
        }
        else if (channel.getTopic() != null && channel.getTopic().contains("FeedbackDiscussion")) {
            return ChannelSubType.FEEDBACK_DISCUSSION;
        }
        else {
            return ChannelSubType.GENERAL;
        }
    }

    /**
     * Determines the subtype reference ID based on associated entities
     */
    private static Long determineSubTypeReferenceId(Channel channel) {
        if (channel.getExercise() != null) {
            return channel.getExercise().getId();
        }
        else if (channel.getLecture() != null) {
            return channel.getLecture().getId();
        }
        else if (channel.getExam() != null) {
            return channel.getExam().getId();
        }
        return null;
    }

    /**
     * Converts the DTO to a channel entity
     *
     * @return the created channel entity based on the attributes in the DTO
     */
    public Channel toChannel() {
        Channel channel = new Channel();
        channel.setName(this.name);
        channel.setDescription(this.description);
        channel.setTopic(this.topic);
        channel.setIsPublic(this.isPublic);
        channel.setIsArchived(this.isArchived);
        channel.setIsAnnouncementChannel(this.isAnnouncementChannel);
        channel.setIsCourseWide(this.isCourseWide);
        return channel;
    }

    public boolean getIsAnnouncementChannel() {
        return isAnnouncementChannel;
    }

    public boolean getIsArchived() {
        return isArchived;
    }

    public boolean getIsCourseWide() {
        return isCourseWide;
    }

    public boolean getHasChannelModerationRights() {
        return hasChannelModerationRights;
    }

    public boolean getIsChannelModerator() {
        return isChannelModerator;
    }

    @Override
    public String toString() {
        return "ChannelDTO{" + "subType='" + subType + '\'' + ", name='" + name + '\'' + ", description='" + description + '\'' + ", topic='" + topic + '\'' + ", isPublic="
                + isPublic + ", isAnnouncementChannel=" + isAnnouncementChannel + ", isArchived=" + isArchived + ", isCourseWide=" + isCourseWide + ", isChannelModerator="
                + isChannelModerator + ", hasChannelModerationRights=" + hasChannelModerationRights + ", tutorialGroupId=" + tutorialGroupId + ", tutorialGroupTitle="
                + tutorialGroupTitle + "} " + "ConversationDTO{" + "type='" + type + '\'' + ", id=" + id + ", creationDate=" + creationDate + ", lastMessageDate=" + lastMessageDate
                + ", unreadMessageCount=" + unreadMessagesCount + ", lastReadDate=" + lastReadDate + ", isMember=" + isMember + ", isFavorite=" + isFavorite + ", isHidden="
                + isHidden + ", isCreator=" + isCreator + ", numberOfMembers=" + numberOfMembers + ", creator=" + (creator != null ? creator.publicInfo().name() : "") + '}';
    }
}
