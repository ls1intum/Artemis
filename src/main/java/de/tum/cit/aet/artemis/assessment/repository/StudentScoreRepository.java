package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.web.rest.dto.score.StudentScoreSum;

@Profile(PROFILE_CORE)
@Repository
public interface StudentScoreRepository extends ArtemisJpaRepository<StudentScore, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(long userId);

    @EntityGraph(type = LOAD, attributePaths = { "user", "exercise" })
    Optional<StudentScore> findByExercise_IdAndUser_Id(long exerciseId, long userId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.score.StudentScoreSum(u.id, COALESCE(SUM(s.lastRatedPoints), 0))
            FROM StudentScore s
                LEFT JOIN s.user u
            WHERE s.exercise IN :exercises
            GROUP BY u.id
            """)
    Set<StudentScoreSum> getAchievedPointsOfStudents(@Param("exercises") Set<Exercise> exercises);

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
