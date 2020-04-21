package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Team;

/**
 * Spring Data repository for the Team entity.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByExerciseId(@Param("exerciseId") Long exerciseId);

    Optional<Team> findOneByExerciseIdAndShortName(@Param("exerciseId") Long exerciseId, @Param("shortName") String shortName);

    @Query(value = "SELECT team FROM Team team LEFT JOIN team.students student WHERE team.exercise.id = :#{#exerciseId} AND student.id = :#{#userId}")
    Optional<Team> findOneByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query(value = "SELECT team FROM Team team LEFT JOIN team.students student WHERE team.exercise.id = :#{#exerciseId} AND student.login = :#{#userLogin}")
    Optional<Team> findOneByExerciseIdAndUserLogin(@Param("exerciseId") Long exerciseId, @Param("userLogin") String userLogin);

    @Query(value = "SELECT student.id, team.id FROM Team team LEFT JOIN team.students student WHERE team.exercise.id = :#{#exerciseId} AND student.id IN :#{#userIds}")
    List<long[]> findAssignedUserIdsWithTeamIdsByExerciseIdAndUserIds(@Param("exerciseId") Long exerciseId, @Param("userIds") List<Long> userIds);

    @Query(value = "SELECT DISTINCT team FROM Team team LEFT JOIN FETCH team.students WHERE team.exercise.id = :#{#exerciseId}")
    List<Team> findAllWithEagerStudentsByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT team FROM Team team LEFT JOIN FETCH team.students WHERE team.id = :#{#teamId}")
    Optional<Team> findOneWithEagerStudents(@Param("teamId") Long teamId);
}
