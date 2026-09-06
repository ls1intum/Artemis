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
// EXISTING_PROPERTY (not the default PROPERTY): every subtype declares its own `type` record component, so the
// discriminator and the bean property are the same field. Jackson 3.2 rejects the default with
// "Conflict between type id property 'type' and bean property with same name"; this tells Jackson to read and
// write the discriminator from that existing component instead of emitting a second one. `visible = true` keeps
// the component populated on deserialization and present on the wire (same pattern as LectureDetailsDTO and
// QuizQuestionRefinementResponseDTO). The wire format is unchanged: a single `type` field with the subtype name.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = IrisVideoContextDTO.class, name = "video"), @JsonSubTypes.Type(value = IrisSlidesContextDTO.class, name = "slides"),
        @JsonSubTypes.Type(value = IrisCombinedViewContextDTO.class, name = "combinedView") })
public sealed interface IrisMessageContextDTO permits IrisVideoContextDTO, IrisSlidesContextDTO, IrisCombinedViewContextDTO {

    /**
     * @return the type identifier for this context (e.g., "video", "slides", "exercise")
     */
    String type();
}
