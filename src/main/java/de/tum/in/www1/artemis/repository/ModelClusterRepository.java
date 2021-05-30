package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.modeling.ModelCluster;

/**
 * Spring Data JPA repository for the ModelCluster entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelClusterRepository extends JpaRepository<ModelCluster, Long> {

    @Query("select distinct cluster from ModelCluster cluster left join fetch cluster.modelElements element where cluster.exercise.id = :#{#exerciseId}")
    List<ModelCluster> findAllByExerciseIdWithEagerElements(@Param("exerciseId") Long exerciseId);

    @Query("select cluster from ModelCluster cluster left join fetch cluster.modelElements element where cluster.id = :#{#clusterId}")
    Optional<ModelCluster> findByIdWithEagerElements(@Param("clusterId") Long clusterId);

    @Query("select distinct feedback from Feedback feedback join ModelElement element where element.cluster.id = :#{#clusterId} and feedback.reference = concat(element.modelElementType,':',element.modelElementId)")
    List<Feedback> findFeedbacksWithClusterId(@Param("clusterId") Long clusterId);

}
