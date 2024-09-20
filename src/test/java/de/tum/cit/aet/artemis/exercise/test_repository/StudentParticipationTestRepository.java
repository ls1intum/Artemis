package de.tum.cit.aet.artemis.exercise.test_repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

@Repository
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
                LEFT JOIN FETCH p.results r
                LEFT JOIN FETCH r.submission rs
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH r.assessor
            WHERE p.id = :participationId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
                AND (rs.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR rs.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsAndResultsAssessorsById(@Param("participationId") long participationId);

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.results r
                LEFT JOIN FETCH r.submission s
                LEFT JOIN FETCH r.feedbacks
            WHERE p.id = :participationId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerResultsAndFeedbackById(@Param("participationId") long participationId);
}
