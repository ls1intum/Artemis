package de.tum.cit.aet.artemis.globalsearch.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;

/**
 * Read/write access to the {@link IngestionCoverageEntry} projection. The dashboard reads it here: cross-course sorts
 * (worst-first, release-date, most-recent-ingestion) go through a {@link Pageable} {@code Sort} on the indexed columns,
 * the status filter through {@link #findAllByStatus}, and the live-per-page path (stored values for the visible courses)
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
     * Reads the stored rows for a set of courses in one query, for the live-per-page path that compares the visible
     * courses' fresh values against the stored projection.
     *
     * @param courseIds the ids of the courses
     * @return the stored coverage rows for the courses that have one
     */
    List<IngestionCoverageEntry> findAllByCourseIdIn(Collection<Long> courseIds);

    /**
     * Reads the rows with the given overall status, paginated, for the matrix status filter.
     *
     * @param status   the coverage status to filter by
     * @param pageable the page and sort
     * @return the matching page of coverage rows
     */
    Page<IngestionCoverageEntry> findAllByStatus(IngestionCoverageStatus status, Pageable pageable);
}
