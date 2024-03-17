package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
                SELECT reaction
                FROM Reaction reaction
                    LEFT JOIN FETCH reaction.post
                    LEFT JOIN FETCH reaction.answerPost answerPost
                    LEFT JOIN FETCH answerPost.post post
                    LEFT JOIN FETCH post.conversation
                WHERE reaction.id = :reactionId
            """)
    Optional<Reaction> findWithAnswerPostOrPostById(@Param("reactionId") Long reactionId);

    default Reaction findByIdElseThrow(Long reactionId) throws EntityNotFoundException {
        return findWithAnswerPostOrPostById(reactionId).orElseThrow(() -> new EntityNotFoundException("Reaction", reactionId));
    }
}
