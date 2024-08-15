package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the AnswerPost entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AnswerPostRepository extends ArtemisJpaRepository<AnswerPost, Long> {

    List<AnswerPost> findAnswerPostsByAuthorId(long authorId);

    @NonNull
    default AnswerPost findAnswerPostByIdElseThrow(Long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() == null), answerPostId);
    }

    @NonNull
    default AnswerPost findAnswerMessageByIdElseThrow(Long answerPostId) {
        return getValueElseThrow(findById(answerPostId).filter(answerPost -> answerPost.getPost().getConversation() != null), answerPostId);
    }
}
