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

    @Column(name = "name")
    @Size(min = 1, max = 20)
    @NotNull
    private String name;

    @Column(name = "description")
    @Size(min = 1, max = 250)
    @Nullable
    private String description;

    @Column(name = "is_public")
    @NotNull
    private Boolean isPublic;

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

    public void setIsPublic(@Nullable Boolean aPublic) {
        isPublic = aPublic;
    }
}
