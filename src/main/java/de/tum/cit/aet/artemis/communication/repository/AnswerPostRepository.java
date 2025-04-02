package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the AnswerPost entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AnswerPostRepository extends ArtemisJpaRepository<AnswerPost, Long> {

    List<AnswerPost> findAnswerPostsByAuthorId(long authorId);

    @NotNull
    default AnswerPost findAnswerPostByIdElseThrow(Long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() == null), answerPostId);
    }

    @NotNull
    default AnswerPost findAnswerMessageByIdElseThrow(Long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() != null), answerPostId);
    }

    @NotNull
    default AnswerPost findAnswerPostOrMessageByIdElseThrow(Long answerPostId) {
        return getValueElseThrow(findById(answerPostId), answerPostId);
    }

    long countAnswerPostsByPostIdIn(List<Long> postIds);

    List<AnswerPost> findByIdIn(List<Long> idList);

    /**
     * Counts how many of the given AnswerPost IDs are accessible by the given user.
     * <p>
     * An AnswerPost is considered accessible if:
     * <ul>
     * <li>It is associated with a {@link Post} that belongs to a {@link Channel} which is course-wide (i.e., {@code isCourseWide = true}), or</li>
     * <li>The user is a participant of the {@link Conversation} the post belongs to (e.g., {@link GroupChat} or {@link OneToOneChat}).</li>
     * </ul>
     * <p>
     * The JPQL query leverages the {@code TYPE()} function and {@code TREAT(... AS ...)} to safely access subclass fields
     * in a polymorphic {@code Conversation} hierarchy.
     *
     * @param answerPostIds the IDs of the {@link AnswerPost} entities to check
     * @param userId        the ID of the user whose access is being validated
     * @return the number of {@link AnswerPost} IDs from the input list that the user has access to
     */
    @Query("""
            SELECT COUNT(DISTINCT answerPost.id)
            FROM AnswerPost answerPost
                LEFT JOIN answerPost.post post
                LEFT JOIN post.conversation conv
                LEFT JOIN conv.conversationParticipants cp
            WHERE answerPost.id IN :answerPostIds
                AND (
                    (TYPE(conv) = Channel AND TREAT(conv AS Channel).isCourseWide = TRUE)
                    OR (cp.user.id = :userId)
                )
            """)
    long countAccessibleAnswerPosts(@Param("answerPostIds") Collection<Long> answerPostIds, @Param("userId") Long userId);

    /**
     * Ensures that the given user has access to all specified {@link AnswerPost} IDs.
     * <p>
     * Access is granted under the same conditions as described in {@link #countAccessibleAnswerPosts(Collection, Long)}.
     * If access to even a single {@link AnswerPost} is denied, an {@link AccessForbiddenException} is thrown.
     *
     * @param answerPostIds the set of {@link AnswerPost} IDs to validate
     * @param userId        the ID of the user to check access for
     * @throws AccessForbiddenException if the user does not have access to all requested {@link AnswerPost} entities
     */
    default void userHasAccessToAllAnswerPostsElseThrow(Collection<Long> answerPostIds, Long userId) {
        long accessibleCount = countAccessibleAnswerPosts(answerPostIds, userId);
        if (accessibleCount != answerPostIds.size()) {
            if (answerPostIds.size() == 1) {
                throw new AccessForbiddenException("AnswerPost", answerPostIds.iterator().next());
            }
            throw new AccessForbiddenException("AnswerPost", answerPostIds);
        }
    }
}
