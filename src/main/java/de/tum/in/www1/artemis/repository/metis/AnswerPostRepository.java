package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.annotation.Nonnull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Nonnull
    default AnswerPost findAnswerPostByIdElseThrow(Long answerPostId) {
        return findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() == null)
                .orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }

    @Nonnull
    default AnswerPost findAnswerMessageByIdElseThrow(Long answerPostId) {
        return findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() != null)
                .orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }

    @Nonnull
    default AnswerPost findAnswerPostOrAnswerMessageByIdElseThrow(Long answerPostId) {
        return findById(answerPostId).orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }
}
