package de.tum.in.www1.artemis.repository.specs;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.*;

public class PostSpecs {

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
     *         https://github.com/h2database/h2database/issues/408
     */
    public static Specification<Post> distinct() {
        return (root, query, criteriaBuilder) -> {
            query.groupBy(root.get(Post_.ID));
            return null;
        };
    }
}
