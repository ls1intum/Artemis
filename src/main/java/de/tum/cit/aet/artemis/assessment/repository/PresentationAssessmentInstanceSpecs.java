package de.tum.cit.aet.artemis.assessment.repository;

import jakarta.persistence.criteria.Expression;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.account.domain.User_;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance_;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment_;
import de.tum.cit.aet.artemis.core.domain.DomainObject_;

/**
 * Filters for paginated presentation assessment instances.
 */
public final class PresentationAssessmentInstanceSpecs {

    private PresentationAssessmentInstanceSpecs() {
    }

    /**
     * Matches instances belonging to the given course.
     *
     * @param courseId the owning course id
     * @return specification restricting instances to the course
     */
    public static Specification<PresentationAssessmentInstance> forCourse(long courseId) {
        return (root, query, builder) -> builder.equal(root.get(PresentationAssessmentInstance_.PRESENTATION_ASSESSMENT).get(PresentationAssessment_.COURSE).get(DomainObject_.ID),
                courseId);
    }

    /**
     * Matches instances of a specific presentation assessment.
     *
     * @param assessmentId the presentation assessment id, or null for no filter
     * @return specification applying the presentation assessment filter
     */
    public static Specification<PresentationAssessmentInstance> forAssessment(@Nullable Long assessmentId) {
        if (assessmentId == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) -> builder.equal(root.get(PresentationAssessmentInstance_.PRESENTATION_ASSESSMENT).get(DomainObject_.ID), assessmentId);
    }

    /**
     * Filters instances by whether result points have been assigned.
     *
     * @param assessed true for assessed instances, false for pending instances, or null for no filter
     * @return specification applying the assessment status filter
     */
    public static Specification<PresentationAssessmentInstance> withAssessmentStatus(@Nullable Boolean assessed) {
        if (assessed == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) -> assessed ? builder.isNotNull(root.get(PresentationAssessmentInstance_.RESULT_POINTS))
                : builder.isNull(root.get(PresentationAssessmentInstance_.RESULT_POINTS));
    }

    /**
     * Filters instances by whether their presentation assessment is linked to an exercise.
     *
     * @param linkedToExercise true for exercise-linked presentations, false for standalone presentations, or null for no filter
     * @return specification applying the exercise-link filter
     */
    public static Specification<PresentationAssessmentInstance> linkedToExercise(@Nullable Boolean linkedToExercise) {
        if (linkedToExercise == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) -> {
            var exercise = root.get(PresentationAssessmentInstance_.PRESENTATION_ASSESSMENT).get(PresentationAssessment_.EXERCISE);
            return linkedToExercise ? builder.isNotNull(exercise) : builder.isNull(exercise);
        };
    }

    /**
     * Matches student login, full name, or presentation title against a prepared LIKE pattern.
     *
     * @param searchPattern the lower-case LIKE pattern with escaped special characters and surrounding wildcards, or null for no filter
     * @return specification applying the search predicate
     */
    public static Specification<PresentationAssessmentInstance> matchesSearch(@Nullable String searchPattern) {
        if (searchPattern == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) -> {
            var student = root.join(PresentationAssessmentInstance_.STUDENT);
            var assessment = root.join(PresentationAssessmentInstance_.PRESENTATION_ASSESSMENT);
            Expression<String> fullName = builder
                    .trim(builder.concat(builder.concat(builder.coalesce(student.get(User_.FIRST_NAME), ""), " "), builder.coalesce(student.get(User_.LAST_NAME), "")));

            return builder.or(builder.like(builder.lower(student.get(User_.LOGIN)), searchPattern, '\\'), builder.like(builder.lower(fullName), searchPattern, '\\'),
                    builder.like(builder.lower(assessment.get(PresentationAssessment_.TITLE)), searchPattern, '\\'));
        };
    }
}
