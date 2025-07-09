package de.tum.cit.aet.artemis.exam.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;

@Repository
@Primary
public interface ExamTestRepository extends ExamRepository {

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups", "exerciseGroups.exercises", "exerciseGroups.exercises.studentParticipations",
            "exerciseGroups.exercises.studentParticipations.submissions" })
    Optional<Exam> findWithExerciseGroupsExercisesParticipationsAndSubmissionsById(long examId);
}
