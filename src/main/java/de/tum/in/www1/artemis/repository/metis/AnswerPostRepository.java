package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the AnswerPost entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AnswerPostRepository extends JpaRepository<AnswerPost, Long> {

    List<AnswerPost> findAnswerPostsByAuthorId(long authorId);

    @Query("""
                SELECT answerPost
                FROM AnswerPost answerPost
                    LEFT JOIN FETCH answerPost.post post
                    LEFT JOIN FETCH post.conversation
                WHERE answerPost.id = :answerPostId
            """)
    Optional<AnswerPost> findWithPostById(@Param("answerPostId") Long answerPostId);

    default AnswerPost saveAndReload(AnswerPost answerPost) {
        Long id = save(answerPost).getId();
        return findWithPostById(id).orElseThrow();
    }

    @NotNull
    default AnswerPost findAnswerPostByIdElseThrow(Long answerPostId) {
        return findWithPostById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() == null)
                .orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }

    @NotNull
    default AnswerPost findAnswerMessageByIdElseThrow(Long answerPostId) {
        return findWithPostById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() != null)
                .orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }

    @NotNull
    default AnswerPost findAnswerPostOrAnswerMessageByIdElseThrow(Long answerPostId) {
        return findWithPostById(answerPostId).orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }
}
