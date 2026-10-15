package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The full stored record of one {@code SearchableEntities} row, for the browser's detail pane.
 * <p>
 * This is the heavy counterpart of {@link IndexedEntityDTO}, which carries only what the tree needs to place a row.
 * It is read for the one type an admin has selected rather than for a whole course, because the property map includes
 * the entity's body text.
 *
 * @param type       the {@code SearchableEntitySchema.TypeValues} discriminator
 * @param entityId   the database id of the entity this row represents
 * @param title      the stored title, or {@code null} if the row has none
 * @param ingestedAt when Weaviate created the object, or {@code null} if the creation time could not be read
 * @param properties the stored properties, minus the ones this row has no value for
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IndexedEntityRecordDTO(String type, long entityId, String title, Instant ingestedAt, Map<String, Object> properties) {
}
