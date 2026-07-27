package de.tum.cit.aet.artemis.localci.repository;

import java.time.Duration;
import java.time.ZonedDateTime;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.Course_;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.domain.BuildJob_;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Criteria API predicates for filtering {@link BuildJob}s in the build overview.
 * <p>
 * These replace a single JPQL query that expressed every optional filter as {@code (:param IS NULL OR column = :param)}. MySQL cannot fold those branches away, so it could not
 * estimate the selectivity of any filter and fell back to satisfying the {@code ORDER BY} by scanning {@code idx_build_job_build_submission_date} in reverse across the whole
 * table — 17 s for a course-scoped query on production. Every method here returns {@code null} when its filter is absent; the caller filters those out before combining, so an
 * unset filter contributes no SQL at all and the optimizer sees only real predicates. Note that {@code Specification.allOf} rejects null elements, so they must not simply be
 * passed through.
 */
public final class BuildJobSpecs {

    private BuildJobSpecs() {
    }

    /**
     * Restricts to build jobs that are no longer queued or running.
     *
     * @return specification matching only finished build jobs
     */
    public static Specification<BuildJob> isFinished() {
        return (root, query, cb) -> cb.not(root.get(BuildJob_.BUILD_STATUS).in(BuildStatus.QUEUED, BuildStatus.BUILDING));
    }

    /**
     * @param buildStatus the status to filter by, or null for no filter
     * @return specification matching the given build status, or null
     */
    public static @Nullable Specification<BuildJob> hasBuildStatus(@Nullable BuildStatus buildStatus) {
        return buildStatus == null ? null : (root, query, cb) -> cb.equal(root.get(BuildJob_.BUILD_STATUS), buildStatus);
    }

    /**
     * @param buildAgentAddress the build agent address to filter by, or null for no filter
     * @return specification matching the given build agent, or null
     */
    public static @Nullable Specification<BuildJob> hasBuildAgentAddress(@Nullable String buildAgentAddress) {
        return buildAgentAddress == null ? null : (root, query, cb) -> cb.equal(root.get(BuildJob_.BUILD_AGENT_ADDRESS), buildAgentAddress);
    }

    /**
     * @param courseId the course to filter by, or null for no filter
     * @return specification matching build jobs of the given course, or null
     */
    public static @Nullable Specification<BuildJob> inCourse(@Nullable Long courseId) {
        return courseId == null ? null : (root, query, cb) -> cb.equal(root.get(BuildJob_.COURSE_ID), courseId);
    }

    /**
     * @param startDate the earliest submission date, or null for no lower bound
     * @return specification matching build jobs submitted at or after the given date, or null
     */
    public static @Nullable Specification<BuildJob> submittedFrom(@Nullable ZonedDateTime startDate) {
        return startDate == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(BuildJob_.BUILD_SUBMISSION_DATE), startDate);
    }

    /**
     * @param endDate the latest submission date, or null for no upper bound
     * @return specification matching build jobs submitted at or before the given date, or null
     */
    public static @Nullable Specification<BuildJob> submittedUntil(@Nullable ZonedDateTime endDate) {
        return endDate == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get(BuildJob_.BUILD_SUBMISSION_DATE), endDate);
    }

    /**
     * @param durationLower the minimum build duration, or null for no lower bound
     * @return specification matching build jobs that took at least the given duration, or null
     */
    public static @Nullable Specification<BuildJob> durationAtLeast(@Nullable Duration durationLower) {
        return durationLower == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(buildDuration(root, cb), durationLower);
    }

    /**
     * @param durationUpper the maximum build duration, or null for no upper bound
     * @return specification matching build jobs that took at most the given duration, or null
     */
    public static @Nullable Specification<BuildJob> durationAtMost(@Nullable Duration durationUpper) {
        return durationUpper == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(buildDuration(root, cb), durationUpper);
    }

    /**
     * Free-text match against the repository name or the title of the owning course.
     * <p>
     * {@code BuildJob.courseId} is a plain column rather than an association, so the course side is a subquery. It is deliberately <em>uncorrelated</em>: it selects the ids of
     * all courses whose title matches, independently of the current row. MySQL then evaluates it once instead of per {@code build_job} row. A correlated {@code EXISTS} was
     * measured at ~0.0016 ms per row over 229 439 rows (~380 ms) on production, whereas the uncorrelated form lets the optimizer drop the branch entirely when no course title
     * matches — the common case when searching by repository name.
     * <p>
     * Both sides use a leading wildcard, which no index can serve; making this sargable would need prefix matching or a full-text index and is deliberately out of scope. Case
     * sensitivity is intentionally left as-is (the database collation decides, as before) so that this stays a pure performance change.
     *
     * @param searchTerm the term to search for, or null/blank for no filter
     * @return specification matching the search term, or null
     */
    public static @Nullable Specification<BuildJob> matchesSearchTerm(@Nullable String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }
        final String pattern = "%" + searchTerm + "%";
        return (root, query, cb) -> {
            Subquery<Long> matchingCourseIds = query.subquery(Long.class);
            Root<Course> course = matchingCourseIds.from(Course.class);
            matchingCourseIds.select(course.get(DomainObject_.ID)).where(cb.like(course.get(Course_.TITLE), pattern));
            return cb.or(cb.like(root.get(BuildJob_.REPOSITORY_NAME), pattern), root.get(BuildJob_.COURSE_ID).in(matchingCourseIds));
        };
    }

    /**
     * The wall-clock build duration. Mirrors the JPQL {@code (b.buildCompletionDate - b.buildStartDate)} this replaces; it is an expression over two columns, so no index can
     * serve a filter on it either before or after this change.
     * <p>
     * Note the argument order: {@code durationBetween(x, y)} builds {@code x - y}, not {@code y - x} (see {@code SqmCriteriaNodeBuilder}). Passing start before completion yields
     * a negative duration and silently matches nothing.
     */
    private static Expression<Duration> buildDuration(Root<BuildJob> root, CriteriaBuilder cb) {
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        return hcb.durationBetween(root.get(BuildJob_.BUILD_COMPLETION_DATE), root.get(BuildJob_.BUILD_START_DATE));
    }
}
