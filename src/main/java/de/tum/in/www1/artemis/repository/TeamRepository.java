package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Team;

/**
 * Spring Data repository for the Team entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    Set<Team> findAllByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query(value = "select team from Team team left join fetch team.students s where team.exercise.id = :#{#exerciseId} and s.id = :#{#userId}")
    Optional<Team> findOneByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query(value = "select distinct team from Team team left join fetch team.students where team.exercise.id = :#{#exerciseId}")
    Set<Team> findAllByExerciseIdWithEagerStudents(@Param("exerciseId") Long exerciseId);

    @Query("select team from Team team left join fetch team.students where team.id = :#{#teamId}")
    Optional<Team> findOneWithEagerStudents(@Param("teamId") Long teamId);
}
