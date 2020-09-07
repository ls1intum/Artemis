package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.text.TextExercise;
import de.tum.in.www1.artemis.domain.text.TextPairwiseDistance;

/**
 * Spring Data repository for the TextPairwiseDistance entity.
 */
@Repository
public interface TextPairwiseDistanceRepository extends JpaRepository<TextPairwiseDistance, Long> {

    List<TextPairwiseDistance> findAllByExercise(TextExercise exercise);

    TextPairwiseDistance findByExerciseAndAndBlockIAndBlockJ(TextExercise exercise, long blockI, long blockJ);
}
