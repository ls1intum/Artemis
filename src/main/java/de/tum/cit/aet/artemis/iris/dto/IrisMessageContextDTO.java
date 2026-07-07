package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for context information attached to Iris messages.
 * Context provides additional information about what the user is currently viewing or working on,
 * which helps Iris give more relevant and contextual responses.
 * <p>
 * This interface uses Jackson polymorphic type handling to support multiple context types.
 * New context types (e.g., exercise context) can be added by:
 * 1. Creating a new record implementing this interface
 * 2. Adding it to the @JsonSubTypes annotation
 * 3. Defining a unique type name
 * <p>
 * Context information is NOT persisted in the database - it is only sent to Pyris for enhanced responses.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = IrisVideoContextDTO.class, name = "video"), @JsonSubTypes.Type(value = IrisSlidesContextDTO.class, name = "slides"),
        @JsonSubTypes.Type(value = IrisCombinedViewContextDTO.class, name = "combinedView") })
public sealed interface IrisMessageContextDTO permits IrisVideoContextDTO, IrisSlidesContextDTO, IrisCombinedViewContextDTO {

    /**
     * @return the type identifier for this context (e.g., "video", "slides", "exercise")
     */
    String type();
}
