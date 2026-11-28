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
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.dto.score.OwnExerciseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.PeerExerciseAverageDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoreAverageDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoreSumDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface StudentScoreRepository extends ArtemisJpaRepository<StudentScore, Long> {

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.OwnExerciseScoreDTO(s.exercise.id, s.lastRatedScore)
            FROM StudentScore s
            WHERE s.exercise.id IN :exerciseIds AND s.user.id = :studentId AND s.lastRatedResult IS NOT NULL AND s.lastRatedScore IS NOT NULL
            """)
    List<OwnExerciseScoreDTO> findOwnRatedScores(@Param("exerciseIds") Set<Long> exerciseIds, @Param("studentId") long studentId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.StudentScoreAverageDTO(s.user.id, SUM(s.lastRatedScore), COUNT(s.id))
            FROM StudentScore s
                JOIN s.user.courseRoles role
            WHERE s.exercise.id IN :exerciseIds AND s.lastRatedResult IS NOT NULL AND s.lastRatedScore IS NOT NULL
                AND role.course.id = :courseId AND role.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                AND s.user.deleted = FALSE
            GROUP BY s.user.id
            """)
    List<StudentScoreAverageDTO> findStudentAveragesForComparison(@Param("courseId") long courseId, @Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.PeerExerciseAverageDTO(s.exercise.id, AVG(s.lastRatedScore), COUNT(s.id))
            FROM StudentScore s
            WHERE s.exercise.id IN :exerciseIds AND s.user.id IN :studentIds
                AND s.lastRatedResult IS NOT NULL AND s.lastRatedScore IS NOT NULL
            GROUP BY s.exercise.id
            """)
    List<PeerExerciseAverageDTO> findPeerExerciseAverages(@Param("exerciseIds") Set<Long> exerciseIds, @Param("studentIds") Set<Long> studentIds);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(long userId);

    @EntityGraph(type = LOAD, attributePaths = { "user", "exercise" })
    Optional<StudentScore> findByExercise_IdAndUser_Id(long exerciseId, long userId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.score.StudentScoreSumDTO(u.id, COALESCE(SUM(s.lastRatedPoints), 0))
            FROM StudentScore s
                LEFT JOIN s.user u
            WHERE s.exercise IN :exercises
            GROUP BY u.id
            """)
    Set<StudentScoreSumDTO> getAchievedPointsOfStudents(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT s
            FROM StudentScore s
            WHERE s.user = :user
                AND s.exercise IN :exercises
            """)
    List<StudentScore> findAllByExercisesAndUser(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Query("""
            SELECT s
            FROM StudentScore s
                LEFT JOIN FETCH s.exercise ex
            WHERE ex IN :exercises
                AND s.user = :user
            """)
    List<StudentScore> findAllByExerciseAndUserWithEagerExercise(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Query("""
            SELECT stud
            FROM StudentScore s
                LEFT JOIN s.user stud
            WHERE s.exercise = :exercise
            """)
    Set<User> findAllUsersWithScoresByExercise(@Param("exercise") Exercise exercise);

    @Transactional // ok because of delete
    @Modifying
    void deleteByExerciseAndUser(Exercise exercise, User user);

}
