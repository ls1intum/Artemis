package de.tum.cit.aet.artemis.globalsearch.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;

/**
 * Read/write access to the {@link IngestionCoverageEntry} projection. The dashboard reads it here: cross-course sorts
 * (worst-first, release-date, most-recent-ingestion) go through a {@link Pageable} {@code Sort} on the indexed columns,
 * the status/active filters through {@link #findFiltered}, and the live-per-page path (stored values for the visible courses)
 * through {@link #findAllByCourseIdIn}.
 * <p>
 * Not gated on Weaviate: the projection table is created unconditionally by Liquibase and the recompute is the only
 * writer, so the repository is always available ({@code PROFILE_CORE}). The Weaviate dependency lives in the recompute
 * service, not here.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface IngestionCoverageRepository extends ArtemisJpaRepository<IngestionCoverageEntry, Long> {

    /**
     * Finds the projection row for a single course.
     *
     * @param courseId the id of the course
     * @return the stored coverage row, or empty if the course has not been computed yet
     */
    Optional<IngestionCoverageEntry> findByCourseId(long courseId);

    /**
     * Returns the row with the oldest {@code computed_at}, so the recompute can decide whether the projection as a whole
     * is stale (the freshest possible view is only as fresh as its oldest row). Empty when nothing has been computed yet.
     *
     * @return the least recently computed row, if any
     */
    Optional<IngestionCoverageEntry> findTopByOrderByComputedAtAsc();

    /**
     * Reads the stored rows for a set of courses in one query, for the live-per-page path that compares the visible
     * courses' fresh values against the stored projection.
     *
     * @param courseIds the ids of the courses
     * @return the stored coverage rows for the courses that have one
     */
    List<IngestionCoverageEntry> findAllByCourseIdIn(Collection<Long> courseIds);

    /**
     * Reads the projection rows for the cross-course matrix, optionally filtered by overall status, by whether the course
     * is currently active, and by a case-insensitive course-title search. A {@code null} filter means "any", so passing
     * {@code null} for all three returns every row; this single query backs the unfiltered view and every combination of
     * the three filters.
     * <p>
     * The title search is applied here rather than only on the live path because the two are used together: filtering by
     * status and typing a course name is one question, and answering it by ignoring half of it returns the wrong rows.
     * {@code %} and {@code _} in the term are escaped so a typed wildcard searches for itself.
     *
     * @param status   the coverage status to filter by, or {@code null} for any status
     * @param active   {@code true}/{@code false} to keep only active/inactive courses, or {@code null} for either
     * @param search   a case-insensitive substring of the course title, or {@code null}/blank for any title
     * @param pageable the page and sort
     * @return the matching page of coverage rows
     */
    @Query("""
            SELECT e
            FROM IngestionCoverageEntry e
            WHERE (:status IS NULL OR e.status = :status)
                AND (:active IS NULL OR e.active = :active)
                AND (:search IS NULL OR LOWER(e.courseTitle) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(:search, '%', '\\%'), '_', '\\_'), '%')) ESCAPE '\\')
            """)
    Page<IngestionCoverageEntry> findFiltered(@Param("status") IngestionCoverageStatus status, @Param("active") Boolean active, @Param("search") String search, Pageable pageable);
}
