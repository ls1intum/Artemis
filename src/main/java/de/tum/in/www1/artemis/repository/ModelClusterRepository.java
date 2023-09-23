package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ModelCluster;

/**
 * Spring Data JPA repository for the ModelCluster entity.
 */
@Repository
public interface ModelClusterRepository extends JpaRepository<ModelCluster, Long> {

    @Query("""
            SELECT COUNT (DISTINCT cluster)
            FROM ModelCluster cluster
            WHERE cluster.exercise.id = :#{#exerciseId}
            """)
    Integer countByExerciseIdWithEagerElements(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT cluster
            FROM ModelCluster cluster
            LEFT JOIN FETCH cluster.modelElements element
            WHERE cluster.exercise.id = :#{#exerciseId}
            """)
    List<ModelCluster> findAllByExerciseIdWithEagerElements(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT cluster
            FROM ModelCluster cluster
            LEFT JOIN FETCH cluster.modelElements element
            WHERE cluster.id in :#{#clusterIds}
            """)
    List<ModelCluster> findAllByIdInWithEagerElements(@Param("clusterIds") List<Long> clusterIds);
}
