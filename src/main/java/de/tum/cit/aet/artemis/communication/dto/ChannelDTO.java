package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.ChannelSubType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ChannelDTO extends ConversationDTO {

    private String name;

    private String description;

    private String topic;

    private Boolean isPublic;

    private Boolean isAnnouncementChannel;

    private Boolean isArchived;

    private Boolean isCourseWide;

    // property not taken from entity
    /**
     * A course instructor has channel moderation rights but is not necessarily a moderator of the channel
     */
    private Boolean hasChannelModerationRights;

    // property not taken from entity
    /**
     * Member of the channel that is also a moderator of the channel
     */
    private Boolean isChannelModerator;

    // property not taken from entity
    /**
     * The id of the tutorial group that is associated with the channel, if any
     */
    private Long tutorialGroupId;

    // property not taken from entity
    /**
     * The name of the tutorial group that is associated with the channel, if any
     */
    private String tutorialGroupTitle;

    // property not taken from entity
    /**
     * Determines the subtype of the channel depending on whether the channel is associated with an exercise/lecture/exam or not
     */
    private ChannelSubType subType;

    // property not taken from entity
    /**
     * Contains the lecture/exercise/exam id if the channel is associated with a lecture/exercise/exam, else null
     */
    private Long subTypeReferenceId;

    public ChannelDTO(Channel channel) {
        super(channel, "channel");
        this.setSubTypeWithReferenceFromChannel(channel);
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.isPublic = channel.getIsPublic();
        this.topic = channel.getTopic();
        this.isArchived = channel.getIsArchived();
        this.isAnnouncementChannel = channel.getIsAnnouncementChannel();
        this.isCourseWide = channel.getIsCourseWide();
    }

    public ChannelDTO() {
        this.setType("channel");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Boolean getIsAnnouncementChannel() {
        return isAnnouncementChannel;
    }

    public void setIsAnnouncementChannel(Boolean announcementChannel) {
        isAnnouncementChannel = announcementChannel;
    }

    public Boolean getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(Boolean archived) {
        isArchived = archived;
    }

    public Boolean getHasChannelModerationRights() {
        return hasChannelModerationRights;
    }

    public void setHasChannelModerationRights(Boolean hasChannelModerationRights) {
        this.hasChannelModerationRights = hasChannelModerationRights;
    }

    public Boolean getIsChannelModerator() {
        return isChannelModerator;
    }

    public void setIsChannelModerator(Boolean isChannelModerator) {
        this.isChannelModerator = isChannelModerator;
    }

    public Long getTutorialGroupId() {
        return tutorialGroupId;
    }

    public void setTutorialGroupId(Long tutorialGroupId) {
        this.tutorialGroupId = tutorialGroupId;
    }

    public String getTutorialGroupTitle() {
        return tutorialGroupTitle;
    }

    public void setTutorialGroupTitle(String tutorialGroupTitle) {
        this.tutorialGroupTitle = tutorialGroupTitle;
    }

    public ChannelSubType getSubType() {
        return subType;
    }

    public Long getSubTypeReferenceId() {
        return subTypeReferenceId;
    }

    public Boolean getIsCourseWide() {
        return isCourseWide;
    }

    public void setIsCourseWide(Boolean courseWide) {
        isCourseWide = courseWide;
    }

    @Override
    public String toString() {
        return "ChannelDTO{" + "subType='" + subType + '\'' + ", name='" + name + '\'' + ", description='" + description + '\'' + ", topic='" + topic + '\'' + ", isPublic="
                + isPublic + ", isAnnouncementChannel=" + isAnnouncementChannel + ", isArchived=" + isArchived + ", isCourseWide=" + isCourseWide + ", isChannelModerator="
                + isChannelModerator + ", hasChannelModerationRights=" + hasChannelModerationRights + ", tutorialGroupId=" + tutorialGroupId + ", tutorialGroupTitle="
                + tutorialGroupTitle + "}" + super.toString();
    }

    private void setSubTypeWithReferenceFromChannel(Channel channel) {
        if (channel.getExercise() != null) {
            this.subType = ChannelSubType.EXERCISE;
            this.subTypeReferenceId = channel.getExercise().getId();
        }
        else if (channel.getLecture() != null) {
            this.subType = ChannelSubType.LECTURE;
            this.subTypeReferenceId = channel.getLecture().getId();
        }
        else if (channel.getExam() != null) {
            this.subType = ChannelSubType.EXAM;
            this.subTypeReferenceId = channel.getExam().getId();
        }
        else {
            this.subType = ChannelSubType.GENERAL;
        }
    }
}
