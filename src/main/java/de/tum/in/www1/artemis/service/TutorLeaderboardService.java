package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;

@Service
@Transactional(readOnly = true)
public class TutorLeaderboardService {

    private final TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository;

    private final TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository;

    private final TutorLeaderboardMoreFeedbackRequestsViewRepository tutorLeaderboardMoreFeedbackRequestsViewRepository;

    private final TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository;

    private final TutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository;

    private final UserService userService;

    public TutorLeaderboardService(TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository,
            TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository,
            TutorLeaderboardMoreFeedbackRequestsViewRepository tutorLeaderboardMoreFeedbackRequestsViewRepository,
            TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository,
            TutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository, UserService userService) {
        this.tutorLeaderboardAssessmentViewRepository = tutorLeaderboardAssessmentViewRepository;
        this.tutorLeaderboardComplaintsViewRepository = tutorLeaderboardComplaintsViewRepository;
        this.tutorLeaderboardMoreFeedbackRequestsViewRepository = tutorLeaderboardMoreFeedbackRequestsViewRepository;
        this.tutorLeaderboardComplaintResponsesViewRepository = tutorLeaderboardComplaintResponsesViewRepository;
        this.tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository = tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository;
        this.userService = userService;
    }

    /**
     * Returns tutor leaderboards for the specified course.
     *
     * @param course course for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getCourseLeaderboard(Course course) {

        List<User> tutors = userService.getTutors(course);

        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardAcceptedComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardNotAnsweredMoreFeedbackRequestsView> tutorLeaderboardMoreFeedbackRequests = tutorLeaderboardMoreFeedbackRequestsViewRepository
                .findAllByCourseId(course.getId());
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> tutorLeaderboardAnsweredMoreFeedbackRequests = tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository
                .findAllByCourseId(course.getId());

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests);
    }

    /**
     * Returns tutor leaderboards for the specified exercise.
     *
     * @param exercise exercise for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getExerciseLeaderboard(Exercise exercise) {

        List<User> tutors = userService.getTutors(exercise.getCourse());

        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardAcceptedComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardNotAnsweredMoreFeedbackRequestsView> tutorLeaderboardMoreFeedbackRequests = tutorLeaderboardMoreFeedbackRequestsViewRepository
                .findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository
                .findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> tutorLeaderboardAnsweredMoreFeedbackRequests = tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository
                .findAllByLeaderboardId_ExerciseId(exercise.getId());

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests);
    }

    @NotNull
    private List<TutorLeaderboardDTO> aggregateTutorLeaderboardData(List<User> tutors, List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments,
            List<TutorLeaderboardAcceptedComplaintsView> tutorLeaderboardAcceptedComplaints,
            List<TutorLeaderboardNotAnsweredMoreFeedbackRequestsView> tutorLeaderboardNotAnsweredMoreFeedbackRequests,
            List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses,
            List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> tutorLeaderboardAnsweredMoreFeedbackRequests) {

        List<TutorLeaderboardDTO> tutorLeaderBoardEntries = new ArrayList<>();
        for (User tutor : tutors) {

            Long numberOfAssessments = 0L;
            Long numberOfAcceptedComplaints = 0L;
            Long numberOfNotAnsweredMoreFeedbackRequests = 0L;
            Long numberOfComplaintResponses = 0L;
            Long numberOfAnsweredMoreFeedbackRequests = 0L;
            Long points = 0L;

            for (TutorLeaderboardAssessmentView assessmentsView : tutorLeaderboardAssessments) {
                if (assessmentsView.getUserId().equals(tutor.getId())) {
                    numberOfAssessments += assessmentsView.getAssessments();
                    if (assessmentsView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the assessments
                        points += assessmentsView.getPoints();
                    }
                    else if (assessmentsView.getAssessments() != null) {
                        points += assessmentsView.getAssessments();
                    }
                }
            }

            for (TutorLeaderboardAcceptedComplaintsView acceptedComplaintsView : tutorLeaderboardAcceptedComplaints) {
                if (acceptedComplaintsView.getUserId().equals(tutor.getId())) {
                    numberOfAcceptedComplaints += acceptedComplaintsView.getAcceptedComplaints();
                    // accepted complaints count 2x negatively
                    if (acceptedComplaintsView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the accepted complaints
                        points -= 2 * acceptedComplaintsView.getPoints();
                    }
                    else if (acceptedComplaintsView.getAcceptedComplaints() != null) {
                        points -= 2 * acceptedComplaintsView.getAcceptedComplaints();
                    }
                }
            }

            for (TutorLeaderboardNotAnsweredMoreFeedbackRequestsView notAnsweredMoreFeedbackRequestsView : tutorLeaderboardNotAnsweredMoreFeedbackRequests) {
                if (notAnsweredMoreFeedbackRequestsView.getUserId().equals(tutor.getId())) {
                    numberOfNotAnsweredMoreFeedbackRequests += notAnsweredMoreFeedbackRequestsView.getNotAnsweredRequests();
                    // not answered requests count only 1x negatively
                    if (notAnsweredMoreFeedbackRequestsView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the not answered
                                                                                     // requests
                        points -= notAnsweredMoreFeedbackRequestsView.getPoints();
                    }
                    else if (notAnsweredMoreFeedbackRequestsView.getNotAnsweredRequests() != null) {
                        points -= notAnsweredMoreFeedbackRequestsView.getNotAnsweredRequests();
                    }
                }
            }

            for (TutorLeaderboardComplaintResponsesView complaintResponsesView : tutorLeaderboardComplaintResponses) {
                if (complaintResponsesView.getUserId().equals(tutor.getId())) {
                    numberOfComplaintResponses += complaintResponsesView.getComplaintResponses();
                    // resolved complaints count 2x
                    if (complaintResponsesView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the complaint responses
                        points += 2 * complaintResponsesView.getPoints();
                    }
                    else if (complaintResponsesView.getComplaintResponses() != null) {
                        points += 2 * complaintResponsesView.getComplaintResponses();
                    }
                }
            }

            for (TutorLeaderboardAnsweredMoreFeedbackRequestsView moreFeedbackRequestsView : tutorLeaderboardAnsweredMoreFeedbackRequests) {
                if (moreFeedbackRequestsView.getUserId().equals(tutor.getId())) {
                    numberOfAnsweredMoreFeedbackRequests += moreFeedbackRequestsView.getAnsweredRequests();
                    // answered requests doesn't count, because it only means that the tutor repaired the negative points
                }
            }

            tutorLeaderBoardEntries.add(new TutorLeaderboardDTO(tutor.getId(), tutor.getName(), numberOfAssessments, numberOfAcceptedComplaints,
                    numberOfNotAnsweredMoreFeedbackRequests, numberOfComplaintResponses, numberOfAnsweredMoreFeedbackRequests, points));
        }
        return tutorLeaderBoardEntries;
    }
}
