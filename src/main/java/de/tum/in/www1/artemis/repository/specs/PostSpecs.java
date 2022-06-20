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
                return criteriaBuilder.conjunction();
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
                return criteriaBuilder.equal(root.get(Post_.EXERCISE).get(Exercise_.ID), exerciseId);
            }
        });
    }

    public static Specification<Post> getCourseWideContextSpecification(CourseWideContext courseWideContext) {
        return ((root, query, criteriaBuilder) -> {
            if (courseWideContext == null) {
                return criteriaBuilder.conjunction();
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.COURSE_WIDE_CONTEXT), courseWideContext);
            }
        });
    }

    public static Specification<Post> getOwnSpecification(Boolean filterToOwn, Long userId) {
        return ((root, query, criteriaBuilder) -> {
            if (filterToOwn == null || !filterToOwn) {
                return criteriaBuilder.conjunction();
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.AUTHOR), userId);
            }
        });
    }

    public static Specification<Post> getAnsweredOrReactedSpecification(Boolean answeredOrReacted, Long userId) {
        return ((root, query, criteriaBuilder) -> {
            if (answeredOrReacted == null || !answeredOrReacted) {
                return criteriaBuilder.conjunction();
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
                return criteriaBuilder.conjunction();
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

    /**
     * filters posts on a search string in a match-all-manner
     * post is only kept if the search string (which is not a #id pattern) is included in either the post title, content or tag (all strings lowercased)
     *
     * @param searchText text to be searched within posts
     * @return boolean predicate if the post is kept (true) or filtered out (false)
     */
    public static Specification<Post> getSearchTextSpecification(String searchText) {
        return ((root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            // search by text or #post
            else if (searchText.startsWith("#") && (searchText.substring(1) != null && !searchText.substring(1).isBlank())) {
                // if searchText starts with a # and is followed by a post id, filter for post with id
                return criteriaBuilder.equal(root.get(Post_.ID), Integer.parseInt(searchText.substring(1)));
            }
            else {
                // regular search on content, title, and tags
                Expression<String> searchTextLiteral = criteriaBuilder.literal("%" + searchText.toLowerCase() + "%");

                Predicate searchInPostTitle = criteriaBuilder.like(criteriaBuilder.lower(root.get(Post_.TITLE)), searchTextLiteral);
                Predicate searchInPostContent = criteriaBuilder.like(criteriaBuilder.lower(root.get(Post_.CONTENT)), searchTextLiteral);
                Predicate searchInPostTags = criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.concat(root.join(Post_.TAGS), " ")), searchTextLiteral);

                return criteriaBuilder.or(searchInPostTitle, searchInPostContent, searchInPostTags);
            }
        });
    }

    public static Specification<Post> getSortSpecification(PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {
        return ((root, query, criteriaBuilder) -> {

            List<Order> orderList = new ArrayList<>();

            Expression<Object> pinnedFirstThenAnnouncementsArchivedLast = criteriaBuilder.selectCase()
                    .when(criteriaBuilder.and(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.PINNED)),
                            criteriaBuilder.equal(root.get(Post_.COURSE_WIDE_CONTEXT), criteriaBuilder.literal(CourseWideContext.ANNOUNCEMENT))), 1)
                    .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.PINNED)), 2)
                    .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.NONE)), 3)
                    .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.ARCHIVED)), 4);
            orderList.add(criteriaBuilder.asc(pinnedFirstThenAnnouncementsArchivedLast));

            if (postSortCriterion != null) {
                Expression<?> sortCriterion = null;

                if (postSortCriterion == PostSortCriterion.CREATION_DATE) {
                    sortCriterion = root.get(Post_.CREATION_DATE);
                }
                else if (postSortCriterion == PostSortCriterion.ANSWER_COUNT) {
                    // Count number of Answers per Post
                    Subquery<Long> subQuery = query.subquery(Long.class);
                    Root<AnswerPost> subRoot = subQuery.from(AnswerPost.class);
                    Predicate postBinder = criteriaBuilder.equal(root.get(Post_.ID), subRoot.get(AnswerPost_.POST).get(Post_.ID));
                    subQuery.select(criteriaBuilder.count(subRoot.get(AnswerPost_.POST).get(Post_.ID))).where(criteriaBuilder.and(postBinder)).groupBy(root.get(Post_.ID));

                    sortCriterion = criteriaBuilder.selectCase().when(criteriaBuilder.exists(subQuery).not(), 0).otherwise(subQuery.getSelection());

                }
                else if (postSortCriterion == PostSortCriterion.VOTES) {
                    // TODO
                }
                orderList.add(sortingOrder == SortingOrder.ASCENDING ? criteriaBuilder.asc(sortCriterion) : criteriaBuilder.desc(sortCriterion));
            }

            query.orderBy(orderList);

            return criteriaBuilder.conjunction();
        });
    }
}
