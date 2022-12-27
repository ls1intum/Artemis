package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.Participation;

@Repository
public interface ParticipationTestRepository extends JpaRepository<Participation, Long> {

    @Query("""
             SELECT DISTINCT p
             FROM Participation p
                 LEFT JOIN FETCH p.submissions
             WHERE p.exercise.exerciseGroup.exam.id = :examId
            """)
    List<Participation> findByExercise_ExerciseGroup_Exam_Id(long examId);
}
