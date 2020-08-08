package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.text.TextExercise;
import de.tum.in.www1.artemis.domain.text.TextPairwiseDistance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for the TextPairwiseDistance entity.
 */
@Repository
public interface PairwiseDistanceRepository extends JpaRepository<TextPairwiseDistance, Long> {

    List<TextPairwiseDistance> findAllByExercise(TextExercise exercise);
}
