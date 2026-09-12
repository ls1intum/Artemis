package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

/**
 * Everything the content browser needs to open a course, in one response.
 * <p>
 * These four are served together rather than from an endpoint each because they are all derived from the same two
 * id-sets: what the database expects indexed, and what the index holds. Fetched separately, each request reloaded those
 * sets, so opening one course paid for the same reads three times over.
 *
 * @param entities        the {@code SearchableEntities} rows stored for the course
 * @param contentPresence which lecture units hold content in each Iris collection
 * @param missingEntities the entities the database expects that the index does not hold, named
 * @param contentGaps     the lecture units whose slide or transcript content was never ingested, named
 */
public record CourseBrowserDataDTO(List<IndexedEntityDTO> entities, List<IndexedContentPresenceDTO> contentPresence, List<MissingEntityDTO> missingEntities,
        List<MissingContentDTO> contentGaps) {
}
