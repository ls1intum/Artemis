package de.tum.cit.aet.artemis.modeling.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelCluster;

/**
 * Spring Data JPA repository for the ModelCluster entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ModelClusterRepository extends ArtemisJpaRepository<ModelCluster, Long> {

    @Query("""
            SELECT COUNT (DISTINCT cluster)
            FROM ModelCluster cluster
            WHERE cluster.exercise.id = :exerciseId
            """)
    Integer countByExerciseIdWithEagerElements(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT cluster
            FROM ModelCluster cluster
                LEFT JOIN FETCH cluster.modelElements element
            WHERE cluster.exercise.id = :exerciseId
            """)
    List<ModelCluster> findAllByExerciseIdWithEagerElements(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT cluster
            FROM ModelCluster cluster
                LEFT JOIN FETCH cluster.modelElements element
            WHERE cluster.id IN :clusterIds
            """)
    List<ModelCluster> findAllByIdInWithEagerElements(@Param("clusterIds") List<Long> clusterIds);
}
