package de.tum.in.www1.artemis.repository.specs;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Exercise_;
import de.tum.in.www1.artemis.domain.Lecture_;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.*;

public class PostSpecs {

    public static Specification<Post> getCourseSpecification(Long courseId, Long lectureId, Long exerciseId) {
        return (root, query, criteriaBuilder) -> {
            if (lectureId != null || exerciseId != null) {
                return criteriaBuilder.conjunction();
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.COURSE), courseId);
            }
        };
    }

    public static Specification<Post> getLectureSpecification(Long lectureId) {
        return ((root, query, criteriaBuilder) -> {
            if (lectureId == null) {
                return null;
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.LECTURE).get(Lecture_.ID), lectureId);
            }
        });
    }

    public static Specification<Post> getExerciseSpecification(Long exerciseId) {
        return ((root, query, criteriaBuilder) -> {
            if (exerciseId == null) {
                return criteriaBuilder.conjunction();
            }
            else {
                Join<Post, Exercise> joinedExercises = root.join(Post_.EXERCISE, JoinType.INNER);
                joinedExercises.on(criteriaBuilder.equal(joinedExercises.get(Exercise_.ID), exerciseId));

                return criteriaBuilder.equal(root.get(Post_.EXERCISE).get(Exercise_.ID), exerciseId);
            }
        });
    }

    public static Specification<Post> getCourseWideContextSpecification(CourseWideContext courseWideContext) {
        return ((root, query, criteriaBuilder) -> {
            if (courseWideContext == null) {
                return null;
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.COURSE_WIDE_CONTEXT), courseWideContext);
            }
        });
    }

    public static Specification<Post> getOwnSpecification(Boolean filterToOwn, Long userId) {
        return ((root, query, criteriaBuilder) -> {
            if (filterToOwn == null || !filterToOwn) {
                return null;
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.AUTHOR), userId);
            }
        });
    }

    public static Specification<Post> getAnsweredOrReactedSpecification(Boolean answeredOrReacted, Long userId) {
        return ((root, query, criteriaBuilder) -> {
            if (answeredOrReacted == null || !answeredOrReacted) {
                return null;
            }
            else {
                Join<Post, AnswerPost> joinedAnswers = root.join(Post_.ANSWERS, JoinType.LEFT);
                joinedAnswers.on(criteriaBuilder.equal(root.get(Post_.ID), joinedAnswers.get(AnswerPost_.POST).get(Post_.ID)));

                Join<Post, AnswerPost> joinedReactions = root.join(Post_.REACTIONS, JoinType.LEFT);
                joinedReactions.on(criteriaBuilder.equal(root.get(Post_.ID), joinedReactions.get(Reaction_.POST).get(Post_.ID)));

                Predicate answered = criteriaBuilder.equal(joinedAnswers.get(AnswerPost_.AUTHOR), userId);
                Predicate reacted = criteriaBuilder.equal(joinedReactions.get(Reaction_.USER), userId);

                return criteriaBuilder.or(answered, reacted);
            }
        });
    }

    public static Specification<Post> getUnresolvedSpecification(Boolean unresolved) {
        // TODO
        return ((root, query, criteriaBuilder) -> {
            if (unresolved == null || !unresolved) {
                return null;
            }
            else {
                Join<Post, AnswerPost> joinedAnswers = root.join(Post_.ANSWERS, JoinType.LEFT);
                joinedAnswers.on(criteriaBuilder.equal(root.get(Post_.ID), joinedAnswers.get(AnswerPost_.POST).get(Post_.ID)));

                // Predicate postsWithoutCourseWideContext = criteriaBuilder.isNull(root.get(Post_.COURSE_WIDE_CONTEXT));
                // Predicate notAnnouncementPosts = criteriaBuilder.notEqual(root.get(Post_.COURSE_WIDE_CONTEXT), CourseWideContext.ANNOUNCEMENT);

                // Predicate postsWithoutAnswers = criteriaBuilder.isNull(joinedAnswers.get(Post_.ANSWERS));

                Predicate postsWithoutResolvingAnswers = criteriaBuilder.isNotMember(Boolean.TRUE, joinedAnswers.get(AnswerPost_.RESOLVES_POST));

                // Predicate notAnnouncement = criteriaBuilder.or(postsWithoutCourseWideContext, notAnnouncementPosts);
                // Predicate notResolves = criteriaBuilder.and(criteriaBuilder.conjunction(), postsWithoutResolvingAnswers);

                // return criteriaBuilder.and(notAnnouncement, postsWithoutResolvingAnswers);

                return criteriaBuilder.not(postsWithoutResolvingAnswers);
            }
        });
    }

    public static Specification<Post> getSortSpecification(PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {
        return ((root, query, criteriaBuilder) -> {

            List<Order> orderList = new ArrayList<>();

            Expression<Object> caseExpression = criteriaBuilder.selectCase()
                    .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.PINNED)), 1)
                    .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.NONE)), 2)
                    .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.ARCHIVED)), 3);
            orderList.add(criteriaBuilder.asc(caseExpression));

            if (postSortCriterion != null && postSortCriterion != PostSortCriterion.CREATION_DATE) {
                orderList.add(sortingOrder == SortingOrder.ASCENDING ? criteriaBuilder.asc(root.get(Post_.CREATION_DATE)) : criteriaBuilder.desc(root.get(Post_.CREATION_DATE)));
            }

            query.orderBy(orderList);

            return criteriaBuilder.conjunction();
        });
    }

    /**
     * Creates the specification to get distinct results.
     *
     * @return specification used to chain database operations
     */
    public static Specification<Post> distinct() {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            return null;
        };
    }
}
