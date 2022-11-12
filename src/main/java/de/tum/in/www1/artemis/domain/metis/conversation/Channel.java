package de.tum.in.www1.artemis.domain.metis.conversation;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("C")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Channel extends Conversation {

    /**
     * The name of the channel. Must be unique in the course.
     */
    @Column(name = "name")
    @Size(min = 1, max = 20)
    @NotNull
    private String name;

    /**
     * What is the purpose of this channel? (not shown in header)
     */
    @Column(name = "description")
    @Size(min = 1, max = 250)
    @Nullable
    private String description;

    /**
     * What is the current topic of this channel? (shown in header)
     */
    @Column(name = "topic")
    @Size(min = 1, max = 250)
    @Nullable
    private String topic;

    /**
     * A channel is either public or private. Users need an invitation to join a private channel. Every user can join a public channel.
     */
    @Column(name = "is_public")
    @NotNull
    private Boolean isPublic;

    /**
     * A channel that is no longer needed can be archived or deleted.
     * Archived channels are closed to new activity, but the message history is retained and searchable.
     * The channel can be unarchived at any time.
     */
    @Column(name = "is_archived")
    @NotNull
    private Boolean isArchived;

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Nullable
    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(@Nullable Boolean isPublic) {
        this.isPublic = isPublic;
    }

    @Nullable
    public String getTopic() {
        return topic;
    }

    public void setTopic(@Nullable String topic) {
        this.topic = topic;
    }

    public Boolean getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(Boolean archived) {
        isArchived = archived;
    }
}
