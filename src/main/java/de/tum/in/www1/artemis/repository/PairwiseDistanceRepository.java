package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.PairwiseDistance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

/**
 * Spring Data repository for the PairwiseDistance entity.
 */
@Repository
public interface PairwiseDistanceRepository extends JpaRepository<PairwiseDistance, Long> {

    @EntityGraph(type = LOAD, attributePaths = "pairwiseDistances")
    List<PairwiseDistance> findAllByExercise(TextExercise exercise);
}
