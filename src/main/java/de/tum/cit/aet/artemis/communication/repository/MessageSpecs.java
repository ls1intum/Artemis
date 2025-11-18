package de.tum.cit.aet.artemis.communication.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost_;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostSortCriterion;
import de.tum.cit.aet.artemis.communication.domain.Post_;
import de.tum.cit.aet.artemis.communication.domain.Reaction_;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel_;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation_;
import de.tum.cit.aet.artemis.core.domain.Course_;
import de.tum.cit.aet.artemis.core.domain.User_;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;

public class MessageSpecs {

    /**
     * Specification which filters Messages and answer posts according to a search string and a list of authors
     * message and answer post are only kept if the search string (which is not a #id pattern) is included in the message content (all strings lowercased)
     * and the author of the message or answer post is in the list of authors
     *
     * @param searchText Text to be searched within messages
     * @param authorIds  ids of the authors
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getSearchTextAndAuthorSpecification(String searchText, long[] authorIds) {
        return (root, query, criteriaBuilder) -> {
            boolean hasText = searchText != null && !searchText.isBlank();
            boolean hasAuthors = authorIds != null && authorIds.length > 0;
            // no author ids and no search text means no filtering
            if (!hasText && !hasAuthors) {
                return null;
            }
            // if only a search text is given, use the search text specification
            if (hasText && !hasAuthors) {
                return getSearchTextSpecification(searchText).toPredicate(root, query, criteriaBuilder);
            }
            // if only author ids are given, use the author specification
            if (!hasText && hasAuthors) {
                return getAuthorSpecification(authorIds).toPredicate(root, query, criteriaBuilder);
            }

            List<Long> authorIdList = Arrays.stream(authorIds).boxed().toList();
            Expression<String> searchTextLiteral = criteriaBuilder.literal("%" + searchText.toLowerCase() + "%");
            Predicate baseTextPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get(Post_.CONTENT)), searchTextLiteral);
            Predicate baseAuthorPredicate = root.get(Post_.AUTHOR).get(User_.ID).in(authorIdList);
            Predicate baseCombined = criteriaBuilder.and(baseTextPredicate, baseAuthorPredicate);

            Join<Post, AnswerPost> answerJoin = root.join(Post_.ANSWERS, JoinType.LEFT);
            Predicate answerTextPredicate = criteriaBuilder.like(criteriaBuilder.lower(answerJoin.get(AnswerPost_.CONTENT)), searchTextLiteral);
            Predicate answerAuthorPredicate = answerJoin.get(AnswerPost_.AUTHOR).get(User_.ID).in(authorIdList);
            Predicate answerCombined = criteriaBuilder.and(answerTextPredicate, answerAuthorPredicate);

            return criteriaBuilder.or(baseCombined, answerCombined);
        };
    }

    /**
     * Specification which filters Messages and answer posts according to a search string in a match-all-manner
     * message and answer post are only kept if the search string (which is not a #id pattern) is included in the message content (all strings lowercased)
     *
     * @param searchText Text to be searched within messages
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getSearchTextSpecification(String searchText) {
        return ((root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.isBlank()) {
                return null;
            }
            // search by text or #message
            else if (searchText.startsWith("#") && StringUtils.isNumeric(searchText.substring(1))) {
                // if searchText starts with a # and is followed by a message id, filter for message with id
                return criteriaBuilder.equal(root.get(Post_.ID), Integer.parseInt(searchText.substring(1)));
            }
            else {
                // regular search on content
                Expression<String> searchTextLiteral = criteriaBuilder.literal("%" + searchText.toLowerCase() + "%");

                Predicate searchInMessageContent = criteriaBuilder.like(criteriaBuilder.lower(root.get(Post_.CONTENT)), searchTextLiteral);
                Join<Post, AnswerPost> answersJoin = root.join(Post_.ANSWERS, JoinType.LEFT);
                Predicate searchInAnswerContent = criteriaBuilder.like(criteriaBuilder.lower(answersJoin.get(AnswerPost_.CONTENT)), searchTextLiteral);

                return criteriaBuilder.or(searchInMessageContent, searchInAnswerContent);
            }
        });
    }

    /**
     * Specification to fetch messages belonging to a list of conversations
     *
     * @param conversationIds ids of the conversation messages belong to
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getConversationsSpecification(long[] conversationIds) {
        return ((root, query, criteriaBuilder) -> {
            if (conversationIds == null || conversationIds.length == 0) {
                return null;
            }
            else {
                return root.get(Post_.CONVERSATION).get(Conversation_.ID).in(Arrays.stream(conversationIds).boxed().toList());
            }
        });
    }

    /**
     * Creates a specification to fetch messages belonging to a course-wide channels in the course
     *
     * @param filterToCourseWide whether only the Posts in course-wide channels should be fetched or not
     * @param courseId           id of course the posts belong to
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getCourseWideChannelsSpecification(boolean filterToCourseWide, Long courseId) {
        return (root, query, criteriaBuilder) -> {
            if (!filterToCourseWide) {
                return null;
            }
            final var conversationJoin = root.join(Post_.conversation, JoinType.LEFT);
            final var isInCoursePredicate = criteriaBuilder.equal(conversationJoin.get(Channel_.COURSE).get(Course_.ID), courseId);
            final var isCourseWidePredicate = criteriaBuilder.isTrue(conversationJoin.get(Channel_.IS_COURSE_WIDE));
            // make sure we only fetch channels (which are sub types of conversations)
            // this avoids the creation of sub queries
            final var isChannelPredicate = criteriaBuilder.equal(conversationJoin.type(), criteriaBuilder.literal(Channel.class));
            return criteriaBuilder.and(isInCoursePredicate, isCourseWidePredicate, isChannelPredicate);
        };
    }

    /**
     * Specification to fetch Posts that were created by given authors for the calling user
     *
     * @param authorIds ids of the post authors
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getAuthorSpecification(long[] authorIds) {
        return ((root, query, criteriaBuilder) -> {
            if (authorIds == null || authorIds.length == 0) {
                return null;
            }
            else {
                List<Long> authorIdList = Arrays.stream(authorIds).boxed().toList();
                Join<Post, AnswerPost> answersJoin = root.join(Post_.ANSWERS, JoinType.LEFT);
                Predicate isAnswerPostAuthorPredicate = answersJoin.get(AnswerPost_.AUTHOR).get(User_.ID).in(authorIdList);
                Predicate isPostAuthorPredicate = root.get(Post_.AUTHOR).get(User_.ID).in(authorIdList);
                return criteriaBuilder.or(isAnswerPostAuthorPredicate, isPostAuthorPredicate);
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
    @NonNull
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

                Predicate answered = criteriaBuilder.equal(joinedAnswers.get(AnswerPost_.AUTHOR).get(User_.ID), userId);
                Predicate reacted = criteriaBuilder.equal(joinedReactions.get(Reaction_.USER).get(User_.ID), userId);

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
    @NonNull
    public static Specification<Post> getUnresolvedSpecification(boolean unresolved) {
        return ((root, query, criteriaBuilder) -> {
            if (!unresolved) {
                return null;
            }
            else {
                // Post should not have any answer that resolves
                Predicate noResolvingAnswer = criteriaBuilder.isFalse(root.get(Post_.resolved));
                // Posts in announcement channels can not be answered, therefore they can not be unresolved
                Predicate notAnnouncementChannel = criteriaBuilder.isFalse(root.get(Post_.conversation).get(Channel_.IS_ANNOUNCEMENT_CHANNEL));

                return criteriaBuilder.and(noResolvingAnswer, notAnnouncementChannel);
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
    @NonNull
    public static Specification<Post> getSortSpecification(boolean pagingEnabled, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {
        return ((root, query, criteriaBuilder) -> {
            if (pagingEnabled && postSortCriterion != null && sortingOrder != null && query != null) {

                List<Order> orderList = new ArrayList<>();

                Expression<?> sortCriterion = null;

                if (postSortCriterion == PostSortCriterion.CREATION_DATE) {
                    // sort by creation date
                    sortCriterion = root.get(Post_.CREATION_DATE);
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
     *         <a href="https://github.com/h2database/h2database/issues/408">...</a>
     */
    @NonNull
    public static Specification<Post> distinct() {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.groupBy(root.get(Post_.ID));
            }
            return null;
        };
    }

    /**
     * Specification to fetch only pinned Posts (DisplayPriority = PINNED)
     *
     * @param pinnedOnly whether only pinned posts should be fetched
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getPinnedSpecification(boolean pinnedOnly) {
        return (root, query, criteriaBuilder) -> {
            if (!pinnedOnly) {
                return null;
            }
            return criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), DisplayPriority.PINNED.name());
        };
    }
}
