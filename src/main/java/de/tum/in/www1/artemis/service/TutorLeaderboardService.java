package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessmentView;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponsesView;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintsView;
import de.tum.in.www1.artemis.repository.TutorLeaderboardAssessmentViewRepository;
import de.tum.in.www1.artemis.repository.TutorLeaderboardComplaintResponsesViewRepository;
import de.tum.in.www1.artemis.repository.TutorLeaderboardComplaintsViewRepository;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;

@Service
@Transactional(readOnly = true)
public class TutorLeaderboardService {

    private final TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository;

    private final TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository;

    private final TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository;

    private final UserService userService;

    public TutorLeaderboardService(TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository,
            TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository,
            TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository, UserService userService) {
        this.tutorLeaderboardAssessmentViewRepository = tutorLeaderboardAssessmentViewRepository;
        this.tutorLeaderboardComplaintsViewRepository = tutorLeaderboardComplaintsViewRepository;
        this.tutorLeaderboardComplaintResponsesViewRepository = tutorLeaderboardComplaintResponsesViewRepository;
        this.userService = userService;
    }

    public List<TutorLeaderboardDTO> getCourseLeaderboard(Course course) {

        List<User> tutors = userService.getTutors(course);

        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository.findAllByCourseId(course.getId());

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardComplaintResponses);
    }

    public List<TutorLeaderboardDTO> getExerciseLeaderboard(Exercise exercise) {

        List<User> tutors = userService.getTutors(exercise.getCourse());

        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository
                .findAllByLeaderboardId_ExerciseId(exercise.getId());

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardComplaintResponses);
    }

    @NotNull
    private List<TutorLeaderboardDTO> aggregateTutorLeaderboardData(List<User> tutors, List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments,
            List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints, List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses) {

        List<TutorLeaderboardDTO> tutorLeaderBoardEntries = new ArrayList<>();
        for (User tutor : tutors) {

            Long numberOfAssessments = 0L;
            Long numberOfComplaints = 0L;
            Long numberOfComplaintResponses = 0L;
            Long points = 0L;

            for (TutorLeaderboardAssessmentView assessmentsView : tutorLeaderboardAssessments) {
                if (assessmentsView.getUserId().equals(tutor.getId())) {
                    numberOfAssessments += assessmentsView.getAssessments();
                    points += assessmentsView.getPoints();
                }
            }

            for (TutorLeaderboardComplaintsView complaintsView : tutorLeaderboardComplaints) {
                if (complaintsView.getUserId().equals(tutor.getId())) {
                    numberOfComplaints += complaintsView.getComplaints();
                    // accepted complaints count negatively
                    points -= complaintsView.getPoints();
                }
            }

            for (TutorLeaderboardComplaintResponsesView complaintResponsesView : tutorLeaderboardComplaintResponses) {
                if (complaintResponsesView.getUserId().equals(tutor.getId())) {
                    numberOfComplaintResponses += complaintResponsesView.getComplaintResponses();
                    // resolved complaints count 2x
                    points += 2 * complaintResponsesView.getPoints();
                }
            }

            tutorLeaderBoardEntries.add(new TutorLeaderboardDTO(tutor.getId(), tutor.getName(), numberOfAssessments, numberOfComplaints, numberOfComplaintResponses, points));
        }
        return tutorLeaderBoardEntries;
    }
}
