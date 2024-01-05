package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Post entity.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    List<Post> findPostsByAuthorId(long authorId);

    /**
     * find all posts of a user in a course
     * currently only used for testing
     *
     * @param authorId id of the user
     * @param courseId id of the course
     * @return a list of posts
     */
    @Query("""
            SELECT p
            FROM Post p
            WHERE p.author.id =:authorId
                  AND p.conversation.course.id =:courseId
            """)
    List<Post> findPostsByAuthorIdAndCourseId(long authorId, long courseId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByConversationId(Long conversationId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.answers answer LEFT JOIN post.reactions reaction
            WHERE post.plagiarismCase.id = :#{#plagiarismCaseId}
            """)
    List<Post> findPostsByPlagiarismCaseId(@Param("plagiarismCaseId") Long plagiarismCaseId);

    default Post findPostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).filter(post -> post.getConversation() == null).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }

    default Post findPostOrMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
