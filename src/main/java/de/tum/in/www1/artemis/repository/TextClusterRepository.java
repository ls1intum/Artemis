package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;

/**
 * Spring Data repository for the TextCluster entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextClusterRepository extends JpaRepository<TextCluster, Long> {

    @EntityGraph(type = LOAD, attributePaths = "blocks")
    List<TextCluster> findAllByExercise(TextExercise exercise);

}
