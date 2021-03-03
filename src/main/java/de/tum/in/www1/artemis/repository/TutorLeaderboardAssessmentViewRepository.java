package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessmentView;

@Repository
public interface TutorLeaderboardAssessmentViewRepository extends JpaRepository<TutorLeaderboardAssessmentView, Long> {

    List<TutorLeaderboardAssessmentView> findAllByCourseId(long courseId);

    // TODO: add when examId is added to the view: List<TutorLeaderboardAssessmentView> findAllByExamId(long examId);

    List<TutorLeaderboardAssessmentView> findAllByLeaderboardId_ExerciseId(long exerciseId);
}
