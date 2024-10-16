package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.StudentsAlreadyAssignedException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * Spring Data repository for the Team entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TeamRepository extends ArtemisJpaRepository<Team, Long> {

    @EntityGraph(type = LOAD, attributePaths = "students")
    List<Team> findAllByExerciseId(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "students")
    List<Team> findAllWithStudentsByIdIn(Collection<Long> teamIds);

    List<Team> findAllByExerciseCourseIdAndShortName(Long courseId, String shortName);

    /**
     * Fetches the number of teams created for an exercise
     *
     * @param exerciseId the id of the exercise to get the number of teams for
     * @return the amount of teams for an exercise
     */
    @Query("""
            SELECT COUNT(DISTINCT team)
            FROM Team team
            WHERE team.exercise.id = :exerciseId
            """)
    Integer getNumberOfTeamsForExercise(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT team
            FROM Team team
                LEFT JOIN team.students student
            WHERE team.exercise.course.id = :courseId
                AND student.id = :userId
            ORDER BY team.id DESC
            """)
    List<Team> findAllByCourseIdAndUserIdOrderByIdDesc(@Param("courseId") long courseId, @Param("userId") long userId);

    boolean existsByExerciseCourseIdAndShortName(Long courseId, String shortName);

    @Query("""
            SELECT team
            FROM Team team
                LEFT JOIN team.students student
            WHERE team.exercise.id = :exerciseId
                AND student.id = :userId
            """)
    Optional<Team> findOneByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT team
            FROM Team team
                LEFT JOIN team.students student
            WHERE team.exercise.id = :exerciseId
                AND student.login = :userLogin
            """)
    Optional<Team> findOneByExerciseIdAndUserLogin(@Param("exerciseId") Long exerciseId, @Param("userLogin") String userLogin);

    @Query("""
            SELECT student.id, team.id
            FROM Team team
                LEFT JOIN team.students student
            WHERE team.exercise.id = :exerciseId
                AND student.id IN :userIds
            """)
    List<long[]> findAssignedUserIdsWithTeamIdsByExerciseIdAndUserIds(@Param("exerciseId") Long exerciseId, @Param("userIds") List<Long> userIds);

    @Query("""
            SELECT DISTINCT team
            FROM Team team
                LEFT JOIN FETCH team.students
            WHERE team.exercise.id = :exerciseId
            """)
    List<Team> findAllByExerciseIdWithEagerStudents(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT team
            FROM Team team
                LEFT JOIN FETCH team.students
            WHERE team.exercise.id = :exerciseId
                AND team.owner.id = :teamOwnerId
            """)
    List<Team> findAllByExerciseIdAndTeamOwnerIdWithEagerStudents(@Param("exerciseId") long exerciseId, @Param("teamOwnerId") long teamOwnerId);

    @EntityGraph(type = LOAD, attributePaths = "students")
    Optional<Team> findWithStudentsById(Long teamId);

    /**
     * Returns all teams for an exercise (optionally filtered for a specific tutor who owns the teams)
     *
     * @param exercise    Exercise for which to return all teams
     * @param teamOwnerId Optional user id by which to filter teams on their owner
     * @return List of teams
     */
    default List<Team> findAllByExerciseIdWithEagerStudents(Exercise exercise, Optional<Long> teamOwnerId) {
        return teamOwnerId.map(id -> findAllByExerciseIdAndTeamOwnerIdWithEagerStudents(exercise.getId(), id))
                .orElseGet(() -> findAllByExerciseIdWithEagerStudents(exercise.getId()));
    }

    /**
     * Saves a team to the database (and verifies before that none of the students is already assigned to another team)
     *
     * @param exercise Exercise which the team belongs to
     * @param team     Team to be saved
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
        team = save(team);
        return findWithStudentsByIdElseThrow(team.getId());
    }

    /**
     * Checks for each student in the given team whether they already belong to a different team
     *
     * @param exercise Exercise which the team belongs to
     * @param team     Team whose students should be checked for conflicts with other teams
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

    default Team findWithStudentsByIdElseThrow(long teamId) throws EntityNotFoundException {
        return getValueElseThrow(findWithStudentsById(teamId), teamId);
    }
}
