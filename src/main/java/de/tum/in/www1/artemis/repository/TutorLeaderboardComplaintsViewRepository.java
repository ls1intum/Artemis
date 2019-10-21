package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAcceptedComplaintsView;

@Repository
public interface TutorLeaderboardComplaintsViewRepository extends JpaRepository<TutorLeaderboardAcceptedComplaintsView, Long> {

    List<TutorLeaderboardAcceptedComplaintsView> findAllByCourseId(Long courseId);

    List<TutorLeaderboardAcceptedComplaintsView> findAllByLeaderboardId_ExerciseId(Long exerciseId);
}
