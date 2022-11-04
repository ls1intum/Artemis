package de.tum.in.www1.artemis.domain.metis.conversation;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("G")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupChat extends Conversation {
}
