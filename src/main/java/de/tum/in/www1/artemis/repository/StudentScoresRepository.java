package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.StudentScore;

@Repository
public interface StudentScoresRepository extends JpaRepository<StudentScore, Long> {

    List<StudentScore> findAllByExerciseId(long exerciseId);
}
