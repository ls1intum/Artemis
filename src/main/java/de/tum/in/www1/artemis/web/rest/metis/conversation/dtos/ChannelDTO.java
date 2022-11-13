package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;

public class ChannelDTO extends ConversationDTO {

    private String name;

    private String description;

    private String topic;

    private Boolean isPublic;

    private Boolean isArchived;

    // property not taken from entity
    /**
     * Am course instructor has channel admin rights but is not necessarily a member or admin of the channel
     */
    private Boolean hasChannelAdminRights;

    // property not taken from entity
    /**
     * Member of the channel that is also an admin of the channel
     */
    private Boolean isChannelAdmin;

    public ChannelDTO(Channel channel) {
        super(channel, "channel");
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.isPublic = channel.getIsPublic();
        this.topic = channel.getTopic();
        this.isArchived = channel.getIsArchived();
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

    public Boolean getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(Boolean archived) {
        isArchived = archived;
    }

    public Boolean getHasChannelAdminRights() {
        return hasChannelAdminRights;
    }

    public void setHasChannelAdminRights(Boolean hasChannelAdminRights) {
        this.hasChannelAdminRights = hasChannelAdminRights;
    }

    public Boolean getIsChannelAdmin() {
        return isChannelAdmin;
    }

    public void setIsChannelAdmin(Boolean isChannelAdmin) {
        this.isChannelAdmin = isChannelAdmin;
    }

    public Channel toChannel() {
        Channel channel = new Channel();
        channel.setName(name);
        channel.setDescription(description);
        channel.setIsPublic(isPublic);
        channel.setIsArchived(isArchived);
        return channel;
    }

    @Override
    public String toString() {
        return "ChannelDTO{" + "name='" + name + '\'' + ", description='" + description + '\'' + ", topic='" + topic + '\'' + ", isPublic=" + isPublic + ", isArchived="
                + isArchived + ", isChannelAdmin=" + isChannelAdmin + ", hasChannelAdminRights=" + hasChannelAdminRights + "}" + super.toString();
    }
}
