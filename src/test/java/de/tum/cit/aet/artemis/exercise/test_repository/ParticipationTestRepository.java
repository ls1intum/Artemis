package de.tum.cit.aet.artemis.exercise.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;

@Repository
public interface ParticipationTestRepository extends ParticipationRepository {

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    List<Participation> findByExercise_ExerciseGroup_Exam_Id(long examId);

    Set<Participation> findByExerciseId(long exerciseId);

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.id = :participationId
                AND (s.id = (SELECT MAX(s2.id) FROM p.submissions s2) OR s.id IS NULL)
            """)
    Optional<Participation> findByIdWithLatestSubmission(@Param("participationId") long participationId);

    default Participation findByIdWithLatestSubmissionElseThrow(Long participationId) {
        return getValueElseThrow(findByIdWithLatestSubmission(participationId), participationId);
    }
}
