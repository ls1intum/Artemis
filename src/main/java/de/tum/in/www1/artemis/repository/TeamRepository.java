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

    List<Team> findAllByExerciseCourseIdAndShortName(@Param("courseId") Long courseId, @Param("shortName") String shortName);

    @Query("select count(distinct team) from Team team where team.exercise.id = :#{#exerciseId}")
    Integer getNumberOfTeamsForExercise(@Param("exerciseId") Long exerciseId);

    @Query(value = "select distinct team from Team team left join team.students student where team.exercise.course.id = :#{#courseId} and student.id = :#{#userId} order by team.id desc")
    List<Team> findAllByCourseIdAndUserIdOrderByIdDesc(@Param("courseId") long courseId, @Param("userId") long userId);

    boolean existsByExerciseCourseIdAndShortName(@Param("courseId") Long courseId, @Param("shortName") String shortName);

    @Query(value = "select team from Team team left join team.students student where team.exercise.id = :#{#exerciseId} and student.id = :#{#userId}")
    Optional<Team> findOneByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query(value = "select team from Team team left join team.students student where team.exercise.id = :#{#exerciseId} and student.login = :#{#userLogin}")
    Optional<Team> findOneByExerciseIdAndUserLogin(@Param("exerciseId") Long exerciseId, @Param("userLogin") String userLogin);

    @Query(value = "select student.id, team.id from Team team left join team.students student where team.exercise.id = :#{#exerciseId} and student.id in :#{#userIds}")
    List<long[]> findAssignedUserIdsWithTeamIdsByExerciseIdAndUserIds(@Param("exerciseId") Long exerciseId, @Param("userIds") List<Long> userIds);

    @Query(value = "select distinct team from Team team left join fetch team.students where team.exercise.id = :#{#exerciseId}")
    List<Team> findAllByExerciseIdWithEagerStudents(@Param("exerciseId") Long exerciseId);

    @Query(value = "select distinct team from Team team left join fetch team.students where team.exercise.id = :#{#exerciseId} and team.owner.id = :#{#teamOwnerId}")
    List<Team> findAllByExerciseIdAndTeamOwnerIdWithEagerStudents(@Param("exerciseId") long exerciseId, @Param("teamOwnerId") long teamOwnerId);

    @Query("select team from Team team left join fetch team.students where team.id = :#{#teamId}")
    Optional<Team> findOneWithEagerStudents(@Param("teamId") Long teamId);
}
