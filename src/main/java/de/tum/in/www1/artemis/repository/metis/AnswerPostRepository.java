package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the AnswerPost entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AnswerPostRepository extends ArtemisJpaRepository<AnswerPost, Long> {

    List<AnswerPost> findAnswerPostsByAuthorId(long authorId);

    @NotNull
    default AnswerPost findAnswerPostByIdElseThrow(Long answerPostId) {
        return findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() == null)
                .orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }

    @NotNull
    default AnswerPost findAnswerMessageByIdElseThrow(Long answerPostId) {
        return findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() != null)
                .orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }

    @NotNull
    default AnswerPost findAnswerPostOrAnswerMessageByIdElseThrow(Long answerPostId) {
        return findById(answerPostId).orElseThrow(() -> new EntityNotFoundException("Answer Post", answerPostId));
    }
}
