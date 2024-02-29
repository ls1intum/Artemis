package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Reaction entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    List<Reaction> findReactionsByPostId(Long postId);

    List<Reaction> findReactionsByUserId(long userId);

    List<Reaction> findReactionsByAnswerPostId(Long answerPostId);

    default Reaction findByIdElseThrow(Long reactionId) throws EntityNotFoundException {
        return findById(reactionId).orElseThrow(() -> new EntityNotFoundException("Reaction", reactionId));
    }
}
