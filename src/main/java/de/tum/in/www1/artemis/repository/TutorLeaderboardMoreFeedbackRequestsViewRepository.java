package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequestsView;

@Repository
public interface TutorLeaderboardMoreFeedbackRequestsViewRepository extends JpaRepository<TutorLeaderboardMoreFeedbackRequestsView, Long> {

    List<TutorLeaderboardMoreFeedbackRequestsView> findAllByCourseId(long courseId);

    List<TutorLeaderboardMoreFeedbackRequestsView> findAllByLeaderboardId_ExerciseId(long exerciseId);
}
