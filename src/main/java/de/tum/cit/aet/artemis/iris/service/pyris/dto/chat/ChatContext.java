package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The context of a chat session in the unified Pyris chat pipeline.
 */
public enum ChatContext {
    @JsonProperty("course")
    COURSE, @JsonProperty("lecture")
    LECTURE, @JsonProperty("exercise")
    EXERCISE, @JsonProperty("text_exercise")
    TEXT_EXERCISE,
}
