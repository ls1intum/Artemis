package de.tum.in.www1.artemis.repository.metis;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the AnswerPost entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnswerPostRepository extends JpaRepository<AnswerPost, Long> {

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
