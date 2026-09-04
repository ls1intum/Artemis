package de.tum.cit.aet.artemis.account.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Removes the communication content of a user that is being deleted permanently.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 *
 * <p>
 * Authorship is removed with the content, while the marks somebody left on other people's content - having verified a
 * post, having opened a conversation - are only detached, because that content belongs to whoever wrote it.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CommunicationDataCleanupRepository extends ArtemisJpaRepository<Post, Long> {

    @Query("""
            SELECT post.author.id AS userId, COUNT(post) AS count
            FROM Post post
            WHERE post.author.id IN :userIds
            GROUP BY post.author.id
            """)
    List<UserReferenceCount> countPosts(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Post post
            WHERE post.author.id = :userId
            """)
    int deletePosts(@Param("userId") long userId);

    @Query("""
            SELECT answerPost.author.id AS userId, COUNT(answerPost) AS count
            FROM AnswerPost answerPost
            WHERE answerPost.author.id IN :userIds
            GROUP BY answerPost.author.id
            """)
    List<UserReferenceCount> countAnswerPosts(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM AnswerPost answerPost
            WHERE answerPost.author.id = :userId
            """)
    int deleteAnswerPosts(@Param("userId") long userId);

    @Query("""
            SELECT answerPost.verifiedBy.id AS userId, COUNT(answerPost) AS count
            FROM AnswerPost answerPost
            WHERE answerPost.verifiedBy.id IN :userIds
            GROUP BY answerPost.verifiedBy.id
            """)
    List<UserReferenceCount> countVerifiedAnswerPosts(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE AnswerPost answerPost
            SET answerPost.verifiedBy = NULL
            WHERE answerPost.verifiedBy.id = :userId
            """)
    int detachVerifiedAnswerPosts(@Param("userId") long userId);

    @Query("""
            SELECT reaction.user.id AS userId, COUNT(reaction) AS count
            FROM Reaction reaction
            WHERE reaction.user.id IN :userIds
            GROUP BY reaction.user.id
            """)
    List<UserReferenceCount> countReactions(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Reaction reaction
            WHERE reaction.user.id = :userId
            """)
    int deleteReactions(@Param("userId") long userId);

    @Query("""
            SELECT savedPost.user.id AS userId, COUNT(savedPost) AS count
            FROM SavedPost savedPost
            WHERE savedPost.user.id IN :userIds
            GROUP BY savedPost.user.id
            """)
    List<UserReferenceCount> countSavedPosts(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM SavedPost savedPost
            WHERE savedPost.user.id = :userId
            """)
    int deleteSavedPosts(@Param("userId") long userId);

    @Query("""
            SELECT conversation.creator.id AS userId, COUNT(conversation) AS count
            FROM Conversation conversation
            WHERE conversation.creator.id IN :userIds
            GROUP BY conversation.creator.id
            """)
    List<UserReferenceCount> countCreatedConversations(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE Conversation conversation
            SET conversation.creator = NULL
            WHERE conversation.creator.id = :userId
            """)
    int detachCreatedConversations(@Param("userId") long userId);

    @Query("""
            SELECT participant.user.id AS userId, COUNT(participant) AS count
            FROM ConversationParticipant participant
            WHERE participant.user.id IN :userIds
            GROUP BY participant.user.id
            """)
    List<UserReferenceCount> countConversationMemberships(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ConversationParticipant participant
            WHERE participant.user.id = :userId
            """)
    int deleteConversationMemberships(@Param("userId") long userId);

    /**
     * The threads that go with the account: the ones it started. Their ids are read before the rows are deleted,
     * because the search index holds a copy of what they said and deleting the row does not reach it.
     *
     * @param userId the account being deleted
     * @return the ids of those threads
     */
    @Query("""
            SELECT post.id
            FROM Post post
            WHERE post.author.id = :userId
            """)
    List<Long> findPostIdsAuthoredBy(@Param("userId") long userId);

    /**
     * The answers that go with the account: its own, and the ones other people wrote below a thread it started.
     *
     * @param userId the account being deleted
     * @return the ids of those answers
     */
    @Query("""
            SELECT answerPost.id
            FROM AnswerPost answerPost
            WHERE answerPost.author.id = :userId
                OR answerPost.post.id IN (SELECT post.id FROM Post post WHERE post.author.id = :userId)
            """)
    List<Long> findAnswerPostIdsAuthoredBy(@Param("userId") long userId);

    /**
     * The threads held on the given plagiarism cases, which go with the cases.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return the ids of those threads
     */
    @Query("""
            SELECT post.id
            FROM Post post
            WHERE post.plagiarismCase.id IN :plagiarismCaseIds
            """)
    List<Long> findPlagiarismCasePostIds(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);

    /**
     * The answers below the threads held on the given plagiarism cases.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return the ids of those answers
     */
    @Query("""
            SELECT answerPost.id
            FROM AnswerPost answerPost
            WHERE answerPost.post.id IN (SELECT post.id FROM Post post WHERE post.plagiarismCase.id IN :plagiarismCaseIds)
            """)
    List<Long> findPlagiarismCaseAnswerPostIds(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);

    /**
     * Deletes the reactions other people left on the answers below a thread the account started, and on its own
     * answers. Reactions are the leaves of the tree and have to go before the answers they hang on.
     *
     * @param userId the account being deleted
     * @return how many reactions were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Reaction reaction
            WHERE reaction.answerPost.id IN (
                SELECT answerPost.id
                FROM AnswerPost answerPost
                WHERE answerPost.author.id = :userId
                    OR answerPost.post.id IN (SELECT post.id FROM Post post WHERE post.author.id = :userId)
            )
            """)
    int deleteReactionsOnAnswersAuthoredBy(@Param("userId") long userId);

    /**
     * Deletes the answers below a thread the account started, whoever wrote them, together with its own answers
     * elsewhere. A thread cannot be removed while answers still hang below it.
     *
     * @param userId the account being deleted
     * @return how many answers were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM AnswerPost answerPost
            WHERE answerPost.author.id = :userId
                OR answerPost.post.id IN (SELECT post.id FROM Post post WHERE post.author.id = :userId)
            """)
    int deleteAnswersAuthoredBy(@Param("userId") long userId);

    /**
     * Deletes the reactions other people left on the threads the account started.
     *
     * @param userId the account being deleted
     * @return how many reactions were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Reaction reaction
            WHERE reaction.post.id IN (SELECT post.id FROM Post post WHERE post.author.id = :userId)
            """)
    int deleteReactionsOnPostsAuthoredBy(@Param("userId") long userId);

    /**
     * Deletes the reactions on the answers of the discussion threads held on the given plagiarism cases.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return how many reactions were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Reaction reaction
            WHERE reaction.answerPost.id IN (
                SELECT answerPost.id
                FROM AnswerPost answerPost
                WHERE answerPost.post.id IN (SELECT post.id FROM Post post WHERE post.plagiarismCase.id IN :plagiarismCaseIds)
            )
            """)
    int deleteReactionsOnPlagiarismCaseAnswers(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);

    /**
     * Deletes the answers of the discussion threads held on the given plagiarism cases.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return how many answers were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM AnswerPost answerPost
            WHERE answerPost.post.id IN (SELECT post.id FROM Post post WHERE post.plagiarismCase.id IN :plagiarismCaseIds)
            """)
    int deletePlagiarismCaseAnswers(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);

    /**
     * Deletes the reactions on the discussion threads held on the given plagiarism cases.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return how many reactions were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Reaction reaction
            WHERE reaction.post.id IN (SELECT post.id FROM Post post WHERE post.plagiarismCase.id IN :plagiarismCaseIds)
            """)
    int deleteReactionsOnPlagiarismCasePosts(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);

    /**
     * Deletes the discussion threads held on the given plagiarism cases, so that the cases themselves can be removed.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return how many threads were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Post post
            WHERE post.plagiarismCase.id IN :plagiarismCaseIds
            """)
    int deletePlagiarismCasePosts(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);
}
