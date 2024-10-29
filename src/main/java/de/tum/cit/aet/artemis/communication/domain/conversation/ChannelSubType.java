package de.tum.cit.aet.artemis.communication.domain.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ChannelSubType {
    @JsonProperty("general")
    GENERAL,

    @JsonProperty("exercise")
    EXERCISE,

    @JsonProperty("lecture")
    LECTURE,

    @JsonProperty("exam")
    EXAM
}
