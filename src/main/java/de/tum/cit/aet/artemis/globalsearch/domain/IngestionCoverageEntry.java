package de.tum.cit.aet.artemis.globalsearch.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;

/**
 * Persisted per-course projection of Weaviate index coverage, written only by the coverage recompute and read by the
 * admin ingestion-observability dashboard. This is a plain read model, NOT a cache: it holds no domain truth of its own,
 * only a denormalized snapshot so the matrix can sort and filter across all courses without joining or re-scanning.
 * <p>
 * The denormalized course fields ({@link #courseTitle}, {@link #releaseDate}, {@link #active}, {@link #semester}) and the
 * precomputed {@link #coverageGapScore} exist so the list view is a single indexed read on this table with no join to
 * {@code course}. There is intentionally no foreign key to the course: the recompute inserts, updates, and deletes rows
 * to keep the projection in step with the current set of courses.
 */
@Entity
@Table(name = "ingestion_coverage")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IngestionCoverageEntry extends DomainObject {

    @Column(name = "course_id", nullable = false, unique = true)
    private long courseId;

    /**
     * Per-type coverage counts (the metadata types plus the pdf/video content types), stored as a JSON array. Read and
     * written as a unit and never queried by individual type, which is why a JSON column is used rather than a child
     * table.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "type_counts", columnDefinition = "json", nullable = false)
    private List<IngestionTypeCountDTO> typeCounts = new ArrayList<>();

    /**
     * Precomputed coverage-gap severity, higher is worse, so worst-first ordering is a plain
     * {@code ORDER BY coverage_gap_score DESC} on an indexed column rather than a computation at read time. It is a
     * per-course severity score, not a positional rank, so it never needs a global re-ranking when one course changes.
     */
    @Column(name = "coverage_gap_score", nullable = false)
    private int coverageGapScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IngestionCoverageStatus status;

    @Column(name = "course_title")
    private String courseTitle;

    /** Denormalized course release/start date, so the matrix can sort by it without joining the course table. */
    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    /** Denormalized "course is currently active" flag, computed at recompute time. */
    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "semester")
    private String semester;

    /** When this projection row was last recomputed. */
    @Column(name = "computed_at", nullable = false)
    private ZonedDateTime computedAt;

    /** The most recent index write across the course's objects, or {@code null} if the course has nothing indexed. */
    @Column(name = "last_ingested_at")
    private ZonedDateTime lastIngestedAt;

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public List<IngestionTypeCountDTO> getTypeCounts() {
        return typeCounts;
    }

    public void setTypeCounts(List<IngestionTypeCountDTO> typeCounts) {
        this.typeCounts = typeCounts;
    }

    public int getCoverageGapScore() {
        return coverageGapScore;
    }

    public void setCoverageGapScore(int coverageGapScore) {
        this.coverageGapScore = coverageGapScore;
    }

    public IngestionCoverageStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionCoverageStatus status) {
        this.status = status;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public ZonedDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(ZonedDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public ZonedDateTime getLastIngestedAt() {
        return lastIngestedAt;
    }

    public void setLastIngestedAt(ZonedDateTime lastIngestedAt) {
        this.lastIngestedAt = lastIngestedAt;
    }
}
