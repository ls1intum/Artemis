package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextCluster;

/**
 * Spring Data repository for the TextCluster entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextClusterRepository extends JpaRepository<TextCluster, Long> {

    // public List<TextCluster> findAllByExercise(TextExercise exercise);

}
