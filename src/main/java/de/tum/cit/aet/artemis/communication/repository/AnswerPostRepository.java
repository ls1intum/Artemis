package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
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

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE AnswerPost answerPost
            SET answerPost.verified = TRUE,
                answerPost.verifiedBy = :verifier,
                answerPost.verifiedAt = :verifiedAt
            WHERE answerPost.id = :answerPostId
                  AND answerPost.verified = FALSE
            """)
    int updateVerificationIfUnverified(@Param("answerPostId") long answerPostId, @Param("verifier") User verifier, @Param("verifiedAt") ZonedDateTime verifiedAt);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE AnswerPost answerPost
            SET answerPost.verified = TRUE,
                answerPost.verifiedBy = :verifier,
                answerPost.verifiedAt = :verifiedAt,
                answerPost.content = :content,
                answerPost.updatedDate = :verifiedAt
            WHERE answerPost.id = :answerPostId
                  AND answerPost.verified = FALSE
            """)
    int updateVerificationAndContentIfUnverified(@Param("answerPostId") long answerPostId, @Param("verifier") User verifier, @Param("verifiedAt") ZonedDateTime verifiedAt,
            @Param("content") String content);

    /**
     * Marks an unverified answer message as verified, optionally replacing its content in the same statement.
     * <p>
     * Two tutors may press approve at the same time; only one may win, because verifying makes the answer visible to
     * students and broadcasts it. The {@code verified = FALSE} guard sits inside the update, so the check and the write
     * are one statement and the loser is told the answer is already verified. Replacing the content in that same
     * statement is what keeps edit-and-approve safe: the edited content is committed no later than the visibility, so
     * there is no instant in which students can read the unedited reply.
     * <p>
     * Being a bulk update, it bypasses the persistence context; callers that need the verified state must re-read.
     *
     * @param answerPostId the id of the answer message to verify
     * @param verifier     the user verifying the answer message
     * @param verifiedAt   the timestamp to record as the verification time
     * @param content      the updated content, or null to keep the existing content
     * @return whether this call verified the answer message; false when it was already verified
     */
    default boolean verifyIfUnverified(long answerPostId, User verifier, ZonedDateTime verifiedAt, @Nullable String content) {
        int updated = content == null ? updateVerificationIfUnverified(answerPostId, verifier, verifiedAt)
                : updateVerificationAndContentIfUnverified(answerPostId, verifier, verifiedAt, content);
        return updated > 0;
    }

    /**
     * Retrieves an {@link AnswerPost} by id together with the context needed to authorise and broadcast a
     * verification: its author, its post with that post's author, the conversation the post belongs to, and the
     * verifier the response DTO reports. Used both before and after verifying, so the verifier is fetched with a left
     * join: it is still null on the read that precedes the verification.
     *
     * @param answerPostId the id of the answer message
     * @return the answer message if found and linked to a conversation
     */
    @Query("""
            SELECT answerPost
            FROM AnswerPost answerPost
                JOIN FETCH answerPost.author
                LEFT JOIN FETCH answerPost.verifiedBy
                JOIN FETCH answerPost.post post
                LEFT JOIN FETCH post.author
                JOIN FETCH post.conversation
            WHERE answerPost.id = :answerPostId
            """)
    Optional<AnswerPost> findAnswerMessageWithPostConversationAndVerifierById(@Param("answerPostId") long answerPostId);

    @NonNull
    default AnswerPost findAnswerMessageWithPostConversationAndVerifierByIdElseThrow(long answerPostId) {
        return getValueElseThrow(findAnswerMessageWithPostConversationAndVerifierById(answerPostId), answerPostId);
    }

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
}
