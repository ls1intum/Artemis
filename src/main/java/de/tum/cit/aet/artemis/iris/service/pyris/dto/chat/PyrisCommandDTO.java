package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for a command Iris performs on the client alongside a chat answer, such as pointing the student to a position in the lecture combined view.
 * <p>
 * Uses Jackson polymorphic type handling: add a command type by implementing this interface, listing it in {@code @JsonSubTypes} and {@code permits}, and giving it a unique type
 * name.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = PyrisPointOutCommandDTO.class, name = "pointOut") })
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface PyrisCommandDTO permits PyrisPointOutCommandDTO {

    /**
     * @return the type identifier for this command (e.g., "pointOut")
     */
    String type();
}
