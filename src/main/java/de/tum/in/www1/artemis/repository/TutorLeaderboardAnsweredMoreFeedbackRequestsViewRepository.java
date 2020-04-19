package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequestsView;

@Repository
public interface TutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository extends JpaRepository<TutorLeaderboardAnsweredMoreFeedbackRequestsView, Long> {

    List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> findAllByCourseId(long courseId);

    List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> findAllByLeaderboardId_ExerciseId(long exerciseId);
}
