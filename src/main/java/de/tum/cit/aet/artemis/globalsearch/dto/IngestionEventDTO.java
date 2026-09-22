package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventKind;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventLogEntry;

/**
 * One row of the admin activity feed: a single thing that happened to one indexed entity.
 *
 * @param id         the event's own id, used as the feed's stable list key
 * @param kind       what happened
 * @param occurredAt when it happened
 * @param entityType the kind of thing it happened to, e.g. {@code LectureUnit}
 * @param entityId   the id of that thing
 * @param courseId   the course it belongs to, or null when the event is not course-scoped
 * @param detail     short human-readable context, rendered verbatim
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngestionEventDTO(long id, IngestionEventKind kind, ZonedDateTime occurredAt, String entityType, long entityId, @Nullable Long courseId, @Nullable String detail) {

    /**
     * Projects a stored event onto the wire shape.
     *
     * @param entry the stored event
     * @return the DTO for the feed
     */
    public static IngestionEventDTO of(IngestionEventLogEntry entry) {
        return new IngestionEventDTO(entry.getId(), entry.getKind(), entry.getOccurredAt(), entry.getEntityType(), entry.getEntityId(), entry.getCourseId(), entry.getDetail());
    }
}
