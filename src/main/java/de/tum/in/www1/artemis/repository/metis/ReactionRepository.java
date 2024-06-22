package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Reaction entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ReactionRepository extends ArtemisJpaRepository<Reaction, Long> {

    List<Reaction> findReactionsByPostId(Long postId);

    List<Reaction> findReactionsByUserId(long userId);

    List<Reaction> findReactionsByAnswerPostId(Long answerPostId);
}
