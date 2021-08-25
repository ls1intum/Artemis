package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;

/**
 * Spring Data repository for the TextCluster entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TutorEffortStatistcsRepository extends JpaRepository<TextCluster, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "blocks", "blocks.submission", "blocks.submission.results" })
    List<TextCluster> findAllByExercise(TextExercise exercise);

    @Query("SELECT distinct cluster FROM TextCluster cluster LEFT JOIN FETCH cluster.blocks b LEFT JOIN FETCH b.submission blocksub LEFT JOIN FETCH blocksub.results WHERE cluster.id IN :#{#clusterIds}")
    List<TextCluster> findAllByIdsWithEagerTextBlocks(@Param("clusterIds") Set<Long> clusterIds);

}
