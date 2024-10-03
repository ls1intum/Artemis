package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

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
        return getValueElseThrow(findById(postId).filter(post -> post.getConversation() == null), postId);
    }

    default Post findPostOrMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return getValueElseThrow(findById(postId), postId);
    }

    List<Post> findAllByConversationId(Long conversationId);

    List<Post> findAllByCourseId(Long courseId);
}
