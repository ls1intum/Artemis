package de.tum.in.www1.artemis.repository.specs;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.*;
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
                Join<Post, Lecture> joinedLectures = root.join(Post_.LECTURE, JoinType.LEFT);
                Join<Post, Exercise> joinedExercises = root.join(Post_.EXERCISE, JoinType.LEFT);

                Predicate coursePosts = criteriaBuilder.equal(root.get(Post_.COURSE), courseId);
                Predicate coursePostsWithLectureContext = criteriaBuilder.equal(joinedLectures.get(Lecture_.COURSE).get(Course_.ID), courseId);
                Predicate coursePostsWithExerciseContext = criteriaBuilder.equal(joinedExercises.get(Exercise_.COURSE).get(Course_.ID), courseId);
                return criteriaBuilder.or(coursePosts, coursePostsWithLectureContext, coursePostsWithExerciseContext);
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
                root.join(Post_.EXERCISE, JoinType.INNER);
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
        return ((root, query, criteriaBuilder) -> {
            if (unresolved == null || !unresolved) {
                return null;
            }
            else {
                root.join(Post_.ANSWERS, JoinType.LEFT);

                Predicate postsWithoutCourseWideContext = criteriaBuilder.isNull(root.get(Post_.COURSE_WIDE_CONTEXT));
                Predicate notAnnouncementPosts = criteriaBuilder.notEqual(root.get(Post_.COURSE_WIDE_CONTEXT), CourseWideContext.ANNOUNCEMENT);

                // Post should not have any answer that resolves
                Subquery<Long> subQuery = query.subquery(Long.class);
                Root<AnswerPost> subRoot = subQuery.from(AnswerPost.class);
                Predicate subPredicate = criteriaBuilder.equal(subRoot.get(AnswerPost_.RESOLVES_POST), Boolean.TRUE);
                Predicate postBinder = criteriaBuilder.equal(root.get(Post_.ID), subRoot.get(AnswerPost_.POST).get(Post_.ID));
                subQuery.select(subRoot.get(AnswerPost_.ID)).where(criteriaBuilder.and(postBinder, subPredicate));

                Predicate notAnnouncement = criteriaBuilder.or(postsWithoutCourseWideContext, notAnnouncementPosts);
                Predicate notResolves = criteriaBuilder.exists(subQuery).not();

                return criteriaBuilder.and(notAnnouncement, notResolves);
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
