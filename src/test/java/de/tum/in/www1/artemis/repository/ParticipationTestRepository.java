package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.Participation;

@Repository
public interface ParticipationTestRepository extends JpaRepository<Participation, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    List<Participation> findByExercise_ExerciseGroup_Exam_Id(long examId);
}
