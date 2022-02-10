package de.tum.in.www1.artemis.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.errors.StudentsAlreadyAssignedException;

/**
 * Spring Data repository for the Team entity.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByExerciseId(@Param("exerciseId") Long exerciseId);

    List<Team> findAllByExerciseCourseIdAndShortName(@Param("courseId") Long courseId, @Param("shortName") String shortName);

    /**
     * Fetches the number of teams created for an exercise
     *
     * @param exerciseId the id of the exercise to get the number of teams for
     * @return the amount of teams for an exercise
     */
    @Query("""
            SELECT COUNT(DISTINCT team)
            FROM Team team
            WHERE team.exercise.id = :#{#exerciseId}
            """)
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

    /**
     * Returns all teams for an exercise (optionally filtered for a specific tutor who owns the teams)
     * @param exercise Exercise for which to return all teams
     * @param teamOwnerId Optional user id by which to filter teams on their owner
     * @return List of teams
     */
    default List<Team> findAllByExerciseIdWithEagerStudents(Exercise exercise, Long teamOwnerId) {
        if (teamOwnerId != null) {
            return findAllByExerciseIdAndTeamOwnerIdWithEagerStudents(exercise.getId(), teamOwnerId);
        }
        else {
            return findAllByExerciseIdWithEagerStudents(exercise.getId());
        }
    }

    /**
     * Saves a team to the database (and verifies before that none of the students is already assigned to another team)
     *
     * @param exercise Exercise which the team belongs to
     * @param team Team to be saved
     * @return saved Team
     */
    default Team save(Exercise exercise, Team team) {
        // verify that students are not assigned yet to another team for this exercise
        List<Pair<User, Team>> conflicts = findStudentTeamConflicts(exercise, team);
        if (!conflicts.isEmpty()) {
            throw new StudentsAlreadyAssignedException(conflicts);
        }
        // audit information is normally updated automatically but since changes in the many-to-many relationships are not registered,
        // we need to trigger the audit explicitly by modifying a column of the team entity itself
        if (team.getId() != null) {
            team.setLastModifiedDate(Instant.now());
        }
        team.setExercise(exercise);
        return save(team);
    }

    /**
     * Checks for each student in the given team whether they already belong to a different team
     *
     * @param exercise Exercise which the team belongs to
     * @param team Team whose students should be checked for conflicts with other teams
     * @return list of conflict pairs <student, team> where team is a different team than in the argument
     */
    private List<Pair<User, Team>> findStudentTeamConflicts(Exercise exercise, Team team) {
        List<Pair<User, Team>> conflicts = new ArrayList<>();
        team.getStudents().forEach(student -> {
            Optional<Team> assignedTeam = findOneByExerciseIdAndUserId(exercise.getId(), student.getId());
            if (assignedTeam.isPresent() && !assignedTeam.get().equals(team)) {
                conflicts.add(Pair.of(student, assignedTeam.get()));
            }
        });
        return conflicts;
    }

    default Team findByIdElseThrow(long teamId) throws EntityNotFoundException {
        return findById(teamId).orElseThrow(() -> new EntityNotFoundException("Team", teamId));
    }
}
