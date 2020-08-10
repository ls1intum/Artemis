package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.text.TextExercise;
import de.tum.in.www1.artemis.domain.text.TextPairwiseDistance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for the TextPairwiseDistance entity.
 */
@Repository
public interface TextPairwiseDistanceRepository extends JpaRepository<TextPairwiseDistance, Long> {

    List<TextPairwiseDistance> findAllByExercise(TextExercise exercise);

    // It must hold: treeIdI <= treeIdJ
    @Query("SELECT distinct d FROM TextPairwiseDistance d " +
        "LEFT JOIN TextExercise e ON d.exercise = e " +
        "WHERE e.id = :#{#exerciseId} AND d.block_i = :#{#treeIdI} AND d.block_j = :#{#treeIdJ}")
    TextPairwiseDistance findByExerciseAndBlocks(@Param("id") Long exerciseId, @Param("block_i") Long treeIdI, @Param("block_j") Long treeIdJ);
}
