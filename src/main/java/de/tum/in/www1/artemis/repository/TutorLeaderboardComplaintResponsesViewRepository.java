package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponsesView;

@Repository
public interface TutorLeaderboardComplaintResponsesViewRepository extends JpaRepository<TutorLeaderboardComplaintResponsesView, Long> {

    List<TutorLeaderboardComplaintResponsesView> findAllByCourseId(long courseId);

    List<TutorLeaderboardComplaintResponsesView> findAllByLeaderboardId_ExerciseId(long exerciseId);
}
