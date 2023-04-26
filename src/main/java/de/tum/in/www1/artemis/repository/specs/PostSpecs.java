package de.tum.in.www1.artemis.repository.specs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.*;

public class PostSpecs {

    /**
     * Creates a specification to fetch Posts belonging to a Course
     *
     * @param courseId id of course the posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getCourseSpecification(Long courseId) {
        return (root, query, criteriaBuilder) -> {
            Join<Post, Lecture> joinedLectures = root.join(Post_.LECTURE, JoinType.LEFT);
            Join<Post, Exercise> joinedExercises = root.join(Post_.EXERCISE, JoinType.LEFT);

            Predicate coursePosts = criteriaBuilder.equal(root.get(Post_.COURSE), courseId);
            Predicate coursePostsWithLectureContext = criteriaBuilder.equal(joinedLectures.get(Lecture_.COURSE).get(Course_.ID), courseId);
            Predicate coursePostsWithExerciseContext = criteriaBuilder.equal(joinedExercises.get(Exercise_.COURSE).get(Course_.ID), courseId);
            return criteriaBuilder.or(coursePosts, coursePostsWithLectureContext, coursePostsWithExerciseContext);
        };
    }

    /**
     * Specification to fetch Posts belonging to a Lecture
     *
     * @param lectureId id of the lecture the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getLectureSpecification(Long[] lectureId) {
        return ((root, query, criteriaBuilder) -> {
            if (lectureId == null) {
                return null;
            }
            else {
                return root.get(Post_.LECTURE).get(Lecture_.ID).in(Arrays.asList(lectureId));
            }
        });
    }

    /**
     * Specification to fetch Posts belonging to an Exercise
     *
     * @param exerciseId id of the exercise the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getExerciseSpecification(Long[] exerciseId) {
        return ((root, query, criteriaBuilder) -> {
            if (exerciseId == null) {
                return null;
            }
            else {
                return root.get(Post_.EXERCISE).get(Exercise_.ID).in(Arrays.asList(exerciseId));
            }
        });
    }

    /**
     * Specification to fetch Posts by CourseWideContext
     *
     * @param courseWideContext context of the Posts within the current course
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getCourseWideContextSpecification(CourseWideContext[] courseWideContext) {
        return ((root, query, criteriaBuilder) -> {
            if (courseWideContext == null) {
                return null;
            }
            else {
                return root.get(Post_.COURSE_WIDE_CONTEXT).in(Arrays.asList(courseWideContext));
            }
        });
    }

    /**
     * Specification to fetch Posts of the calling user
     *
     * @param filterToOwn whether only calling users own Posts should be fetched or not
     * @param userId      id of the calling user
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getOwnSpecification(boolean filterToOwn, Long userId) {
        return ((root, query, criteriaBuilder) -> {
            if (!filterToOwn) {
                return null;
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.AUTHOR), userId);
            }
        });
    }

    /**
     * Specification to fetch Posts the calling user has Answered or Reacted to
     *
     * @param answeredOrReacted whether only the Posts calling user has Answered or Reacted to should be fetched or not
     * @param userId            id of the calling user
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getAnsweredOrReactedSpecification(boolean answeredOrReacted, Long userId) {
        return ((root, query, criteriaBuilder) -> {
            if (!answeredOrReacted) {
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

    /**
     * Specification to fetch Posts without any Resolving Answer
     *
     * @param unresolved whether only the Posts without resolving answers should be fetched or not
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getUnresolvedSpecification(boolean unresolved) {
        return ((root, query, criteriaBuilder) -> {
            if (!unresolved) {
                return null;
            }
            else {
                Predicate postsWithoutCourseWideContext = criteriaBuilder.isNull(root.get(Post_.COURSE_WIDE_CONTEXT));
                Predicate notAnnouncementPosts = criteriaBuilder.notEqual(root.get(Post_.COURSE_WIDE_CONTEXT), CourseWideContext.ANNOUNCEMENT);
                Predicate notAnnouncement = criteriaBuilder.or(postsWithoutCourseWideContext, notAnnouncementPosts);

                // Post should not have any answer that resolves
                Predicate noResolvingAnswer = criteriaBuilder.isFalse(root.get(Post_.resolved));

                return criteriaBuilder.and(notAnnouncement, noResolvingAnswer);
            }
        });
    }

    /**
     * Specification which filters Posts according to a search string in a match-all-manner
     * post is only kept if the search string (which is not a #id pattern) is included in either the post title, content or tag (all strings lowercased)
     *
     * @param searchText Text to be searched within posts
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getSearchTextSpecification(String searchText) {
        return ((root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.isBlank()) {
                return null;
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
                Predicate searchInPostTags = criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.concat(root.join(Post_.TAGS, JoinType.LEFT), " ")), searchTextLiteral);

                return criteriaBuilder.or(searchInPostTitle, searchInPostContent, searchInPostTags);
            }
        });
    }

    /**
     * Specification which sorts Posts (only for Course Discussion page)
     * 1. criterion: displayPriority is PINNED && Announcement -> 1. precedence ASC
     * 2. criterion: displayPriority is PINNED -> 2. precedence ASC
     * 3. criterion: order by CREATION_DATE, #VOTES, #ANSWERS -> 3 precedence ASC/DESC
     * 4. criterion: displayPriority is ARCHIVED -> last precedence DESC
     *
     * @param pagingEnabled     whether to sort the fetched Posts or not
     * @param postSortCriterion criterion to sort posts (CREATION_DATE, #VOTES, #ANSWERS)
     * @param sortingOrder      direction of sorting (ASC, DESC)
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getSortSpecification(boolean pagingEnabled, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {
        return ((root, query, criteriaBuilder) -> {
            if (pagingEnabled && postSortCriterion != null && sortingOrder != null) {

                List<Order> orderList = new ArrayList<>();

                // sort by priority
                Expression<Object> pinnedFirstThenAnnouncementsArchivedLast = criteriaBuilder.selectCase()
                        .when(criteriaBuilder.and(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.PINNED)),
                                criteriaBuilder.equal(root.get(Post_.COURSE_WIDE_CONTEXT), criteriaBuilder.literal(CourseWideContext.ANNOUNCEMENT))), 1)
                        .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.PINNED)), 2)
                        .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.NONE)), 3)
                        .when(criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), criteriaBuilder.literal(DisplayPriority.ARCHIVED)), 4);
                orderList.add(criteriaBuilder.asc(pinnedFirstThenAnnouncementsArchivedLast));

                Expression<?> sortCriterion = null;

                if (postSortCriterion == PostSortCriterion.CREATION_DATE) {
                    // sort by creation date
                    sortCriterion = root.get(Post_.CREATION_DATE);
                }
                else if (postSortCriterion == PostSortCriterion.ANSWER_COUNT) {
                    // sort by answer count
                    sortCriterion = root.get(Post_.ANSWER_COUNT);
                }
                else if (postSortCriterion == PostSortCriterion.VOTES) {
                    // sort by votes via voteEmojiCount
                    sortCriterion = root.get(Post_.VOTE_COUNT);
                }

                orderList.add(sortingOrder == SortingOrder.ASCENDING ? criteriaBuilder.asc(sortCriterion) : criteriaBuilder.desc(sortCriterion));
                query.orderBy(orderList);
            }

            return null;
        });
    }

    /**
     * Creates the specification to get distinct Posts
     *
     * @return specification that adds the keyword GROUP BY to the query since DISTINCT and ORDER BY keywords are
     *         incompatible with each other at server tests
     *         https://github.com/h2database/h2database/issues/408
     */
    public static Specification<Post> distinct() {
        return (root, query, criteriaBuilder) -> {
            query.groupBy(root.get(Post_.ID));
            return null;
        };
    }
}
