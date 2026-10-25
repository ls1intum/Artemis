package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.TeamScore;
import de.tum.cit.aet.artemis.assessment.dto.score.OwnExerciseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.PeerExerciseAverageDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoreAverageDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.TeamScoreSumDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface TeamScoreRepository extends ArtemisJpaRepository<TeamScore, Long> {

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.OwnExerciseScoreDTO(s.exercise.id, s.lastRatedScore)
            FROM TeamScore s JOIN s.team.students student
            WHERE s.exercise.id IN :exerciseIds AND student.id = :studentId AND s.lastRatedResult IS NOT NULL AND s.lastRatedScore IS NOT NULL
            """)
    List<OwnExerciseScoreDTO> findOwnRatedScores(@Param("exerciseIds") Set<Long> exerciseIds, @Param("studentId") long studentId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.StudentScoreAverageDTO(student.id, SUM(s.lastRatedScore), COUNT(s.id))
            FROM TeamScore s
                JOIN s.team.students student
                JOIN student.courseRoles role
            WHERE s.exercise.id IN :exerciseIds AND s.lastRatedResult IS NOT NULL AND s.lastRatedScore IS NOT NULL
                AND role.course.id = :courseId AND role.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                AND student.deleted = FALSE
            GROUP BY student.id
            """)
    List<StudentScoreAverageDTO> findStudentAveragesForComparison(@Param("courseId") long courseId, @Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.PeerExerciseAverageDTO(s.exercise.id, AVG(s.lastRatedScore), COUNT(DISTINCT s.id))
            FROM TeamScore s
                JOIN s.team.students student
            WHERE s.exercise.id IN :exerciseIds AND student.id IN :studentIds
                AND s.lastRatedResult IS NOT NULL AND s.lastRatedScore IS NOT NULL
                AND NOT EXISTS (SELECT ownStudent.id FROM Team team JOIN team.students ownStudent WHERE team = s.team AND ownStudent.id = :requestingStudentId)
            GROUP BY s.exercise.id
            """)
    List<PeerExerciseAverageDTO> findPeerExerciseAverages(@Param("exerciseIds") Set<Long> exerciseIds, @Param("studentIds") Set<Long> studentIds,
            @Param("requestingStudentId") long requestingStudentId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByTeamId(long teamId);

    @EntityGraph(type = LOAD, attributePaths = { "team.students", "exercise" })
    Optional<TeamScore> findByExercise_IdAndTeam_Id(Long exerciseId, Long teamId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.TeamScoreSumDTO(t.id, COALESCE(SUM(s.lastRatedPoints), 0))
            FROM TeamScore s
                LEFT JOIN s.team t
            WHERE s.exercise IN :exercises
            GROUP BY t.id
            """)
    Set<TeamScoreSumDTO> getAchievedPointsOfTeams(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT s
            FROM TeamScore s
                LEFT JOIN FETCH s.exercise
                LEFT JOIN FETCH s.team t
                LEFT JOIN FETCH t.students
            WHERE s.exercise IN :exercises
                AND :user MEMBER OF t.students
            """)
    List<TeamScore> findAllByExerciseAndUserWithEagerExercise(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Query("""
            SELECT s
            FROM TeamScore s
                LEFT JOIN FETCH s.team t
                LEFT JOIN FETCH t.students
            WHERE s.exercise IN :exercises
                AND :user MEMBER OF t.students
            """)
    List<TeamScore> findAllByExercisesAndUser(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Query("""
            SELECT studs
            FROM TeamScore s
                LEFT JOIN s.team t
                LEFT JOIN t.students studs
            WHERE s.exercise = :exercise
            """)
    Set<User> findAllUsersWithScoresByExercise(@Param("exercise") Exercise exercise);

    @Transactional // ok because of delete
    @Modifying
    void deleteByExerciseAndTeam(Exercise exercise, Team team);
}
