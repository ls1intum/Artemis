package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;

public class ChannelDTO extends ConversationDTO {

    private String name;

    private String description;

    private String topic;

    private Boolean isPublic;

    public ChannelDTO(Channel channel) {
        super(channel, "channel");
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.isPublic = channel.getIsPublic();
        this.topic = channel.getTopic();
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

    public Channel toChannel() {
        Channel channel = new Channel();
        channel.setName(name);
        channel.setDescription(description);
        channel.setIsPublic(isPublic);
        return channel;
    }
}
