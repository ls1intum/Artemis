package de.tum.in.www1.artemis.repository.specs;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.*;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

import static de.tum.in.www1.artemis.config.Constants.VOTE_EMOJI_ID;

public class PostSpecs {

    /**
     * Creates a specification to fetch Posts belonging to a Course
     *
     * @param courseId   id of course the posts belong to
     * @param lectureId  id of lecture the posts belong to
     * @param exerciseId id of exercise the posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getCourseSpecification(Long courseId, Long lectureId, Long exerciseId) {
        return (root, query, criteriaBuilder) -> {
            if (lectureId != null || exerciseId != null) {
                // if lectureId or exerciseId is provided, lecture/exercise specifications will be activated to fetch lecture/exercise posts
                return null;
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

    /**
     * Specification to fetch Posts belonging to a Conversation
     *
     * @param conversationId id of the conversation the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getConversationSpecification(Long conversationId){
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(Post_.CONVERSATION).get(Conversation_.ID), conversationId);
    }

    /**
     * Specification to fetch Posts belonging to a Lecture
     *
     * @param lectureId id of the lecture the Posts belong to
     * @return specification used to chain DB operations
     */
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

    /**
     * Specification to fetch Posts belonging to an Exercise
     *
     * @param exerciseId id of the exercise the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getExerciseSpecification(Long exerciseId) {
        return ((root, query, criteriaBuilder) -> {
            if (exerciseId == null) {
                return null;
            }
            else {
                return criteriaBuilder.equal(root.get(Post_.EXERCISE).get(Exercise_.ID), exerciseId);
            }
        });
    }

    /**
     * Specification to fetch Posts by CourseWideContext
     *
     * @param courseWideContext context of the Posts within the current course
     * @return specification used to chain DB operations
     */
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
     * 1. criterion: displayPriority is PINNED && Announcement  -> 1. precedence ASC
     * 2. criterion: displayPriority is PINNED                  -> 2. precedence ASC
     * 3. criterion: order by CREATION_DATE, #VOTES, #ANSWERS   -> 3 precedence  ASC/DESC
     * 4. criterion: displayPriority is ARCHIVED                -> last precedence DESC
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
                    Subquery<Long> subQuery = query.subquery(Long.class);
                    Root<AnswerPost> subRoot = subQuery.from(AnswerPost.class);
                    Predicate postBinder = criteriaBuilder.equal(root.get(Post_.ID), subRoot.get(AnswerPost_.POST).get(Post_.ID));
                    subQuery.select(criteriaBuilder.count(subRoot.get(AnswerPost_.ID))).where(postBinder).groupBy(root.get(Post_.ID));

                    sortCriterion = criteriaBuilder.selectCase().when(criteriaBuilder.exists(subQuery).not(), 0).otherwise(subQuery.getSelection());
                }
                else if (postSortCriterion == PostSortCriterion.VOTES) {
                    // sort by votes via voteEmojiCount
                    Subquery<Long> subQuery = query.subquery(Long.class);
                    Root<Reaction> subRoot = subQuery.from(Reaction.class);
                    Predicate postBinder = criteriaBuilder.equal(root.get(Post_.ID), subRoot.get(Reaction_.POST).get(Post_.ID));
                    Predicate upVotes = criteriaBuilder.equal(subRoot.get(Reaction_.EMOJI_ID), VOTE_EMOJI_ID);
                    subQuery.select(criteriaBuilder.count(subRoot.get(Reaction_.ID))).where(criteriaBuilder.and(postBinder, upVotes)).groupBy(root.get(Post_.ID));

                    sortCriterion = criteriaBuilder.selectCase().when(criteriaBuilder.exists(subQuery).not(), 0).otherwise(subQuery.getSelection());
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
     * @return specification that adds the keyword distinct to the query
     */
    public static Specification<Post> distinct() {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            return null;
        };
    }
}
