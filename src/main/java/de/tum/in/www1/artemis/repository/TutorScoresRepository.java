package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorScore;

@Repository
public interface TutorScoresRepository extends JpaRepository<TutorScore, Long> {

    List<TutorScore> findAllByExerciseId(long exerciseId);
}
