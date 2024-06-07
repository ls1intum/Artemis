package de.tum.in.www1.artemis.repository.specs;

import java.time.Duration;
import java.time.ZonedDateTime;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.BuildJob_;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Course_;
import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;

public class BuildJobSpecs {

    /**
     * Filter for build jobs that are associated with a specific build status
     *
     * @param buildStatus the id of the participation
     * @return the specification
     */
    public static Specification<BuildJob> hasBuildStatus(BuildStatus buildStatus) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (buildStatus == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get(BuildJob_.buildStatus), buildStatus);
        };
    }

    /**
     * Filter for build jobs that are associated with a build agent address
     *
     * @param buildAgentAddress the build agent address
     * @return the specification
     */
    public static Specification<BuildJob> hasBuildAgentAddress(String buildAgentAddress) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (buildAgentAddress == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get(BuildJob_.BUILD_AGENT_ADDRESS), buildAgentAddress);
        };
    }

    /**
     * Filter for build jobs that are started after a specific date
     *
     * @param startDate the start date
     * @return the specification
     */
    public static Specification<BuildJob> buildStartDateAfter(ZonedDateTime startDate) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (startDate == null) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(root.get(BuildJob_.BUILD_START_DATE), startDate);
        };
    }

    /**
     * Filter for build jobs that have a build start date before the given end date
     *
     * @param endDate the end date
     * @return the specification
     */
    public static Specification<BuildJob> buildStartDateBefore(ZonedDateTime endDate) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (endDate == null) {
                return cb.conjunction();
            }
            return cb.lessThanOrEqualTo(root.get(BuildJob_.BUILD_START_DATE), endDate);
        };
    }

    /**
     * Filter for build jobs that are associated with a search term. Looks into Repository Name and Course Title
     *
     * @param searchTerm the id of the course
     * @return the specification
     */
    public static Specification<BuildJob> hasSearchTerm(String searchTerm) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (searchTerm == null || searchTerm.isEmpty()) {
                return cb.conjunction();
            }
            Join<BuildJob, Course> courseJoin = root.join(BuildJob_.COURSE_ID, JoinType.LEFT);
            return cb.or(cb.like(root.get(BuildJob_.REPOSITORY_NAME), "%" + searchTerm + "%"), cb.like(courseJoin.get(Course_.TITLE), "%" + searchTerm + "%"));
        };
    }

    /**
     * Filter for build jobs that are associated with a specific course
     *
     * @param courseId the id of the course
     * @return the specification
     */
    public static Specification<BuildJob> hasCourseId(Long courseId) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (courseId == null) {
                return null;
            }
            return cb.equal(root.get(BuildJob_.COURSE_ID), courseId);
        };
    }

    /**
     * Filter for build jobs that have a duration greater than or equal to the given duration
     *
     * @param durationLower the lower bound of the duration
     * @return the specification
     */
    public static Specification<BuildJob> durationGreaterThanOrEqualTo(Duration durationLower) {
        return (root, query, builder) -> {
            if (durationLower == null) {
                return builder.conjunction();
            }
            var durationExpression = builder.diff(root.get(BuildJob_.BUILD_COMPLETION_DATE), root.get(BuildJob_.BUILD_START_DATE));
            return builder.greaterThanOrEqualTo(durationExpression.as(Duration.class), durationLower);
        };
    }

    /**
     * Filter for build jobs that have a duration less than or equal to the given duration
     *
     * @param durationUpper the upper bound of the duration
     * @return the specification
     */
    public static Specification<BuildJob> durationLessThanOrEqualTo(Duration durationUpper) {
        return (root, query, builder) -> {
            if (durationUpper == null) {
                return builder.conjunction();
            }
            var durationExpression = builder.diff(root.get(BuildJob_.BUILD_COMPLETION_DATE), root.get(BuildJob_.BUILD_START_DATE));
            return builder.lessThanOrEqualTo(durationExpression.as(Duration.class), durationUpper);
        };
    }
}
