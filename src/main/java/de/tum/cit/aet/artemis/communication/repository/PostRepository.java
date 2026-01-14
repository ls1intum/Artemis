package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Post entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PostRepository extends ArtemisJpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /**
     * Finds all posts authored by the given user ID.
     *
     * @param authorId ID of the author
     * @return list of posts created by the author
     */
    List<Post> findPostsByAuthorId(long authorId);

    /**
     * Deletes all posts associated with the given conversation ID.
     *
     * @param conversationId ID of the conversation
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteAllByConversationId(Long conversationId);

    /**
     * Deletes all posts associated with the given course ID via conversations.
     * This keeps the conversation/channel structure but removes all posts.
     *
     * @param courseId ID of the course
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("DELETE FROM Post p WHERE p.conversation.course.id = :courseId")
    void deleteAllByCourseId(@Param("courseId") long courseId);

    /**
     * Finds all posts related to a given plagiarism case.
     *
     * @param plagiarismCaseId ID of the plagiarism case
     * @return list of posts associated with the case
     */
    @Query("""
            SELECT DISTINCT post
            FROM Post post
            WHERE post.plagiarismCase.id = :plagiarismCaseId
            """)
    List<Post> findPostsByPlagiarismCaseId(@Param("plagiarismCaseId") long plagiarismCaseId);

    /**
     * Retrieves a post by ID or throws an EntityNotFoundException if not found,
     * only if it is not associated with a conversation.
     *
     * @param postId ID of the post
     * @return the found post
     * @throws EntityNotFoundException if the post is not found or belongs to a conversation
     */
    default Post findPostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return getValueElseThrow(findById(postId).filter(post -> post.getConversation() == null), postId);
    }

    /**
     * Retrieves a post by ID or throws an EntityNotFoundException if not found.
     * This method includes message posts (with or without a conversation).
     *
     * @param postId ID of the post
     * @return the found post
     * @throws EntityNotFoundException if the post is not found
     */
    default Post findPostOrMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return getValueElseThrow(findById(postId), postId);
    }

    /**
     * Finds all posts within the given conversation.
     *
     * @param conversationId ID of the conversation
     * @return number of posts in the conversation
     */
    @Query("""
            SELECT COUNT(DISTINCT p.id)
            FROM Post p
            WHERE p.conversation.id = :conversationId
            """)
    long countByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Finds all posts related to a specific course via conversations.
     *
     * @param courseId ID of the course
     * @return collection of posts associated with the course
     */
    @Query("""
            SELECT COUNT(DISTINCT p.id)
            FROM Post p
            WHERE p.conversation.course.id = :courseId
            """)
    long countPostsByCourseId(@Param("courseId") Long courseId);

    /**
     * Finds all posts related to a specific course via conversations.
     *
     * @param courseId ID of the course
     * @return list of posts associated with the course
     */
    @Query("""
            SELECT p
            FROM Post p
            LEFT JOIN FETCH p.author
            LEFT JOIN FETCH p.conversation
            WHERE p.conversation.course.id = :courseId
            """)
    List<Post> findAllByCourseId(@Param("courseId") long courseId);

    /**
     * Finds all posts whose IDs are in the given list.
     *
     * @param idList list of post IDs
     * @return list of matching posts
     */
    List<Post> findByIdIn(Collection<Long> idList);

    /**
     * Counts how many of the given posts are accessible to a specific user.
     * A post is accessible if it's in a course-wide channel or the user is a participant.
     *
     * @param postIds collection of post IDs
     * @param userId  ID of the user
     * @return number of accessible posts
     */
    @Query("""
            SELECT COUNT(DISTINCT post.id)
            FROM Post post
                LEFT JOIN post.conversation conv
                LEFT JOIN conv.conversationParticipants cp
            WHERE post.id IN :postIds
                AND (
                    (TYPE(conv) = Channel AND TREAT(conv AS Channel).isCourseWide = TRUE)
                    OR (cp.user.id = :userId)
                )
            """)
    long countAccessiblePosts(@Param("postIds") Collection<Long> postIds, @Param("userId") Long userId);

    /**
     * Verifies that the user has access to all specified posts.
     * Throws AccessForbiddenException if one or more posts are not accessible.
     *
     * @param postIds collection of post IDs
     * @param userId  ID of the user
     * @throws AccessForbiddenException if access is denied to one or more posts
     */
    default void userHasAccessToAllPostsElseThrow(Collection<Long> postIds, Long userId) {
        long accessibleCount = countAccessiblePosts(postIds, userId);
        if (accessibleCount != postIds.size()) {
            if (postIds.size() == 1) {
                throw new AccessForbiddenException("Post", postIds.iterator().next());
            }
            throw new AccessForbiddenException("Post", postIds);
        }
    }
}
