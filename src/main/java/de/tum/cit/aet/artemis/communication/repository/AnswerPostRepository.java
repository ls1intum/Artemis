package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.dto.ResolvingAnswerEndorserDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the AnswerPost entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AnswerPostRepository extends ArtemisJpaRepository<AnswerPost, Long> {

    /**
     * Finds all {@link AnswerPost} entities authored by the given user ID.
     *
     * @param authorId the ID of the author
     * @return a set of answer posts created by the author
     */
    Set<AnswerPost> findAnswerPostsByAuthorId(long authorId);

    /**
     * Deletes all answer posts associated with the given course ID via conversations.
     * This should be called before deleting posts to handle the cascade properly.
     *
     * @param courseId ID of the course
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("DELETE FROM AnswerPost a WHERE a.post.conversation.course.id = :courseId")
    void deleteAllByCourseId(@Param("courseId") long courseId);

    /**
     * Retrieves an {@link AnswerPost} by ID that is **not** part of a conversation.
     *
     * @param answerPostId the ID of the answer post
     * @return the answer post if found and **not** linked to a conversation
     */
    @NonNull
    default AnswerPost findAnswerPostByIdElseThrow(long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() == null), answerPostId);
    }

    /**
     * Retrieves an {@link AnswerPost} by ID that is part of a conversation (i.e., an answer message).
     *
     * @param answerPostId the ID of the answer message
     * @return the answer message if found and linked to a conversation
     */
    @NonNull
    default AnswerPost findAnswerMessageByIdElseThrow(long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() != null), answerPostId);
    }

    /**
     * Retrieves an {@link AnswerPost} by ID that is part of a conversation while acquiring a pessimistic write lock.
     *
     * @param answerPostId the ID of the answer message
     * @return the answer message if found and linked to a conversation
     */
    @NonNull
    default AnswerPost findAnswerMessageByIdWithPessimisticWriteLockElseThrow(long answerPostId) {
        return getValueElseThrow(findAnswerMessageByIdWithPessimisticWriteLock(answerPostId), answerPostId);
    }

    // The pessimistic write lock intentionally covers only this AnswerPost row (preventing double-verification of this one answer).
    // Loading post triggers a separate eager fetch of Post.answers (the sibling answers), which are NOT locked. This is fine:
    // siblings are only read (e.g. for broadcasting), never written under this lock, so no correctness issue arises.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT answerPost
            FROM AnswerPost answerPost
                JOIN FETCH answerPost.author
                JOIN FETCH answerPost.post post
                LEFT JOIN FETCH post.author
                JOIN FETCH post.conversation
            WHERE answerPost.id = :answerPostId
            """)
    Optional<AnswerPost> findAnswerMessageByIdWithPessimisticWriteLock(@Param("answerPostId") long answerPostId);

    /**
     * Retrieves an {@link AnswerPost} by ID, regardless of whether it is linked to a conversation.
     *
     * @param answerPostId the ID of the answer post or message
     * @return the answer post or message if found
     */
    @NonNull
    default AnswerPost findAnswerPostOrMessageByIdElseThrow(long answerPostId) {
        return getValueElseThrow(findById(answerPostId), answerPostId);
    }

    /**
     * Counts the number of distinct {@link AnswerPost} entities associated with a course via conversations.
     *
     * @param courseId the ID of the course
     * @return the number of answer posts in conversations linked to the course
     */
    @Query("""
            SELECT COUNT(DISTINCT a.id)
            FROM AnswerPost a
            WHERE a.post.conversation.course.id = :courseId
            """)
    long countAnswerPostsByCourseId(@Param("courseId") long courseId);

    /**
     * Finds all answer posts related to a specific course via conversations.
     *
     * @param courseId ID of the course
     * @return list of answer posts associated with the course
     */
    @Query("""
            SELECT a
            FROM AnswerPost a
            LEFT JOIN FETCH a.author
            LEFT JOIN FETCH a.post
            WHERE a.post.conversation.course.id = :courseId
            """)
    List<AnswerPost> findAllByCourseId(@Param("courseId") long courseId);

    /**
     * Counts the number of distinct {@link AnswerPost} entities in a specific conversation.
     *
     * @param conversationId the ID of the conversation
     * @return the number of answer posts in the conversation
     */
    @Query("""
            SELECT COUNT(DISTINCT a.id)
            FROM AnswerPost a
            WHERE a.post.conversation.id = :conversationId
            """)
    long countByConversationId(@Param("conversationId") long conversationId);

    /**
     * Retrieves all {@link AnswerPost} entities with IDs contained in the given list.
     *
     * @param idList a collection of answer post IDs
     * @return a list of matching answer posts
     */
    List<AnswerPost> findByIdIn(Collection<Long> idList);

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

    /**
     * Whether the answer post carries a human verifier, i.e. whether a tutor approved it in the verification dashboard.
     * <p>
     * An Iris answer published automatically on a high confidence score is also stored as {@code verified}, but with no
     * {@code verifiedBy} — see {@code AutonomousTutorService#createAndSaveAnswerPost}, which sets only {@code verifiedAt}
     * because there is no human reviewer. {@code verifiedBy} is therefore what tells the two apart.
     * <p>
     * Queried as a projection rather than read off the entity on purpose: {@code AnswerPost#verifiedBy} is a lazy
     * {@code @ManyToOne} that the thread-loading query does not fetch, and adding it there would put an extra user join
     * on a hot read path.
     *
     * @param answerPostId the ID of the {@link AnswerPost} to check
     * @return {@code true} if a user is recorded as the verifier, {@code false} if none is or the answer post does not exist
     */
    @Query("""
            SELECT CASE WHEN COUNT(answerPost) > 0 THEN TRUE ELSE FALSE END
            FROM AnswerPost answerPost
            WHERE answerPost.id = :answerPostId
                AND answerPost.verifiedBy IS NOT NULL
            """)
    boolean hasHumanVerifier(@Param("answerPostId") long answerPostId);

    /**
     * Returns the login of the user recorded as the verifier of the given {@link AnswerPost}.
     * <p>
     * Queried rather than navigated from the entity because {@code verifiedBy} is lazy and is not part of
     * the eager thread fetch, so reading it off a detached answer would fail outside a transaction.
     *
     * @param answerPostId the ID of the {@link AnswerPost} to look up
     * @return the verifier's login, or empty if none is recorded or the answer post does not exist
     */
    @Query("""
            SELECT answerPost.verifiedBy.login
            FROM AnswerPost answerPost
            WHERE answerPost.id = :answerPostId
                AND answerPost.verifiedBy IS NOT NULL
            """)
    Optional<String> findVerifierLoginById(@Param("answerPostId") long answerPostId);

    /**
     * Returns, for every resolving answer of a thread that records an endorser, who marked it resolving.
     * <p>
     * Course Memory derives an entry's trust tier from this endorsement — a tutor marking an answer resolving
     * vouches for it, a student doing so does not — and has to be able to re-derive it from whichever answer
     * still resolves the thread after another one was un-marked or deleted. Answers resolved before the
     * endorser was recorded are absent from the result and are treated as community-resolved.
     * <p>
     * Queried as a projection for the same reason as {@link #findVerifierLoginById}: {@code resolvedBy} is
     * lazy and not part of the eager thread fetch. One query for the whole thread rather than one per answer.
     *
     * @param postId the id of the thread's root post
     * @return one entry per resolving answer that carries an endorser
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.communication.dto.ResolvingAnswerEndorserDTO(answerPost.id, answerPost.resolvedBy.login)
            FROM AnswerPost answerPost
            WHERE answerPost.post.id = :postId
                AND answerPost.resolvesPost = TRUE
                AND answerPost.resolvedBy IS NOT NULL
            """)
    List<ResolvingAnswerEndorserDTO> findResolvingAnswerEndorsersByPostId(@Param("postId") long postId);
}
