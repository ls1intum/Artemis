package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessmentView;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponsesView;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintsView;
import de.tum.in.www1.artemis.repository.TutorLeaderboardAssessmentViewRepository;
import de.tum.in.www1.artemis.repository.TutorLeaderboardComplaintResponsesViewRepository;
import de.tum.in.www1.artemis.repository.TutorLeaderboardComplaintsViewRepository;

@Service
@Transactional(readOnly = true)
public class TutorLeaderboardService {

    private final TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository;

    private final TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository;

    private final TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository;

    public TutorLeaderboardService(TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository,
            TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository,
            TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository) {
        this.tutorLeaderboardAssessmentViewRepository = tutorLeaderboardAssessmentViewRepository;
        this.tutorLeaderboardComplaintsViewRepository = tutorLeaderboardComplaintsViewRepository;
        this.tutorLeaderboardComplaintResponsesViewRepository = tutorLeaderboardComplaintResponsesViewRepository;
    }

    public void getExerciseLeaderboard(Long exerciseId) {
        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByLeaderboardId_ExerciseId(exerciseId);
        List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByLeaderboardId_ExerciseId(exerciseId);
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository
                .findAllByLeaderboardId_ExerciseId(exerciseId);
        // TODO merge among user and return
    }

    public void getCourseLeaderboard(Long courseId) {
        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByCourseId(courseId);
        List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByCourseId(courseId);
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository.findAllByCourseId(courseId);
        // TODO merge among user and return
    }

}
