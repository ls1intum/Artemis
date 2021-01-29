package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;

@Repository
public interface ParticipantScoreRepository extends JpaRepository<ParticipantScore, Long> {

    void removeAllByExercise(Exercise exercise);

    Optional<ParticipantScore> findParticipantScoreByLastRatedResult(Result result);

    Optional<ParticipantScore> findParticipantScoresByLastResult(Result result);

    List<ParticipantScore> findAllByExerciseIn(Set<Exercise> exercises, Pageable pageable);
}
