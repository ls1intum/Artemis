package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;

public class ChannelDTO extends ConversationDTO {

    private final String name;

    private final String description;

    private final Boolean isPublic;

    public ChannelDTO(Channel channel) {
        super(channel, "channel");
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.isPublic = channel.getIsPublic();
    }

    public String getName() {
        return name;
    }

    public String setName(String name) {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String setDescription(String description) {
        return description;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public Boolean setIsPublic(Boolean isPublic) {
        return isPublic;
    }

}
