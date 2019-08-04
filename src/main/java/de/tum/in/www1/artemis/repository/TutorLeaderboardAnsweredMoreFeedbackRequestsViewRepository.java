package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequestsView;

public interface TutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository extends JpaRepository<TutorLeaderboardAnsweredMoreFeedbackRequestsView, Long> {

    List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> findAllByCourseId(Long courseId);

    List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> findAllByLeaderboardId_ExerciseId(Long exerciseId);
}
