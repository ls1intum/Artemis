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
public interface TextClusterRepository extends JpaRepository<TextCluster, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "blocks", "blocks.submission", "blocks.submission.results" })
    List<TextCluster> findAllByExercise(TextExercise exercise);

    @Query("SELECT distinct cluster FROM TextCluster cluster LEFT JOIN FETCH cluster.blocks b LEFT JOIN FETCH b.submission blocksub LEFT JOIN FETCH blocksub.results WHERE cluster.id IN :#{#clusterIds}")
    List<TextCluster> findAllByIdsWithEagerTextBlocks(@Param("clusterIds") Set<Long> clusterIds);

    interface TextClusterStats {

        Long getClusterId();

        Long getClusterSize();

        Long getNumberOfAutomaticFeedbacks();

        Boolean getDisabled();
    }

    @Query(value = """
            SELECT cluster_stats.*, text_cluster.disabled FROM
            (
            SELECT text_block.cluster_id AS clusterId, count(DISTINCT text_block.id) AS clusterSize, SUM(case when feedback.type = 'AUTOMATIC' then 1 else 0 end) AS numberOfAutomaticFeedbacks
            FROM text_block
            LEFT JOIN submission ON text_block.submission_id = submission.id
            LEFT JOIN result ON result.submission_id = submission.id
            LEFT JOIN feedback ON ( feedback.result_id = result.id and feedback.reference = text_block.id )
            LEFT JOIN participation ON participation.id = submission.participation_id
            WHERE participation.exercise_id = ?1
            GROUP BY clusterId HAVING clusterId > 0
            ) AS cluster_stats
            LEFT JOIN text_cluster ON text_cluster.id = cluster_stats.clusterId
            """, nativeQuery = true)
    List<TextClusterStats> getClusterStatistics(@Param("exerciseId") Long exerciseId);

}
