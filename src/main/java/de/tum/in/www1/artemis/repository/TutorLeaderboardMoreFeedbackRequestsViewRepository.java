package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardNotAnsweredMoreFeedbackRequestsView;

@Repository
public interface TutorLeaderboardMoreFeedbackRequestsViewRepository extends JpaRepository<TutorLeaderboardNotAnsweredMoreFeedbackRequestsView, Long> {

    List<TutorLeaderboardNotAnsweredMoreFeedbackRequestsView> findAllByCourseId(Long courseId);

    List<TutorLeaderboardNotAnsweredMoreFeedbackRequestsView> findAllByLeaderboardId_ExerciseId(Long exerciseId);
}
