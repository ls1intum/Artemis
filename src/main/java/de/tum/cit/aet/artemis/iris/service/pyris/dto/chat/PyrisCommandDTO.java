package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for a command Iris performs on the client alongside a chat answer, such as pointing the student to a position in the lecture combined view. Commands ride along on
 * the {@link PyrisChatStatusUpdateDTO} of a pipeline run; Artemis fans each one out into a COMMAND message (a clickable marker in the chat history) and, where applicable, drives
 * the corresponding client-side action.
 * <p>
 * This interface uses Jackson polymorphic type handling to support multiple command types. New command types can be added by:
 * 1. Creating a new record implementing this interface
 * 2. Adding it to the {@code @JsonSubTypes} annotation and the {@code permits} clause
 * 3. Defining a unique type name and handling it in the fan-out dispatch
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
