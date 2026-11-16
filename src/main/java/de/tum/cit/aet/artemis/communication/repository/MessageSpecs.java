package de.tum.cit.aet.artemis.communication.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.domain.User_;
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
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.course.domain.Course_;

public class MessageSpecs {

    /**
     * Specification which filters Messages and answer posts according to a search string and a list of authors
     * message and answer post are only kept if the search string (which is not a #id pattern) is included in the content or author name (all strings lowercased)
     * and the author of that same message or answer post is in the list of authors
     *
     * @param searchText                   Text to be searched within messages
     * @param authorIds                    ids of the authors
     * @param includeUnverifiedIrisAnswers whether the requester may read pending Iris replies
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getSearchTextAndAuthorSpecification(String searchText, long[] authorIds, boolean includeUnverifiedIrisAnswers) {
        return (root, query, criteriaBuilder) -> {
            boolean hasText = searchText != null && !searchText.isBlank();
            boolean hasAuthors = authorIds != null && authorIds.length > 0;
            // no author ids and no search text means no filtering
            if (!hasText && !hasAuthors) {
                return criteriaBuilder.conjunction();
            }
            // if only a search text is given, use the search text specification
            if (hasText && !hasAuthors) {
                return getSearchTextSpecification(searchText, includeUnverifiedIrisAnswers).toPredicate(root, query, criteriaBuilder);
            }
            // if only author ids are given, use the author specification
            if (!hasText && hasAuthors) {
                return getAuthorSpecification(authorIds, includeUnverifiedIrisAnswers).toPredicate(root, query, criteriaBuilder);
            }

            List<Long> authorIdList = Arrays.stream(authorIds).boxed().toList();
            Expression<String> searchTextLiteral = criteriaBuilder.literal("%" + searchText.toLowerCase(Locale.ROOT) + "%");
            Join<Post, User> authorJoin = root.join(Post_.AUTHOR, JoinType.LEFT);
            Predicate baseTextPredicate = criteriaBuilder.or(criteriaBuilder.like(criteriaBuilder.lower(root.get(Post_.CONTENT)), searchTextLiteral),
                    authorNameMatches(criteriaBuilder, authorJoin, searchTextLiteral));
            Predicate baseAuthorPredicate = authorJoin.get(User_.ID).in(authorIdList);
            Predicate baseCombined = criteriaBuilder.and(baseTextPredicate, baseAuthorPredicate);

            // Keep answers inside EXISTS so multiple matching replies do not multiply paged post rows.
            Subquery<Long> answerSubquery = query.subquery(Long.class);
            Root<AnswerPost> answerRoot = answerSubquery.from(AnswerPost.class);
            Join<AnswerPost, User> answerAuthorJoin = answerRoot.join(AnswerPost_.AUTHOR, JoinType.LEFT);
            answerSubquery.select(answerRoot.get(AnswerPost_.ID));
            Predicate answerNamePredicate = authorNameMatches(criteriaBuilder, answerAuthorJoin, searchTextLiteral);
            Predicate answerTextPredicate = criteriaBuilder.or(criteriaBuilder.like(criteriaBuilder.lower(answerRoot.get(AnswerPost_.CONTENT)), searchTextLiteral),
                    answerNamePredicate);
            answerSubquery.where(criteriaBuilder.equal(answerRoot.get(AnswerPost_.POST), root), answerTextPredicate, answerAuthorJoin.get(User_.ID).in(authorIdList),
                    answerIsVisible(criteriaBuilder, answerRoot, answerAuthorJoin, includeUnverifiedIrisAnswers));
            Predicate answerCombined = criteriaBuilder.exists(answerSubquery);

            return criteriaBuilder.or(baseCombined, answerCombined);
        };
    }

    /**
     * Specification which filters Messages and answer posts according to a search string in a match-all-manner
     * message and answer post are only kept if the search string (which is not a #id pattern) is included in the message content or in the name of the author
     * of the message or of one of its answers (all strings lowercased). The name is matched as first name, last name or "first last".
     *
     * @param searchText                   Text to be searched within messages
     * @param includeUnverifiedIrisAnswers whether the requester may read pending Iris replies
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getSearchTextSpecification(String searchText, boolean includeUnverifiedIrisAnswers) {
        return ((root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            // search by text or #message
            else if (searchText.startsWith("#") && StringUtils.isNumeric(searchText.substring(1))) {
                // if searchText starts with a # and is followed by a message id, filter for message with id
                return criteriaBuilder.equal(root.get(Post_.ID), Integer.parseInt(searchText.substring(1)));
            }
            else {
                // regular search on content
                Expression<String> searchTextLiteral = criteriaBuilder.literal("%" + searchText.toLowerCase(Locale.ROOT) + "%");

                Predicate searchInMessageContent = criteriaBuilder.like(criteriaBuilder.lower(root.get(Post_.CONTENT)), searchTextLiteral);
                Join<Post, User> authorJoin = root.join(Post_.AUTHOR, JoinType.LEFT);
                Predicate searchInAuthorName = authorNameMatches(criteriaBuilder, authorJoin, searchTextLiteral);

                // EXISTS instead of a join: a join returns one row per answer, which would repeat a post and distort the paging and the total count.
                Subquery<Long> answerSubquery = query.subquery(Long.class);
                Root<AnswerPost> answerRoot = answerSubquery.from(AnswerPost.class);
                Join<AnswerPost, User> answerAuthorJoin = answerRoot.join(AnswerPost_.AUTHOR, JoinType.LEFT);
                answerSubquery.select(answerRoot.get(AnswerPost_.ID));
                Predicate searchInAnswerContent = criteriaBuilder.like(criteriaBuilder.lower(answerRoot.get(AnswerPost_.CONTENT)), searchTextLiteral);
                Predicate searchInAnswerAuthorName = authorNameMatches(criteriaBuilder, answerAuthorJoin, searchTextLiteral);
                answerSubquery.where(criteriaBuilder.equal(answerRoot.get(AnswerPost_.POST), root), criteriaBuilder.or(searchInAnswerContent, searchInAnswerAuthorName),
                        answerIsVisible(criteriaBuilder, answerRoot, answerAuthorJoin, includeUnverifiedIrisAnswers));

                return criteriaBuilder.or(searchInMessageContent, searchInAuthorName, criteriaBuilder.exists(answerSubquery));
            }
        });
    }

    /**
     * Keeps answers hidden from students from influencing search results and counts.
     *
     * @param criteriaBuilder              the builder of the query
     * @param answer                       the answer being matched
     * @param author                       the answer's author
     * @param includeUnverifiedIrisAnswers whether the requester may read pending Iris replies
     * @return the predicate matching answers visible to the requester
     */
    private static Predicate answerIsVisible(CriteriaBuilder criteriaBuilder, Root<AnswerPost> answer, Join<AnswerPost, User> author, boolean includeUnverifiedIrisAnswers) {
        if (includeUnverifiedIrisAnswers) {
            return criteriaBuilder.conjunction();
        }
        return criteriaBuilder.or(criteriaBuilder.notEqual(author.get(User_.LOGIN), User.IRIS_BOT_LOGIN), criteriaBuilder.isTrue(answer.get(AnswerPost_.VERIFIED)));
    }

    /**
     * Builds the predicate that checks whether the lowercased name of the author, written as "first last", contains the lowercased search literal. This also covers a
     * search for the first name or the last name alone. The login is only searched when both names are blank and it is displayed as the fallback name.
     * The email address is never searched, so a search cannot reveal more than the name displayed next to a message.
     *
     * @param criteriaBuilder   the builder of the query
     * @param author            the join to the author
     * @param searchTextLiteral the lowercased search text wrapped in wildcards
     * @return the predicate matching the author's name
     */
    private static Predicate authorNameMatches(CriteriaBuilder criteriaBuilder, Join<?, User> author, Expression<String> searchTextLiteral) {
        Expression<String> firstName = criteriaBuilder.coalesce(author.get(User_.FIRST_NAME), "");
        Expression<String> lastName = criteriaBuilder.coalesce(author.get(User_.LAST_NAME), "");
        Expression<String> fullName = criteriaBuilder.concat(criteriaBuilder.concat(firstName, " "), lastName);
        Predicate hasNoName = criteriaBuilder.and(criteriaBuilder.equal(criteriaBuilder.trim(firstName), ""), criteriaBuilder.equal(criteriaBuilder.trim(lastName), ""));
        Predicate displayedLoginMatches = criteriaBuilder.and(hasNoName, criteriaBuilder.like(criteriaBuilder.lower(author.get(User_.LOGIN)), searchTextLiteral));
        return criteriaBuilder.or(criteriaBuilder.like(criteriaBuilder.lower(fullName), searchTextLiteral), displayedLoginMatches);
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
                return criteriaBuilder.conjunction();
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
                return criteriaBuilder.conjunction();
            }
            final var conversationJoin = root.join(Post_.conversation, JoinType.LEFT);
            // Use treat() to downcast the Conversation join to Channel for accessing subclass attributes (required by Hibernate 7)
            final var channelJoin = criteriaBuilder.treat(conversationJoin, Channel.class);
            final var isInCoursePredicate = criteriaBuilder.equal(channelJoin.get(Channel_.COURSE).get(Course_.ID), courseId);
            final var isCourseWidePredicate = criteriaBuilder.isTrue(channelJoin.get(Channel_.IS_COURSE_WIDE));
            // make sure we only fetch channels (which are sub types of conversations)
            // this avoids the creation of sub queries
            final var isChannelPredicate = criteriaBuilder.equal(conversationJoin.type(), criteriaBuilder.literal(Channel.class));
            return criteriaBuilder.and(isInCoursePredicate, isCourseWidePredicate, isChannelPredicate);
        };
    }

    /**
     * Specification to fetch Posts that were created by given authors for the calling user
     *
     * @param authorIds                    ids of the post authors
     * @param includeUnverifiedIrisAnswers whether the requester may read pending Iris replies
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getAuthorSpecification(long[] authorIds, boolean includeUnverifiedIrisAnswers) {
        return ((root, query, criteriaBuilder) -> {
            if (authorIds == null || authorIds.length == 0) {
                return criteriaBuilder.conjunction();
            }
            else {
                List<Long> authorIdList = Arrays.stream(authorIds).boxed().toList();
                Subquery<Long> answerSubquery = query.subquery(Long.class);
                Root<AnswerPost> answerRoot = answerSubquery.from(AnswerPost.class);
                Join<AnswerPost, User> answerAuthor = answerRoot.join(AnswerPost_.AUTHOR, JoinType.LEFT);
                answerSubquery.select(answerRoot.get(AnswerPost_.ID));
                answerSubquery.where(criteriaBuilder.equal(answerRoot.get(AnswerPost_.POST), root), answerAuthor.get(User_.ID).in(authorIdList),
                        answerIsVisible(criteriaBuilder, answerRoot, answerAuthor, includeUnverifiedIrisAnswers));
                Predicate isPostAuthorPredicate = root.get(Post_.AUTHOR).get(User_.ID).in(authorIdList);
                return criteriaBuilder.or(isPostAuthorPredicate, criteriaBuilder.exists(answerSubquery));
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
                return criteriaBuilder.conjunction();
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
                return criteriaBuilder.conjunction();
            }
            else {
                // Post should not have any answer that resolves
                Predicate noResolvingAnswer = criteriaBuilder.isFalse(root.get(Post_.resolved));
                // Posts in announcement channels can not be answered, therefore they can not be unresolved
                // Use treat() to downcast Conversation to Channel for accessing subclass attributes (required by Hibernate 7)
                var channelPath = criteriaBuilder.treat(root.get(Post_.conversation), Channel.class);
                Predicate notAnnouncementChannel = criteriaBuilder.isFalse(channelPath.get(Channel_.IS_ANNOUNCEMENT_CHANNEL));

                return criteriaBuilder.and(noResolvingAnswer, notAnnouncementChannel);
            }
        });
    }

    /**
     * Specification to fetch Posts that have at least one AnswerPost authored by the Iris bot user which is
     * not yet verified. Used by the tutor "messages to verify" dashboard.
     *
     * @param unverifiedIrisOnly whether the filter is active
     * @param irisBotLogin       login of the Iris bot user (used to identify bot-authored answers)
     * @return specification used to chain DB operations
     */
    @NonNull
    public static Specification<Post> getUnverifiedIrisAnswersSpecification(boolean unverifiedIrisOnly, String irisBotLogin) {
        return (root, query, criteriaBuilder) -> {
            if (!unverifiedIrisOnly) {
                return criteriaBuilder.conjunction();
            }
            // Use EXISTS subquery to avoid row-multiplication when a post has multiple answers
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<AnswerPost> answerRoot = subquery.from(AnswerPost.class);
            subquery.select(answerRoot.get(AnswerPost_.ID));
            subquery.where(criteriaBuilder.equal(answerRoot.get(AnswerPost_.POST), root), criteriaBuilder.isFalse(answerRoot.get(AnswerPost_.VERIFIED)),
                    criteriaBuilder.equal(answerRoot.get(AnswerPost_.AUTHOR).get(User_.LOGIN), irisBotLogin));
            return criteriaBuilder.exists(subquery);
        };
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
                // Break ties on the id, so paging is stable. Two messages posted in the same millisecond have no
                // defined order otherwise, and a page boundary falling between them lets the database return one of
                // them on two consecutive pages (the client then renders it twice) or on neither (it disappears).
                // Same reasoning as the answer ordering in PostResponseDTO.
                orderList.add(sortingOrder == SortingOrder.ASCENDING ? criteriaBuilder.asc(root.get(Post_.ID)) : criteriaBuilder.desc(root.get(Post_.ID)));
                query.orderBy(orderList);
            }

            return criteriaBuilder.conjunction();
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
            return criteriaBuilder.conjunction();
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
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get(Post_.DISPLAY_PRIORITY), DisplayPriority.PINNED.name());
        };
    }
}
