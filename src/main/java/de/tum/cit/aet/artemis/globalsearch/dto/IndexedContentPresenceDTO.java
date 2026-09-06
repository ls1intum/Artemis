package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.Set;

/**
 * Which lecture units actually hold content in one Iris collection for a course, for the admin content browser's tree.
 * <p>
 * This is presence, not payload. The browser needs to know which units have slides, a transcript, and so on in order to
 * draw the tree; it does not need the objects themselves until a node is selected. Reading presence as a distinct unit
 * set keeps the answer exact at any course size, whereas reading the objects and inferring presence from them would be
 * truncated by any cap: these collections are chunk-grained, so a single unit can hold hundreds of objects.
 *
 * @param key     the browser's stable content key ({@code slides}, {@code transcript}, {@code unit_summary}, {@code segments})
 * @param unitIds the ids of the lecture units holding at least one object in the backing collection
 */
public record IndexedContentPresenceDTO(String key, Set<Long> unitIds) {
}
