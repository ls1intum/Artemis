package de.tum.cit.aet.artemis.communication.test_repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.repository.ReactionRepository;

@Repository
public interface ReactionTestRepository extends ReactionRepository {

    List<Reaction> findReactionsByAnswerPostId(Long answerPostId);

    List<Reaction> findReactionsByPostId(Long postId);
}
