package de.tum.in.www1.artemis.repository;

import static java.util.stream.Collectors.toMap;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.web.rest.dto.TextClusterStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the TextCluster entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextClusterRepository extends JpaRepository<TextCluster, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "blocks", "blocks.submission", "blocks.submission.results" })
    List<TextCluster> findAllByExercise(TextExercise exercise);

    @EntityGraph(type = LOAD, attributePaths = { "exercise" })
    Optional<TextCluster> findWithEagerExerciseById(Long clusterId);

    @NotNull
    default TextCluster findWithEagerExerciseByIdElseThrow(Long clusterId) {
        return findWithEagerExerciseById(clusterId).orElseThrow(() -> new EntityNotFoundException("TextCluster", clusterId));
    }

    @Query("SELECT distinct cluster FROM TextCluster cluster LEFT JOIN FETCH cluster.blocks b LEFT JOIN FETCH b.submission blocksub LEFT JOIN FETCH blocksub.results WHERE cluster.id IN :#{#clusterIds}")
    List<TextCluster> findAllByIdsWithEagerTextBlocks(@Param("clusterIds") Set<Long> clusterIds);

    @Query(value = """
            SELECT new de.tum.in.www1.artemis.web.rest.dto.TextClusterStatisticsDTO(
                textblock.cluster.id,
                count(DISTINCT textblock.id),
                SUM(case when feedback.type = 'AUTOMATIC' then 1 else 0 end)
            )
            FROM TextBlock textblock
            LEFT JOIN Submission submission ON textblock.submission.id = submission.id
            LEFT JOIN Result result ON result.submission.id = submission.id
            LEFT JOIN Feedback feedback ON ( feedback.result.id = result.id and feedback.reference = textblock.id )
            LEFT JOIN Participation participation ON participation.id = submission.participation.id
            WHERE participation.exercise.id = :#{#exerciseId}
            GROUP BY textblock.cluster.id HAVING textblock.cluster.id > 0
            """)
    List<TextClusterStatisticsDTO> getClusterStatistics1(@Param("exerciseId") Long exerciseId);

    interface TextClusterIdAndDisabled {

        Long getClusterId();

        boolean getDisabled();
    }

    @Query("SELECT cluster.id as clusterId, cluster.disabled as disabled FROM TextCluster cluster")
    List<TextClusterIdAndDisabled> findAllWithIdAndDisabled();

    default Map<Long, Boolean> getTextClusterWithIdAndDisabled() {
        return findAllWithIdAndDisabled().stream().collect(toMap(TextClusterIdAndDisabled::getClusterId, TextClusterIdAndDisabled::getDisabled));
    }
}
