package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Post entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface PostRepository extends ArtemisJpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

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
                AND p.conversation.course.id = :courseId
            """)
    List<Post> findPostsByAuthorIdAndCourseId(@Param("authorId") long authorId, @Param("courseId") long courseId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByConversationId(Long conversationId);

    @Query("""
            SELECT DISTINCT post
            FROM Post post
                LEFT JOIN post.answers answer
                LEFT JOIN post.reactions reaction
            WHERE post.plagiarismCase.id = :plagiarismCaseId
            """)
    List<Post> findPostsByPlagiarismCaseId(@Param("plagiarismCaseId") Long plagiarismCaseId);

    default Post findPostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).filter(post -> post.getConversation() == null).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }

    default Post findPostOrMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
