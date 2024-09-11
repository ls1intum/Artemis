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
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.domain.Course_;
import de.tum.cit.aet.artemis.domain.User_;
import de.tum.cit.aet.artemis.domain.enumeration.SortingOrder;
import de.tum.cit.aet.artemis.domain.metis.AnswerPost;
import de.tum.cit.aet.artemis.domain.metis.AnswerPost_;
import de.tum.cit.aet.artemis.domain.metis.Post;
import de.tum.cit.aet.artemis.domain.metis.PostSortCriterion;
import de.tum.cit.aet.artemis.domain.metis.Post_;
import de.tum.cit.aet.artemis.domain.metis.Reaction_;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel_;
import de.tum.cit.aet.artemis.domain.metis.conversation.Conversation_;

public class MessageSpecs {

    /**
     * Specification to fetch Posts belonging to a Conversation
     *
     * @param conversationId id of the conversation the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getConversationSpecification(Long conversationId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(Post_.CONVERSATION).get(Conversation_.ID), conversationId));
    }

    /**
     * Specification which filters Messages according to a search string in a match-all-manner
     * message is only kept if the search string (which is not a #id pattern) is included in the message content (all strings lowercased)
     *
     * @param searchText Text to be searched within messages
     * @return specification used to chain DB operations
     */
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

                return criteriaBuilder.and(searchInMessageContent);
            }
        });
    }

    /**
     * Specification to fetch messages belonging to a list of conversations
     *
     * @param conversationIds ids of the conversation messages belong to
     * @return specification used to chain DB operations
     */
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
     * @param courseId id of course the posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getCourseWideChannelsSpecification(Long courseId) {
        return (root, query, criteriaBuilder) -> {
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
                return criteriaBuilder.equal(root.get(Post_.AUTHOR).get(User_.ID), userId);
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
    public static Specification<Post> getUnresolvedSpecification(boolean unresolved) {
        return ((root, query, criteriaBuilder) -> {
            if (!unresolved) {
                return null;
            }
            else {
                // Post should not have any answer that resolves
                Predicate noResolvingAnswer = criteriaBuilder.isFalse(root.get(Post_.resolved));

                return criteriaBuilder.and(noResolvingAnswer);
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
     *         <a href="https://github.com/h2database/h2database/issues/408">...</a>
     */
    public static Specification<Post> distinct() {
        return (root, query, criteriaBuilder) -> {
            query.groupBy(root.get(Post_.ID));
            return null;
        };
    }
}
