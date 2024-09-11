package de.tum.cit.aet.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

@Repository
public interface ParticipationTestRepository extends ArtemisJpaRepository<Participation, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    List<Participation> findByExercise_ExerciseGroup_Exam_Id(long examId);
}
