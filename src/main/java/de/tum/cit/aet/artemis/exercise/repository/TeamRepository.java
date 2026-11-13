package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTeamAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.exception.StudentsAlreadyAssignedException;

/**
 * Spring Data repository for the Team entity.
 */
@Profile(PROFILE_CORE)
@Lazy
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

    /**
     * The student's team for an exercise, with its members.
     * <p>
     * Joined twice on purpose: the first join finds the team the student belongs to, the second fetches all of its members.
     * Filtering on the fetched alias would return the team carrying only the student that matched.
     *
     * @param exerciseId the id of the exercise
     * @param userId     the id of the student
     * @return the team with its students, or empty when the student is in none
     */
    @Query("""
            SELECT team
            FROM Team team
                JOIN team.students teamMember
                LEFT JOIN FETCH team.students
            WHERE team.exercise.id = :exerciseId
                AND teamMember.id = :userId
            """)
    Optional<Team> findOneWithStudentsByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    /**
     * Finds the requesting student's team for every given team exercise in one projection query.
     *
     * @param exerciseIds the team exercises shown in the course overview
     * @param userId      the requesting student
     * @return one assignment per exercise in which the student has a team
     */
    @Query("""
            SELECT NEW de.tum.cit.aet.artemis.exercise.dto.ExerciseTeamAssignmentDTO(team.exercise.id, team.id)
            FROM Team team
                JOIN team.students student
            WHERE team.exercise.id IN :exerciseIds
                AND student.id = :userId
            """)
    List<ExerciseTeamAssignmentDTO> findAssignmentsForCourseOverview(@Param("exerciseIds") Collection<Long> exerciseIds, @Param("userId") long userId);

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
     * @param team     Team to be saved
     * @return saved Team
     */
    default Team save(Exercise exercise, Team team) {
        // verify that students are not assigned yet to another team for this exercise
        List<Pair<User, Team>> conflicts = findStudentTeamConflicts(exercise, team);
        if (!conflicts.isEmpty()) {
            throw new StudentsAlreadyAssignedException(conflicts);
        }
        team.setExercise(exercise);
        team = save(team);
        return findWithStudentsByIdElseThrow(team.getId());
    }

    /**
     * Saves the tutor change and transfers unfinished assessments across matching course teams.
     *
     * @param exercise the team's exercise
     * @param team     the updated team
     * @param assessor the eligible new assessor, or null to unassign drafts
     * @return the saved team
     */
    @Transactional
    default Team saveWithTutorChange(Exercise exercise, Team team, User assessor) {
        var instances = lockCourseTeamsForAssessment(exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getId(), team.getShortName(), team.getId());
        Team saved = save(exercise, team);
        instances.forEach(instance -> instance.setOwner(saved.getOwner()));
        saveAll(instances);
        reassignDraftAssessors(instances.stream().map(Team::getId).toList(), assessor);
        return saved;
    }

    /** Locks matching teams in a stable order before transferring their drafts. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query("SELECT t FROM Team t WHERE (t.exercise.course.id = :courseId AND t.shortName = :shortName) OR t.id = :teamId ORDER BY t.id")
    List<Team> lockCourseTeamsForAssessment(@Param("courseId") long courseId, @Param("shortName") String shortName, @Param("teamId") long teamId);

    /** Holds the team lock until assessment creation has committed. */
    @Transactional
    default <T> T withAssessmentLock(Team team, Supplier<T> assessment) {
        lockTeamForAssessment(team.getId());
        // Reject stale requests instead of saving a result loaded under the previous tutor.
        if (!Objects.equals(team.getOwner(), findAssessmentOwner(team.getId()))) {
            throw new AccessForbiddenException("The team tutor changed. Reload the assessment.");
        }
        return assessment.get();
    }

    /** Locks a team against reassignment while its assessment is claimed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query("SELECT t FROM Team t WHERE t.id = :teamId")
    Team lockTeamForAssessment(@Param("teamId") long teamId);

    /** Reads the persisted owner rather than a previously loaded team association. */
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query("SELECT owner FROM Team t LEFT JOIN t.owner owner WHERE t.id = :teamId")
    User findAssessmentOwner(@Param("teamId") long teamId);

    /**
     * Transfers drafts without changing completed results, feedback, or notes.
     *
     * @param teamIds the teams whose drafts are transferred
     * @param owner   the new assessor, or null to unassign
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Result r SET r.assessor = :owner
            WHERE r.completionDate IS NULL
                AND r.assessmentType IN (de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL,
                    de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC)
                AND r.submission.id IN (SELECT submission.id FROM StudentParticipation p JOIN p.submissions submission
                    WHERE p.team.id IN :teamIds AND p.testRun = FALSE AND p.exercise.mode = de.tum.cit.aet.artemis.exercise.domain.ExerciseMode.TEAM)
            """)
    void reassignDraftAssessors(@Param("teamIds") List<Long> teamIds, @Param("owner") User owner);

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
