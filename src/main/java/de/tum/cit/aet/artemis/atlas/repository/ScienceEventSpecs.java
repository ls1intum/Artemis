package de.tum.cit.aet.artemis.atlas.repository;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;

/**
 * Filters for the research export over {@link ScienceEvent}.
 * <p>
 * These exist instead of one JPQL query expressing every optional filter as {@code (:param IS NULL OR column = :param)}.
 * PostgreSQL cannot infer a type for a parameter whose only use before the OR branch is {@code ? IS NULL}, so an export
 * that left the date range out failed with {@code could not determine data type of parameter}. Each method here returns
 * {@code null} when its filter is absent and the caller drops those before combining, so an unset filter contributes no
 * SQL and no bind parameter at all — and the optimizer sees only real predicates rather than a branch it cannot fold.
 * <p>
 * Note that {@code Specification.allOf} rejects null elements, so they must not simply be passed through.
 */
public final class ScienceEventSpecs {

    private ScienceEventSpecs() {
    }

    /**
     * Restricts the export to events that were already covered by a recorded consent decision when they happened.
     * <p>
     * Without this, the export reaches events that predate course-level consent entirely. The migration backfills
     * {@code course_id} onto historical rows so that a student can delete them per course, and those rows were
     * collected under the former global setting, which the server never consulted before storing an event. Naming the
     * course would otherwise be enough to export them, for people who never agreed to anything a server enforced.
     * <p>
     * The decision has to predate the event, not merely exist. Asking only whether a decision exists inverts the
     * intent: a student with backfilled history who opens the settings page and declines would thereby admit their
     * whole pre-consent history to the export, having made themselves more exportable by refusing.
     * <p>
     * A decision to decline still counts for the events that follow it, which in practice means its own marker: that
     * marker is what separates "did not interact" from "did not agree to be measured", and dropping it would erase the
     * refusal from the research record rather than honour it.
     * <p>
     * Bounding on time also keeps the gate stable for the length of an export. A decision recorded while the export
     * runs is necessarily later than every event the export covers, so it cannot admit rows the earlier pages have
     * already passed.
     *
     * @param decisionTypes the event types that record a consent decision
     * @return the specification
     */
    public static Specification<ScienceEvent> hasRecordedConsentDecision(Set<ScienceEventType> decisionTypes) {
        return (root, query, criteriaBuilder) -> {
            // query is only absent for a Specification evaluated outside a criteria query, which the research export
            // never does; letting it throw here is a truer signal than quietly dropping the consent gate.
            Subquery<Integer> decision = query.subquery(Integer.class);
            Root<ScienceEvent> marker = decision.from(ScienceEvent.class);
            decision.select(criteriaBuilder.literal(1)).where(criteriaBuilder.equal(marker.get("identity"), root.get("identity")),
                    criteriaBuilder.equal(marker.get("courseId"), root.get("courseId")), marker.get("type").in(decisionTypes),
                    criteriaBuilder.lessThanOrEqualTo(marker.get("timestamp"), root.get("timestamp")));
            return criteriaBuilder.exists(decision);
        };
    }

    public static Specification<ScienceEvent> inCourses(Set<Long> courseIds) {
        return (root, query, criteriaBuilder) -> root.get("courseId").in(courseIds);
    }

    public static Specification<ScienceEvent> hasTypeIn(Set<ScienceEventType> eventTypes) {
        return (root, query, criteriaBuilder) -> root.get("type").in(eventTypes);
    }

    public static @Nullable Specification<ScienceEvent> recordedFrom(@Nullable ZonedDateTime from) {
        return from == null ? null : (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static @Nullable Specification<ScienceEvent> recordedUntil(@Nullable ZonedDateTime to) {
        return to == null ? null : (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    /**
     * Bounds the export to the events that existed when it started, so that rows written while it runs cannot shift the
     * pages underneath it.
     *
     * @param maxEventId the highest event id the export covers
     * @return the specification
     */
    public static Specification<ScienceEvent> idAtMost(long maxEventId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("id"), maxEventId);
    }

    /**
     * Continues after the last exported row. Keyset rather than offset pagination, because a deletion during the export
     * would shift every later offset and silently skip rows; the id breaks ties between events sharing a timestamp.
     *
     * @param lastTimestamp the timestamp of the last exported event, or {@code null} for the first page
     * @param lastId        the id of the last exported event
     * @return the specification, or {@code null} for the first page
     */
    public static @Nullable Specification<ScienceEvent> after(@Nullable ZonedDateTime lastTimestamp, @Nullable Long lastId) {
        if (lastTimestamp == null || lastId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(criteriaBuilder.greaterThan(root.get("timestamp"), lastTimestamp),
                criteriaBuilder.and(criteriaBuilder.equal(root.get("timestamp"), lastTimestamp), criteriaBuilder.greaterThan(root.get("id"), lastId)));
    }
}
