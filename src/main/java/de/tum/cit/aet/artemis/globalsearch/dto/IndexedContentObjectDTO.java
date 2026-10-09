package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One object stored in an Iris lecture-content collection (a slide chunk, a transcript segment, a unit summary, or an
 * aligned segment), for the admin content browser's detail pane.
 * <p>
 * These collections belong to the Iris ingestion pipeline rather than to Artemis, so the property map is passed through
 * as read instead of being mapped onto a fixed shape: the browser exists precisely to show what is really there. Fields
 * the object has no value for are dropped, as they are for indexed entities.
 *
 * @param ingestedAt when Weaviate created the object, or {@code null} if the creation time could not be read
 * @param properties the populated stored properties
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IndexedContentObjectDTO(Instant ingestedAt, Map<String, Object> properties) {
}
