package de.tum.in.www1.artemis.repository.specs;

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
import de.tum.in.www1.artemis.domain.Result_;
import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;
import de.tum.in.www1.artemis.domain.participation.Participation_;

public class BuildJobSpecs {

    public static Specification<BuildJob> hasBuildStatus(BuildStatus buildStatus) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (buildStatus == null) {
                return null;
            }
            return cb.equal(root.get(BuildJob_.buildStatus), buildStatus);
        };
    }

    public static Specification<BuildJob> hasBuildAgentAddress(String buildAgentAddress) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (buildAgentAddress == null) {
                return null;
            }
            return cb.equal(root.get(BuildJob_.BUILD_AGENT_ADDRESS), buildAgentAddress);
        };
    }

    public static Specification<BuildJob> buildStartDateAfter(ZonedDateTime startDate) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (startDate == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(root.get(BuildJob_.BUILD_START_DATE), startDate);
        };
    }

    public static Specification<BuildJob> buildStartDateBefore(ZonedDateTime endDate) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (endDate == null) {
                return null;
            }
            return cb.lessThanOrEqualTo(root.get(BuildJob_.BUILD_START_DATE), endDate);
        };
    }

    public static Specification<BuildJob> hasSearchTerm(String searchTerm) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (searchTerm == null || searchTerm.isEmpty()) {
                return null;
            }
            Join<BuildJob, Course> courseJoin = root.join("course", JoinType.LEFT);
            return cb.or(cb.like(root.get(BuildJob_.REPOSITORY_NAME), "%" + searchTerm + "%"), cb.like(courseJoin.get(Course_.TITLE), "%" + searchTerm + "%"));
        };
    }

    public static Specification<BuildJob> hasCourseId(Long courseId) {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (courseId == null) {
                return null;
            }
            return cb.equal(root.get(BuildJob_.COURSE_ID), courseId);
        };
    }

    public static Specification<BuildJob> fetchAssociations() {
        return (Root<BuildJob> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (query.getResultType() != Long.class) {
                root.fetch(BuildJob_.RESULT, JoinType.LEFT).fetch(Result_.PARTICIPATION, JoinType.LEFT).fetch(Participation_.EXERCISE, JoinType.LEFT);
                root.fetch(BuildJob_.RESULT, JoinType.LEFT).fetch(Result_.SUBMISSION, JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}
