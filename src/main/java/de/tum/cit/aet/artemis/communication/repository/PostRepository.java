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
}
