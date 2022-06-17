package de.tum.in.www1.artemis.repository.specs;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.*;

public class PostSpecs {

    public static Specification<Post> getCourseSpecification(long courseId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(Post_.COURSE), courseId);
    }

    public static Specification<Post> getLectureSpecification(Long lectureId) {
        return ((root, query, criteriaBuilder) -> {
            if (lectureId == null) {
                return null;
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.LECTURE), lectureId);
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
                Join<Post, AnswerPost> joinedReactions = root.join(Post_.REACTIONS, JoinType.LEFT);

                Predicate predicate1 = criteriaBuilder.equal(root.get(Post_.ID), joinedAnswers.get(AnswerPost_.POST).get(Post_.ID));
                Predicate predicate2 = criteriaBuilder.equal(joinedAnswers.get(AnswerPost_.AUTHOR), userId);
                Predicate answered = criteriaBuilder.and(predicate1, predicate2);

                Predicate predicate3 = criteriaBuilder.equal(root.get(Post_.ID), joinedReactions.get(Reaction_.POST).get(Post_.ID));
                Predicate predicate4 = criteriaBuilder.equal(joinedReactions.get(Reaction_.USER), userId);
                Predicate reacted = criteriaBuilder.and(predicate3, predicate4);

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
                Join<Post, AnswerPost> joinedAnswers = root.join(Post_.ANSWERS, JoinType.LEFT);

                Predicate predicate1 = criteriaBuilder.isNull(root.get(Post_.COURSE_WIDE_CONTEXT));
                Predicate predicate2 = criteriaBuilder.notEqual(root.get(Post_.COURSE_WIDE_CONTEXT), CourseWideContext.ANNOUNCEMENT);

                Predicate predicate3 = criteriaBuilder.equal(criteriaBuilder.count(root.get(Post_.ANSWERS)), 0); // todo : error here
                Predicate predicate4 = criteriaBuilder.equal(root.get(Post_.ID), joinedAnswers.get(AnswerPost_.POST).get(Post_.ID));
                Predicate predicate5 = criteriaBuilder.equal(joinedAnswers.get(AnswerPost_.RESOLVES_POST), Boolean.TRUE);

                Predicate notAnnouncement = criteriaBuilder.or(predicate1, predicate2);
                Predicate notResolves = criteriaBuilder.or(predicate3, criteriaBuilder.and(predicate4, predicate5).not());

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
