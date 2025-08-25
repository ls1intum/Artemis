package de.tum.cit.aet.artemis.exercise.test_repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

@Lazy
@Repository
@Primary
public interface StudentParticipationTestRepository extends StudentParticipationRepository {

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = :testRun
            """)
    List<StudentParticipation> findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(@Param("exerciseId") long exerciseId, @Param("testRun") boolean testRun);

    @Query("""
            SELECT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor
            WHERE p.id = :participationId
            """)
    Optional<StudentParticipation> findWithEagerSubmissionsAndResultsAssessorsById(@Param("participationId") long participationId);

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE p.id = :participationId
            """)
    Optional<StudentParticipation> findWithEagerResultsAndFeedbackById(@Param("participationId") long participationId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN p.team.students ts
            WHERE p.exercise.course.id = :courseId
                AND (p.student.id = :studentId
                    OR ts.id = :studentId)
                AND (r.rated IS NULL
                    OR r.rated = TRUE)
            """)
    List<StudentParticipation> findByCourseIdAndStudentIdWithEagerRatedResults(@Param("courseId") long courseId, @Param("studentId") long studentId);

}
