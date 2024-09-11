package de.tum.cit.aet.artemis.repository.metis;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.metis.AnswerPost;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the AnswerPost entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AnswerPostRepository extends ArtemisJpaRepository<AnswerPost, Long> {

    List<AnswerPost> findAnswerPostsByAuthorId(long authorId);

    @NotNull
    default AnswerPost findAnswerPostByIdElseThrow(Long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() == null), answerPostId);
    }

    @NotNull
    default AnswerPost findAnswerMessageByIdElseThrow(Long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() != null), answerPostId);
    }
}
